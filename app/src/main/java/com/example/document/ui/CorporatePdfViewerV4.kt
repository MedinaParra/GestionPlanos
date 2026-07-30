package com.example.document.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.document.model.DocumentRecord
import com.example.document.model.PlanComment
import com.example.document.model.ReviewMarkupInput
import com.example.document.model.ReviewMarkupType
import com.example.document.model.ReviewPoint
import com.example.document.model.WorkflowEvent
import com.example.ui.theme.SkmDanger
import com.example.ui.theme.SkmGraphite
import com.example.ui.theme.SkmOrange
import com.example.ui.theme.SkmOrangeDark
import com.example.ui.theme.SkmSuccess
import com.example.ui.theme.SkmWarning
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class AdaptiveTextPlacement(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.34f
)

private data class AdaptiveRedo(
    val input: ReviewMarkupInput,
    val clientId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporatePdfViewerV4(
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
    var tool by rememberSaveable { mutableStateOf(AdaptiveViewerTool.HAND) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    var showProperties by rememberSaveable { mutableStateOf(false) }
    var showMarks by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showRequestChanges by rememberSaveable { mutableStateOf(false) }
    var textPlacement by remember { mutableStateOf<AdaptiveTextPlacement?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var drawingPreview by remember { mutableStateOf<AdaptiveDrawingPreview?>(null) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFFF6A00.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(1f) }
    var undoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var redoItems by remember { mutableStateOf<List<AdaptiveRedo>>(emptyList()) }
    var hiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findViewerActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler {
        when {
            showPalette -> showPalette = false
            showMarks -> showMarks = false
            showHistory -> showHistory = false
            else -> onClose()
        }
    }

    LaunchedEffect(file, page) {
        val rendered = renderAdaptivePage(file, page)
        bitmap = rendered.first
        pageCount = rendered.second
        scale = 1f
        pan = Offset.Zero
        tool = AdaptiveViewerTool.HAND
        drawingPreview = null
        selected = null
    }

    LaunchedEffect(comments, hiddenIds) {
        val resolved = hiddenIds.mapNotNull { clientId ->
            comments.firstOrNull { decodeAdaptiveMarkup(it).clientId == clientId }
        }
        resolved.forEach(onDeleteComment)
        if (resolved.isNotEmpty()) {
            hiddenIds = hiddenIds - resolved.map { decodeAdaptiveMarkup(it).clientId }.toSet()
        }
    }

    val pageMarkups = remember(comments, page, hiddenIds) {
        comments
            .filter { it.pageIndex == page }
            .map(::decodeAdaptiveMarkup)
            .filterNot { it.clientId in hiddenIds }
    }
    val editable = canComment && document.status == "EN_REVISIÓN"
    val canApprove = document.canBeSignedBy(currentEmail)

    fun fittedPageSize(): Pair<Float, Float> {
        val image = bitmap ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fit = min(
            viewport.width.toFloat() / image.width.toFloat(),
            viewport.height.toFloat() / image.height.toFloat()
        )
        return image.width * fit to image.height * fit
    }

    fun clampPan(candidate: Offset, targetScale: Float = scale): Offset {
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f || targetScale <= 1f) return Offset.Zero
        val maxX = ((baseWidth * targetScale - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((baseHeight * targetScale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun screenToNormalized(position: Offset): ReviewPoint? {
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return null
        val pageOrigin = Offset(
            (viewport.width - baseWidth) / 2f,
            (viewport.height - baseHeight) / 2f
        )
        val center = Offset(baseWidth / 2f, baseHeight / 2f)
        val local = Offset(
            center.x + (position.x - pageOrigin.x - center.x - pan.x) / scale,
            center.y + (position.y - pageOrigin.y - center.y - pan.y) / scale
        )
        if (local.x !in 0f..baseWidth || local.y !in 0f..baseHeight) return null
        return ReviewPoint(
            (local.x / baseWidth).coerceIn(0f, 1f),
            (local.y / baseHeight).coerceIn(0f, 1f)
        )
    }

    fun applyTransform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(1f, 12f)
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return
        val pageOrigin = Offset(
            (viewport.width - baseWidth) / 2f,
            (viewport.height - baseHeight) / 2f
        )
        val center = Offset(baseWidth / 2f, baseHeight / 2f)
        val local = Offset(
            center.x + (centroid.x - pageOrigin.x - center.x - pan.x) / oldScale,
            center.y + (centroid.y - pageOrigin.y - center.y - pan.y) / oldScale
        )
        val candidate = Offset(
            centroid.x - pageOrigin.x - center.x - (local.x - center.x) * newScale + panChange.x,
            centroid.y - pageOrigin.y - center.y - (local.y - center.y) * newScale + panChange.y
        )
        scale = newScale
        pan = clampPan(candidate, newScale)
    }

    fun submitMarkup(input: ReviewMarkupInput, clientId: String = UUID.randomUUID().toString()) {
        val encoded = encodeAdaptiveMarkup(input, clientId)
        onAddComment(input.pageIndex, encoded, input.x, input.y, input.width)
        undoIds = undoIds + clientId
        redoItems = emptyList()
    }

    fun finishDrawing(preview: AdaptiveDrawingPreview) {
        val points = preview.points.ifEmpty { listOf(preview.start, preview.end) }
        val minX = points.minOfOrNull { it.x } ?: preview.start.x
        val minY = points.minOfOrNull { it.y } ?: preview.start.y
        val maxX = points.maxOfOrNull { it.x } ?: preview.end.x
        val maxY = points.maxOfOrNull { it.y } ?: preview.end.y
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
                strokeWidth = if (preview.type == ReviewMarkupType.HIGHLIGHT) strokeWidth.coerceAtLeast(0.018f) else strokeWidth,
                opacity = if (preview.type == ReviewMarkupType.HIGHLIGHT) opacity.coerceAtMost(0.38f) else opacity,
                points = preview.points
            )
        )
    }

    fun undo() {
        val clientId = undoIds.lastOrNull() ?: return
        val existing = comments.firstOrNull { decodeAdaptiveMarkup(it).clientId == clientId }
        if (existing != null) {
            val decoded = decodeAdaptiveMarkup(existing)
            onDeleteComment(existing)
            redoItems = redoItems + AdaptiveRedo(decoded.toAdaptiveInput(), clientId)
        } else {
            hiddenIds = hiddenIds + clientId
        }
        undoIds = undoIds.dropLast(1)
    }

    fun redo() {
        val redo = redoItems.lastOrNull() ?: return
        submitMarkup(redo.input, redo.clientId)
        redoItems = redoItems.dropLast(1)
        hiddenIds = hiddenIds - redo.clientId
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF202327))
    ) {
        val landscape = maxWidth > maxHeight
        val compactHeight = maxHeight < 430.dp

        AdaptiveViewerCanvas(
            bitmap = bitmap,
            viewport = viewport,
            scale = scale,
            pan = pan,
            tool = tool,
            editable = editable,
            markups = pageMarkups,
            drawingPreview = drawingPreview,
            onViewport = {
                viewport = it
                pan = clampPan(pan)
            },
            onTransform = ::applyTransform,
            onDoubleTap = { position ->
                if (scale > 1.05f) {
                    scale = 1f
                    pan = Offset.Zero
                } else {
                    applyTransform(position, Offset.Zero, 2.5f)
                }
            },
            screenToNormalized = ::screenToNormalized,
            onTextPoint = { point -> textPlacement = AdaptiveTextPlacement(page, point.x, point.y) },
            onSelect = { point -> selected = adaptiveHitTest(pageMarkups, point) },
            onDrawingChanged = { drawingPreview = it },
            onDrawingFinished = {
                drawingPreview = null
                finishDrawing(it)
            }
        )

        if (chromeVisible) {
            AdaptiveViewerHeader(
                document = document,
                page = page,
                pageCount = pageCount,
                scale = scale,
                landscape = landscape,
                compactHeight = compactHeight,
                onClose = onClose,
                onPrevious = { if (page > 0) page-- },
                onNext = { if (page + 1 < pageCount) page++ },
                onZoomOut = {
                    val target = (scale / 1.35f).coerceAtLeast(1f)
                    scale = target
                    pan = clampPan(pan, target)
                },
                onZoomIn = {
                    val target = (scale * 1.35f).coerceAtMost(12f)
                    scale = target
                    pan = clampPan(pan, target)
                },
                onFit = { scale = 1f; pan = Offset.Zero },
                onMarks = { showMarks = true },
                onHistory = { showHistory = true },
                onHide = { chromeVisible = false }
            )

            if (landscape) {
                AdaptiveLandscapeRail(
                    tool = tool,
                    editable = editable,
                    undoEnabled = undoIds.isNotEmpty(),
                    redoEnabled = redoItems.isNotEmpty(),
                    onTool = {
                        tool = it
                        drawingPreview = null
                        selected = null
                    },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onPalette = { showPalette = true }
                )
            } else {
                AdaptivePortraitBar(
                    tool = tool,
                    editable = editable,
                    undoEnabled = undoIds.isNotEmpty(),
                    onTool = {
                        tool = it
                        drawingPreview = null
                        selected = null
                    },
                    onUndo = ::undo,
                    onPalette = { showPalette = true }
                )
            }
        } else {
            FloatingActionButton(
                onClick = { chromeVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
                containerColor = SkmOrange,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Build, "Mostrar herramientas")
            }
        }

        if (showPalette) {
            AdaptiveToolPalette(
                landscape = landscape,
                tool = tool,
                editable = editable,
                showProperties = showProperties,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
                opacity = opacity,
                undoEnabled = undoIds.isNotEmpty(),
                redoEnabled = redoItems.isNotEmpty(),
                canApprove = canApprove,
                canRequestChanges = document.status == "EN_REVISIÓN",
                onDismiss = { showPalette = false },
                onTool = {
                    tool = it
                    drawingPreview = null
                    selected = null
                    if (it == AdaptiveViewerTool.HAND || it == AdaptiveViewerTool.SELECT) showPalette = false
                },
                onToggleProperties = { showProperties = !showProperties },
                onColor = { colorArgb = it },
                onStroke = { strokeWidth = it },
                onOpacity = { opacity = it },
                onUndo = ::undo,
                onRedo = ::redo,
                onMarks = { showPalette = false; showMarks = true },
                onHistory = { showPalette = false; showHistory = true },
                onApprove = { showPalette = false; onApprove() },
                onRequestChanges = { showPalette = false; showRequestChanges = true }
            )
        }
    }

    textPlacement?.let { placement ->
        AdaptiveTextDialog(
            page = placement.pageIndex,
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
        AdaptiveMarkupDialog(
            comment = comment,
            currentEmail = currentEmail,
            isAdmin = isAdmin,
            editable = editable,
            onDismiss = { selected = null },
            onPublish = if (!comment.published && comment.authorEmail.equals(currentEmail, true)) {
                { selected = null; onPublishComment(comment) }
            } else null,
            onDelete = {
                selected = null
                onDeleteComment(comment)
            },
            onUpdate = { newText ->
                val decoded = decodeAdaptiveMarkup(comment)
                val input = decoded.toAdaptiveInput().copy(text = newText)
                selected = null
                onUpdateComment(
                    comment.copy(
                        text = encodeAdaptiveMarkup(input, decoded.clientId),
                        markupType = decoded.type,
                        endX = decoded.endX,
                        endY = decoded.endY,
                        height = decoded.height,
                        colorArgb = decoded.colorArgb,
                        strokeWidth = decoded.strokeWidth,
                        opacity = decoded.opacity,
                        points = decoded.points
                    )
                )
            }
        )
    }

    if (showMarks) {
        AdaptiveMarksDialog(
            comments = comments,
            currentPage = page,
            currentEmail = currentEmail,
            onDismiss = { showMarks = false },
            onSelect = {
                showMarks = false
                page = it.pageIndex.coerceIn(0, pageCount - 1)
                selected = it
            },
            onPublishAll = {
                comments.filter { !it.published && it.authorEmail.equals(currentEmail, true) }.forEach(onPublishComment)
                showMarks = false
            }
        )
    }

    if (showHistory) {
        AdaptiveHistoryDialog(timeline, onDismiss = { showHistory = false })
    }

    if (showRequestChanges) {
        AdaptiveRequestChangesDialog(
            onDismiss = { showRequestChanges = false },
            onConfirm = {
                showRequestChanges = false
                onRequestChanges(it)
            }
        )
    }
}

@Composable
private fun AdaptiveViewerCanvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    scale: Float,
    pan: Offset,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    markups: List<AdaptiveMarkup>,
    drawingPreview: AdaptiveDrawingPreview?,
    onViewport: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onTextPoint: (ReviewPoint) -> Unit,
    onSelect: (ReviewPoint) -> Unit,
    onDrawingChanged: (AdaptiveDrawingPreview?) -> Unit,
    onDrawingFinished: (AdaptiveDrawingPreview) -> Unit
) {
    val image = bitmap
    val fit = if (image != null && viewport.width > 0 && viewport.height > 0) {
        min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    } else 1f
    val pageWidthPx = image?.width?.times(fit) ?: 1f
    val pageHeightPx = image?.height?.times(fit) ?: 1f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pageWidthDp = with(density) { pageWidthPx.toDp() }
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    val gestures = when {
        tool == AdaptiveViewerTool.HAND -> Modifier
            .pointerInput(viewport, scale, pan) {
                detectTransformGestures(panZoomLock = false) { centroid, panChange, zoom, _ ->
                    onTransform(centroid, panChange, zoom)
                }
            }
            .pointerInput(scale) { detectTapGestures(onDoubleTap = onDoubleTap) }
        tool == AdaptiveViewerTool.SELECT -> Modifier.pointerInput(markups, scale, pan) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onSelect) }
        }
        tool == AdaptiveViewerTool.TEXT && editable -> Modifier.pointerInput(scale, pan) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onTextPoint) }
        }
        tool.editsDocument && editable -> Modifier.pointerInput(tool, scale, pan) {
            var start: ReviewPoint? = null
            var points = mutableListOf<ReviewPoint>()
            detectDragGestures(
                onDragStart = { position ->
                    start = screenToNormalized(position)
                    points = mutableListOf<ReviewPoint>().apply { start?.let(::add) }
                    val first = start
                    if (first != null) onDrawingChanged(
                        AdaptiveDrawingPreview(requireNotNull(tool.markupType), first, first, points.toList())
                    )
                },
                onDragCancel = { onDrawingChanged(null) },
                onDragEnd = {
                    val first = start
                    if (first != null) {
                        val last = points.lastOrNull() ?: first
                        onDrawingFinished(
                            AdaptiveDrawingPreview(requireNotNull(tool.markupType), first, last, points.toList())
                        )
                    }
                    onDrawingChanged(null)
                },
                onDrag = { change, _ ->
                    val point = screenToNormalized(change.position) ?: return@detectDragGestures
                    if (tool == AdaptiveViewerTool.FREEHAND || tool == AdaptiveViewerTool.HIGHLIGHT) {
                        if (points.isEmpty() || adaptivePointDistance(points.last(), point) > 0.0015f) points.add(point)
                    } else {
                        points = mutableListOf(start ?: point, point)
                    }
                    val first = start ?: point
                    onDrawingChanged(
                        AdaptiveDrawingPreview(requireNotNull(tool.markupType), first, point, points.toList())
                    )
                    change.consume()
                }
            )
        }
        else -> Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF202327))
            .onGloballyPositioned { onViewport(it.size) }
            .then(gestures),
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
                        transformOrigin = TransformOrigin.Center
                    }
                    .background(Color.White)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Plano PDF",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(Modifier.fillMaxSize()) {
                    markups.forEach { drawAdaptiveMarkup(it) }
                    drawingPreview?.let {
                        drawAdaptivePreview(it, Color(0xFFFF6A00), 0.004f, 0.90f)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    markups.filter { it.type == ReviewMarkupType.TEXT }.forEach { markup ->
                        Card(
                            modifier = Modifier
                                .offset(
                                    x = maxWidth * markup.x.coerceIn(0f, 0.92f),
                                    y = maxHeight * markup.y.coerceIn(0f, 0.92f)
                                )
                                .width((maxWidth * markup.width.coerceIn(0.18f, 0.62f)).coerceAtLeast(90.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(markup.colorArgb).copy(alpha = markup.opacity.coerceIn(0.35f, 0.96f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                markup.text,
                                Modifier.padding(6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveViewerHeader(
    document: DocumentRecord,
    page: Int,
    pageCount: Int,
    scale: Float,
    landscape: Boolean,
    compactHeight: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onMarks: () -> Unit,
    onHistory: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = if (landscape) 72.dp else 10.dp, vertical = 8.dp)
            .fillMaxWidth(),
        color = Color.White.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, "Cerrar visor")
            }
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                    "OT ${document.otNumber} · ${document.code}",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compactHeight) {
                    Text(
                        "Rev ${document.revision} · ${adaptiveStatusLabel(document.status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF616161),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onPrevious, enabled = page > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ChevronLeft, "Hoja anterior")
            }
            Surface(color = Color(0xFFF1F2F4), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "${page + 1}/$pageCount",
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onNext, enabled = page + 1 < pageCount, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ChevronRight, "Hoja siguiente")
            }
            if (landscape) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
                IconButton(onClick = onZoomIn, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onFit, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.FitScreen, "Ajustar") }
                IconButton(onClick = onMarks, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Layers, "Marcas") }
                IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.History, "Historial") }
            }
            IconButton(onClick = onHide, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Fullscreen, "Ocultar controles")
            }
        }
    }
}

