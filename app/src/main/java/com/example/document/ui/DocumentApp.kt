package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.document.model.DocumentRecord
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.example.document.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentApp(
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
        if (!state.driveConnected || state.session == null) {
            ConnectScreen(Modifier.padding(padding), state.busy, onConnectDrive)
        } else {
            Dashboard(
                Modifier.padding(padding),
                state,
                onConnectDrive,
                onRefresh,
                onUploadPdf,
                onOpenPdf,
                onPrepareSignature,
                onConfigureDrive,
                onSaveProfile,
                onUpdateUser,
                onUpdateSettings,
                onSignOut
            )
        }
    }

    if (state.previewDocument != null && state.previewFile != null) {
        PdfDialog(state.previewFile, state.previewDocument, onClosePdf)
    }
    if (state.signingDocument != null && state.signingFile != null) {
        SignaturePlacementDialog(
            file = state.signingFile,
            document = state.signingDocument,
            profile = requireNotNull(state.session).profile,
            onDismiss = onCancelSignaturePlacement,
            onConfirm = { placement -> onRequestSignature(state.signingDocument, placement) }
        )
    }
}

@Composable
private fun ConnectScreen(modifier: Modifier, busy: Boolean, onConnectDrive: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).widthIn(max = 520.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Cloud, null, Modifier.size(54.dp))
                Text("SKM Industrial Gestión de Planos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Control de OT, revisiones, firmas y aprobación para fabricación usando Google Drive.")
                Button(onClick = onConnectDrive, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Conectar mi Google Drive")
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    modifier: Modifier,
    state: DocumentUiState,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onPrepareSignature: (DocumentRecord) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    val session = requireNotNull(state.session)
    var upload by rememberSaveable { mutableStateOf(false) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var admin by rememberSaveable { mutableStateOf(false) }
    var configure by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SKM Industrial Gestión de Planos", fontWeight = FontWeight.Bold)
                        Text("${session.profile.displayName.ifBlank { session.displayName }} · ${session.profile.role.name}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { profile = true }) { Icon(Icons.Default.Person, "Mi perfil") }
                    if (session.isAdmin) IconButton(onClick = { admin = true }) { Icon(Icons.Default.AdminPanelSettings, "Administrar") }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = onSignOut) { Icon(Icons.Default.Logout, "Salir") }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ConnectionCard(state, onConnectDrive, { configure = true })
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard("Planos", state.documents.size.toString(), Modifier.weight(1f))
                        SummaryCard("Pendientes", state.documents.count { !it.completed }.toString(), Modifier.weight(1f))
                        SummaryCard("Aptos", state.documents.count { it.completed }.toString(), Modifier.weight(1f))
                    }
                }
                if (session.isAdmin && state.configuration.isConfigured) {
                    item {
                        Button(onClick = { upload = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Subir plano y crear estructura OT")
                        }
                    }
                }
                item { Text("Documentos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (state.documents.isEmpty()) {
                    item { Text("Todavía no hay documentos registrados.", modifier = Modifier.padding(16.dp)) }
                } else {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentCard(document, session.email, onOpenPdf, onPrepareSignature)
                    }
                }
            }
        }
    }

    if (upload) UploadDialog({ upload = false }) { uri, ot, code, rev ->
        upload = false
        onUploadPdf(uri, ot, code, rev)
    }
    if (configure) ConfigureDialog(
        currentName = state.configuration.folderName,
        onDismiss = { configure = false },
        onSave = { link, name -> configure = false; onConfigureDrive(link, name) }
    )
    if (profile) ProfileDialog(
        profile = session.profile,
        photoFile = state.profilePhotoFile,
        signatureFile = state.profileSignatureFile,
        onDismiss = { profile = false },
        onSave = { name, rut, position, placement, photo, signature ->
            profile = false
            onSaveProfile(name, rut, position, placement, photo, signature)
        }
    )
    if (admin) AdminDialog(
        users = state.users,
        reviewDays = state.settings.reviewDays,
        onDismiss = { admin = false },
        onUpdateUser = onUpdateUser,
        onUpdateSettings = onUpdateSettings
    )
}

