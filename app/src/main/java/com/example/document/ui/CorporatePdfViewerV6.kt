package com.example.document.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.SkmSuccess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class V6Panel { NONE, TOOLS, MARKS, HISTORY, ACTIONS }

private data class V6Editor(
    val pageIndex: Int,
    val type: ReviewMarkupType,
    val text: String,
    val x: Float,
    val y: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
    val height: Float,
    val labelX: Float,
    val labelY: Float,
    val source: PlanComment? = null,
    val clientId: String = UUID.randomUUID().toString()
)

private data class V6Action(val clientId: String, val input: ReviewMarkupInput)
private val V6_SYMBOLS = listOf("Ø", "R", "±", "°", "M", "N6", "N8", "H7", "H8", "△", "○", "×", "✓")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporatePdfViewerV6(
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
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf(V6Panel.NONE) }
    var editor by remember { mutableStateOf<V6Editor?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var movingSelected by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AdaptiveDrawingPreview?>(null) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFE53935.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.95f) }
    var requestChangesOpen by rememberSaveable { mutableStateOf(false) }
    val pending = remember { mutableStateListOf<V6Action>() }
    var undoStack by remember { mutableStateOf<List<V6Action>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<V6Action>>(emptyList()) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findV6Activity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler {
        when {
            editor != null -> editor = null
            selected != null -> selected = null
            panel != V6Panel.NONE -> panel = V6Panel.NONE
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
        editor = null
        selected = null
        movingSelected = false
        preview = null
    }

    LaunchedEffect(comments) {
        val storedIds = comments.map { decodeAdaptiveMarkup(it).clientId }.toSet()
        pending.removeAll { it.clientId in storedIds }
    }

    val storedMarkups = remember(comments, page) {
        comments.filter { it.pageIndex == page }.map(::decodeAdaptiveMarkup)
    }
    val pendingMarkups = pending.filter { it.input.pageIndex == page }.map { action ->
        action.input.toV6Markup(action.clientId)
    }
    val pageMarkups = storedMarkups + pendingMarkups
    val editable = canComment && document.status == "EN_REVISIÓN"
    val canApprove = document.canBeSignedBy(currentEmail)

    fun fittedPageSize(): Pair<Float, Float> {
        val image = bitmap ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
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
        val origin = Offset((viewport.width - baseWidth) / 2f, (viewport.height - baseHeight) / 2f)
        val center = Offset(baseWidth / 2f, baseHeight / 2f)
        val local = Offset(
            center.x + (position.x - origin.x - center.x - pan.x) / scale,
            center.y + (position.y - origin.y - center.y - pan.y) / scale
        )
        if (local.x !in 0f..baseWidth || local.y !in 0f..baseHeight) return null
        return ReviewPoint((local.x / baseWidth).coerceIn(0f, 1f), (local.y / baseHeight).coerceIn(0f, 1f))
    }

    fun applyTransform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(1f, 12f)
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return
        val origin = Offset((viewport.width - baseWidth) / 2f, (viewport.height - baseHeight) / 2f)
        val center = Offset(baseWidth / 2f, baseHeight / 2f)
        val local = Offset(
            center.x + (centroid.x - origin.x - center.x - pan.x) / oldScale,
            center.y + (centroid.y - origin.y - center.y - pan.y) / oldScale
        )
        val candidate = Offset(
            centroid.x - origin.x - center.x - (local.x - center.x) * newScale + panChange.x,
            centroid.y - origin.y - center.y - (local.y - center.y) * newScale + panChange.y
        )
        scale = newScale
        pan = clampPan(candidate, newScale)
    }

    fun submit(input: ReviewMarkupInput, clientId: String = UUID.randomUUID().toString(), rememberAction: Boolean = true) {
        val action = V6Action(clientId, input)
        pending.removeAll { it.clientId == clientId }
        pending += action
        onAddComment(input.pageIndex, encodeAdaptiveMarkup(input, clientId), input.x, input.y, input.width)
        if (rememberAction) {
            undoStack = undoStack + action
            redoStack = emptyList()
        }
    }

    fun updateExisting(source: PlanComment, input: ReviewMarkupInput, clientId: String) {
        onUpdateComment(
            source.copy(
                text = encodeAdaptiveMarkup(input, clientId),
                pageIndex = input.pageIndex,
                markupType = input.type,
                x = input.x,
                y = input.y,
                width = input.width,
                height = input.height,
                endX = input.endX,
                endY = input.endY,
                colorArgb = input.colorArgb,
                strokeWidth = input.strokeWidth,
                opacity = input.opacity,
                points = input.points
            )
        )
    }

    fun undo() {
        val action = undoStack.lastOrNull() ?: return
        val stored = comments.firstOrNull { decodeAdaptiveMarkup(it).clientId == action.clientId }
        if (stored != null) onDeleteComment(stored) else pending.removeAll { it.clientId == action.clientId }
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + action
    }

    fun redo() {
        val action = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        submit(action.input, action.clientId, rememberAction = false)
        undoStack = undoStack + action
    }

    fun saveEditor(value: V6Editor) {
        val cleanText = value.text.trim()
        if (cleanText.isBlank()) return
        val input = ReviewMarkupInput(
            pageIndex = value.pageIndex,
            type = value.type,
            text = cleanText,
            x = value.x,
            y = value.y,
            endX = value.endX,
            endY = value.endY,
            width = value.width,
            height = value.height,
            labelX = value.labelX,
            labelY = value.labelY,
            colorArgb = colorArgb,
            strokeWidth = strokeWidth,
            opacity = opacity
        )
        value.source?.let { updateExisting(it, input, value.clientId) } ?: submit(input, value.clientId)
        editor = null
        selected = null
        tool = AdaptiveViewerTool.HAND
    }

    fun openEditor(comment: PlanComment) {
        val mark = decodeAdaptiveMarkup(comment)
        editor = V6Editor(
            pageIndex = comment.pageIndex,
            type = mark.type,
            text = mark.text,
            x = mark.x,
            y = mark.y,
            endX = mark.endX,
            endY = mark.endY,
            width = mark.width,
            height = mark.height,
            labelX = mark.labelX,
            labelY = mark.labelY,
            source = comment,
            clientId = mark.clientId
        )
        selected = null
        movingSelected = false
        tool = AdaptiveViewerTool.HAND
    }

    fun moveMark(comment: PlanComment, point: ReviewPoint) {
        val mark = decodeAdaptiveMarkup(comment)
        val input = mark.toAdaptiveInput()
        val moved = when (mark.type) {
            ReviewMarkupType.TEXT, ReviewMarkupType.SYMBOL -> input.copy(
                x = point.x.coerceIn(0f, 0.95f),
                y = point.y.coerceIn(0f, 0.95f),
                endX = (point.x + mark.width).coerceAtMost(1f),
                endY = (point.y + mark.height).coerceAtMost(1f)
            )
            ReviewMarkupType.DIMENSION -> input.copy(labelX = point.x, labelY = point.y)
            else -> {
                val dx = point.x - (mark.x + mark.endX) / 2f
                val dy = point.y - (mark.y + mark.endY) / 2f
                input.copy(
                    x = (mark.x + dx).coerceIn(0f, 1f),
                    y = (mark.y + dy).coerceIn(0f, 1f),
                    endX = (mark.endX + dx).coerceIn(0f, 1f),
                    endY = (mark.endY + dy).coerceIn(0f, 1f),
                    labelX = (mark.labelX + dx).coerceIn(0f, 1f),
                    labelY = (mark.labelY + dy).coerceIn(0f, 1f),
                    points = mark.points.map { ReviewPoint((it.x + dx).coerceIn(0f, 1f), (it.y + dy).coerceIn(0f, 1f)) }
                )
            }
        }
        updateExisting(comment, moved, mark.clientId)
        selected = null
        movingSelected = false
        tool = AdaptiveViewerTool.HAND
    }

    fun finishDrawing(drawing: AdaptiveDrawingPreview) {
        if (adaptivePointDistance(drawing.start, drawing.end) < 0.008f) {
            preview = null
            tool = AdaptiveViewerTool.HAND
            return
        }
        if (drawing.type == ReviewMarkupType.DIMENSION) {
            editor = V6Editor(
                pageIndex = page,
                type = ReviewMarkupType.DIMENSION,
                text = "",
                x = drawing.start.x,
                y = drawing.start.y,
                endX = drawing.end.x,
                endY = drawing.end.y,
                width = 0.20f,
                height = 0.055f,
                labelX = drawing.label.x,
                labelY = drawing.label.y
            )
            preview = null
            tool = AdaptiveViewerTool.HAND
            return
        }
        val points = drawing.points.ifEmpty { listOf(drawing.start, drawing.end) }
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        submit(
            ReviewMarkupInput(
                pageIndex = page,
                type = drawing.type,
                x = drawing.start.x,
                y = drawing.start.y,
                endX = drawing.end.x,
                endY = drawing.end.y,
                width = (maxX - minX).coerceAtLeast(0.01f),
                height = (maxY - minY).coerceAtLeast(0.01f),
                labelX = drawing.label.x,
                labelY = drawing.label.y,
                colorArgb = colorArgb,
                strokeWidth = if (drawing.type == ReviewMarkupType.HIGHLIGHT) strokeWidth.coerceAtLeast(0.018f) else strokeWidth,
                opacity = if (drawing.type == ReviewMarkupType.HIGHLIGHT) opacity.coerceAtMost(0.38f) else opacity,
                points = drawing.points
            )
        )
        preview = null
        tool = AdaptiveViewerTool.HAND
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF202327))) {
        val landscape = maxWidth > maxHeight
        V6Canvas(
            bitmap = bitmap,
            viewport = viewport,
            scale = scale,
            pan = pan,
            tool = tool,
            editable = editable,
            markups = pageMarkups,
            preview = preview,
            editor = editor,
            selected = selected,
            colorArgb = colorArgb,
            strokeWidth = strokeWidth,
            opacity = opacity,
            onViewport = { viewport = it; pan = clampPan(pan) },
            onTransform = ::applyTransform,
            screenToNormalized = ::screenToNormalized,
            onTap = { point, doubleTap ->
                when {
                    doubleTap && tool == AdaptiveViewerTool.HAND -> {
                        if (scale > 1.05f) { scale = 1f; pan = Offset.Zero }
                        else {
                            val (w, h) = fittedPageSize()
                            val screen = Offset((viewport.width - w) / 2f + point.x * w, (viewport.height - h) / 2f + point.y * h)
                            applyTransform(screen, Offset.Zero, 2.5f)
                        }
                    }
                    movingSelected && selected != null -> moveMark(requireNotNull(selected), point)
                    tool == AdaptiveViewerTool.TEXT && editable -> {
                        editor = V6Editor(page, ReviewMarkupType.TEXT, "", point.x, point.y, (point.x + 0.32f).coerceAtMost(1f), (point.y + 0.10f).coerceAtMost(1f), 0.32f, 0.10f, point.x, point.y)
                        tool = AdaptiveViewerTool.HAND
                    }
                    tool == AdaptiveViewerTool.SYMBOL && editable -> {
                        editor = V6Editor(page, ReviewMarkupType.SYMBOL, "Ø", point.x, point.y, (point.x + 0.10f).coerceAtMost(1f), (point.y + 0.08f).coerceAtMost(1f), 0.10f, 0.08f, point.x, point.y)
                        tool = AdaptiveViewerTool.HAND
                    }
                    tool == AdaptiveViewerTool.SELECT -> selected = adaptiveHitTest(pageMarkups, point)
                }
            },
            onPreview = { preview = it },
            onDrawingFinished = ::finishDrawing,
            onEditorChange = { editor = it },
            onEditorSave = { editor?.let(::saveEditor) },
            onEditorCancel = { editor = null; tool = AdaptiveViewerTool.HAND }
        )

        if (controlsVisible) {
            V6TopBar(
                document = document,
                page = page,
                pageCount = pageCount,
                scale = scale,
                landscape = landscape,
                onClose = onClose,
                onPrevious = { if (page > 0) page-- },
                onNext = { if (page + 1 < pageCount) page++ },
                onZoomOut = { val target = (scale / 1.35f).coerceAtLeast(1f); scale = target; pan = clampPan(pan, target) },
                onZoomIn = { val target = (scale * 1.35f).coerceAtMost(12f); scale = target; pan = clampPan(pan, target) },
                onFit = { scale = 1f; pan = Offset.Zero },
                onMarks = { panel = V6Panel.MARKS },
                onHistory = { panel = V6Panel.HISTORY },
                onActions = { panel = V6Panel.ACTIONS },
                onHide = { controlsVisible = false }
            )
            V6ToolRail(
                landscape = landscape,
                tool = tool,
                editable = editable && editor == null,
                onTool = { selected = null; movingSelected = false; preview = null; tool = it },
                onMore = { panel = V6Panel.TOOLS }
            )
        } else {
            FloatingActionButton(
                onClick = { controlsVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                containerColor = SkmOrange,
                contentColor = Color.White
            ) { Icon(Icons.Default.Edit, "Mostrar controles") }
        }

        selected?.let { comment ->
            V6SelectionBar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (landscape) 12.dp else 84.dp),
                comment = comment,
                canModify = editable && comment.canBeModifiedBy(currentEmail, isAdmin),
                canPublish = !comment.published && comment.authorEmail.equals(currentEmail, true),
                onEdit = { openEditor(comment) },
                onMove = { movingSelected = true; tool = AdaptiveViewerTool.SELECT },
                onPublish = { selected = null; onPublishComment(comment) },
                onDelete = { selected = null; onDeleteComment(comment) },
                onClose = { selected = null; movingSelected = false }
            )
        }

        when (panel) {
            V6Panel.TOOLS -> V6ToolsPanel(
                modifier = if (landscape) Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 360.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 520.dp),
                tool = tool,
                editable = editable && editor == null,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
                opacity = opacity,
                undoEnabled = undoStack.isNotEmpty(),
                redoEnabled = redoStack.isNotEmpty(),
                onDismiss = { panel = V6Panel.NONE },
                onTool = { selected = null; movingSelected = false; preview = null; tool = it; panel = V6Panel.NONE },
                onColor = { colorArgb = it },
                onStroke = { strokeWidth = it },
                onOpacity = { opacity = it },
                onUndo = ::undo,
                onRedo = ::redo
            )
            V6Panel.MARKS -> V6MarksPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                comments = comments,
                currentEmail = currentEmail,
                onSelect = { panel = V6Panel.NONE; page = it.pageIndex.coerceIn(0, pageCount - 1); selected = it; tool = AdaptiveViewerTool.SELECT },
                onPublishAll = { comments.filter { !it.published && it.authorEmail.equals(currentEmail, true) }.forEach(onPublishComment); panel = V6Panel.NONE },
                onDismiss = { panel = V6Panel.NONE }
            )
            V6Panel.HISTORY -> V6HistoryPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                timeline = timeline,
                onDismiss = { panel = V6Panel.NONE }
            )
            V6Panel.ACTIONS -> V6ActionsPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                canApprove = canApprove,
                canRequestChanges = document.status == "EN_REVISIÓN",
                onApprove = { panel = V6Panel.NONE; onApprove() },
                onRequestChanges = { panel = V6Panel.NONE; requestChangesOpen = true },
                onDismiss = { panel = V6Panel.NONE }
            )
            V6Panel.NONE -> Unit
        }
    }

    if (requestChangesOpen) {
        V6RequestChangesDialog(
            onDismiss = { requestChangesOpen = false },
            onConfirm = { requestChangesOpen = false; onRequestChanges(it) }
        )
    }
}

