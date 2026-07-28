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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.SessionUser
import com.example.document.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentApp(
    state: DocumentUiState,
    onGoogleSignIn: () -> Unit,
    onViewerSignIn: (String, String) -> Unit,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onToggleSigned: (DocumentRecord) -> Unit,
    onUpdateRevision: (DocumentRecord, String) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onCreateViewer: (String, String, String) -> Unit,
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

    if (state.initialLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (state.session == null) {
            LoginScreen(
                modifier = Modifier.padding(padding),
                busy = state.busy,
                onGoogleSignIn = onGoogleSignIn,
                onViewerSignIn = onViewerSignIn
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
                onCreateViewer = onCreateViewer,
                onSignOut = onSignOut
            )
        }
    }

    val previewFile = state.previewFile
    val previewDocument = state.previewDocument
    if (previewFile != null && previewDocument != null) {
        PdfPreviewDialog(
            file = previewFile,
            document = previewDocument,
            onClose = onClosePdf
        )
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    busy: Boolean,
    onGoogleSignIn: () -> Unit,
    onViewerSignIn: (String, String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
                    imageVector = Icons.Default.FolderCopy,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Gestión de Planos SKM", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Control de revisiones, firmas y PDF conectado a Google Drive.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = onGoogleSignIn,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Ingresar con Google corporativo")
                }
                Text(
                    "Solo cuentas @skmindustrial.cl. La primera cuenta corporativa registrada queda como administradora.",
                    style = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()
                Text("Acceso de solo lectura", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onViewerSignIn(username, password) },
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entrar como visualizador")
                }
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
    onCreateViewer: (String, String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val session = requireNotNull(state.session)
    var showUpload by rememberSaveable { mutableStateOf(false) }
    var showAdmin by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestión de Planos", fontWeight = FontWeight.Bold)
                        Text(
                            "${session.displayName} · ${roleLabel(session.role)}",
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
                        onConnectDrive = onConnectDrive
                    )
                }
                item {
                    SummaryRow(state.documents)
                }
                if (session.canEdit) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showUpload = true },
                                enabled = state.driveConnected && state.configuration.isConfigured,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Agregar PDF")
                            }
                            if (session.isAdmin) {
                                OutlinedButton(
                                    onClick = { showAdmin = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Administrar")
                                }
                            }
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
                    item {
                        EmptyDocumentsCard(session.canEdit)
                    }
                } else {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            canEdit = session.canEdit,
                            driveConnected = state.driveConnected,
                            onOpen = { onOpenPdf(document) },
                            onToggleSigned = { onToggleSigned(document) },
                            onUpdateRevision = { revision -> onUpdateRevision(document, revision) }
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

    if (showAdmin) {
        AdminDialog(
            configuration = state.configuration,
            driveConnected = state.driveConnected,
            onDismiss = { showAdmin = false },
            onConfigureDrive = onConfigureDrive,
            onCreateViewer = onCreateViewer
        )
    }
}

