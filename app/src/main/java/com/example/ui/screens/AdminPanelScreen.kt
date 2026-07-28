package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.excel.ExcelExporter
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHtmlForm: () -> Unit
) {
    val context = LocalContext.current
    val workOrders by viewModel.workOrders.collectAsState()
    val approvers by viewModel.approverUsers.collectAsState()
    val logs by viewModel.signatureLogs.collectAsState()
    val settings by viewModel.adminSettings.collectAsState()

    // Configurable state
    var driveFolder by remember(settings) { mutableStateOf(settings?.googleDriveBaseFolder ?: "Google Drive / Fabricaciones Manto OT") }
    var notificationEmails by remember(settings) { mutableStateOf(settings?.notificationEmails ?: "aprobaciones@empresa.com, taller.jefe@empresa.com, control.calidad@empresa.com") }
    var autoSendEmail by remember(settings) { mutableStateOf(settings?.autoSendEmailOnApproval ?: true) }
    var deadlineReminderHours by remember(settings) { mutableStateOf((settings?.deadlineReminderHours ?: 48).toString()) }

    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Analytics calculations
    val totalOts = workOrders.size
    val approvedOts = workOrders.count { it.status == "APROBADO" }
    val pendingOts = totalOts - approvedOts
    val nearDeadlineOts = workOrders.count { it.isNearDeadline }

    // Average hours calculation
    val avgHours = remember(workOrders, logs) {
        if (workOrders.isEmpty()) 0L else {
            val totalHours = workOrders.sumOf { ot ->
                (System.currentTimeMillis() - ot.createdAt) / (1000 * 3600)
            }
            totalHours / workOrders.size
        }
    }

    Scaffold(
        modifier = Modifier.testTag("admin_panel_screen"),
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF2563EB))
                        }
                        Text(
                            text = "Panel Administrador y Métricas",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color(0xFF0F172A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Export Excel Report Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1FAE5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(
                                    text = "Informe en Excel / CSV de Firma",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Reporte detallado: planos firmados, pendientes, tiempos y revisores",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.exportExcelReport { file ->
                                    ExcelExporter.shareCsvFile(context, file)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_excel_report_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generar y Exportar Reporte a Excel / CSV", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Metrics Overview Cards Grid
            item {
                Text(
                    text = "Métricas de Control de Tiempos y Avance OT:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Tiempo Promedio OT",
                        value = "${avgHours}h",
                        subtitle = "Contando tiempo",
                        icon = Icons.Default.Timer,
                        iconBg = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total OT Creadas",
                        value = "$totalOts",
                        subtitle = "Sincronizadas Drive",
                        icon = Icons.Default.Folder,
                        iconBg = Color(0xFFE0F2FE),
                        iconTint = Color(0xFF0284C7),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Firmados / Aprobados",
                        value = "$approvedOts",
                        subtitle = "6 Firmas completadas",
                        icon = Icons.Default.Verified,
                        iconBg = Color(0xFFD1FAE5),
                        iconTint = Color(0xFF059669),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Alertas Vencimiento",
                        value = "$nearDeadlineOts",
                        subtitle = "Próximos a vencer",
                        icon = Icons.Default.Warning,
                        iconBg = Color(0xFFFEE2E2),
                        iconTint = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Bottlenecks / Pending Approvers List
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Control de Desempeño por Usuario",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        approvers.take(6).forEach { user ->
                            val userSignedCount = logs.count { it.approverName == user.name }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = user.name, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold))
                                    Text(text = user.roleTitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B)))
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        text = "$userSignedCount Firmas",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Email Configuration Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mail, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Configuración de Notificaciones por Correo",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        OutlinedTextField(
                            value = notificationEmails,
                            onValueChange = { notificationEmails = it },
                            label = { Text("Casillas de Correo Electrónico Configurables") },
                            placeholder = { Text("email1@empresa.com, email2@empresa.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Switch(
                                checked = autoSendEmail,
                                onCheckedChange = { autoSendEmail = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2563EB))
                            )
                            Text(
                                text = "Enviar correo automáticamente al completar las 6 firmas",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            )
                        }

                        OutlinedTextField(
                            value = driveFolder,
                            onValueChange = { driveFolder = it },
                            label = { Text("Ruta Base de Carpetas en Google Drive") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.updateAdminSettings(
                                    driveFolder = driveFolder,
                                    emails = notificationEmails,
                                    autoSend = autoSendEmail,
                                    deadlineHours = deadlineReminderHours.toIntOrNull() ?: 48,
                                    biometricRequired = true
                                )
                                saveSuccessMessage = "Configuración guardada exitosamente"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_settings_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar Configuración de Administrador", fontWeight = FontWeight.Bold)
                        }

                        saveSuccessMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = Color(0xFF059669),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. HTML Local Form Link & Generator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Formulario Web HTML Local Integrado",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Enviar solicitudes OT a la App y Google Drive sin servidor",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                            )
                        }

                        Button(
                            onClick = onNavigateToHtmlForm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver HTML", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium.copy(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold))
            Text(text = title, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF334155), fontWeight = FontWeight.Bold))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 10.sp))
        }
    }
}
