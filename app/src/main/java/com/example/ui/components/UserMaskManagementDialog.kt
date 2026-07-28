package com.example.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ApproverUserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMaskManagementDialog(
    approvers: List<ApproverUserEntity>,
    activeUser: ApproverUserEntity?,
    onDismiss: () -> Unit,
    onSelectActiveUser: (ApproverUserEntity) -> Unit,
    onSaveUserMask: (
        id: String,
        name: String,
        roleTitle: String,
        rut: String,
        email: String,
        googleAccount: String
    ) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var selectedSlotId by remember { mutableStateOf(approvers.firstOrNull()?.id ?: "USR-1") }

    // Form inputs
    var inputName by remember { mutableStateOf("") }
    var inputRole by remember { mutableStateOf("Jefe de Taller Mecánico") }
    var inputRut by remember { mutableStateOf("") }
    var inputEmail by remember { mutableStateOf("") }
    var inputGoogleAccount by remember { mutableStateOf("") }

    // Pre-fill form if editing slot
    val selectedUser = approvers.find { it.id == selectedSlotId }
    LaunchedEffect(selectedSlotId) {
        selectedUser?.let { u ->
            inputName = u.name
            inputRole = u.roleTitle
            inputRut = u.rut
            inputEmail = u.email
            inputGoogleAccount = u.googleAccount.ifBlank { if (u.email.isNotBlank()) u.email else "usuario.drive@gmail.com" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("user_mask_management_dialog"),
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Máscaras de Gestión Google Drive",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Crear usuarios y seleccionar sesión activa",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active User Session Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SESIÓN ACTIVA ACTUAL (LOGUEO DRIVE)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF0369A1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                            Text(
                                text = if (!activeUser?.name.isNullOrBlank()) activeUser!!.name else "Sin Usuario Logueado (Seleccionar abajo)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = Color(0xFF0C4A6E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                            if (!activeUser?.roleTitle.isNullOrBlank()) {
                                Text(
                                    text = "${activeUser.roleTitle} • ${activeUser.googleAccount.ifBlank { activeUser.email }}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF0369A1),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }

                if (!showCreateForm) {
                    // List of 6 Slots / Users
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seleccionar o Editar Usuario (6 Slots):",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        TextButton(onClick = { showCreateForm = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar Datos Slot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(approvers) { user ->
                                val isSelectedActive = activeUser?.id == user.id
                                val isBlank = user.name.isBlank()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelectedActive) Color(0xFFE0F2FE) else Color.White)
                                        .border(
                                            width = if (isSelectedActive) 1.5.dp else 1.dp,
                                            color = if (isSelectedActive) Color(0xFF0284C7) else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedSlotId = user.id
                                            if (!isBlank) {
                                                onSelectActiveUser(user)
                                            } else {
                                                showCreateForm = true
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(if (isBlank) Color(0xFFCBD5E1) else Color(0xFF2563EB)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isBlank) "--" else user.avatarInitials.ifBlank { user.name.take(2).uppercase() },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = if (isBlank) "[Slot ${user.id} Sin Nombre]" else user.name,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (isBlank) Color(0xFF94A3B8) else Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            Text(
                                                text = user.roleTitle,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF64748B),
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }

                                    if (isSelectedActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF0284C7)
                                        ) {
                                            Text(
                                                text = "LOGUEADO",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                selectedSlotId = user.id
                                                showCreateForm = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isBlank) "+ Asignar" else "Editar",
                                                fontSize = 10.sp,
                                                color = Color(0xFF2563EB)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Form to Register / Update User Mask
                    Text(
                        text = "Registrar / Modificar Datos del Usuario:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.Bold
                        )
                    )

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nombre Completo del Usuario") },
                        placeholder = { Text("Ej: Ing. Pedro Ramírez") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = inputRole,
                        onValueChange = { inputRole = it },
                        label = { Text("Cargo / Rol Técnico") },
                        placeholder = { Text("Ej: Jefe de Taller, Inspector QA") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = inputRut,
                        onValueChange = { inputRut = it },
                        label = { Text("RUT del Usuario (Obligatorio para Timbre)") },
                        placeholder = { Text("Ej: 12.345.678-9") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Correo Electrónico Empresarial") },
                        placeholder = { Text("pedro.ramirez@empresa.cl") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = inputGoogleAccount,
                        onValueChange = { inputGoogleAccount = it },
                        label = { Text("Cuenta Google / Máscara de Drive") },
                        placeholder = { Text("pedro.ramirez.drive@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showCreateForm = false }) {
                            Text("Volver", color = Color(0xFF64748B))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputName.isNotBlank()) {
                                    onSaveUserMask(
                                        selectedSlotId,
                                        inputName.trim(),
                                        inputRole.trim(),
                                        inputRut.trim(),
                                        inputEmail.trim(),
                                        inputGoogleAccount.trim().ifBlank { inputEmail.trim() }
                                    )
                                    showCreateForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar Usuario", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showCreateForm) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
