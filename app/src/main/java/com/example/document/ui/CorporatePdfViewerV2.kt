package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.document.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ViewerTool(val label: String) {
    HAND("Navegar"),
    SELECT("Seleccionar"),
    TEXT("Texto"),
    FREEHAND("Lápiz"),
    HIGHLIGHT("Resaltador"),
    LINE("Línea"),
    ARROW("Flecha"),
    RECTANGLE("Rectángulo"),
    ELLIPSE("Elipse"),
    CLOUD("Nube")
}

private data class ViewerMarkup(
    val source: PlanComment,
    val clientId: String,
    val type: ReviewMarkupType,
    val text: String,
    val x: Float,
    val y: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
    val height: Float,
    val colorArgb: Int,
    val strokeWidth: Float,
    val opacity: Float,
    val points: List<ReviewPoint>
)

private data class DrawingPreview(
    val type: ReviewMarkupType,
    val start: ReviewPoint,
    val end: ReviewPoint,
    val points: List<ReviewPoint> = emptyList()
)

private data class TextPlacement(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.34f
)

private data class RedoMarkup(
    val input: ReviewMarkupInput,
    val clientId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporatePdfViewerV2(
    file: File,
    document: DocumentRecord,
    comments: List<PlanComment>,
    timeline: List<WorkflowEvent>,
    currentEmail: String,
    isAdmin: Boolean,
    canComment: Boolean,
    onClose: () -> Unit,
    onApprove: () -> Unit,
    onRequestChanges: (String) -> Unit,
    onAddComment: (Int, String, Float, Float, Float) -> Unit,
    onPublishComment: (PlanComment) -> Unit,
    onUpdateComment: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit
) {
    var page by rememberSaveable(file.path) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(1) }
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(file, page) { mutableFloatStateOf(1f) }
    var pan by remember(file, page) { mutableStateOf(Offset.Zero) }
    var tool by rememberSaveable { mutableStateOf(ViewerTool.HAND) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var propertiesVisible by rememberSaveable { mutableStateOf(false) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFFF6A00.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(1f) }
    var drawingPreview by remember { mutableStateOf<DrawingPreview?>(null) }
    var textPlacement by remember { mutableStateOf<TextPlacement?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var showMarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showRequestChanges by remember { mutableStateOf(false) }
    var undoClientIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var redoMarkups by remember { mutableStateOf<List<RedoMarkup>>(emptyList()) }
    var hiddenClientIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val density = LocalDensity.current

    val pageMarkups = remember(comments, page, hiddenClientIds) {
        comments
            .filter { it.pageIndex == page }
            .map(::decodeViewerMarkup)
            .filterNot { it.clientId in hiddenClientIds }
    }

    LaunchedEffect(file, page) {
        val rendered = renderTechnicalPage(file, page)
        bitmap = rendered.first
        pageCount = rendered.second
        scale = 1f
        pan = Offset.Zero
        drawingPreview = null
        selected = null
        tool = ViewerTool.HAND
    }

    LaunchedEffect(comments, hiddenClientIds) {
        val resolved = hiddenClientIds.mapNotNull { clientId ->
            comments.firstOrNull { decodeViewerMarkup(it).clientId == clientId }
        }
        resolved.forEach(onDeleteComment)
        if (resolved.isNotEmpty()) {
            hiddenClientIds = hiddenClientIds - resolved.map { decodeViewerMarkup(it).clientId }.toSet()
        }
    }

    fun bitmapFitSize(): Pair<Float, Float> {
        val image = bitmap ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fit = min(
            viewport.width.toFloat() / image.width.toFloat(),
            viewport.height.toFloat() / image.height.toFloat()
        )
        return image.width * fit to image.height * fit
    }

    fun clampPan(candidate: Offset, targetScale: Float = scale): Offset {
        val (baseWidth, baseHeight) = bitmapFitSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return Offset.Zero
        val maxX = ((baseWidth * targetScale - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((baseHeight * targetScale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun screenToNormalized(position: Offset): ReviewPoint? {
        val (baseWidth, baseHeight) = bitmapFitSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return null
        val baseLeft = (viewport.width - baseWidth) / 2f
        val baseTop = (viewport.height - baseHeight) / 2f
        val localX = (position.x - baseLeft - pan.x) / scale
        val localY = (position.y - baseTop - pan.y) / scale
        if (localX < 0f || localY < 0f || localX > baseWidth || localY > baseHeight) return null
        return ReviewPoint((localX / baseWidth).coerceIn(0f, 1f), (localY / baseHeight).coerceIn(0f, 1f))
    }

    fun submitMarkup(input: ReviewMarkupInput, clientId: String = UUID.randomUUID().toString()) {
        val encoded = encodeViewerMarkup(input, clientId)
        onAddComment(input.pageIndex, encoded, input.x, input.y, input.width)
        undoClientIds = undoClientIds + clientId
        redoMarkups = emptyList()
    }

    fun finishDrawing(preview: DrawingPreview) {
        val xs = (preview.points.ifEmpty { listOf(preview.start, preview.end) }).map { it.x }
        val ys = (preview.points.ifEmpty { listOf(preview.start, preview.end) }).map { it.y }
        val minX = xs.minOrNull() ?: preview.start.x
        val minY = ys.minOrNull() ?: preview.start.y
        val maxX = xs.maxOrNull() ?: preview.end.x
        val maxY = ys.maxOrNull() ?: preview.end.y
        val effectiveOpacity = if (preview.type == ReviewMarkupType.HIGHLIGHT) opacity.coerceAtMost(0.38f) else opacity
        val effectiveStroke = if (preview.type == ReviewMarkupType.HIGHLIGHT) strokeWidth.coerceAtLeast(0.018f) else strokeWidth
        submitMarkup(
            ReviewMarkupInput(
                pageIndex = page,
                type = preview.type,
                x = minX,
                y = minY,
                endX = preview.end.x,
                endY = preview.end.y,
                width = (maxX - minX).coerceAtLeast(0.01f),
                height = (maxY - minY).coerceAtLeast(0.01f),
                colorArgb = colorArgb,
                strokeWidth = effectiveStroke,
                opacity = effectiveOpacity,
                points = preview.points
            )
        )
    }

    fun undoLast() {
        val clientId = undoClientIds.lastOrNull() ?: return
        val existing = comments.firstOrNull { decodeViewerMarkup(it).clientId == clientId }
        val view = existing?.let(::decodeViewerMarkup)
        if (existing != null && view != null) {
            onDeleteComment(existing)
            redoMarkups = redoMarkups + RedoMarkup(view.toInput(), clientId)
        } else {
            hiddenClientIds = hiddenClientIds + clientId
        }
        undoClientIds = undoClientIds.dropLast(1)
    }

    fun redoLast() {
        val redo = redoMarkups.lastOrNull() ?: return
        submitMarkup(redo.input, redo.clientId)
        redoMarkups = redoMarkups.dropLast(1)
        hiddenClientIds = hiddenClientIds - redo.clientId
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color(0xFF202327)
        ) {
            Box(Modifier.fillMaxSize()) {
                TechnicalCanvas(
                    bitmap = bitmap,
                    viewport = viewport,
                    scale = scale,
                    pan = pan,
                    tool = tool,
                    pageMarkups = pageMarkups,
                    drawingPreview = drawingPreview,
                    onViewportChanged = { viewport = it; pan = clampPan(pan) },
                    onTransform = { centroid, panChange, zoom ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.75f, 12f)
                        val (baseWidth, baseHeight) = bitmapFitSize()
                        if (baseWidth > 0f && baseHeight > 0f) {
                            val baseOrigin = Offset((viewport.width - baseWidth) / 2f, (viewport.height - baseHeight) / 2f)
                            val contentX = (centroid.x - baseOrigin.x - pan.x) / oldScale
                            val contentY = (centroid.y - baseOrigin.y - pan.y) / oldScale
                            val candidate = Offset(
                                centroid.x - baseOrigin.x - contentX * newScale + panChange.x,
                                centroid.y - baseOrigin.y - contentY * newScale + panChange.y
                            )
                            scale = newScale
                            pan = clampPan(candidate, newScale)
                        }
                    },
                    onDoubleTap = { position ->
                        val target = if (scale < 2f) 2.5f else 1f
                        val oldScale = scale
                        val (baseWidth, baseHeight) = bitmapFitSize()
                        val baseOrigin = Offset((viewport.width - baseWidth) / 2f, (viewport.height - baseHeight) / 2f)
                        val contentX = (position.x - baseOrigin.x - pan.x) / oldScale
                        val contentY = (position.y - baseOrigin.y - pan.y) / oldScale
                        val candidate = Offset(
                            position.x - baseOrigin.x - contentX * target,
                            position.y - baseOrigin.y - contentY * target
                        )
                        scale = target
                        pan = if (target == 1f) Offset.Zero else clampPan(candidate, target)
                    },
                    screenToNormalized = ::screenToNormalized,
                    onTextPoint = { point -> textPlacement = TextPlacement(page, point.x, point.y) },
                    onDrawingChanged = { drawingPreview = it },
                    onDrawingFinished = { preview -> drawingPreview = null; finishDrawing(preview) },
                    onSelect = { point -> selected = hitTestMarkup(pageMarkups, point) }
                )

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TechnicalTopBar(
                        document = document,
                        page = page,
                        pageCount = pageCount,
                        scale = scale,
                        commentsCount = comments.size,
                        onClose = onClose,
                        onPrevious = { if (page > 0) page-- },
                        onNext = { if (page + 1 < pageCount) page++ },
                        onZoomOut = {
                            val target = (scale / 1.4f).coerceAtLeast(0.75f)
                            scale = target
                            pan = if (target <= 1f) Offset.Zero else clampPan(pan, target)
                        },
                        onZoomIn = {
                            val target = (scale * 1.4f).coerceAtMost(12f)
                            scale = target
                            pan = clampPan(pan, target)
                        },
                        onFit = { scale = 1f; pan = Offset.Zero },
                        onShowMarks = { showMarks = true },
                        onShowHistory = { showHistory = true }
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    TechnicalToolPanel(
                        tool = tool,
                        canEdit = canComment && document.isUnderReview,
                        propertiesVisible = propertiesVisible,
                        colorArgb = colorArgb,
                        strokeWidth = strokeWidth,
                        opacity = opacity,
                        undoEnabled = undoClientIds.isNotEmpty(),
                        redoEnabled = redoMarkups.isNotEmpty(),
                        document = document,
                        currentEmail = currentEmail,
                        onTool = {
                            tool = it
                            drawingPreview = null
                            selected = null
                        },
                        onToggleProperties = { propertiesVisible = !propertiesVisible },
                        onColor = { colorArgb = it },
                        onStroke = { strokeWidth = it },
                        onOpacity = { opacity = it },
                        onUndo = ::undoLast,
                        onRedo = ::redoLast,
                        onApprove = onApprove,
                        onRequestChanges = { showRequestChanges = true }
                    )
                }

                FilledTonalIconButton(
                    onClick = { chromeVisible = !chromeVisible },
                    modifier = Modifier
                        .align(if (chromeVisible) Alignment.CenterEnd else Alignment.BottomEnd)
                        .padding(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = SkmOrangeDark
                    )
                ) {
                    Icon(if (chromeVisible) Icons.Default.Fullscreen else Icons.Default.Build, if (chromeVisible) "Ocultar herramientas" else "Mostrar herramientas")
                }
            }
        }
    }

    textPlacement?.let { placement ->
        TechnicalTextEditor(
            title = "Nueva observación · hoja ${placement.pageIndex + 1}",
            initialText = "",
            initialWidth = placement.width,
            onDismiss = { textPlacement = null },
            onSave = { text, width ->
                textPlacement = null
                submitMarkup(
                    ReviewMarkupInput(
                        pageIndex = placement.pageIndex,
                        type = ReviewMarkupType.TEXT,
                        text = text,
                        x = placement.x,
                        y = placement.y,
                        width = width,
                        height = 0.10f,
                        colorArgb = colorArgb,
                        strokeWidth = strokeWidth,
                        opacity = opacity
                    )
                )
            }
        )
    }

    selected?.let { comment ->
        val view = decodeViewerMarkup(comment)
        TechnicalMarkupInspector(
            markup = view,
            canModify = comment.canBeModifiedBy(currentEmail, isAdmin) && canComment,
            onDismiss = { selected = null },
            onPublish = if (!comment.published && comment.authorEmail.equals(currentEmail, true)) {
                { selected = null; onPublishComment(comment) }
            } else null,
            onDelete = {
                selected = null
                onDeleteComment(comment)
            },
            onUpdateText = { newText ->
                selected = null
                val updatedInput = view.toInput().copy(text = newText)
                onUpdateComment(comment.copy(
                    text = if (view.type == ReviewMarkupType.TEXT) newText else encodeViewerMarkup(updatedInput, view.clientId),
                    markupType = view.type,
                    endX = view.endX,
                    endY = view.endY,
                    height = view.height,
                    colorArgb = view.colorArgb,
                    strokeWidth = view.strokeWidth,
                    opacity = view.opacity,
                    points = view.points
                ))
            }
        )
    }

    if (showMarks) {
        TechnicalMarkupsList(
            comments = comments,
            page = page,
            onDismiss = { showMarks = false },
            onSelect = {
                showMarks = false
                page = it.pageIndex.coerceIn(0, pageCount - 1)
                selected = it
            },
            onPublishAllDrafts = {
                comments.filter { !it.published && it.authorEmail.equals(currentEmail, true) }.forEach(onPublishComment)
                showMarks = false
            }
        )
    }

    if (showHistory) {
        TechnicalHistoryDialog(timeline, onDismiss = { showHistory = false })
    }

    if (showRequestChanges) {
        TechnicalRequestChangesDialog(
            onDismiss = { showRequestChanges = false },
            onConfirm = {
                showRequestChanges = false
                onRequestChanges(it)
            }
        )
    }
}

@Composable
private fun TechnicalCanvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    scale: Float,
    pan: Offset,
    tool: ViewerTool,
    pageMarkups: List<ViewerMarkup>,
    drawingPreview: DrawingPreview?,
    onViewportChanged: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onTextPoint: (ReviewPoint) -> Unit,
    onDrawingChanged: (DrawingPreview?) -> Unit,
    onDrawingFinished: (DrawingPreview) -> Unit,
    onSelect: (ReviewPoint) -> Unit
) {
    val density = LocalDensity.current
    val image = bitmap
    val fit = if (image != null && viewport.width > 0 && viewport.height > 0) {
        min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    } else 1f
    val pageWidthPx = image?.width?.times(fit) ?: 1f
    val pageHeightPx = image?.height?.times(fit) ?: 1f
    val pageWidthDp = with(density) { pageWidthPx.toDp() }
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    val gestureModifier = when (tool) {
        ViewerTool.HAND -> Modifier
            .pointerInput(viewport, scale, pan) {
                detectTransformGestures(panZoomLock = true) { centroid, panChange, zoom, _ ->
                    onTransform(centroid, panChange, zoom)
                }
            }
            .pointerInput(scale) {
                detectTapGestures(onDoubleTap = onDoubleTap)
            }
        ViewerTool.SELECT -> Modifier.pointerInput(pageMarkups, scale, pan) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onSelect) }
        }
        ViewerTool.TEXT -> Modifier.pointerInput(scale, pan) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onTextPoint) }
        }
        else -> Modifier.pointerInput(tool, scale, pan) {
            var start: ReviewPoint? = null
            var points = mutableListOf<ReviewPoint>()
            detectDragGestures(
                onDragStart = { position ->
                    start = screenToNormalized(position)
                    points = mutableListOf<ReviewPoint>().apply { start?.let(::add) }
                    start?.let { point ->
                        onDrawingChanged(DrawingPreview(tool.toMarkupType(), point, point, points.toList()))
                    }
                },
                onDragCancel = { onDrawingChanged(null) },
                onDragEnd = {
                    val preview = start?.let { first ->
                        val last = points.lastOrNull() ?: first
                        DrawingPreview(tool.toMarkupType(), first, last, points.toList())
                    }
                    onDrawingChanged(null)
                    if (preview != null) onDrawingFinished(preview)
                },
                onDrag = { change, _ ->
                    val point = screenToNormalized(change.position) ?: return@detectDragGestures
                    if (tool == ViewerTool.FREEHAND || tool == ViewerTool.HIGHLIGHT) {
                        if (points.isEmpty() || distance(points.last(), point) > 0.0015f) points.add(point)
                    } else {
                        points = mutableListOf(start ?: point, point)
                    }
                    val first = start ?: point
                    onDrawingChanged(DrawingPreview(tool.toMarkupType(), first, point, points.toList()))
                    change.consume()
                }
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF2B2E32))
            .onGloballyPositioned { onViewportChanged(it.size) }
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        if (image == null) {
            CircularProgressIndicator(color = SkmOrange)
        } else {
            Box(
                Modifier
                    .size(pageWidthDp, pageHeightDp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
                    .background(Color.White)
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Plano PDF",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(Modifier.fillMaxSize()) {
                    pageMarkups.forEach { drawViewerMarkup(it) }
                    drawingPreview?.let { drawPreviewMarkup(it, Color(0xFFFF6A00), 0.004f, 0.9f) }
                }
                pageMarkups.filter { it.type == ReviewMarkupType.TEXT }.forEach { markup ->
                    TextMarkupCard(markup)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TextMarkupCard(markup: ViewerMarkup) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    Card(
        modifier = Modifier
            .onGloballyPositioned { size = it.size }
            .offset {
                IntOffset(
                    (markup.x * constraints.maxWidth).roundToInt(),
                    (markup.y * constraints.maxHeight).roundToInt()
                )
            }
            .width(with(density) { (constraints.maxWidth * markup.width.coerceIn(0.18f, 0.62f)).toDp() }),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(markup.colorArgb).copy(alpha = if (markup.source.published) 0.92f else 0.72f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (markup.source.published) "PUBLICADA" else "BORRADOR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = Color.White)
            Text(markup.text, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

@Composable
private fun TechnicalTopBar(
    document: DocumentRecord,
    page: Int,
    pageCount: Int,
    scale: Float,
    commentsCount: Int,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onShowMarks: () -> Unit,
    onShowHistory: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shadowElevation = 8.dp) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Cerrar") }
                Column(Modifier.weight(1f)) {
                    Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Rev ${document.revision} · ${document.workflowStatusLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onShowMarks) {
                    BadgedBox(badge = { if (commentsCount > 0) Badge { Text(commentsCount.toString()) } }) {
                        Icon(Icons.Default.Layers, "Marcas de revisión")
                    }
                }
                IconButton(onClick = onShowHistory) { Icon(Icons.Default.History, "Historial") }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                IconButton(onClick = onPrevious, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Anterior") }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("${page + 1} / $pageCount", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onNext, enabled = page + 1 < pageCount) { Icon(Icons.Default.ChevronRight, "Siguiente") }
                VerticalDivider(Modifier.height(26.dp))
                IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onFit) { Icon(Icons.Default.FitScreen, "Ajustar") }
            }
        }
    }
}

@Composable
private fun TechnicalToolPanel(
    tool: ViewerTool,
    canEdit: Boolean,
    propertiesVisible: Boolean,
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    document: DocumentRecord,
    currentEmail: String,
    onTool: (ViewerTool) -> Unit,
    onToggleProperties: () -> Unit,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), shadowElevation = 12.dp) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(ViewerTool.HAND, tool, Icons.Default.PanTool, true, onTool)
                ToolButton(ViewerTool.SELECT, tool, Icons.Default.NearMe, true, onTool)
                VerticalDivider(Modifier.height(34.dp))
                ToolButton(ViewerTool.TEXT, tool, Icons.Default.TextFields, canEdit, onTool)
                ToolButton(ViewerTool.FREEHAND, tool, Icons.Default.Draw, canEdit, onTool)
                ToolButton(ViewerTool.HIGHLIGHT, tool, Icons.Default.BorderColor, canEdit, onTool)
                ToolButton(ViewerTool.LINE, tool, Icons.Default.HorizontalRule, canEdit, onTool)
                ToolButton(ViewerTool.ARROW, tool, Icons.Default.ArrowRightAlt, canEdit, onTool)
                ToolButton(ViewerTool.RECTANGLE, tool, Icons.Default.CropSquare, canEdit, onTool)
                ToolButton(ViewerTool.ELLIPSE, tool, Icons.Default.Circle, canEdit, onTool)
                ToolButton(ViewerTool.CLOUD, tool, Icons.Default.CloudQueue, canEdit, onTool)
                VerticalDivider(Modifier.height(34.dp))
                IconButton(onClick = onUndo, enabled = undoEnabled) { Icon(Icons.Default.Undo, "Deshacer") }
                IconButton(onClick = onRedo, enabled = redoEnabled) { Icon(Icons.Default.Redo, "Rehacer") }
                IconButton(onClick = onToggleProperties, enabled = tool.isDrawingTool()) {
                    Icon(Icons.Default.Tune, "Propiedades", tint = if (propertiesVisible) SkmOrange else LocalContentColor.current)
                }
            }

            AnimatedVisibility(visible = propertiesVisible && tool.isDrawingTool()) {
                MarkupProperties(
                    colorArgb = colorArgb,
                    strokeWidth = strokeWidth,
                    opacity = opacity,
                    onColor = onColor,
                    onStroke = onStroke,
                    onOpacity = onOpacity
                )
            }

            if (document.canBeSignedBy(currentEmail)) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    if (maxWidth < 470.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRequestChanges, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) {
                                Text("Cambios")
                            }
                            Button(onClick = onApprove, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) {
                                Text("Aprobar y firmar")
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onRequestChanges, colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) { Text("Solicitar cambios") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) { Text("Aprobar y firmar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(tool: ViewerTool, selected: ViewerTool, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onTool: (ViewerTool) -> Unit) {
    FilledTonalIconButton(
        onClick = { onTool(tool) },
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (tool == selected) SkmOrange else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (tool == selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Icon(icon, tool.label) }
}

@Composable
private fun MarkupProperties(
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit
) {
    val colors = listOf(
        0xFFFF6A00.toInt(),
        0xFFD32F2F.toInt(),
        0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(),
        0xFF212121.toInt(),
        0xFFF9A825.toInt()
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Color", style = MaterialTheme.typography.labelMedium)
            colors.forEach { value ->
                Surface(
                    onClick = { onColor(value) },
                    shape = CircleShape,
                    color = Color(value),
                    border = if (value == colorArgb) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                    modifier = Modifier.size(30.dp)
                ) {}
            }
            VerticalDivider(Modifier.height(28.dp))
            Text("Espesor", style = MaterialTheme.typography.labelMedium)
            listOf(0.0025f, 0.004f, 0.008f, 0.014f).forEach { value ->
                FilterChip(selected = kotlin.math.abs(strokeWidth - value) < 0.001f, onClick = { onStroke(value) }, label = { Text("${(value * 1000).roundToInt()}") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacidad", style = MaterialTheme.typography.labelMedium)
            Slider(value = opacity, onValueChange = onOpacity, valueRange = 0.15f..1f, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text("${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TechnicalTextEditor(title: String, initialText: String, initialWidth: Float, onDismiss: () -> Unit, onSave: (String, Float) -> Unit) {
    var text by rememberSaveable(title) { mutableStateOf(initialText) }
    var width by rememberSaveable(title) { mutableFloatStateOf(initialWidth) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") } }
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Cancelar") }
                            Button(onClick = { onSave(text.trim(), width) }, enabled = text.trim().isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) { Text("Guardar borrador") }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("La observación será privada hasta que decidas publicarla.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 1200) text = it },
                        label = { Text("Texto de la observación") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14,
                        supportingText = { Text("${text.length}/1200") }
                    )
                    Text("Ancho del cuadro: ${(width * 100).roundToInt()}%")
                    Slider(value = width, onValueChange = { width = it }, valueRange = 0.20f..0.62f)
                }
            }
        }
    }
}

@Composable
private fun TechnicalMarkupInspector(
    markup: ViewerMarkup,
    canModify: Boolean,
    onDismiss: () -> Unit,
    onPublish: (() -> Unit)?,
    onDelete: () -> Unit,
    onUpdateText: (String) -> Unit
) {
    var note by rememberSaveable(markup.source.id) { mutableStateOf(markup.text) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(14.dp).widthIn(max = 620.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(markup.type.icon(), null, tint = Color(markup.colorArgb))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(markup.source.displayLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(if (markup.source.published) "Publicada" else "Borrador privado", color = if (markup.source.published) SkmSuccess else SkmWarning)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Text("${markup.source.authorName.ifBlank { markup.source.authorEmail }} · ${formatTechnicalDate(markup.source.createdAt)}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (canModify && it.length <= 1200) note = it },
                    label = { Text(if (markup.type == ReviewMarkupType.TEXT) "Observación" else "Nota opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    readOnly = !canModify
                )
                if (canModify) {
                    Button(onClick = { onUpdateText(note.trim()) }, enabled = markup.type != ReviewMarkupType.TEXT || note.trim().isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar cambios") }
                    onPublish?.let { publish ->
                        Button(onClick = publish, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) { Icon(Icons.Default.Publish, null); Spacer(Modifier.width(8.dp)); Text("Publicar para todos") }
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Eliminar marca") }
                }
            }
        }
    }
}

@Composable
private fun TechnicalMarkupsList(comments: List<PlanComment>, page: Int, onDismiss: () -> Unit, onSelect: (PlanComment) -> Unit, onPublishAllDrafts: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("Marcas y observaciones") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") } }) },
                bottomBar = {
                    if (comments.any { !it.published }) {
                        Surface(shadowElevation = 8.dp) {
                            Button(onClick = onPublishAllDrafts, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) { Text("Publicar mis borradores") }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (comments.isEmpty()) item { Text("No hay marcas registradas.") }
                    items(comments, key = { it.id }) { comment ->
                        val view = decodeViewerMarkup(comment)
                        OutlinedCard(onClick = { onSelect(comment) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = CircleShape, color = Color(view.colorArgb).copy(alpha = 0.15f), modifier = Modifier.size(42.dp)) { Icon(view.type.icon(), null, Modifier.padding(9.dp), tint = Color(view.colorArgb)) }
                                Column(Modifier.weight(1f)) {
                                    Text("${view.source.displayLabel} · hoja ${comment.pageIndex + 1}", fontWeight = FontWeight.Bold)
                                    Text(view.text.ifBlank { "Sin nota adicional" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    Text(if (comment.published) "Publicada" else "Borrador privado", style = MaterialTheme.typography.labelSmall, color = if (comment.published) SkmSuccess else SkmWarning)
                                }
                                if (comment.pageIndex == page) Icon(Icons.Default.Visibility, "Hoja actual", tint = SkmOrange)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalHistoryDialog(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(14.dp).widthIn(max = 720.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Historial de revisión", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                LazyColumn(Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (timeline.isEmpty()) item { Text("No hay actividades registradas.") }
                    items(timeline, key = { it.id }) { event ->
                        OutlinedCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(event.detail, fontWeight = FontWeight.Bold)
                                Text("${event.actorName.ifBlank { event.actorEmail }} · ${formatTechnicalDate(event.createdAt)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalRequestChangesDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar cambios") },
        text = {
            OutlinedTextField(reason, { if (it.length <= 1200) reason = it }, label = { Text("Motivo y cambios requeridos") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { Button(onClick = { onConfirm(reason.trim()) }, enabled = reason.trim().length >= 5, colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) { Text("Detener revisión") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun ViewerTool.isDrawingTool(): Boolean = this !in setOf(ViewerTool.HAND, ViewerTool.SELECT)

private fun ViewerTool.toMarkupType(): ReviewMarkupType = when (this) {
    ViewerTool.TEXT -> ReviewMarkupType.TEXT
    ViewerTool.FREEHAND -> ReviewMarkupType.FREEHAND
    ViewerTool.HIGHLIGHT -> ReviewMarkupType.HIGHLIGHT
    ViewerTool.LINE -> ReviewMarkupType.LINE
    ViewerTool.ARROW -> ReviewMarkupType.ARROW
    ViewerTool.RECTANGLE -> ReviewMarkupType.RECTANGLE
    ViewerTool.ELLIPSE -> ReviewMarkupType.ELLIPSE
    ViewerTool.CLOUD -> ReviewMarkupType.CLOUD
    else -> ReviewMarkupType.FREEHAND
}

private fun ReviewMarkupType.icon() = when (this) {
    ReviewMarkupType.TEXT -> Icons.Default.TextFields
    ReviewMarkupType.FREEHAND -> Icons.Default.Draw
    ReviewMarkupType.HIGHLIGHT -> Icons.Default.BorderColor
    ReviewMarkupType.LINE -> Icons.Default.HorizontalRule
    ReviewMarkupType.ARROW -> Icons.Default.ArrowRightAlt
    ReviewMarkupType.RECTANGLE -> Icons.Default.CropSquare
    ReviewMarkupType.ELLIPSE -> Icons.Default.Circle
    ReviewMarkupType.CLOUD -> Icons.Default.CloudQueue
}

private fun DrawScope.drawViewerMarkup(markup: ViewerMarkup) {
    val color = Color(markup.colorArgb).copy(alpha = markup.opacity.coerceIn(0.08f, 1f))
    val stroke = (size.minDimension * markup.strokeWidth).coerceAtLeast(1.5f)
    when (markup.type) {
        ReviewMarkupType.TEXT -> Unit
        ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> drawPointPath(markup.points, color, stroke, markup.type == ReviewMarkupType.HIGHLIGHT)
        ReviewMarkupType.LINE -> drawLine(color, Offset(markup.x * size.width, markup.y * size.height), Offset(markup.endX * size.width, markup.endY * size.height), stroke, cap = StrokeCap.Round)
        ReviewMarkupType.ARROW -> drawArrow(color, Offset(markup.x * size.width, markup.y * size.height), Offset(markup.endX * size.width, markup.endY * size.height), stroke)
        ReviewMarkupType.RECTANGLE -> drawRect(color, Offset(markup.x * size.width, markup.y * size.height), Size(markup.width * size.width, markup.height * size.height), style = Stroke(stroke))
        ReviewMarkupType.ELLIPSE -> drawOval(color, Offset(markup.x * size.width, markup.y * size.height), Size(markup.width * size.width, markup.height * size.height), style = Stroke(stroke))
        ReviewMarkupType.CLOUD -> drawRoundRect(color, Offset(markup.x * size.width, markup.y * size.height), Size(markup.width * size.width, markup.height * size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 2f), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.2f, stroke * 1.1f), 0f)))
    }
}

private fun DrawScope.drawPreviewMarkup(preview: DrawingPreview, color: Color, strokeWidth: Float, opacity: Float) {
    val points = preview.points
    val xs = points.ifEmpty { listOf(preview.start, preview.end) }.map { it.x }
    val ys = points.ifEmpty { listOf(preview.start, preview.end) }.map { it.y }
    val minX = xs.minOrNull() ?: preview.start.x
    val minY = ys.minOrNull() ?: preview.start.y
    val maxX = xs.maxOrNull() ?: preview.end.x
    val maxY = ys.maxOrNull() ?: preview.end.y
    drawViewerMarkup(
        ViewerMarkup(
            source = PlanComment(), clientId = "preview", type = preview.type, text = "",
            x = minX, y = minY, endX = preview.end.x, endY = preview.end.y,
            width = (maxX - minX).coerceAtLeast(0.01f), height = (maxY - minY).coerceAtLeast(0.01f),
            colorArgb = color.copy(alpha = opacity).value.toLong().toInt(), strokeWidth = strokeWidth, opacity = opacity, points = points
        )
    )
}

private fun DrawScope.drawPointPath(points: List<ReviewPoint>, color: Color, stroke: Float, highlight: Boolean) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x * size.width, points.first().y * size.height)
        points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
    }
    drawPath(path, color.copy(alpha = if (highlight) color.alpha.coerceAtMost(0.35f) else color.alpha), style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun DrawScope.drawArrow(color: Color, start: Offset, end: Offset, stroke: Float) {
    drawLine(color, start, end, stroke, cap = StrokeCap.Round)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val head = (stroke * 5.5f).coerceAtLeast(12f)
    val a1 = angle + Math.PI * 0.82
    val a2 = angle - Math.PI * 0.82
    drawLine(color, end, Offset(end.x + cos(a1).toFloat() * head, end.y + sin(a1).toFloat() * head), stroke, cap = StrokeCap.Round)
    drawLine(color, end, Offset(end.x + cos(a2).toFloat() * head, end.y + sin(a2).toFloat() * head), stroke, cap = StrokeCap.Round)
}

private fun hitTestMarkup(markups: List<ViewerMarkup>, point: ReviewPoint): PlanComment? =
    markups.asReversed().firstOrNull { markup ->
        when (markup.type) {
            ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> markup.points.any { distance(it, point) < 0.025f }
            ReviewMarkupType.LINE, ReviewMarkupType.ARROW -> pointToSegmentDistance(point, ReviewPoint(markup.x, markup.y), ReviewPoint(markup.endX, markup.endY)) < 0.025f
            else -> point.x in (markup.x - 0.015f)..(markup.x + markup.width + 0.015f) && point.y in (markup.y - 0.015f)..(markup.y + markup.height + 0.015f)
        }
    }?.source

private fun distance(a: ReviewPoint, b: ReviewPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun pointToSegmentDistance(p: ReviewPoint, a: ReviewPoint, b: ReviewPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return distance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return distance(p, ReviewPoint(a.x + t * dx, a.y + t * dy))
}

private fun ViewerMarkup.toInput(): ReviewMarkupInput = ReviewMarkupInput(
    pageIndex = source.pageIndex,
    type = type,
    text = text,
    x = x,
    y = y,
    endX = endX,
    endY = endY,
    width = width,
    height = height,
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    opacity = opacity,
    points = points
)

private fun encodeViewerMarkup(input: ReviewMarkupInput, clientId: String): String {
    val points = JSONArray()
    input.points.forEach { points.put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())) }
    return MARKUP_PREFIX + JSONObject()
        .put("clientId", clientId)
        .put("type", input.type.name)
        .put("text", input.text)
        .put("endX", input.endX.toDouble())
        .put("endY", input.endY.toDouble())
        .put("height", input.height.toDouble())
        .put("colorArgb", input.colorArgb.toLong())
        .put("strokeWidth", input.strokeWidth.toDouble())
        .put("opacity", input.opacity.toDouble())
        .put("points", points)
        .toString()
}

private fun decodeViewerMarkup(comment: PlanComment): ViewerMarkup {
    if (!comment.text.startsWith(MARKUP_PREFIX)) {
        return ViewerMarkup(
            source = comment,
            clientId = comment.id,
            type = comment.markupType,
            text = comment.text,
            x = comment.x,
            y = comment.y,
            endX = comment.endX,
            endY = comment.endY,
            width = comment.width,
            height = comment.height,
            colorArgb = comment.colorArgb,
            strokeWidth = comment.strokeWidth,
            opacity = comment.opacity,
            points = comment.points
        )
    }
    return runCatching {
        val json = JSONObject(comment.text.removePrefix(MARKUP_PREFIX))
        val pointArray = json.optJSONArray("points") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointArray.length()) {
                val point = pointArray.optJSONObject(index) ?: continue
                add(ReviewPoint(point.optDouble("x").toFloat(), point.optDouble("y").toFloat()))
            }
        }
        ViewerMarkup(
            source = comment,
            clientId = json.optString("clientId", comment.id),
            type = runCatching { ReviewMarkupType.valueOf(json.optString("type")) }.getOrDefault(ReviewMarkupType.TEXT),
            text = json.optString("text"),
            x = comment.x,
            y = comment.y,
            endX = json.optDouble("endX", comment.endX.toDouble()).toFloat(),
            endY = json.optDouble("endY", comment.endY.toDouble()).toFloat(),
            width = comment.width,
            height = json.optDouble("height", comment.height.toDouble()).toFloat(),
            colorArgb = json.optLong("colorArgb", comment.colorArgb.toLong()).toInt(),
            strokeWidth = json.optDouble("strokeWidth", comment.strokeWidth.toDouble()).toFloat(),
            opacity = json.optDouble("opacity", comment.opacity.toDouble()).toFloat(),
            points = points
        )
    }.getOrElse {
        ViewerMarkup(comment, comment.id, ReviewMarkupType.TEXT, comment.text, comment.x, comment.y, comment.endX, comment.endY, comment.width, comment.height, comment.colorArgb, comment.strokeWidth, comment.opacity, comment.points)
    }
}

private suspend fun renderTechnicalPage(file: File, index: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val maxSide = 2600f
                val naturalScale = 2.2f
                val scale = min(naturalScale, maxSide / maxOf(page.width, page.height).toFloat())
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).roundToInt().coerceAtLeast(1),
                    (page.height * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap to renderer.pageCount
            }
        }
    }
}

private fun formatTechnicalDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}

private const val MARKUP_PREFIX = "@SKM_MARKUP_V2@"
