package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.data.excel.ExcelExporter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.data.notification.SmartNotificationManager
import com.example.data.local.entity.ApproverUserEntity
import com.example.data.local.entity.WorkOrderEntity
import com.example.ui.components.GoogleDriveSyncHeader
import com.example.ui.components.OtCategoryChipRow
import com.example.ui.components.UserMaskManagementDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToHtmlForm: () -> Unit,
    onOpenCreateOtDialog: () -> Unit
) {
    val workOrders by viewModel.filteredWorkOrders.collectAsState()
    val approvers by viewModel.approverUsers.collectAsState()
    val signatureLogs by viewModel.signatureLogs.collectAsState()
    val context = LocalContext.current
    val driveUser by viewModel.googleDriveUser.collectAsState()
    val isSyncingDrive by viewModel.isSyncingDrive.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val adminSettings by viewModel.adminSettings.collectAsState()

    val activeUserMask by viewModel.activeUserMask.collectAsState()
    val isUserMaskModalOpen by viewModel.userMaskModalOpen.collectAsState()

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val deadlineAlertCount = remember(workOrders) {
        workOrders.count { it.isNearDeadline || (it.deadlineTimestamp - System.currentTimeMillis() < 48 * 3600 * 1000L) }
    }

    val pendingCount = remember(workOrders) {
        workOrders.count { it.status != "APROBADO" }
    }

    val approvedCount = remember(workOrders) {
        workOrders.count { it.status == "APROBADO" }
    }

    val traceabilityText = remember(approvedCount, workOrders.size) {
        if (workOrders.isNotEmpty()) "${(approvedCount * 100) / workOrders.size}%" else "0%"
    }

    if (isUserMaskModalOpen) {
        UserMaskManagementDialog(
            approvers = approvers,
            activeUser = activeUserMask,
            onDismiss = { viewModel.closeUserMaskModal() },
            onSelectActiveUser = { viewModel.setActiveUserMask(it) },
            onSaveUserMask = { id, name, role, rut, email, gAccount ->
                viewModel.saveUserMask(id, name, role, rut, email, gAccount)
            }
        )
    }

    Scaffold(
        modifier = Modifier.testTag("home_screen"),
        containerColor = Color(0xFFF7F9FF),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Engineering, contentDescription = null, tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "SKM INDUSTRIAL",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = "FIRMA DE PLANOS OT & FABRICACIÓN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    // HTML Form generator button
                    IconButton(
                        onClick = onNavigateToHtmlForm,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                            .testTag("open_html_form_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Formulario Web HTML",
                            tint = Color(0xFF2563EB)
                        )
                    }
                    // Admin Panel Button
                    IconButton(
                        onClick = onNavigateToAdmin,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .testTag("open_admin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Panel Administrador",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCreateOtDialog,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva OT / Plano", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_ot_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Bento Grid Hero Section (Gradient Card)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2563EB), Color(0xFF4338CA))
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Text(
                                                text = "6 Usuarios en línea",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Icon(
                                            Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Gestión Digital de Planos OT",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                )

                                Text(
                                    text = "$pendingCount OT(s) requiriendo validación biométrica en Drive",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Approver Avatars Stack in Bento Hero
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                        approvers.take(6).forEach { user ->
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(2.dp, Color(0xFF2563EB), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = user.avatarInitials,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFF1D4ED8),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = onNavigateToAdmin,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "Panel Control",
                                            color = Color(0xFF1D4ED8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Bento Grid 2x2 Quick Metrics Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tile 1: Trazabilidad
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Trazabilidad",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFEDD5)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFFEA580C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = traceabilityText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Tile 2: Drive Status
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Google Drive",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD1FAE5)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (driveUser.isAuthenticated) "Sincronizado" else "Pendiente",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = if (driveUser.isAuthenticated) Color(0xFF059669) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Google Drive Sync Header Card
                item {
                    GoogleDriveSyncHeader(
                        driveUser = driveUser,
                        isSyncing = isSyncingDrive,
                        syncMessage = syncMessage,
                        driveFolderName = adminSettings?.googleDriveBaseFolder ?: "Google Drive / Fabricaciones Manto OT",
                        onSyncClick = { viewModel.syncWithGoogleDrive() },
                        onToggleAuthClick = { viewModel.toggleGoogleDriveAuth() },
                        activeUserMask = activeUserMask,
                        onManageUsersClick = { viewModel.openUserMaskModal() },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // 3.2. Cloud Excel Database & Control Documental Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.TableChart,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Control Documental Excel Nube",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color(0xFF14532D),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = "Base de Datos sincronizada con Google Drive • Registro Único de Firmas",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF15803D),
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        val csv = ExcelExporter.generateCsvReport(workOrders, emptyMap(), approvers, signatureLogs)
                                        val file = ExcelExporter.exportAndShareCsv(context, csv)
                                        ExcelExporter.shareCsvFile(context, file)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF16A34A)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("Excel .xlsx", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3.5. Smart Notification Rules Card SKM INDUSTRIAL
                item {
                    val activeWindow = SmartNotificationManager.getCurrentWindow(daysPending = 0)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Avisos Programados de Firma OT",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                        Text(
                                            text = "08:00 - 16:00h Normal • 16:00 - 19:00h Retrasos • Cada hora (>2 días)",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        val ok = SmartNotificationManager.sendBlueprintEmittedNotification(
                                            context = context,
                                            otId = "OT-2026-DEMO",
                                            blueprintName = "Plano Eje Principal Manto",
                                            daysPending = 3,
                                            pendingApproversCount = 6
                                        )
                                        if (ok) {
                                            Toast.makeText(context, "Notificación programada emitida con éxito", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Fuera de horario de notificaciones (19:00 - 08:00)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Send,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Probar Aviso",
                                            color = Color(0xFF1E40AF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Search Bar in Bento Style
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar por código OT, componente o área...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color(0xFF0F172A))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // 5. Category Filter Chips
                item {
                    OtCategoryChipRow(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.setCategoryFilter(it) }
                    )
                }

                // 6. Deadline Alert Bento Banner
                if (deadlineAlertCount > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "¡Alerta de Fechas Límite!",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = Color(0xFF991B1B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "$deadlineAlertCount OT(s) próximas a vencer (< 48 hrs) requiriendo firma.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB91C1C))
                                    )
                                }
                                Button(
                                    onClick = { viewModel.setCategoryFilter("ALERTAS") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Filtrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 7. Work Orders Bento List
                if (workOrders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "No hay Órdenes de Trabajo que coincidan.",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                                )
                            }
                        }
                    }
                } else {
                    items(workOrders, key = { it.id }) { ot ->
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            WorkOrderCardItem(
                                workOrder = ot,
                                approvers = approvers,
                                onClick = { onNavigateToDetail(ot.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkOrderCardItem(
    workOrder: WorkOrderEntity,
    approvers: List<ApproverUserEntity>,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val now = System.currentTimeMillis()
    val isNearDeadline = workOrder.isNearDeadline || (workOrder.deadlineTimestamp - now < 48 * 3600 * 1000L)
    val isApproved = workOrder.status == "APROBADO"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("work_order_card_${workOrder.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: OT ID & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = workOrder.id,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    // Category Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = workOrder.categoryDisplayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isApproved -> Color(0xFFD1FAE5)
                        isNearDeadline -> Color(0xFFFEE2E2)
                        else -> Color(0xFFEFF6FF)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isApproved -> Icons.Default.CheckCircle
                                isNearDeadline -> Icons.Default.AccessTimeFilled
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = when {
                                isApproved -> Color(0xFF059669)
                                isNearDeadline -> Color(0xFFDC2626)
                                else -> Color(0xFF2563EB)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when {
                                isApproved -> "APROBADO"
                                isNearDeadline -> "PRÓXIMO VENCER"
                                else -> "PENDIENTE FIRMA"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = when {
                                    isApproved -> Color(0xFF065F46)
                                    isNearDeadline -> Color(0xFF991B1B)
                                    else -> Color(0xFF1E40AF)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title Component Name
            Text(
                text = workOrder.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Client / Area Destination
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                Text(
                    text = workOrder.clientOrArea,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar (6 Connected Users Approval Progress)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progreso Firma 6 Aprobadores:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${workOrder.signedCount} / ${workOrder.totalApproversNeeded} Firmados (${workOrder.signedCount * 100 / workOrder.totalApproversNeeded}%)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                LinearProgressIndicator(
                    progress = { workOrder.signedCount.toFloat() / workOrder.totalApproversNeeded.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isApproved) Color(0xFF10B981) else Color(0xFF2563EB),
                    trackColor = Color(0xFFF1F5F9)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Approver Avatars Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    approvers.take(6).forEachIndexed { index, approver ->
                        val isUserSigned = index < workOrder.signedCount
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isUserSigned) Color(0xFF10B981) else Color(0xFFCBD5E1))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = approver.avatarInitials,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Deadline date text
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = if (isNearDeadline) Color(0xFFDC2626) else Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Límite: ${dateFormat.format(Date(workOrder.deadlineTimestamp))}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isNearDeadline) Color(0xFFDC2626) else Color(0xFF64748B),
                            fontWeight = if (isNearDeadline) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}
