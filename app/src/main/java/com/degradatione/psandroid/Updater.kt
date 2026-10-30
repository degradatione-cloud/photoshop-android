package com.degradatione.psandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Автообновление из GitHub Releases: приложение само видит новую версию,
 * качает APK и открывает системный установщик — переустанавливать руками не надо.
 */
object Updater {

    const val DEFAULT_REPO = "degradatione-cloud/photoshop-android"

    data class Release(
        val tag: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val size: Long,
        val digest: String?,
    )

    fun versionCodeOf(tag: String): Int {
        val v = tag.trim().removePrefix("v").removePrefix("V")
        val parts = v.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        if (parts.isEmpty()) return 0
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }

    fun installedVersionCode(ctx: Context): Int = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    suspend fun check(repo: String): Release? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("accept", "application/vnd.github+json")
            setRequestProperty("user-agent", "photoshop-android")
        }
        try {
            if (conn.responseCode != 200) {
                android.util.Log.w("update", "releases/latest → HTTP ${conn.responseCode} ($repo)")
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().readText()
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val notes = o.optString("body").take(2000)
            val assets = o.optJSONArray("assets") ?: return@withContext null
            var best: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (!name.endsWith(".apk", true)) continue
                if (best == null) best = a
                if (name.contains("release", true)) {
                    best = a
                    break
                }
            }
            val asset = best ?: return@withContext null
            Release(
                tag = tag,
                versionCode = versionCodeOf(tag),
                notes = notes,
                apkUrl = asset.optString("browser_download_url"),
                apkName = asset.optString("name"),
                size = asset.optLong("size"),
                digest = asset.optString("digest").takeIf { it.startsWith("sha256:") },
            )
        } catch (t: Throwable) {
            android.util.Log.w("update", "проверка обновлений: ${t.message}")
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    suspend fun download(
        ctx: Context,
        release: Release,
        onProgress: (Int, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, release.apkName.ifBlank { "update.apk" })
        if (target.exists() && release.size > 0 && target.length() == release.size && digestOk(target, release.digest)) {
            android.util.Log.i("update", "APK уже скачан: ${target.name}")
            return@withContext target
        }
        android.util.Log.i("update", "качаю ${release.apkUrl}")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("user-agent", "photoshop-android")
        }
        conn.connect()
        val total = if (release.size > 0) release.size else conn.contentLengthLong
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct, done)
                    }
                }
            }
        }
        conn.disconnect()
        if (release.size > 0 && target.length() != release.size) {
            target.delete()
            throw IllegalStateException("Размер не совпал: ${target.length()} ≠ ${release.size}")
        }
        if (!digestOk(target, release.digest)) {
            target.delete()
            throw IllegalStateException("sha256 не совпал с релизом")
        }
        android.util.Log.i("update", "скачано ${target.length()} байт")
        target
    }

    private fun digestOk(f: File, digest: String?): Boolean {
        if (digest == null) return true
        val expected = digest.substringAfter("sha256:").trim().lowercase()
        if (expected.isEmpty()) return true
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) android.util.Log.e("update", "sha256: $actual ≠ $expected")
        return actual == expected
    }

    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            android.util.Log.w("update", "нет разрешения «Установка неизвестных приложений» — открываю настройки")
            runCatching {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        android.util.Log.i("update", "запускаю установщик для ${apk.name}")
        ctx.startActivity(intent)
    }
}
