package com.example.document.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class V7Panel { NONE, TOOLS, MARKS, HISTORY, ACTIONS }

private data class V7Editor(
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

private data class V7Action(val clientId: String, val input: ReviewMarkupInput)

private data class V7Confetti(
    val side: Int,
    val lane: Float,
    val delay: Float,
    val travel: Float,
    val spin: Float,
    val size: Float,
    val color: Color
)

private val V7_SYMBOLS = listOf("Ø", "R", "±", "°", "M", "N6", "N8", "H7", "H8", "△", "○", "×", "✓")
private val V7_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFFFF6A00),
    Color(0xFF0077CC),
    Color(0xFF2E7D32),
    Color(0xFF111827)
)

@Composable
fun CorporatePdfViewerV7(
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
    var panel by rememberSaveable { mutableStateOf(V7Panel.NONE) }
    var editor by remember { mutableStateOf<V7Editor?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var movingSelected by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AdaptiveDrawingPreview?>(null) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFE53935.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.95f) }
    var requestChangesOpen by rememberSaveable { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    val pending = remember { mutableStateListOf<V7Action>() }
    var undoStack by remember { mutableStateOf<List<V7Action>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<V7Action>>(emptyList()) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findV7Activity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
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
    val pendingMarkups = pending.filter { it.input.pageIndex == page }.map { it.input.toV7Markup(it.clientId) }
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
        val action = V7Action(clientId, input)
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

    fun saveEditor(value: V7Editor) {
        val cleanText = value.text.trim()
        if (cleanText.isBlank()) {
            editor = null
            tool = AdaptiveViewerTool.HAND
            return
        }
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
        editor = V7Editor(
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

    fun finishDrawing(drawing: AdaptiveDrawingPreview) {
        if (adaptivePointDistance(drawing.start, drawing.end) < 0.008f) {
            preview = null
            tool = AdaptiveViewerTool.HAND
            return
        }
        if (drawing.type == ReviewMarkupType.DIMENSION) {
            editor = V7Editor(
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

    BackHandler {
        when {
            celebrating -> Unit
            editor != null -> editor?.let(::saveEditor)
            selected != null -> selected = null
            panel != V7Panel.NONE -> panel = V7Panel.NONE
            else -> onClose()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF202327))) {
        val landscape = maxWidth > maxHeight

        V7Canvas(
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
                        if (scale > 1.05f) {
                            scale = 1f
                            pan = Offset.Zero
                        } else {
                            val (w, h) = fittedPageSize()
                            val screen = Offset((viewport.width - w) / 2f + point.x * w, (viewport.height - h) / 2f + point.y * h)
                            applyTransform(screen, Offset.Zero, 2.5f)
                        }
                    }
                    movingSelected && selected != null -> moveMark(requireNotNull(selected), point)
                    tool == AdaptiveViewerTool.TEXT && editable -> {
                        editor = V7Editor(
                            pageIndex = page,
                            type = ReviewMarkupType.TEXT,
                            text = "",
                            x = point.x,
                            y = point.y,
                            endX = (point.x + 0.32f).coerceAtMost(1f),
                            endY = (point.y + 0.10f).coerceAtMost(1f),
                            width = 0.32f,
                            height = 0.10f,
                            labelX = point.x,
                            labelY = point.y
                        )
                        tool = AdaptiveViewerTool.HAND
                    }
                    tool == AdaptiveViewerTool.SYMBOL && editable -> {
                        editor = V7Editor(
                            pageIndex = page,
                            type = ReviewMarkupType.SYMBOL,
                            text = "Ø",
                            x = point.x,
                            y = point.y,
                            endX = (point.x + 0.10f).coerceAtMost(1f),
                            endY = (point.y + 0.08f).coerceAtMost(1f),
                            width = 0.10f,
                            height = 0.08f,
                            labelX = point.x,
                            labelY = point.y
                        )
                        tool = AdaptiveViewerTool.HAND
                    }
                    tool == AdaptiveViewerTool.SELECT -> selected = adaptiveHitTest(pageMarkups, point)
                }
            },
            onPreview = { preview = it },
            onDrawingFinished = ::finishDrawing,
            onEditorChange = { editor = it },
            onEditorSave = { editor?.let(::saveEditor) }
        )

        if (controlsVisible && !celebrating) {
            V7TopBar(
                document = document,
                page = page,
                pageCount = pageCount,
                scale = scale,
                landscape = landscape,
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
                onMarks = { panel = V7Panel.MARKS },
                onHistory = { panel = V7Panel.HISTORY },
                onActions = { panel = V7Panel.ACTIONS },
                onHide = { controlsVisible = false }
            )
            V7ToolRail(
                landscape = landscape,
                tool = tool,
                editable = editable && editor == null,
                onTool = { selected = null; movingSelected = false; preview = null; tool = it },
                onMore = { panel = V7Panel.TOOLS }
            )
        } else if (!celebrating) {
            FloatingActionButton(
                onClick = { controlsVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                containerColor = SkmOrange,
                contentColor = Color.White
            ) { Icon(Icons.Default.Edit, "Mostrar controles") }
        }

        selected?.takeIf { !celebrating }?.let { comment ->
            V7SelectionBar(
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
            V7Panel.TOOLS -> V7ToolsPanel(
                modifier = if (landscape) Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 370.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 540.dp),
                tool = tool,
                editable = editable && editor == null,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
                opacity = opacity,
                undoEnabled = undoStack.isNotEmpty(),
                redoEnabled = redoStack.isNotEmpty(),
                onDismiss = { panel = V7Panel.NONE },
                onTool = { selected = null; movingSelected = false; preview = null; tool = it; panel = V7Panel.NONE },
                onColor = { colorArgb = it },
                onStroke = { strokeWidth = it },
                onOpacity = { opacity = it },
                onUndo = ::undo,
                onRedo = ::redo
            )
            V7Panel.MARKS -> V7MarksPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                comments = comments,
                currentEmail = currentEmail,
                onSelect = { panel = V7Panel.NONE; page = it.pageIndex.coerceIn(0, pageCount - 1); selected = it; tool = AdaptiveViewerTool.SELECT },
                onPublishAll = { comments.filter { !it.published && it.authorEmail.equals(currentEmail, true) }.forEach(onPublishComment); panel = V7Panel.NONE },
                onDismiss = { panel = V7Panel.NONE }
            )
            V7Panel.HISTORY -> V7HistoryPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                timeline = timeline,
                onDismiss = { panel = V7Panel.NONE }
            )
            V7Panel.ACTIONS -> V7ActionsPanel(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
                canApprove = canApprove && !celebrating,
                canRequestChanges = document.status == "EN_REVISIÓN" && !celebrating,
                onApprove = { panel = V7Panel.NONE; onApprove() },
                onRequestChanges = { panel = V7Panel.NONE; requestChangesOpen = true },
                onDismiss = { panel = V7Panel.NONE }
            )
            V7Panel.NONE -> Unit
        }

        if (celebrating) {
            V7ApprovalCelebration(
                document = document,
                onFinished = {
                    celebrating = false
                    onApprove()
                }
            )
        }
    }

    if (requestChangesOpen) {
        V7RequestChangesDialog(
            onDismiss = { requestChangesOpen = false },
            onConfirm = { requestChangesOpen = false; onRequestChanges(it) }
        )
    }
}

@Composable
private fun V7Canvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    scale: Float,
    pan: Offset,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    markups: List<AdaptiveMarkup>,
    preview: AdaptiveDrawingPreview?,
    editor: V7Editor?,
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
    onEditorChange: (V7Editor) -> Unit,
    onEditorSave: () -> Unit
) {
    val image = bitmap
    val fit = if (image != null && viewport.width > 0 && viewport.height > 0) {
        min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    } else 1f
    val pageWidthPx = image?.width?.times(fit) ?: 1f
    val pageHeightPx = image?.height?.times(fit) ?: 1f
    val density = LocalDensity.current
    val pageWidthDp = with(density) { pageWidthPx.toDp() }
    val pageHeightDp = with(density) { pageHeightPx.toDp() }
    val gestures = if (editor == null) {
        Modifier.v7Gestures(tool, editable, screenToNormalized, onTransform, onTap, onPreview, onDrawingFinished)
    } else Modifier

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
                    image.asImageBitmap(),
                    "Plano PDF",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
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
                            ReviewMarkupType.TEXT -> V7TextLabel(mark, maxWidth, maxHeight, selected?.id == mark.source.id)
                            ReviewMarkupType.SYMBOL -> V7CompactLabel(mark, maxWidth, maxHeight, selected?.id == mark.source.id, symbol = true)
                            ReviewMarkupType.DIMENSION -> V7CompactLabel(mark, maxWidth, maxHeight, selected?.id == mark.source.id, symbol = false)
                            else -> Unit
                        }
                    }
                    editor?.let { V7TransparentEditor(it, maxWidth, maxHeight, onEditorChange, onEditorSave) }
                }
            }
        }
    }
}