@Composable
private fun V6Canvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    scale: Float,
    pan: Offset,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    markups: List<AdaptiveMarkup>,
    preview: AdaptiveDrawingPreview?,
    editor: V6Editor?,
    selected: PlanComment?,
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    onViewport: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onTap: (ReviewPoint, Boolean) -> Unit,
    onPreview: (AdaptiveDrawingPreview?) -> Unit,
    onDrawingFinished: (AdaptiveDrawingPreview) -> Unit,
    onEditorChange: (V6Editor) -> Unit,
    onEditorSave: () -> Unit,
    onEditorCancel: () -> Unit
) {
    val image = bitmap
    val fit = if (image != null && viewport.width > 0 && viewport.height > 0) min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height) else 1f
    val pageWidthPx = image?.width?.times(fit) ?: 1f
    val pageHeightPx = image?.height?.times(fit) ?: 1f
    val density = LocalDensity.current
    val pageWidthDp = with(density) { pageWidthPx.toDp() }
    val pageHeightDp = with(density) { pageHeightPx.toDp() }
    val gestures = if (editor == null) {
        Modifier.v6Gestures(tool, editable, screenToNormalized, onTransform, onTap, onPreview, onDrawingFinished)
    } else Modifier

    Box(
        Modifier.fillMaxSize().clipToBounds().background(Color(0xFF202327)).onGloballyPositioned { onViewport(it.size) }.then(gestures),
        contentAlignment = Alignment.Center
    ) {
        if (image == null) {
            CircularProgressIndicator(color = SkmOrange)
        } else {
            Box(
                Modifier.size(pageWidthDp, pageHeightDp).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                    transformOrigin = TransformOrigin.Center
                }.background(Color.White)
            ) {
                androidx.compose.foundation.Image(image.asImageBitmap(), "Plano PDF", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()) {
                    markups.forEach { drawAdaptiveMarkup(it) }
                    preview?.let { drawAdaptivePreview(it, Color(colorArgb), strokeWidth, opacity) }
                    editor?.takeIf { it.type == ReviewMarkupType.DIMENSION }?.let {
                        drawAdaptivePreview(
                            AdaptiveDrawingPreview(
                                ReviewMarkupType.DIMENSION,
                                ReviewPoint(it.x, it.y),
                                ReviewPoint(it.endX, it.endY),
                                label = ReviewPoint(it.labelX, it.labelY)
                            ),
                            Color(colorArgb),
                            strokeWidth,
                            opacity
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    markups.forEach { mark ->
                        when (mark.type) {
                            ReviewMarkupType.TEXT -> V6Label(mark, maxWidth, maxHeight, selected?.id == mark.source.id, symbol = false, dimension = false)
                            ReviewMarkupType.SYMBOL -> V6Label(mark, maxWidth, maxHeight, selected?.id == mark.source.id, symbol = true, dimension = false)
                            ReviewMarkupType.DIMENSION -> V6Label(mark, maxWidth, maxHeight, selected?.id == mark.source.id, symbol = false, dimension = true)
                            else -> Unit
                        }
                    }
                    editor?.let { V6InlineEditor(it, maxWidth, maxHeight, onEditorChange, onEditorSave, onEditorCancel) }
                }
            }
        }
    }
}

