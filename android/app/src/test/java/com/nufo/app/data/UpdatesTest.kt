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

    @Test fun `finds the APKs and checksums attached to the release`() {
        val release = Json.parseToJsonElement("""{"tag_name":"v1.0.4","assets":[
            {"name":"nufo-1.0.4.apk","browser_download_url":"https://github.com/TPAINN/nufo/releases/download/v1.0.4/nufo-1.0.4.apk"},
            {"name":"nufo-1.0.4-32bit.apk","browser_download_url":"https://github.com/TPAINN/nufo/releases/download/v1.0.4/nufo-1.0.4-32bit.apk"},
            {"name":"nufo-1.0.4.aab","browser_download_url":"https://github.com/TPAINN/nufo/releases/download/v1.0.4/nufo-1.0.4.aab"},
            {"name":"SHA256SUMS.txt","browser_download_url":"https://github.com/TPAINN/nufo/releases/download/v1.0.4/SHA256SUMS.txt"},
            {"name":"evil.apk","browser_download_url":"http://insecure.example/evil.apk"}]}""").jsonObject
        val u = Updates.parse(release, "1.0.3")!!
        assertTrue(u.apk!!.endsWith("/nufo-1.0.4.apk"))
        assertTrue(u.apk32!!.endsWith("/nufo-1.0.4-32bit.apk"))
        assertTrue(u.sums!!.endsWith("/SHA256SUMS.txt"))
    }

    @Test fun `reads checksums in sha256sum format`() {
        val sums = "fb8e0d3d7a18289992f693f2f949355c8e6a43acbfc05fec0f38541f463df20b *nufo-1.0.3-32bit.apk\n" +
            "3A9A893B6F98F74F7D497E5D686DF619CC4CEB1AA9475362171651E40D861EB4  nufo-1.0.3.apk\n"
        assertEquals("3a9a893b6f98f74f7d497e5d686df619cc4ceb1aa9475362171651e40d861eb4", Updates.checksum(sums, "nufo-1.0.3.apk"))
        assertEquals("fb8e0d3d7a18289992f693f2f949355c8e6a43acbfc05fec0f38541f463df20b", Updates.checksum(sums, "nufo-1.0.3-32bit.apk"))
        assertNull(Updates.checksum(sums, "nufo-1.0.2.apk"))
        assertNull(Updates.checksum("not-a-hash nufo-1.0.3.apk", "nufo-1.0.3.apk"))
    }

    @Test fun `parses the latest GitHub release`() {
        fun release(tag: String) = Json.parseToJsonElement("""{"tag_name":"$tag","name":"Nufo"}""").jsonObject
        assertEquals("1.0.1", Updates.parse(release("v1.0.1"), "1.0.0")!!.version)
        assertNull(Updates.parse(release("v1.0.0"), "1.0.0"))
        assertNull(Updates.parse(Json.parseToJsonElement("""{"message":"Not Found"}""").jsonObject, "1.0.0"))
    }
}
