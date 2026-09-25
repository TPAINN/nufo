package com.nufo.app.data

/** EU's 14 regulated allergens as Open Food Facts tag ids; names come from [TagNames]. */
val ALLERGENS = listOf(
    "en:gluten", "en:milk", "en:eggs", "en:nuts", "en:peanuts", "en:soybeans", "en:fish", "en:crustaceans",
    "en:molluscs", "en:sesame-seeds", "en:celery", "en:mustard", "en:lupin", "en:sulphur-dioxide-and-sulphites",
)

/** Allergen tags from the user's alert list that this product declares. */
fun Product.matchingAllergens(alertTags: Set<String>): List<String> = allergenTags.filter { it in alertTags }

/** Search categories as OFF tag ids (names are string resources in the UI). */
val SEARCH_CATEGORIES = listOf(
    "en:dairies", "en:cheeses", "en:yogurts", "en:olive-oils", "en:beverages", "en:snacks", "en:breakfasts",
    "en:cereals-and-potatoes", "en:fruits-and-vegetables-based-foods", "en:meats-and-their-products", "en:desserts",
)

data class SearchFilters(
    val category: String? = null,
    /** Only products sold in Greece (Open Food Facts countries_tags). */
    val greekOnly: Boolean = false,
    val grades: Set<String> = emptySet(),
    val nova: Set<Int> = emptySet(),
    val vegetarian: Boolean = false,
    val vegan: Boolean = false,
    val excludeAllergens: Set<String> = emptySet(),
) {
    /** Tag filters only Open Food Facts can apply; USDA results are hidden while any is active. */
    val needsOffTags get() = category != null || greekOnly || vegetarian || vegan || excludeAllergens.isNotEmpty()
    val isActive get() = needsOffTags || grades.isNotEmpty() || nova.isNotEmpty()

    fun offQueryClauses(): List<String> = buildList {
        category?.let { add("categories_tags:\"$it\"") }
        if (greekOnly) add("countries_tags:\"en:greece\"")
        if (vegan) add("labels_tags:\"en:vegan\"")
        else if (vegetarian) add("labels_tags:\"en:vegetarian\"")
        excludeAllergens.forEach { add("-allergens_tags:\"$it\"") }
    }

    fun accepts(hit: SearchHit): Boolean =
        (grades.isEmpty() || hit.nutriscoreGrade in grades) &&
            (nova.isEmpty() || hit.novaGroup in nova) &&
            (!needsOffTags || hit.source == OffParser.SOURCE)
}