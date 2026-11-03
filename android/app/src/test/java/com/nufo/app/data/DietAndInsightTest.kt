package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietAndInsightTest {
    private fun product(
        n: Nutrition = Nutrition(),
        allergens: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        labels: List<String> = emptyList(),
        nova: Int? = null,
    ) = Product(
        barcode = "1", name = "X", brand = null, quantity = null, servingSize = null, servingGrams = null, imageUrl = null,
        ingredientsText = null, allergenTags = allergens, categoryTags = categories, labelTags = labels,
        nutritionPer100g = n, nutritionPerServing = null, nutriscoreGrade = null, novaGroup = nova, ecoscoreGrade = null,
        nufoScore = 0, source = OffParser.SOURCE, lastUpdated = 0,
    )

    // ---- Diet warnings ----

    @Test fun `meat is not vegetarian even though meat is not an allergen`() {
        // Arrange
        val ham = product(categories = listOf("en:meats-and-their-products", "en:hams"))
        // Act
        val note = dietNote(ham, Diet.Vegetarian)
        // Assert
        assertEquals(DietNote.NotVegetarian, note?.first)
    }

    @Test fun `poultry and fish are not vegetarian`() {
        assertEquals(DietNote.NotVegetarian, dietNote(product(categories = listOf("en:poultries")), Diet.Vegetarian)?.first)
        assertEquals(DietNote.NotVegetarian, dietNote(product(allergens = listOf("en:fish")), Diet.Vegetarian)?.first)
    }

    @Test fun `meat is not vegan`() {
        assertEquals(DietNote.NotVegan, dietNote(product(categories = listOf("en:meats")), Diet.Vegan)?.first)
    }

    @Test fun `dairy and eggs are vegetarian but not vegan`() {
        val yogurt = product(allergens = listOf("en:milk"), categories = listOf("en:yogurts"))
        assertNull(dietNote(yogurt, Diet.Vegetarian))
        assertEquals(DietNote.NotVegan, dietNote(yogurt, Diet.Vegan)?.first)
    }

    @Test fun `an official vegan or vegetarian label wins`() {
        val veganBurger = product(categories = listOf("en:meat-analogues"), labels = listOf("en:vegan"))
        assertNull(dietNote(veganBurger, Diet.Vegan))
        assertNull(dietNote(product(categories = listOf("en:meats"), labels = listOf("en:vegetarian")), Diet.Vegetarian))
    }

    @Test fun `meat analogues are not mistaken for meat`() {
        assertNull(dietNote(product(categories = listOf("en:meat-analogues", "en:plant-based-foods")), Diet.Vegetarian))
    }

    @Test fun `keto and low sodium report the amount`() {
        val bread = product(Nutrition(carbs = 48.0, salt = 1.2))
        assertEquals(DietNote.HighCarbsKeto to 48.0, dietNote(bread, Diet.Keto))
        assertEquals(DietNote.NotLowSodium to 1.2, dietNote(bread, Diet.LowSodium))
        assertNull(dietNote(product(Nutrition(carbs = 5.0, salt = 0.1)), Diet.Keto))
        assertNull(dietNote(bread, Diet.None))
    }

    // ---- Insights ----

    @Test fun `insights describe the nutrients in both directions`() {
        val good = Scoring.insights(product(Nutrition(protein = 12.0, fiber = 6.0, sugar = 3.0, salt = 0.2), nova = 1))
        assertEquals(
            listOf(InsightKind.HighProtein, InsightKind.GoodFiber, InsightKind.LowSugar, InsightKind.LowSodium, InsightKind.Unprocessed),
            good,
        )
        val bad = Scoring.insights(product(Nutrition(fiber = 1.0, sugar = 30.0, salt = 2.0, saturatedFat = 8.0), nova = 4))
        assertEquals(
            listOf(InsightKind.LowFiber, InsightKind.HighSugar, InsightKind.HighSodium, InsightKind.HighSatFat, InsightKind.UltraProcessed),
            bad,
        )
        assertTrue(bad.none { it.positive })
    }

    @Test fun `missing nutrients give no insight`() {
        assertTrue(Scoring.insights(product()).isEmpty())
    }

    // ---- Search filters ----

    private fun hit(source: String = OffParser.SOURCE, grade: String? = null, nova: Int? = null) =
        SearchHit("X", null, null, grade, nova, null, source)

    @Test fun `filters build Open Food Facts clauses`() {
        val f = SearchFilters(category = "en:yogurts", greekOnly = true, vegan = true, vegetarian = true, excludeAllergens = setOf("en:milk"))
        assertEquals(
            listOf("categories_tags:\"en:yogurts\"", "countries_tags:\"en:greece\"", "labels_tags:\"en:vegan\"", "-allergens_tags:\"en:milk\""),
            f.offQueryClauses(),
        )
        assertEquals(listOf("labels_tags:\"en:vegetarian\""), SearchFilters(vegetarian = true).offQueryClauses())
    }

    @Test fun `filters accept by grade and NOVA`() {
        val f = SearchFilters(grades = setOf("a", "b"), nova = setOf(1))
        assertTrue(f.accepts(hit(grade = "a", nova = 1)))
        assertFalse(f.accepts(hit(grade = "c", nova = 1)))
        assertFalse(f.accepts(hit(grade = "a", nova = 4)))
        assertTrue(f.isActive)
        assertFalse(f.needsOffTags)
    }

    @Test fun `tag filters hide sources that cannot apply them`() {
        val f = SearchFilters(greekOnly = true)
        assertTrue(f.accepts(hit()))
        assertFalse(f.accepts(hit(source = UsdaParser.SOURCE)))
        assertFalse(SearchFilters().isActive)
    }

    @Test fun `allergen alerts match declared tags only`() {
        val p = product(allergens = listOf("en:milk", "en:gluten"))
        assertEquals(listOf("en:milk"), p.matchingAllergens(setOf("en:milk", "en:peanuts")))
    }
}
