package com.nufo.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.nufo.app.R
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generic nutrition for dishes the photo classifier recognises, from USDA FNDDS survey foods (public domain),
 * bundled in assets/food/dishes.json by scripts/build_dishes.py. Works offline and needs no API quota.
 */
class DishTable(private val context: Context) {
    private val entries: Map<String, JsonObject> by lazy {
        val text = context.assets.open("food/dishes.json").bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonObject }
    }

    /**
     * The dish under the user's own name for it, with its typical recipe in [lang]. The USDA entry it comes from is
     * kept visible as the brand line; a Greek home recipe computed from USDA ingredients says so instead.
     */
    fun product(key: String, name: String, lang: String): Product? {
        val entry = entries[key] ?: return null
        val description = (entry["description"] as? JsonPrimitive)?.content
        val recipe = entry["ingredients"]?.jsonArray.orEmpty().mapNotNull { element ->
            val part = element.jsonObject
            val partName = part[if (lang == "el") "el" else "en"]?.jsonPrimitive?.content
            val grams = part["g"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (partName != null && grams != null) RecipePart(partName, grams) else null
        }
        val brand = when {
            description == null -> null
            description.endsWith("typical Greek recipe") -> context.getString(R.string.dish_typical_greek)
            else -> "USDA · $description"
        }
        return UsdaParser.parseFood(entry)?.copy(
            name = name, brand = brand, isDish = true, recipe = recipe,
            ingredientsText = recipe.takeIf { it.isNotEmpty() }?.joinToString { it.name },
        )
    }
}