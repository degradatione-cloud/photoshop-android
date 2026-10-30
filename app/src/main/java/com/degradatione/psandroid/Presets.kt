package com.degradatione.psandroid

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 1600 пресетов, собранных из сетки параметров (яркость × контраст ×
 * насыщенность × температура) — каждый реально применяется как фильтр.
 */
object PresetFactory {

    data class Preset(val id: String, val group: String, val def: FilterDef)

    private val brightness = listOf(-30f, -20f, -10f, 0f, 10f, 20f, 30f, 42f)
    private val contrast = listOf(-30f, -18f, -8f, 0f, 8f, 18f, 30f, 42f)
    private val saturation = listOf(0f, 0.5f, 0.8f, 1f, 1.2f, 1.45f, 1.7f, 2f)
    private val temperature = listOf(-45f, -20f, 0f, 20f, 45f)

    val all: List<Preset> by lazy {
        val out = ArrayList<Preset>(1600)
        var n = 0
        loop@ for (b in brightness) {
            for (c in contrast) {
                for (s in saturation) {
                    for (t in temperature) {
                        if (out.size >= 1600) break@loop
                        n++
                        val name = "B${b.toInt()} C${c.toInt()} S${(s * 100).toInt()} T${t.toInt()}"
                        out.add(
                            Preset(
                                id = "p$n",
                                group = when {
                                    b >= 20 -> "светлые"
                                    b <= -20 -> "тёмные"
                                    s <= 0.5f -> "ч/б"
                                    t >= 20 -> "тёплые"
                                    t <= -20 -> "холодные"
                                    c >= 30 -> "контраст"
                                    else -> "базовые"
                                },
                                def = FilterDef(
                                    id = "p$n",
                                    name = name,
                                    brightness = b,
                                    contrast = c,
                                    saturation = s,
                                    temperature = t,
                                ),
                            )
                        )
                    }
                }
            }
        }
        out
    }

    val groups: List<String> by lazy { listOf("все") + all.map { it.group }.distinct() }
}

/**
 * Экспорт проекта в настоящий .psd: слои растеризуются в PNG,
 * дальше их собирает Rust-ядро PhotoCraft.
 */
object PsdExporter {

    fun export(ed: Editor, cacheDir: File, name: String): File {
        val work = File(cacheDir, "psdout-${System.currentTimeMillis()}").apply { mkdirs() }
        val layersJson = JSONArray()
        // PSD хранит слои снизу вверх — у нас тот же порядок
        for (l in ed.doc.layers) {
            val f = File(work, "layer-${l.id}.png")
            f.outputStream().use { l.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            layersJson.put(
                JSONObject()
                    .put("png", f.absolutePath)
                    .put("name", l.name)
                    .put("x", 0)
                    .put("y", 0)
                    .put("opacity", l.opacity.toDouble())
                    .put("visible", l.visible)
                    .put("blend", l.blend.label)
            )
        }
        val composite = File(work, "merged.png")
        composite.outputStream().use { ed.composite().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val manifest = JSONObject()
            .put("width", ed.doc.width)
            .put("height", ed.doc.height)
            .put("composite", composite.absolutePath)
            .put("layers", layersJson)
        val out = File(work, if (name.endsWith(".psd", true)) name else "$name.psd")
        val json = JSONObject(PsdBridge.writePsd(manifest.toString(), out))
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "ядро не собрало PSD"))
        }
        ed.status = "PSD: ${out.length() / 1024} KB, ${json.optInt("layers")} слоёв"
        return out
    }
}
