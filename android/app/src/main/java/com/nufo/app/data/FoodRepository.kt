package com.nufo.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/**
 * Search results, best first. [top] is the answer that matches every word of the query, [correction]
 * what the query was understood as when that differs from what was typed («gala» → «γάλα»), and
 * [offline] means they come from products saved on this phone, not the databases.
 */
data class SearchOutcome(
    val hits: List<SearchHit>,
    val offline: Boolean = false,
    val top: SearchHit? = null,
    val correction: String? = null,
)

class FoodRepository(
    private val api: FoodApi, private val dao: HistoryDao, private val cache: CacheDao,
    private val dishes: DishTable, private val foods: FoodTable,
) {

    /** Generic nutrition for a photographed dish, if the bundled table has it. */
    fun dish(key: String, name: String, lang: String): Product? = dishes.product(key, name, lang)
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
        // What the user read from the package label earlier fills whatever the databases still lack.
        val fromLabel = dao.get(barcode)?.let { decode(it.json) }?.takeIf { it.filledFromLabel }
        if (product != null) {
            cache.upsert(CachedProduct(barcode, encode(product), System.currentTimeMillis()))
            Lookup.Found(if (fromLabel != null) product.fillMissingFrom(fromLabel) else product)
        } else if (fromLabel != null) {
            Lookup.Found(fromLabel)
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

    /**
     * Understands the query, asks Open Food Facts and the bundled USDA table in parallel, then ranks
     * all answers together. Offline, the table and the products saved on this phone still answer.
     */
    suspend fun search(query: String, filters: SearchFilters, lang: String): Result<SearchOutcome> = coroutineScope {
        val q = SmartSearch.understand(query)
        val off = async { runCatching { withTimeout(GIVE_UP_MS) { api.offSearch(q, filters, lang) } } }
        // Plain foods come from the USDA table bundled in the app: instant, offline, no API quota.
        val table = async(kotlinx.coroutines.Dispatchers.Default) { if (filters.needsOffTags) emptyList() else foods.search(q) }
        val offResult = off.await()
        offResult.exceptionOrNull()?.let { android.util.Log.w("Nufo", "Open Food Facts search failed", it) }
        val offline = offResult.exceptionOrNull() is IOException
        val hits = (offResult.getOrDefault(emptyList()) + table.await() + (if (offline) searchSaved(q) else emptyList()))
            .filter(filters::accepts)
        if (offResult.isFailure && !offline && hits.isEmpty()) return@coroutineScope Result.failure(offResult.exceptionOrNull()!!)
        val ranked = SmartSearch.rank(hits, q)
        val shown = if (offline) ranked.hits.take(40) else withPictures(ranked.hits.take(40), q)
        val top = ranked.top?.let { t -> shown.firstOrNull { it.name == t.name && it.source == t.source && it.barcode == t.barcode } }
        Result.success(SearchOutcome(shown, offline = offline, top = top, correction = q.correction))
    }

    /**
     * Plain USDA foods have no photos; they get one of the food itself (bananas for "Bananas, raw"),
     * fetched in parallel and never allowed to hold up the results for more than a couple of seconds.
     */
    private suspend fun withPictures(hits: List<SearchHit>, q: SmartSearch.Query): List<SearchHit> = coroutineScope {
        hits.map { h ->
            async {
                val pic = SmartSearch.pictureSubject(h, q)?.let { withTimeoutOrNull(2_500) { api.foodPicture(it) } }
                when {
                    pic == null -> h
                    // USDA foods are the food itself, so the picture is theirs on the product page too.
                    h.source == UsdaParser.SOURCE -> h.copy(imageUrl = pic, product = h.product?.copy(imageUrl = h.product.imageUrl ?: pic))
                    // A packaged product keeps an honest empty photo on its own page; the list only needs a cue.
                    else -> h.copy(imageUrl = pic)
                }
            }
        }.awaitAll()
    }

    /** Offline search over products cached or saved on this phone, matching any form of every query word. */
    private suspend fun searchSaved(q: SmartSearch.Query): List<SearchHit> {
        val products = (dao.snapshot().map { it.json } + cache.recent().map { it.json }).mapNotNull(::decode).distinctBy { it.key }
        return products.filter { p ->
            val text = GreekSearch.normalize("${p.name} ${p.brand.orEmpty()}")
            q.terms.all { t -> t.forms.any { f -> SmartSearch.stem(f) in text } }
        }.map { p ->
            SearchHit(
                name = p.name, brand = p.brand, imageUrl = p.imageUrl, nutriscoreGrade = p.nutriscoreGrade,
                novaGroup = p.novaGroup, caloriesPer100g = p.nutritionPer100g.calories, source = p.source,
                barcode = p.barcode, product = p, soldInGreece = p.soldInGreece,
            )
        }
    }

    suspend fun analyzeMeal(jpeg: ByteArray): Result<MealAnalysis> = runCatching { api.analyzeMeal(jpeg) }

    suspend fun readLabel(jpegs: List<ByteArray>, barcode: String?, share: Boolean): Result<LabelReading?> =
        runCatching { api.readLabel(jpegs, barcode, share) }

    suspend fun latestUpdate(current: String): Result<AppUpdate?> = runCatching { api.latestUpdate(current) }

    suspend fun prices(barcode: String): Result<List<PriceReport>> = runCatching { api.prices(barcode) }

    suspend fun save(product: Product) =
        dao.upsert(HistoryEntity(product.key, encode(product), System.currentTimeMillis()))

    suspend fun delete(product: Product) = dao.delete(product.key)
    suspend fun clearHistory() { dao.clear(); cache.clear() }
}