@Composable
private fun ConnectionCard(state: DocumentUiState, onConnectDrive: () -> Unit, onConfigure: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.configuration.isConfigured) state.configuration.folderName else "Carpeta principal no configurada", fontWeight = FontWeight.Bold)
            Text(if (state.configuration.isConfigured) "${state.configuration.folderId} · ${if (state.configuration.canEdit) "Lectura y escritura" else "Solo lectura"}" else "El administrador debe seleccionar una carpeta de Drive.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onConnectDrive, label = { Text("Reconectar Drive") })
                if (state.session?.isAdmin == true) AssistChip(onClick = onConfigure, label = { Text("Elegir carpeta") })
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DocumentCard(
    document: DocumentRecord,
    currentEmail: String,
    onOpen: (DocumentRecord) -> Unit,
    onSign: (DocumentRecord) -> Unit
) {
    val progress = "${document.approvals.size}/${document.requiredReviewerEmails.size}"
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold)
                    Text("Rev ${document.revision} · ${document.status}")
                }
                Text(progress, fontWeight = FontWeight.Bold)
            }
            Text("Archivo: ${document.fileName}", style = MaterialTheme.typography.bodySmall)
            Text("Firmas: ${document.approvals.joinToString { it.name.ifBlank { it.email } }.ifBlank { "Ninguna" }}", style = MaterialTheme.typography.bodySmall)
            if (!document.completed) {
                Text("Turno actual: ${document.currentReviewerEmail}", style = MaterialTheme.typography.bodySmall)
                Text("Plazo: ${formatDate(document.dueAt)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen(document) }, modifier = Modifier.weight(1f)) {
                    Text("Abrir")
                }
                if (document.canBeSignedBy(currentEmail)) {
                    Button(onClick = { onSign(document) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Draw, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Revisar y firmar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureDialog(currentName: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(currentName.ifBlank { "Planos SKM" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carpeta principal de Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(link, { link = it }, label = { Text("Enlace de carpeta") })
                OutlinedTextField(name, { name = it }, label = { Text("Nombre visible") })
            }
        },
        confirmButton = { Button(onClick = { onSave(link, name) }, enabled = link.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun UploadDialog(onDismiss: () -> Unit, onUpload: (Uri, String, String, String) -> Unit) {
    var ot by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf("0") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subir plano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(ot, { ot = it.filter(Char::isDigit) }, label = { Text("Número OT") })
                OutlinedTextField(code, { code = it }, label = { Text("Código del plano") })
                OutlinedTextField(revision, { revision = it }, label = { Text("Revisión") })
                OutlinedButton(onClick = { picker.launch("application/pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uri == null) "Seleccionar PDF" else "PDF seleccionado")
                }
                Text("Se creará OT $ot / Rev $revision y una copia roja NO APTO PARA FABRICACIÓN.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onUpload(requireNotNull(uri), ot, code, revision) }, enabled = uri != null && ot.isNotBlank() && code.isNotBlank()) { Text("Subir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProfileDialog(
    profile: UserProfile,
    photoFile: File?,
    signatureFile: File?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var rut by rememberSaveable { mutableStateOf(profile.rut) }
    var position by rememberSaveable { mutableStateOf(profile.position) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var placementWidth by rememberSaveable { mutableFloatStateOf(profile.placement.width) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photoUri = it }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 720.dp), shape = RoundedCornerShape(22.dp)) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Mi perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = photoUri ?: photoFile,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(36.dp)),
                        contentScale = ContentScale.Crop
                    )
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }) { Text("Cambiar foto") }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rut, { rut = it }, label = { Text("RUT") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(position, { position = it }, label = { Text("Cargo") }, modifier = Modifier.fillMaxWidth())
                Text("Firma manual", fontWeight = FontWeight.Bold)
                if (signatureFile != null) AsyncImage(signatureFile, "Firma actual", Modifier.fillMaxWidth().height(80.dp), contentScale = ContentScale.Fit)
                SignaturePad(strokes)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { strokes.clear() }) { Text("Limpiar firma") }
                }
                Text("Ancho del timbre: ${(placementWidth * 100).roundToInt()}%")
                Slider(placementWidth, { placementWidth = it }, valueRange = 0.20f..0.42f)
                Text("La posición exacta se mueve sobre el plano antes de confirmar cada firma.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        onSave(
                            name,
                            rut,
                            position,
                            profile.placement.copy(width = placementWidth),
                            photoUri,
                            if (strokes.isEmpty()) null else signaturePng(strokes)
                        )
                    }) { Text("Guardar perfil") }
                }
            }
        }
    }
}

@Composable
private fun SignaturePad(strokes: MutableList<List<Offset>>) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    OutlinedCard(Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point -> current = listOf(point) },
                    onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() },
                    onDragCancel = { current = emptyList() },
                    onDrag = { change, _ -> change.consume(); current = current + change.position }
                )
            }
        ) {
            fun drawPath(points: List<Offset>) {
                points.zipWithNext().forEach { (a, b) -> drawLine(androidx.compose.ui.graphics.Color.Black, a, b, 4f) }
            }
            strokes.forEach(::drawPath)
            drawPath(current)
            drawRect(androidx.compose.ui.graphics.Color.LightGray, style = Stroke(1f))
        }
    }
}

private fun signaturePng(strokes: List<List<Offset>>): ByteArray {
    val bitmap = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val all = strokes.flatten()
    val maxX = all.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 1f
    val maxY = all.maxOfOrNull { it.y }?.coerceAtLeast(1f) ?: 1f
    val scaleX = 860f / maxX
    val scaleY = 260f / maxY
    strokes.forEach { points ->
        points.zipWithNext().forEach { (a, b) ->
            canvas.drawLine(20f + a.x * scaleX, 20f + a.y * scaleY, 20f + b.x * scaleX, 20f + b.y * scaleY, paint)
        }
    }
    return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
}

