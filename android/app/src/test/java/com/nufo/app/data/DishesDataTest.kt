package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the bundled dish table (assets/food/dishes.json) that photographed plates are answered from. */
class DishesDataTest {
    private val dishes = Json.parseToJsonElement(File("src/main/assets/food/dishes.json").readText()).jsonObject

    @Test fun `every dish parses with calories and a typical serving`() {
        dishes.forEach { (key, entry) ->
            val p = UsdaParser.parseFood(entry.jsonObject)
            assertTrue("$key does not parse", p != null)
            assertTrue("$key has no calories", p!!.nutritionPer100g.calories != null)
            assertTrue("$key has no serving", (p.servingGrams ?: 0.0) > 0)
        }
    }

    @Test fun `recipes are grams per 100 g with Greek names`() {
        dishes.forEach { (key, entry) ->
            val parts = entry.jsonObject["ingredients"]?.jsonArray ?: return@forEach
            val total = parts.sumOf { it.jsonObject["g"]!!.jsonPrimitive.content.toDouble() }
            // Water is not listed, so soups and pilaf add up to well under 100 g.
            assertTrue("$key recipe adds up to $total g per 100 g", total in 20.0..100.5)
            parts.forEach { assertTrue("$key has an untranslated ingredient", it.jsonObject["el"]!!.jsonPrimitive.content.any { c -> c in 'Α'..'ω' }) }
        }
    }

    @Test fun `Greek salad is the Greek recipe, dressed with olive oil`() {
        val salad = dishes["Greek salad"]!!.jsonObject
        val kcal = UsdaParser.parseFood(salad)!!.nutritionPer100g.calories!!
        assertTrue("horiatiki with feta and olive oil is ~90-130 kcal per 100 g, got $kcal", kcal in 85.0..135.0)
        val names = salad["ingredients"]!!.jsonArray.map { it.jsonObject["en"]!!.jsonPrimitive.content }
        assertTrue("Olive oil" in names && "Feta" in names && "Romaine lettuce" !in names)
    }
}