@Composable
private fun BoxScope.AdaptivePortraitBar(
    tool: AdaptiveViewerTool,
    editable: Boolean,
    undoEnabled: Boolean,
    onTool: (AdaptiveViewerTool) -> Unit,
    onUndo: () -> Unit,
    onPalette: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(10.dp)
            .fillMaxWidth(),
        color = Color.White.copy(alpha = 0.97f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdaptivePrimaryButton("Mover", Icons.Default.PanTool, tool == AdaptiveViewerTool.HAND, true, Modifier.weight(1f)) {
                onTool(AdaptiveViewerTool.HAND)
            }
            AdaptivePrimaryButton("Texto", Icons.Default.TextFields, tool == AdaptiveViewerTool.TEXT, editable, Modifier.weight(1f)) {
                onTool(AdaptiveViewerTool.TEXT)
            }
            AdaptivePrimaryButton("Lápiz", Icons.Default.Draw, tool == AdaptiveViewerTool.FREEHAND, editable, Modifier.weight(1f)) {
                onTool(AdaptiveViewerTool.FREEHAND)
            }
            AdaptivePrimaryButton("Deshacer", Icons.Default.Undo, false, undoEnabled, Modifier.weight(1f), onUndo)
            AdaptivePrimaryButton("Herramientas", Icons.Default.Apps, false, true, Modifier.weight(1f), onPalette)
        }
    }
}

