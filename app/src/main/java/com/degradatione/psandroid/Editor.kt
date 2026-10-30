package com.degradatione.psandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PsBlend(val label: String, val compose: BlendMode, val porter: PorterDuff.Mode) {
    NORMAL("Normal", BlendMode.SrcOver, PorterDuff.Mode.SRC_OVER),
    MULTIPLY("Multiply", BlendMode.Multiply, PorterDuff.Mode.MULTIPLY),
    SCREEN("Screen", BlendMode.Screen, PorterDuff.Mode.SCREEN),
    OVERLAY("Overlay", BlendMode.Overlay, PorterDuff.Mode.OVERLAY),
    DARKEN("Darken", BlendMode.Darken, PorterDuff.Mode.DARKEN),
    LIGHTEN("Lighten", BlendMode.Lighten, PorterDuff.Mode.LIGHTEN),
    ADD("Linear Dodge", BlendMode.Plus, PorterDuff.Mode.ADD),
    DIFFERENCE("Difference", BlendMode.Difference, PorterDuff.Mode.XOR),
}

class Layer(
    val id: Int,
    var name: String,
    var bitmap: Bitmap,
    var visible: Boolean = true,
    var opacity: Float = 1f,
    var blend: PsBlend = PsBlend.NORMAL,
) {
    fun copyDeep(): Layer = Layer(id, name, bitmap.copy(Bitmap.Config.ARGB_8888, true), visible, opacity, blend)
}

class PsDocument(var width: Int, var height: Int, val layers: MutableList<Layer>) {
    companion object {
        private var layerSeq = 1
        fun nextId(): Int = layerSeq++
        fun blank(w: Int, h: Int, fill: Int = Color.WHITE): PsDocument {
            val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bg).drawColor(fill)
            return PsDocument(w, h, mutableListOf(Layer(nextId(), "Background", bg)))
        }
        fun transparent(w: Int, h: Int): PsDocument {
            val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            return PsDocument(w, h, mutableListOf(Layer(nextId(), "Layer 1", bg)))
        }
        fun fromBitmap(src: Bitmap): PsDocument {
            val max = 1600
            val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawBitmap(src, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            return PsDocument(w, h, mutableListOf(Layer(nextId(), "Layer 1", bmp)))
        }
    }
}

data class UndoStep(val label: String, val doc: PsDocument, val active: Int)

/** Растрированный слой, пришедший из PSD-ядра. */
data class ParsedLayer(
    val name: String,
    val x: Int,
    val y: Int,
    val opacity: Float,
    val blend: PsBlend,
    val visible: Boolean,
    val bitmap: Bitmap?,
)

enum class PsTool(val key: String, val label: String) {
    MOVE("V", "Перемещение"),
    BRUSH("B", "Кисть"),
    ERASER("E", "Ластик"),
    FILL("G", "Заливка"),
    EYEDROPPER("I", "Пипетка"),
    CROP("C", "Кадрирование"),
    TEXT("T", "Текст"),
    ZOOM("Z", "Масштаб"),
}

/**
 * Движок редактора: растровые слои, режимы наложения, история, инструменты, экспорт.
 */
class Editor {

    var doc by mutableStateOf(PsDocument.blank(1080, 1440))
        private set
    var activeIndex by mutableIntStateOf(0)
        private set
    /** Растёт при каждом изменении пикселей — Compose по нему перерисовывает холст. */
    var version by mutableIntStateOf(0)
        private set
    var history by mutableStateOf<List<String>>(emptyList())
        private set
    var redoAvailable by mutableIntStateOf(0)
        private set

    var tool by mutableStateOf(PsTool.BRUSH)
    var brushSize by mutableStateOf(28f)
    var brushHardness by mutableStateOf(0.85f)
    var brushOpacity by mutableStateOf(1f)
    var foreground by mutableStateOf(Color.parseColor("#FF3B30"))
    var background by mutableStateOf(Color.WHITE)
    var docName by mutableStateOf("Untitled-1")
    var status by mutableStateOf("Готово")

