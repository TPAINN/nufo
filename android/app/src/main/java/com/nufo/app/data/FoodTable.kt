package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Plain foods ("Bananas, raw", "Beef, ground, raw") with their nutrition per 100 g: the 5,431 USDA
 * FNDDS survey foods (public domain), bundled in assets/food/fndds.json as
 * [description, kcal, protein, carbs, fat, fiber, sugar, salt].
 *
 * Search used to ask the USDA API for these, with the shared DEMO_KEY: about ten requests an hour
 * per phone, after which plain foods silently vanished from results. From the phone they are
 * instant, need no quota and work offline.
 */
class FoodTable(private val load: () -> String) {
    private class Row(val name: String, val words: Set<String>, val v: List<Double?>)

    private val rows: List<Row> by lazy {
        Json.parseToJsonElement(load()).jsonArray.mapNotNull { el ->
            val a = el.jsonArray
            val name = a.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
            Row(name, words(name), (1..7).map { a.getOrNull(it)?.jsonPrimitive?.doubleOrNull })
        }
    }

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9%]+")).filter { it.isNotBlank() }.toSet()

    private fun isEnglish(f: String) = f.any(Char::isLetter) && f.all { it.code < 128 }

    private fun has(r: Row, part: String) =
        part in r.words || part.removeSuffix("s") in r.words || "${part}s" in r.words || "${part}es" in r.words

    /**
     * Foods whose name holds every word of the query in English (the query's English forms come from
     * [SmartSearch.understand], so «κιμάς», «kima» and "ground beef" all arrive here as ground beef).
     * Brand searches get nothing: these are foods, not products. Ranking is [SmartSearch.rank]'s job.
     */
    fun search(q: SmartSearch.Query, limit: Int = 30): List<SearchHit> {
        if (q.brand != null) return emptyList()
        val perTerm = q.terms.map { t -> t.forms.filter(::isEnglish) }
        if (perTerm.isEmpty() || perTerm.any { it.isEmpty() }) return emptyList()
        val intent = q.usdaIntent?.split(' ')?.filter { it != "raw" }.orEmpty()
        return rows.asSequence()
            .filter { r -> perTerm.all { forms -> forms.any { f -> f.split(' ').all { has(r, it) } } } }
            // What people mean by the bare word first («κοτόπουλο» → chicken breast), then the plainest names.
            .sortedWith(compareByDescending<Row> { r -> intent.isNotEmpty() && intent.all { has(r, it) } }.thenBy { it.name.length })
            .take(limit)
            .map(::hit)
            .toList()
    }

    private fun hit(r: Row): SearchHit {
        val (kcal, protein, carbs, fat, fiber, sugar, salt) = r.v
        val per100 = Nutrition(
            calories = kcal, protein = protein, carbs = carbs, fat = fat, fiber = fiber, sugar = sugar,
            salt = salt, sodium = salt?.div(2.5),
        )
        val product = Product(
            barcode = null, name = r.name, brand = null, quantity = null, servingSize = null, servingGrams = null,
            imageUrl = null, ingredientsText = null, nutritionPer100g = per100, nutritionPerServing = null,
            nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null,
            nufoScore = Scoring.nufoScore(per100, null, null).score, source = UsdaParser.SOURCE, lastUpdated = 0L,
        )
        return SearchHit(r.name, null, null, null, null, kcal, UsdaParser.SOURCE, product = product)
    }
}

private operator fun <T> List<T>.component6() = this[5]
private operator fun <T> List<T>.component7() = this[6]