private fun Modifier.v7Gestures(
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
            val event = awaitPointerEvent(PointerEventPass.Main)
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
                            val label = ReviewPoint(
                                (startPoint.x + point.x) / 2f,
                                (((startPoint.y + point.y) / 2f) - 0.045f).coerceIn(0.02f, 0.96f)
                            )
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
private fun BoxScope.V7TextLabel(mark: AdaptiveMarkup, pageWidth: Dp, pageHeight: Dp, selected: Boolean) {
    Box(
        modifier = Modifier
            .offset(pageWidth * mark.x.coerceIn(0f, 0.96f), pageHeight * mark.y.coerceIn(0f, 0.96f))
            .width((pageWidth * mark.width.coerceIn(0.10f, 0.70f)).coerceAtLeast(60.dp))
            .then(
                if (selected) Modifier.background(Color(0xFFFFF3E8).copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                else Modifier
            )
    ) {
        Text(
            text = mark.text,
            color = Color(mark.colorArgb).copy(alpha = mark.opacity.coerceIn(0.20f, 1f)),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BoxScope.V7CompactLabel(mark: AdaptiveMarkup, pageWidth: Dp, pageHeight: Dp, selected: Boolean, symbol: Boolean) {
    val x = if (mark.type == ReviewMarkupType.DIMENSION) mark.labelX else mark.x
    val y = if (mark.type == ReviewMarkupType.DIMENSION) mark.labelY else mark.y
    val width = if (symbol) {
        (pageWidth * mark.width.coerceIn(0.05f, 0.14f)).coerceIn(30.dp, 74.dp)
    } else {
        (pageWidth * mark.width.coerceIn(0.12f, 0.30f)).coerceIn(60.dp, 180.dp)
    }
    Surface(
        modifier = Modifier.offset(pageWidth * x.coerceIn(0f, 0.95f), pageHeight * y.coerceIn(0f, 0.95f)).width(width),
        shape = RoundedCornerShape(if (symbol) 20.dp else 4.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkmOrange else Color(mark.colorArgb))
    ) {
        Text(
            mark.text.ifBlank { if (symbol) "Ø" else "COTA" },
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color(mark.colorArgb),
            fontWeight = FontWeight.Bold,
            fontSize = if (symbol) 17.sp else 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun BoxScope.V7TransparentEditor(
    value: V7Editor,
    pageWidth: Dp,
    pageHeight: Dp,
    onChange: (V7Editor) -> Unit,
    onSave: () -> Unit
) {
    val focus = remember(value.clientId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val pageWidthPx = with(density) { pageWidth.toPx() }
    val pageHeightPx = with(density) { pageHeight.toPx() }
    val x = if (value.type == ReviewMarkupType.DIMENSION) value.labelX else value.x
    val y = if (value.type == ReviewMarkupType.DIMENSION) value.labelY else value.y
    val visibleWidth = if (value.type == ReviewMarkupType.DIMENSION) value.width.coerceIn(0.16f, 0.42f) else value.width.coerceIn(0.12f, 0.70f)

    LaunchedEffect(value.clientId) {
        focus.requestFocus()
        delay(90)
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .offset(pageWidth * x.coerceIn(0f, 0.94f), pageHeight * y.coerceIn(0f, 0.94f))
            .width((pageWidth * visibleWidth).coerceAtLeast(82.dp))
    ) {
        Icon(
            imageVector = Icons.Default.OpenWith,
            contentDescription = "Mover texto",
            tint = SkmOrange.copy(alpha = 0.82f),
            modifier = Modifier
                .offset((-13).dp, (-13).dp)
                .size(22.dp)
                .background(Color.White.copy(alpha = 0.72f), CircleShape)
                .padding(3.dp)
                .pointerInput(value, pageWidthPx, pageHeightPx) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val dx = amount.x / pageWidthPx.coerceAtLeast(1f)
                        val dy = amount.y / pageHeightPx.coerceAtLeast(1f)
                        onChange(
                            if (value.type == ReviewMarkupType.DIMENSION) {
                                value.copy(
                                    labelX = (value.labelX + dx).coerceIn(0f, 0.94f),
                                    labelY = (value.labelY + dy).coerceIn(0f, 0.94f)
                                )
                            } else {
                                value.copy(
                                    x = (value.x + dx).coerceIn(0f, 0.94f),
                                    y = (value.y + dy).coerceIn(0f, 0.94f),
                                    endX = (value.endX + dx).coerceIn(0f, 1f),
                                    endY = (value.endY + dy).coerceIn(0f, 1f)
                                )
                            }
                        )
                    }
                }
        )

        BasicTextField(
            value = value.text,
            onValueChange = { onChange(value.copy(text = it.take(if (value.type == ReviewMarkupType.DIMENSION) 60 else 1200))) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (value.type == ReviewMarkupType.TEXT) 30.dp else 24.dp, max = 170.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    if (it.key == Key.Enter && value.type != ReviewMarkupType.TEXT) {
                        onSave()
                        true
                    } else false
                },
            textStyle = TextStyle(
                color = Color(0xFFB71C1C),
                fontSize = if (value.type == ReviewMarkupType.SYMBOL) 18.sp else 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (value.type == ReviewMarkupType.DIMENSION || value.type == ReviewMarkupType.SYMBOL) FontWeight.Bold else FontWeight.Normal
            ),
            cursorBrush = SolidColor(SkmOrange),
            singleLine = value.type != ReviewMarkupType.TEXT,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            decorationBox = { field ->
                Box {
                    if (value.text.isBlank()) {
                        Text(
                            if (value.type == ReviewMarkupType.DIMENSION) "2216 / Ø305 / R35" else "Escribe sobre el plano…",
                            color = Color.Gray.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                    }
                    field()
                }
            }
        )
    }
}

@Composable
private fun BoxScope.V7TopBar(
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
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = if (landscape) 70.dp else 7.dp, vertical = 7.dp).fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 9.dp
    ) {
        Row(Modifier.heightIn(min = 50.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.ArrowBack, "Volver") }
            Column(Modifier.weight(1f)) {
                Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Rev ${document.revision} · ${v7Status(document.status)}", fontSize = 9.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = onPrevious, enabled = page > 0, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.ChevronLeft, "Anterior") }
            Text("${page + 1}/$pageCount", Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNext, enabled = page + 1 < pageCount, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.ChevronRight, "Siguiente") }
            if (landscape) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", Modifier.widthIn(min = 42.dp), textAlign = TextAlign.Center, fontSize = 10.sp)
                IconButton(onClick = onZoomIn, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onFit, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.FitScreen, "Ajustar") }
                IconButton(onClick = onMarks, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Layers, "Marcas") }
                IconButton(onClick = onHistory, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.History, "Historial") }
            }
            IconButton(onClick = onActions, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.MoreHoriz, "Acciones") }
            IconButton(onClick = onHide, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Edit, "Ocultar controles") }
        }
    }
}