    private val undo = ArrayDeque<UndoStep>()
    private val redo = ArrayDeque<UndoStep>()
    private var strokeShot: Bitmap? = null
    private var strokeLayerId = -1

    val layer: Layer? get() = doc.layers.getOrNull(activeIndex)
    val canUndo: Boolean get() = undo.isNotEmpty()

    private fun copyDoc(): PsDocument =
        PsDocument(doc.width, doc.height, doc.layers.map { it.copyDeep() }.toMutableList())

    // ── структура документа ────────────────────────────────────────────────
    fun checkpoint(label: String) {
        if (undo.size >= 6) undo.removeFirst()
        undo.addLast(UndoStep(label, copyDoc(), activeIndex))
        redo.clear()
        redoAvailable = 0
        history = undo.map { it.label }
        status = label
    }

    fun undoStep() {
        val step = undo.removeLastOrNull() ?: return
        redo.addLast(UndoStep(step.label, copyDoc(), activeIndex))
        doc = step.doc
        activeIndex = activeIndex.coerceIn(0, doc.layers.lastIndex)
        history = undo.map { it.label }
        redoAvailable = redo.size
        status = "Отменено: ${step.label}"
        version++
    }

    fun redoStep() {
        val step = redo.removeLastOrNull() ?: return
        undo.addLast(UndoStep(step.label, copyDoc(), activeIndex))
        doc = step.doc
        activeIndex = activeIndex.coerceIn(0, doc.layers.lastIndex)
        history = undo.map { it.label }
        redoAvailable = redo.size
        status = "Повторено: ${step.label}"
        version++
    }

    fun newDocument(w: Int, h: Int, transparent: Boolean = false) {
        doc = if (transparent) PsDocument.transparent(w, h) else PsDocument.blank(w, h)
        activeIndex = 0
        undo.clear(); redo.clear(); history = emptyList(); redoAvailable = 0
        docName = "Untitled-${(1..999).random()}"
        status = "Новый документ $w × $h"
        version++
    }

    fun openImage(bitmap: Bitmap, name: String) {
        doc = PsDocument.fromBitmap(bitmap)
        activeIndex = 0
        undo.clear(); redo.clear(); history = emptyList(); redoAvailable = 0
        docName = name
        status = "Открыто: $name (${doc.width} × ${doc.height})"
        version++
    }

    fun addLayer() {
        checkpoint("Новый слой")
        val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        doc.layers.add(Layer(PsDocument.nextId(), "Layer ${doc.layers.size + 1}", bmp))
        activeIndex = doc.layers.lastIndex
        version++
    }

    fun duplicateLayer() {
        val l = layer ?: return
        checkpoint("Дублировать слой")
        val dup = l.copyDeep().also { it.name = l.name + " copy" }
        doc.layers.add(activeIndex + 1, dup)
        activeIndex += 1
        version++
    }

    fun deleteLayer() {
        if (doc.layers.size <= 1) {
            status = "Нельзя удалить последний слой"
            return
        }
        checkpoint("Удалить слой")
        doc.layers.removeAt(activeIndex)
        activeIndex = activeIndex.coerceIn(0, doc.layers.lastIndex)
        version++
    }

    fun moveLayer(up: Boolean) {
        val i = activeIndex
        val j = if (up) i + 1 else i - 1
        if (j !in doc.layers.indices) return
        checkpoint("Порядок слоёв")
        val a = doc.layers[i]
        doc.layers[i] = doc.layers[j]
        doc.layers[j] = a
        activeIndex = j
        version++
    }

    fun selectLayer(i: Int) {
        activeIndex = i.coerceIn(0, doc.layers.lastIndex)
        version++
    }

    fun toggleVisible(i: Int) {
        doc.layers.getOrNull(i)?.let {
            it.visible = !it.visible
            version++
        }
    }

    fun setOpacity(v: Float) {
        layer?.let {
            it.opacity = v.coerceIn(0f, 1f)
            version++
        }
    }

