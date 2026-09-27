package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelTest {
    private val answer = Json.parseToJsonElement(
        """{"readable":true,"confidence":1,"issues":[],"per100":{"energy_kj":1823,"energy_kcal":440,"fat":15,"saturated_fat":4.1,
           "carbohydrates":63,"sugars":19,"fiber":9.3,"protein":6.3,"salt":0.03,"sodium":null},"serving_size_g":45,
           "ingredients_text":"Νιφάδες βρώμης, ζάχαρη, άρωμα","product_name":"Crunchy","nutriscore":{"grade":"c","score":9},
           "nova":{"group":4,"markers":["αρωμα"]},"contributed":false}""",
    ).jsonObject

    private fun product(n: Nutrition, nutri: String? = null, nova: Int? = null) = Product(
        barcode = "5201024865193", name = "Crunchy cereal", brand = "Brand", quantity = null, servingSize = null, servingGrams = null,
        imageUrl = null, ingredientsText = null, nutritionPer100g = n, nutritionPerServing = null, nutriscoreGrade = nutri,
        novaGroup = nova, ecoscoreGrade = null, nufoScore = 100, source = OffParser.SOURCE, lastUpdated = 0,
    )

    @Test fun `parses the label reader answer`() {
        val l = LabelParser.parse(answer)!!
        assertEquals(440.0, l.per100.calories!!, 0.0)
        assertEquals(0.012, l.per100.sodium!!, 1e-9) // from salt
        assertEquals("c", l.nutriscoreGrade)
        assertEquals(4, l.novaGroup)
        assertEquals(45.0, l.servingGrams!!, 0.0)
    }

    @Test fun `an unreadable photo gives nothing`() {
        assertNull(LabelParser.parse(Json.parseToJsonElement("""{"readable":false}""").jsonObject))
    }

    @Test fun `the database's own values always win over the label`() {
        val p = product(Nutrition(calories = 450.0), nutri = "b").withLabel(LabelParser.parse(answer)!!, now = 1)
        assertEquals(450.0, p.nutritionPer100g.calories!!, 0.0)   // kept
        assertEquals(15.0, p.nutritionPer100g.fat!!, 0.0)          // filled
        assertEquals("b", p.nutriscoreGrade)                       // kept
        assertFalse(p.nutriscoreFromLabel)
        assertEquals(4, p.novaGroup)                               // filled
        assertTrue(p.novaFromLabel && p.filledFromLabel)
        assertEquals(19.0 * 0.45, p.nutritionPerServing!!.sugar!!, 1e-9)
        assertFalse(p.missingLabelFacts())
    }

    @Test fun `an unknown barcode becomes a product from its label`() {
        val p = LabelParser.parse(answer)!!.toProduct("5201024865193", identified = null, now = 1)
        assertEquals("Crunchy", p.name)
        assertEquals(LABEL_SOURCE, p.source)
        assertTrue(p.nutriscoreFromLabel && Scoring.canScore(p))
    }

    @Test fun `a product lacking grades or macros asks for the label`() {
        assertTrue(product(Nutrition(calories = 100.0)).missingLabelFacts())
        assertFalse(product(Nutrition(calories = 1.0, protein = 1.0, carbs = 1.0, fat = 1.0), "a", 1).missingLabelFacts())
    }
}
