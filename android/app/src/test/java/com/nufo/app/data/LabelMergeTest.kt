package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMergeTest {
    private fun product(n: Nutrition, nutri: String? = null, nova: Int? = null, name: String = "X") = Product(
        barcode = "1", name = name, brand = null, quantity = null, servingSize = null, servingGrams = null, imageUrl = null,
        ingredientsText = null, nutritionPer100g = n, nutritionPerServing = null, nutriscoreGrade = nutri, novaGroup = nova,
        ecoscoreGrade = null, nufoScore = 100, source = OffParser.SOURCE, lastUpdated = 5,
    )

    @Test fun `a later database answer keeps its own values and takes the label's for the rest`() {
        val labelled = product(Nutrition(calories = 440.0, fat = 15.0), nutri = "c", nova = 4)
            .copy(filledFromLabel = true, nutriscoreFromLabel = true, novaFromLabel = true)
        val fresh = product(Nutrition(calories = 450.0), nutri = "b")
        val m = fresh.fillMissingFrom(labelled)
        assertEquals(450.0, m.nutritionPer100g.calories!!, 0.0)
        assertEquals(15.0, m.nutritionPer100g.fat!!, 0.0)
        assertEquals("b", m.nutriscoreGrade)
        assertFalse(m.nutriscoreFromLabel)
        assertEquals(4, m.novaGroup)
        assertTrue(m.novaFromLabel && m.filledFromLabel)
    }

    @Test fun `nothing to fill leaves the product unmarked`() {
        val full = product(Nutrition(calories = 1.0, protein = 1.0, carbs = 1.0, fat = 1.0), nutri = "a", nova = 1)
        val m = full.fillMissingFrom(full.copy(filledFromLabel = true))
        assertFalse(m.filledFromLabel)
    }
}