private fun Modifier.v6Gestures(
    tool: AdaptiveViewerTool,
    editable: Boolean,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onTransform: (Offset, Offset, Float) -> Unit,
    onTap: (ReviewPoint, Boolean) -> Unit,
    onPreview: (AdaptiveDrawingPreview?) -> Unit,
    onDrawingFinished: (AdaptiveDrawingPreview) -> Unit
): Modifier = pointerInput(tool, editable) {
    var lastTapTime = 0L
    var lastTapPoint: ReviewPoint? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startPoint = screenToNormalized(down.position)
        var lastPixel = down.position
        var moved = false
        var transforming = false
        var currentPreview: AdaptiveDrawingPreview? = null
        val points = mutableListOf<ReviewPoint>().apply { startPoint?.let(::add) }

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (!transforming) {
                    transforming = true
                    currentPreview = null
                    onPreview(null)
                }
                onTransform(event.calculateCentroid(useCurrent = true), event.calculatePan(), event.calculateZoom())
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1 && !transforming) {
                val change = pressed.first()
                val delta = change.position - lastPixel
                if (delta.getDistance() > 1.2f) moved = true
                when {
                    tool == AdaptiveViewerTool.HAND -> {
                        onTransform(change.position, delta, 1f)
                        moved = true
                    }
                    editable && tool.editsDocument && tool != AdaptiveViewerTool.TEXT && tool != AdaptiveViewerTool.SYMBOL -> {
                        val point = screenToNormalized(change.position)
                        if (startPoint != null && point != null) {
                            if (tool == AdaptiveViewerTool.FREEHAND || tool == AdaptiveViewerTool.HIGHLIGHT) {
                                if (points.isEmpty() || adaptivePointDistance(points.last(), point) > 0.0015f) points += point
                            }
                            val label = ReviewPoint((startPoint.x + point.x) / 2f, (((startPoint.y + point.y) / 2f) - 0.045f).coerceIn(0.02f, 0.96f))
                            currentPreview = AdaptiveDrawingPreview(
                                type = requireNotNull(tool.markupType),
                                start = startPoint,
                                end = point,
                                points = if (tool == AdaptiveViewerTool.FREEHAND || tool == AdaptiveViewerTool.HIGHLIGHT) points.toList() else listOf(startPoint, point),
                                label = label
                            )
                            onPreview(currentPreview)
                        }
                    }
                }
                lastPixel = change.position
                change.consume()
            }

            if (event.changes.none { it.pressed }) {
                if (!transforming) {
                    if (moved && currentPreview != null) {
                        onDrawingFinished(requireNotNull(currentPreview))
                    } else if (!moved && startPoint != null) {
                        val releasedAt = event.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                        val previous = lastTapPoint
                        val doubleTap = previous != null && releasedAt - lastTapTime < 340L && adaptivePointDistance(previous, startPoint) < 0.035f
                        onTap(startPoint, doubleTap)
                        if (doubleTap) {
                            lastTapTime = 0L
                            lastTapPoint = null
                        } else {
                            lastTapTime = releasedAt
                            lastTapPoint = startPoint
                        }
                    }
                }
                onPreview(null)
                break
            }
        }
    }
}

