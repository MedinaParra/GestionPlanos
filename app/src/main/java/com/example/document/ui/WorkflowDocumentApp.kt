package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.document.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WorkflowDocumentApp(
    state: DocumentUiState,
    timeline: List<WorkflowEvent>,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onPrepareSignature: (DocumentRecord) -> Unit,
    onRequestSignature: (DocumentRecord, SignaturePlacement) -> Unit,
    onRequestChanges: (DocumentRecord, String) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit,
    onSignOut: () -> Unit,
    onClosePdf: () -> Unit,
    onCancelSignaturePlacement: () -> Unit,
    onClearFeedback: () -> Unit,
    onAddComment: (DocumentRecord, Int, String, Float, Float, Float) -> Unit,
    onPublishComment: (PlanComment) -> Unit,
    onUpdateComment: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit
) {
    DocumentApp(
        state = state.copy(previewDocument = null, previewFile = null),
        onConnectDrive = onConnectDrive,
        onRefresh = onRefresh,
        onUploadPdf = onUploadPdf,
        onOpenPdf = onOpenPdf,
        onPrepareSignature = onOpenPdf,
        onRequestSignature = onRequestSignature,
        onConfigureDrive = onConfigureDrive,
        onSaveProfile = onSaveProfile,
        onUpdateUser = onUpdateUser,
        onUpdateSettings = onUpdateSettings,
        onSignOut = onSignOut,
        onClosePdf = onClosePdf,
        onCancelSignaturePlacement = onCancelSignaturePlacement,
        onClearFeedback = onClearFeedback
    )

    val document = state.previewDocument
    val file = state.previewFile
    val session = state.session
    if (document != null && file != null && session != null) {
        WorkflowPdfDialog(
            file = file,
            document = document,
            comments = state.previewComments,
            timeline = timeline,
            currentEmail = session.email,
            isAdmin = session.isAdmin,
            canComment = state.configuration.canEdit && session.profile.active,
            onClose = onClosePdf,
            onApprove = { onPrepareSignature(document) },
            onRequestChanges = { reason -> onRequestChanges(document, reason) },
            onAddComment = { page, text, x, y, width ->
                onAddComment(document, page, text, x, y, width)
            },
            onPublishComment = onPublishComment,
            onUpdateComment = onUpdateComment,
            onDeleteComment = onDeleteComment
        )
    }
}

private data class CommentDraft(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.36f
)

