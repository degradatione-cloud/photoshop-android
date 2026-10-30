package com.degradatione.psandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * Фильтры и эффекты. Встроенные — тот же формат, что у плагинов,
 * поэтому сторонний .json просто добавляется в список.
 */
data class FilterDef(
    val id: String,
    val name: String,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f,
    val duotoneA: String? = null,
    val duotoneB: String? = null,
    val plugin: Boolean = false,
) {
    fun matrix(): ColorMatrix {
        val cm = ColorMatrix()
        // насыщенность
        val s = ColorMatrix()
        s.setSaturation(saturation.coerceIn(0f, 3f))
        cm.postConcat(s)
        // яркость/контраст
        val c = contrast.coerceIn(-100f, 100f) / 100f * 255f
        val b = brightness.coerceIn(-100f, 100f) / 100f * 255f
        val scale = (259f * (c + 255f)) / (255f * (259f - c))
        val bc = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, b,
                0f, scale, 0f, 0f, b,
                0f, 0f, scale, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        cm.postConcat(bc)
        // температура
        val t = (temperature.coerceIn(-100f, 100f) / 100f) * 0.3f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f + t, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - t, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return cm
    }
}

object FilterLibrary {

    val builtIn = listOf(
        FilterDef("original", "Оригинал"),
        FilterDef("vivid", "Vivid", brightness = 4f, contrast = 16f, saturation = 1.45f),
        FilterDef("warm", "Тёплый", brightness = 3f, contrast = 6f, saturation = 1.15f, temperature = 35f),
        FilterDef("cool", "Холодный", contrast = 8f, saturation = 1.1f, temperature = -40f),
        FilterDef("film", "Плёнка", contrast = -6f, saturation = 0.9f, fade = 0.12f, grain = 0.35f, vignette = 0.18f),
        FilterDef("fade", "Fade", brightness = 6f, contrast = -18f, saturation = 0.85f, fade = 0.22f),
        FilterDef("bw", "Ч/Б", saturation = 0f, contrast = 12f),
        FilterDef("mono", "Моно", saturation = 0f, contrast = 26f, brightness = -4f, vignette = 0.15f),
        FilterDef("sepia", "Сепия", saturation = 0.35f, temperature = 45f, brightness = 4f),
        FilterDef("drama", "Драма", contrast = 42f, saturation = 1.2f, brightness = -8f, vignette = 0.3f),
        FilterDef("neon", "Neon", contrast = 24f, saturation = 1.7f, temperature = -25f, duotoneA = "#00E5FF", duotoneB = "#FF00E5"),
        FilterDef("duotone", "Дюотон", saturation = 0f, duotoneA = "#312E81", duotoneB = "#F97316"),
        FilterDef("vignette", "Виньетка", vignette = 0.45f),
        FilterDef("grain", "Зерно", grain = 0.6f),
    )

    fun pluginDir(appFiles: File): File = File(appFiles, "plugins").apply { mkdirs() }

    fun loadPlugins(appFiles: File): List<FilterDef> {
        val dir = pluginDir(appFiles)
        val out = mutableListOf<FilterDef>()
        dir.listFiles()?.filter { it.name.endsWith(".json", true) }?.sortedBy { it.name }?.forEach { f ->
            runCatching {
                val o = JSONObject(f.readText())
                out.add(
                    FilterDef(
                        id = o.optString("id", f.nameWithoutExtension),
                        name = o.optString("name", f.nameWithoutExtension),
                        brightness = o.optDouble("brightness", 0.0).toFloat(),
                        contrast = o.optDouble("contrast", 0.0).toFloat(),
                        saturation = o.optDouble("saturation", 1.0).toFloat(),
                        temperature = o.optDouble("temperature", 0.0).toFloat(),
                        vignette = o.optDouble("vignette", 0.0).toFloat(),
                        grain = o.optDouble("grain", 0.0).toFloat(),
                        fade = o.optDouble("fade", 0.0).toFloat(),
                        duotoneA = o.optString("duotoneA").takeIf { it.isNotBlank() },
                        duotoneB = o.optString("duotoneB").takeIf { it.isNotBlank() },
                        plugin = true,
                    )
                )
            }
        }
        return out
    }

    fun installPlugin(appFiles: File, json: String, fileName: String): FilterDef {
        val o = JSONObject(json) // бросает, если это не JSON — плагин не установится
        val name = o.optString("name").ifBlank { fileName }
        require(o.has("saturation") || o.has("contrast") || o.has("brightness") || o.has("duotoneA")) {
            "плагину нужен хотя бы один параметр: saturation/contrast/brightness/duotoneA"
        }
        val target = File(pluginDir(appFiles), fileName.removeSuffix(".json").take(40) + ".json")
        target.writeText(o.toString(2))
        return FilterDef(
            id = o.optString("id", target.nameWithoutExtension),
            name = name,
            brightness = o.optDouble("brightness", 0.0).toFloat(),
            contrast = o.optDouble("contrast", 0.0).toFloat(),
            saturation = o.optDouble("saturation", 1.0).toFloat(),
            temperature = o.optDouble("temperature", 0.0).toFloat(),
            vignette = o.optDouble("vignette", 0.0).toFloat(),
            grain = o.optDouble("grain", 0.0).toFloat(),
            fade = o.optDouble("fade", 0.0).toFloat(),
            duotoneA = o.optString("duotoneA").takeIf { it.isNotBlank() },
            duotoneB = o.optString("duotoneB").takeIf { it.isNotBlank() },
            plugin = true,
        )
    }

    /** Маленькое превью эффекта — чтобы было видно, что применяешь. */
    fun preview(src: Bitmap, def: FilterDef, size: Int = 96): Bitmap {
        val small = Bitmap.createScaledBitmap(src, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(def.matrix()) }
        c.drawBitmap(small, 0f, 0f, paint)
        if (def.fade > 0f) {
            c.drawColor(AColor.argb((def.fade * 255).toInt(), 255, 255, 255), PorterDuff.Mode.SCREEN)
        }
        if (def.duotoneA != null && def.duotoneB != null) {
            val grad = android.graphics.LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                AColor.parseColor(def.duotoneA), AColor.parseColor(def.duotoneB), Shader.TileMode.CLAMP,
            )
            val p = Paint().apply {
                shader = grad
                xfermode = PorterDuffXfermode(PorterDuff.Mode.COLOR)
                alpha = 190
            }
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        }
        if (def.vignette > 0f) {
            val r = size * 0.72f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    size / 2f, size / 2f, r,
                    intArrayOf(0x00000000, AColor.argb((def.vignette * 255).toInt(), 0, 0, 0)),
                    floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        }
        return out
    }

    fun grainOverlay(bitmap: Bitmap, amount: Float) {
        if (amount <= 0f) return
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val noisePaint = Paint()
        val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(layer)
        val rnd = Random(amount.toLong() * 7919)
        val step = if (amount > 0.4f) 1 else 2
        val strength = (amount.coerceIn(0f, 1f) * 90).toInt()
        noisePaint.color = AColor.argb(strength, 255, 255, 255)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                if (rnd.nextFloat() < amount * 0.35f) {
                    noisePaint.color = if (rnd.nextBoolean()) {
                        AColor.argb(strength, 255, 255, 255)
                    } else {
                        AColor.argb(strength, 0, 0, 0)
                    }
                    c.drawPoint(x.toFloat(), y.toFloat(), noisePaint)
                }
                x += step
            }
            y += step
        }
        Canvas(bitmap).drawBitmap(layer, 0f, 0f, Paint())
    }
}