@Composable
private fun BoxScope.V7ToolRail(
    landscape: Boolean,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    onTool: (AdaptiveViewerTool) -> Unit,
    onMore: () -> Unit
) {
    val quick = listOf(AdaptiveViewerTool.HAND, AdaptiveViewerTool.SELECT, AdaptiveViewerTool.TEXT, AdaptiveViewerTool.FREEHAND, AdaptiveViewerTool.LINE)
    Surface(
        modifier = if (landscape) Modifier.align(Alignment.CenterStart).padding(start = 8.dp).width(56.dp) else Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
        color = Color.White.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 9.dp
    ) {
        if (landscape) {
            Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                quick.forEach { V7ToolButton(it, tool == it, editable || !it.editsDocument, onTool) }
                IconButton(onClick = onMore) { Icon(Icons.Default.MoreHoriz, "Más herramientas") }
            }
        } else {
            Row(Modifier.padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                quick.forEach { V7ToolButton(it, tool == it, editable || !it.editsDocument, onTool) }
                IconButton(onClick = onMore) { Icon(Icons.Default.MoreHoriz, "Más herramientas") }
            }
        }
    }
}

@Composable
private fun V7ToolButton(tool: AdaptiveViewerTool, selected: Boolean, enabled: Boolean, onTool: (AdaptiveViewerTool) -> Unit) {
    IconButton(onClick = { onTool(tool) }, enabled = enabled, modifier = Modifier.size(44.dp).alpha(if (enabled) 1f else 0.35f)) {
        Icon(v7Icon(tool), tool.label, tint = if (selected) SkmOrange else SkmGraphite)
    }
}

