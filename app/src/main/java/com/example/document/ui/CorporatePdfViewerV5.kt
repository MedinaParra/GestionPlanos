package com.example.document.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class DirectInlineEditor(
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

private val directSymbols = listOf("△", "○", "×", "✓", "Ø", "R", "±", "↗")

@Composable
fun CorporatePdfViewerV5(
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
    var paletteVisible by rememberSaveable { mutableStateOf(false) }
    var symbolsVisible by rememberSaveable { mutableStateOf(false) }
    var selectedSymbol by rememberSaveable { mutableStateOf("△") }
    var editor by remember { mutableStateOf<DirectInlineEditor?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var moveSelected by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<AdaptiveDrawingPreview?>(null) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFE53935.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.95f) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showMarks by rememberSaveable { mutableStateOf(false) }
    var requestChangesText by rememberSaveable { mutableStateOf("") }
    var requestChangesVisible by rememberSaveable { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.directViewerActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler {
        when {
            editor != null -> editor = null
            symbolsVisible -> symbolsVisible = false
            paletteVisible -> paletteVisible = false
            showHistory -> showHistory = false
            showMarks -> showMarks = false
            selected != null -> selected = null
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
        moveSelected = false
        preview = null
    }

    val pageMarkups = remember(comments, page) {
        comments.filter { it.pageIndex == page }.map(::decodeAdaptiveMarkup)
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

    fun addInput(input: ReviewMarkupInput, clientId: String = UUID.randomUUID().toString()) {
        onAddComment(input.pageIndex, encodeAdaptiveMarkup(input, clientId), input.x, input.y, input.width)
    }

    fun updateInput(source: PlanComment, input: ReviewMarkupInput, clientId: String) {
        onUpdateComment(
            source.copy(
                text = encodeAdaptiveMarkup(input, clientId),
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

    fun saveEditor(value: DirectInlineEditor) {
        val clean = value.text.trim()
        if (clean.isBlank()) return
        val input = ReviewMarkupInput(
            pageIndex = value.pageIndex,
            type = value.type,
            text = clean,
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
        value.source?.let { updateInput(it, input, value.clientId) } ?: addInput(input, value.clientId)
        editor = null
        selected = null
        tool = AdaptiveViewerTool.HAND
    }

    fun openExistingEditor(comment: PlanComment) {
        val decoded = decodeAdaptiveMarkup(comment)
        editor = DirectInlineEditor(
            pageIndex = comment.pageIndex,
            type = decoded.type,
            text = decoded.text,
            x = decoded.x,
            y = decoded.y,
            endX = decoded.endX,
            endY = decoded.endY,
            width = decoded.width,
            height = decoded.height,
            labelX = decoded.labelX,
            labelY = decoded.labelY,
            source = comment,
            clientId = decoded.clientId
        )
        selected = null
        tool = AdaptiveViewerTool.HAND
    }

    fun moveExisting(comment: PlanComment, point: ReviewPoint) {
        val decoded = decodeAdaptiveMarkup(comment)
        val input = decoded.toAdaptiveInput()
        val updated = when (decoded.type) {
            ReviewMarkupType.DIMENSION -> input.copy(labelX = point.x, labelY = point.y)
            ReviewMarkupType.TEXT, ReviewMarkupType.SYMBOL -> input.copy(
                x = point.x,
                y = point.y,
                endX = (point.x + decoded.width).coerceAtMost(1f),
                endY = (point.y + decoded.height).coerceAtMost(1f)
            )
            else -> {
                val centerX = (decoded.x + decoded.endX) / 2f
                val centerY = (decoded.y + decoded.endY) / 2f
                val dx = point.x - centerX
                val dy = point.y - centerY
                input.copy(
                    x = (decoded.x + dx).coerceIn(0f, 1f),
                    y = (decoded.y + dy).coerceIn(0f, 1f),
                    endX = (decoded.endX + dx).coerceIn(0f, 1f),
                    endY = (decoded.endY + dy).coerceIn(0f, 1f),
                    labelX = (decoded.labelX + dx).coerceIn(0f, 1f),
                    labelY = (decoded.labelY + dy).coerceIn(0f, 1f),
                    points = decoded.points.map { ReviewPoint((it.x + dx).coerceIn(0f, 1f), (it.y + dy).coerceIn(0f, 1f)) }
                )
            }
        }
        updateInput(comment, updated, decoded.clientId)
        moveSelected = false
        selected = null
        tool = AdaptiveViewerTool.HAND
    }

    fun finishDrawing(drawing: AdaptiveDrawingPreview) {
        if (adaptivePointDistance(drawing.start, drawing.end) < 0.008f) {
            preview = null
            tool = AdaptiveViewerTool.HAND
            return
        }
        if (drawing.type == ReviewMarkupType.DIMENSION) {
            editor = DirectInlineEditor(
                pageIndex = page,
                type = ReviewMarkupType.DIMENSION,
                text = "",
                x = drawing.start.x,
                y = drawing.start.y,
                endX = drawing.end.x,
                endY = drawing.end.y,
                width = 0.18f,
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
        addInput(
            ReviewMarkupInput(
                pageIndex = page,
                type = drawing.type,
                x = minX,
                y = minY,
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

        DirectViewerCanvas(
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
            onViewport = { viewport = it; pan = clampPan(pan) },
            onTransform = ::applyTransform,
            onDoubleTap = { position ->
                if (scale > 1.05f) { scale = 1f; pan = Offset.Zero } else applyTransform(position, Offset.Zero, 2.4f)
            },
            screenToNormalized = ::screenToNormalized,
            onTapText = { point ->
                editor = DirectInlineEditor(page, ReviewMarkupType.TEXT, "", point.x, point.y, point.x + 0.30f, point.y + 0.10f, 0.30f, 0.10f, point.x, point.y)
                tool = AdaptiveViewerTool.HAND
            },
            onTapSymbol = { point ->
                addInput(
                    ReviewMarkupInput(
                        pageIndex = page,
                        type = ReviewMarkupType.SYMBOL,
                        text = selectedSymbol,
                        x = point.x,
                        y = point.y,
                        endX = (point.x + 0.07f).coerceAtMost(1f),
                        endY = (point.y + 0.07f).coerceAtMost(1f),
                        width = 0.07f,
                        height = 0.07f,
                        colorArgb = colorArgb,
                        opacity = opacity
                    )
                )
                tool = AdaptiveViewerTool.HAND
            },
            onSelect = { point ->
                if (moveSelected && selected != null) moveExisting(requireNotNull(selected), point)
                else selected = adaptiveHitTest(pageMarkups, point)
            },
            onPreview = { preview = it },
            onDrawingFinished = ::finishDrawing,
            onEditorChange = { editor = it },
            onEditorSave = { editor?.let(::saveEditor) },
            onEditorCancel = { editor = null; tool = AdaptiveViewerTool.HAND }
        )

        if (controlsVisible) {
            DirectTopBar(
                document = document,
                page = page,
                pageCount = pageCount,
                scale = scale,
                landscape = landscape,
                onClose = onClose,
                onPrevious = { if (page > 0) page-- },
                onNext = { if (page + 1 < pageCount) page++ },
                onZoomOut = { scale = (scale / 1.35f).coerceAtLeast(1f); pan = clampPan(pan) },
                onZoomIn = { scale = (scale * 1.35f).coerceAtMost(12f); pan = clampPan(pan) },
                onFit = { scale = 1f; pan = Offset.Zero },
                onMarks = { showMarks = !showMarks },
                onHistory = { showHistory = !showHistory },
                onHide = { controlsVisible = false }
            )

            if (landscape) {
                DirectLandscapeRail(
                    tool = tool,
                    editable = editable,
                    onTool = { selected = null; moveSelected = false; preview = null; tool = it },
                    onSymbols = { symbolsVisible = true },
                    onMore = { paletteVisible = !paletteVisible }
                )
            } else {
                DirectPortraitBar(
                    tool = tool,
                    editable = editable,
                    onTool = { selected = null; moveSelected = false; preview = null; tool = it },
                    onSymbols = { symbolsVisible = true },
                    onMore = { paletteVisible = !paletteVisible }
                )
            }
        } else {
            FloatingActionButton(
                onClick = { controlsVisible = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                containerColor = SkmOrange,
                contentColor = Color.White
            ) { Icon(Icons.Default.Fullscreen, "Mostrar controles") }
        }

        if (paletteVisible) {
            DirectPalette(
                landscape = landscape,
                editable = editable,
                tool = tool,
                colorArgb = colorArgb,
                strokeWidth = strokeWidth,
                opacity = opacity,
                canApprove = canApprove,
                canRequestChanges = document.status == "EN_REVISIÓN",
                onDismiss = { paletteVisible = false },
                onTool = { selected = null; moveSelected = false; preview = null; tool = it; paletteVisible = false },
                onSymbols = { symbolsVisible = true },
                onColor = { colorArgb = it },
                onStroke = { strokeWidth = it },
                onOpacity = { opacity = it },
                onApprove = { paletteVisible = false; onApprove() },
                onRequestChanges = { paletteVisible = false; requestChangesVisible = true }
            )
        }

        if (symbolsVisible) {
            DirectSymbolPicker(
                selected = selectedSymbol,
                onSelect = { selectedSymbol = it; symbolsVisible = false; tool = AdaptiveViewerTool.SYMBOL },
                onDismiss = { symbolsVisible = false }
            )
        }

        selected?.let { comment ->
            DirectSelectionBar(
                comment = comment,
                canModify = editable && comment.canBeModifiedBy(currentEmail, isAdmin),
                onEdit = { openExistingEditor(comment) },
                onMove = { moveSelected = true; tool = AdaptiveViewerTool.SELECT },
                onPublish = if (!comment.published && comment.authorEmail.equals(currentEmail, true)) ({ onPublishComment(comment); selected = null }) else null,
                onDelete = { onDeleteComment(comment); selected = null },
                onClose = { selected = null; moveSelected = false }
            )
        }

        if (showMarks) {
            DirectMarksPanel(
                comments = comments,
                currentEmail = currentEmail,
                onSelect = { page = it.pageIndex.coerceIn(0, pageCount - 1); selected = it; showMarks = false; tool = AdaptiveViewerTool.SELECT },
                onPublish = onPublishComment,
                onDismiss = { showMarks = false }
            )
        }

        if (showHistory) {
            DirectHistoryPanel(timeline, onDismiss = { showHistory = false })
        }

        if (requestChangesVisible) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 520.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Solicitar cambios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = requestChangesText,
                        onValueChange = { requestChangesText = it.take(1200) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp)).padding(12.dp),
                        textStyle = TextStyle(color = SkmGraphite, fontSize = 16.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { requestChangesVisible = false }) { Text("Cancelar") }
                        Button(
                            onClick = { if (requestChangesText.trim().length >= 5) { onRequestChanges(requestChangesText); requestChangesVisible = false } },
                            enabled = requestChangesText.trim().length >= 5,
                            colors = ButtonDefaults.buttonColors(containerColor = SkmDanger)
                        ) { Text("Enviar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectViewerCanvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    scale: Float,
    pan: Offset,
    tool: AdaptiveViewerTool,
    editable: Boolean,
    markups: List<AdaptiveMarkup>,
    preview: AdaptiveDrawingPreview?,
    editor: DirectInlineEditor?,
    selected: PlanComment?,
    onViewport: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onTapText: (ReviewPoint) -> Unit,
    onTapSymbol: (ReviewPoint) -> Unit,
    onSelect: (ReviewPoint) -> Unit,
    onPreview: (AdaptiveDrawingPreview?) -> Unit,
    onDrawingFinished: (AdaptiveDrawingPreview) -> Unit,
    onEditorChange: (DirectInlineEditor) -> Unit,
    onEditorSave: () -> Unit,
    onEditorCancel: () -> Unit
) {
    val image = bitmap
    val fit = if (image != null && viewport.width > 0 && viewport.height > 0) min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height) else 1f
    val pageWidthPx = image?.width?.times(fit) ?: 1f
    val pageHeightPx = image?.height?.times(fit) ?: 1f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pageWidthDp = with(density) { pageWidthPx.toDp() }
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    val gestureModifier = when {
        editor != null -> Modifier.pointerInput(scale) { detectTapGestures(onDoubleTap = onDoubleTap) }
        tool == AdaptiveViewerTool.HAND -> Modifier
            .pointerInput(viewport, scale, pan) { detectTransformGestures(panZoomLock = false) { centroid, panChange, zoom, _ -> onTransform(centroid, panChange, zoom) } }
            .pointerInput(scale) { detectTapGestures(onDoubleTap = onDoubleTap) }
        tool == AdaptiveViewerTool.SELECT -> Modifier.pointerInput(markups, scale, pan) { detectTapGestures { position -> screenToNormalized(position)?.let(onSelect) } }
        tool == AdaptiveViewerTool.TEXT && editable -> Modifier.pointerInput(scale, pan) { detectTapGestures { position -> screenToNormalized(position)?.let(onTapText) } }
        tool == AdaptiveViewerTool.SYMBOL && editable -> Modifier.pointerInput(scale, pan) { detectTapGestures { position -> screenToNormalized(position)?.let(onTapSymbol) } }
        tool.editsDocument && editable -> Modifier.pointerInput(tool, scale, pan) {
            var start: ReviewPoint? = null
            var points = mutableListOf<ReviewPoint>()
            detectDragGestures(
                onDragStart = { position ->
                    start = screenToNormalized(position)
                    points = mutableListOf<ReviewPoint>().apply { start?.let(::add) }
                    start?.let { onPreview(AdaptiveDrawingPreview(requireNotNull(tool.markupType), it, it, points.toList())) }
                },
                onDragCancel = { onPreview(null) },
                onDragEnd = {
                    val first = start
                    if (first != null) {
                        val last = points.lastOrNull() ?: first
                        onDrawingFinished(AdaptiveDrawingPreview(requireNotNull(tool.markupType), first, last, points.toList()))
                    }
                    onPreview(null)
                },
                onDrag = { change, _ ->
                    val point = screenToNormalized(change.position) ?: return@detectDragGestures
                    if (tool == AdaptiveViewerTool.FREEHAND || tool == AdaptiveViewerTool.HIGHLIGHT) {
                        if (points.isEmpty() || adaptivePointDistance(points.last(), point) > 0.002f) points.add(point)
                    } else points = mutableListOf(start ?: point, point)
                    val first = start ?: point
                    val label = ReviewPoint((first.x + point.x) / 2f, (((first.y + point.y) / 2f) - 0.045f).coerceIn(0.02f, 0.96f))
                    onPreview(AdaptiveDrawingPreview(requireNotNull(tool.markupType), first, point, points.toList(), label))
                    change.consume()
                }
            )
        }
        else -> Modifier
    }

    Box(
        Modifier.fillMaxSize().clipToBounds().background(Color(0xFF202327)).onGloballyPositioned { onViewport(it.size) }.then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        if (image == null) CircularProgressIndicator(color = SkmOrange)
        else Box(
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
                preview?.let { drawAdaptivePreview(it, Color(0xFFE53935), 0.004f, 0.92f) }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                markups.forEach { markup ->
                    when (markup.type) {
                        ReviewMarkupType.TEXT -> DirectMarkupLabel(markup, maxWidth, maxHeight, selected?.id == markup.source.id)
                        ReviewMarkupType.SYMBOL -> DirectSymbolLabel(markup, maxWidth, maxHeight, selected?.id == markup.source.id)
                        ReviewMarkupType.DIMENSION -> DirectDimensionLabel(markup, maxWidth, maxHeight, selected?.id == markup.source.id)
                        else -> Unit
                    }
                }
                editor?.takeIf { it.pageIndex >= 0 }?.let {
                    DirectInlineEditorBox(it, maxWidth, maxHeight, onEditorChange, onEditorSave, onEditorCancel)
                }
            }
        }
    }
}

@Composable
private fun DirectMarkupLabel(markup: AdaptiveMarkup, pageWidth: androidx.compose.ui.unit.Dp, pageHeight: androidx.compose.ui.unit.Dp, selected: Boolean) {
    Card(
        modifier = Modifier.offset(pageWidth * markup.x, pageHeight * markup.y).width((pageWidth * markup.width.coerceIn(0.16f, 0.55f)).coerceAtLeast(90.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(markup.colorArgb).copy(alpha = markup.opacity.coerceIn(0.45f, 0.98f))),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, SkmOrange) else null
    ) { Text(markup.text, Modifier.padding(6.dp), color = Color.White, fontSize = 10.sp, maxLines = 8, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun DirectSymbolLabel(markup: AdaptiveMarkup, pageWidth: androidx.compose.ui.unit.Dp, pageHeight: androidx.compose.ui.unit.Dp, selected: Boolean) {
    Surface(
        modifier = Modifier.offset(pageWidth * markup.x, pageHeight * markup.y).size((pageWidth * markup.width.coerceIn(0.035f, 0.12f)).coerceIn(26.dp, 64.dp)),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.90f),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkmOrange else Color(markup.colorArgb))
    ) { Box(contentAlignment = Alignment.Center) { Text(markup.text, color = Color(markup.colorArgb), fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
}

@Composable
private fun DirectDimensionLabel(markup: AdaptiveMarkup, pageWidth: androidx.compose.ui.unit.Dp, pageHeight: androidx.compose.ui.unit.Dp, selected: Boolean) {
    Surface(
        modifier = Modifier.offset(pageWidth * markup.labelX, pageHeight * markup.labelY).widthIn(min = 54.dp, max = 150.dp),
        shape = RoundedCornerShape(3.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkmOrange else Color(markup.colorArgb))
    ) { Text(markup.text, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color(markup.colorArgb), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun DirectInlineEditorBox(
    editor: DirectInlineEditor,
    pageWidth: androidx.compose.ui.unit.Dp,
    pageHeight: androidx.compose.ui.unit.Dp,
    onChange: (DirectInlineEditor) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focus = remember(editor.clientId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editor.clientId) { focus.requestFocus(); keyboard?.show() }
    val x = if (editor.type == ReviewMarkupType.DIMENSION) editor.labelX else editor.x
    val y = if (editor.type == ReviewMarkupType.DIMENSION) editor.labelY else editor.y
    val width = if (editor.type == ReviewMarkupType.DIMENSION) 0.22f else editor.width
    Surface(
        modifier = Modifier.offset(pageWidth * x, pageHeight * y).width((pageWidth * width.coerceIn(0.18f, 0.55f)).coerceAtLeast(120.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, SkmOrange),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (editor.type == ReviewMarkupType.DIMENSION) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Ø", "R", "±").forEach { prefix ->
                        Surface(
                            modifier = Modifier.clickable { onChange(editor.copy(text = prefix + editor.text.removePrefix("Ø").removePrefix("R").removePrefix("±"))) },
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF0F1F3)
                        ) { Text(prefix, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            BasicTextField(
                value = editor.text,
                onValueChange = { onChange(editor.copy(text = it.take(if (editor.type == ReviewMarkupType.DIMENSION) 40 else 500))) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).background(Color(0xFFF6F7F8), RoundedCornerShape(4.dp)).padding(7.dp),
                textStyle = TextStyle(color = SkmGraphite, fontSize = if (editor.type == ReviewMarkupType.DIMENSION) 14.sp else 12.sp, fontWeight = if (editor.type == ReviewMarkupType.DIMENSION) FontWeight.Bold else FontWeight.Normal),
                singleLine = editor.type == ReviewMarkupType.DIMENSION
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Cancelar", tint = SkmDanger) }
                IconButton(onClick = onSave, enabled = editor.text.trim().isNotEmpty(), modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Save, "Guardar", tint = SkmSuccess) }
            }
        }
    }
}

@Composable
private fun BoxScope.DirectTopBar(
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
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = if (landscape) 74.dp else 8.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.96f), shadowElevation = 10.dp
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ArrowBack, "Volver") }
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                Text("Rev ${document.revision} · ${document.status.replace('_', ' ')}", fontSize = 10.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = onPrevious, enabled = page > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronLeft, "Anterior") }
            Text("${page + 1}/$pageCount", Modifier.widthIn(min = 44.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNext, enabled = page + 1 < pageCount, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ChevronRight, "Siguiente") }
            if (landscape) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", Modifier.widthIn(min = 46.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
                IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onFit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.FitScreen, "Ajustar") }
                IconButton(onClick = onMarks, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Layers, "Marcas") }
                IconButton(onClick = onHistory, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.History, "Historial") }
            }
            IconButton(onClick = onHide, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Fullscreen, "Ocultar") }
        }
    }
}

@Composable
private fun BoxScope.DirectPortraitBar(tool: AdaptiveViewerTool, editable: Boolean, onTool: (AdaptiveViewerTool) -> Unit, onSymbols: () -> Unit, onMore: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.97f), shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DirectToolButton(Icons.Default.PanTool, "Mover", tool == AdaptiveViewerTool.HAND, true) { onTool(AdaptiveViewerTool.HAND) }
            DirectToolButton(Icons.Default.TextFields, "Texto", tool == AdaptiveViewerTool.TEXT, editable) { onTool(AdaptiveViewerTool.TEXT) }
            DirectToolButton(Icons.Default.Category, "Símbolo", tool == AdaptiveViewerTool.SYMBOL, editable, onSymbols)
            DirectToolButton(Icons.Default.Straighten, "Cota", tool == AdaptiveViewerTool.DIMENSION, editable) { onTool(AdaptiveViewerTool.DIMENSION) }
            DirectToolButton(Icons.Default.Draw, "Lápiz", tool == AdaptiveViewerTool.FREEHAND, editable) { onTool(AdaptiveViewerTool.FREEHAND) }
            DirectToolButton(Icons.Default.MoreHoriz, "Más", false, true, onMore)
        }
    }
}

@Composable
private fun BoxScope.DirectLandscapeRail(tool: AdaptiveViewerTool, editable: Boolean, onTool: (AdaptiveViewerTool) -> Unit, onSymbols: () -> Unit, onMore: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(60.dp).fillMaxHeight(0.72f),
        shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.97f), shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            DirectToolButton(Icons.Default.PanTool, "Mover", tool == AdaptiveViewerTool.HAND, true) { onTool(AdaptiveViewerTool.HAND) }
            DirectToolButton(Icons.Default.TextFields, "Texto", tool == AdaptiveViewerTool.TEXT, editable) { onTool(AdaptiveViewerTool.TEXT) }
            DirectToolButton(Icons.Default.Category, "Símbolo", tool == AdaptiveViewerTool.SYMBOL, editable, onSymbols)
            DirectToolButton(Icons.Default.Straighten, "Cota", tool == AdaptiveViewerTool.DIMENSION, editable) { onTool(AdaptiveViewerTool.DIMENSION) }
            DirectToolButton(Icons.Default.Draw, "Lápiz", tool == AdaptiveViewerTool.FREEHAND, editable) { onTool(AdaptiveViewerTool.FREEHAND) }
            DirectToolButton(Icons.Default.MoreHoriz, "Más", false, true, onMore)
        }
    }
}

@Composable
private fun DirectToolButton(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.32f).clickable(enabled = enabled, onClick = onClick).padding(3.dp)) {
        Surface(shape = CircleShape, color = if (selected) SkmOrange else Color.Transparent) {
            Icon(icon, label, Modifier.padding(8.dp).size(22.dp), tint = if (selected) Color.White else SkmGraphite)
        }
        Text(label, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun BoxScope.DirectPalette(
    landscape: Boolean,
    editable: Boolean,
    tool: AdaptiveViewerTool,
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    canApprove: Boolean,
    canRequestChanges: Boolean,
    onDismiss: () -> Unit,
    onTool: (AdaptiveViewerTool) -> Unit,
    onSymbols: () -> Unit,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    Surface(
        modifier = if (landscape) Modifier.align(Alignment.CenterEnd).padding(end = 76.dp).width(290.dp).fillMaxHeight(0.82f) else Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp, start = 10.dp, end = 10.dp).fillMaxWidth().heightIn(max = 430.dp),
        shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Herramientas de revisión", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            val tools = listOf(
                AdaptiveViewerTool.HAND to Icons.Default.PanTool,
                AdaptiveViewerTool.SELECT to Icons.Default.TouchApp,
                AdaptiveViewerTool.TEXT to Icons.Default.TextFields,
                AdaptiveViewerTool.SYMBOL to Icons.Default.Category,
                AdaptiveViewerTool.DIMENSION to Icons.Default.Straighten,
                AdaptiveViewerTool.FREEHAND to Icons.Default.Draw,
                AdaptiveViewerTool.HIGHLIGHT to Icons.Default.Highlight,
                AdaptiveViewerTool.LINE to Icons.Default.ShowChart,
                AdaptiveViewerTool.ARROW to Icons.Default.ArrowForward,
                AdaptiveViewerTool.RECTANGLE to Icons.Default.CropSquare,
                AdaptiveViewerTool.ELLIPSE to Icons.Default.Circle,
                AdaptiveViewerTool.CLOUD to Icons.Default.Cloud
            )
            tools.chunked(3).forEach { rowTools ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowTools.forEach { (candidate, icon) ->
                        DirectToolButton(icon, candidate.label, tool == candidate, candidate == AdaptiveViewerTool.HAND || candidate == AdaptiveViewerTool.SELECT || editable) {
                            if (candidate == AdaptiveViewerTool.SYMBOL) onSymbols() else onTool(candidate)
                        }
                    }
                }
            }
            Divider()
            Text("Color", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0xFFE53935.toInt(), 0xFFFF6A00.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF111111.toInt()).forEach { color ->
                    Surface(modifier = Modifier.size(34.dp).clickable { onColor(color) }, shape = CircleShape, color = Color(color), border = if (color == colorArgb) androidx.compose.foundation.BorderStroke(3.dp, Color.White) else null) {}
                }
            }
            Text("Espesor", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.0025f, 0.004f, 0.007f, 0.012f).forEach { value ->
                    OutlinedButton(onClick = { onStroke(value) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (abs(strokeWidth - value) < 0.0005f) Color(0xFFFFE6D5) else Color.Transparent)) { Text("${(value * 1000).roundToInt()}") }
                }
            }
            Text("Opacidad", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.35f, 0.65f, 0.95f).forEach { value ->
                    OutlinedButton(onClick = { onOpacity(value) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (abs(opacity - value) < 0.05f) Color(0xFFFFE6D5) else Color.Transparent)) { Text("${(value * 100).roundToInt()}%") }
                }
            }
            Divider()
            Button(onClick = onApprove, enabled = canApprove, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SkmSuccess)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar y firmar") }
            OutlinedButton(onClick = onRequestChanges, enabled = canRequestChanges, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) { Text("Solicitar cambios") }
        }
    }
}

