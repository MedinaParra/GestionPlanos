package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOtDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var otNumber by remember { mutableStateOf("OT-2026-089") }
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("eje") }
    var clientOrArea by remember { mutableStateOf("Taller Mecánico Central") }
    var deadlineDaysStr by remember { mutableStateOf("3") }
    var pdfName by remember { mutableStateOf("PLANO-FABRICACION-COMPONENTE.pdf") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_ot_dialog"),
        containerColor = Color(0xFF1E293B),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
                Text(
                    text = "Nueva Orden de Trabajo / Plano",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = otNumber,
                    onValueChange = { otNumber = it },
                    label = { Text("Código OT") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título / Nombre del Componente") },
                    placeholder = { Text("Ej: Fabricación Polea Motriz 500mm") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Category dropdown / chips
                Text(
                    text = "Categoría de Fabricación:",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("manto", "eje", "poleas", "sellos", "armado_taller").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    when (cat) {
                                        "manto" -> "Manto"
                                        "eje" -> "Eje"
                                        "poleas" -> "Polea"
                                        "sellos" -> "Sello"
                                        else -> "Armado"
                                    },
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0072C6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = clientOrArea,
                    onValueChange = { clientOrArea = it },
                    label = { Text("Área o Planta Destino") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = deadlineDaysStr,
                    onValueChange = { deadlineDaysStr = it },
                    label = { Text("Días Límite para Aprobación") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = pdfName,
                    onValueChange = { pdfName = it },
                    label = { Text("Nombre Archivo Plano PDF") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && otNumber.isNotBlank()) {
                        viewModel.createNewOtFromForm(
                            otNumber = otNumber,
                            title = title,
                            category = selectedCategory,
                            clientOrArea = clientOrArea,
                            deadlineDays = deadlineDaysStr.toIntOrNull() ?: 3,
                            pdfName = pdfName
                        )
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072C6)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_create_ot_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Crear OT en Drive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        }
    )
}
