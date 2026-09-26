package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealTest {
    private val answer = Json.parseToJsonElement(
        """{"meal_el":"Κοτόπουλο με γλυκοπατάτα","meal_en":"Chicken with sweet potato","items":[
          {"name_el":"Στήθος κοτόπουλο ψητό","name_en":"Grilled chicken breast","grams":150,"confidence":0.95,"source":"usda",
           "per100":{"kcal":176,"protein":30.5,"carbs":0,"fat":5.5,"fiber":0,"sugar":0,"salt":0.3}},
          {"name_el":"Γλυκοπατάτα ψητή","name_en":"Baked sweet potato","grams":150,"confidence":0.9,"source":"estimate",
           "per100":{"kcal":90,"protein":2,"carbs":20.7,"fat":0.1,"fiber":3.3,"sugar":6.5,"salt":0.1}},
          {"name_el":"Χαλασμένο","grams":0,"per100":{"kcal":1}}]}""",
    ).jsonObject

    @Test fun `parses items and drops unusable ones`() {
        val meal = MealParser.parse(answer)
        assertEquals(2, meal.items.size)
        assertEquals("Κοτόπουλο με γλυκοπατάτα", meal.name("el"))
        assertTrue(meal.items[0].fromUsda)
        assertEquals(0.12, meal.items[0].per100.sodium!!, 1e-9) // salt / 2.5
    }

    @Test fun `the plate is weighted by grams and served whole`() {
        val p = MealParser.parse(answer).toProduct("el", brand = null, now = 1)
        assertEquals(300.0, p.servingGrams!!, 0.0)
        assertEquals(133.0, p.nutritionPer100g.calories!!, 0.01) // (176 + 90) / 2
        assertEquals(listOf("Στήθος κοτόπουλο ψητό", "Γλυκοπατάτα ψητή"), p.recipe.map { it.name })
        assertEquals(50.0, p.recipe[0].grams, 0.01) // 150 of 300 g
        assertTrue(p.isDish && Scoring.canScore(p))
    }

    @Test fun `a nutrient missing for any item is not guessed`() {
        val json = Json.parseToJsonElement(
            """{"items":[{"name_en":"A","grams":100,"per100":{"kcal":100,"fiber":2}},{"name_en":"B","grams":100,"per100":{"kcal":50}}]}""",
        ).jsonObject
        val p = MealParser.parse(json).toProduct("en", null, 1)
        assertEquals(75.0, p.nutritionPer100g.calories!!, 0.0)
        assertNull(p.nutritionPer100g.fiber)
    }
}
