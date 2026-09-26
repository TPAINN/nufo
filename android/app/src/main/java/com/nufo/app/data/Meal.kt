package com.nufo.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One food found in a meal photo: [grams] is the estimated amount on the plate, [per100] its nutrition. */
data class MealItem(
    val nameEl: String,
    val nameEn: String,
    val grams: Int,
    val confidence: Double,
    val per100: Nutrition,
    /** Nutrition is USDA FNDDS data (it agreed with the model), not the model's own estimate. */
    val fromUsda: Boolean,
) {
    fun name(lang: String) = if (lang == "el") nameEl else nameEn
}

data class MealAnalysis(val nameEl: String, val nameEn: String, val items: List<MealItem>) {
    fun name(lang: String) = if (lang == "el") nameEl else nameEn
}

/** Reads the answer of the Nufo meal analysis service (nufo.vercel.app/api/analyze). */
object MealParser {
    const val SOURCE = "Nufo AI · USDA"
    const val ENDPOINT = "https://nufo.vercel.app/api/analyze"

    fun parse(root: JsonObject): MealAnalysis {
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
        val items = root["items"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val per100 = item["per100"]?.jsonObject ?: return@mapNotNull null
            val grams = item.num("grams")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            val salt = per100.num("salt")
            MealItem(
                nameEl = item.str("name_el") ?: item.str("name_en") ?: return@mapNotNull null,
                nameEn = item.str("name_en") ?: item.str("name_el") ?: return@mapNotNull null,
                grams = grams,
                confidence = item.num("confidence") ?: 0.0,
                per100 = Nutrition(
                    calories = per100.num("kcal"), protein = per100.num("protein"), carbs = per100.num("carbs"),
                    fat = per100.num("fat"), fiber = per100.num("fiber"), sugar = per100.num("sugar"),
                    salt = salt, sodium = salt?.let { it / 2.5 },
                ),
                fromUsda = item.str("source") == "usda",
            )
        }
        return MealAnalysis(root.str("meal_el") ?: items.firstOrNull()?.nameEl.orEmpty(), root.str("meal_en") ?: items.firstOrNull()?.nameEn.orEmpty(), items)
    }
}

/**
 * The whole plate as one product: nutrition per 100 g of the meal (each item weighted by its grams; a nutrient
 * only when every item has it), a serving that is the whole plate, and the items as its recipe.
 */
fun MealAnalysis.toProduct(lang: String, brand: String?, now: Long = System.currentTimeMillis()): Product {
    val total = items.sumOf { it.grams }.toDouble()
    fun avg(field: (Nutrition) -> Double?): Double? =
        items.map { field(it.per100) ?: return null }.zip(items).sumOf { (v, i) -> v * i.grams } / total
    val per100 = Nutrition(
        calories = avg { it.calories }, protein = avg { it.protein }, carbs = avg { it.carbs }, fat = avg { it.fat },
        fiber = avg { it.fiber }, sugar = avg { it.sugar }, salt = avg { it.salt }, sodium = avg { it.sodium },
    )
    return Product(
        barcode = null, name = name(lang), brand = brand, quantity = null, servingSize = "${total.toInt()} g", servingGrams = total,
        imageUrl = null, ingredientsText = items.joinToString { it.name(lang) },
        nutritionPer100g = per100, nutritionPerServing = null, nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null,
        nufoScore = Scoring.nufoScore(per100, null, null).score, source = MealParser.SOURCE, lastUpdated = now,
        isDish = true,
        recipe = items.sortedByDescending { it.grams }.map { RecipePart(it.name(lang), it.grams * 100 / total, it.per100.calories) },
    )
}