@Composable
private fun BoxScope.AdaptiveLandscapeRail(
    tool: AdaptiveViewerTool,
    editable: Boolean,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onTool: (AdaptiveViewerTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPalette: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 10.dp, top = 72.dp, bottom = 16.dp)
            .width(62.dp)
            .heightIn(max = 500.dp),
        color = Color.White.copy(alpha = 0.97f),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AdaptiveRailButton(Icons.Default.PanTool, "Navegar", tool == AdaptiveViewerTool.HAND, true) { onTool(AdaptiveViewerTool.HAND) }
            AdaptiveRailButton(Icons.Default.NearMe, "Seleccionar", tool == AdaptiveViewerTool.SELECT, true) { onTool(AdaptiveViewerTool.SELECT) }
            AdaptiveRailButton(Icons.Default.TextFields, "Texto", tool == AdaptiveViewerTool.TEXT, editable) { onTool(AdaptiveViewerTool.TEXT) }
            AdaptiveRailButton(Icons.Default.Draw, "Lápiz", tool == AdaptiveViewerTool.FREEHAND, editable) { onTool(AdaptiveViewerTool.FREEHAND) }
            AdaptiveRailButton(Icons.Default.BorderColor, "Resaltador", tool == AdaptiveViewerTool.HIGHLIGHT, editable) { onTool(AdaptiveViewerTool.HIGHLIGHT) }
            HorizontalDivider(Modifier.padding(horizontal = 10.dp))
            AdaptiveRailButton(Icons.Default.Undo, "Deshacer", false, undoEnabled, onUndo)
            AdaptiveRailButton(Icons.Default.Redo, "Rehacer", false, redoEnabled, onRedo)
            AdaptiveRailButton(Icons.Default.Apps, "Todas las herramientas", false, true, onPalette)
        }
    }
}

