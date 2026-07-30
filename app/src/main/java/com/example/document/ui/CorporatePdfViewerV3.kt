package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.ui.theme.SkmOrangeLight
import com.example.ui.theme.SkmSuccess
import com.example.ui.theme.SkmTextSecondary
import com.example.ui.theme.SkmWarning
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
import kotlin.math.sqrt

private enum class ImmersiveViewerTool(val label: String, val icon: ImageVector) {
    HAND("Mover", Icons.Default.PanTool),
    SELECT("Seleccionar", Icons.Default.NearMe),
    TEXT("Texto", Icons.Default.TextFields),
    FREEHAND("Lápiz", Icons.Default.Draw),
    HIGHLIGHT("Resaltador", Icons.Default.BorderColor),
    LINE("Línea", Icons.Default.HorizontalRule),
    ARROW("Flecha", Icons.Default.ArrowRightAlt),
    RECTANGLE("Rectángulo", Icons.Default.CropSquare),
    ELLIPSE("Elipse", Icons.Default.Circle),
    CLOUD("Nube", Icons.Default.CloudQueue)
}

private data class ImmersiveTransform(val scale: Float = 1f, val pan: Offset = Offset.Zero)

private data class ImmersiveMarkup(
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

private data class ImmersiveDrawing(
    val type: ReviewMarkupType,
    val start: ReviewPoint,
    val end: ReviewPoint,
    val points: List<ReviewPoint>
)

private data class ImmersiveTextPlacement(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.34f
)

private data class ImmersiveRedo(val input: ReviewMarkupInput, val clientId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporatePdfViewerV3(
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
    var transform by remember(file, page) { mutableStateOf(ImmersiveTransform()) }
    var tool by rememberSaveable { mutableStateOf(ImmersiveViewerTool.HAND) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var propertiesVisible by rememberSaveable { mutableStateOf(false) }
    var colorArgb by rememberSaveable { mutableIntStateOf(0xFFFF6A00.toInt()) }
    var strokeWidth by rememberSaveable { mutableFloatStateOf(0.004f) }
    var opacity by rememberSaveable { mutableFloatStateOf(1f) }
    var drawing by remember { mutableStateOf<ImmersiveDrawing?>(null) }
    var textPlacement by remember { mutableStateOf<ImmersiveTextPlacement?>(null) }
    var selected by remember { mutableStateOf<PlanComment?>(null) }
    var showMarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showRequestChanges by remember { mutableStateOf(false) }
    var undoClientIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var redoItems by remember { mutableStateOf<List<ImmersiveRedo>>(emptyList()) }
    var hiddenClientIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val pageMarkups = remember(comments, page, hiddenClientIds) {
        comments
            .filter { it.pageIndex == page }
            .map(::decodeImmersiveMarkup)
            .filterNot { it.clientId in hiddenClientIds }
    }

    LaunchedEffect(file, page) {
        val rendered = renderImmersivePage(file, page)
        bitmap = rendered.first
        pageCount = rendered.second
        transform = ImmersiveTransform()
        drawing = null
        selected = null
        tool = ImmersiveViewerTool.HAND
    }

    LaunchedEffect(comments, hiddenClientIds) {
        val resolved = hiddenClientIds.mapNotNull { clientId ->
            comments.firstOrNull { decodeImmersiveMarkup(it).clientId == clientId }
        }
        resolved.forEach(onDeleteComment)
        if (resolved.isNotEmpty()) {
            hiddenClientIds = hiddenClientIds - resolved.map { decodeImmersiveMarkup(it).clientId }.toSet()
        }
    }

    fun fittedPageSize(): Pair<Float, Float> {
        val image = bitmap ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fit = min(
            viewport.width.toFloat() / image.width.toFloat(),
            viewport.height.toFloat() / image.height.toFloat()
        )
        return image.width * fit to image.height * fit
    }

    fun clampPan(candidate: Offset, targetScale: Float): Offset {
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return Offset.Zero
        val maxX = ((baseWidth * targetScale - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((baseHeight * targetScale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun screenToNormalized(position: Offset): ReviewPoint? {
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return null
        val baseOrigin = Offset(
            (viewport.width - baseWidth) / 2f,
            (viewport.height - baseHeight) / 2f
        )
        val pageCenter = Offset(baseWidth / 2f, baseHeight / 2f)
        val local = Offset(
            (position.x - baseOrigin.x - pageCenter.x - transform.pan.x) / transform.scale + pageCenter.x,
            (position.y - baseOrigin.y - pageCenter.y - transform.pan.y) / transform.scale + pageCenter.y
        )
        if (local.x !in 0f..baseWidth || local.y !in 0f..baseHeight) return null
        return ReviewPoint(local.x / baseWidth, local.y / baseHeight)
    }

    fun applyGesture(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val old = transform
        val newScale = (old.scale * zoomChange).coerceIn(1f, 12f)
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return
        val baseOrigin = Offset(
            (viewport.width - baseWidth) / 2f,
            (viewport.height - baseHeight) / 2f
        )
        val pageCenter = Offset(baseWidth / 2f, baseHeight / 2f)
        val contentPoint = Offset(
            (centroid.x - baseOrigin.x - pageCenter.x - old.pan.x) / old.scale + pageCenter.x,
            (centroid.y - baseOrigin.y - pageCenter.y - old.pan.y) / old.scale + pageCenter.y
        )
        val candidate = Offset(
            centroid.x - baseOrigin.x - pageCenter.x - (contentPoint.x - pageCenter.x) * newScale + panChange.x,
            centroid.y - baseOrigin.y - pageCenter.y - (contentPoint.y - pageCenter.y) * newScale + panChange.y
        )
        transform = ImmersiveTransform(newScale, clampPan(candidate, newScale))
    }

    fun zoomAt(position: Offset, targetScale: Float) {
        val old = transform
        val target = targetScale.coerceIn(1f, 12f)
        val (baseWidth, baseHeight) = fittedPageSize()
        if (baseWidth <= 0f || baseHeight <= 0f) return
        val baseOrigin = Offset((viewport.width - baseWidth) / 2f, (viewport.height - baseHeight) / 2f)
        val pageCenter = Offset(baseWidth / 2f, baseHeight / 2f)
        val contentPoint = Offset(
            (position.x - baseOrigin.x - pageCenter.x - old.pan.x) / old.scale + pageCenter.x,
            (position.y - baseOrigin.y - pageCenter.y - old.pan.y) / old.scale + pageCenter.y
        )
        val candidate = Offset(
            position.x - baseOrigin.x - pageCenter.x - (contentPoint.x - pageCenter.x) * target,
            position.y - baseOrigin.y - pageCenter.y - (contentPoint.y - pageCenter.y) * target
        )
        transform = ImmersiveTransform(target, if (target == 1f) Offset.Zero else clampPan(candidate, target))
    }

    fun submitMarkup(input: ReviewMarkupInput, clientId: String = UUID.randomUUID().toString()) {
        onAddComment(input.pageIndex, encodeImmersiveMarkup(input, clientId), input.x, input.y, input.width)
        undoClientIds = undoClientIds + clientId
        redoItems = emptyList()
    }

    fun finishDrawing(preview: ImmersiveDrawing) {
        val sourcePoints = preview.points.ifEmpty { listOf(preview.start, preview.end) }
        val minX = sourcePoints.minOf { it.x }
        val minY = sourcePoints.minOf { it.y }
        val maxX = sourcePoints.maxOf { it.x }
        val maxY = sourcePoints.maxOf { it.y }
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

    fun undoLast() {
        val clientId = undoClientIds.lastOrNull() ?: return
        val existing = comments.firstOrNull { decodeImmersiveMarkup(it).clientId == clientId }
        val decoded = existing?.let(::decodeImmersiveMarkup)
        if (existing != null && decoded != null) {
            onDeleteComment(existing)
            redoItems = redoItems + ImmersiveRedo(decoded.toInput(), clientId)
        } else {
            hiddenClientIds = hiddenClientIds + clientId
        }
        undoClientIds = undoClientIds.dropLast(1)
    }

    fun redoLast() {
        val item = redoItems.lastOrNull() ?: return
        submitMarkup(item.input, item.clientId)
        redoItems = redoItems.dropLast(1)
        hiddenClientIds = hiddenClientIds - item.clientId
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        ImmersiveSystemBars()
        BackHandler(onBack = onClose)
        Surface(Modifier.fillMaxSize(), color = Color(0xFF17191B)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                ImmersiveCanvas(
                    bitmap = bitmap,
                    viewport = viewport,
                    transform = transform,
                    tool = tool,
                    markups = pageMarkups,
                    drawing = drawing,
                    onViewport = {
                        viewport = it
                        transform = transform.copy(pan = clampPan(transform.pan, transform.scale))
                    },
                    onTransform = ::applyGesture,
                    onDoubleTap = { position -> zoomAt(position, if (transform.scale < 2f) 2.7f else 1f) },
                    screenToNormalized = ::screenToNormalized,
                    onText = { point -> textPlacement = ImmersiveTextPlacement(page, point.x, point.y) },
                    onDrawing = { drawing = it },
                    onDrawingFinished = {
                        drawing = null
                        finishDrawing(it)
                    },
                    onSelect = { point -> selected = hitTestImmersive(pageMarkups, point) }
                )

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    ImmersiveHeader(
                        document = document,
                        page = page,
                        pageCount = pageCount,
                        scale = transform.scale,
                        commentsCount = comments.size,
                        landscape = landscape,
                        onClose = onClose,
                        onPrevious = { if (page > 0) page-- },
                        onNext = { if (page + 1 < pageCount) page++ },
                        onZoomOut = {
                            val target = (transform.scale / 1.35f).coerceAtLeast(1f)
                            zoomAt(Offset(viewport.width / 2f, viewport.height / 2f), target)
                        },
                        onZoomIn = {
                            val target = (transform.scale * 1.35f).coerceAtMost(12f)
                            zoomAt(Offset(viewport.width / 2f, viewport.height / 2f), target)
                        },
                        onFit = { transform = ImmersiveTransform() },
                        onMarks = { showMarks = true },
                        onHistory = { showHistory = true }
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ImmersiveToolShelf(
                        selected = tool,
                        canEdit = canComment && document.isUnderReview,
                        undoEnabled = undoClientIds.isNotEmpty(),
                        redoEnabled = redoItems.isNotEmpty(),
                        onTool = {
                            tool = it
                            drawing = null
                            selected = null
                        },
                        onProperties = { propertiesVisible = !propertiesVisible },
                        onUndo = ::undoLast,
                        onRedo = ::redoLast
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible && propertiesVisible && tool.isDrawingTool(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ImmersiveProperties(
                        colorArgb = colorArgb,
                        strokeWidth = strokeWidth,
                        opacity = opacity,
                        bottomPadding = if (landscape) 78 else 82,
                        onColor = { colorArgb = it },
                        onStroke = { strokeWidth = it },
                        onOpacity = { opacity = it }
                    )
                }

                if (chromeVisible && document.canBeSignedBy(currentEmail)) {
                    ImmersiveDecisionShelf(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        landscape = landscape,
                        onRequestChanges = { showRequestChanges = true },
                        onApprove = onApprove
                    )
                }

                SmallViewerToggle(
                    visible = chromeVisible,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = { chromeVisible = !chromeVisible }
                )
            }
        }
    }

    textPlacement?.let { placement ->
        ImmersiveTextEditor(
            page = placement.pageIndex,
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
        ImmersiveMarkupInspector(
            comment = comment,
            currentEmail = currentEmail,
            isAdmin = isAdmin,
            canComment = canComment,
            onDismiss = { selected = null },
            onPublish = {
                selected = null
                onPublishComment(comment)
            },
            onDelete = {
                selected = null
                onDeleteComment(comment)
            },
            onUpdate = {
                selected = null
                onUpdateComment(it)
            }
        )
    }

    if (showMarks) {
        ImmersiveMarkupsList(
            comments = comments,
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
        ImmersiveHistory(timeline, onDismiss = { showHistory = false })
    }

    if (showRequestChanges) {
        ImmersiveRequestChanges(
            onDismiss = { showRequestChanges = false },
            onConfirm = {
                showRequestChanges = false
                onRequestChanges(it)
            }
        )
    }
}

@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            window.setDimAmount(0f)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun ImmersiveCanvas(
    bitmap: Bitmap?,
    viewport: IntSize,
    transform: ImmersiveTransform,
    tool: ImmersiveViewerTool,
    markups: List<ImmersiveMarkup>,
    drawing: ImmersiveDrawing?,
    onViewport: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    screenToNormalized: (Offset) -> ReviewPoint?,
    onText: (ReviewPoint) -> Unit,
    onDrawing: (ImmersiveDrawing?) -> Unit,
    onDrawingFinished: (ImmersiveDrawing) -> Unit,
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
        ImmersiveViewerTool.HAND -> Modifier
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    onTransform(centroid, pan, zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = onDoubleTap)
            }

        ImmersiveViewerTool.SELECT -> Modifier.pointerInput(markups) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onSelect) }
        }

        ImmersiveViewerTool.TEXT -> Modifier.pointerInput(Unit) {
            detectTapGestures { position -> screenToNormalized(position)?.let(onText) }
        }

        else -> Modifier.pointerInput(tool) {
            var start: ReviewPoint? = null
            var points = mutableListOf<ReviewPoint>()
            detectDragGestures(
                onDragStart = { position ->
                    start = screenToNormalized(position)
                    points = mutableListOf<ReviewPoint>().apply { start?.let(::add) }
                    start?.let { onDrawing(ImmersiveDrawing(tool.toMarkupType(), it, it, points.toList())) }
                },
                onDragCancel = { onDrawing(null) },
                onDragEnd = {
                    val first = start
                    val last = points.lastOrNull()
                    onDrawing(null)
                    if (first != null && last != null) {
                        onDrawingFinished(ImmersiveDrawing(tool.toMarkupType(), first, last, points.toList()))
                    }
                },
                onDrag = { change, _ ->
                    val point = screenToNormalized(change.position) ?: return@detectDragGestures
                    if (tool == ImmersiveViewerTool.FREEHAND || tool == ImmersiveViewerTool.HIGHLIGHT) {
                        if (points.isEmpty() || immersiveDistance(points.last(), point) > 0.0012f) points.add(point)
                    } else {
                        points = mutableListOf(start ?: point, point)
                    }
                    val first = start ?: point
                    onDrawing(ImmersiveDrawing(tool.toMarkupType(), first, point, points.toList()))
                    change.consume()
                }
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF202225))
            .onGloballyPositioned { onViewport(it.size) }
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
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.pan.x
                        translationY = transform.pan.y
                        transformOrigin = TransformOrigin.Center
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
                    markups.filter { it.type != ReviewMarkupType.TEXT }.forEach(::drawImmersiveMarkup)
                    drawing?.let { drawImmersivePreview(it, Color(0xFFFF6A00), 0.004f, 0.92f) }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    markups.filter { it.type == ReviewMarkupType.TEXT }.forEach { markup ->
                        val cardWidth = maxWidth * markup.width.coerceIn(0.18f, 0.62f)
                        Card(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (markup.x * constraints.maxWidth).roundToInt(),
                                        (markup.y * constraints.maxHeight).roundToInt()
                                    )
                                }
                                .width(cardWidth),
                            shape = RoundedCornerShape(7.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(markup.colorArgb).copy(alpha = if (markup.source.published) 0.90f else 0.78f)
                            ),
                            border = if (markup.source.published) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                        ) {
                            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (markup.source.published) "PUBLICADA" else "BORRADOR",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(markup.text, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ImmersiveHeader(
    document: DocumentRecord,
    page: Int,
    pageCount: Int,
    scale: Float,
    commentsCount: Int,
    landscape: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onMarks: () -> Unit,
    onHistory: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(10.dp)
            .widthIn(max = if (landscape) 900.dp else 620.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 10.dp
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 560.dp
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ViewerHeaderIcon(onClose, Icons.Default.ArrowBack, "Cerrar")
                if (!compact) {
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            "OT ${document.otNumber} · ${document.code}",
                            color = SkmGraphite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Rev ${document.revision} · ${document.workflowStatusLabel}",
                            color = SkmTextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                } else {
                    Spacer(Modifier.width(2.dp))
                }
                ViewerHeaderIcon(onPrevious, Icons.Default.ChevronLeft, "Hoja anterior", page > 0)
                Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFF1F2F3)) {
                    Text(
                        "${page + 1}/$pageCount",
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = SkmGraphite,
                        fontWeight = FontWeight.Bold
                    )
                }
                ViewerHeaderIcon(onNext, Icons.Default.ChevronRight, "Hoja siguiente", page + 1 < pageCount)
                if (!compact) {
                    ViewerHeaderIcon(onZoomOut, Icons.Default.ZoomOut, "Alejar")
                    Text("${(scale * 100).roundToInt()}%", color = SkmGraphite, style = MaterialTheme.typography.labelMedium)
                    ViewerHeaderIcon(onZoomIn, Icons.Default.ZoomIn, "Acercar")
                }
                ViewerHeaderIcon(onFit, Icons.Default.FitScreen, "Ajustar")
                IconButton(onClick = onMarks, modifier = Modifier.size(44.dp)) {
                    BadgedBox(badge = { if (commentsCount > 0) Badge { Text(commentsCount.toString()) } }) {
                        Icon(Icons.Default.Layers, "Marcas", tint = SkmGraphite)
                    }
                }
                ViewerHeaderIcon(onHistory, Icons.Default.History, "Historial")
            }
        }
    }
}

@Composable
private fun ViewerHeaderIcon(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, description, tint = if (enabled) SkmGraphite else Color.LightGray)
    }
}

@Composable
private fun BoxScope.ImmersiveToolShelf(
    selected: ImmersiveViewerTool,
    canEdit: Boolean,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onTool: (ImmersiveViewerTool) -> Unit,
    onProperties: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(10.dp).widthIn(max = 820.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 12.dp
    ) {
        LazyRow(
            modifier = Modifier.heightIn(min = 58.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(ImmersiveViewerTool.entries) { item ->
                val enabled = item == ImmersiveViewerTool.HAND || item == ImmersiveViewerTool.SELECT || canEdit
                FilledTonalIconButton(
                    onClick = { onTool(item) },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (item == selected) SkmOrange else Color(0xFFF0F1F2),
                        contentColor = if (item == selected) Color.White else SkmGraphite
                    )
                ) {
                    Icon(item.icon, item.label)
                }
            }
            item {
                Spacer(Modifier.width(3.dp))
                IconButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Undo, "Deshacer", tint = SkmGraphite)
                }
            }
            item {
                IconButton(onClick = onRedo, enabled = redoEnabled, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Redo, "Rehacer", tint = SkmGraphite)
                }
            }
            item {
                IconButton(onClick = onProperties, enabled = selected.isDrawingTool(), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Tune, "Propiedades", tint = SkmGraphite)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ImmersiveProperties(
    colorArgb: Int,
    strokeWidth: Float,
    opacity: Float,
    bottomPadding: Int,
    onColor: (Int) -> Unit,
    onStroke: (Float) -> Unit,
    onOpacity: (Float) -> Unit
) {
    val colors = listOf(
        0xFFFF6A00.toInt(), 0xFFD32F2F.toInt(), 0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(), 0xFF212121.toInt(), 0xFFF9A825.toInt()
    )
    Surface(
        modifier = Modifier.padding(bottom = bottomPadding.dp).widthIn(max = 620.dp).padding(horizontal = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 10.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { Text("Color", color = SkmGraphite, fontWeight = FontWeight.Bold) }
                items(colors) { value ->
                    Surface(
                        onClick = { onColor(value) },
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = Color(value),
                        border = if (value == colorArgb) BorderStroke(3.dp, SkmGraphite) else null
                    ) {}
                }
                item { Text("Espesor", color = SkmGraphite, fontWeight = FontWeight.Bold) }
                items(listOf(0.0025f, 0.004f, 0.008f, 0.014f)) { value ->
                    FilledTonalIconButton(
                        onClick = { onStroke(value) },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (kotlin.math.abs(strokeWidth - value) < 0.001f) SkmOrange else Color(0xFFF0F1F2),
                            contentColor = if (kotlin.math.abs(strokeWidth - value) < 0.001f) Color.White else SkmGraphite
                        )
                    ) { Text("${(value * 1000).roundToInt()}") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacidad", color = SkmGraphite, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = opacity,
                    onValueChange = onOpacity,
                    valueRange = 0.15f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Text("${(opacity * 100).roundToInt()}%", color = SkmGraphite, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun BoxScope.ImmersiveDecisionShelf(
    modifier: Modifier,
    landscape: Boolean,
    onRequestChanges: () -> Unit,
    onApprove: () -> Unit
) {
    Surface(
        modifier = modifier.padding(end = 12.dp, bottom = if (landscape) 82.dp else 88.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(
                onClick = onRequestChanges,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)
            ) { Text("Cambios") }
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("Aprobar")
            }
        }
    }
}

@Composable
private fun BoxScope.SmallViewerToggle(
    visible: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.padding(12.dp).size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = SkmOrange,
            contentColor = Color.White
        )
    ) {
        Icon(if (visible) Icons.Default.Fullscreen else Icons.Default.Build, if (visible) "Ocultar controles" else "Mostrar controles")
    }
}

@Composable
private fun ImmersiveTextEditor(
    page: Int,
    initialWidth: Float,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable { mutableFloatStateOf(initialWidth) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                    Text("Observación · hoja ${page + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Se guardará como borrador privado hasta que la publiques.")
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
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Cancelar") }
                    Button(
                        onClick = { onSave(text.trim(), width) },
                        enabled = text.trim().isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)
                    ) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveMarkupInspector(
    comment: PlanComment,
    currentEmail: String,
    isAdmin: Boolean,
    canComment: Boolean,
    onDismiss: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (PlanComment) -> Unit
) {
    val markup = decodeImmersiveMarkup(comment)
    val canModify = canComment && comment.canBeModifiedBy(currentEmail, isAdmin)
    var note by rememberSaveable(comment.id) { mutableStateOf(markup.text) }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().widthIn(max = 620.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(markup.type.icon(), null, tint = Color(markup.colorArgb))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(comment.displayLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(if (comment.published) "Publicada" else "Borrador privado", color = if (comment.published) SkmSuccess else SkmWarning)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Text("${comment.authorName.ifBlank { comment.authorEmail }} · ${formatImmersiveDate(comment.createdAt)}")
                if (markup.type == ReviewMarkupType.TEXT) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (canModify && it.length <= 1200) note = it },
                        label = { Text("Observación") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        readOnly = !canModify
                    )
                }
                if (canModify && markup.type == ReviewMarkupType.TEXT) {
                    Button(
                        onClick = {
                            val updatedInput = markup.toInput().copy(text = note.trim())
                            onUpdate(comment.copy(text = encodeImmersiveMarkup(updatedInput, markup.clientId)))
                        },
                        enabled = note.trim().isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar cambios") }
                }
                if (canModify && !comment.published && comment.authorEmail.equals(currentEmail, true)) {
                    Button(onClick = onPublish, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) {
                        Icon(Icons.Default.Publish, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Publicar para todos")
                    }
                }
                if (canModify) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveMarkupsList(
    comments: List<PlanComment>,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSelect: (PlanComment) -> Unit,
    onPublishAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                    Text("Marcas y observaciones", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    val drafts = comments.count { !it.published && it.authorEmail.equals(currentEmail, true) }
                    if (drafts > 0) TextButton(onClick = onPublishAll) { Text("Publicar $drafts") }
                }
                HorizontalDivider()
                if (comments.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay marcas en este plano.") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(comments, key = { it.id }) { comment ->
                            val decoded = decodeImmersiveMarkup(comment)
                            OutlinedCard(onClick = { onSelect(comment) }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(decoded.type.icon(), null, tint = Color(decoded.colorArgb))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Hoja ${comment.pageIndex + 1} · ${comment.displayLabel}", fontWeight = FontWeight.Bold)
                                        Text(decoded.text.ifBlank { "Sin nota adicional" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(comment.authorName.ifBlank { comment.authorEmail }, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(if (comment.published) "PUBLICADA" else "BORRADOR", color = if (comment.published) SkmSuccess else SkmWarning, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveHistory(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                    Text("Historial del plano", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(timeline, key = { it.id }) { event ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(event.detail, fontWeight = FontWeight.Bold)
                                Text(event.actorName.ifBlank { event.actorEmail }, style = MaterialTheme.typography.bodySmall)
                                Text(formatImmersiveDate(event.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveRequestChanges(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EditNote, null, tint = SkmDanger) },
        title = { Text("Solicitar cambios") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= 1200) reason = it },
                label = { Text("Motivo obligatorio") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason.trim()) }, enabled = reason.trim().isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = SkmDanger)) {
                Text("Solicitar cambios")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun ImmersiveViewerTool.isDrawingTool(): Boolean = this !in listOf(ImmersiveViewerTool.HAND, ImmersiveViewerTool.SELECT)

private fun ImmersiveViewerTool.toMarkupType(): ReviewMarkupType = when (this) {
    ImmersiveViewerTool.TEXT -> ReviewMarkupType.TEXT
    ImmersiveViewerTool.FREEHAND -> ReviewMarkupType.FREEHAND
    ImmersiveViewerTool.HIGHLIGHT -> ReviewMarkupType.HIGHLIGHT
    ImmersiveViewerTool.LINE -> ReviewMarkupType.LINE
    ImmersiveViewerTool.ARROW -> ReviewMarkupType.ARROW
    ImmersiveViewerTool.RECTANGLE -> ReviewMarkupType.RECTANGLE
    ImmersiveViewerTool.ELLIPSE -> ReviewMarkupType.ELLIPSE
    ImmersiveViewerTool.CLOUD -> ReviewMarkupType.CLOUD
    else -> ReviewMarkupType.FREEHAND
}

private fun ReviewMarkupType.icon(): ImageVector = when (this) {
    ReviewMarkupType.TEXT -> Icons.Default.TextFields
    ReviewMarkupType.FREEHAND -> Icons.Default.Draw
    ReviewMarkupType.HIGHLIGHT -> Icons.Default.BorderColor
    ReviewMarkupType.LINE -> Icons.Default.HorizontalRule
    ReviewMarkupType.ARROW -> Icons.Default.ArrowRightAlt
    ReviewMarkupType.RECTANGLE -> Icons.Default.CropSquare
    ReviewMarkupType.ELLIPSE -> Icons.Default.Circle
    ReviewMarkupType.CLOUD -> Icons.Default.CloudQueue
}

private fun DrawScope.drawImmersiveMarkup(markup: ImmersiveMarkup) {
    val color = Color(markup.colorArgb).copy(alpha = markup.opacity.coerceIn(0.08f, 1f))
    val stroke = (markup.strokeWidth * size.minDimension).coerceAtLeast(1.5f)
    val start = Offset(markup.x * size.width, markup.y * size.height)
    val end = Offset(markup.endX * size.width, markup.endY * size.height)
    when (markup.type) {
        ReviewMarkupType.TEXT -> Unit
        ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> {
            val points = markup.points.map { Offset(it.x * size.width, it.y * size.height) }
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        ReviewMarkupType.LINE -> drawLine(color, start, end, stroke, StrokeCap.Round)
        ReviewMarkupType.ARROW -> drawImmersiveArrow(color, start, end, stroke)
        ReviewMarkupType.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            drawRect(color, Offset(left, top), Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)), style = Stroke(stroke))
        }
        ReviewMarkupType.ELLIPSE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            drawOval(color, Offset(left, top), Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)), style = Stroke(stroke))
        }
        ReviewMarkupType.CLOUD -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            drawRoundRect(
                color,
                Offset(left, top),
                Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 3f),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2.2f, stroke * 1.3f)))
            )
        }
    }
}

private fun DrawScope.drawImmersivePreview(preview: ImmersiveDrawing, color: Color, strokeWidth: Float, opacity: Float) {
    val markup = ImmersiveMarkup(
        source = PlanComment(), clientId = "preview", type = preview.type, text = "",
        x = preview.start.x, y = preview.start.y, endX = preview.end.x, endY = preview.end.y,
        width = kotlin.math.abs(preview.end.x - preview.start.x),
        height = kotlin.math.abs(preview.end.y - preview.start.y),
        colorArgb = color.value.toLong().toInt(), strokeWidth = strokeWidth, opacity = opacity,
        points = preview.points
    )
    drawImmersiveMarkup(markup)
}

private fun DrawScope.drawImmersiveArrow(color: Color, start: Offset, end: Offset, stroke: Float) {
    drawLine(color, start, end, stroke, StrokeCap.Round)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val length = (stroke * 5.5f).coerceAtLeast(14f)
    val first = Offset(end.x - length * cos(angle - 0.55f), end.y - length * sin(angle - 0.55f))
    val second = Offset(end.x - length * cos(angle + 0.55f), end.y - length * sin(angle + 0.55f))
    drawLine(color, end, first, stroke, StrokeCap.Round)
    drawLine(color, end, second, stroke, StrokeCap.Round)
}

private fun hitTestImmersive(markups: List<ImmersiveMarkup>, point: ReviewPoint): PlanComment? {
    return markups.asReversed().firstOrNull { markup ->
        when (markup.type) {
            ReviewMarkupType.TEXT, ReviewMarkupType.RECTANGLE, ReviewMarkupType.ELLIPSE, ReviewMarkupType.CLOUD -> {
                val minX = minOf(markup.x, markup.endX)
                val maxX = maxOf(markup.x + markup.width, markup.endX)
                val minY = minOf(markup.y, markup.endY)
                val maxY = maxOf(markup.y + markup.height, markup.endY)
                point.x in (minX - 0.02f)..(maxX + 0.02f) && point.y in (minY - 0.02f)..(maxY + 0.02f)
            }
            ReviewMarkupType.LINE, ReviewMarkupType.ARROW -> immersivePointToSegment(point, ReviewPoint(markup.x, markup.y), ReviewPoint(markup.endX, markup.endY)) < 0.025f
            ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> markup.points.zipWithNext().any { immersivePointToSegment(point, it.first, it.second) < 0.028f }
        }
    }?.source
}

private fun immersiveDistance(a: ReviewPoint, b: ReviewPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun immersivePointToSegment(p: ReviewPoint, a: ReviewPoint, b: ReviewPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return immersiveDistance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return immersiveDistance(p, ReviewPoint(a.x + t * dx, a.y + t * dy))
}

private fun ImmersiveMarkup.toInput(): ReviewMarkupInput = ReviewMarkupInput(
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

private fun encodeImmersiveMarkup(input: ReviewMarkupInput, clientId: String): String {
    val points = JSONArray()
    input.points.forEach { points.put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())) }
    return IMMERSIVE_MARKUP_PREFIX + JSONObject()
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

private fun decodeImmersiveMarkup(comment: PlanComment): ImmersiveMarkup {
    if (!comment.text.startsWith(IMMERSIVE_MARKUP_PREFIX)) {
        return ImmersiveMarkup(
            comment, comment.id, comment.markupType, comment.text,
            comment.x, comment.y, comment.endX, comment.endY,
            comment.width, comment.height, comment.colorArgb,
            comment.strokeWidth, comment.opacity, comment.points
        )
    }
    return runCatching {
        val json = JSONObject(comment.text.removePrefix(IMMERSIVE_MARKUP_PREFIX))
        val pointArray = json.optJSONArray("points") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointArray.length()) {
                val item = pointArray.optJSONObject(index) ?: continue
                add(ReviewPoint(item.optDouble("x").toFloat(), item.optDouble("y").toFloat()))
            }
        }
        ImmersiveMarkup(
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
        ImmersiveMarkup(
            comment, comment.id, ReviewMarkupType.TEXT, comment.text,
            comment.x, comment.y, comment.endX, comment.endY,
            comment.width, comment.height, comment.colorArgb,
            comment.strokeWidth, comment.opacity, comment.points
        )
    }
}

private suspend fun renderImmersivePage(file: File, index: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val maxSide = 3000f
                val naturalScale = 2.5f
                val renderScale = min(naturalScale, maxSide / maxOf(page.width, page.height).toFloat())
                val output = Bitmap.createBitmap(
                    (page.width * renderScale).roundToInt().coerceAtLeast(1),
                    (page.height * renderScale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                output.eraseColor(AndroidColor.WHITE)
                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                output to renderer.pageCount
            }
        }
    }
}

private fun formatImmersiveDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}

private const val IMMERSIVE_MARKUP_PREFIX = "@SKM_MARKUP_V2@"
