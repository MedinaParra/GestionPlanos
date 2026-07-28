package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ApproverUserEntity
import com.example.data.local.entity.BlueprintEntity
import kotlinx.coroutines.delay

@Composable
fun BiometricPromptDialog(
    blueprint: BlueprintEntity,
    approvers: List<ApproverUserEntity>,
    onDismiss: () -> Unit,
    onConfirmSignature: (
        approverName: String,
        approverRole: String,
        approverRut: String,
        biometricType: String,
        observations: String
    ) -> Unit,
    activeUserMask: ApproverUserEntity? = null
) {
    val initialUser = activeUserMask ?: approvers.firstOrNull { it.name.isNotBlank() } ?: approvers.firstOrNull() ?: ApproverUserEntity("1", "Usuario Firmante", "Jefe de Taller Mecánico", "carlos@empresa.com", "carlos.g@gmail.com", "12.345.678-9")
    var selectedApprover by remember { mutableStateOf(initialUser) }
    var customSignerName by remember { mutableStateOf(initialUser.name.ifBlank { "Ing. Revisor OT" }) }
    var customSignerRut by remember { mutableStateOf(initialUser.rut.ifBlank { "12.345.678-9" }) }
    var biometricType by remember { mutableStateOf("HUELLA_BIOMETRICA") }
    var observations by remember { mutableStateOf("") }

    var isScanning by remember { mutableStateOf(false) }
    var scanSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(selectedApprover) {
        if (selectedApprover.name.isNotBlank()) {
            customSignerName = selectedApprover.name
        }
        if (selectedApprover.rut.isNotBlank()) {
            customSignerRut = selectedApprover.rut
        }
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(1200)
            isScanning = false
            scanSuccess = true
            delay(500)
            onConfirmSignature(
                customSignerName.ifBlank { selectedApprover.name.ifBlank { "Revisor Autorizado" } },
                selectedApprover.roleTitle,
                customSignerRut.ifBlank { selectedApprover.rut.ifBlank { "12.345.678-9" } },
                biometricType,
                observations
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("biometric_dialog"),
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Validación Biométrica",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Firma Digital de Plano: ${blueprint.id}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Selector de Revisor / Usuario (6 Usuarios Conectados)
                Text(
                    text = "Seleccionar Usuario Revisor (Aprobador):",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Bold
                    )
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        approvers.take(6).forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedApprover.id == user.id) Color(0xFFEFF6FF) else Color.Transparent)
                                    .clickable { selectedApprover = user }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2563EB)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (user.name.isNotBlank()) user.avatarInitials else user.id,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (user.name.isNotBlank()) user.name else "[Slot ${user.id} - Sin Nombre]",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (user.name.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = user.roleTitle,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                                        )
                                    }
                                }

                                if (selectedApprover.id == user.id) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Método de Verificación Biométrica Tab
                Text(
                    text = "Método de Verificación:",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF334155), fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = biometricType == "HUELLA_BIOMETRICA",
                        onClick = { biometricType = "HUELLA_BIOMETRICA" },
                        label = { Text("Huella") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = biometricType == "FACIAL_ID",
                        onClick = { biometricType = "FACIAL_ID" },
                        label = { Text("Facial") },
                        leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = biometricType == "PIN_SEGURIDAD",
                        onClick = { biometricType = "PIN_SEGURIDAD" },
                        label = { Text("PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.weight(0.8f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }

                // 3. Observations TextField
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Observación Técnica / Tolerancia (Opcional)") },
                    placeholder = { Text("Ej: Plano verificado, apto para tornero") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = Color(0xFF2563EB),
                        unfocusedLabelColor = Color(0xFF64748B),
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Biometric Scan Indicator
                if (isScanning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF2563EB),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Escaneando sensor biométrico...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Comprobando hash de identidad de ${selectedApprover.name}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                            )
                        }
                    }
                } else if (scanSuccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669))
                            Text(
                                text = "¡Identidad Validada y Firma Registrada!",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { isScanning = true },
                enabled = !isScanning && !scanSuccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("confirm_biometric_button")
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Validar y Firmar Plano", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isScanning
            ) {
                Text("Cancelar", color = Color(0xFF64748B))
            }
        }
    )
}
