package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreekSearchTest {
    @Test fun `normalize removes accents and unifies final sigma`() {
        assertEquals("γιαουρτι", GreekSearch.normalize("Γιαούρτι"))
        assertEquals("φακεσ", GreekSearch.normalize("φακές"))
        assertEquals("μουσακασ", GreekSearch.normalize("ΜΟΥΣΑΚΑΣ"))
    }

    @Test fun `detects Greek text`() {
        assertTrue(GreekSearch.hasGreek("φέτα"))
        assertTrue(GreekSearch.hasGreek("ΔΕΛΤΑ milk"))
        assertFalse(GreekSearch.hasGreek("feta"))
    }

    @Test fun `translates known words regardless of accents or case`() {
        assertEquals("yogurt", GreekSearch.english("γιαουρτι"))
        assertEquals("strained yogurt", GreekSearch.english("Στραγγιστό ΓΙΑΟΎΡΤΙ"))
        assertEquals("olive oil", GreekSearch.english("ελαιόλαδο"))
        assertEquals("feta 2%", GreekSearch.english("φέτα 2%"))
        assertEquals("spoon sweet", GreekSearch.english("γλυκό του κουταλιού"))
    }

    @Test fun `does not translate when a Greek word is unknown`() {
        assertNull(GreekSearch.english("ΔΕΛΤΑ γάλα")) // brand names stay untranslated, so no English query
    }
}
