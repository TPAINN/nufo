package com.nufo.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** What the Nufo label reader found on a photographed nutrition table (nufo.vercel.app/api/label). */
data class LabelReading(
    val per100: Nutrition,
    val servingGrams: Double?,
    val ingredientsText: String?,
    val productName: String?,
    val brand: String?,
    val quantity: String?,
    /** Official Nutri-Score (2023 algorithm) computed from the printed values; null when it cannot be computed. */
    val nutriscoreGrade: String?,
    /** NOVA group estimated from the printed ingredient list; null without ingredients. */
    val novaGroup: Int?,
    /** Shared with Open Food Facts, so every app using it gets the values. */
    val contributed: Boolean,
)

object LabelParser {
    const val ENDPOINT = "https://nufo.vercel.app/api/label"

    /** Null when the photo showed no readable nutrition table. */
    fun parse(root: JsonObject): LabelReading? {
        fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
        if ((root["readable"] as? JsonPrimitive)?.content != "true") return null
        val p = root["per100"] as? JsonObject ?: return null
        val salt = p.num("salt") ?: p.num("sodium")?.let { it * 2.5 }
        val per100 = Nutrition(
            calories = p.num("energy_kcal"), protein = p.num("protein"), carbs = p.num("carbohydrates"), fat = p.num("fat"),
            saturatedFat = p.num("saturated_fat"), fiber = p.num("fiber"), sugar = p.num("sugars"),
            salt = salt, sodium = p.num("sodium") ?: salt?.let { it / 2.5 },
        )
        if (per100.calories == null && per100.protein == null && per100.carbs == null && per100.fat == null) return null
        return LabelReading(
            per100 = per100,
            servingGrams = root.num("serving_size_g")?.takeIf { it > 0 },
            ingredientsText = root.str("ingredients_text"),
            productName = root.str("product_name"), brand = root.str("brand"), quantity = root.str("quantity"),
            nutriscoreGrade = (root["nutriscore"] as? JsonObject)?.str("grade")?.takeIf { it in setOf("a", "b", "c", "d", "e") },
            novaGroup = (root["nova"] as? JsonObject)?.num("group")?.toInt()?.takeIf { it in 1..4 },
            contributed = (root["contributed"] as? JsonPrimitive)?.content == "true",
        )
    }
}

/** A product whose missing facts were read from its package label; the database's own values always win. */
fun Product.withLabel(l: LabelReading, now: Long = System.currentTimeMillis()): Product {
    val n = nutritionPer100g
    val merged = n.copy(
        calories = n.calories ?: l.per100.calories, protein = n.protein ?: l.per100.protein, carbs = n.carbs ?: l.per100.carbs,
        fat = n.fat ?: l.per100.fat, saturatedFat = n.saturatedFat ?: l.per100.saturatedFat, fiber = n.fiber ?: l.per100.fiber,
        sugar = n.sugar ?: l.per100.sugar, salt = n.salt ?: l.per100.salt, sodium = n.sodium ?: l.per100.sodium,
    )
    val grade = nutriscoreGrade ?: l.nutriscoreGrade
    val nova = novaGroup ?: l.novaGroup
    val grams = servingGrams ?: l.servingGrams
    return copy(
        name = name.ifBlank { l.productName.orEmpty() },
        brand = brand ?: l.brand,
        quantity = quantity ?: l.quantity,
        servingGrams = grams,
        servingSize = servingSize ?: grams?.let { "${fmtGrams(it)} g" },
        ingredientsText = ingredientsText ?: l.ingredientsText,
        nutritionPer100g = merged,
        nutritionPerServing = nutritionPerServing ?: grams?.let { merged.scaled(it / 100) },
        nutriscoreGrade = grade,
        novaGroup = nova,
        nutriscoreFromLabel = nutriscoreFromLabel || (nutriscoreGrade == null && grade != null),
        novaFromLabel = novaFromLabel || (novaGroup == null && nova != null),
        filledFromLabel = true,
        nufoScore = Scoring.nufoScore(merged, grade, nova).score,
        lastUpdated = now,
    )
}

/** This product with the values a label-completed copy of it supplied where this one still has none. */
fun Product.fillMissingFrom(labelCopy: Product): Product {
    val grade = labelCopy.nutriscoreGrade?.takeIf { nutriscoreGrade == null && labelCopy.nutriscoreFromLabel }
    val nova = labelCopy.novaGroup?.takeIf { novaGroup == null && labelCopy.novaFromLabel }
    val n = labelCopy.nutritionPer100g
    val reading = LabelReading(n, labelCopy.servingGrams, labelCopy.ingredientsText, labelCopy.name.ifBlank { null }, labelCopy.brand,
        labelCopy.quantity, grade, nova, contributed = false)
    val merged = withLabel(reading, lastUpdated)
    // Only mark the parts that really came from the label.
    return merged.copy(
        filledFromLabel = merged.nutritionPer100g != nutritionPer100g || grade != null || nova != null || merged.ingredientsText != ingredientsText,
        nutriscoreFromLabel = grade != null, novaFromLabel = nova != null,
    )
}

/** A product no database knows, built from its label (and whatever a barcode database could name). */
fun LabelReading.toProduct(barcode: String?, identified: Identified?, now: Long = System.currentTimeMillis()): Product = Product(
    barcode = barcode, name = productName ?: identified?.name.orEmpty(), brand = brand, quantity = quantity,
    servingSize = null, servingGrams = null, imageUrl = identified?.imageUrl, ingredientsText = null,
    nutritionPer100g = Nutrition(), nutritionPerServing = null, nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null,
    nufoScore = 100, source = LABEL_SOURCE, lastUpdated = now,
).withLabel(this, now)

const val LABEL_SOURCE = "Ετικέτα προϊόντος"

private fun fmtGrams(g: Double) = if (g % 1.0 == 0.0) g.toInt().toString() else "%.1f".format(g)

/** True when the product lacks what a label photo can supply: energy, the main macros, a grade or ingredients. */
fun Product.missingLabelFacts(): Boolean {
    if (isDish) return false
    val n = nutritionPer100g
    return n.calories == null || n.protein == null || n.carbs == null || n.fat == null || nutriscoreGrade == null || novaGroup == null
}
