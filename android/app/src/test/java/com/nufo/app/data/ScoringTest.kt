package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private fun product(
        n: Nutrition = Nutrition(),
        nutri: String? = null,
        nova: Int? = null,
        categories: List<String> = emptyList(),
    ) = Product(
        barcode = "1", name = "X", brand = null, quantity = null, servingSize = null, servingGrams = null, imageUrl = null,
        ingredientsText = null, categoryTags = categories, nutritionPer100g = n, nutritionPerServing = null,
        nutriscoreGrade = nutri, novaGroup = nova, ecoscoreGrade = null, nufoScore = 0, source = OffParser.SOURCE, lastUpdated = 0,
    )

    private fun scored(p: Product) = requireNotNull(Scoring.score(p)) { "expected a score" }

    private val fullNutrients = Nutrition(calories = 100.0, fat = 1.0, saturatedFat = 0.5, sugar = 2.0, salt = 0.1, protein = 3.0)

    // ---- Graded products: Nutri-Score is the base, NOVA adjusts, nutrients are NOT counted again ----

    @Test fun `grade A unprocessed scores 100`() {
        // Arrange
        val p = product(fullNutrients, nutri = "a", nova = 1)
        // Act
        val r = scored(p)
        // Assert
        assertEquals(100, r.score)
    }

    @Test fun `each grade maps to its documented base`() {
        assertEquals(100, scored(product(nutri = "a")).score)
        assertEquals(85, scored(product(nutri = "b")).score)
        assertEquals(65, scored(product(nutri = "c")).score)
        assertEquals(45, scored(product(nutri = "d")).score)
        assertEquals(25, scored(product(nutri = "e")).score)
    }

    @Test fun `NOVA adjusts a graded score`() {
        assertEquals(85, scored(product(nutri = "b", nova = 1)).score)
        assertEquals(80, scored(product(nutri = "b", nova = 2)).score)
        assertEquals(75, scored(product(nutri = "b", nova = 3)).score)
        assertEquals(70, scored(product(nutri = "b", nova = 4)).score)
    }

    @Test fun `graded products are not penalised twice for nutrients the grade already includes`() {
        // Arrange: grade E already reflects the sugar, salt and saturated fat.
        val p = product(Nutrition(calories = 500.0, sugar = 50.0, salt = 3.0, saturatedFat = 10.0, fat = 30.0, protein = 5.0), nutri = "e", nova = 4)
        // Act
        val r = scored(p)
        // Assert
        assertEquals(10, r.score)
        assertEquals(listOf(Reason.NutriE, Reason.Nova4), r.reasons.map { it.reason })
    }

    @Test fun `the order of grades is never inverted by NOVA`() {
        // Worst processing on a better grade still beats the best processing on the next grade down... or ties.
        listOf("a", "b", "c", "d").zip(listOf("b", "c", "d", "e")).forEach { (better, worse) ->
            val betterWorst = scored(product(nutri = better, nova = 4)).score
            val worseBest = scored(product(nutri = worse, nova = 1)).score
            assertTrue("$better/4 ($betterWorst) vs $worse/1 ($worseBest)", betterWorst >= worseBest)
        }
    }

    // ---- Ungraded products: nutrients on the same FSA table as the traffic lights ----

    @Test fun `ungraded thresholds match the traffic lights`() {
        // Sugar 15 g is "medium" on the label dots, so it must not be treated as "high" in the score.
        val medium = scored(product(fullNutrients.copy(sugar = 15.0)))
        assertEquals(listOf(Reason.MediumSugar), medium.reasons.map { it.reason })
        val high = scored(product(fullNutrients.copy(sugar = 22.6)))
        assertEquals(listOf(Reason.HighSugar), high.reasons.map { it.reason })
        assertEquals(Level.High, Scoring.sugarLevel(22.6))
        assertEquals(Level.Medium, Scoring.sugarLevel(15.0))
    }

    @Test fun `ungraded high nutrients each apply their delta`() {
        val p = product(Nutrition(calories = 300.0, fat = 20.0, saturatedFat = 6.0, sugar = 25.0, salt = 2.0, protein = 2.0))
        val r = scored(p)
        assertEquals(listOf(Reason.HighFat, Reason.HighSatFat, Reason.HighSugar, Reason.HighSalt), r.reasons.map { it.reason })
        assertEquals(100 - 10 - 20 - 20 - 20, r.score)
    }

    @Test fun `protein and fibre only offset penalties on ungraded products`() {
        val p = product(Nutrition(calories = 300.0, fat = 2.0, saturatedFat = 1.0, sugar = 25.0, salt = 0.1, protein = 12.0, fiber = 7.0))
        val r = scored(p)
        assertEquals(100 - 20 + 5 + 5, r.score)
        assertTrue(Reason.HighProtein in r.reasons.map { it.reason })
    }

    @Test fun `score is capped at 100 and floored at 0`() {
        assertEquals(100, scored(product(fullNutrients.copy(protein = 25.0, fiber = 10.0))).score)
        val worst = scored(product(Nutrition(calories = 500.0, fat = 40.0, saturatedFat = 20.0, sugar = 60.0, salt = 5.0), nova = 4))
        assertEquals(15, worst.score) // 100 - 10 - 20 - 20 - 20 - 15
    }

    @Test fun `drinks use the FSA drink thresholds`() {
        // 8 g sugar per 100 ml: medium for food, high for a drink.
        val drink = product(fullNutrients.copy(sugar = 12.0), categories = listOf("en:beverages", "en:sodas"))
        val food = product(fullNutrients.copy(sugar = 12.0))
        assertEquals(listOf(Reason.HighSugar), scored(drink).reasons.map { it.reason })
        assertEquals(listOf(Reason.MediumSugar), scored(food).reasons.map { it.reason })
        assertEquals(Level.High, Scoring.sugarLevel(12.0, drink = true))
    }

    @Test fun `reasons always sum to the score`() {
        listOf(
            product(nutri = "c", nova = 3),
            product(Nutrition(calories = 300.0, fat = 20.0, saturatedFat = 6.0, sugar = 25.0, salt = 2.0, protein = 2.0)),
            product(fullNutrients.copy(sugar = 15.0, protein = 15.0)),
        ).forEach { p ->
            val r = scored(p)
            assertEquals((100 + r.reasons.sumOf { it.delta }).coerceIn(0, 100), r.score)
        }
    }

    // ---- Evidence ----

    @Test fun `stub entries are not scored`() {
        assertNull(Scoring.score(product(Nutrition(calories = 10.0))))
        assertFalse(Scoring.canScore(product(Nutrition(calories = 10.0))))
    }

    @Test fun `NOVA alone is not enough evidence for a score`() {
        assertNull(Scoring.score(product(nova = 1)))
    }

    @Test fun `a grade alone or four key nutrients is enough`() {
        assertTrue(Scoring.canScore(product(nutri = "c")))
        assertTrue(Scoring.canScore(product(Nutrition(calories = 50.0, fat = 1.0, sugar = 2.0, sodium = 0.1))))
    }

    @Test fun `the stored score is ignored so old history follows current rules`() {
        val stale = product(nutri = "e").copy(nufoScore = 100)
        assertEquals(25, scored(stale).score)
    }

    // ---- Verdicts and traffic lights ----

    @Test fun `verdict bands`() {
        assertEquals(Verdict.Excellent, Verdict.of(80))
        assertEquals(Verdict.Good, Verdict.of(79))
        assertEquals(Verdict.Fair, Verdict.of(40))
        assertEquals(Verdict.Poor, Verdict.of(20))
        assertEquals(Verdict.VeryPoor, Verdict.of(0))
    }

    @Test fun `FSA traffic lights use food thresholds by default`() {
        assertEquals(Level.Low, Scoring.sugarLevel(5.0))
        assertEquals(Level.Medium, Scoring.sugarLevel(5.1))
        assertEquals(Level.High, Scoring.sugarLevel(22.6))
        assertEquals(Level.High, Scoring.saltLevel(1.6))
        assertEquals(Level.Medium, Scoring.fatLevel(17.5))
        assertEquals(Level.High, Scoring.satFatLevel(5.1))
    }

    @Test fun `drink detection reads category tags`() {
        assertTrue(Scoring.isDrink(product(categories = listOf("en:beverages"))))
        assertTrue(Scoring.isDrink(product(categories = listOf("en:plant-based-beverages"))))
        assertFalse(Scoring.isDrink(product(categories = listOf("en:yogurts"))))
    }
}