@Composable
private fun ConnectionCard(
    session: SessionUser,
    configuration: DriveConfiguration,
    driveConnected: Boolean,
    onConnectDrive: () -> Unit
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
                        if (configuration.isConfigured) configuration.folderName else "Drive aún no configurado",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (configuration.isConfigured) "Planilla: ${configuration.spreadsheetName}" else "El administrador debe indicar la carpeta de trabajo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (session.canEdit) {
                AssistChip(
                    onClick = onConnectDrive,
                    label = { Text(if (driveConnected) "Drive conectado" else "Conectar Google Drive") },
                    leadingIcon = {
                        Icon(
                            if (driveConnected) Icons.Default.CheckCircle else Icons.Default.Link,
                            contentDescription = null
                        )
                    }
                )
                Text(
                    "La autorización de Drive se solicita por sesión y queda restringida al dominio corporativo.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "Modo visualizador: los PDF se abren desde una copia protegida, sin permisos para modificar Drive.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
private fun SummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyDocumentsCard(canEdit: Boolean) {
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
                if (canEdit) "Conecte Drive y agregue el primer PDF." else "El administrador todavía no ha cargado documentos.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentRecord,
    canEdit: Boolean,
    driveConnected: Boolean,
    onOpen: () -> Unit,
    onToggleSigned: () -> Unit,
    onUpdateRevision: (String) -> Unit
) {
    var showRevisionDialog by rememberSaveable { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(document.code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(document.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text("Rev. ${document.revision}") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (document.signed) Icons.Default.Verified else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (document.signed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    if (document.signed) "Firmado por ${document.signedByName}" else "Pendiente de firma",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Actualizado: ${formatDate(document.updatedAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ver PDF")
                }
                if (canEdit) {
                    OutlinedButton(onClick = { showRevisionDialog = true }, enabled = driveConnected) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Revisión")
                    }
                    FilledTonalButton(onClick = onToggleSigned, enabled = driveConnected) {
                        Icon(if (document.signed) Icons.Default.Undo else Icons.Default.Draw, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (document.signed) "Desmarcar" else "Firmado")
                    }
                }
            }
        }
    }

    if (showRevisionDialog) {
        RevisionDialog(
            currentRevision = document.revision,
            onDismiss = { showRevisionDialog = false },
            onSave = {
                showRevisionDialog = false
                onUpdateRevision(it)
            }
        )
    }
}

@Composable
private fun UploadPdfDialog(
    onDismiss: () -> Unit,
    onUpload: (Uri, String, String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf("A") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
        title = { Text("Agregar plano PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Codificación del plano") },
                    placeholder = { Text("Ej.: SKM-1634-01-02") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = revision,
                    onValueChange = { revision = it.uppercase() },
                    label = { Text("Revisión") },
                    singleLine = true
                )
                OutlinedButton(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) "Seleccionar PDF" else "PDF seleccionado")
                }
                selectedUri?.let {
                    Text(it.lastPathSegment.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUri?.let { onUpload(it, code, revision) } },
                enabled = selectedUri != null && code.isNotBlank()
            ) { Text("Cargar y registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RevisionDialog(
    currentRevision: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var revision by rememberSaveable(currentRevision) { mutableStateOf(currentRevision) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar revisión") },
        text = {
            OutlinedTextField(
                value = revision,
                onValueChange = { revision = it.uppercase() },
                label = { Text("Nueva revisión") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSave(revision) }, enabled = revision.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AdminDialog(
    configuration: DriveConfiguration,
    driveConnected: Boolean,
    onDismiss: () -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onCreateViewer: (String, String, String) -> Unit
) {
    var folderInput by rememberSaveable(configuration.folderId) {
        mutableStateOf(
            configuration.folderId.takeIf { it.isNotBlank() }
                ?.let { "https://drive.google.com/drive/folders/$it" }
                .orEmpty()
        )
    }
    var folderName by rememberSaveable(configuration.folderName) { mutableStateOf(configuration.folderName) }
    var viewerName by rememberSaveable { mutableStateOf("") }
    var viewerUsername by rememberSaveable { mutableStateOf("") }
    var viewerPassword by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Panel administrador", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Carpeta oficial de Google Drive", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    label = { Text("Enlace o ID de carpeta") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nombre visible") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onConfigureDrive(folderInput, folderName) },
                    enabled = driveConnected && folderInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar carpeta y crear planilla")
                }
                if (!driveConnected) {
                    Text("Conecte Drive antes de modificar la carpeta.", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()
                Text("Crear visualizador", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = viewerName,
                    onValueChange = { viewerName = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewerUsername,
                    onValueChange = { viewerUsername = it.lowercase() },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewerPassword,
                    onValueChange = { viewerPassword = it },
                    label = { Text("Contraseña inicial") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onCreateViewer(viewerUsername, viewerPassword, viewerName) },
                    enabled = viewerUsername.isNotBlank() && viewerPassword.length >= 8,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Crear acceso de solo lectura")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
            }
        }
    }
}

private data class PdfPageData(val image: ImageBitmap, val pageCount: Int)

@Composable
private fun PdfPreviewDialog(
    file: File,
    document: DocumentRecord,
    onClose: () -> Unit
) {
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    val pageData by produceState<PdfPageData?>(initialValue = null, file, pageIndex) {
        value = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val safeIndex = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safeIndex).use { page ->
                        val targetWidth = 1600
                        val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                            .coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPageData(bitmap.asImageBitmap(), renderer.pageCount)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                    Column(Modifier.weight(1f)) {
                        Text(document.code, fontWeight = FontWeight.Bold)
                        Text(document.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Rev. ${document.revision}")
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val data = pageData
                    if (data == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = data.image,
                            contentDescription = "Página ${pageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                val count = pageData?.pageCount ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior")
                    }
                    Text("Página ${pageIndex + 1} de ${count.coerceAtLeast(1)}")
                    IconButton(onClick = { pageIndex++ }, enabled = count > 0 && pageIndex < count - 1) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente")
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMIN -> "Administrador"
    UserRole.EDITOR -> "Editor corporativo"
    UserRole.VIEWER -> "Solo lectura"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "--"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