@Composable
private fun BoxScope.V6Label(mark: AdaptiveMarkup, pageWidth: Dp, pageHeight: Dp, selected: Boolean, symbol: Boolean, dimension: Boolean) {
    val x = if (dimension) mark.labelX else mark.x
    val y = if (dimension) mark.labelY else mark.y
    val width = when {
        symbol -> (pageWidth * mark.width.coerceIn(0.05f, 0.14f)).coerceIn(30.dp, 74.dp)
        dimension -> (pageWidth * mark.width.coerceIn(0.12f, 0.30f)).coerceIn(60.dp, 180.dp)
        else -> (pageWidth * mark.width.coerceIn(0.16f, 0.60f)).coerceAtLeast(90.dp)
    }
    val container = if (symbol || dimension) Color.White.copy(alpha = 0.94f) else Color(mark.colorArgb).copy(alpha = mark.opacity.coerceIn(0.45f, 0.98f))
    val foreground = if (symbol || dimension) Color(mark.colorArgb) else Color.White
    Surface(
        modifier = Modifier.offset(pageWidth * x.coerceIn(0f, 0.95f), pageHeight * y.coerceIn(0f, 0.95f)).width(width),
        shape = RoundedCornerShape(if (symbol) 20.dp else 5.dp),
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkmOrange else if (symbol || dimension) Color(mark.colorArgb) else Color.Transparent),
        shadowElevation = if (selected) 5.dp else 0.dp
    ) {
        Text(
            mark.text.ifBlank { if (dimension) "COTA" else "" },
            Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            color = foreground,
            fontWeight = if (symbol || dimension) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (symbol) 18.sp else 10.sp,
            textAlign = if (symbol || dimension) TextAlign.Center else TextAlign.Start,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.V6InlineEditor(
    value: V6Editor,
    pageWidth: Dp,
    pageHeight: Dp,
    onChange: (V6Editor) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focus = remember(value.clientId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val pageWidthPx = with(density) { pageWidth.toPx() }
    val pageHeightPx = with(density) { pageHeight.toPx() }
    LaunchedEffect(value.clientId) { focus.requestFocus(); keyboard?.show() }
    val x = if (value.type == ReviewMarkupType.DIMENSION) value.labelX else value.x
    val y = if (value.type == ReviewMarkupType.DIMENSION) value.labelY else value.y
    val visibleWidth = if (value.type == ReviewMarkupType.DIMENSION) value.width.coerceIn(0.16f, 0.38f) else value.width.coerceIn(0.16f, 0.62f)

    Surface(
        modifier = Modifier.offset(pageWidth * x.coerceIn(0f, 0.92f), pageHeight * y.coerceIn(0f, 0.92f)).width((pageWidth * visibleWidth).coerceAtLeast(125.dp)),
        shape = RoundedCornerShape(7.dp),
        color = Color.White.copy(alpha = 0.98f),
        border = BorderStroke(2.dp, SkmOrange),
        shadowElevation = 9.dp
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.OpenWith,
                    "Mover cuadro",
                    modifier = Modifier.size(34.dp).padding(6.dp).pointerInput(value, pageWidthPx, pageHeightPx) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            val dx = amount.x / pageWidthPx.coerceAtLeast(1f)
                            val dy = amount.y / pageHeightPx.coerceAtLeast(1f)
                            onChange(
                                if (value.type == ReviewMarkupType.DIMENSION) value.copy(labelX = (value.labelX + dx).coerceIn(0f, 0.94f), labelY = (value.labelY + dy).coerceIn(0f, 0.94f))
                                else value.copy(x = (value.x + dx).coerceIn(0f, 0.94f), y = (value.y + dy).coerceIn(0f, 0.94f))
                            )
                        }
                    },
                    tint = SkmOrange
                )
                Text(
                    when (value.type) {
                        ReviewMarkupType.DIMENSION -> "Valor de cota"
                        ReviewMarkupType.SYMBOL -> "Símbolo técnico"
                        else -> "Texto en el plano"
                    },
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                IconButton(onClick = onSave, enabled = value.text.trim().isNotEmpty(), modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Save, "Guardar", tint = SkmSuccess) }
                IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Cancelar", tint = SkmDanger) }
            }
            BasicTextField(
                value = value.text,
                onValueChange = { onChange(value.copy(text = it.take(if (value.type == ReviewMarkupType.DIMENSION) 60 else 1200))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp, max = 150.dp).background(Color(0xFFF5F6F7), RoundedCornerShape(5.dp)).padding(7.dp).focusRequester(focus),
                textStyle = TextStyle(color = SkmGraphite, fontSize = if (value.type == ReviewMarkupType.SYMBOL) 18.sp else 12.sp, fontWeight = if (value.type == ReviewMarkupType.DIMENSION || value.type == ReviewMarkupType.SYMBOL) FontWeight.Bold else FontWeight.Normal),
                cursorBrush = SolidColor(SkmOrange),
                singleLine = value.type != ReviewMarkupType.TEXT,
                decorationBox = { field ->
                    if (value.text.isBlank()) Text(if (value.type == ReviewMarkupType.DIMENSION) "Ej.: 2216, Ø305, R35, 25 ±0,05" else "Escribe aquí…", color = Color.Gray, fontSize = 10.sp)
                    field()
                }
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                V6_SYMBOLS.forEach { token ->
                    Surface(onClick = { onChange(value.copy(text = value.text + token)) }, color = Color(0xFFEDEFF2), shape = RoundedCornerShape(5.dp)) {
                        Text(token, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ancho", fontSize = 9.sp)
                Slider(value = value.width, onValueChange = { onChange(value.copy(width = it)) }, valueRange = 0.16f..0.62f, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BoxScope.V6TopBar(
    document: DocumentRecord,
    page: Int,
    pageCount: Int,
    scale: Float,
    landscape: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onMarks: () -> Unit,
    onHistory: () -> Unit,
    onActions: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = if (landscape) 70.dp else 8.dp, vertical = 7.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 9.dp
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(43.dp)) { Icon(Icons.Default.ArrowBack, "Volver") }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Rev ${document.revision} · ${v6Status(document.status)}", color = Color.Gray, fontSize = 9.sp, maxLines = 1)
            }
            IconButton(onClick = onPrevious, enabled = page > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronLeft, "Anterior") }
            Text("${page + 1}/$pageCount", Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNext, enabled = page + 1 < pageCount, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronRight, "Siguiente") }
            if (landscape) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", Modifier.widthIn(min = 45.dp), textAlign = TextAlign.Center, fontSize = 10.sp)
                IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onFit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.FitScreen, "Ajustar") }
                IconButton(onClick = onMarks, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Layers, "Marcas") }
                IconButton(onClick = onHistory, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.History, "Historial") }
            }
            IconButton(onClick = onActions, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Acciones") }
            IconButton(onClick = onHide, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Fullscreen, "Ocultar") }
        }
    }
}