@Composable
private fun V7ToolsPanel(
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
    val tools = AdaptiveViewerTool.entries
    Surface(modifier = modifier.padding(10.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Herramientas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Cerrar") }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(tools.chunked(3)) { rowTools ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTools.forEach { item ->
                            val enabled = editable || !item.editsDocument
                            Surface(
                                modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled) { onTool(item) },
                                color = if (tool == item) Color(0xFFFFE8D5) else Color(0xFFF3F4F6),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(v7Icon(item), item.label, tint = if (tool == item) SkmOrange else SkmGraphite)
                                    Text(item.label, fontSize = 9.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        repeat(3 - rowTools.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.size(6.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Color", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                V7_COLORS.forEach { color ->
                    Surface(
                        modifier = Modifier.size(31.dp).clickable { onColor(color.value.toLong().toInt()) },
                        color = color,
                        shape = CircleShape,
                        border = if (color.value.toLong().toInt() == colorArgb) BorderStroke(3.dp, SkmOrange) else null
                    ) {}
                }
            }
            Text("Espesor", fontSize = 10.sp)
            Slider(value = strokeWidth, onValueChange = onStroke, valueRange = 0.001f..0.025f)
            Text("Opacidad", fontSize = 10.sp)
            Slider(value = opacity, onValueChange = onOpacity, valueRange = 0.12f..1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onUndo, enabled = undoEnabled) { Icon(Icons.Default.Undo, null); Text("Deshacer") }
                TextButton(onClick = onRedo, enabled = redoEnabled) { Icon(Icons.Default.Redo, null); Text("Rehacer") }
            }
        }
    }
}

