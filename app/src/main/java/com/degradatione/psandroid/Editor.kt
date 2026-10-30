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
}