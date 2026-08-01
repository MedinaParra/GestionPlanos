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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.document.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class CorporateDestination(
    val title: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("Panel general", "Inicio", Icons.Default.Dashboard),
    PLANS("Gestión de planos", "Planos", Icons.Default.Description),
    REVIEWS("Mis revisiones", "Mis revisiones", Icons.Default.RateReview),
    USERS("Usuarios y firmantes", "Usuarios y firmantes", Icons.Default.Groups),
    NOTIFICATIONS("Notificaciones", "Notificaciones", Icons.Default.Notifications),
    PROFILE("Mi perfil", "Mi perfil", Icons.Default.AccountCircle),
    SETTINGS("Configuración", "Configuración", Icons.Default.Settings),
    HELP("Ayuda", "Ayuda", Icons.Default.HelpOutline)
}

@Composable
fun CorporateDocumentApp(
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
    val snackbarHostState = remember { SnackbarHostState() }
    val feedback = state.error ?: state.message
    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback)
            onClearFeedback()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!state.driveConnected || state.session == null) {
            CorporateConnectScreen(state.busy, onConnectDrive)
        } else {
            CorporateShell(
                state = state,
                onRefresh = onRefresh,
                onReconnect = onConnectDrive,
                onUploadPdf = onUploadPdf,
                onOpenPdf = onOpenPdf,
                onSaveProfile = onSaveProfile,
                onUpdateUser = onUpdateUser,
                onUpdateSettings = onUpdateSettings,
                onConfigureDrive = onConfigureDrive,
                onSignOut = onSignOut
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
        if (state.busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
            )
        }
    }

    val previewDocument = state.previewDocument
    val previewFile = state.previewFile
    val session = state.session
    if (previewDocument != null && previewFile != null && session != null) {
        CorporatePdfViewer(
            file = previewFile,
            document = previewDocument,
            comments = state.previewComments,
            timeline = timeline,
            currentEmail = session.email,
            isAdmin = session.isAdmin,
            canComment = state.configuration.canEdit && session.profile.active,
            onClose = onClosePdf,
            onApprove = { onPrepareSignature(previewDocument) },
            onRequestChanges = { reason -> onRequestChanges(previewDocument, reason) },
            onAddComment = { page, text, x, y, width ->
                onAddComment(previewDocument, page, text, x, y, width)
            },
            onPublishComment = onPublishComment,
            onUpdateComment = onUpdateComment,
            onDeleteComment = onDeleteComment
        )
    }

    if (state.signingDocument != null && state.signingFile != null && session != null) {
        CorporateSignaturePlacementDialog(
            file = state.signingFile,
            document = state.signingDocument,
            profile = session.profile,
            onDismiss = onCancelSignaturePlacement,
            onConfirm = { placement -> onRequestSignature(state.signingDocument, placement) }
        )
    }
}

