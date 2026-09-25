package com.nufo.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val barcode: String?,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val servingSize: String?,
    /** Serving weight in grams when the source states it; lets portions scale per-100g values. */
    val servingGrams: Double?,
    val imageUrl: String?,
    val ingredientsText: String?,
    /** Raw Open Food Facts tag ids ("en:milk"); display names come from [tagNames]. */
    val allergenTags: List<String> = emptyList(),
    val additiveTags: List<String> = emptyList(),
    val categoryTags: List<String> = emptyList(),
    val labelTags: List<String> = emptyList(),
    /** Tag id -> name in the app language, from the Open Food Facts taxonomy (missing ones fall back). */
    val tagNames: Map<String, String> = emptyMap(),
    val nutritionPer100g: Nutrition,
    val nutritionPerServing: Nutrition?,
    val nutriscoreGrade: String?,
    val novaGroup: Int?,
    val ecoscoreGrade: String?,
    val nufoScore: Int,
    val source: String,
    val lastUpdated: Long,
    /** Open Food Facts' own 0..1 completeness estimate; null for sources that don't publish one. */
    val completeness: Double? = null,
    /** True when Open Food Facts lists Greece among the countries where it is sold. */
    val soldInGreece: Boolean = false,
) {
    /** Stable key for history: barcode when known, otherwise source + name. */
    val key: String get() = barcode ?: "$source:$name"

    fun tagName(tag: String, lang: String): String = tagNames[tag] ?: TagNames.fallback(tag, lang) ?: prettyTag(tag)
}

@Serializable
data class Nutrition(
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    /** Grams. */
    val sodium: Double? = null,
    /** Grams. */
    val salt: Double? = null,
    /** Keyed by [Micronutrient] id, e.g. "vitamin-c". */
    val vitamins: Map<String, Double> = emptyMap(),
    val minerals: Map<String, Double> = emptyMap(),
) {
    fun scaled(factor: Double) = Nutrition(
        calories?.times(factor), protein?.times(factor), carbs?.times(factor), fat?.times(factor),
        saturatedFat?.times(factor), fiber?.times(factor), sugar?.times(factor),
        sodium?.times(factor), salt?.times(factor),
        vitamins.mapValues { it.value * factor }, minerals.mapValues { it.value * factor },
    )
}

/** Search row. OFF hits carry only a barcode (detail fetched on tap); USDA hits are complete. */
data class SearchHit(
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val nutriscoreGrade: String?,
    val novaGroup: Int?,
    val caloriesPer100g: Double?,
    val source: String,
    val barcode: String? = null,
    val product: Product? = null,
    val soldInGreece: Boolean = false,
)

// Open Food Facts serves each selected photo at 100/200/400 px height and at full resolution
// ("front_el.12.400.jpg" / "front_el.12.full.jpg"). Thumbnails use 400 px (sharp at 3x, shared with the offline cache);
// the hero adds full resolution on top.
private val OFF_SIZE = Regex("""\.(100|200|400)\.jpg$""")

/** Same OFF photo at another size ("200", "400", "full"); other URLs are returned unchanged. */
fun offImage(url: String?, size: String): String? = url?.let { if (OFF_SIZE.containsMatchIn(it)) it.replace(OFF_SIZE, ".$size.jpg") else it }

/** One crowdsourced price observation from Open Prices. */
data class PriceReport(
    val price: Double,
    val currency: String,
    /** ISO date, "2026-09-13". */
    val date: String,
    val store: String?,
    val city: String?,
    val country: String?,
) {
    val inGreece get() = country in setOf("Greece", "Ελλάδα", "Ελλάς")
}

/** What a secondary barcode database knows about a product Open Food Facts lacks: identity only, no nutrition. */
data class Identified(val name: String, val brand: String?, val imageUrl: String?, val source: String)