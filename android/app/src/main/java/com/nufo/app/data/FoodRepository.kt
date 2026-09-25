package com.nufo.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

sealed interface Lookup {
    /** [cachedAt] is set when the network failed and a copy saved on this phone was used. */
    data class Found(val product: Product, val cachedAt: Long? = null) : Lookup
    /** Unknown to the food databases; [identified] is what a general barcode database could name, if anything. */
    data class NotFound(val identified: Identified? = null) : Lookup
    data class Failed(val message: String, val offline: Boolean) : Lookup
}

/** Which source a barcode lookup is asking right now, for the loading caption. */
enum class LookupStep { OpenFoodFacts, Usda, OtherDatabases }

/** Longest a lookup or search may run before the user gets a saved copy or an error with Retry. */
const val GIVE_UP_MS = 15_000L

/** Search results; [offline] means they come from products saved on this phone, not the databases. */
data class SearchOutcome(val hits: List<SearchHit>, val offline: Boolean = false)

class FoodRepository(private val api: FoodApi, private val dao: HistoryDao, private val cache: CacheDao, private val dishes: DishTable) {

    /** Generic nutrition for a photographed dish, if the bundled table has it. */
    fun dish(key: String, name: String): Product? = dishes.product(key, name)
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(s: String) = runCatching { json.decodeFromString<Product>(s) }.getOrNull()
    private fun encode(p: Product) = json.encodeToString(Product.serializer(), p)

    /** Null until the database has answered once, so screens can show loading instead of a false "empty". */
    val history = dao.all().map { rows -> rows.mapNotNull { decode(it.json) } }

    /**
     * Barcode pipeline: Open Food Facts, then USDA branded foods by UPC, then UPCitemdb for a name only.
     * Without a connection, a copy cached or saved on this phone is returned instead.
     */
    /**
     * Open Food Facts, then USDA, then UPCitemdb, reporting each step so the UI can say what it is doing.
     * Gives up after [GIVE_UP_MS] (each request alone may take 12 s): past that, a looping loader only
     * frustrates, so the user gets a saved copy or an error with Retry instead.
     */
    suspend fun lookupBarcode(barcode: String, lang: String, onStep: (LookupStep) -> Unit = {}): Lookup =
        withTimeoutOrNull(GIVE_UP_MS) { fetch(barcode, lang, onStep) }
            ?: saved(barcode) ?: Lookup.Failed("Timed out", offline = false)

    private suspend fun fetch(barcode: String, lang: String, onStep: (LookupStep) -> Unit): Lookup = try {
        onStep(LookupStep.OpenFoodFacts)
        val product = api.offProduct(barcode, lang)?.let { withTagNames(it, lang) }
            ?: run {
                onStep(LookupStep.Usda)
                api.usdaSearch(barcode, pageSize = 5).firstOrNull { it.product?.barcode?.trimStart('0') == barcode.trimStart('0') }?.product
            }
        if (product != null) {
            cache.upsert(CachedProduct(barcode, encode(product), System.currentTimeMillis()))
            Lookup.Found(product)
        } else {
            onStep(LookupStep.OtherDatabases)
            Lookup.NotFound(api.identify(barcode))
        }
    } catch (e: IOException) {
        saved(barcode) ?: Lookup.Failed(e.message ?: "Network error", offline = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Lookup.Failed(e.message ?: "Error", offline = false)
    }

    /** The offline copy: the lookup cache first, then history. */
    private suspend fun saved(barcode: String): Lookup.Found? =
        cache.get(barcode)?.let { c -> decode(c.json)?.let { Lookup.Found(it, c.fetchedAt) } }
            ?: dao.get(barcode)?.let { h -> decode(h.json)?.let { Lookup.Found(it, h.savedAt) } }

    /** Adds translated names for the product's tags; any taxonomy that fails just keeps the fallbacks. */
    private suspend fun withTagNames(p: Product, lang: String): Product = coroutineScope {
        val parts = listOf(
            "allergens" to p.allergenTags, "labels" to p.labelTags,
            "categories" to p.categoryTags.takeLast(4), "additives" to p.additiveTags,
        ).map { (type, tags) -> async { api.taxonomyNames(type, tags, lang) } }
        p.copy(tagNames = parts.flatMap { it.await().entries }.associate { it.key to it.value })
    }

    suspend fun product(hit: SearchHit, lang: String, onStep: (LookupStep) -> Unit = {}): Lookup =
        hit.product?.let { Lookup.Found(it) } ?: hit.barcode?.let { lookupBarcode(it, lang, onStep) } ?: Lookup.NotFound()

    /** Searches both sources in parallel; a failing source never hides the other one's results. */
    suspend fun search(query: String, filters: SearchFilters, lang: String): Result<SearchOutcome> = coroutineScope {
        val off = async { runCatching { withTimeout(GIVE_UP_MS) { api.offSearch(query, filters, lang) } } }
        val usda = async { if (filters.needsOffTags) Result.success(emptyList()) else runCatching { withTimeout(GIVE_UP_MS) { api.usdaSearch(query) } } }
        val results = listOf(off.await(), usda.await())
        results.forEach { r -> r.exceptionOrNull()?.let { android.util.Log.w("Nufo", "Search source failed", it) } }
        val hits = results.flatMap { it.getOrDefault(emptyList()) }.filter(filters::accepts)
        when {
            hits.isNotEmpty() || results.any { it.isSuccess } -> Result.success(SearchOutcome(hits))
            results.all { it.exceptionOrNull() is IOException } -> Result.success(SearchOutcome(searchSaved(query), offline = true))
            else -> Result.failure(results.first().exceptionOrNull()!!)
        }
    }

    /** Offline search over products cached or saved on this phone, matching every word of the query. */
    private suspend fun searchSaved(query: String): List<SearchHit> {
        val words = GreekSearch.normalize(query).split(' ').filter { it.isNotBlank() }
        val products = (dao.snapshot().map { it.json } + cache.recent().map { it.json }).mapNotNull(::decode).distinctBy { it.key }
        return products.filter { p ->
            val text = GreekSearch.normalize("${p.name} ${p.brand.orEmpty()}")
            words.all { it in text }
        }.map { p ->
            SearchHit(
                name = p.name, brand = p.brand, imageUrl = p.imageUrl, nutriscoreGrade = p.nutriscoreGrade,
                novaGroup = p.novaGroup, caloriesPer100g = p.nutritionPer100g.calories, source = p.source,
                barcode = p.barcode, product = p, soldInGreece = p.soldInGreece,
            )
        }
    }

    suspend fun prices(barcode: String): Result<List<PriceReport>> = runCatching { api.prices(barcode) }

    suspend fun save(product: Product) =
        dao.upsert(HistoryEntity(product.key, encode(product), System.currentTimeMillis()))

    suspend fun delete(product: Product) = dao.delete(product.key)
    suspend fun clearHistory() { dao.clear(); cache.clear() }
}