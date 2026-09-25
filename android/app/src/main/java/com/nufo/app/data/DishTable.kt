package com.nufo.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Generic nutrition for dishes the photo classifier recognises, from USDA FNDDS survey foods (public domain),
 * bundled in assets/food/dishes.json by scripts/build_dishes.py. Works offline and needs no API quota.
 */
class DishTable(private val context: Context) {
    private val entries: Map<String, JsonObject> by lazy {
        val text = context.assets.open("food/dishes.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonObject }
    }

    /** The dish under the user's own name for it; the USDA entry it comes from is kept visible as the brand line. */
    fun product(key: String, name: String): Product? {
        val entry = entries[key] ?: return null
        val usdaName = (entry["description"] as? JsonPrimitive)?.content
        return UsdaParser.parseFood(entry)?.copy(name = name, brand = usdaName?.let { "USDA · $it" })
    }
}