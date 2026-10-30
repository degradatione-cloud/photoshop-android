package com.degradatione.psandroid

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ── палитра Photoshop (тёмная тема CC) ──────────────────────────────────────
object Ps {
    val canvasBg = Color(0xFF1E1E1E)
    val panel = Color(0xFF323232)
    val panelAlt = Color(0xFF3A3A3A)
    val panelDark = Color(0xFF262626)
    val border = Color(0xFF141414)
    val text = Color(0xFFD6D6D6)
    val textDim = Color(0xFF9A9A9A)
    val textFaint = Color(0xFF6E6E6E)
    val blue = Color(0xFF31A8FF)      // Ps mark
    val accent = Color(0xFF1473E6)    // Adobe UI blue
    val selected = Color(0xFF4A4A4A)
    val hover = Color(0xFF454545)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ed = Editor()
        setContent {
            PsTheme {
                PsApp(ed)
            }
        }
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
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsApp(ed: Editor) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val journal = remember { mutableStateListOf<String>() }
    fun note(msg: String) {
        journal.add(0, "%s · %s".format(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()), msg))
        if (journal.size > 60) journal.removeAt(journal.lastIndex)
        ed.status = msg
    }

    var zoom by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showPanels by remember { mutableStateOf(false) }
    var panelTab by remember { mutableIntStateOf(0) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var pendingTextPoint by remember { mutableStateOf<Offset?>(null) }
    var showNewDoc by remember { mutableStateOf(false) }
    var showBrightContrast by remember { mutableStateOf(false) }
    var showSaturation by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var openMenu by remember { mutableStateOf<String?>(null) }

    fun fitToScreen() {
        if (canvasSize.width == 0) return
        val z = minOf(
            (canvasSize.width - 16).toFloat() / ed.doc.width,
            (canvasSize.height - 16).toFloat() / ed.doc.height,
        ).coerceIn(0.05f, 6f)
        zoom = z
        pan = Offset(
            (canvasSize.width - ed.doc.width * z) / 2f,
            (canvasSize.height - ed.doc.height * z) / 2f,
        )
    }

    LaunchedEffect(canvasSize, ed.doc.width, ed.doc.height) {
        if (zoom == 0f) fitToScreen()
    }

    LaunchedEffect(Unit) {
        note("Photoshop Android 0.1 · ${ed.doc.width}×${ed.doc.height}")
        Updater.check(Updater.DEFAULT_REPO).let { r ->
            if (r != null && r.versionCode > Updater.installedVersionCode(ctx)) update = r
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                val src = if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        d.isMutableRequired = false
                    }
                } else {
                    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
                }
                ed.openImage(src, "opened-${System.currentTimeMillis() % 10000}.png")
                zoom = 0f
                note("Открыто изображение ${src.width}×${src.height}")
            }.onFailure { note("Не открылось: ${it.message}") }
        }
    }

    fun saveToGallery() {
        runCatching {
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
            note("Галерея: Pictures/Photoshop/$name")
        }.onFailure { note("Не сохранилось: ${it.message}") }
    }

    fun sharePng() {
        runCatching {
            val file = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "photoshop-${System.currentTimeMillis()}.png")
            ed.exportPng(file)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Поделиться"))
            note("Экспорт PNG: ${file.length() / 1024} KB")
        }.onFailure { note("Экспорт не удался: ${it.message}") }
    }

    Column(Modifier.fillMaxSize().background(Ps.canvasBg).systemBarsPadding().imePadding()) {
        TopBar(
            ed = ed,
            zoom = zoom,
            openMenu = openMenu,
            onMenu = { openMenu = it },
            onAction = { action ->
                openMenu = null
                when (action) {
                    "new" -> showNewDoc = true
                    "open" -> pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    "save" -> saveToGallery()
                    "share" -> sharePng()
                    "undo" -> ed.undoStep()
                    "redo" -> ed.redoStep()
                    "flatten" -> ed.flatten()
                    "bright" -> showBrightContrast = true
                    "sat" -> showSaturation = true
                    "bw" -> { ed.saturation(0f); note("Чёрно-белое") }
                    "invert" -> { ed.invert(); note("Инверсия") }
                    "blur" -> { ed.blur(); note("Размытие") }
                    "crop" -> { ed.tool = PsTool.CROP; cropRect = null; note("Кадрирование: тяни рамку по холсту") }
                    "layerAdd" -> { ed.addLayer(); note("Новый слой") }
                    "layerDup" -> { ed.duplicateLayer(); note("Слой дублирован") }
                    "layerDel" -> { ed.deleteLayer(); note(ed.status) }
                    "layerUp" -> ed.moveLayer(true)
                    "layerDown" -> ed.moveLayer(false)
                    "update" -> scope.launch {
                        val r = Updater.check(Updater.DEFAULT_REPO)
                        if (r != null && r.versionCode > Updater.installedVersionCode(ctx)) {
                            update = r
                            note("Найдено обновление ${r.tag}")
                        } else {
                            note("Обновлений нет")
                        }
                    }
                }
            },
        )

        update?.let { r ->
            Row(
                Modifier.fillMaxWidth().background(Ps.accent.copy(alpha = 0.18f))
                    .clickable {
                        scope.launch {
                            launchDownload(ctx, r, { msg: String -> note(msg) }) { update = null }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Ps.blue, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("Доступно обновление ${r.tag} — обновить", color = Ps.text, fontSize = 12.sp)
            }
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { canvasSize = it }
        ) {
            val checker = remember { checkerBrush() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(ed.tool, ed.doc.width, ed.doc.height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tool = ed.tool
                        var lastDoc = toDoc(down.position, pan, zoom)
                        var moved = false
                        var cropStart = lastDoc
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
                                val prevCentroid = (a.previousPosition + b.previousPosition) / 2f
                                val curCentroid = (a.position + b.position) / 2f
                                val newZoom = (zoom * factor).coerceIn(0.05f, 8f)
                                pan = curCentroid - (prevCentroid - pan) * (newZoom / zoom)
                                zoom = newZoom
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val pos = pressed[0].position
                                val docPt = toDoc(pos, pan, zoom)
                                when (tool) {
                                    PsTool.BRUSH -> ed.strokeTo(lastDoc.x, lastDoc.y, docPt.x, docPt.y, false)
                                    PsTool.ERASER -> ed.strokeTo(lastDoc.x, lastDoc.y, docPt.x, docPt.y, true)
                                    PsTool.MOVE -> pan += pressed[0].position - pressed[0].previousPosition
                                    PsTool.CROP -> {
                                        cropRect = Rect(
                                            minOf(cropStart.x, docPt.x), minOf(cropStart.y, docPt.y),
                                            maxOf(cropStart.x, docPt.x), maxOf(cropStart.y, docPt.y),
                                        )
                                    }
                                    PsTool.EYEDROPPER -> ed.pick(docPt.x.toInt(), docPt.y.toInt())?.let {
                                        ed.foreground = it
                                        note("Цвет #%06X".format(it and 0xFFFFFF))
                                    }
                                    PsTool.ZOOM -> Unit
                                    PsTool.FILL -> Unit
                                    PsTool.TEXT -> Unit
                                }
                                if ((pos - down.position).getDistance() > 4f) moved = true
                                lastDoc = docPt
                                pressed[0].consume()
                            }
                            event = awaitPointerEvent()
                        }
                        when (tool) {
                            PsTool.BRUSH, PsTool.ERASER -> ed.endStroke(tool == PsTool.ERASER)
                            PsTool.FILL -> ed.fillAt(lastDoc.x.toInt(), lastDoc.y.toInt())
                            PsTool.TEXT -> if (!moved) pendingTextPoint = lastDoc
                            PsTool.ZOOM -> {
                                val target = lastDoc
                                val f = 1.6f
                                val newZoom = (zoom * f).coerceIn(0.05f, 8f)
                                pan = Offset(
                                    canvasSize.width / 2f - target.x * newZoom,
                                    canvasSize.height / 2f - target.y * newZoom,
                                )
                                zoom = newZoom
                            }
                            else -> Unit
                        }
                    }
                }
            ) {
                ed.version // перерисовка при изменении пикселей
                if (zoom <= 0f) return@Canvas
                withTransform({
                    translate(pan.x, pan.y)
                    scale(zoom, zoom, pivot = Offset.Zero)
                }) {
                    drawRect(
                        brush = checker,
                        topLeft = Offset.Zero,
                        size = Size(ed.doc.width.toFloat(), ed.doc.height.toFloat()),
                    )
                    for (l in ed.doc.layers) {
                        if (!l.visible) continue
                        drawImage(
                            image = l.bitmap.asImageBitmap(),
                            topLeft = Offset.Zero,
                            alpha = l.opacity,
                            blendMode = l.blend.compose,
                        )
                    }
                    cropRect?.let { r ->
                        drawRect(
                            color = Color.Black.copy(alpha = 0.45f),
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                        )
                        drawRect(
                            color = Color(0xFF31A8FF),
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / zoom),
                        )
                    }
                }
                drawRect(
                    color = Color(0xFF000000),
                    topLeft = Offset(pan.x - 1, pan.y - 1),
                    size = Size(ed.doc.width * zoom + 2, ed.doc.height * zoom + 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                )
            }

            ToolRail(ed = ed, modifier = Modifier.align(Alignment.CenterStart))

            if (cropRect != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                        .background(Ps.panel, RoundedCornerShape(8.dp))
                        .border(1.dp, Ps.border, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PsButton("Применить") {
                        cropRect?.let { r ->
                            if (r.width > 4 && r.height > 4) {
                                ed.crop(android.graphics.RectF(r.left, r.top, r.right, r.bottom))
                                note(ed.status)
                            }
                        }
                        cropRect = null
                    }
                    PsButton("Отмена") { cropRect = null }
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PsButton("−") { zoom = (zoom / 1.4f).coerceAtLeast(0.05f) }
                PsButton("+") { zoom = (zoom * 1.4f).coerceAtMost(8f) }
                PsButton("Fit") { fitToScreen() }
            }
        }

        StatusBar(ed = ed, zoom = zoom, onPanels = { showPanels = true })

        if (showPanels) {
            ModalBottomSheet(
                onDismissRequest = { showPanels = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Ps.panel,
                contentColor = Ps.text,
                dragHandle = {
                    Box(
                        Modifier.padding(vertical = 8.dp).size(width = 34.dp, height = 4.dp)
                            .background(Ps.textFaint, RoundedCornerShape(2.dp))
                    )
                },
            ) {
                PanelsSheet(
                    ed = ed,
                    journal = journal,
                    tab = panelTab,
                    onTab = { panelTab = it },
                    note = { note(it) },
                )
            }
        }
    }

    if (showNewDoc) {
        NewDocDialog(
            onDismiss = { showNewDoc = false },
            onCreate = { w, h, transparent ->
                ed.newDocument(w, h, transparent)
                zoom = 0f
                fitToScreen()
                note("Новый документ $w×$h")
                showNewDoc = false
            },
        )
    }
    if (showBrightContrast) {
        TwoSliderDialog(
            title = "Яркость/Контраст",
            aLabel = "Яркость", bLabel = "Контраст",
            onApply = { a, b ->
                ed.brightnessContrast(a, b)
                note("Яркость ${a.toInt()} / Контраст ${b.toInt()}")
                showBrightContrast = false
            },
            onDismiss = { showBrightContrast = false },
        )
    }
    if (showSaturation) {
        OneSliderDialog(
            title = "Насыщенность",
            label = "Насыщенность",
            initial = 1f, min = 0f, max = 2f,
            onApply = { v ->
                ed.saturation(v)
                note("Насыщенность ${(v * 100).toInt()}%")
                showSaturation = false
            },
            onDismiss = { showSaturation = false },
        )
    }
    pendingTextPoint?.let { pt ->
        TextDialog(
            onDismiss = { pendingTextPoint = null },
            onApply = { text, size, color ->
                ed.drawText(text, pt.x, pt.y, size, color)
                note("Текст «${text.take(24)}»")
                pendingTextPoint = null
            },
        )
    }
}

