package com.nufo.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Updates the app in place: downloads the signed APK for this phone from the GitHub release, checks it against the
 * release's SHA-256 list, and hands it to the system installer. Android itself only installs it over this app when
 * it is signed with the same key, so a tampered file cannot replace Nufo.
 */
class AppInstaller(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // no call timeout: 35 MB on a slow connection takes a while
        .build()
    private val dir get() = File(context.cacheDir, "updates")

    /** The APK for this phone: 64-bit when it can run it, else the 32-bit build. Null if the release has neither. */
    fun apkFor(update: AppUpdate): String? =
        if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) update.apk ?: update.apk32 else update.apk32 ?: update.apk

    /** Downloads and verifies the update; [onProgress] gets 0..1 (or -1 while the size is unknown). */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = apkFor(update) ?: throw IOException("No APK in release ${update.version}")
        val name = url.substringAfterLast('/')
        val expected = update.sums?.let { get(it).use { r -> Updates.checksum(r.body.string(), name) } }
        dir.mkdirs()
        val part = File(dir, "$name.part")
        val digest = MessageDigest.getInstance("SHA-256")
        get(url).use { res ->
            val total = res.body.contentLength()
            var done = 0L
            res.body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (expected != null && actual != expected) { part.delete(); throw IOException("Checksum mismatch for $name") }
        File(dir, name).also { part.renameTo(it) }
    }

    private fun get(url: String) = client.newCall(Request.Builder().url(url).build()).execute().also {
        if (!it.isSuccessful) { it.close(); throw IOException("HTTP ${it.code} for $url") }
    }

    /** Android 8+ asks once per app before it may install others; false until the user allows it. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** The system page where the user allows Nufo to install updates. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", apk), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Leftovers from an earlier update; safe once the app is running again. */
    fun cleanUp() { dir.deleteRecursively() }
}
