package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forty checks of the smart search: understanding what was typed, then ranking what came back. */
class SmartSearchTest {
    private val off = OffParser.SOURCE
    private val usda = UsdaParser.SOURCE
    private fun hit(name: String, brand: String? = null, source: String = off, gr: Boolean = false, complete: Boolean = true) =
        SearchHit(name, brand, if (complete) "img" else null, if (complete) "b" else null, null, if (complete) 100.0 else null, source, soldInGreece = gr)

    private fun q(s: String) = SmartSearch.understand(s)
    private fun forms(s: String) = q(s).terms.flatMap { it.forms }.toSet()
    private fun top(query: String, vararg hits: SearchHit) = SmartSearch.rank(hits.toList(), q(query)).hits.first().name

    // ---- greeklish ----
    @Test fun `01 greeklish gala means milk`() { assertTrue("γαλα" in forms("gala")); assertTrue("milk" in forms("gala")) }
    @Test fun `02 greeklish giaourti means yogurt`() { assertTrue("yogurt" in forms("giaourti")) }
    @Test fun `03 greeklish psomi finds bread despite o for omega`() { assertTrue("bread" in forms("psomi")) }
    @Test fun `04 greeklish kotopoulo means chicken`() { assertTrue("chicken" in forms("kotopoulo")) }
    @Test fun `05 greeklish tzatziki and souvlaki`() { assertTrue("tzatziki" in forms("tzatziki")); assertTrue("souvlaki" in forms("souvlaki")) }
    @Test fun `06 greeklish is shown back in Greek`() { assertEquals("γάλα", q("gala").correction) }
    @Test fun `07 greeklish with a typo still lands`() { assertTrue("moussaka" in forms("moussakas")) }

    // ---- Greek typos and accents ----
    @Test fun `08 eta for iota typo`() { assertTrue("yogurt" in forms("γιαουρτη")); assertEquals("γιαούρτι", q("γιαουρτη").correction) }
    @Test fun `09 omega for omicron typo`() { assertTrue("bread" in forms("ψομί")) }
    @Test fun `10 missing letter typo`() { assertTrue("olive oil" in forms("ελαιολάδο") || "oil" in forms("ελαιολαδο")) }
    @Test fun `11 capitals and no accents are the same word`() { assertEquals(forms("ΦΕΤΑ"), forms("φέτα")) }
    @Test fun `12 a correct word is not reported as corrected`() { assertNull(q("φέτα").correction) }

    // ---- English ----
    @Test fun `13 English word gets its Greek form`() { assertTrue("γιαουρτι" in forms("yogurt")) }
    @Test fun `14 British spelling`() { assertTrue("γιαουρτι" in forms("yoghurt")) }
    @Test fun `15 English typo chiken`() { assertTrue("chicken" in forms("chiken")); assertEquals("chicken", q("chiken").correction) }
    @Test fun `16 English phrase olive oil stays a phrase`() { assertTrue("ελαιολαδο" in forms("olive oil")) }
    private val table = FoodTable {
        """[["Beef, ground, raw",254,17.2,0,20,0,0,0.17],["Meatballs, frozen",280,14,9,20,1,2,1.2],
            ["Chicken breast, raw",120,22.5,0,2.6,0,0,0.12],["Chicken feet, boiled",215,19,0.2,14.6,0,0,0.17],
            ["Bananas, raw",89,1.1,22.8,0.3,2.6,12.2,0]]"""
    }
    @Test fun `17 the bundled USDA table answers a Greek query in English`() {
        assertEquals(listOf("Beef, ground, raw"), table.search(q("κιμάς")).map { it.name })
        assertEquals("Chicken breast, raw", table.search(q("kotopoulo")).first().name)
    }
    @Test fun `18 the bundled table has plain foods, not brands, and carries nutrition`() {
        assertTrue(table.search(q("fage")).isEmpty())
        assertEquals(89.0, table.search(q("banana")).single().product!!.nutritionPer100g.calories!!, 0.01)
    }

    // ---- brands ----
    @Test fun `19 brand typed in Greek is found in Latin`() { val x = q("φαγε"); assertEquals("ΦΑΓΕ", x.brand?.display); assertTrue(x.offQueries.any { "fage" in it.lowercase() || "ΦΑΓΕ" in it }) }
    @Test fun `20 brand typed in Latin gets Greek query`() { val x = q("delta γάλα"); assertEquals("ΔΕΛΤΑ", x.brand?.display); assertTrue(x.offQueries.any { it.contains("ΔΕΛΤΑ") }) }
    @Test fun `21 two word brand`() { assertEquals("Κρι Κρι", q("kri kri γιαούρτι").brand?.display) }
    @Test fun `22 brand plus product sends an English query too`() { assertTrue(q("fage yogurt").offQueries.any { it.lowercase() == "fage yogurt" }) }
    @Test fun `23 no brand in a plain food`() { assertNull(q("μπανάνα").brand) }