@Composable
private fun AdaptivePrimaryButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 58.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = if (selected) SkmOrange else Color.Transparent,
            shape = CircleShape
        ) {
            Icon(icon, label, Modifier.padding(8.dp), tint = if (selected) Color.White else SkmGraphite)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AdaptiveRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(50.dp).alpha(if (enabled) 1f else 0.35f),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) SkmOrange else Color.Transparent,
            contentColor = if (selected) Color.White else SkmGraphite
        )
    ) {
        Icon(icon, label)
    }
}

@Composable
private fun BoxScope.AdaptiveToolPalette(
    landscape: Boolean,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    showProperties: Boolean,
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    canApprove: Boolean,
    canRequestChanges: Boolean,
    onDismiss: () -> Unit,
    onTool: (AdaptiveViewerTool) -> Unit,
    onToggleProperties: () -> Unit,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMarks: () -> Unit,
    onHistory: () -> Unit,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss)
    )
    Surface(
        modifier = if (landscape) {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 330.dp, max = 420.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        },
        color = Color.White,
        shape = if (landscape) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 18.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Herramientas del plano", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (editable) "Selecciona una herramienta de revisión." else "Plano en solo lectura: la edición permanece visible, pero deshabilitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (editable) Color(0xFF616161) else SkmWarning
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }

            AdaptiveToolGrid(tool, editable, onTool)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Undo, null); Spacer(Modifier.width(6.dp)); Text("Deshacer")
                }
                OutlinedButton(onClick = onRedo, enabled = redoEnabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Redo, null); Spacer(Modifier.width(6.dp)); Text("Rehacer")
                }
            }

            OutlinedButton(
                onClick = onToggleProperties,
                enabled = tool.editsDocument && editable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tune, null)
                Spacer(Modifier.width(8.dp))
                Text(if (showProperties) "Ocultar color y espesor" else "Color, espesor y opacidad")
            }

            if (showProperties && tool.editsDocument && editable) {
                AdaptiveProperties(colorArgb, strokeWidth, opacity, onColor, onStroke, onOpacity)
            }

            Text("Revisión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMarks, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Layers, null); Spacer(Modifier.width(6.dp)); Text("Marcas")
                }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("Historial")
                }
            }

            Text("Decisión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = onRequestChanges,
                enabled = canRequestChanges,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)
            ) {
                Icon(Icons.Default.ChangeCircle, null); Spacer(Modifier.width(8.dp)); Text("Solicitar cambios")
            }
            Button(
                onClick = onApprove,
                enabled = canApprove,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)
            ) {
                Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Aprobar y firmar")
            }
            if (!canApprove) {
                Text(
                    "Aprobar y firmar permanece deshabilitado hasta que sea tu turno de revisión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
private fun AdaptiveToolGrid(
    selected: AdaptiveViewerTool,
    editable: Boolean,
    onTool: (AdaptiveViewerTool) -> Unit
) {
    val tools = AdaptiveViewerTool.entries
    tools.chunked(4).forEach { rowTools ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowTools.forEach { candidate ->
                val enabled = !candidate.editsDocument || editable
                AdaptiveToolTile(
                    candidate,
                    selected == candidate,
                    enabled,
                    Modifier.weight(1f)
                ) { onTool(candidate) }
            }
            repeat(4 - rowTools.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AdaptiveToolTile(
    tool: AdaptiveViewerTool,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val icon = when (tool) {
        AdaptiveViewerTool.HAND -> Icons.Default.PanTool
        AdaptiveViewerTool.SELECT -> Icons.Default.NearMe
        AdaptiveViewerTool.TEXT -> Icons.Default.TextFields
        AdaptiveViewerTool.FREEHAND -> Icons.Default.Draw
        AdaptiveViewerTool.HIGHLIGHT -> Icons.Default.BorderColor
        AdaptiveViewerTool.LINE -> Icons.Default.HorizontalRule
        AdaptiveViewerTool.ARROW -> Icons.Default.ArrowRightAlt
        AdaptiveViewerTool.RECTANGLE -> Icons.Default.CropSquare
        AdaptiveViewerTool.ELLIPSE -> Icons.Default.Circle
        AdaptiveViewerTool.CLOUD -> Icons.Default.CloudQueue
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.35f),
        color = if (selected) SkmOrange.copy(alpha = 0.14f) else Color(0xFFF4F5F7),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, SkmOrange) else null,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, tool.label, tint = if (selected) SkmOrangeDark else SkmGraphite)
            Text(tool.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdaptiveProperties(
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit
) {
    val colors = listOf(
        0xFFFF6A00.toInt(), 0xFFD32F2F.toInt(), 0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(), 0xFF212121.toInt(), 0xFFF9A825.toInt()
    )
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F8))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Color", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { value ->
                    Surface(
                        onClick = { onColor(value) },
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = Color(value),
                        border = if (value == colorArgb) androidx.compose.foundation.BorderStroke(3.dp, SkmGraphite) else null
                    ) {}
                }
            }
            Text("Espesor", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0025f, 0.004f, 0.008f, 0.014f).forEach { value ->
                    FilterChip(
                        selected = abs(strokeWidth - value) < 0.001f,
                        onClick = { onStroke(value) },
                        label = { Text("${(value * 1000).roundToInt()}") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacidad")
                Slider(opacity, onOpacity, valueRange = 0.15f..1f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("${(opacity * 100).roundToInt()}%")
            }
        }
    }
}

@Composable
private fun AdaptiveTextDialog(
    page: Int,
    initialText: String,
    initialWidth: Float,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit
) {
    var text by rememberSaveable(page) { mutableStateOf(initialText) }
    var width by rememberSaveable(page) { mutableFloatStateOf(initialWidth) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).imePadding().widthIn(max = 640.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Observación · hoja ${page + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Text("Se guardará como borrador privado hasta que decidas publicarla.", color = Color(0xFF616161))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1200) text = it },
                    label = { Text("Texto del comentario") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    supportingText = { Text("${text.length}/1200") }
                )
                Text("Ancho del cuadro: ${(width * 100).roundToInt()}%")
                Slider(width, { width = it }, valueRange = 0.20f..0.62f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        onClick = { onSave(text.trim(), width) },
                        enabled = text.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)
                    ) { Text("Guardar borrador") }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveMarkupDialog(
    comment: PlanComment,
    currentEmail: String,
    isAdmin: Boolean,
    editable: Boolean,
    onDismiss: () -> Unit,
    onPublish: (() -> Unit)?,
    onDelete: () -> Unit,
    onUpdate: (String) -> Unit
) {
    val markup = decodeAdaptiveMarkup(comment)
    val canModify = editable && comment.canBeModifiedBy(currentEmail, isAdmin)
    var text by rememberSaveable(comment.id) { mutableStateOf(markup.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(markup.source.displayLabel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (comment.published) "Publicada" else "Borrador privado", color = if (comment.published) SkmSuccess else SkmWarning)
                Text("${comment.authorName.ifBlank { comment.authorEmail }} · hoja ${comment.pageIndex + 1}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (canModify && it.length <= 1200) text = it },
                    label = { Text(if (markup.type == ReviewMarkupType.TEXT) "Comentario" else "Nota de la marca") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    readOnly = !canModify
                )
                if (canModify) {
                    Button(onClick = { onUpdate(text.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cambios") }
                    onPublish?.let { publish ->
                        Button(onClick = publish, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) {
                            Icon(Icons.Default.Publish, null); Spacer(Modifier.width(8.dp)); Text("Publicar para todos")
                        }
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Eliminar")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AdaptiveMarksDialog(
    comments: List<PlanComment>,
    currentPage: Int,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSelect: (PlanComment) -> Unit,
    onPublishAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Marcas y comentarios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                val ownDrafts = comments.count { !it.published && it.authorEmail.equals(currentEmail, true) }
                Button(onClick = onPublishAll, enabled = ownDrafts > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) {
                    Icon(Icons.Default.Publish, null); Spacer(Modifier.width(8.dp)); Text("Publicar mis borradores ($ownDrafts)")
                }
                Spacer(Modifier.height(8.dp))
                if (comments.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay observaciones en este plano.") }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        comments.sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt }).forEach { comment ->
                            val markup = decodeAdaptiveMarkup(comment)
                            ListItem(
                                headlineContent = { Text(markup.source.displayLabel) },
                                supportingContent = {
                                    Text("Hoja ${comment.pageIndex + 1}${if (comment.pageIndex == currentPage) " · actual" else ""} · ${if (comment.published) "Publicada" else "Borrador"}")
                                },
                                leadingContent = { Icon(markupIcon(markup.type), null, tint = Color(markup.colorArgb)) },
                                modifier = Modifier.clickable { onSelect(comment) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveHistoryDialog(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de revisión") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (timeline.isEmpty()) Text("Aún no hay eventos.")
                timeline.sortedByDescending { it.createdAt }.forEach { event ->
                    ListItem(
                        headlineContent = { Text(event.detail.ifBlank { event.type.name }) },
                        supportingContent = { Text("${event.actorName.ifBlank { event.actorEmail }} · ${adaptiveDate(event.createdAt)}") },
                        leadingContent = { Icon(Icons.Default.History, null) }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AdaptiveRequestChangesDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar cambios") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= 1200) reason = it },
                label = { Text("Motivo obligatorio") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SkmDanger)
            ) { Text("Solicitar cambios") }
        }
    )
}

private fun Context.findViewerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findViewerActivity()
    else -> null
}

private fun adaptiveStatusLabel(status: String): String = when (status) {
    "EN_REVISIÓN" -> "En revisión"
    "CAMBIOS_SOLICITADOS" -> "Cambios solicitados"
    "APTO_PARA_FABRICACIÓN" -> "Apto para fabricación"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun adaptivePointDistance(a: ReviewPoint, b: ReviewPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun markupIcon(type: ReviewMarkupType): ImageVector = when (type) {
    ReviewMarkupType.TEXT -> Icons.Default.TextFields
    ReviewMarkupType.FREEHAND -> Icons.Default.Draw
    ReviewMarkupType.HIGHLIGHT -> Icons.Default.BorderColor
    ReviewMarkupType.LINE -> Icons.Default.HorizontalRule
    ReviewMarkupType.ARROW -> Icons.Default.ArrowRightAlt
    ReviewMarkupType.RECTANGLE -> Icons.Default.CropSquare
    ReviewMarkupType.ELLIPSE -> Icons.Default.Circle
    ReviewMarkupType.CLOUD -> Icons.Default.CloudQueue
}

private fun adaptiveDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
