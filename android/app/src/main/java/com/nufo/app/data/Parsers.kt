package com.nufo.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.ZoneOffset

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
private fun JsonObject.tags(key: String) = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/** "en:sesame-seeds" -> "Sesame seeds". Last resort when no translated name exists. */
fun prettyTag(tag: String): String = tag.substringAfter(':').replace('-', ' ').replaceFirstChar { it.uppercase() }

/**
 * Energy in kcal, rejecting impossible values: nothing exceeds 900 kcal per 100 g (pure fat).
 * A kcal field above that is usually kJ typed into the wrong box, so the kJ field (÷ 4.184) is
 * used instead, or nothing. [perGrams] is null for per-serving values, which have no fixed ceiling.
 */
internal fun kcal(kcal: Double?, kj: Double?, perGrams: Double?): Double? {
    fun ok(v: Double) = v >= 0 && (perGrams == null || v <= 9.0 * perGrams)
    return kcal?.takeIf(::ok) ?: kj?.div(4.184)?.takeIf(::ok)
}

private fun grade(value: String?) = value?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' }

/** OFF sometimes HTML-escapes names ("Φέτα &quot;Ελαφρύ&quot;"). */
private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace("&apos;", "'")

object OffParser {
    const val SOURCE = "Open Food Facts"
    // EU "℮" estimated-quantity mark, which OFF often stores as a trailing " e".
    private val ESTIMATED_MARK = Regex("""\s*(℮|\be)\s*$""")

    const val FIELDS = "code,product_name,product_name_el,product_name_en,brands,quantity,serving_size,serving_quantity," +
        "nutriments,nutriscore_grade,nova_group,ecoscore_grade,ingredients_text,ingredients_text_el,ingredients_text_en," +
        "allergens_tags,additives_tags,image_url,categories_tags,labels_tags,countries_tags,completeness,last_modified_t"
    const val SEARCH_FIELDS = "code,product_name,product_name_el,product_name_en,brands,image_url,nutriscore_grade,nova_group,nutriments,countries_tags"

    private fun nutrition(n: JsonObject, suffix: String): Nutrition? {
        fun v(key: String) = n.num("${key}_$suffix")
        val result = Nutrition(
            calories = kcal(v("energy-kcal"), v("energy"), if (suffix == "100g") 100.0 else null),
            protein = v("proteins"),
            carbs = v("carbohydrates"),
            fat = v("fat"),
            saturatedFat = v("saturated-fat"),
            fiber = v("fiber"),
            sugar = v("sugars"),
            sodium = v("sodium"),
            salt = v("salt"),
            vitamins = Micronutrients.vitamins.keys.mapNotNull { k -> v(k)?.let { k to it } }.toMap(),
            minerals = Micronutrients.minerals.keys.mapNotNull { k -> v(k)?.let { k to it } }.toMap(),
        )
        return result.takeUnless { it == Nutrition() }
    }

    /** Name in the app language when OFF has one, else the name in the product's main language. */
    private fun localized(p: JsonObject, field: String, lang: String) =
        (p.str("${field}_$lang") ?: p.str(field) ?: p.str("${field}_en") ?: p.str("${field}_el"))?.let(::unescape)

    /** Returns null when OFF reports the product as missing (status != 1). */
    fun parseProductResponse(root: JsonObject, barcode: String, lang: String = "en"): Product? {
        if (root.int("status") != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        return parseProduct(product, barcode, lang)
    }

    fun parseProduct(p: JsonObject, barcode: String?, lang: String = "en"): Product {
        val nutriments = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val per100 = nutrition(nutriments, "100g") ?: Nutrition()
        val servingGrams = p.num("serving_quantity")?.takeIf { it > 0 }
        val perServing = nutrition(nutriments, "serving")
            ?: servingGrams?.takeIf { per100 != Nutrition() }?.let { per100.scaled(it / 100) }
        val nutri = grade(p.str("nutriscore_grade"))
        val nova = p.int("nova_group")?.takeIf { it in 1..4 }
        return Product(
            barcode = barcode ?: p.str("code"),
            name = localized(p, "product_name", lang) ?: "",
            brand = p.str("brands")?.split(',')?.first()?.trim(),
            quantity = p.str("quantity")?.replace(ESTIMATED_MARK, "")?.trim()?.ifEmpty { null },
            servingSize = p.str("serving_size"),
            servingGrams = servingGrams,
            imageUrl = p.str("image_url"),
            ingredientsText = localized(p, "ingredients_text", lang)?.replace("_", ""), // OFF wraps allergens in _underscores_
            allergenTags = p.tags("allergens_tags"),
            additiveTags = p.tags("additives_tags"),
            categoryTags = p.tags("categories_tags"),
            labelTags = p.tags("labels_tags"),
            nutritionPer100g = per100,
            nutritionPerServing = perServing,
            nutriscoreGrade = nutri,
            novaGroup = nova,
            ecoscoreGrade = grade(p.str("ecoscore_grade")),
            nufoScore = Scoring.nufoScore(per100, nutri, nova).score,
            source = SOURCE,
            lastUpdated = (p.num("last_modified_t")?.toLong() ?: 0L) * 1000,
            completeness = p.num("completeness")?.coerceIn(0.0, 1.0),
            soldInGreece = "en:greece" in p.tags("countries_tags"),
        )
    }

    /** Legacy search.pl returns "products"; Search-a-licious returns "hits" with brands as an array. */
    fun parseSearch(root: JsonObject, lang: String = "en"): List<SearchHit> =
        ((root["hits"] ?: root["products"]) as? JsonArray).orEmpty().mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val name = localized(p, "product_name", lang) ?: return@mapNotNull null
            SearchHit(
                name = name,
                brand = p.tags("brands").firstOrNull()?.trim() ?: p.str("brands")?.split(',')?.first()?.trim(),
                imageUrl = p.str("image_url"),
                nutriscoreGrade = grade(p.str("nutriscore_grade")),
                novaGroup = p.int("nova_group")?.takeIf { it in 1..4 },
                caloriesPer100g = (p["nutriments"] as? JsonObject)?.let { kcal(it.num("energy-kcal_100g"), it.num("energy_100g"), 100.0) },
                source = SOURCE,
                barcode = p.str("code"),
                soldInGreece = "en:greece" in p.tags("countries_tags"),
            )
        }

