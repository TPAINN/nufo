package com.nufo.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun `compares versions numerically`() {
        assertTrue(Updates.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updates.isNewer("1.0.10", "1.0.9"))
        assertTrue(Updates.isNewer("1.1", "1.0.9"))
        assertFalse(Updates.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updates.isNewer("1.0", "1.0.0"))
        assertFalse(Updates.isNewer("0.9.9", "1.0.0"))
    }

    @Test fun `parses the latest GitHub release`() {
        fun release(tag: String) = Json.parseToJsonElement("""{"tag_name":"$tag","name":"Nufo"}""").jsonObject
        assertEquals("1.0.1", Updates.parse(release("v1.0.1"), "1.0.0")!!.version)
        assertNull(Updates.parse(release("v1.0.0"), "1.0.0"))
        assertNull(Updates.parse(Json.parseToJsonElement("""{"message":"Not Found"}""").jsonObject, "1.0.0"))
    }
}