    fun setBlend(b: PsBlend) {
        layer?.let {
            it.blend = b
            version++
        }
    }

    fun renameLayer(name: String) {
        layer?.let {
            it.name = name
            version++
        }
    }

    fun flatten() {
        checkpoint("Выполнить сведение")
        val out = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        for (l in doc.layers) {
            if (!l.visible) continue
            val p = Paint()
            p.alpha = (l.opacity * 255).toInt()
            p.xfermode = PorterDuffXfermode(l.blend.porter)
            c.drawBitmap(l.bitmap, 0f, 0f, p)
        }
        doc = PsDocument(doc.width, doc.height, mutableListOf(Layer(PsDocument.nextId(), "Background", out)))
        activeIndex = 0
        version++
    }

    // ── кисти и заливка ────────────────────────────────────────────────────
    private val brushPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = true
    }

    fun beginStroke() {
        val l = layer ?: return
        strokeLayerId = l.id
        strokeShot = l.bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun strokeTo(x0: Float, y0: Float, x1: Float, y1: Float, eraser: Boolean) {
        val l = layer ?: return
        val c = Canvas(l.bitmap)
        brushPaint.strokeWidth = brushSize
        brushPaint.alpha = (brushOpacity.coerceIn(0.05f, 1f) * 255).toInt()
        brushPaint.maskFilter = if (brushHardness < 0.98f) {
            android.graphics.BlurMaskFilter(
                (brushSize * 0.22f * (1f - brushHardness)).coerceAtLeast(0.8f),
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        } else null
        if (eraser) {
            brushPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            brushPaint.color = Color.TRANSPARENT
        } else {
            brushPaint.xfermode = null
            brushPaint.color = foreground
        }
        c.drawLine(x0, y0, x1, y1, brushPaint)
        version++
    }

    fun endStroke(eraser: Boolean) {
        val shot = strokeShot ?: return
        strokeShot = null
        val label = if (eraser) "Ластик" else "Кисть"
        if (undo.size >= 6) undo.removeFirst()
        // другие слои не менялись — ссылки не копируем, только снимок активного
        val layers = doc.layers.map { l ->
            if (l.id == strokeLayerId) Layer(l.id, l.name, shot, l.visible, l.opacity, l.blend) else l
        }
        undo.addLast(UndoStep(label, PsDocument(doc.width, doc.height, layers.toMutableList()), activeIndex))
        redo.clear()
        redoAvailable = 0
        history = undo.map { it.label }
        version++
    }

    fun pick(x: Int, y: Int): Int? {
        val l = layer ?: return null
        if (x !in 0 until l.bitmap.width || y !in 0 until l.bitmap.height) return null
        return l.bitmap.getPixel(x, y)
    }

    fun fillAt(x: Int, y: Int) {
        val l = layer ?: return
        val w = l.bitmap.width
        val h = l.bitmap.height
        if (x !in 0 until w || y !in 0 until h) return
        checkpoint("Заливка")
        val pixels = IntArray(w * h)
        l.bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val target = pixels[y * w + x]
        val fill = foreground
        if (target == fill) return
        val tol = 40
        fun close(a: Int, b: Int): Boolean =
            kotlin.math.abs(Color.red(a) - Color.red(b)) <= tol &&
                kotlin.math.abs(Color.green(a) - Color.green(b)) <= tol &&
                kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= tol &&
                kotlin.math.abs(Color.alpha(a) - Color.alpha(b)) <= tol

        val cap = w * h
        val stack = IntArray(cap)
        var sp = 0
        stack[sp++] = y * w + x
        var filled = 0
        var guard = 0
        while (sp > 0 && guard < 8_000_000) {
            guard++
            val p = stack[--sp]
            if (!close(pixels[p], target)) continue
            pixels[p] = fill
            filled++
            val px = p % w
            val py = p / w
            if (sp < cap - 4) {
                if (px > 0) stack[sp++] = p - 1
                if (px < w - 1) stack[sp++] = p + 1
                if (py > 0) stack[sp++] = p - w
                if (py < h - 1) stack[sp++] = p + w
            }
        }
        l.bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        status = "Заливка: $filled пикс."
        version++
    }

    // ── текст ──────────────────────────────────────────────────────────────
    fun drawText(text: String, x: Float, y: Float, size: Float, color: Int) {
        val l = layer ?: return
        checkpoint("Текст")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        Canvas(l.bitmap).drawText(text, x, y, paint)
        version++
        status = "Текст добавлен"
    }

    // ── коррекции ──────────────────────────────────────────────────────────
    private fun applyMatrix(cm: ColorMatrix, label: String) {
        val l = layer ?: return
        checkpoint(label)
        val out = Bitmap.createBitmap(l.bitmap.width, l.bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(l.bitmap, 0f, 0f, paint)
        l.bitmap = out
        version++
    }

    fun brightnessContrast(brightness: Float, contrast: Float) {
        val c = contrast.coerceIn(-100f, 100f) / 100f * 255f
        val b = brightness.coerceIn(-100f, 100f) / 100f * 255f
        val scale = (259 * (c + 255)) / (255 * (259 - c))
        val cm = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, b,
                0f, scale, 0f, 0f, b,
                0f, 0f, scale, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        applyMatrix(cm, "Яркость/Контраст")
    }

    fun saturation(value: Float) {
        val cm = ColorMatrix()
        cm.setSaturation(value.coerceIn(0f, 3f))
        applyMatrix(cm, if (value == 0f) "Чёрно-белое" else "Насыщенность")
    }

    fun invert() {
        applyMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ),
            "Инверсия",
        )
    }

    fun blur() {
        val l = layer ?: return
        checkpoint("Размытие")
        val w = l.bitmap.width
        val h = l.bitmap.height
        val small = Bitmap.createBitmap((w / 6).coerceAtLeast(1), (h / 6).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(l.bitmap, null, Rect(0, 0, small.width, small.height), Paint(Paint.FILTER_BITMAP_FLAG))
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            small, null, Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        l.bitmap = out
        version++
    }

    // ── трансформации, уровни, тон, резкость ───────────────────────────────
    fun rotate90(clockwise: Boolean) {
        checkpoint(if (clockwise) "Поворот 90°" else "Поворот −90°")
        val nw = doc.height
        val nh = doc.width
        val out = doc.layers.map { l ->
            val bmp = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.translate(nw / 2f, nh / 2f)
            c.rotate(if (clockwise) 90f else -90f)
            c.translate(-doc.width / 2f, -doc.height / 2f)
            c.drawBitmap(l.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            Layer(l.id, l.name, bmp, l.visible, l.opacity, l.blend)
        }
        doc = PsDocument(nw, nh, out.toMutableList())
        version++
    }

    fun flip(horizontal: Boolean) {
        checkpoint(if (horizontal) "Отразить по горизонтали" else "Отразить по вертикали")
        doc.layers.forEach { l ->
            val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            if (horizontal) {
                c.scale(-1f, 1f, doc.width / 2f, doc.height / 2f)
            } else {
                c.scale(1f, -1f, doc.width / 2f, doc.height / 2f)
            }
            c.drawBitmap(l.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            l.bitmap = bmp
        }
        version++
    }

    fun resize(w: Int, h: Int) {
        val tw = w.coerceIn(16, 2400)
        val th = h.coerceIn(16, 2400)
        checkpoint("Размер $tw × $th")
        val out = doc.layers.map { l ->
            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawBitmap(
                l.bitmap, null, Rect(0, 0, tw, th),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
            Layer(l.id, l.name, bmp, l.visible, l.opacity, l.blend)
        }
        doc = PsDocument(tw, th, out.toMutableList())
        version++
    }

    /** Уровни: чёрная и белая точки входа 0..255. */
    fun levels(black: Float, white: Float) {
        val b = black.coerceIn(0f, 254f)
        val w = white.coerceIn(b + 1f, 255f)
        val scale = 255f / (w - b)
        applyMatrix(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, -b * scale,
                    0f, scale, 0f, 0f, -b * scale,
                    0f, 0f, scale, 0f, -b * scale,
                    0f, 0f, 0f, 1f, 0f,
                )
            ),
            "Уровни %.0f–%.0f".format(b, w),
        )
    }

    /** Экспозиция в EV: −2…+2. */
    fun exposure(ev: Float) {
        val g = 2f.pow(ev.coerceIn(-2f, 2f))
        applyMatrix(
            ColorMatrix(
                floatArrayOf(
                    g, 0f, 0f, 0f, 0f,
                    0f, g, 0f, 0f, 0f,
                    0f, 0f, g, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ),
            "Экспозиция %+.1f".format(ev),
        )
    }

    /** Температура: −100 холоднее, +100 теплее. */
    fun temperature(k: Float) {
        val t = (k.coerceIn(-100f, 100f) / 100f) * 0.3f
        applyMatrix(
            ColorMatrix(
                floatArrayOf(
                    1f + t, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - t, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ),
            "Температура %+.0f".format(k),
        )
    }

    /** Резкость: свёртка 3×3 (нерезкое маскирование по ядру). */
    fun sharpen() {
        val l = layer ?: return
        checkpoint("Резкость")
        val w = l.bitmap.width
        val h = l.bitmap.height
        val src = IntArray(w * h)
        l.bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)
        val k = intArrayOf(0, -1, 0, -1, 5, -1, 0, -1, 0)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var i = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val px = (x + dx).coerceIn(0, w - 1)
                        val py = (y + dy).coerceIn(0, h - 1)
                        val c = src[py * w + px]
                        val wgt = k[i++]
                        r += Color.red(c) * wgt
                        g += Color.green(c) * wgt
                        b += Color.blue(c) * wgt
                    }
                }
                val a = Color.alpha(src[y * w + x])
                dst[y * w + x] = Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
        }
        l.bitmap.setPixels(dst, 0, w, 0, 0, w, h)
        version++
        status = "Резкость применена"
    }

    fun exportJpg(file: File, quality: Int = 92): File {
        file.parentFile?.mkdirs()
        file.outputStream().use { composite().compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 100), it) }
        status = "Сохранено JPG: ${file.name} (${file.length() / 1024} KB)"
        return file
    }

    // ── эффекты и плагины ──────────────────────────────────────────────────
    /**
     * Применяет фильтр к активному слою. strength 0..1 — сила эффекта
     * (смешивание с оригиналом), чтобы не убивать фото сразу.
     */
    fun applyFilter(def: FilterDef, strength: Float = 1f) {
        val l = layer ?: return
        if (def.id == "original") return
        checkpoint(def.name)
        val w = l.bitmap.width
        val h = l.bitmap.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(def.matrix()) }
        c.drawBitmap(l.bitmap, 0f, 0f, paint)
        if (def.fade > 0f) {
            val p = Paint().apply {
                color = Color.WHITE
                alpha = (def.fade * 200).toInt()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        if (def.duotoneA != null && def.duotoneB != null) {
            val grad = android.graphics.LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor(def.duotoneA), Color.parseColor(def.duotoneB),
                android.graphics.Shader.TileMode.CLAMP,
            )
            val p = Paint().apply {
                shader = grad
                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                alpha = 210
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        if (def.vignette > 0f) {
            drawVignette(c, w, h, def.vignette)
        }
        if (def.grain > 0f) {
            FilterLibrary.grainOverlay(out, def.grain)
        }
        val s = strength.coerceIn(0f, 1f)
        if (s < 0.999f) {
            // смешиваем с оригиналом: сила эффекта
            c.drawBitmap(l.bitmap, 0f, 0f, Paint().apply { alpha = ((1f - s) * 255).toInt() })
        }
        l.bitmap = out
        version++
        status = "${def.name}: ${(s * 100).roundToInt()}%"
    }

    private fun drawVignette(c: Canvas, w: Int, h: Int, strength: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                w / 2f, h / 2f, maxOf(w, h) * 0.72f,
                intArrayOf(0x00000000, android.graphics.Color.argb((strength.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)),
                floatArrayOf(0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    fun quickVignette() {
        val l = layer ?: return
        checkpoint("Виньетка")
        drawVignette(Canvas(l.bitmap), l.bitmap.width, l.bitmap.height, 0.45f)
        version++
    }

    fun quickGrain() {
        val l = layer ?: return
        checkpoint("Зерно")
        FilterLibrary.grainOverlay(l.bitmap, 0.5f)
        version++
    }

    /** Рамка отдельным слоем — исходники не портит. */
    fun addFrame(color: Int, widthPx: Int) {
        checkpoint("Рамка")
        val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = widthPx.toFloat()
            this.color = color
        }
        val inset = widthPx / 2f
        c.drawRect(inset, inset, doc.width - inset, doc.height - inset, p)
        doc.layers.add(Layer(PsDocument.nextId(), "Frame", bmp))
        activeIndex = doc.layers.lastIndex
        version++
        status = "Рамка добавлена"
    }

    /** Эмодзи-стикер отдельным слоем по центру. */
    fun stampEmoji(emoji: String, sizePx: Float) {
        checkpoint("Стикер $emoji")
        val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            textAlign = Paint.Align.CENTER
        }
        c.drawText(emoji, doc.width / 2f, doc.height / 2f + sizePx / 3f, p)
        doc.layers.add(Layer(PsDocument.nextId(), "Sticker $emoji", bmp))
        activeIndex = doc.layers.lastIndex
        version++
        status = "Стикер $emoji"
    }

    // ── кадрирование и экспорт ─────────────────────────────────────────────
    fun crop(rect: RectF) {
        val x = rect.left.toInt().coerceIn(0, doc.width - 1)
        val y = rect.top.toInt().coerceIn(0, doc.height - 1)
        val w = rect.width().toInt().coerceIn(1, doc.width - x)
        val h = rect.height().toInt().coerceIn(1, doc.height - y)
        checkpoint("Кадрирование $w × $h")
        val newLayers = doc.layers.map { l ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawBitmap(l.bitmap, -x.toFloat(), -y.toFloat(), null)
            Layer(l.id, l.name, bmp, l.visible, l.opacity, l.blend)
        }
        doc = PsDocument(w, h, newLayers.toMutableList())
        version++
        status = "Кадрировано: $w × $h"
    }

    fun composite(): Bitmap {
        val out = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        for (l in doc.layers) {
            if (!l.visible) continue
            val p = Paint()
            p.alpha = (l.opacity * 255).toInt()
            p.xfermode = PorterDuffXfermode(l.blend.porter)
            c.drawBitmap(l.bitmap, 0f, 0f, p)
        }
        return out
    }

    fun exportPng(file: File): File {
        file.parentFile?.mkdirs()
        file.outputStream().use { composite().compress(Bitmap.CompressFormat.PNG, 100, it) }
        status = "Сохранено: ${file.name} (${file.length() / 1024} KB)"
        return file
    }

    fun loadFile(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

    /** Импорт PSD: слои приходят уже растрированными (PSD-ядро PhotoCraft). */
    fun importLayers(w: Int, h: Int, name: String, parsed: List<ParsedLayer>) {
        val layers = parsed.map { p ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            if (p.bitmap != null) {
                Canvas(bmp).drawBitmap(p.bitmap, p.x.toFloat(), p.y.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            Layer(PsDocument.nextId(), p.name, bmp, p.visible, p.opacity, p.blend)
        }
        doc = PsDocument(w, h, (if (layers.isEmpty()) mutableListOf(Layer(PsDocument.nextId(), "Layer 1", Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))) else layers).toMutableList())
        activeIndex = doc.layers.lastIndex
        undo.clear(); redo.clear(); history = emptyList(); redoAvailable = 0
        docName = name
        status = "PSD: ${parsed.size} слоёв, $w × $h"
        version++
    }
}