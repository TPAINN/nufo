package com.nufo.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A newer release. [apk] and [apk32] are the signed APKs for 64-bit and 32-bit phones, [sums] the release's
 * SHA256SUMS.txt; any of them is null when the release lacks it, and the app then sends people to [url].
 */
data class AppUpdate(
    val version: String,
    val url: String,
    val apk: String? = null,
    val apk32: String? = null,
    val sums: String? = null,
)

/** New versions are published as GitHub releases; the signed APKs are attached to each one. */
object Updates {
    const val LATEST_RELEASE = "https://api.github.com/repos/TPAINN/nufo/releases/latest"
    const val DOWNLOAD_PAGE = "https://nufo.vercel.app/#download"
    const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** The release newer than [current], or null when [current] is up to date (or the response is unusable). */
    fun parse(release: JsonObject, current: String): AppUpdate? {
        val tag = release["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
        if (!isNewer(tag, current)) return null
        val assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { a ->
            val o = a.jsonObject
            val name = (o["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val link = (o["browser_download_url"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            name to link
        }.toMap()
        return AppUpdate(
            version = tag, url = DOWNLOAD_PAGE,
            apk = assets["nufo-$tag.apk"], apk32 = assets["nufo-$tag-32bit.apk"], sums = assets["SHA256SUMS.txt"],
        )
    }

    /** The SHA-256 listed for [fileName] in a `sha256sum` output ("<hex> *name" or "<hex>  name"), lowercase. */
    fun checksum(sums: String, fileName: String): String? = sums.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { it.size == 2 && it[1].trimStart('*') == fileName }
        ?.first()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    /** Numeric comparison, so 1.0.10 is newer than 1.0.9; suffixes like "-beta" are ignored. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