@Composable
private fun BoxScope.DirectSymbolPicker(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 360.dp),
        shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selecciona un símbolo", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            directSymbols.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { symbol ->
                        Surface(
                            modifier = Modifier.size(58.dp).clickable { onSelect(symbol) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (symbol == selected) Color(0xFFFFE6D5) else Color(0xFFF2F3F5),
                            border = if (symbol == selected) androidx.compose.foundation.BorderStroke(2.dp, SkmOrange) else null
                        ) { Box(contentAlignment = Alignment.Center) { Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold) } }
                    }
                }
            }
            Text("Después de elegirlo, toca el plano para ubicarlo.", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun BoxScope.DirectSelectionBar(comment: PlanComment, canModify: Boolean, onEdit: () -> Unit, onMove: () -> Unit, onPublish: (() -> Unit)?, onDelete: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp, start = 12.dp, end = 12.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(comment.displayLabel, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
            IconButton(onClick = onEdit, enabled = canModify && comment.markupType in listOf(ReviewMarkupType.TEXT, ReviewMarkupType.DIMENSION)) { Icon(Icons.Default.Edit, "Editar") }
            IconButton(onClick = onMove, enabled = canModify) { Icon(Icons.Default.SelectAll, "Mover") }
            onPublish?.let { IconButton(onClick = it) { Icon(Icons.Default.Publish, "Publicar", tint = SkmSuccess) } }
            IconButton(onClick = onDelete, enabled = canModify) { Icon(Icons.Default.Delete, "Eliminar", tint = SkmDanger) }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
        }
    }
}

