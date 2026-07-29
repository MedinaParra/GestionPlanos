package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.document.model.DocumentRecord
import com.example.document.model.PlanComment
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EnhancedDocumentApp(
    state: DocumentUiState,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onPrepareSignature: (DocumentRecord) -> Unit,
    onRequestSignature: (DocumentRecord, SignaturePlacement) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit,
    onSignOut: () -> Unit,
    onClosePdf: () -> Unit,
    onCancelSignaturePlacement: () -> Unit,
    onClearFeedback: () -> Unit,
    onAddComment: (DocumentRecord, Int, String, Float, Float, Float) -> Unit,
    onUpdateComment: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit
) {
    DocumentApp(
        state = state.copy(previewDocument = null, previewFile = null),
        onConnectDrive = onConnectDrive,
        onRefresh = onRefresh,
        onUploadPdf = onUploadPdf,
        onOpenPdf = onOpenPdf,
        onPrepareSignature = onPrepareSignature,
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
        EnhancedPdfDialog(
            file = file,
            document = document,
            comments = state.previewComments,
            currentEmail = session.email,
            isAdmin = session.isAdmin,
            canComment = state.configuration.canEdit && session.profile.active,
            onClose = onClosePdf,
            onAddComment = { page, text, x, y, width ->
                onAddComment(document, page, text, x, y, width)
            },
            onUpdateComment = onUpdateComment,
            onDeleteComment = onDeleteComment
        )
    }
}

private data class CommentDraft(
    val existing: PlanComment? = null,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 0.36f
)

@Composable
private fun EnhancedPdfDialog(
    file: File,
    document: DocumentRecord,
    comments: List<PlanComment>,
    currentEmail: String,
    isAdmin: Boolean,
    canComment: Boolean,
    onClose: () -> Unit,
    onAddComment: (Int, String, Float, Float, Float) -> Unit,
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
    val density = LocalDensity.current

    LaunchedEffect(file, page) {
        val rendered = renderEnhancedPage(file, page)
        bitmap = rendered.first
        count = rendered.second
        scale = 1f
        pan = Offset.Zero
        addMode = false
        movingComment = null
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        pan += panChange
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxSize().padding(6.dp)) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${document.code} · Rev ${document.revision}", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                movingComment != null -> "Toca la nueva ubicación del comentario."
                                addMode -> "Toca el plano donde quieres agregar el comentario."
                                else -> "Pellizca para acercar o alejar; arrastra con dos dedos para desplazarte."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { scale = (scale / 1.35f).coerceAtLeast(1f); if (scale == 1f) pan = Offset.Zero }) {
                        Icon(Icons.Default.ZoomOut, "Alejar")
                    }
                    Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { scale = (scale * 1.35f).coerceAtMost(6f) }) {
                        Icon(Icons.Default.ZoomIn, "Acercar")
                    }
                    IconButton(onClick = { scale = 1f; pan = Offset.Zero }) {
                        Icon(Icons.Default.RestartAlt, "Restablecer zoom")
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canComment) {
                        Button(
                            onClick = {
                                addMode = !addMode
                                movingComment = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddComment, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (addMode) "Cancelar comentario" else "Agregar comentario")
                        }
                    }
                    Text(
                        "${comments.count { it.pageIndex == page }} comentario(s) en esta hoja",
                        modifier = Modifier.align(Alignment.CenterVertically),
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
                                        draft = CommentDraft(pageIndex = page, x = normalizedX, y = normalizedY)
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
                                contentDescription = "Hoja ${page + 1} del plano",
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
                                    Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(comment.text, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${comment.authorName.ifBlank { comment.authorEmail }} · ${formatCommentDate(comment.createdAt)}",
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
                    OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
                    Text("${page + 1} / $count")
                    OutlinedButton(onClick = { if (page + 1 < count) page++ }, enabled = page + 1 < count) { Text("Siguiente") }
                }
            }
        }
    }

    draft?.let { value ->
        CommentEditorDialog(
            title = "Nuevo comentario · hoja ${value.pageIndex + 1}",
            initialText = "",
            initialWidth = value.width,
            canDelete = false,
            onDismiss = { draft = null },
            onSave = { text, width ->
                draft = null
                onAddComment(value.pageIndex, text, value.x, value.y, width)
            },
            onRelocate = null,
            onDelete = null
        )
    }

    editing?.let { comment ->
        val canModify = comment.canBeModifiedBy(currentEmail, isAdmin) && canComment
        CommentEditorDialog(
            title = "Comentario · hoja ${comment.pageIndex + 1}",
            initialText = comment.text,
            initialWidth = comment.width,
            canDelete = canModify,
            readOnly = !canModify,
            onDismiss = { editing = null },
            onSave = { text, width ->
                editing = null
                onUpdateComment(comment.copy(text = text, width = width))
            },
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
}

@Composable
private fun CommentEditorDialog(
    title: String,
    initialText: String,
    initialWidth: Float,
    canDelete: Boolean,
    readOnly: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, Float) -> Unit,
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1200) text = it },
                    label = { Text("Comentario") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    readOnly = readOnly,
                    supportingText = { Text("${text.length}/1200") }
                )
                if (!readOnly) {
                    Text("Ancho del cuadro: ${(width * 100).roundToInt()}%")
                    Slider(value = width, onValueChange = { width = it }, valueRange = 0.22f..0.62f)
                    if (onRelocate != null) {
                        OutlinedButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.EditLocationAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reubicar en el plano")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (readOnly) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            } else {
                Button(onClick = { onSave(text, width) }, enabled = text.trim().isNotBlank()) { Text("Guardar") }
            }
        },
        dismissButton = {
            Row {
                if (canDelete && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }
                if (!readOnly) TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

private suspend fun renderEnhancedPage(file: File, index: Int): Pair<Bitmap, Int> =
    withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = 2.0f
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).roundToInt(),
                        (page.height * scale).roundToInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap to renderer.pageCount
                }
            }
        }
    }

private fun formatCommentDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin fecha"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
