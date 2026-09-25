package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.classLoader!!.getResource(name)!!.readText()).jsonObject

    @Test fun `parses a real Open Food Facts product`() {
        val p = OffParser.parseProductResponse(fixture("off_nutella.json"), "3017620422003")!!
        assertEquals("Nutella", p.name)
        assertEquals("Nutella", p.brand)
        assertEquals("e", p.nutriscoreGrade)
        assertEquals(4, p.novaGroup)
        assertNull("'unknown' eco-score must not be shown as a grade", p.ecoscoreGrade)
        assertEquals(539.0, p.nutritionPer100g.calories!!, 0.5)
        assertEquals(56.3, p.nutritionPer100g.sugar!!, 0.1)
        assertEquals(listOf("en:milk", "en:nuts", "en:soybeans"), p.allergenTags)
        assertEquals("Milk", p.tagName("en:milk", "en"))
        assertEquals("Γάλα", p.tagName("en:milk", "el"))
        assertTrue(p.lastUpdated > 0)
        assertEquals(OffParser.SOURCE, p.source)
    }

    @Test fun `estimated-quantity mark is stripped from quantity`() {
        fun q(raw: String) = OffParser.parseProductResponse(Json.parseToJsonElement(
            """{"status":1,"product":{"product_name":"X","quantity":"$raw"}}""").jsonObject, "1")!!.quantity
        assertEquals("400 g", q("400 g e"))
        assertEquals("1 l", q("1 l ℮"))
        assertEquals("250 g", q("250 g"))
        assertEquals("Large", q("Large"))
    }

    @Test fun `missing OFF product returns null`() {
        val root = Json.parseToJsonElement("""{"status":0,"status_verbose":"product not found"}""").jsonObject
        assertNull(OffParser.parseProductResponse(root, "000"))
    }

    @Test fun `absent nutrients stay null instead of zero`() {
        val root = Json.parseToJsonElement("""{"status":1,"product":{"product_name":"Water","nutriments":{"energy-kcal_100g":0}}}""").jsonObject
        val p = OffParser.parseProductResponse(root, "1")!!
        assertEquals(0.0, p.nutritionPer100g.calories!!, 0.0)
        assertNull(p.nutritionPer100g.protein)
        assertNull(p.nutritionPerServing)
    }

    @Test fun `serving values are derived only from a stated serving weight`() {
        val root = Json.parseToJsonElement("""{"status":1,"product":{"product_name":"Cola","serving_quantity":330,"nutriments":{"sugars_100g":10.6}}}""").jsonObject
        val p = OffParser.parseProductResponse(root, "2")!!
        assertEquals(34.98, p.nutritionPerServing!!.sugar!!, 0.01)
    }

    @Test fun `parses a real USDA search response per 100 g in grams`() {
        val hits = UsdaParser.parseSearch(fixture("usda_banana.json"))
        assertTrue(hits.isNotEmpty())
        val n = hits.first().product!!.nutritionPer100g
        assertEquals(346.0, n.calories!!, 0.1)
        assertEquals(88.3, n.carbs!!, 0.1)
        assertEquals(0.003, n.sodium!!, 1e-6) // 3 mg
        assertEquals(0.007, n.vitamins["vitamin-c"]!!, 1e-6) // 7 mg
        assertEquals(UsdaParser.SOURCE, hits.first().source)
    }

    @Test fun `parses Search-a-licious hits with array brands`() {
        val hits = OffParser.parseSearch(fixture("off_search_hits.json"))
        assertEquals(3, hits.size)
        assertEquals("Stonyfield Organic", hits[0].brand)
        assertEquals("a", hits[0].nutriscoreGrade)
        assertEquals(3, hits[0].novaGroup)
        assertEquals("0052159703356", hits[0].barcode)
    }

    @Test fun `filters become Lucene clauses`() {
        val f = SearchFilters(category = "en:dairies", vegan = true, excludeAllergens = setOf("en:milk"))
        assertEquals(listOf("categories_tags:\"en:dairies\"", "labels_tags:\"en:vegan\"", "-allergens_tags:\"en:milk\""), f.offQueryClauses())
    }

    @Test fun `prefers the product name in the app language`() {
        val json = """{"status":1,"product":{"product_name":"FAGE Total 5%","product_name_el":"Γιαούρτι FAGE Total 5%",
            "ingredients_text":"Lait, crème","ingredients_text_el":"Γάλα, κρέμα _γάλακτος_","countries_tags":["en:france","en:greece"],"completeness":0.98}}"""
        val root = Json.parseToJsonElement(json).jsonObject
        val el = OffParser.parseProductResponse(root, "5201054017388", "el")!!
        assertEquals("Γιαούρτι FAGE Total 5%", el.name)
        assertEquals("Γάλα, κρέμα γάλακτος", el.ingredientsText)
        assertTrue(el.soldInGreece)
        assertEquals(0.98, el.completeness!!, 1e-9)
        val en = OffParser.parseProductResponse(root, "5201054017388", "en")!!
        assertEquals("FAGE Total 5%", en.name) // no _en name: falls back to the main name, never to Greek first
        assertEquals("Lait, crème", en.ingredientsText)
    }

    @Test fun `unescapes HTML entities in names`() {
        val root = Json.parseToJsonElement("""{"status":1,"product":{"product_name":"Φέτα &quot;Ελαφρύ&quot;"}}""").jsonObject
        assertEquals("Φέτα \"Ελαφρύ\"", OffParser.parseProductResponse(root, "1")!!.name)
    }

    @Test fun `parses taxonomy translations`() {
        val root = Json.parseToJsonElement("""{"en:milk":{"name":{"el":"γάλα"}},"en:organic":{"name":{}}}""").jsonObject
        assertEquals(mapOf("en:milk" to "Γάλα"), OffParser.parseTaxonomy(root, "el"))
    }

    @Test fun `rejects impossible energy values`() {
        assertEquals(263.0, kcal(263.0, 1100.0, 100.0)!!, 0.0)
        assertEquals(1100 / 4.184, kcal(1291.0, 1100.0, 100.0)!!, 0.01) // kJ typed into kcal: use the kJ field
        assertNull(kcal(1291.0, 5400.0, 100.0)) // both impossible: show nothing rather than a wrong number
        assertEquals(1291.0, kcal(1291.0, null, null)!!, 0.0) // per serving has no fixed ceiling
    }

    @Test fun `prices put Greek stores first and drop invalid rows`() {
        val root = Json.parseToJsonElement("""{"items":[
            {"price":4.68,"currency":"EUR","date":"2026-09-13","location":{"osm_name":"Carrefour","osm_address_city":"Lyon","osm_address_country":"France"}},
            {"price":0,"currency":"EUR","date":"2026-09-12"},
            {"price":3.99,"currency":"EUR","date":"2026-09-01","location":{"osm_name":"Σκλαβενίτης","osm_address_city":"Αθήνα","osm_address_country":"Ελλάδα"}}
        ]}""").jsonObject
        val prices = PricesParser.parse(root)
        assertEquals(2, prices.size)
        assertEquals("Σκλαβενίτης", prices[0].store)
        assertTrue(prices[0].inGreece)
    }

    @Test fun `upcitemdb identifies name and only keeps https images`() {
        val root = Json.parseToJsonElement("""{"code":"OK","items":[{"title":"Coca-Cola Bottle, 2 Liters","brand":"Coca-Cola",
            "images":["http://insecure.example/a.jpg","https://secure.example/b.jpg"]}]}""").jsonObject
        val id = UpcItemDbParser.parse(root)!!
        assertEquals("Coca-Cola Bottle, 2 Liters", id.name)
        assertEquals("https://secure.example/b.jpg", id.imageUrl)
        assertNull(UpcItemDbParser.parse(Json.parseToJsonElement("""{"code":"OK","items":[]}""").jsonObject))
    }

    @Test fun `image urls switch between OFF sizes`() {
        val u = "https://images.openfoodfacts.org/images/products/520/105/401/7388/front_fr.9.400.jpg"
        assertEquals(u.replace(".400.jpg", ".full.jpg"), offImage(u, "full"))
        assertEquals(u.replace(".400.jpg", ".200.jpg"), offImage(u, "200"))
        assertEquals("https://other.example/x.png", offImage("https://other.example/x.png", "full"))
        assertNull(offImage(null, "full"))
    }

    @Test fun `pretty tags`() {
        assertEquals("Soybeans", prettyTag("en:soybeans"))
        assertEquals("Sesame seeds", prettyTag("en:sesame-seeds"))
    }
}