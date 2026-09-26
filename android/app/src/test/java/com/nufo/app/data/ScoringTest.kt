package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    @Test fun `perfect data scores 100 and is capped`() {
        val r = Scoring.nufoScore(Nutrition(protein = 20.0, fiber = 8.0), "a", 1)
        assertEquals(100, r.score)
    }

    @Test fun `worst case is floored at 0`() {
        val r = Scoring.nufoScore(Nutrition(sugar = 50.0, salt = 3.0, saturatedFat = 10.0), "e", 4)
        assertEquals(0, r.score) // 100 - 45 - 30 - 15 - 15 - 10 < 0
    }

    @Test fun `each rule applies its documented delta`() {
        assertEquals(95, Scoring.nufoScore(Nutrition(), "b", null).score)
        assertEquals(85, Scoring.nufoScore(Nutrition(), "c", null).score)
        assertEquals(70, Scoring.nufoScore(Nutrition(), "d", null).score)
        assertEquals(95, Scoring.nufoScore(Nutrition(), null, 2).score)
        assertEquals(85, Scoring.nufoScore(Nutrition(), null, 3).score)
        assertEquals(85, Scoring.nufoScore(Nutrition(sugar = 10.1), null, null).score)
        assertEquals(85, Scoring.nufoScore(Nutrition(salt = 1.01), null, null).score)
        assertEquals(90, Scoring.nufoScore(Nutrition(saturatedFat = 5.1), null, null).score)
    }

    @Test fun `thresholds are exclusive where the spec says greater-than`() {
        assertEquals(100, Scoring.nufoScore(Nutrition(sugar = 10.0, salt = 1.0, saturatedFat = 5.0), null, null).score)
    }

    @Test fun `missing values never change the score`() {
        val r = Scoring.nufoScore(Nutrition(), null, null)
        assertEquals(100, r.score)
        assertTrue(r.reasons.isEmpty())
    }

    @Test fun `reasons explain the score`() {
        val r = Scoring.nufoScore(Nutrition(salt = 2.0, protein = 12.0), "c", 4)
        assertEquals(listOf(Reason.NutriC, Reason.Nova4, Reason.HighProtein, Reason.HighSalt), r.reasons.map { it.reason })
        assertEquals(100 + r.reasons.sumOf { it.delta }, r.score)
    }

    @Test fun `verdict bands`() {
        assertEquals(Verdict.Excellent, Verdict.of(80))
        assertEquals(Verdict.Good, Verdict.of(79))
        assertEquals(Verdict.Fair, Verdict.of(40))
        assertEquals(Verdict.Poor, Verdict.of(20))
        assertEquals(Verdict.VeryPoor, Verdict.of(0))
    }

    @Test fun `FSA traffic lights use food thresholds`() {
        assertEquals(Level.Low, Scoring.sugarLevel(5.0))
        assertEquals(Level.Medium, Scoring.sugarLevel(5.1))
        assertEquals(Level.High, Scoring.sugarLevel(22.6))
        assertEquals(Level.High, Scoring.saltLevel(1.6))
        assertEquals(Level.Medium, Scoring.fatLevel(17.5))
        assertEquals(Level.High, Scoring.satFatLevel(5.1))
    }

    @Test fun `stub entries are not scored`() {
        fun product(n: Nutrition, nutri: String? = null, nova: Int? = null) = Product(
            barcode = "1", name = "X", brand = null, quantity = null, servingSize = null, servingGrams = null, imageUrl = null,
            ingredientsText = null, nutritionPer100g = n, nutritionPerServing = null, nutriscoreGrade = nutri, novaGroup = nova,
            ecoscoreGrade = null, nufoScore = 100, source = OffParser.SOURCE, lastUpdated = 0,
        )
        assertTrue(!Scoring.canScore(product(Nutrition(calories = 10.0))))
        assertTrue(Scoring.canScore(product(Nutrition(), nutri = "c")))
        assertTrue(Scoring.canScore(product(Nutrition(), nova = 1)))
        assertTrue(Scoring.canScore(product(Nutrition(calories = 50.0, fat = 1.0, sugar = 2.0, sodium = 0.1))))
    }
}