private suspend fun launchDownload(
    ctx: Context,
    rel: Updater.Release,
    note: (String) -> Unit,
    done: () -> Unit,
) {
    note("Качаю ${rel.tag}…")
    runCatching {
        val f = Updater.download(ctx, rel) { pct, _ -> }
        Updater.install(ctx, f)
        note("Установщик открыт")
    }.onFailure { note("Обновление: ${it.message}") }
    done()
}

private fun checkerBrush(): Brush {
    val tile = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(tile)
    c.drawColor(0xFFBFBFBF.toInt())
    val paint = android.graphics.Paint().apply { color = 0xFF8C8C8C.toInt() }
    c.drawRect(0f, 0f, 12f, 12f, paint)
    c.drawRect(12f, 12f, 24f, 24f, paint)
    return ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun toDoc(pos: Offset, pan: Offset, zoom: Float): Offset =
    if (zoom <= 0f) Offset.Zero else Offset((pos.x - pan.x) / zoom, (pos.y - pan.y) / zoom)

@Composable
fun TopBar(
    ed: Editor,
    zoom: Float,
    openMenu: String?,
    onMenu: (String?) -> Unit,
    onAction: (String) -> Unit,
) {
    val menus = listOf(
        "Файл" to listOf(
            "new" to "Новый…",
            "open" to "Открыть изображение…",
            "save" to "Сохранить в галерею",
            "share" to "Поделиться PNG",
            "update" to "Проверить обновления",
        ),
        "Правка" to listOf("undo" to "Отменить", "redo" to "Повторить", "flatten" to "Свести слои"),
        "Изображение" to listOf(
            "bright" to "Яркость/Контраст…",
            "sat" to "Насыщенность…",
            "bw" to "Чёрно-белое",
            "invert" to "Инверсия",
            "blur" to "Размытие",
            "crop" to "Кадрирование",
        ),
        "Слой" to listOf(
            "layerAdd" to "Новый слой",
            "layerDup" to "Дублировать",
            "layerDel" to "Удалить",
            "layerUp" to "Вверх",
            "layerDown" to "Вниз",
        ),
    )
    Column(Modifier.fillMaxWidth().background(Ps.panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).background(Color(0xFF001E36), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF31A8FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ps", color = Ps.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ed.docName,
                    color = Ps.text, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@ ${(zoom * 100).roundToInt()}%  ·  ${ed.doc.width} × ${ed.doc.height}  ·  RGB/8",
                    color = Ps.textFaint, fontSize = 9.5.sp, maxLines = 1,
                )
            }
            SmallIcon(Icons.Rounded.Refresh, "Сброс вида") { onAction("undo") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((title, items) in menus) {
                Box {
                    Text(
                        title,
                        color = Ps.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onMenu(title) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                    )
                    DropdownMenu(
                        expanded = openMenu == title,
                        onDismissRequest = { onMenu(null) },
                        containerColor = Ps.panelAlt,
                    ) {
                        for ((key, label) in items) {
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

@Composable
fun ToolRail(ed: Editor, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(6.dp).background(Ps.panel, RoundedCornerShape(6.dp))
            .border(1.dp, Ps.border, RoundedCornerShape(6.dp)).padding(2.dp),
    ) {
        for (tool in PsTool.entries) {
            val sel = ed.tool == tool
            Box(
                Modifier.size(38.dp)
                    .background(if (sel) Ps.selected else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { ed.tool = tool },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tool.key,
                    color = if (sel) Ps.blue else Ps.textDim,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun StatusBar(ed: Editor, zoom: Float, onPanels: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ps.panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ps.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ed.tool.label, color = Ps.textDim, fontSize = 10.5.sp)
            Spacer(Modifier.width(10.dp))
            Text("${(zoom * 100).roundToInt()}%", color = Ps.textDim, fontSize = 10.5.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                ed.status,
                color = Ps.textFaint, fontSize = 10.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.background(Ps.panelAlt, RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPanels,
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Панели", color = Ps.text, fontSize = 11.sp)
            }
        }
    }
}