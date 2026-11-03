package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateLimitTest {
    @Test fun `retry waits as long as the server asks, within limits`() {
        assertEquals(3000L, retryDelayMs("3"))
        assertEquals(2000L, retryDelayMs(null))
        assertEquals(2000L, retryDelayMs("Wed, 21 Oct 2026 07:28:00 GMT")) // date form: fall back
        assertEquals(5000L, retryDelayMs("120")) // never keep someone waiting longer
        assertEquals(1000L, retryDelayMs("0"))
    }

    @Test fun `tag names are cached per language`() {
        // Arrange
        val cache = TagNameCache()
        // Act
        cache.putAll("el", mapOf("en:milk" to "Γάλα"))
        // Assert
        assertEquals("Γάλα", cache.get("el", "en:milk"))
        assertNull(cache.get("en", "en:milk"))
        assertNull(cache.get("el", "en:eggs"))
    }
}
