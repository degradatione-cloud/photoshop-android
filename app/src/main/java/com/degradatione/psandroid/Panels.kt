package com.degradatione.psandroid

import android.graphics.Color as AColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PanelsSheet(
    ed: Editor,
    journal: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    note: (String) -> Unit,
    onAction: (String) -> Unit,
) {
    val tabs = listOf("Слои", "Цвет", "Правка", "История")
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { i, title ->
                val sel = tab == i
                val bg by animateColorAsState(
                    if (sel) Ps.blue.copy(alpha = 0.15f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "tabBg",
                )
                val fg by animateColorAsState(if (sel) Ps.blue else Ps.textDim, animationSpec = tween(180), label = "tabFg")
                TapBox(
                    Modifier.background(bg, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    onClick = { onTab(i) },
                ) { Text(title, color = fg, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal) }
            }
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.97f, animationSpec = tween(160))) togetherWith fadeOut(tween(110))
            },
            label = "panelContent",
        ) { current ->
            when (current) {
                0 -> LayersPanel(ed, note)
                1 -> ColorPanel(ed, note)
                2 -> AdjustPanel(ed, onAction)
                else -> HistoryPanel(ed, journal, note)
            }
        }
    }
}

@Composable
private fun LayersPanel(ed: Editor, note: (String) -> Unit) {
    var blendOpen by remember { mutableStateOf(false) }
    val active = ed.layer
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TapBox(
                    Modifier.background(Ps.panelAlt, RoundedCornerShape(9.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp).width(88.dp),
                    onClick = { blendOpen = true },
                ) {
                    Text(
                        active?.blend?.label ?: "Normal", color = Ps.text, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = blendOpen, onDismissRequest = { blendOpen = false }, containerColor = Ps.panelAlt) {
                    for (b in PsBlend.entries) {
                        DropdownMenuItem(
                            text = { Text(b.label, color = Ps.text, fontSize = 12.5.sp) },
                            onClick = { ed.setBlend(b); note("Режим ${b.label}"); blendOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                Slider(
                    value = active?.opacity ?: 1f,
                    onValueChange = { ed.setOpacity(it) },
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent, inactiveTrackColor = Ps.border),
                )
            }
            Text(
                "${((active?.opacity ?: 1f) * 100).roundToInt()}%",
                color = Ps.textDim, fontSize = 10.sp, modifier = Modifier.width(30.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Ps.panelDark)) {
            items(ed.doc.layers.indices.reversed().toList(), key = { ed.doc.layers[it].id }) { idx ->
                val l = ed.doc.layers[idx]
                val sel = idx == ed.activeIndex
                val rowBg by animateColorAsState(
                    if (sel) Ps.blue.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "rowBg",
                )
                Row(
                    Modifier.fillMaxWidth().background(rowBg)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallIcon(
                        if (l.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        "Видимость",
                    ) { ed.toggleVisible(idx) }
                    TapBox(Modifier.size(34.dp), onClick = { ed.selectLayer(idx) }) {
                        Box(
                            Modifier.size(34.dp).background(Color(0xFF242424), RoundedCornerShape(4.dp))
                                .border(1.dp, Ps.border, RoundedCornerShape(4.dp))
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = l.bitmap.asImageBitmap(),
                                contentDescription = l.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            l.name, color = if (sel) Ps.text else Ps.textDim, fontSize = 11.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${l.blend.label} · ${(l.opacity * 100).roundToInt()}%",
                            color = Ps.textFaint, fontSize = 9.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIcon(Icons.Rounded.Add, "Новый слой") { ed.addLayer() }
            SmallIcon(Icons.Rounded.ContentCopy, "Дублировать") { ed.duplicateLayer() }
            SmallIcon(Icons.Rounded.ArrowUpward, "Вверх") { ed.moveLayer(true) }
            SmallIcon(Icons.Rounded.ArrowDownward, "Вниз") { ed.moveLayer(false) }
            Spacer(Modifier.weight(1f))
            SmallIcon(Icons.Rounded.Delete, "Удалить") { ed.deleteLayer(); note(ed.status) }
        }
    }
}

@Composable
private fun ColorPanel(ed: Editor, note: (String) -> Unit) {
    val hsv = remember { FloatArray(3) }
    AColor.colorToHSV(ed.foreground, hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun push() {
        ed.foreground = AColor.HSVToColor(floatArrayOf(hue, sat, value))
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(Color(ed.foreground), RoundedCornerShape(6.dp))
                    .border(1.dp, Ps.border, RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(6.dp))
            TapBox(
                Modifier.size(28.dp).background(Color(ed.background), RoundedCornerShape(6.dp))
                    .border(1.dp, Ps.border, RoundedCornerShape(6.dp)),
                onClick = {
                    val t = ed.foreground
                    ed.foreground = ed.background
                    ed.background = t
                    val h = FloatArray(3)
                    AColor.colorToHSV(ed.foreground, h)
                    hue = h[0]; sat = h[1]; value = h[2]
                },
            )
            Spacer(Modifier.width(10.dp))
            Text("#%06X".format(ed.foreground and 0xFFFFFF), color = Ps.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${ed.brushSize.roundToInt()} px", color = Ps.textFaint, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        val hueColor = Color(AColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Canvas(
            Modifier.fillMaxWidth().height(118.dp).border(1.dp, Ps.border)
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        sat = (p.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        push()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            sat = (p.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            push()
                        },
                    ) { change, _ ->
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        push()
                        change.consume()
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, 6f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawCircle(Color.Black, 8f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(16.dp).border(1.dp, Ps.border)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { p ->
                        hue = (p.x / size.width * 359f).coerceIn(0f, 359f); push()
                    }) { change, _ ->
                        hue = (change.position.x / size.width * 359f).coerceIn(0f, 359f)
                        push()
                        change.consume()
                    }
                },
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                )
            )
            val x = hue / 359f * size.width
            drawRect(Color.White, Offset(x - 1.5f, 0f), androidx.compose.ui.geometry.Size(3f, size.height))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (s in listOf("#000000", "#FFFFFF", "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#007AFF", "#AF52DE", "#8E8E93")) {
                val c = AColor.parseColor(s)
                TapBox(
                    Modifier.size(26.dp).background(Color(c), RoundedCornerShape(5.dp))
                        .border(1.dp, Ps.border, RoundedCornerShape(5.dp)),
                    onClick = {
                        ed.foreground = c
                        val h = FloatArray(3)
                        AColor.colorToHSV(c, h)
                        hue = h[0]; sat = h[1]; value = h[2]
                        note("Цвет $s")
                    },
                ) {}
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AdjustPanel(ed: Editor, onAction: (String) -> Unit) {
    val items = listOf(
        "Поворот" to "rotate",
        "Отразить ↔" to "flipH",
        "Отразить ↕" to "flipV",
        "Размер…" to "resize",
        "Кадр" to "crop",
        "Уровни…" to "levels",
        "Экспозиция…" to "exposure",
        "Температура…" to "temp",
        "Резкость" to "sharpen",
        "Ярк/Контр…" to "bright",
        "Насыщенность…" to "sat",
        "Ч/Б" to "bw",
        "Инверсия" to "invert",
        "Размытие" to "blur",
        "Новый слой" to "layerAdd",
        "Свести" to "flatten",
        "Сохранить PNG" to "png",
        "Поделиться JPG" to "shareJpg",
    )
    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        Text("ИНСТРУМЕНТЫ ИЗОБРАЖЕНИЯ", color = Ps.textFaint, fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        // сетка 3 в ряд, на маленьком экране это компактнее списка
        val rows = items.chunked(3)
        for (row in rows) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (it in row) {
                    TapBox(
                        Modifier.weight(1f).background(Ps.panelAlt, RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp),
                        onClick = { onAction(it.second) },
                    ) {
                        Text(it.first, color = Ps.text, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Слой: ${ed.layer?.name ?: "—"} · ${ed.doc.layers.size} шт", color = Ps.textFaint, fontSize = 9.5.sp)
    }
}

@Composable
private fun HistoryPanel(ed: Editor, journal: List<String>, note: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Отменить") { ed.undoStep(); note("Отменено") }
            PsChip("Повторить") { ed.redoStep(); note("Повторено") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Ps.panelDark).padding(horizontal = 8.dp)) {
            items(ed.history.reversed()) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("›", color = Ps.blue, fontSize = 11.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(h, color = Ps.text, fontSize = 11.sp)
                }
            }
            if (ed.history.isEmpty()) {
                item { Text("История пуста", color = Ps.textFaint, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            item { Spacer(Modifier.height(6.dp)) }
            item { Text("ЖУРНАЛ", color = Ps.textFaint, fontSize = 9.sp, letterSpacing = 1.sp) }
            items(journal) { line ->
                Text(
                    line, color = Ps.textFaint, fontSize = 9.5.sp,
                    style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
fun SmallIcon(icon: ImageVector, desc: String, tint: Color = Ps.textDim, onClick: () -> Unit) {
    TapBox(Modifier.size(34.dp), scaleTo = 0.85f, onClick = onClick) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(15.dp))
    }
}

// ── диалоги ────────────────────────────────────────────────────────────────
@Composable
private fun PsDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(Ps.panel, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, color = Ps.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PsField(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = false, onChange: (String) -> Unit) {
    Column(modifier) {
        Text(label, color = Ps.textFaint, fontSize = 9.5.sp)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().background(Ps.panelDark, RoundedCornerShape(8.dp))
                .border(1.dp, Ps.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { v -> onChange(if (numeric) v.filter { it.isDigit() }.take(5) else v) },
                textStyle = TextStyle(color = Ps.text, fontSize = 12.5.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun NewDocDialog(onDismiss: () -> Unit, onCreate: (Int, Int, Boolean) -> Unit) {
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1440") }
    var transparent by remember { mutableStateOf(false) }
    PsDialog("Новый документ", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsField("Ширина", w, Modifier.weight(1f), numeric = true) { w = it }
            PsField("Высота", h, Modifier.weight(1f), numeric = true) { h = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (p in listOf(Triple("1:1", 1080, 1080), Triple("16:9", 1920, 1080), Triple("3:4", 1080, 1440))) {
                PsChip(p.first) { onCreate(p.second, p.third, transparent) }
            }
        }
        Spacer(Modifier.height(8.dp))
        PsChip(if (transparent) "Фон: прозрачный" else "Фон: белый") { transparent = !transparent }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Создать", accent = true) { onCreate(w.toIntOrNull() ?: 1080, h.toIntOrNull() ?: 1440, transparent) }
            PsChip("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun NumberPairDialog(
    title: String,
    aLabel: String,
    bLabel: String,
    a0: String,
    b0: String,
    onDismiss: () -> Unit,
    onApply: (Int, Int) -> Unit,
) {
    var a by remember { mutableStateOf(a0) }
    var b by remember { mutableStateOf(b0) }
    PsDialog(title, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsField(aLabel, a, Modifier.weight(1f), numeric = true) { a = it }
            PsField(bLabel, b, Modifier.weight(1f), numeric = true) { b = it }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Применить", accent = true) {
                onApply(a.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0)
            }
            PsChip("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun LevelDialog(onDismiss: () -> Unit, onApply: (Float, Float) -> Unit) {
    var black by remember { mutableFloatStateOf(0f) }
    var white by remember { mutableFloatStateOf(255f) }
    PsDialog("Уровни", onDismiss) {
        Text("Чёрная точка: ${black.roundToInt()}", color = Ps.text, fontSize = 11.sp)
        Slider(
            value = black, onValueChange = { black = it.coerceAtMost(white - 1f) }, valueRange = 0f..254f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Text("Белая точка: ${white.roundToInt()}", color = Ps.text, fontSize = 11.sp)
        Slider(
            value = white, onValueChange = { white = it.coerceAtLeast(black + 1f) }, valueRange = 1f..255f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Применить", accent = true) { onApply(black, white) }
            PsChip("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun TwoSliderDialog(
    title: String,
    aLabel: String,
    bLabel: String,
    minA: Float,
    maxA: Float,
    minB: Float,
    maxB: Float,
    onApply: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableFloatStateOf((minA + maxA) / 2f) }
    var b by remember { mutableFloatStateOf((minB + maxB) / 2f) }
    PsDialog(title, onDismiss) {
        Text("$aLabel: ${a.roundToInt()}", color = Ps.text, fontSize = 11.sp)
        Slider(
            value = a, onValueChange = { a = it }, valueRange = minA..maxA,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Text("$bLabel: ${b.roundToInt()}", color = Ps.text, fontSize = 11.sp)
        Slider(
            value = b, onValueChange = { b = it }, valueRange = minB..maxB,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Применить", accent = true) { onApply(a, b) }
            PsChip("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun OneSliderDialog(
    title: String,
    label: String,
    initial: Float,
    min: Float,
    max: Float,
    percent: Boolean,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var v by remember { mutableFloatStateOf(initial) }
    PsDialog(title, onDismiss) {
        Text(
            "$label: " + if (percent) "${(v * 100).roundToInt()}%" else "%.1f".format(v),
            color = Ps.text, fontSize = 11.sp,
        )
        Slider(
            value = v, onValueChange = { v = it }, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Применить", accent = true) { onApply(v) }
            PsChip("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun TextDialog(onDismiss: () -> Unit, onApply: (String, Float, Int) -> Unit) {
    var text by remember { mutableStateOf("Текст") }
    var size by remember { mutableFloatStateOf(64f) }
    var hex by remember { mutableStateOf("#FFFFFF") }
    PsDialog("Текст", onDismiss) {
        PsField("Содержимое", text, Modifier.fillMaxWidth()) { text = it }
        Spacer(Modifier.height(8.dp))
        Text("Размер: ${size.roundToInt()} px", color = Ps.text, fontSize = 11.sp)
        Slider(
            value = size, onValueChange = { size = it }, valueRange = 12f..300f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (c in listOf("#FFFFFF", "#000000", "#FF3B30", "#007AFF")) {
                TapBox(
                    Modifier.size(26.dp).background(Color(AColor.parseColor(c)), RoundedCornerShape(5.dp))
                        .border(if (hex == c) 2.dp else 1.dp, if (hex == c) Ps.blue else Ps.border, RoundedCornerShape(5.dp)),
                    onClick = { hex = c },
                ) {}
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsChip("Добавить", accent = true) { onApply(text, size, AColor.parseColor(hex)) }
            PsChip("Отмена") { onDismiss() }
        }
    }
}
