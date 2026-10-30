package com.degradatione.psandroid

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Результат разбора PSD: размеры документа и растрированные слои. */
data class PsdImport(val width: Int, val height: Int, val name: String, val layers: List<ParsedLayer>)

/**
 * Импорт PSD через Rust-ядро PhotoCraft: копируем файл в кэш, зовём JNI,
 * получаем JSON + PNG слоёв, собираем ParsedLayer.
 */
object PsdImporter {

    fun import(ctx: Context, uri: Uri, maxSide: Int): PsdImport {
        if (!PsdBridge.available()) throw IllegalStateException(PsdBridge.status())
        val work = File(ctx.cacheDir, "psd-${System.currentTimeMillis()}").apply { mkdirs() }
        val psd = File(work, "import.psd")
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "пустой поток из галереи" }
            psd.outputStream().use { out -> input.copyTo(out) }
        }
        val json = JSONObject(PsdBridge.readPsd(psd, File(work, "layers")))
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "ядро вернуло ошибку"))
        }
        val w = json.getInt("width")
        val h = json.getInt("height")
        val rawLayers = json.optJSONArray("layers") ?: org.json.JSONArray()
        val parsed = ArrayList<ParsedLayer>(rawLayers.length())
        for (i in 0 until rawLayers.length()) {
            val o = rawLayers.getJSONObject(i)
            val bmp = PsdBridge.loadLayer(o.optString("png").takeIf { it.isNotBlank() && it != "null" }, maxSide)
            parsed.add(
                ParsedLayer(
                    name = o.optString("name", "Layer ${i + 1}"),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                    blend = mapBlend(o.optString("blend", "Normal")),
                    visible = o.optBoolean("visible", true),
                    bitmap = bmp,
                )
            )
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(40) ?: "imported.psd"
        return PsdImport(w, h, name, parsed)
    }

    private fun mapBlend(name: String): PsBlend = when (name.lowercase()) {
        "multiply" -> PsBlend.MULTIPLY
        "screen" -> PsBlend.SCREEN
        "overlay" -> PsBlend.OVERLAY
        "darken" -> PsBlend.DARKEN
        "lighten" -> PsBlend.LIGHTEN
        "lineardodge", "linear_dodge" -> PsBlend.ADD
        "difference" -> PsBlend.DIFFERENCE
        else -> PsBlend.NORMAL
    }
}