@Composable
private fun CorporateConnectScreen(busy: Boolean, onConnectDrive: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxWidth < 480.dp
        Card(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(if (compact) 22.dp else 30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier.padding(if (compact) 22.dp else 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SkmOrange,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Description, null, Modifier.padding(15.dp), tint = Color.White)
                }
                Text(
                    "SKM Industrial",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Gestión de Planos",
                    style = MaterialTheme.typography.titleLarge,
                    color = SkmOrange,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Controla OT, revisiones, observaciones y aprobaciones desde la misma carpeta corporativa de Google Drive.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onConnectDrive,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Cloud, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Conectar cuenta corporativa")
                }
                Text(
                    "Acceso para cuentas @skmindustrial.cl",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorporateShell(
    state: DocumentUiState,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onUploadPdf: (Uri, String, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val session = requireNotNull(state.session)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(CorporateDestination.HOME) }
    var showUpload by rememberSaveable { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 330.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                CorporateDrawer(
                    session = session,
                    selected = destination,
                    onSelect = {
                        destination = it
                        scope.launch { drawerState.close() }
                    },
                    onSignOut = onSignOut
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                destination.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "SKM Industrial Gestión de Planos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Abrir menú")
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Sync, "Actualizar desde Drive")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (
                    session.isAdmin &&
                    state.configuration.isConfigured &&
                    destination in listOf(CorporateDestination.HOME, CorporateDestination.PLANS)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showUpload = true },
                        icon = { Icon(Icons.Default.UploadFile, null) },
                        text = { Text("Subir plano") },
                        containerColor = SkmOrange,
                        contentColor = Color.White
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (destination) {
                    CorporateDestination.HOME -> CorporateHomePage(
                        state = state,
                        onOpenPdf = onOpenPdf,
                        onUpload = { showUpload = true },
                        onGoReviews = { destination = CorporateDestination.REVIEWS }
                    )
                    CorporateDestination.PLANS -> CorporatePlansPage(state, onOpenPdf)
                    CorporateDestination.REVIEWS -> CorporateReviewsPage(state, onOpenPdf)
                    CorporateDestination.USERS -> CorporateUsersPage(state, onUpdateUser)
                    CorporateDestination.NOTIFICATIONS -> CorporateNotificationsPage(state)
                    CorporateDestination.PROFILE -> CorporateProfilePage(
                        state = state,
                        onSaveProfile = onSaveProfile
                    )
                    CorporateDestination.SETTINGS -> CorporateSettingsPage(
                        state = state,
                        onReconnect = onReconnect,
                        onConfigureDrive = onConfigureDrive,
                        onUpdateSettings = onUpdateSettings
                    )
                    CorporateDestination.HELP -> CorporateHelpPage()
                }
            }
        }
    }

    if (showUpload) {
        CorporateUploadDialog(
            onDismiss = { showUpload = false },
            onUpload = { uri, ot, code, revision ->
                showUpload = false
                onUploadPdf(uri, ot, code, revision)
            }
        )
    }
}

@Composable
private fun CorporateDrawer(
    session: SessionUser,
    selected: CorporateDestination,
    onSelect: (CorporateDestination) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = 12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = SkmOrange, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Description, null, Modifier.padding(11.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("SKM Industrial", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Gestión de Planos", color = SkmOrange, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CorporateDestination.entries.forEach { item ->
                if (item == CorporateDestination.USERS && !session.isAdmin) return@forEach
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    icon = { Icon(item.icon, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = SkmOrangeLight,
                        selectedIconColor = SkmOrangeDark,
                        selectedTextColor = SkmGraphite
                    )
                )
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                session.profile.displayName.ifBlank { session.displayName },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                session.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SkmDanger)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun CorporatePageContainer(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 1160.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}

@Composable
private fun CorporateHomePage(
    state: DocumentUiState,
    onOpenPdf: (DocumentRecord) -> Unit,
    onUpload: () -> Unit,
    onGoReviews: () -> Unit
) {
    val session = requireNotNull(state.session)
    val pending = state.documents.filter { it.canBeSignedBy(session.email) }
    val changes = state.documents.count { it.changesRequested }
    CorporatePageContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CorporateWelcomeCard(session, state)
            }
            item {
                CorporateSummaryGrid(
                    total = state.documents.size,
                    pending = pending.size,
                    changes = changes,
                    approved = state.documents.count { it.completed }
                )
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 520.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (session.isAdmin && state.configuration.isConfigured) {
                                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                                    Icon(Icons.Default.UploadFile, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Subir plano y solicitar revisión")
                                }
                            }
                            OutlinedButton(onClick = onGoReviews, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                                Icon(Icons.Default.RateReview, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Ver mis revisiones pendientes")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (session.isAdmin && state.configuration.isConfigured) {
                                Button(onClick = onUpload, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                                    Icon(Icons.Default.UploadFile, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Subir plano")
                                }
                            }
                            OutlinedButton(onClick = onGoReviews, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                                Icon(Icons.Default.RateReview, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mis revisiones")
                            }
                        }
                    }
                }
            }
            item {
                Text("Prioridad de hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (pending.isEmpty()) {
                item { CorporateEmptyCard(Icons.Default.TaskAlt, "No tienes revisiones pendientes", "Los planos asignados aparecerán aquí.") }
            } else {
                items(pending.take(5), key = { it.id }) { document ->
                    CorporateDocumentCard(document, session.email, onOpenPdf)
                }
            }
        }
    }
}

