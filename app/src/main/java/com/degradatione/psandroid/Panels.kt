package com.degradatione.psandroid

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Undo
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
fun PsButton(text: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (accent) Ps.accent else Ps.panelAlt, RoundedCornerShape(4.dp))
            .border(1.dp, if (accent) Ps.accent else Ps.border, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (accent) Color.White else Ps.text, fontSize = 11.5.sp)
    }
}

@Composable
fun SmallIcon(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = Ps.textDim) {
    Box(
        Modifier.size(34.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun PanelsSheet(
    ed: Editor,
    journal: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    note: (String) -> Unit,
) {
    val tabs = listOf("Слои", "Цвет", "История", "Журнал")
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { i, title ->
                Box(
                    Modifier
                        .background(if (tab == i) Ps.selected else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTab(i) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(title, color = if (tab == i) Ps.blue else Ps.textDim, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        when (tab) {
            0 -> LayersPanel(ed, note)
            1 -> ColorPanel(ed, note)
            2 -> HistoryPanel(ed, note)
            else -> JournalPanel(journal)
        }
    }
}

@Composable
private fun LayersPanel(ed: Editor, note: (String) -> Unit) {
    var blendOpen by remember { mutableStateOf(false) }
    val active = ed.layer
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier.background(Ps.panelAlt, RoundedCornerShape(4.dp))
                        .border(1.dp, Ps.border, RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { blendOpen = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .width(112.dp)
                ) {
                    Text(
                        active?.blend?.label ?: "Normal",
                        color = Ps.text, fontSize = 11.5.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = blendOpen, onDismissRequest = { blendOpen = false }, containerColor = Ps.panelAlt) {
                    for (b in PsBlend.entries) {
                        DropdownMenuItem(
                            text = { Text(b.label, color = Ps.text, fontSize = 13.sp) },
                            onClick = { ed.setBlend(b); note("Режим: ${b.label}"); blendOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("Непрозр.", color = Ps.textFaint, fontSize = 10.5.sp, modifier = Modifier.width(50.dp))
            Slider(
                value = active?.opacity ?: 1f,
                onValueChange = { ed.setOpacity(it) },
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Ps.text,
                    activeTrackColor = Ps.accent,
                    inactiveTrackColor = Ps.panelDark,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${((active?.opacity ?: 1f) * 100).roundToInt()}%",
                color = Ps.textDim, fontSize = 10.5.sp, modifier = Modifier.width(34.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Ps.panelDark)) {
            items(ed.doc.layers.indices.reversed().toList()) { idx ->
                val l = ed.doc.layers[idx]
                val sel = idx == ed.activeIndex
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (sel) Ps.selected else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { ed.selectLayer(idx) }
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallIcon(
                        if (l.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        "Видимость",
                    ) { ed.toggleVisible(idx) }
                    Box(
                        Modifier.size(38.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
                            .border(1.dp, Ps.border, RoundedCornerShape(2.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = l.bitmap.asImageBitmap(),
                            contentDescription = l.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            l.name, color = if (sel) Color.White else Ps.text, fontSize = 11.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${l.blend.label} · ${(l.opacity * 100).roundToInt()}%",
                            color = Ps.textFaint, fontSize = 9.5.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIcon(Icons.Rounded.Add, "Новый слой") { ed.addLayer(); note("Новый слой") }
            SmallIcon(Icons.Rounded.ContentCopy, "Дублировать") { ed.duplicateLayer(); note("Дублирован") }
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
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }

    fun push() {
        ed.foreground = AColor.HSVToColor(floatArrayOf(hue, sat, value))
    }

    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp)
                    .background(Color(ed.foreground), RoundedCornerShape(3.dp))
                    .border(1.dp, Ps.border, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(30.dp)
                    .background(Color(ed.background), RoundedCornerShape(3.dp))
                    .border(1.dp, Ps.border, RoundedCornerShape(3.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val t = ed.foreground
                        ed.foreground = ed.background
                        ed.background = t
                        AColor.colorToHSV(ed.foreground, hsv)
                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        note("Цвета поменяны")
                    }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "#%06X".format(ed.foreground and 0xFFFFFF),
                color = Ps.text, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text("HSL", color = Ps.textFaint, fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        val hueColor = Color(AColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .border(1.dp, Ps.border)
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
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        sat = (p.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        push()
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, radius = 6f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawCircle(Color.Black, radius = 8f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(18.dp)
                .border(1.dp, Ps.border)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            hue = (p.x / size.width * 360f).coerceIn(0f, 359f); push()
                        },
                    ) { change, _ ->
                        hue = (change.position.x / size.width * 360f).coerceIn(0f, 359f)
                        push()
                        change.consume()
                    }
                }
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                        Color.Blue, Color.Magenta, Color.Red,
                    )
                )
            )
            val x = hue / 360f * size.width
            drawRect(
                Color.White,
                topLeft = Offset(x - 1.5f, 0f),
                size = androidx.compose.ui.geometry.Size(3f, size.height),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Образцы", color = Ps.textFaint, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val swatches = listOf(
                "#000000", "#FFFFFF", "#FF3B30", "#FF9500", "#FFCC00",
                "#34C759", "#00C7BE", "#007AFF", "#5856D6", "#AF52DE", "#8E8E93",
            )
            for (s in swatches) {
                val c = AColor.parseColor(s)
                Box(
                    Modifier.size(26.dp)
                        .background(Color(c), RoundedCornerShape(3.dp))
                        .border(1.dp, Ps.border, RoundedCornerShape(3.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            ed.foreground = c
                            AColor.colorToHSV(c, hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            note("Цвет $s")
                        }
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(ed: Editor, note: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PsButton("Отменить") { ed.undoStep(); note("Отменено") }
            PsButton("Повторить") { ed.redoStep(); note("Повторено") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        LazyColumn(Modifier.fillMaxWidth().background(Ps.panelDark)) {
            items(ed.history.reversed()) { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("›", color = Ps.blue, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(h, color = Ps.text, fontSize = 11.5.sp)
                }
            }
            if (ed.history.isEmpty()) {
                item {
                    Text(
                        "История пуста — сделай любое действие",
                        color = Ps.textFaint, fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalPanel(journal: List<String>) {
    LazyColumn(Modifier.fillMaxWidth().background(Ps.panelDark).padding(8.dp)) {
        items(journal) { line ->
            Text(
                line,
                color = Ps.textDim,
                fontSize = 10.5.sp,
                style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
        if (journal.isEmpty()) {
            item { Text("Журнал пуст", color = Ps.textFaint, fontSize = 11.sp) }
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
            PsField("Ширина", w, Modifier.weight(1f)) { w = it.filter { c -> c.isDigit() }.take(5) }
            PsField("Высота", h, Modifier.weight(1f)) { h = it.filter { c -> c.isDigit() }.take(5) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, ww, hh) in listOf(
                Triple("1080×1080", 1080, 1080),
                Triple("1920×1080", 1920, 1080),
                Triple("1080×1440", 1080, 1440),
            )) {
                PsButton(label) {
                    onCreate(ww, hh, transparent)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PsButton(if (transparent) "Фон: прозрачный" else "Фон: белый") { transparent = !transparent }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsButton("Создать", accent = true) {
                onCreate(w.toIntOrNull() ?: 1080, h.toIntOrNull() ?: 1440, transparent)
            }
            PsButton("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun TwoSliderDialog(
    title: String,
    aLabel: String,
    bLabel: String,
    onApply: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableFloatStateOf(0f) }
    var b by remember { mutableFloatStateOf(0f) }
    PsDialog(title, onDismiss) {
        Text("$aLabel: ${a.roundToInt()}", color = Ps.text, fontSize = 11.5.sp)
        Slider(
            value = a, onValueChange = { a = it }, valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Text("$bLabel: ${b.roundToInt()}", color = Ps.text, fontSize = 11.5.sp)
        Slider(
            value = b, onValueChange = { b = it }, valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsButton("Применить", accent = true) { onApply(a, b) }
            PsButton("Отмена") { onDismiss() }
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
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var v by remember { mutableFloatStateOf(initial) }
    PsDialog(title, onDismiss) {
        Text("$label: ${(v * 100).roundToInt()}%", color = Ps.text, fontSize = 11.5.sp)
        Slider(
            value = v, onValueChange = { v = it }, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsButton("Применить", accent = true) { onApply(v) }
            PsButton("Отмена") { onDismiss() }
        }
    }
}

@Composable
fun TextDialog(onDismiss: () -> Unit, onApply: (String, Float, Int) -> Unit) {
    var text by remember { mutableStateOf("Текст") }
    var size by remember { mutableFloatStateOf(64f) }
    var colorHex by remember { mutableStateOf("#FFFFFF") }
    PsDialog("Текст", onDismiss) {
        PsField("Содержимое", text, Modifier.fillMaxWidth()) { text = it }
        Spacer(Modifier.height(8.dp))
        Text("Размер: ${size.roundToInt()} px", color = Ps.text, fontSize = 11.5.sp)
        Slider(
            value = size, onValueChange = { size = it }, valueRange = 12f..300f,
            colors = SliderDefaults.colors(thumbColor = Ps.text, activeTrackColor = Ps.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in listOf("#FFFFFF", "#000000", "#FF3B30", "#007AFF")) {
                Box(
                    Modifier.size(26.dp)
                        .background(Color(AColor.parseColor(c)), RoundedCornerShape(3.dp))
                        .border(if (colorHex == c) 2.dp else 1.dp, if (colorHex == c) Ps.blue else Ps.border, RoundedCornerShape(3.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { colorHex = c }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PsButton("Добавить", accent = true) {
                onApply(text, size, AColor.parseColor(colorHex))
            }
            PsButton("Отмена") { onDismiss() }
        }
    }
}

@Composable
private fun PsDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Ps.panel, RoundedCornerShape(6.dp))
                .border(1.dp, Ps.border, RoundedCornerShape(6.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, color = Ps.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PsField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        Text(label, color = Ps.textFaint, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(Ps.panelDark, RoundedCornerShape(3.dp))
                .border(1.dp, Ps.border, RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = Ps.text, fontSize = 12.5.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}