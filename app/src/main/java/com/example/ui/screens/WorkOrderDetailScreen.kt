package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.html.LocalHtmlGenerator
import com.example.data.local.entity.ApproverUserEntity
import com.example.data.local.entity.BlueprintEntity
import com.example.data.local.entity.SignatureLogEntity
import com.example.data.local.entity.WorkOrderEntity
import com.example.ui.components.BiometricPromptDialog
import com.example.ui.components.BlueprintCanvasView
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedWorkOrder by viewModel.selectedWorkOrder.collectAsState()
    val blueprints by viewModel.selectedBlueprints.collectAsState()
    val logs by viewModel.selectedLogs.collectAsState()
    val approvers by viewModel.approverUsers.collectAsState()
    val biometricModalOpen by viewModel.biometricModalOpen.collectAsState()
    val activeBlueprintToSign by viewModel.activeBlueprintToSign.collectAsState()
    val activeUserMask by viewModel.activeUserMask.collectAsState()

    val ot = selectedWorkOrder ?: return

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Planos PDF & Firma, 1 = 6 Aprobadores, 2 = Historial y Trazabilidad

    Scaffold(
        modifier = Modifier.testTag("work_order_detail_screen"),
        containerColor = Color(0xFFF7F9FF),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = ot.id,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = ot.title,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color(0xFF0F172A))
                    }
                },
                actions = {
                    // Open Drive Link
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ot.driveFolderUrl))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Cloud, contentDescription = "Abrir Google Drive", tint = Color(0xFF2563EB))
                    }
                    // Share PC Screen Link
                    IconButton(onClick = {
                        val pcUrl = LocalHtmlGenerator.generatePcViewerWebLink(ot.id, ot.title, ot.driveFolderUrl)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Enlace para Revisar Plano en PC - ${ot.id}")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Revisar plano en pantalla grande PC:\n$pcUrl\n\nCarpeta Drive:\n${ot.driveFolderUrl}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir Enlace PC"))
                    }) {
                        Icon(Icons.Default.DesktopWindows, contentDescription = "Ver en PC Más Grande", tint = Color(0xFF10B981))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs Row in Bento style
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.White,
                contentColor = Color(0xFF2563EB)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Planos & Firma", color = if (activeTab == 0) Color(0xFF2563EB) else Color(0xFF64748B), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Description, contentDescription = null, tint = if (activeTab == 0) Color(0xFF2563EB) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("6 Aprobadores", color = if (activeTab == 1) Color(0xFF2563EB) else Color(0xFF64748B), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Group, contentDescription = null, tint = if (activeTab == 1) Color(0xFF2563EB) else Color(0xFF64748B)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Trazabilidad", color = if (activeTab == 2) Color(0xFF2563EB) else Color(0xFF64748B), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = if (activeTab == 2) Color(0xFF2563EB) else Color(0xFF64748B)) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // OT Header Info Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CATEGORÍA: ${ot.categoryDisplayName.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (ot.status == "APROBADO") Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (ot.status == "APROBADO") Color(0xFF16A34A) else Color(0xFFDC2626))
                                        )
                                        Text(
                                            text = if (ot.status == "APROBADO") "APTO PARA FABRICACIÓN" else "NO APTO PARA FABRICACIÓN",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (ot.status == "APROBADO") Color(0xFF15803D) else Color(0xFFB91C1C),
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ot.title,
                                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Área / Planta: ${ot.clientOrArea}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // PC Viewer Share Quick Banner
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Laptop, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(
                                                text = "Enlace para Revisar desde el PC",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF065F46),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = "Permite ver el plano en pantalla grande",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF047857), fontSize = 10.sp)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val pcUrl = LocalHtmlGenerator.generatePcViewerWebLink(ot.id, ot.title, ot.driveFolderUrl)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, pcUrl)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Enlace PC"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Copiar / Enviar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB CONTENT
                when (activeTab) {
                    0 -> {
                        // Blueprints & Canvas Signatures
                        items(blueprints, key = { it.id }) { bp ->
                            val bpLog = logs.find { it.blueprintId == bp.id }
                            BlueprintCanvasView(
                                blueprint = bp,
                                signerName = bpLog?.approverName ?: activeUserMask?.name,
                                signerRole = bpLog?.approverRole ?: activeUserMask?.roleTitle,
                                signerRut = bpLog?.approverRut ?: activeUserMask?.rut,
                                onSignClick = {
                                    viewModel.openBiometricSigningDialog(bp)
                                }
                            )
                        }
                    }

                    1 -> {
                        // 6 Approvers connected status
                        item {
                            Text(
                                text = "Estado de Revisión de los 6 Usuarios Aprobadores:",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        items(approvers.take(6)) { approver ->
                            val isSigned = logs.any { it.approverName == approver.name && it.status == "APROBADO" }
                            val userLog = logs.find { it.approverName == approver.name }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(if (isSigned) Color(0xFF10B981) else Color(0xFFCBD5E1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = approver.avatarInitials,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = if (approver.name.isNotBlank()) approver.name else "[Slot ${approver.id} - Sin Nombre Registrado]",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = if (approver.name.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = approver.roleTitle,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                            )
                                            Text(
                                                text = if (isSigned) "Validado con ${userLog?.biometricType ?: "Huella Biométrica"}" else "Pendiente de Revisión y Firma",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSigned) Color(0xFF059669) else Color(0xFFD97706),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSigned) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
                                    ) {
                                        Icon(
                                            imageVector = if (isSigned) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = if (isSigned) Color(0xFF059669) else Color(0xFFD97706),
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // Signature History & Traceability Timeline
                        item {
                            Text(
                                text = "Historial y Registro de Firma Biométrica (Trazabilidad):",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        if (logs.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Text(
                                        text = "Aún no hay registros de firmas para esta OT.",
                                        modifier = Modifier.padding(16.dp),
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        } else {
                            items(logs) { log ->
                                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = log.approverName,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = dateFormat.format(Date(log.timestamp)),
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                                            )
                                        }
                                        Text(
                                            text = "${log.approverRole} • ${log.biometricType}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "HASH CERTIFICADO: ${log.signatureHash}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF059669),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        )
                                        if (log.observations.isNotBlank()) {
                                            Text(
                                                text = "Observación: ${log.observations}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF334155))
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

    // Biometric Modal Dialog
    if (biometricModalOpen && activeBlueprintToSign != null) {
        BiometricPromptDialog(
            blueprint = activeBlueprintToSign!!,
            approvers = approvers,
            activeUserMask = activeUserMask,
            onDismiss = { viewModel.closeBiometricSigningDialog() },
            onConfirmSignature = { name, role, rut, bioType, obs ->
                viewModel.executeBiometricSignature(name, role, rut, bioType, obs) { hash ->
                    // Done
                }
            }
        )
    }
}