    // ---- query plumbing ----
    @Test fun `24 Lucene syntax is stripped`() { assertEquals("feta 2%", q("feta: 2%").raw.replace("  ", " ")) }
    @Test fun `25 blank query is empty`() { assertTrue(q("   ").offQueries.isEmpty()) }
    @Test fun `26 at most four database queries`() { assertTrue(q("delta gala fresko").offQueries.size <= 4) }
    @Test fun `27 numbers are kept as typed`() { assertTrue(q("γάλα 1.5").terms.any { it.typed == "1.5" }) }

    // ---- ranking ----
    @Test fun `28 the food itself beats a dish that contains it`() {
        assertEquals("Φέτα ΠΟΠ", top("φέτα", hit("Σαλάτα με φέτα"), hit("Φέτα ΠΟΠ")))
    }
    @Test fun `29 flavoured products drop below the plain one`() {
        assertEquals("Γιαούρτι στραγγιστό 2%", top("γιαούρτι", hit("Μπάρα δημητριακών με γεύση γιαούρτι"), hit("Γιαούρτι στραγγιστό 2%")))
    }
    @Test fun `30 greeklish query ranks Greek names`() {
        assertEquals("Γάλα φρέσκο πλήρες", top("gala", hit("Galaxy chocolate"), hit("Γάλα φρέσκο πλήρες")))
    }
    @Test fun `31 English query finds Greek-named products`() {
        assertEquals("Ελαιόλαδο εξαιρετικό παρθένο", top("olive oil", hit("Olive tapenade"), hit("Ελαιόλαδο εξαιρετικό παρθένο")))
    }
    @Test fun `32 brand query puts the brand first`() {
        assertEquals("Total 2%", top("fage", hit("Στραγγιστό γιαούρτι", "Κρι Κρι"), hit("Total 2%", "FAGE")))
    }
    @Test fun `33 wrong brand is pushed down`() {
        val r = SmartSearch.rank(listOf(hit("Γάλα ελαφρύ", "Όλυμπος"), hit("Γάλα ελαφρύ", "ΔΕΛΤΑ")), q("δέλτα γάλα"))
        assertEquals("ΔΕΛΤΑ", r.hits.first().brand)
    }
    @Test fun `34 generic query prefers the USDA generic entry`() {
        assertEquals("Bananas, raw", top("banana", hit("Banana chips", "Crispy Co"), hit("Bananas, raw", source = usda)))
    }
    @Test fun `35 sold in Greece breaks a tie`() {
        assertTrue(SmartSearch.rank(listOf(hit("Φέτα", "A"), hit("Φέτα", "B", gr = true)), q("φέτα")).hits.first().soldInGreece)
    }
    @Test fun `36 complete entries beat empty ones`() {
        assertTrue(SmartSearch.rank(listOf(hit("Feta", "X", complete = false), hit("Feta", "Y")), q("feta")).hits.first().imageUrl != null)
    }
    @Test fun `37 the same product from two sources is one row`() {
        val r = SmartSearch.rank(listOf(hit("Φέτα ΠΟΠ", "Δωδώνη"), hit("ΦΕΤΑ ΠΟΠ", "ΔΩΔΩΝΗ", complete = false)), q("φέτα"))
        assertEquals(1, r.hits.size)
    }
    @Test fun `38 top answer only when every word matches`() {
        assertNotNull(SmartSearch.rank(listOf(hit("Γιαούρτι στραγγιστό", gr = true)), q("γιαούρτι στραγγιστό")).top)
        assertNull(SmartSearch.rank(listOf(hit("Γιαούρτι αγελάδος")), q("γιαούρτι στραγγιστό")).top)
    }

    // ---- suggestions ----
    @Test fun `39 Greek prefix suggests the word`() { assertTrue("γιαούρτι" in SmartSearch.suggest("γιαο")) }
    @Test fun `40 Latin prefix suggests English, Greek and brands`() {
        val s = SmartSearch.suggest("yog"); assertTrue("yogurt" in s); assertTrue("γιαούρτι" in s)
        assertTrue("ΦΑΓΕ" in SmartSearch.suggest("fa"))
        assertFalse(SmartSearch.suggest("x").isNotEmpty())
    }