@Composable
private fun CorporateWelcomeCard(session: SessionUser, state: DocumentUiState) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SkmGraphite)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val compact = maxWidth < 520.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CorporateWelcomeText(session)
                    CorporateConnectionBadge(state)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CorporateWelcomeText(session, Modifier.weight(1f))
                    CorporateConnectionBadge(state)
                }
            }
        }
    }
}

@Composable
private fun CorporateWelcomeText(session: SessionUser, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Hola, ${session.profile.displayName.ifBlank { session.displayName }}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Revisa el estado de los planos y atiende tus pendientes.", color = Color.White.copy(alpha = 0.74f))
    }
}

@Composable
private fun CorporateConnectionBadge(state: DocumentUiState) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (state.configuration.isConfigured) SkmSuccessSurface else SkmWarningSurface
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.configuration.isConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                null,
                Modifier.size(18.dp),
                tint = if (state.configuration.isConfigured) SkmSuccess else SkmWarning
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (state.configuration.isConfigured) "Drive conectado" else "Falta configurar carpeta",
                color = SkmGraphite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun CorporateSummaryGrid(total: Int, pending: Int, changes: Int, approved: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 620.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CorporateMetricCard("Planos", total.toString(), Icons.Default.Description, SkmInfo, Modifier.weight(1f))
                    CorporateMetricCard("Pendientes", pending.toString(), Icons.Default.Schedule, SkmWarning, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CorporateMetricCard("Cambios", changes.toString(), Icons.Default.RateReview, SkmDanger, Modifier.weight(1f))
                    CorporateMetricCard("Aptos", approved.toString(), Icons.Default.Verified, SkmSuccess, Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CorporateMetricCard("Planos", total.toString(), Icons.Default.Description, SkmInfo, Modifier.weight(1f))
                CorporateMetricCard("Pendientes", pending.toString(), Icons.Default.Schedule, SkmWarning, Modifier.weight(1f))
                CorporateMetricCard("Cambios", changes.toString(), Icons.Default.RateReview, SkmDanger, Modifier.weight(1f))
                CorporateMetricCard("Aptos", approved.toString(), Icons.Default.Verified, SkmSuccess, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CorporateMetricCard(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = accent)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CorporatePlansPage(state: DocumentUiState, onOpenPdf: (DocumentRecord) -> Unit) {
    val session = requireNotNull(state.session)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("TODOS") }
    val filtered = state.documents.filter { document ->
        val matchesText = query.isBlank() || listOf(document.otNumber, document.code, document.fileName, document.revision)
            .any { it.contains(query, ignoreCase = true) }
        val matchesStatus = when (filter) {
            "REVISION" -> document.isUnderReview
            "CAMBIOS" -> document.changesRequested
            "APTO" -> document.completed
            else -> true
        }
        matchesText && matchesStatus
    }
    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar por OT, código, revisión o archivo") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpiar") }
                    }
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CorporateFilterChip("TODOS", "Todos", filter) { filter = it }
                    CorporateFilterChip("REVISION", "En revisión", filter) { filter = it }
                    CorporateFilterChip("CAMBIOS", "Cambios solicitados", filter) { filter = it }
                    CorporateFilterChip("APTO", "Aptos", filter) { filter = it }
                }
            }
            if (filtered.isEmpty()) {
                item { CorporateEmptyCard(Icons.Default.FolderOff, "No se encontraron planos", "Cambia los filtros o sincroniza nuevamente desde Drive.") }
            } else {
                items(filtered, key = { it.id }) { document ->
                    CorporateDocumentCard(document, session.email, onOpenPdf)
                }
            }
        }
    }
}

