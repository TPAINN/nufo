package com.nufo.app.data

import com.nufo.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String) : IOException(message)

/**
 * Thin HTTP layer over the free food APIs. No personal data is ever sent: only the barcode
 * or the search text the user typed.
 */
class FoodApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Open Food Facts asks every client to identify itself.
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build(),
) {
    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (res.code == 404) return@use JsonObject(emptyMap())
            if (!res.isSuccessful) throw ApiException("HTTP ${res.code}")
            Json.parseToJsonElement(res.body.string()).jsonObject
        }
    }

    suspend fun latestUpdate(current: String): AppUpdate? = Updates.parse(get(Updates.LATEST_RELEASE), current)

    suspend fun offProduct(barcode: String, lang: String): Product? {
        val url = "$OFF/api/v2/product/$barcode.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", OffParser.FIELDS).build().toString()
        return OffParser.parseProductResponse(get(url), barcode, lang)
    }

    /** Tag id -> name in [lang] for one taxonomy ("allergens", "labels", ...). Best effort: failures give an empty map. */
    suspend fun taxonomyNames(type: String, tags: List<String>, lang: String): Map<String, String> {
        if (tags.isEmpty()) return emptyMap()
        val url = "$OFF/api/v2/taxonomy".toHttpUrl().newBuilder()
            .addQueryParameter("tagtype", type)
            .addQueryParameter("tags", tags.joinToString(","))
            .addQueryParameter("lc", lang)
            .addQueryParameter("fields", "name")
            .build().toString()
        return runCatching { OffParser.parseTaxonomy(get(url), lang) }.getOrDefault(emptyMap())
    }

    /**
     * Search-a-licious (OFF's current search API). Search-a-licious has no free-text OR, so each
     * Greek variant (as typed, without accents, English) runs as its own query restricted to
     * products sold in Greece, alongside one worldwide query. Greek products are listed first.
     * User text is stripped of Lucene syntax (a stray ":" makes the server return 500).
     * With zero hits, retries with fuzzy brand matching, which rescues OCR typos like "nutelld".
     */
    suspend fun offSearch(query: String, filters: SearchFilters, lang: String): List<SearchHit> = coroutineScope {
        val clean = query.replace(Regex("""[+\-&|!(){}\[\]^"~*?:\\/]"""), " ").trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return@coroutineScope emptyList()
        val greek = filters.copy(greekOnly = true)
        val calls = GreekSearch.variants(clean).map { v -> async { runCatching { offQuery(v, greek, lang) } } } +
            (if (filters.greekOnly) emptyList() else listOf(async { runCatching { offQuery(clean, filters, lang) } }))
        val results = calls.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        val wanted = GreekSearch.variants(clean).map { GreekSearch.normalize(it).split(' ') }
        // Search-a-licious stems words ("φέτες" matches "φέτα"), so exact whole-word matches go first.
        fun exact(h: SearchHit): Boolean {
            val words = GreekSearch.normalize(h.name).split(Regex("[^\\p{L}\\p{N}%]+")).toSet()
            return wanted.any { v -> v.all { it in words } }
        }
        val merged = results.flatMap { it.getOrDefault(emptyList()) }
            .distinctBy { it.barcode ?: it.name }
            .sortedWith(
                compareByDescending<SearchHit> { it.soldInGreece }.thenByDescending(::exact)
                    // Then entries a shopper can trust at a glance: photo, calories and grade present.
                    .thenByDescending { listOf(it.imageUrl, it.caloriesPer100g, it.nutriscoreGrade).count { f -> f != null } },
            ) // stable within groups
        if (merged.isNotEmpty()) return@coroutineScope merged.take(40)
        val words = GreekSearch.stripAccents(clean).split(' ').filter { it.length >= 3 }
        if (words.isEmpty()) emptyList() else offQuery(words.joinToString(" OR ", "(", ")") { "brands:$it~2" }, filters, lang)
    }

    private suspend fun offQuery(q: String, filters: SearchFilters, lang: String): List<SearchHit> {
        val url = "https://search.openfoodfacts.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", (listOf(q) + filters.offQueryClauses()).joinToString(" "))
            .addQueryParameter("langs", "el,en")
            .addQueryParameter("page_size", "20")
            .addQueryParameter("fields", OffParser.SEARCH_FIELDS)
            .build().toString()
        return OffParser.parseSearch(get(url), lang)
    }

    /** USDA is English-only: Greek queries go through the Greek food dictionary, or are skipped. */
    suspend fun usdaSearch(query: String, pageSize: Int = 15): List<SearchHit> {
        val q = if (GreekSearch.hasGreek(query)) GreekSearch.english(query) ?: return emptyList() else query
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", BuildConfig.USDA_API_KEY)
            .addQueryParameter("query", q)
            .addQueryParameter("pageSize", pageSize.toString())
            .build().toString()
        return UsdaParser.parseSearch(get(url))
    }

    /** Crowdsourced shop prices for a barcode (Open Prices, by Open Food Facts). */
    suspend fun prices(barcode: String): List<PriceReport> {
        val url = "https://prices.openfoodfacts.org/api/v1/prices".toHttpUrl().newBuilder()
            .addQueryParameter("product_code", barcode)
            .addQueryParameter("order_by", "-date")
            .addQueryParameter("size", "20")
            .build().toString()
        return PricesParser.parse(get(url))
    }

    /**
     * Last resort for barcodes neither food database knows: UPCitemdb's free trial endpoint
     * (no key, ~100 lookups a day per IP) can at least name the product. Mostly North American data.
     */
    suspend fun identify(barcode: String): Identified? {
        if (barcode.length !in 8..14) return null
        val url = "https://api.upcitemdb.com/prod/trial/lookup".toHttpUrl().newBuilder().addQueryParameter("upc", barcode).build().toString()
        return runCatching { UpcItemDbParser.parse(get(url)) }.getOrNull()
    }

    companion object {
        const val OFF = "https://world.openfoodfacts.org"
        const val USER_AGENT = "Nufo/1.0 (contact@nufo.app)"
    }
}