    // ---- what people expect from a bare staple, in Greek, greeklish and English ----
    private val chickenHits = arrayOf(
        hit("Chicken feet, raw", source = usda), hit("Chicken skin, roasted", source = usda),
        hit("Orange chicken", source = usda), hit("Soup, chicken", source = usda),
        hit("Φιλέτο στήθος κοτόπουλο", "Μιμίκος", gr = true),
    )
    @Test fun `41 κοτόπουλο means breast or fillet, in Greek`() { assertEquals("Φιλέτο στήθος κοτόπουλο", top("κοτόπουλο", *chickenHits)) }
    @Test fun `42 kotopoulo means the same in greeklish`() { assertEquals("Φιλέτο στήθος κοτόπουλο", top("kotopoulo", *chickenHits)) }
    @Test fun `43 chicken means the same in English`() { assertEquals("Φιλέτο στήθος κοτόπουλο", top("chicken", *chickenHits)) }
    @Test fun `44 κιμάς kima and ground beef all mean minced beef`() {
        val hits = arrayOf(hit("Beef, ground, 85% lean meat, raw", source = usda), hit("Meatballs, frozen", source = usda), hit("Κιμάς μοσχαρίσιος", gr = true))
        for (query in listOf("κιμάς", "kimas", "ground beef")) assertTrue(query, top(query, *hits).let { it.startsWith("Κιμάς") || it.startsWith("Beef, ground") })
        assertEquals("ground beef raw", q("kima").usdaIntent)
    }
    @Test fun `45 banana raw beats banana pudding`() {
        assertEquals("Bananas, raw", top("banana", hit("Banana, baked", source = usda), hit("Banana pudding", source = usda), hit("Bananas, raw", source = usda)))
    }
    @Test fun `46 yogurt a plain strained one beats tofu yogurt`() {
        assertEquals("Στραγγιστό γιαούρτι 2%", top("giaourti", hit("Tofu yogurt", source = usda), hit("Yogurt tube", source = usda), hit("Στραγγιστό γιαούρτι 2%", "ΦΑΓΕ", gr = true)))
    }
    @Test fun `47 searching the side word itself keeps it`() {
        assertEquals("Soup, chicken", top("chicken soup", hit("Chicken feet, raw", source = usda), hit("Soup, chicken", source = usda)))
    }
    @Test fun `48 picture subject of a USDA name`() {
        assertEquals("bananas", SmartSearch.imageSubject("Bananas, raw"))
        assertEquals("chicken soup", SmartSearch.imageSubject("Soup, chicken, canned"))
        assertNull(SmartSearch.imageSubject("12 grain, nfs"))
    }
    @Test fun `49 Greek and greeklish searchers see Greek products before USDA`() {
        val hits = arrayOf(hit("Chicken, breast, boneless, skinless, raw", source = usda), hit("Φιλέτο στήθος κοτόπουλο", gr = true, complete = false))
        assertEquals("Φιλέτο στήθος κοτόπουλο", top("kotopoulo", *hits))
        assertEquals("Φιλέτο στήθος κοτόπουλο", top("κοτόπουλο", *hits))
        assertTrue(q("kotopoulo").greekUser); assertFalse(q("chicken").greekUser)
    }
    @Test fun `50 pictures show the food, never the animal or a letter`() {
        assertEquals("Chicken as food", SmartSearch.pictureSubject(hit("Chicken, breast, raw", source = usda, complete = false), q("chicken")))
        assertEquals("yogurt", SmartSearch.pictureSubject(hit("Γιαούρτι πρόβειο", complete = false), q("giaourti")))
        assertNull(SmartSearch.pictureSubject(hit("Γιαούρτι πρόβειο"), q("giaourti")))
        assertNull(SmartSearch.pictureSubject(hit("Τυρόπιτα", complete = false), q("giaourti")))
    }
    @Test fun `51 kimas finds minced beef, not a curry or sausages`() {
        val hits = arrayOf(hit("Kima curry", gr = true), hit("Sausages Beef", "Moutevelis", gr = true), hit("Beef, ground, 85% lean meat, raw", source = usda), hit("Κιμάς μοσχαρίσιος", gr = true))
        assertEquals("Κιμάς μοσχαρίσιος", top("kimas", *hits))
        val r = SmartSearch.rank(hits.toList(), q("kimas")).hits.map { it.name }
        assertTrue(r.indexOf("Beef, ground, 85% lean meat, raw") < r.indexOf("Kima curry"))
    }
    @Test fun `52 breaded patties and luncheon meat sink for a bare chicken`() {
        val r = SmartSearch.rank(listOf(hit("Chicken-style Breaded Patties", gr = true), hit("Chicken Luncheon Meat", gr = true), hit("Chicken, breast, raw", source = usda)), q("chicken")).hits
        assertEquals("Chicken, breast, raw", r.first().name)
    }
}