@Composable
private fun CorporateFilterChip(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SkmOrangeLight,
            selectedLabelColor = SkmGraphite
        )
    )
}

@Composable
private fun CorporateReviewsPage(state: DocumentUiState, onOpenPdf: (DocumentRecord) -> Unit) {
    val session = requireNotNull(state.session)
    val assigned = state.documents.filter {
        it.currentReviewerEmail.equals(session.email, ignoreCase = true) && it.isUnderReview
    }
    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CorporateInfoBanner(
                    icon = Icons.Default.RateReview,
                    title = "Revisión secuencial",
                    text = "Solo aparecen los documentos cuyo turno actual te corresponde. Abre el plano para comentar, aprobar o solicitar cambios."
                )
            }
            if (assigned.isEmpty()) {
                item { CorporateEmptyCard(Icons.Default.TaskAlt, "Sin tareas pendientes", "No hay planos esperando tu revisión.") }
            } else {
                items(assigned, key = { it.id }) { CorporateDocumentCard(it, session.email, onOpenPdf) }
            }
        }
    }
}

@Composable
private fun CorporateDocumentCard(
    document: DocumentRecord,
    currentEmail: String,
    onOpen: (DocumentRecord) -> Unit
) {
    val statusColor = when {
        document.completed -> SkmSuccess
        document.changesRequested -> SkmDanger
        document.canBeSignedBy(currentEmail) -> SkmOrange
        else -> SkmInfo
    }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.padding(10.dp), tint = statusColor)
                }
                Column(Modifier.weight(1f)) {
                    Text("OT ${document.otNumber} · ${document.code}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Revisión ${document.revision}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.12f)) {
                    Text(
                        document.workflowStatusLabel,
                        Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(document.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = {
                    if (document.requiredReviewerEmails.isEmpty()) 0f
                    else document.approvals.size.toFloat() / document.requiredReviewerEmails.size.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
                color = SkmOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 440.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CorporateDocumentMeta(document)
                        Button(onClick = { onOpen(document) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(if (document.canBeSignedBy(currentEmail)) Icons.Default.RateReview else Icons.Default.Visibility, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (document.canBeSignedBy(currentEmail)) "Abrir revisión" else "Abrir plano")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CorporateDocumentMeta(document, Modifier.weight(1f))
                        Button(onClick = { onOpen(document) }) {
                            Icon(if (document.canBeSignedBy(currentEmail)) Icons.Default.RateReview else Icons.Default.Visibility, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (document.canBeSignedBy(currentEmail)) "Revisar" else "Abrir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorporateDocumentMeta(document: DocumentRecord, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Aprobaciones: ${document.approvals.size}/${document.requiredReviewerEmails.size}", style = MaterialTheme.typography.bodySmall)
        if (document.isUnderReview) {
            Text("Turno: ${document.currentReviewerEmail.ifBlank { "Sin asignación" }}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Plazo: ${corporateDate(document.dueAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CorporateUsersPage(state: DocumentUiState, onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit) {
    val session = requireNotNull(state.session)
    CorporatePageContainer {
        if (!session.isAdmin) {
            CorporateEmptyCard(Icons.Default.Lock, "Acceso administrativo", "Solo el administrador puede modificar usuarios y firmantes.")
            return@CorporatePageContainer
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CorporateInfoBanner(
                    Icons.Default.AdminPanelSettings,
                    "Control de participantes",
                    "Activa usuarios, asigna roles y define quiénes deben aprobar obligatoriamente cada plano nuevo."
                )
            }
            if (state.users.isEmpty()) {
                item { CorporateEmptyCard(Icons.Default.GroupOff, "Sin usuarios registrados", "Los usuarios aparecerán cuando conecten su cuenta por primera vez.") }
            } else {
                items(state.users, key = { it.email }) { user ->
                    CorporateUserCard(user, onUpdateUser)
                }
            }
        }
    }
}

@Composable
private fun CorporateUserCard(user: UserProfile, onUpdate: (String, UserRole, Boolean, Boolean) -> Unit) {
    var role by remember(user.email, user.role) { mutableStateOf(user.role) }
    var active by remember(user.email, user.active) { mutableStateOf(user.active) }
    var signer by remember(user.email, user.requiredSigner) { mutableStateOf(user.requiredSigner) }
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = SkmOrangeLight, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Person, null, Modifier.padding(10.dp), tint = SkmOrangeDark)
                }
                Column(Modifier.weight(1f)) {
                    Text(user.displayName.ifBlank { user.email }, fontWeight = FontWeight.Bold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("${user.position.ifBlank { "Cargo pendiente" }} · ${user.rut.ifBlank { "RUT pendiente" }}", style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Rol: ${role.name}")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    UserRole.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = { role = option; menu = false }
                        )
                    }
                }
            }
            CorporateSwitchRow("Usuario activo", active) { active = it }
            CorporateSwitchRow("Firmante obligatorio", signer) { signer = it }
            Button(onClick = { onUpdate(user.email, role, active, signer) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Guardar cambios del usuario")
            }
        }
    }
}

@Composable
private fun CorporateSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CorporateNotificationsPage(state: DocumentUiState) {
    val session = requireNotNull(state.session)
    val pending = state.documents.filter { it.canBeSignedBy(session.email) }
    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CorporateInfoBanner(
                    Icons.Default.NotificationsActive,
                    "Recordatorios automáticos",
                    "La app comprueba pendientes a las ${state.settings.morningHour}:00 y ${state.settings.afternoonHour}:00. Después de ${state.settings.escalationAfterHours} horas, aumenta la frecuencia."
                )
            }
            item { Text("Pendientes que generan aviso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (pending.isEmpty()) {
                item { CorporateEmptyCard(Icons.Default.NotificationsOff, "No hay avisos pendientes", "No tienes documentos esperando tu decisión.") }
            } else {
                items(pending, key = { it.id }) { document ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Schedule, null, tint = SkmWarning)
                            Column(Modifier.weight(1f)) {
                                Text("OT ${document.otNumber} · ${document.code} · Rev ${document.revision}", fontWeight = FontWeight.Bold)
                                Text("Vence: ${corporateDate(document.dueAt)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorporateProfilePage(
    state: DocumentUiState,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit
) {
    val session = requireNotNull(state.session)
    val profile = session.profile
    var name by rememberSaveable(profile.email, profile.displayName) { mutableStateOf(profile.displayName) }
    var rut by rememberSaveable(profile.email, profile.rut) { mutableStateOf(profile.rut) }
    var position by rememberSaveable(profile.email, profile.position) { mutableStateOf(profile.position) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var placementWidth by rememberSaveable(profile.email, profile.placement.width) { mutableFloatStateOf(profile.placement.width) }
    val strokes = remember(profile.email) { mutableStateListOf<List<Offset>>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photoUri = it }

    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AsyncImage(
                                model = photoUri ?: state.profilePhotoFile,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.size(82.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Column(Modifier.weight(1f)) {
                                Text(name.ifBlank { session.displayName }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(session.email, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.padding(top = 6.dp)) {
                                    Icon(Icons.Default.PhotoCamera, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cambiar foto")
                                }
                            }
                        }
                        OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(rut, { rut = it }, label = { Text("RUT") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(position, { position = it }, label = { Text("Cargo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Firma manual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Dibuja nuevamente solo cuando necesites reemplazar la firma guardada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.profileSignatureFile != null) {
                            AsyncImage(state.profileSignatureFile, "Firma actual", Modifier.fillMaxWidth().height(74.dp), contentScale = ContentScale.Fit)
                        }
                        CorporateSignaturePad(strokes)
                        TextButton(onClick = { strokes.clear() }) { Text("Limpiar firma dibujada") }
                        Text("Ancho predeterminado del timbre: ${(placementWidth * 100).roundToInt()}%")
                        Slider(value = placementWidth, onValueChange = { placementWidth = it }, valueRange = 0.20f..0.42f)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onSaveProfile(
                            name,
                            rut,
                            position,
                            profile.placement.copy(width = placementWidth),
                            photoUri,
                            if (strokes.isEmpty()) null else corporateSignaturePng(strokes)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar mi perfil")
                }
            }
        }
    }
}

@Composable
private fun CorporateSettingsPage(
    state: DocumentUiState,
    onReconnect: () -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onUpdateSettings: (Int) -> Unit
) {
    val session = requireNotNull(state.session)
    var folderLink by rememberSaveable { mutableStateOf("") }
    var folderName by rememberSaveable(state.configuration.folderName) { mutableStateOf(state.configuration.folderName.ifBlank { "Planos SKM" }) }
    var days by rememberSaveable(state.settings.reviewDays) { mutableStateOf(state.settings.reviewDays.toString()) }
    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, null, tint = SkmOrange)
                            Spacer(Modifier.width(10.dp))
                            Text("Conexión a Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (state.configuration.isConfigured) {
                                "${state.configuration.folderName} · ${if (state.configuration.canEdit) "Lectura y escritura" else "Solo lectura"}"
                            } else "No existe una carpeta principal configurada.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reconectar autorización de Drive")
                        }
                    }
                }
            }
            if (session.isAdmin) {
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Carpeta principal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = folderLink,
                                onValueChange = { folderLink = it },
                                label = { Text("Enlace o ID de la carpeta") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            OutlinedTextField(
                                value = folderName,
                                onValueChange = { folderName = it },
                                label = { Text("Nombre visible") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = { onConfigureDrive(folderLink, folderName) },
                                enabled = folderLink.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Guardar carpeta principal") }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Flujo y recordatorios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = days,
                                onValueChange = { days = it.filter(Char::isDigit) },
                                label = { Text("Días disponibles para revisar") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text("Avisos: ${state.settings.morningHour}:00 y ${state.settings.afternoonHour}:00 · Escalamiento después de ${state.settings.escalationAfterHours} horas.", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { days.toIntOrNull()?.let(onUpdateSettings) },
                                enabled = days.toIntOrNull() in 1..30,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) { Text("Guardar plazo de revisión") }
                        }
                    }
                }
            } else {
                item { CorporateInfoBanner(Icons.Default.Lock, "Configuración administrada", "La carpeta y los plazos solo pueden ser modificados por el administrador.") }
            }
        }
    }
}

@Composable
private fun CorporateHelpPage() {
    CorporatePageContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CorporateInfoBanner(Icons.Default.TouchApp, "Navegación", "Abre el menú lateral desde el icono superior izquierdo. Sincronizar está siempre arriba; cerrar sesión está aislado al final del menú.") }
            item { CorporateInfoBanner(Icons.Default.ZoomIn, "Visor PDF", "Pellizca para acercar, arrastra con dos dedos y utiliza la barra inferior para cambiar de hoja, comentar o revisar el historial.") }
            item { CorporateInfoBanner(Icons.Default.Comment, "Observaciones", "Pulsa Nueva observación, toca un punto del plano y escribe el texto. Primero queda como borrador privado; luego puedes publicarla.") }
            item { CorporateInfoBanner(Icons.Default.Approval, "Decisión", "Cuando sea tu turno puedes aprobar y firmar o detener la revisión solicitando cambios.") }
        }
    }
}

@Composable
private fun CorporateInfoBanner(icon: ImageVector, title: String, text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SkmOrangeSurface)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = SkmOrangeDark)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = SkmTextSecondary)
            }
        }
    }
}

