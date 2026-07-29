package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class CorporateCommentDraft(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.36f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorporatePdfViewer(
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
    var scale by remember(file, page) { mutableFloatStateOf(1f) }
    var pan by remember(file, page) { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var addMode by remember { mutableStateOf(false) }
    var movingComment by remember { mutableStateOf<PlanComment?>(null) }
    var draft by remember { mutableStateOf<CorporateCommentDraft?>(null) }
    var editing by remember { mutableStateOf<PlanComment?>(null) }
    var showComments by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showRequestChanges by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(file, page) {
        val rendered = corporateRenderPage(file, page)
        bitmap = rendered.first
        pageCount = rendered.second
        scale = 1f
        pan = Offset.Zero
        addMode = false
        movingComment = null
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        pan = if (scale <= 1f) Offset.Zero else pan + panChange
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Cerrar visor") }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "OT ${document.otNumber} · ${document.code}",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Rev ${document.revision} · ${document.workflowStatusLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showComments = true }) {
                                BadgedBox(
                                    badge = {
                                        if (comments.isNotEmpty()) Badge { Text(comments.size.toString()) }
                                    }
                                ) { Icon(Icons.Default.Comment, "Lista de observaciones") }
                            }
                            IconButton(onClick = { showHistory = true }) {
                                Icon(Icons.Default.History, "Historial")
                            }
                        }
                    }
                },
                bottomBar = {
                    CorporateViewerBottomBar(
                        document = document,
                        currentEmail = currentEmail,
                        page = page,
                        pageCount = pageCount,
                        scale = scale,
                        canComment = canComment,
                        addMode = addMode,
                        commentCount = comments.count { it.pageIndex == page },
                        onPrevious = { if (page > 0) page-- },
                        onNext = { if (page + 1 < pageCount) page++ },
                        onZoomOut = {
                            scale = (scale / 1.35f).coerceAtLeast(1f)
                            if (scale == 1f) pan = Offset.Zero
                        },
                        onZoomIn = { scale = (scale * 1.35f).coerceAtMost(6f) },
                        onReset = { scale = 1f; pan = Offset.Zero },
                        onToggleComment = {
                            addMode = !addMode
                            movingComment = null
                        },
                        onShowComments = { showComments = true },
                        onApprove = onApprove,
                        onRequestChanges = { showRequestChanges = true }
                    )
                }
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    Surface(
                        color = when {
                            addMode || movingComment != null -> SkmWarningSurface
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    movingComment != null -> Icons.Default.EditLocationAlt
                                    addMode -> Icons.Default.AddComment
                                    else -> Icons.Default.TouchApp
                                },
                                null,
                                Modifier.size(18.dp),
                                tint = if (addMode || movingComment != null) SkmWarning else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    movingComment != null -> "Toca el plano para reubicar la observación."
                                    addMode -> "Toca el punto del plano donde quieres escribir la observación."
                                    else -> "Pellizca para acercar y arrastra con dos dedos para desplazarte."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (addMode || movingComment != null) SkmGraphite else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(Color(0xFFCFD2D6))
                            .onGloballyPositioned { container = it.size }
                            .transformable(transformState)
                            .pointerInput(addMode, movingComment, scale, pan, container, page) {
                                detectTapGestures { tap ->
                                    if ((addMode || movingComment != null) && container.width > 0 && container.height > 0) {
                                        val logicalX = ((tap.x - pan.x) / scale).coerceIn(0f, container.width.toFloat())
                                        val logicalY = ((tap.y - pan.y) / scale).coerceIn(0f, container.height.toFloat())
                                        val normalizedX = (logicalX / container.width).coerceIn(0f, 0.90f)
                                        val normalizedY = (logicalY / container.height).coerceIn(0f, 0.92f)
                                        val moving = movingComment
                                        if (moving != null) {
                                            onUpdateComment(moving.copy(pageIndex = page, x = normalizedX, y = normalizedY))
                                            movingComment = null
                                        } else {
                                            draft = CorporateCommentDraft(page, normalizedX, normalizedY)
                                            addMode = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = pan.x
                                    translationY = pan.y
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                        ) {
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Hoja ${page + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

                            if (container.width > 0 && container.height > 0) {
                                comments.filter { it.pageIndex == page }.forEach { comment ->
                                    val widthDp = with(density) {
                                        (container.width * comment.width.coerceIn(0.22f, 0.62f)).toDp()
                                    }
                                    Card(
                                        onClick = { editing = comment },
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (comment.x * container.width).roundToInt(),
                                                    (comment.y * container.height).roundToInt()
                                                )
                                            }
                                            .width(widthDp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (comment.published) SkmOrangeLight.copy(alpha = 0.95f) else SkmWarningSurface.copy(alpha = 0.96f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (comment.published) Icons.Default.Visibility else Icons.Default.EditNote,
                                                    null,
                                                    Modifier.size(14.dp),
                                                    tint = if (comment.published) SkmOrangeDark else SkmWarning
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (comment.published) "PUBLICADA" else "BORRADOR",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SkmGraphite
                                                )
                                            }
                                            Text(comment.text, color = SkmGraphite, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                comment.authorName.ifBlank { comment.authorEmail },
                                                color = SkmTextSecondary,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    draft?.let { value ->
        CorporateCommentEditor(
            title = "Nueva observación · hoja ${value.pageIndex + 1}",
            initialText = "",
            initialWidth = value.width,
            published = false,
            canModify = true,
            onDismiss = { draft = null },
            onSave = { text, width ->
                draft = null
                onAddComment(value.pageIndex, text, value.x, value.y, width)
            },
            onPublish = null,
            onRelocate = null,
            onDelete = null
        )
    }

    editing?.let { comment ->
        val canModify = comment.canBeModifiedBy(currentEmail, isAdmin) && canComment
        CorporateCommentEditor(
            title = "Observación · hoja ${comment.pageIndex + 1}",
            initialText = comment.text,
            initialWidth = comment.width,
            published = comment.published,
            canModify = canModify,
            onDismiss = { editing = null },
            onSave = { text, width ->
                editing = null
                onUpdateComment(comment.copy(text = text, width = width))
            },
            onPublish = if (canModify && !comment.published) {
                {
                    editing = null
                    onPublishComment(comment)
                }
            } else null,
            onRelocate = if (canModify) {
                {
                    editing = null
                    movingComment = comment
                    addMode = false
                }
            } else null,
            onDelete = if (canModify) {
                {
                    editing = null
                    onDeleteComment(comment)
                }
            } else null
        )
    }

    if (showComments) {
        CorporateCommentsList(
            comments = comments,
            page = page,
            onDismiss = { showComments = false },
            onSelect = {
                showComments = false
                page = it.pageIndex.coerceIn(0, pageCount - 1)
                editing = it
            }
        )
    }

    if (showHistory) {
        CorporateHistoryDialog(timeline, onDismiss = { showHistory = false })
    }

    if (showRequestChanges) {
        CorporateRequestChangesDialog(
            onDismiss = { showRequestChanges = false },
            onConfirm = {
                showRequestChanges = false
                onRequestChanges(it)
            }
        )
    }
}

@Composable
private fun CorporateViewerBottomBar(
    document: DocumentRecord,
    currentEmail: String,
    page: Int,
    pageCount: Int,
    scale: Float,
    canComment: Boolean,
    addMode: Boolean,
    commentCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    onToggleComment: () -> Unit,
    onShowComments: () -> Unit,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onPrevious, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Hoja anterior") }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("Hoja ${page + 1} / $pageCount", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onNext, enabled = page + 1 < pageCount) { Icon(Icons.Default.ChevronRight, "Hoja siguiente") }
                VerticalDivider(Modifier.height(26.dp))
                IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, "Alejar") }
                Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, "Acercar") }
                IconButton(onClick = onReset) { Icon(Icons.Default.CenterFocusStrong, "Ajustar") }
                VerticalDivider(Modifier.height(26.dp))
                TextButton(onClick = onShowComments) {
                    Icon(Icons.Default.Comment, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Observaciones ($commentCount)")
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 500.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canComment && document.isUnderReview) {
                            OutlinedButton(
                                onClick = onToggleComment,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmOrangeDark)
                            ) {
                                Icon(if (addMode) Icons.Default.Close else Icons.Default.AddComment, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (addMode) "Cancelar observación" else "Nueva observación")
                            }
                        }
                        CorporateDecisionButtons(
                            document = document,
                            currentEmail = currentEmail,
                            stacked = true,
                            onApprove = onApprove,
                            onRequestChanges = onRequestChanges
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (canComment && document.isUnderReview) {
                            OutlinedButton(onClick = onToggleComment) {
                                Icon(if (addMode) Icons.Default.Close else Icons.Default.AddComment, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (addMode) "Cancelar" else "Nueva observación")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        CorporateDecisionButtons(
                            document = document,
                            currentEmail = currentEmail,
                            stacked = false,
                            onApprove = onApprove,
                            onRequestChanges = onRequestChanges
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CorporateDecisionButtons(
    document: DocumentRecord,
    currentEmail: String,
    stacked: Boolean,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    when {
        document.canBeSignedBy(currentEmail) -> {
            if (stacked) {
                OutlinedButton(
                    onClick = onRequestChanges,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)
                ) {
                    Icon(Icons.Default.RateReview, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Solicitar cambios")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)
                ) {
                    Icon(Icons.Default.Approval, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aprobar y firmar")
                }
            } else {
                OutlinedButton(onClick = onRequestChanges, colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)) {
                    Icon(Icons.Default.RateReview, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Solicitar cambios")
                }
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = SkmOrange)) {
                    Icon(Icons.Default.Approval, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Aprobar y firmar")
                }
            }
        }
        document.changesRequested -> {
            Surface(shape = RoundedCornerShape(12.dp), color = SkmDangerSurface) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = SkmDanger)
                    Spacer(Modifier.width(7.dp))
                    Text("Cambios solicitados", color = SkmDanger, fontWeight = FontWeight.Bold)
                }
            }
        }
        document.completed -> {
            Surface(shape = RoundedCornerShape(12.dp), color = SkmSuccessSurface) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = SkmSuccess)
                    Spacer(Modifier.width(7.dp))
                    Text("APTO PARA FABRICACIÓN", color = SkmSuccess, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> {
            Text("Turno: ${document.currentReviewerEmail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CorporateCommentEditor(
    title: String,
    initialText: String,
    initialWidth: Float,
    published: Boolean,
    canModify: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit,
    onPublish: (() -> Unit)?,
    onRelocate: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var text by rememberSaveable(title, initialText) { mutableStateOf(initialText) }
    var width by rememberSaveable(title, initialWidth) { mutableFloatStateOf(initialWidth) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (published) "Publicada para el proyecto" else "Borrador privado", style = MaterialTheme.typography.labelSmall, color = if (published) SkmOrangeDark else SkmWarning)
                            }
                            if (canModify && onDelete != null) {
                                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = SkmDanger) }
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                            if (!canModify) {
                                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Cerrar") }
                            } else if (maxWidth < 470.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onSave(text, width) },
                                        enabled = text.trim().isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                                    ) {
                                        Icon(Icons.Default.Save, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Guardar observación")
                                    }
                                    if (onPublish != null) {
                                        OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                            Icon(Icons.Default.Publish, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Publicar para todos")
                                        }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    if (onPublish != null) {
                                        OutlinedButton(onClick = onPublish) {
                                            Icon(Icons.Default.Publish, null)
                                            Spacer(Modifier.width(7.dp))
                                            Text("Publicar")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Button(onClick = { onSave(text, width) }, enabled = text.trim().isNotBlank()) {
                                        Icon(Icons.Default.Save, null)
                                        Spacer(Modifier.width(7.dp))
                                        Text("Guardar")
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (published) SkmOrangeLight else SkmWarningSurface
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (published) Icons.Default.Visibility else Icons.Default.EditNote, null, tint = if (published) SkmOrangeDark else SkmWarning)
                            Spacer(Modifier.width(8.dp))
                            Text(if (published) "Todos los participantes pueden verla." else "Solo tú puedes verla hasta publicarla.", color = SkmGraphite)
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (canModify && it.length <= 1200) text = it },
                        label = { Text("Escribe la observación") },
                        placeholder = { Text("Describe claramente el cambio, interferencia o antecedente que debe revisarse…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                        minLines = 7,
                        maxLines = 14,
                        readOnly = !canModify,
                        supportingText = { Text("${text.length}/1200 caracteres") }
                    )
                    if (canModify) {
                        Text("Ancho del cuadro en el plano: ${(width * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
                        Slider(value = width, onValueChange = { width = it }, valueRange = 0.22f..0.62f)
                        if (onRelocate != null) {
                            OutlinedButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(Icons.Default.EditLocationAlt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mover cuadro sobre el plano")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorporateCommentsList(
    comments: List<PlanComment>,
    page: Int,
    onDismiss: () -> Unit,
    onSelect: (PlanComment) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                        Column(Modifier.weight(1f)) {
                            Text("Observaciones", fontWeight = FontWeight.Bold)
                            Text("${comments.size} total · hoja actual ${page + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (comments.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(46.dp), tint = SkmTextMuted)
                                Spacer(Modifier.height(10.dp))
                                Text("Todavía no hay observaciones", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        items(comments.sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt }), key = { it.id }) { comment ->
                            Card(onClick = { onSelect(comment) }, shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (comment.published) SkmOrangeLight else SkmWarningSurface
                                        ) {
                                            Text(
                                                if (comment.published) "PUBLICADA" else "BORRADOR",
                                                Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                color = SkmGraphite,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Text("Hoja ${comment.pageIndex + 1}", fontWeight = FontWeight.Bold)
                                    }
                                    Text(comment.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${comment.authorName.ifBlank { comment.authorEmail }} · ${corporateViewerDate(comment.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
private fun CorporateRequestChangesDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                        Text("Solicitar cambios", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onConfirm(reason.trim()) },
                                enabled = reason.trim().length >= 5,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SkmDanger)
                            ) {
                                Icon(Icons.Default.PauseCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Detener revisión y solicitar cambios")
                            }
                            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = SkmDangerSurface) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.WarningAmber, null, tint = SkmDanger)
                            Text("La revisión quedará detenida hasta que el administrador cargue una nueva revisión.", color = SkmDanger)
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { if (it.length <= 1200) reason = it },
                        label = { Text("Motivo y cambios requeridos") },
                        placeholder = { Text("Indica con precisión qué debe corregirse antes de volver a revisar…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
                        minLines = 8,
                        maxLines = 16,
                        supportingText = { Text("${reason.length}/1200 caracteres") }
                    )
                }
            }
        }
    }
}

@Composable
private fun CorporateHistoryDialog(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                        Text("Historial de revisión", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            ) { padding ->
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (timeline.isEmpty()) {
                        item { Text("No hay actividades registradas.", Modifier.padding(24.dp)) }
                    } else {
                        items(timeline, key = { it.id }) { event ->
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = SkmOrangeLight, modifier = Modifier.size(42.dp)) {
                                        Icon(corporateEventIcon(event.type), null, Modifier.padding(9.dp), tint = SkmOrangeDark)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(corporateEventTitle(event.type), fontWeight = FontWeight.Bold)
                                        if (event.detail.isNotBlank()) Text(event.detail)
                                        Text(
                                            "${event.actorName.ifBlank { event.actorEmail }} · ${corporateViewerDate(event.createdAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun corporateEventIcon(type: WorkflowEventType) = when (type) {
    WorkflowEventType.UPLOADED -> Icons.Default.UploadFile
    WorkflowEventType.COMMENT_DRAFTED -> Icons.Default.EditNote
    WorkflowEventType.COMMENT_PUBLISHED -> Icons.Default.Comment
    WorkflowEventType.APPROVED -> Icons.Default.Approval
    WorkflowEventType.CHANGES_REQUESTED -> Icons.Default.RateReview
    WorkflowEventType.COMPLETED -> Icons.Default.Verified
}

private fun corporateEventTitle(type: WorkflowEventType): String = when (type) {
    WorkflowEventType.UPLOADED -> "Revisión iniciada"
    WorkflowEventType.COMMENT_DRAFTED -> "Borrador creado"
    WorkflowEventType.COMMENT_PUBLISHED -> "Observación publicada"
    WorkflowEventType.APPROVED -> "Aprobación registrada"
    WorkflowEventType.CHANGES_REQUESTED -> "Cambios solicitados"
    WorkflowEventType.COMPLETED -> "Flujo completado"
}

private suspend fun corporateRenderPage(file: File, index: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val renderScale = 2.0f
                val bitmap = Bitmap.createBitmap(
                    (page.width * renderScale).roundToInt(),
                    (page.height * renderScale).roundToInt(),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap to renderer.pageCount
            }
        }
    }
}

private fun corporateViewerDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
