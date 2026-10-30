package com.degradatione.psandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Мост к Rust-ядру PSD (crate `photocraft-psd`, вендорен из PhotoCraft).
 * Если библиотека не собрана/не загрузилась — приложение просто не показывает импорт PSD.
 */
object PsdBridge {

    @Volatile
    private var ready = false

    private var loadError: String? = null

    fun available(): Boolean {
        if (ready) return true
        return runCatching {
            System.loadLibrary("psd_jni")
            ready = true
            true
        }.getOrElse {
            loadError = it.message
            false
        }
    }

    fun status(): String = when {
        ready -> runCatching { nativeVersion() }.getOrDefault("psd-core")
        else -> "ядро не загружено: ${loadError ?: "нет .so"}"
    }

    fun readPsd(psd: File, outDir: File): String {
        if (!available()) throw IllegalStateException(status())
        outDir.mkdirs()
        return nativeReadPsd(psd.absolutePath, outDir.absolutePath)
    }

    /** Достаёт слой-картинку и, если нужно, масштабирует её под ограничение документа. */
    fun loadLayer(path: String?, maxSide: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val src = BitmapFactory.decodeFile(path) ?: return null
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val k = maxSide.toFloat() / longest
        val out = Bitmap.createScaledBitmap(
            src,
            (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1),
            true,
        )
        return out
    }

    private external fun nativeReadPsd(path: String, outDir: String): String
    private external fun nativeVersion(): String
}