@Composable
private fun AdminDialog(
    users: List<UserProfile>,
    reviewDays: Int,
    onDismiss: () -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit
) {
    var days by rememberSaveable { mutableStateOf(reviewDays.toString()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(12.dp).widthIn(max = 820.dp), shape = RoundedCornerShape(22.dp)) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Administración de usuarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Días para revisar") }, modifier = Modifier.weight(1f))
                        Button(onClick = { days.toIntOrNull()?.let(onUpdateSettings) }) { Text("Guardar plazo") }
                    }
                }
                items(users, key = { it.email }) { user -> UserAdminRow(user, onUpdateUser) }
                item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") } }
            }
        }
    }
}

@Composable
private fun UserAdminRow(user: UserProfile, onUpdate: (String, UserRole, Boolean, Boolean) -> Unit) {
    var role by remember(user.email, user.role) { mutableStateOf(user.role) }
    var active by remember(user.email, user.active) { mutableStateOf(user.active) }
    var signer by remember(user.email, user.requiredSigner) { mutableStateOf(user.requiredSigner) }
    var menu by remember { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(user.displayName.ifBlank { user.email }, fontWeight = FontWeight.Bold)
            Text(user.email, style = MaterialTheme.typography.bodySmall)
            Text("${user.position.ifBlank { "Cargo pendiente" }} · ${user.rut.ifBlank { "RUT pendiente" }}", style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("Rol: ${role.name}") }
                DropdownMenu(menu, { menu = false }) {
                    UserRole.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { role = option; menu = false })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activo", Modifier.weight(1f)); Switch(active, { active = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Firma obligatoria", Modifier.weight(1f)); Switch(signer, { signer = it })
            }
            Button(onClick = { onUpdate(user.email, role, active, signer) }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar cambios") }
        }
    }
}

@Composable
private fun SignaturePlacementDialog(
    file: File,
    document: DocumentRecord,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onConfirm: (SignaturePlacement) -> Unit
) {
    var pageBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var x by rememberSaveable { mutableFloatStateOf(profile.placement.x) }
    var y by rememberSaveable { mutableFloatStateOf(profile.placement.y) }
    var width by rememberSaveable { mutableFloatStateOf(profile.placement.width) }
    LaunchedEffect(file) { pageBitmap = renderFirstPage(file) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(12.dp).widthIn(max = 800.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ubicar firma · ${document.code} Rev ${document.revision}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Arrastra el timbre hacia una zona que no interfiera con cotas o notas. Se aplicará en todas las hojas.")
                Box(
                    Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(0.72f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onGloballyPositioned { container = it.size }
                ) {
                    pageBitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                    val stampWidthPx = container.width * width
                    val stampHeightPx = stampWidthPx * 0.48f
                    Card(
                        Modifier
                            .width((width * 330).dp.coerceIn(100.dp, 180.dp))
                            .offset { IntOffset((x * container.width).roundToInt(), (y * container.height).roundToInt()) }
                            .pointerInput(container, width) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    if (container.width > 0 && container.height > 0) {
                                        x = (x + drag.x / container.width).coerceIn(0f, (1f - stampWidthPx / container.width).coerceAtLeast(0f))
                                        y = (y + drag.y / container.height).coerceIn(0f, (1f - stampHeightPx / container.height).coerceAtLeast(0f))
                                    }
                                }
                            },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            Text("FIRMADO / REVISADO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text(profile.displayName, style = MaterialTheme.typography.labelSmall)
                            Text(profile.position, style = MaterialTheme.typography.labelSmall)
                            Text("RUT ${profile.rut}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Tamaño del timbre")
                Slider(width, { width = it }, valueRange = 0.20f..0.42f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = { onConfirm(SignaturePlacement(x, y, width)) }) { Text("Confirmar con huella o PIN") }
                }
            }
        }
    }
}

@Composable
private fun PdfDialog(file: File, document: DocumentRecord, onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    var count by remember { mutableStateOf(1) }
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, page) {
        val rendered = renderPage(file, page)
        bitmap = rendered.first
        count = rendered.second
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxSize().padding(8.dp)) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Text("${document.code} · Rev ${document.revision}", fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        ?: CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
                    Text("${page + 1} / $count")
                    OutlinedButton(onClick = { if (page + 1 < count) page++ }, enabled = page + 1 < count) { Text("Siguiente") }
                    TextButton(onClick = onClose) { Text("Cerrar") }
                }
            }
        }
    }
}

private suspend fun renderFirstPage(file: File): Bitmap = renderPage(file, 0).first

private suspend fun renderPage(file: File, index: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val scale = 1.6f
                val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt(), (page.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap to renderer.pageCount
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin plazo"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
