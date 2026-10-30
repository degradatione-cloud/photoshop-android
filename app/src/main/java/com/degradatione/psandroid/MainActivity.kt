package com.degradatione.psandroid

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ── палитра: Photoshop CC + живой акцент ───────────────────────────────────
object Ps {
    val canvasBg = Color(0xFF141414)
    val panel = Color(0xFF1F1F1F)
    val panelAlt = Color(0xFF272727)
    val panelDark = Color(0xFF191919)
    val border = Color(0xFF333333)
    val text = Color(0xFFEDEDED)
    val textDim = Color(0xFF9C9C9C)
    val textFaint = Color(0xFF6E6E6E)
    val blue = Color(0xFF31A8FF)
    val accent = Color(0xFF1473E6)
    val danger = Color(0xFFFF6961)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ed = Editor()
        setContent { PsTheme { PsApp(ed) } }
    }
}

@Composable
fun PsTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Ps.accent,
            background = Ps.canvasBg,
            surface = Ps.panel,
            onSurface = Ps.text,
            outline = Ps.border,
            error = Ps.danger,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsApp(ed: Editor) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val journal = remember { mutableStateListOf<String>() }

    fun note(msg: String) {
        ed.status = msg
        journal.add(
            0,
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " · " + msg,
        )
        if (journal.size > 80) journal.removeAt(journal.lastIndex)
    }

    var zoom by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showPanels by remember { mutableStateOf(false) }
    var panelTab by remember { mutableIntStateOf(0) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var textPoint by remember { mutableStateOf<Offset?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var pill by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ed.status) {
        val s = ed.status
        if (s.isNotBlank()) {
            pill = s
            delay(2600)
            if (pill == s) pill = null
        }
    }

    fun fit() {
        if (canvasSize.width == 0) return
        val z = minOf(
            (canvasSize.width - 24).toFloat() / ed.doc.width,
            (canvasSize.height - 24).toFloat() / ed.doc.height,
        ).coerceIn(0.05f, 8f)
        zoom = z
        pan = Offset(
            (canvasSize.width - ed.doc.width * z) / 2f,
            (canvasSize.height - ed.doc.height * z) / 2f,
        )
    }

    LaunchedEffect(canvasSize, ed.doc.width, ed.doc.height) { if (zoom == 0f) fit() }

    LaunchedEffect(Unit) {
        note("Photoshop 0.2 · ${PsdBridge.status()}")
        runCatching {
            val r = Updater.check(Updater.DEFAULT_REPO)
            if (r != null && r.versionCode > Updater.installedVersionCode(ctx)) update = r
        }
    }

    fun openBitmap(bmp: Bitmap, name: String) {
        ed.openImage(bmp, name)
        note("Открыто $name · ${bmp.width}×${bmp.height}")
        zoom = 0f
        fit()
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Открываю фото…"
            runCatching {
                val bmp = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
                            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                    }
                }
                openBitmap(bmp, "photo-${System.currentTimeMillis() % 10000}")
            }.onFailure { note("Фото не открылось: ${it.message}") }
            busy = null
        }
    }

    val pickPsd = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Разбираю PSD…"
            val res = withContext(Dispatchers.IO) {
                runCatching { PsdImporter.import(ctx, uri, 1600) }
            }
            res.onSuccess { imp ->
                ed.importLayers(imp.width, imp.height, imp.name, imp.layers)
                note("PSD: ${imp.layers.size} слоёв · ${imp.width}×${imp.height}")
                zoom = 0f
                fit()
            }.onFailure { note("PSD: ${it.message}") }
            busy = null
        }
    }

    fun savePng() = runCatching {
        val bmp = ed.composite()
        val name = "PS_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Photoshop")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore отказал")
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        note("PNG → Pictures/Photoshop/$name")
    }.onFailure { note("PNG не сохранился: ${it.message}") }

    fun shareImage(jpg: Boolean) = runCatching {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "ps-${System.currentTimeMillis()}.${if (jpg) "jpg" else "png"}")
        if (jpg) ed.exportJpg(file) else ed.exportPng(file)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = if (jpg) "image/jpeg" else "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Поделиться",
            )
        )
        note("Экспорт ${file.length() / 1024} KB")
    }.onFailure { note("Экспорт: ${it.message}") }

    fun action(key: String) {
        menuOpen = false
        when (key) {
            "new" -> dialog = "new"
            "open" -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "psd" -> pickPsd.launch(arrayOf("*/*"))
            "png" -> savePng()
            "share" -> shareImage(false)
            "shareJpg" -> shareImage(true)
            "undo" -> ed.undoStep()
            "redo" -> ed.redoStep()
            "flatten" -> ed.flatten()
            "rotate" -> { ed.rotate90(true); zoom = 0f; fit() }
            "flipH" -> ed.flip(true)
            "flipV" -> ed.flip(false)
            "resize" -> dialog = "resize"
            "levels" -> dialog = "levels"
            "exposure" -> dialog = "exposure"
            "temp" -> dialog = "temp"
            "sharpen" -> ed.sharpen()
            "bright" -> dialog = "bc"
            "sat" -> dialog = "sat"
            "bw" -> ed.saturation(0f)
            "invert" -> ed.invert()
            "blur" -> ed.blur()
            "crop" -> { ed.tool = PsTool.CROP; cropRect = null; note("Тяни рамку по холсту") }
            "layerAdd" -> ed.addLayer()
            "layerDup" -> ed.duplicateLayer()
            "layerDel" -> ed.deleteLayer()
            "panels" -> showPanels = true
            "update" -> scope.launch {
                val r = Updater.check(Updater.DEFAULT_REPO)
                if (r != null && r.versionCode > Updater.installedVersionCode(ctx)) {
                    update = r
                    note("Найдено ${r.tag}")
                } else {
                    note("Обновлений нет")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Ps.canvasBg).systemBarsPadding().imePadding()) {
        TopBar(ed = ed, zoom = zoom, menuOpen = menuOpen, onMenu = { menuOpen = it }, onAction = { action(it) })

        update?.let { r ->
            Row(
                Modifier.fillMaxWidth().background(Ps.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Ps.blue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("Доступно ${r.tag}", color = Ps.text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                PsChip("Обновить", accent = true) {
                    scope.launch {
                        runCatching {
                            val f = Updater.download(ctx, r) { _, _ -> }
                            Updater.install(ctx, f)
                            note("Установщик открыт")
                        }.onFailure { note("Обновление: ${it.message}") }
                        update = null
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }) {
            val checker = remember { checkerBrush() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(ed.tool, ed.doc.width, ed.doc.height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tool = ed.tool
                        var last = toDoc(down.position, pan, zoom)
                        var moved = false
                        val cropStart = last
                        if (tool == PsTool.BRUSH || tool == PsTool.ERASER) ed.beginStroke()
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val pressed = event.changes.filter { it.pressed }
                            if (event.changes.size >= 2) {
                                val a = event.changes[0]
                                val b = event.changes[1]
                                val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                                val curDist = (a.position - b.position).getDistance()
                                val factor = if (prevDist > 1f) (curDist / prevDist).coerceIn(0.5f, 2f) else 1f
                                val prevC = (a.previousPosition + b.previousPosition) / 2f
                                val curC = (a.position + b.position) / 2f
                                val nz = (zoom * factor).coerceIn(0.05f, 8f)
                                pan = curC - (prevC - pan) * (nz / zoom)
                                zoom = nz
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val pos = pressed[0].position
                                val docPt = toDoc(pos, pan, zoom)
                                when (tool) {
                                    PsTool.BRUSH -> ed.strokeTo(last.x, last.y, docPt.x, docPt.y, false)
                                    PsTool.ERASER -> ed.strokeTo(last.x, last.y, docPt.x, docPt.y, true)
                                    PsTool.MOVE -> pan += pos - pressed[0].previousPosition
                                    PsTool.CROP -> cropRect = Rect(
                                        minOf(cropStart.x, docPt.x), minOf(cropStart.y, docPt.y),
                                        maxOf(cropStart.x, docPt.x), maxOf(cropStart.y, docPt.y),
                                    )
                                    PsTool.EYEDROPPER -> ed.pick(docPt.x.toInt(), docPt.y.toInt())?.let {
                                        ed.foreground = it
                                        note("Цвет #%06X".format(it and 0xFFFFFF))
                                    }
                                    PsTool.TEXT, PsTool.FILL, PsTool.ZOOM -> Unit
                                }
                                if ((pos - down.position).getDistance() > 4f) moved = true
                                last = docPt
                                pressed[0].consume()
                            }
                            event = awaitPointerEvent()
                        }
                        when (tool) {
                            PsTool.BRUSH, PsTool.ERASER -> ed.endStroke(tool == PsTool.ERASER)
                            PsTool.FILL -> ed.fillAt(last.x.toInt(), last.y.toInt())
                            PsTool.TEXT -> if (!moved) textPoint = last
                            PsTool.ZOOM -> {
                                val t = last
                                val nz = (zoom * 1.7f).coerceIn(0.05f, 8f)
                                pan = Offset(canvasSize.width / 2f - t.x * nz, canvasSize.height / 2f - t.y * nz)
                                zoom = nz
                            }
                            else -> Unit
                        }
                    }
                }
            ) {
                ed.version
                if (zoom <= 0f) return@Canvas
                withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, pivot = Offset.Zero) }) {
                    drawRect(checker, Offset.Zero, Size(ed.doc.width.toFloat(), ed.doc.height.toFloat()))
                    for (l in ed.doc.layers) {
                        if (!l.visible) continue
                        drawImage(l.bitmap.asImageBitmap(), Offset.Zero, alpha = l.opacity, blendMode = l.blend.compose)
                    }
                    cropRect?.let { r ->
                        drawRect(Color.Black.copy(alpha = 0.45f), Offset(r.left, r.top), Size(r.width, r.height))
                        drawRect(
                            Ps.blue, Offset(r.left, r.top), Size(r.width, r.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / zoom),
                        )
                    }
                }
                drawRect(
                    Color.Black, Offset(pan.x - 1, pan.y - 1),
                    Size(ed.doc.width * zoom + 2, ed.doc.height * zoom + 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                )
            }

            AnimatedVisibility(
                visible = pill != null,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
                exit = fadeOut(tween(140)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) { StatusPill(pill.orEmpty()) }

            AnimatedVisibility(visible = busy != null, modifier = Modifier.align(Alignment.Center)) {
                StatusPill(busy ?: "")
            }

            AnimatedVisibility(
                visible = cropRect != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, bottom = 8.dp),
            ) {
                Row(
                    Modifier.background(Ps.panel, RoundedCornerShape(18.dp)).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PsChip("Применить", accent = true) {
                        cropRect?.let { r ->
                            if (r.width > 4 && r.height > 4) {
                                ed.crop(android.graphics.RectF(r.left, r.top, r.right, r.bottom))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        cropRect = null
                    }
                    PsChip("Отмена") { cropRect = null }
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                PsMiniIcon("−") { zoom = (zoom / 1.35f).coerceAtLeast(0.05f) }
                PsMiniIcon("${(zoom * 100).roundToInt()}%") { fit() }
                PsMiniIcon("+") { zoom = (zoom * 1.35f).coerceAtMost(8f) }
            }
        }

        BrushStrip(ed)
        ToolDock(ed = ed, onPanels = { showPanels = true; panelTab = 0 }) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    if (showPanels) {
        ModalBottomSheet(
            onDismissRequest = { showPanels = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Ps.panel,
            contentColor = Ps.text,
            dragHandle = {
                Box(
                    Modifier.padding(vertical = 7.dp).size(width = 32.dp, height = 3.dp)
                        .background(Ps.border, RoundedCornerShape(2.dp))
                )
            },
        ) {
            PanelsSheet(
                ed = ed,
                journal = journal,
                tab = panelTab,
                onTab = { panelTab = it },
                note = { note(it) },
                onAction = { action(it) },
            )
        }
    }

    when (dialog) {
        "new" -> NewDocDialog(
            onDismiss = { dialog = null },
            onCreate = { w, h, tr ->
                ed.newDocument(w, h, tr)
                dialog = null
                zoom = 0f
                fit()
                note("Новый документ $w×$h")
            },
        )
        "resize" -> NumberPairDialog(
            title = "Размер изображения",
            aLabel = "Ширина",
            bLabel = "Высота",
            a0 = ed.doc.width.toString(),
            b0 = ed.doc.height.toString(),
            onDismiss = { dialog = null },
            onApply = { w, h -> ed.resize(w, h); dialog = null; zoom = 0f; fit() },
        )
        "levels" -> LevelDialog(
            onDismiss = { dialog = null },
            onApply = { b, w -> ed.levels(b, w); dialog = null },
        )
        "exposure" -> OneSliderDialog(
            title = "Экспозиция", label = "EV",
            initial = 0f, min = -2f, max = 2f, percent = false,
            onApply = { ed.exposure(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "temp" -> OneSliderDialog(
            title = "Температура", label = "Сдвиг",
            initial = 0f, min = -100f, max = 100f, percent = false,
            onApply = { ed.temperature(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "bc" -> TwoSliderDialog(
            title = "Яркость/Контраст",
            aLabel = "Яркость", bLabel = "Контраст",
            minA = -100f, maxA = 100f, minB = -100f, maxB = 100f,
            onApply = { a, b -> ed.brightnessContrast(a, b); dialog = null },
            onDismiss = { dialog = null },
        )
        "sat" -> OneSliderDialog(
            title = "Насыщенность", label = "Насыщенность",
            initial = 1f, min = 0f, max = 2f, percent = true,
            onApply = { ed.saturation(it); dialog = null },
            onDismiss = { dialog = null },
        )
    }

    textPoint?.let { pt ->
        TextDialog(
            onDismiss = { textPoint = null },
            onApply = { text, size, color ->
                ed.drawText(text, pt.x, pt.y, size, color)
                textPoint = null
            },
        )
    }
}

private fun checkerBrush(): Brush {
    val tile = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(tile)
    c.drawColor(0xFFBDBDBD.toInt())
    val paint = android.graphics.Paint().apply { color = 0xFF909090.toInt() }
    c.drawRect(0f, 0f, 10f, 10f, paint)
    c.drawRect(10f, 10f, 20f, 20f, paint)
    return ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun toDoc(pos: Offset, pan: Offset, zoom: Float): Offset =
    if (zoom <= 0f) Offset.Zero else Offset((pos.x - pan.x) / zoom, (pos.y - pan.y) / zoom)

@Composable
fun TopBar(ed: Editor, zoom: Float, menuOpen: Boolean, onMenu: (Boolean) -> Unit, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ps.panel)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).background(Color(0xFF001E36), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("Ps", color = Ps.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ed.docName, color = Ps.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${ed.doc.width}×${ed.doc.height} · ${(zoom * 100).roundToInt()}% · ${ed.doc.layers.size} сл.",
                    color = Ps.textFaint, fontSize = 9.5.sp, maxLines = 1,
                )
            }
            TapBox(Modifier.size(34.dp), onClick = { onAction("undo") }) {
                Icon(
                    Icons.Rounded.Undo, "Отменить",
                    tint = if (ed.canUndo) Ps.text else Ps.textFaint,
                    modifier = Modifier.size(17.dp),
                )
            }
            TapBox(Modifier.size(34.dp), onClick = { onAction("redo") }) {
                Icon(Icons.Rounded.Redo, "Повторить", tint = Ps.textDim, modifier = Modifier.size(17.dp))
            }
            Box {
                TapBox(Modifier.size(34.dp), onClick = { onMenu(true) }) {
                    Icon(Icons.Rounded.MoreVert, "Меню", tint = Ps.text, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, containerColor = Ps.panelAlt) {
                    for ((label, key) in MENU) {
                        if (key.isEmpty()) {
                            Text(
                                label, color = Ps.textFaint, fontSize = 9.sp, letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(label, color = Ps.text, fontSize = 13.sp) },
                                onClick = { onAction(key) },
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
    }
}

private val MENU = listOf(
    "ФАЙЛ" to "",
    "Новый документ" to "new",
    "Открыть фото" to "open",
    "Импорт PSD" to "psd",
    "Сохранить PNG" to "png",
    "Поделиться PNG" to "share",
    "Поделиться JPG" to "shareJpg",
    "ИЗОБРАЖЕНИЕ" to "",
    "Поворот 90°" to "rotate",
    "Отразить ↔" to "flipH",
    "Отразить ↕" to "flipV",
    "Размер…" to "resize",
    "Кадрировать" to "crop",
    "Уровни…" to "levels",
    "Экспозиция…" to "exposure",
    "Температура…" to "temp",
    "Резкость" to "sharpen",
    "Яркость/Контраст…" to "bright",
    "Насыщенность…" to "sat",
    "Чёрно-белое" to "bw",
    "Инверсия" to "invert",
    "Размытие" to "blur",
    "СЛОЙ" to "",
    "Новый слой" to "layerAdd",
    "Дублировать слой" to "layerDup",
    "Удалить слой" to "layerDel",
    "Свести слои" to "flatten",
    "ПРОЧЕЕ" to "",
    "Проверить обновления" to "update",
)

/** Ползунки кисти — выезжают только для кисти/ластика. */
@Composable
fun BrushStrip(ed: Editor) {
    val visible = ed.tool == PsTool.BRUSH || ed.tool == PsTool.ERASER
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Ps.panel).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ed.brushSize.roundToInt()}", color = Ps.textDim, fontSize = 10.sp, modifier = Modifier.width(22.dp))
            Box(Modifier.weight(1.5f)) {
                Slider(
                    value = ed.brushSize, onValueChange = { ed.brushSize = it }, valueRange = 2f..200f,
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Ps.blue, activeTrackColor = Ps.blue, inactiveTrackColor = Ps.border),
                )
            }
            Text("оп ${(ed.brushOpacity * 100).roundToInt()}%", color = Ps.textDim, fontSize = 9.5.sp)
            Box(Modifier.weight(1f)) {
                Slider(
                    value = ed.brushOpacity, onValueChange = { ed.brushOpacity = it }, valueRange = 0.05f..1f,
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Ps.textDim, activeTrackColor = Ps.textDim, inactiveTrackColor = Ps.border),
                )
            }
        }
    }
}

@Composable
fun ToolDock(ed: Editor, onPanels: () -> Unit, onTool: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ps.panel).padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tool in PsTool.entries) {
                val sel = ed.tool == tool
                val bg by animateColorAsState(
                    if (sel) Ps.blue.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "toolBg",
                )
                val tint by animateColorAsState(if (sel) Ps.blue else Ps.textDim, animationSpec = tween(180), label = "toolTint")
                val scale by animateFloatAsState(
                    if (sel) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "toolScale",
                )
                TapBox(
                    Modifier.size(42.dp)
                        .background(bg, RoundedCornerShape(13.dp)),
                    onClick = { ed.tool = tool; onTool() },
                ) {
                    Box(
                        Modifier.size(42.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        contentAlignment = Alignment.Center,
                    ) { ToolGlyph(tool, tint, 22.dp) }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        TapBox(
            Modifier.height(42.dp).background(Ps.panelAlt, RoundedCornerShape(13.dp)).padding(horizontal = 8.dp),
            onClick = onPanels,
        ) { Text("Панели", color = Ps.text, fontSize = 9.sp, maxLines = 1) }
    }
}

@Composable
fun PsChip(text: String, accent: Boolean = false, onClick: () -> Unit) {
    TapBox(
        Modifier
            .background(if (accent) Ps.accent else Ps.panelAlt, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        onClick = onClick,
    ) { Text(text, color = if (accent) Color.White else Ps.text, fontSize = 11.sp) }
}

@Composable
fun PsMiniIcon(text: String, onClick: () -> Unit) {
    TapBox(
        Modifier.size(30.dp).background(Ps.panel.copy(alpha = 0.92f), CircleShape),
        onClick = onClick,
    ) { Text(text, color = Ps.text, fontSize = 10.sp, maxLines = 1) }
}
