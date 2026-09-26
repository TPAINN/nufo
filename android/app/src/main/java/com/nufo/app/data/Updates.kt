package com.nufo.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppUpdate(val version: String, val url: String)

/** New versions are published as GitHub releases; the signed APK is downloaded from the Nufo site. */
object Updates {
    const val LATEST_RELEASE = "https://api.github.com/repos/TPAINN/nufo/releases/latest"
    const val DOWNLOAD_PAGE = "https://nufo.vercel.app/#download"
    const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** The release newer than [current], or null when [current] is up to date (or the response is unusable). */
    fun parse(release: JsonObject, current: String): AppUpdate? {
        val tag = release["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
        return if (isNewer(tag, current)) AppUpdate(tag, DOWNLOAD_PAGE) else null
    }

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