@Composable
private fun BoxScope.DirectMarksPanel(comments: List<PlanComment>, currentEmail: String, onSelect: (PlanComment) -> Unit, onPublish: (PlanComment) -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.CenterStart).padding(12.dp).widthIn(max = 360.dp).fillMaxHeight(0.78f),
        shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marcas y comentarios", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            if (comments.isEmpty()) Text("No hay marcas.", color = Color.Gray)
            comments.sortedByDescending { it.updatedAt }.forEach { comment ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comment) }, colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6F8))) {
                    Column(Modifier.padding(9.dp)) {
                        Text("Hoja ${comment.pageIndex + 1} · ${comment.displayLabel}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(comment.authorName.ifBlank { comment.authorEmail }, fontSize = 10.sp, color = Color.Gray)
                        if (!comment.published && comment.authorEmail.equals(currentEmail, true)) {
                            OutlinedButton(onClick = { onPublish(comment) }, modifier = Modifier.padding(top = 4.dp)) { Text("Publicar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DirectHistoryPanel(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.CenterStart).padding(12.dp).widthIn(max = 380.dp).fillMaxHeight(0.78f),
        shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Historial", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
            }
            if (timeline.isEmpty()) Text("Sin eventos registrados.", color = Color.Gray)
            timeline.sortedByDescending { it.createdAt }.forEach { event ->
                Column(Modifier.fillMaxWidth().background(Color(0xFFF5F6F8), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text(event.type.name.replace('_', ' '), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(event.actorName.ifBlank { event.actorEmail }, fontSize = 10.sp)
                    Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(event.createdAt)), fontSize = 10.sp, color = Color.Gray)
                    if (event.detail.isNotBlank()) Text(event.detail, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun Context.directViewerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.directViewerActivity()
    else -> null
}