@Composable
private fun CorporateEmptyCard(icon: ImageVector, title: String, text: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, Modifier.size(46.dp), tint = SkmTextMuted)
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CorporateUploadDialog(
    onDismiss: () -> Unit,
    onUpload: (Uri, String, String, String) -> Unit
) {
    var ot by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var revision by rememberSaveable { mutableStateOf("0") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    CorporateAdaptiveDialog(
        title = "Subir plano y solicitar revisión",
        onDismiss = onDismiss,
        primaryLabel = "Subir y enviar a revisión",
        primaryEnabled = uri != null && ot.isNotBlank() && code.isNotBlank(),
        onPrimary = { onUpload(requireNotNull(uri), ot, code, revision) }
    ) {
        CorporateInfoBanner(Icons.Default.Info, "Estructura automática", "Se creará OT $ot / Rev $revision, el original y la copia NO APTO PARA FABRICACIÓN.")
        OutlinedTextField(ot, { ot = it.filter(Char::isDigit) }, label = { Text("Número de OT") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Código del plano") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(revision, { revision = it }, label = { Text("Revisión") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = { picker.launch("application/pdf") }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Icon(if (uri == null) Icons.Default.AttachFile else Icons.Default.CheckCircle, null)
            Spacer(Modifier.width(8.dp))
            Text(if (uri == null) "Seleccionar archivo PDF" else "PDF seleccionado")
        }
    }
}

@Composable
private fun CorporateAdaptiveDialog(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(if (maxWidth < 500.dp) 8.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val compact = maxWidth < 500.dp || maxHeight < 620.dp
            Surface(
                modifier = if (compact) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(max = 760.dp).widthIn(max = 720.dp),
                shape = RoundedCornerShape(if (compact) 18.dp else 26.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                    }
                    HorizontalDivider()
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content
                    )
                    HorizontalDivider()
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                        if (maxWidth < 420.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(primaryLabel) }
                                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = onDismiss) { Text("Cancelar") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(primaryLabel) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorporateSignaturePad(strokes: MutableList<List<Offset>>) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    OutlinedCard(Modifier.fillMaxWidth().heightIn(min = 170.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point -> current = listOf(point) },
                        onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() },
                        onDragCancel = { current = emptyList() },
                        onDrag = { change, _ -> change.consume(); current = current + change.position }
                    )
                }
        ) {
            fun drawPath(points: List<Offset>) {
                points.zipWithNext().forEach { (a, b) -> drawLine(Color.Black, a, b, 4f) }
            }
            strokes.forEach(::drawPath)
            drawPath(current)
            drawRect(Color.LightGray, style = Stroke(1f))
        }
    }
}