@Composable
private fun BoxScope.V7SelectionBar(
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
            if (canModify && comment.markupType in listOf(ReviewMarkupType.TEXT, ReviewMarkupType.SYMBOL, ReviewMarkupType.DIMENSION)) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            }
            if (canModify) IconButton(onClick = onMove) { Icon(Icons.Default.OpenWith, "Mover") }
            if (canPublish) IconButton(onClick = onPublish) { Icon(Icons.Default.Publish, "Publicar", tint = SkmSuccess) }
            if (canModify) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = SkmDanger) }
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Cerrar") }
        }
    }
}

@Composable
private fun V7MarksPanel(
    modifier: Modifier,
    comments: List<PlanComment>,
    currentEmail: String,
    onSelect: (PlanComment) -> Unit,
    onPublishAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(modifier = modifier.padding(10.dp).fillMaxWidth().heightIn(max = 520.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marcas y observaciones", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Cerrar") }
            }
            TextButton(onClick = onPublishAll, enabled = comments.any { !it.published && it.authorEmail.equals(currentEmail, true) }) {
                Text("Publicar mis borradores")
            }
            LazyColumn {
                items(comments.sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })) { comment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(comment) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA))
                    ) {
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
private fun V7HistoryPanel(modifier: Modifier, timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Surface(modifier = modifier.padding(10.dp).fillMaxWidth().heightIn(max = 520.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Historial", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Cerrar") }
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
private fun V7ActionsPanel(
    modifier: Modifier,
    canApprove: Boolean,
    canRequestChanges: Boolean,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(modifier = modifier.padding(10.dp).widthIn(max = 430.dp), color = Color.White.copy(alpha = 0.98f), shape = RoundedCornerShape(20.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Acciones de revisión", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Cerrar") }
            }
            Button(
                onClick = onApprove,
                enabled = canApprove,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkmSuccess)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(7.dp))
                Text("Aprobar y firmar")
            }
            OutlinedButton(onClick = onRequestChanges, enabled = canRequestChanges, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                Icon(Icons.Default.Comment, null)
                Spacer(Modifier.width(7.dp))
                Text("Solicitar cambios")
            }
            Text("Primero ubica tu firma y confirma con biometría. La celebración aparece solo después de guardar la aprobación.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun BoxScope.V7ApprovalCelebration(document: DocumentRecord, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val particles = remember {
        List(34) { index ->
            val side = if (index % 2 == 0) -1 else 1
            V7Confetti(
                side = side,
                lane = ((index * 37) % 100) / 100f,
                delay = ((index * 13) % 28) / 100f,
                travel = 0.34f + ((index * 17) % 22) / 100f,
                spin = 90f + ((index * 31) % 240),
                size = 5f + ((index * 11) % 8),
                color = V7_COLORS[index % V7_COLORS.size]
            )
        }
    }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1550, easing = FastOutSlowInEasing))
        delay(120)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            particles.forEach { particle ->
                val local = ((p - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (local > 0f && local < 1f) {
                    val startX = if (particle.side < 0) size.width * 0.015f else size.width * 0.985f
                    val direction = if (particle.side < 0) 1f else -1f
                    val x = startX + direction * size.width * particle.travel * local
                    val baseY = size.height * (0.18f + particle.lane * 0.58f)
                    val y = baseY + sin((local * PI * 2.2 + particle.lane * 5.0)).toFloat() * size.height * 0.055f + local * size.height * 0.13f
                    val alpha = (1f - local).coerceIn(0f, 1f)
                    withTransform({ rotate(particle.spin * local * direction, pivot = Offset(x, y)) }) {
                        drawRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(x - particle.size / 2f, y - particle.size / 2f),
                            size = androidx.compose.ui.geometry.Size(particle.size, particle.size * 1.65f)
                        )
                    }
                }
            }
        }

        val p = progress.value
        val cardAlpha = (p / 0.24f).coerceIn(0f, 1f) * ((1f - p) / 0.12f).coerceIn(0f, 1f)
        val cardScale = 0.82f + 0.18f * (p / 0.25f).coerceIn(0f, 1f)
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                },
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = SkmSuccess, modifier = Modifier.size(58.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "Aprobando", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                Text("Aprobando plano", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("${document.code} · Rev ${document.revision}", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun V7RequestChangesDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar cambios") },
        text = { OutlinedTextField(text, { text = it.take(1200) }, label = { Text("Motivo obligatorio") }, minLines = 4) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.trim().length >= 5) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun ReviewMarkupInput.toV7Markup(clientId: String): AdaptiveMarkup = AdaptiveMarkup(
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

private fun v7Icon(tool: AdaptiveViewerTool): ImageVector = when (tool) {
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

private fun v7Status(status: String): String = when (status) {
    "EN_REVISIÓN" -> "En revisión"
    "CAMBIOS_SOLICITADOS" -> "Cambios solicitados"
    "APTO_PARA_FABRICACIÓN" -> "Apto para fabricación"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private tailrec fun Context.findV7Activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findV7Activity()
    else -> null
}