@Composable
private fun WorkflowPdfDialog(
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
    var page by rememberSaveable(file.path) { mutableStateOf(0) }
    var count by remember(file) { mutableStateOf(1) }
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(file, page) { mutableFloatStateOf(1f) }
    var pan by remember(file, page) { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var addMode by remember { mutableStateOf(false) }
    var movingComment by remember { mutableStateOf<PlanComment?>(null) }
    var draft by remember { mutableStateOf<CommentDraft?>(null) }
    var editing by remember { mutableStateOf<PlanComment?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showRequestChanges by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(file, page) {
        val rendered = renderWorkflowPage(file, page)
        bitmap = rendered.first
        count = rendered.second
        scale = 1f
        pan = Offset.Zero
        addMode = false
        movingComment = null
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        pan += panChange
        if (scale == 1f) pan = Offset.Zero
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxSize().padding(6.dp)) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("OT ${document.otNumber} · ${document.code} · Rev ${document.revision}", fontWeight = FontWeight.Bold)
                        Text(document.workflowStatusLabel, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, "Historial")
                    }
                    IconButton(onClick = {
                        scale = (scale / 1.35f).coerceAtLeast(1f)
                        if (scale == 1f) pan = Offset.Zero
                    }) { Icon(Icons.Default.ZoomOut, "Alejar") }
                    Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { scale = (scale * 1.35f).coerceAtMost(6f) }) {
                        Icon(Icons.Default.ZoomIn, "Acercar")
                    }
                    IconButton(onClick = { scale = 1f; pan = Offset.Zero }) {
                        Icon(Icons.Default.RestartAlt, "Restablecer")
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
                }

                Text(
                    when {
                        movingComment != null -> "Toca la nueva ubicación del comentario."
                        addMode -> "Toca el plano donde quieres crear el borrador."
                        else -> "Pellizca para acercar o alejar; arrastra con dos dedos para desplazarte."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canComment && document.isUnderReview) {
                        Button(
                            onClick = {
                                addMode = !addMode
                                movingComment = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddComment, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (addMode) "Cancelar" else "Crear observación")
                        }
                    }
                    val pageComments = comments.filter { it.pageIndex == page }
                    Text(
                        "${pageComments.count { it.published }} publicadas · ${pageComments.count { !it.published }} borrador(es)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onGloballyPositioned { container = it.size }
                        .transformable(transformState)
                        .pointerInput(addMode, movingComment, scale, pan, container) {
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
                                        draft = CommentDraft(page, normalizedX, normalizedY)
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
                                it.asImageBitmap(),
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
                                OutlinedCard(
                                    onClick = { editing = comment },
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (comment.x * container.width).roundToInt(),
                                                (comment.y * container.height).roundToInt()
                                            )
                                        }
                                        .width(widthDp)
                                ) {
                                    Column(
                                        Modifier.padding(7.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (comment.published) Icons.Default.Visibility else Icons.Default.EditNote,
                                                null,
                                                Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (comment.published) "PUBLICADO" else "BORRADOR",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(comment.text, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${comment.authorName.ifBlank { comment.authorEmail }} · ${formatWorkflowDate(comment.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) {
                        Text("Anterior")
                    }
                    Text("${page + 1} / $count")
                    OutlinedButton(onClick = { if (page + 1 < count) page++ }, enabled = page + 1 < count) {
                        Text("Siguiente")
                    }
                }

                WorkflowDecisionBar(
                    document = document,
                    currentEmail = currentEmail,
                    onApprove = onApprove,
                    onRequestChanges = { showRequestChanges = true }
                )
            }
        }
    }

    draft?.let { value ->
        CommentEditorDialog(
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
        CommentEditorDialog(
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

    if (showHistory) {
        HistoryDialog(timeline = timeline, onDismiss = { showHistory = false })
    }

    if (showRequestChanges) {
        RequestChangesDialog(
            onDismiss = { showRequestChanges = false },
            onConfirm = { reason ->
                showRequestChanges = false
                onRequestChanges(reason)
            }
        )
    }
}

@Composable
private fun WorkflowDecisionBar(
    document: DocumentRecord,
    currentEmail: String,
    onApprove: () -> Unit,
    onRequestChanges: () -> Unit
) {
    when {
        document.canBeSignedBy(currentEmail) -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRequestChanges, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RateReview, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Solicitar cambios")
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Approval, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aprobar y firmar")
                }
            }
        }
        document.changesRequested -> {
            AssistChip(
                onClick = {},
                label = { Text("Cambios solicitados: espera una nueva revisión") },
                leadingIcon = { Icon(Icons.Default.WarningAmber, null) }
            )
        }
        document.completed -> {
            AssistChip(
                onClick = {},
                label = { Text("Flujo cerrado: APTO PARA FABRICACIÓN") },
                leadingIcon = { Icon(Icons.Default.Verified, null) }
            )
        }
        document.isUnderReview -> {
            Text(
                "Turno actual: ${document.currentReviewerEmail}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CommentEditorDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (published) "Publicado para el proyecto" else "Borrador privado") },
                    leadingIcon = {
                        Icon(if (published) Icons.Default.Visibility else Icons.Default.EditNote, null)
                    }
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (canModify && it.length <= 1200) text = it },
                    label = { Text("Observación") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    readOnly = !canModify,
                    supportingText = { Text("${text.length}/1200") }
                )
                if (canModify) {
                    Text("Ancho del cuadro: ${(width * 100).roundToInt()}%")
                    Slider(value = width, onValueChange = { width = it }, valueRange = 0.22f..0.62f)
                    if (onRelocate != null) {
                        OutlinedButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.EditLocationAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reubicar")
                        }
                    }
                    if (onPublish != null) {
                        Button(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Publish, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Publicar para todos")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canModify) {
                Button(onClick = { onSave(text, width) }, enabled = text.trim().isNotBlank()) {
                    Text("Guardar")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            Row {
                if (canModify && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }
                if (canModify) TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun RequestChangesDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar cambios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La revisión quedará detenida hasta que el administrador cargue una nueva revisión.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 1200) reason = it },
                    label = { Text("Motivo y cambios requeridos") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${reason.length}/1200") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason.trim()) }, enabled = reason.trim().length >= 5) {
                Text("Detener revisión")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun HistoryDialog(timeline: List<WorkflowEvent>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 720.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Historial de revisión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                if (timeline.isEmpty()) {
                    Text("No hay actividades registradas.")
                } else {
                    timeline.forEach { event ->
                        OutlinedCard {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(eventIcon(event.type), null)
                                Column(Modifier.weight(1f)) {
                                    Text(eventTitle(event.type), fontWeight = FontWeight.Bold)
                                    Text(event.detail)
                                    Text(
                                        "${event.actorName.ifBlank { event.actorEmail }} · ${formatWorkflowDate(event.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall
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

private fun eventIcon(type: WorkflowEventType) = when (type) {
    WorkflowEventType.UPLOADED -> Icons.Default.UploadFile
    WorkflowEventType.COMMENT_DRAFTED -> Icons.Default.EditNote
    WorkflowEventType.COMMENT_PUBLISHED -> Icons.Default.Comment
    WorkflowEventType.APPROVED -> Icons.Default.Approval
    WorkflowEventType.CHANGES_REQUESTED -> Icons.Default.RateReview
    WorkflowEventType.COMPLETED -> Icons.Default.Verified
}

private fun eventTitle(type: WorkflowEventType): String = when (type) {
    WorkflowEventType.UPLOADED -> "Revisión iniciada"
    WorkflowEventType.COMMENT_DRAFTED -> "Borrador creado"
    WorkflowEventType.COMMENT_PUBLISHED -> "Observación publicada"
    WorkflowEventType.APPROVED -> "Aprobación registrada"
    WorkflowEventType.CHANGES_REQUESTED -> "Cambios solicitados"
    WorkflowEventType.COMPLETED -> "Flujo completado"
}

private suspend fun renderWorkflowPage(file: File, index: Int): Pair<Bitmap, Int> =
    withContext(Dispatchers.IO) {
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

private fun formatWorkflowDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