@Composable
private fun BoxScope.V6ToolRail(landscape: Boolean, tool: AdaptiveViewerTool, editable: Boolean, onTool: (AdaptiveViewerTool) -> Unit, onMore: () -> Unit) {
    val quick = listOf(AdaptiveViewerTool.HAND, AdaptiveViewerTool.SELECT, AdaptiveViewerTool.TEXT, AdaptiveViewerTool.SYMBOL, AdaptiveViewerTool.DIMENSION, AdaptiveViewerTool.LINE, AdaptiveViewerTool.ARROW)
    Surface(
        modifier = if (landscape) Modifier.align(Alignment.CenterStart).padding(start = 7.dp, top = 64.dp, bottom = 7.dp).width(58.dp).fillMaxHeight(0.76f)
        else Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 9.dp
    ) {
        if (landscape) {
            LazyColumn(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(quick) { item -> V6ToolButton(item, tool == item, !item.editsDocument || editable) { onTool(item) } }
                item { V6IconButton(Icons.Default.MoreHoriz, "Más", true, false, onMore) }
            }
        } else {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                items(quick) { item -> V6ToolButton(item, tool == item, !item.editsDocument || editable) { onTool(item) } }
                item { V6IconButton(Icons.Default.MoreHoriz, "Más", true, false, onMore) }
            }
        }
    }
}

