package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.SessionUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentApp(
    state: DocumentUiState,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onToggleSigned: (DocumentRecord) -> Unit,
    onUpdateRevision: (DocumentRecord, String) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onClosePdf: () -> Unit,
    onClearFeedback: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val feedback = state.error ?: state.message
    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback)
            onClearFeedback()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (state.session == null) {
            LoginScreen(
                modifier = Modifier.padding(padding),
                busy = state.busy,
                onConnectDrive = onConnectDrive
            )
        } else {
            DashboardScreen(
                modifier = Modifier.padding(padding),
                state = state,
                onConnectDrive = onConnectDrive,
                onRefresh = onRefresh,
                onUploadPdf = onUploadPdf,
                onOpenPdf = onOpenPdf,
                onToggleSigned = onToggleSigned,
                onUpdateRevision = onUpdateRevision,
                onConfigureDrive = onConfigureDrive,
                onSignOut = onSignOut
            )
        }
    }

    val previewFile = state.previewFile
    val previewDocument = state.previewDocument
    if (previewFile != null && previewDocument != null) {
        PdfPreviewDialog(previewFile, previewDocument, onClosePdf)
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    busy: Boolean,
    onConnectDrive: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    Icons.Default.FolderCopy,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Gestión de Planos SKM",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "La aplicación trabaja directamente con tu Google Drive. No requiere Firebase, servidor ni cuentas internas.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = onConnectDrive,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Conectar mi Google Drive")
                }
                Text(
                    "Se solicitará permiso de lectura y escritura para crear, abrir, renombrar y actualizar archivos en las carpetas que puedas usar.",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                Text(
                    "La app creará en tu Drive una carpeta privada llamada GestionPlanosSKM-Privado. Allí guardará solamente IDs y configuración; no guardará contraseñas en texto visible.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(
    modifier: Modifier,
    state: DocumentUiState,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onToggleSigned: (DocumentRecord) -> Unit,
    onUpdateRevision: (DocumentRecord, String) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val session = requireNotNull(state.session)
    var showUpload by rememberSaveable { mutableStateOf(false) }
    var showFolder by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión de Planos", fontWeight = FontWeight.Bold)
                        Text(
                            "${session.displayName} · ${if (session.canEdit) "Lectura y escritura" else "Solo lectura"}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ConnectionCard(
                        session = session,
                        configuration = state.configuration,
                        driveConnected = state.driveConnected,
                        onReconnect = onConnectDrive,
                        onConfigure = { showFolder = true }
                    )
                }
                item { SummaryRow(state.documents) }
                if (session.canEdit && state.configuration.isConfigured) {
                    item {
                        Button(
                            onClick = { showUpload = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar PDF a Drive")
                        }
                    }
                }
                item {
                    Text(
                        "Planos (${state.documents.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.documents.isEmpty()) {
                    item { EmptyDocumentsCard(state.configuration.isConfigured, session.canEdit) }
                } else {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            canEdit = session.canEdit,
                            onOpen = { onOpenPdf(document) },
                            onToggleSigned = { onToggleSigned(document) },
                            onUpdateRevision = { onUpdateRevision(document, it) }
                        )
                    }
                }
            }
        }
    }

    if (showUpload) {
        UploadPdfDialog(
            onDismiss = { showUpload = false },
            onUpload = { uri, code, revision ->
                showUpload = false
                onUploadPdf(uri, code, revision)
            }
        )
    }

    if (showFolder) {
        FolderDialog(
            configuration = state.configuration,
            onDismiss = { showFolder = false },
            onSave = { link, name ->
                showFolder = false
                onConfigureDrive(link, name)
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    session: SessionUser,
    configuration: DriveConfiguration,
    driveConnected: Boolean,
    onReconnect: () -> Unit,
    onConfigure: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (configuration.isConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (configuration.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        configuration.folderName.ifBlank { "Carpeta compartida no configurada" },
                        fontWeight = FontWeight.Bold
                    )
                    Text(session.email, style = MaterialTheme.typography.bodySmall)
                    if (configuration.privateFolderId.isNotBlank()) {
                        Text("Configuración privada activa en tu Drive", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onConfigure, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Elegir carpeta")
                }
                OutlinedButton(onClick = onReconnect, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (driveConnected) "Reautorizar" else "Conectar")
                }
            }
            Text(
                if (configuration.canEdit) {
                    "Drive confirmó que puedes leer, crear y modificar archivos en esta carpeta."
                } else if (configuration.isConfigured) {
                    "Drive confirmó acceso de solo lectura. La app ocultará las acciones de edición y firma."
                } else {
                    "Pega el enlace de la carpeta que crearás y compartirás directamente desde Google Drive."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SummaryRow(documents: List<DocumentRecord>) {
    val signed = documents.count { it.signed }
    val pending = documents.size - signed
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryCard("Total", documents.size.toString(), Icons.Default.Description, Modifier.weight(1f))
        SummaryCard("Firmados", signed.toString(), Icons.Default.TaskAlt, Modifier.weight(1f))
        SummaryCard("Pendientes", pending.toString(), Icons.Default.PendingActions, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyDocumentsCard(configured: Boolean, canEdit: Boolean) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(44.dp))
            Text("No hay planos registrados", fontWeight = FontWeight.Bold)
            Text(
                when {
                    !configured -> "Primero configura la carpeta compartida de Drive."
                    canEdit -> "Agrega el primer PDF."
                    else -> "El propietario todavía no ha registrado documentos en esta carpeta."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentRecord,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onToggleSigned: () -> Unit,
    onUpdateRevision: (String) -> Unit
) {
    var showRevision by rememberSaveable { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(document.code, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(document.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                AssistChip(onClick = {}, label = { Text("Rev. ${document.revision}") })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (document.signed) Icons.Default.CheckCircle else Icons.Default.PendingActions,
                    contentDescription = null,
                    tint = if (document.signed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(document.status, fontWeight = FontWeight.SemiBold)
                    if (document.signed) {
                        Text("${document.signedByName} · ${formatTimestamp(document.signedAt)}", style = MaterialTheme.typography.bodySmall)
                        Text(document.signatureMethod, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir")
                }
                if (canEdit) {
                    OutlinedButton(onClick = { showRevision = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Revisión")
                    }
                }
            }
            if (canEdit) {
                Button(onClick = onToggleSigned, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (document.signed) "Quitar firma con huella o PIN" else "Firmar con huella o PIN")
                }
            }
        }
    }

    if (showRevision) {
        RevisionDialog(
            current = document.revision,
            onDismiss = { showRevision = false },
            onSave = {
                showRevision = false
                onUpdateRevision(it)
            }
        )
    }
}

@Composable
private fun FolderDialog(
    configuration: DriveConfiguration,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var link by rememberSaveable { mutableStateOf(configuration.folderId) }
    var name by rememberSaveable { mutableStateOf(configuration.folderName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carpeta compartida de Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Crea la carpeta en Drive, comparte allí los permisos de lectura o escritura y pega su enlace en la app.")
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Enlace o ID de la carpeta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre visible opcional") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Dentro de la carpeta se crearán control-documental.json y la planilla Control de Documentos SKM. Los lectores podrán abrir los PDF y ver el estado de firma.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(link, name) }, enabled = link.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun UploadPdfDialog(
    onDismiss: () -> Unit,
    onUpload: (Uri, String, String) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf("A") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        selectedName = uri?.lastPathSegment.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar plano PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código del plano") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = revision, onValueChange = { revision = it }, label = { Text("Revisión") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedName.isBlank()) "Seleccionar PDF" else selectedName)
                }
            }
        },
        confirmButton = {
            Button(onClick = { selectedUri?.let { onUpload(it, code, revision) } }, enabled = selectedUri != null && code.isNotBlank()) {
                Text("Subir")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RevisionDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var revision by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar revisión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = revision, onValueChange = { revision = it }, label = { Text("Nueva revisión") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("La app actualizará el índice, la planilla y renombrará el PDF en Google Drive.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(revision) }, enabled = revision.isNotBlank()) { Text("Actualizar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private data class RenderedPage(val image: ImageBitmap, val pageCount: Int)

@Composable
private fun PdfPreviewDialog(file: File, document: DocumentRecord, onClose: () -> Unit) {
    var pageIndex by rememberSaveable(file.absolutePath) { mutableIntStateOf(0) }
    val rendered by produceState<RenderedPage?>(null, file.absolutePath, pageIndex) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(document.code, fontWeight = FontWeight.Bold)
                        Text(document.fileName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClose) { Text("Cerrar") }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    val page = rendered
                    if (page == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = page.image,
                            contentDescription = "Página ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                val count = rendered?.pageCount ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Página anterior")
                    }
                    Text("Página ${pageIndex + 1} de $count")
                    IconButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < count) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Página siguiente")
                    }
                }
            }
        }
    }
}

private fun renderPdfPage(file: File, requestedPage: Int): RenderedPage {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val index = requestedPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            renderer.openPage(index).use { page ->
                val scale = 2f
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return RenderedPage(bitmap.asImageBitmap(), renderer.pageCount)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