    /** Taxonomy response `{"en:milk":{"name":{"el":"γάλα"}}}` -> tag id to translated name. */
    fun parseTaxonomy(root: JsonObject, lang: String): Map<String, String> =
        root.mapNotNull { (tag, v) ->
            val name = ((v as? JsonObject)?.get("name") as? JsonObject)?.str(lang) ?: return@mapNotNull null
            tag to name.replaceFirstChar { it.uppercase() }
        }.toMap()
}

object UsdaParser {
    const val SOURCE = "USDA FoodData Central"
    private const val MG = 0.001
    private const val UG = 0.000001

    fun parseSearch(root: JsonObject): List<SearchHit> =
        (root["foods"] as? JsonArray).orEmpty().mapNotNull { el ->
            val product = (el as? JsonObject)?.let(::parseFood) ?: return@mapNotNull null
            SearchHit(
                name = product.name, brand = product.brand, imageUrl = null,
                nutriscoreGrade = null, novaGroup = null,
                caloriesPer100g = product.nutritionPer100g.calories,
                source = SOURCE, barcode = product.barcode, product = product,
            )
        }

    /** Values from FDC search are per 100 g; nutrients are keyed by legacy nutrient number. */
    fun parseFood(f: JsonObject): Product? {
        val name = f.str("description") ?: return null
        val nutrients: Map<String, Double> = (f["foodNutrients"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val number = o.str("nutrientNumber") ?: return@mapNotNull null
            val value = o.num("value") ?: return@mapNotNull null
            number to value
        }.toMap()
        fun g(n: String, factor: Double = 1.0) = nutrients[n]?.times(factor)
        val sodium = g("307", MG)
        val per100 = Nutrition(
            calories = g("208") ?: g("957") ?: g("958"),
            protein = g("203"), carbs = g("205"), fat = g("204"), saturatedFat = g("606"),
            fiber = g("291"), sugar = g("269"),
            sodium = sodium,
            salt = sodium?.times(2.5), // EU labelling convention: salt = sodium x 2.5
            vitamins = listOfNotNull(
                g("320", UG)?.let { "vitamin-a" to it }, g("401", MG)?.let { "vitamin-c" to it },
                g("328", UG)?.let { "vitamin-d" to it }, g("323", MG)?.let { "vitamin-e" to it },
                g("415", MG)?.let { "vitamin-b6" to it }, g("418", UG)?.let { "vitamin-b12" to it },
            ).toMap(),
            minerals = listOfNotNull(
                g("301", MG)?.let { "calcium" to it }, g("303", MG)?.let { "iron" to it },
                g("304", MG)?.let { "magnesium" to it }, g("306", MG)?.let { "potassium" to it },
                g("309", MG)?.let { "zinc" to it },
            ).toMap(),
        )
        val servingGrams = f.num("servingSize")?.takeIf { f.str("servingSizeUnit")?.lowercase() in setOf("g", "grm") }
        val date = f.str("publishedDate") ?: f.str("modifiedDate")
        return Product(
            barcode = f.str("gtinUpc"),
            name = name.lowercase().replaceFirstChar { it.uppercase() },
            brand = f.str("brandOwner") ?: f.str("brandName"),
            quantity = null,
            servingSize = servingGrams?.let { "${it.toInt()} g" } ?: f.str("householdServingFullText"),
            servingGrams = servingGrams,
            imageUrl = null,
            ingredientsText = f.str("ingredients"),
            nutritionPer100g = per100,
            nutritionPerServing = servingGrams?.let { per100.scaled(it / 100) },
            nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null,
            nufoScore = Scoring.nufoScore(per100, null, null).score,
            source = SOURCE,
            lastUpdated = date?.let {
                runCatching { LocalDate.parse(it.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            } ?: 0L,
        )
    }
}

object PricesParser {
    const val SOURCE = "Open Prices"

    /** Latest reports, Greek stores first; at most [limit]. */
    fun parse(root: JsonObject, limit: Int = 3): List<PriceReport> =
        (root["items"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val price = o.num("price")?.takeIf { it > 0 } ?: return@mapNotNull null
            val loc = o["location"] as? JsonObject
            PriceReport(
                price = price,
                currency = o.str("currency") ?: return@mapNotNull null,
                date = o.str("date") ?: return@mapNotNull null,
                store = loc?.str("osm_name"),
                city = loc?.str("osm_address_city"),
                country = loc?.str("osm_address_country"),
            )
        }.sortedByDescending { it.inGreece }.take(limit)
}

object UpcItemDbParser {
    const val SOURCE = "UPCitemdb"

    fun parse(root: JsonObject): Identified? {
        val item = (root["items"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val title = item.str("title") ?: return null
        return Identified(
            name = title,
            brand = item.str("brand"),
            // Their image links point at third-party shops; only https ones are safe to load.
            imageUrl = item.tags("images").firstOrNull { it.startsWith("https://") },
            source = SOURCE,
        )
    }
}