@Composable
private fun V6ToolButton(item: AdaptiveViewerTool, selected: Boolean, enabled: Boolean, onClick: () -> Unit) =
    V6IconButton(v6Icon(item), item.label, enabled, selected, onClick)

@Composable
private fun V6IconButton(icon: ImageVector, label: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(54.dp).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(41.dp).background(if (selected) SkmOrange else Color.Transparent, CircleShape)) {
            Icon(icon, label, tint = if (selected) Color.White else if (enabled) SkmGraphite else Color.Gray.copy(alpha = 0.42f))
        }
        Text(label, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled) SkmGraphite else Color.Gray.copy(alpha = 0.45f))
    }
}

@Composable
private fun V6ToolsPanel(
    modifier: Modifier,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onDismiss: () -> Unit,
    onTool: (AdaptiveViewerTool) -> Unit,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Surface(modifier = modifier.padding(10.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Herramientas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            Text("Dos dedos siempre navegan. Un dedo aplica la herramienta seleccionada.", color = Color.Gray, fontSize = 11.sp)
            AdaptiveViewerTool.entries.forEach { item ->
                val enabled = !item.editsDocument || editable
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) { onTool(item) }.alpha(if (enabled) 1f else 0.42f).padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(v6Icon(item), null, tint = if (tool == item) SkmOrange else SkmGraphite)
                    Text(item.label, Modifier.weight(1f).padding(start = 12.dp), fontWeight = if (tool == item) FontWeight.Bold else FontWeight.Normal)
                    if (tool == item) Icon(Icons.Default.Check, null, tint = SkmOrange)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row {
                OutlinedButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Undo, null); Text("Deshacer") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRedo, enabled = redoEnabled, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Redo, null); Text("Rehacer") }
            }
            Text("Color", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0xFFE53935.toInt(), 0xFFFF6A00.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF111827.toInt()).forEach { value ->
                    Surface(onClick = { onColor(value) }, modifier = Modifier.size(36.dp).border(if (value == colorArgb) 3.dp else 1.dp, if (value == colorArgb) SkmOrange else Color.LightGray, CircleShape), color = Color(value), shape = CircleShape) {}
                }
            }
            Text("Espesor", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Slider(strokeWidth, onStroke, valueRange = 0.002f..0.025f)
            Text("Opacidad", fontWeight = FontWeight.Bold)
            Slider(opacity, onOpacity, valueRange = 0.15f..1f)
        }
    }
}