private fun corporateSignaturePng(strokes: List<List<Offset>>): ByteArray {
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
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

@Composable
private fun CorporateSignaturePlacementDialog(
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
    LaunchedEffect(file) { pageBitmap = corporateRenderFirstPage(file) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "Volver") }
                        Column(Modifier.weight(1f)) {
                            Text("Ubicar firma", fontWeight = FontWeight.Bold)
                            Text("${document.code} · Rev ${document.revision}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                            if (maxWidth < 440.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onConfirm(SignaturePlacement(x, y, width)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                                        Icon(Icons.Default.Fingerprint, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Confirmar con huella o PIN")
                                    }
                                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { onConfirm(SignaturePlacement(x, y, width)) }) {
                                        Icon(Icons.Default.Fingerprint, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Confirmar con huella o PIN")
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Arrastra el timbre a una zona libre. Se aplicará en todas las hojas.", style = MaterialTheme.typography.bodySmall)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                            shape = RoundedCornerShape(5.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                Text("FIRMADO / REVISADO", color = SkmOrangeDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text(profile.displayName, color = SkmGraphite, style = MaterialTheme.typography.labelSmall)
                                Text(profile.position, color = SkmGraphite, style = MaterialTheme.typography.labelSmall)
                                Text("RUT ${profile.rut}", color = SkmGraphite, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("Tamaño del timbre: ${(width * 100).roundToInt()}%")
                    Slider(value = width, onValueChange = { width = it }, valueRange = 0.20f..0.42f)
                }
            }
        }
    }
}

private suspend fun corporateRenderFirstPage(file: File): Bitmap = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                val scale = 1.7f
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).roundToInt(),
                    (page.height * scale).roundToInt(),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
}

private fun corporateDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sin plazo"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
}