@Composable
private fun BoxScope.V6SelectionBar(
    modifier: Modifier,
    comment: PlanComment,
    canModify: Boolean,
    canPublish: Boolean,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = modifier, color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(18.dp), shadowElevation = 9.dp) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(max = 170.dp)) {
                Text(comment.displayLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(comment.authorName.ifBlank { comment.authorEmail }, color = Color.Gray, fontSize = 9.sp, maxLines = 1)
            }
            if (canModify && comment.markupType in listOf(ReviewMarkupType.TEXT, ReviewMarkupType.SYMBOL, ReviewMarkupType.DIMENSION)) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            if (canModify) IconButton(onClick = onMove) { Icon(Icons.Default.OpenWith, "Mover") }
            if (canPublish) IconButton(onClick = onPublish) { Icon(Icons.Default.Publish, "Publicar", tint = SkmSuccess) }
            if (canModify) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = SkmDanger) }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
        }
    }
}

@Composable
private fun V6MarksPanel(modifier: Modifier, comments: List<PlanComment>, currentEmail: String, onSelect: (PlanComment) -> Unit, onPublishAll: () -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = modifier.padding(10.dp).fillMaxWidth().heightIn(max = 520.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marcas y observaciones", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            TextButton(onClick = onPublishAll, enabled = comments.any { !it.published && it.authorEmail.equals(currentEmail, true) }) { Text("Publicar mis borradores") }
            LazyColumn {
                items(comments.sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })) { comment ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(comment) }) {
                        Column(Modifier.padding(9.dp)) {
                            Text("Hoja ${comment.pageIndex + 1} · ${comment.displayLabel}", fontWeight = FontWeight.Bold)
                            Text(comment.authorName.ifBlank { comment.authorEmail }, color = Color.Gray, fontSize = 9.sp)
                            Text(if (comment.published) "Publicada" else "Borrador privado", color = if (comment.published) SkmSuccess else SkmOrange, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V6HistoryPanel(modifier: Modifier, timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Surface(modifier = modifier.padding(10.dp).fillMaxWidth().heightIn(max = 520.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Historial", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            LazyColumn {
                items(timeline.sortedByDescending { it.createdAt }) { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(event.type.name.replace('_', ' '), fontWeight = FontWeight.Bold)
                        Text(event.actorName.ifBlank { event.actorEmail }, color = Color.Gray, fontSize = 9.sp)
                        if (event.detail.isNotBlank()) Text(event.detail, fontSize = 11.sp)
                        Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(event.createdAt)), color = Color.Gray, fontSize = 9.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun V6ActionsPanel(modifier: Modifier, canApprove: Boolean, canRequestChanges: Boolean, onApprove: () -> Unit, onRequestChanges: () -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = modifier.padding(10.dp).widthIn(max = 430.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Acciones de revisión", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            Button(onClick = onApprove, enabled = canApprove, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(7.dp)); Text("Aprobar y firmar") }
            OutlinedButton(onClick = onRequestChanges, enabled = canRequestChanges, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Icon(Icons.Default.Comment, null); Spacer(Modifier.width(7.dp)); Text("Solicitar cambios") }
            Text("La última firma genera automáticamente el PDF final con sello azul APTO PARA FABRICACIÓN.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun V6RequestChangesDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar cambios") },
        text = { OutlinedTextField(text, { text = it.take(1200) }, label = { Text("Motivo obligatorio") }, minLines = 4) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.trim().length >= 5) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun ReviewMarkupInput.toV6Markup(clientId: String): AdaptiveMarkup = AdaptiveMarkup(
    source = PlanComment(pageIndex = pageIndex),
    clientId = clientId,
    type = type,
    text = text,
    x = x,
    y = y,
    endX = endX,
    endY = endY,
    width = width,
    height = height,
    labelX = labelX,
    labelY = labelY,
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    opacity = opacity,
    points = points
)

private fun v6Icon(tool: AdaptiveViewerTool): ImageVector = when (tool) {
    AdaptiveViewerTool.HAND -> Icons.Default.PanTool
    AdaptiveViewerTool.SELECT -> Icons.Default.TouchApp
    AdaptiveViewerTool.TEXT -> Icons.Default.TextFields
    AdaptiveViewerTool.SYMBOL -> Icons.Default.Functions
    AdaptiveViewerTool.DIMENSION -> Icons.Default.Straighten
    AdaptiveViewerTool.FREEHAND -> Icons.Default.Edit
    AdaptiveViewerTool.HIGHLIGHT -> Icons.Default.Brush
    AdaptiveViewerTool.LINE -> Icons.Default.Remove
    AdaptiveViewerTool.ARROW -> Icons.Default.ArrowForward
    AdaptiveViewerTool.RECTANGLE -> Icons.Default.CropSquare
    AdaptiveViewerTool.ELLIPSE -> Icons.Default.Circle
    AdaptiveViewerTool.CLOUD -> Icons.Default.Cloud
}

private fun v6Status(status: String): String = when (status) {
    "EN_REVISIÓN" -> "En revisión"
    "CAMBIOS_SOLICITADOS" -> "Cambios solicitados"
    "APTO_PARA_FABRICACIÓN" -> "Apto para fabricación"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private tailrec fun Context.findV6Activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findV6Activity()
    else -> null
}
