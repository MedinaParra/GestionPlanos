package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BlueprintEntity

@Composable
fun BlueprintCanvasView(
    blueprint: BlueprintEntity,
    modifier: Modifier = Modifier,
    signerName: String? = null,
    signerRole: String? = null,
    signerRut: String? = null,
    onSignClick: () -> Unit
) {
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var drawPaths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("blueprint_canvas_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Blueprint Title & Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = blueprint.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Text(
                        text = "Versión: ${blueprint.revision} • Categoría: Industrial Cad PDF",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel + 0.2f).coerceAtMost(2.5f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Acercar", tint = Color(0xFF0F172A))
                    }
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel - 0.2f).coerceAtLeast(0.8f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Alejar", tint = Color(0xFF0F172A))
                    }
                    IconButton(
                        onClick = { drawPaths = emptyList() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Limpiar Trazo", tint = Color(0xFFD97706))
                    }
                }
            }

            // Blueprint Blueprint Drawing Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF0A1120))
                    .padding(8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath = newPath
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentPath?.let { path ->
                                        val position = change.position
                                        path.lineTo(position.x, position.y)
                                        // Trigger redraw
                                        currentPath = Path().apply { addPath(path) }
                                    }
                                },
                                onDragEnd = {
                                    currentPath?.let { path ->
                                        drawPaths = drawPaths + path
                                        currentPath = null
                                    }
                                }
                            )
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw Industrial Grid Lines
                    val gridSize = 40f * zoomLevel
                    var x = 0f
                    while (x < width) {
                        drawLine(
                            color = Color(0xFF1E3A5F),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1f
                        )
                        x += gridSize
                    }
                    var y = 0f
                    while (y < height) {
                        drawLine(
                            color = Color(0xFF1E3A5F),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                        y += gridSize
                    }

                    // 2. Draw Technical Mechanical Component Representation
                    val centerX = width / 2f
                    val centerY = height / 2f

                    // Outer shaft circle / mantle profile
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 80f * zoomLevel,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 50f * zoomLevel,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = Color(0xFF64B5F6),
                        radius = 20f * zoomLevel,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.5f)
                    )

                    // Dimension Center Crosshairs
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(centerX - 110f * zoomLevel, centerY),
                        end = Offset(centerX + 110f * zoomLevel, centerY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(centerX, centerY - 110f * zoomLevel),
                        end = Offset(centerX, centerY + 110f * zoomLevel),
                        strokeWidth = 1f
                    )

                    // 3. User Drawn Signature Paths
                    for (path in drawPaths) {
                        drawPath(
                            path = path,
                            color = Color(0xFFFFD600),
                            style = Stroke(width = 4f)
                        )
                    }
                    currentPath?.let { path ->
                        drawPath(
                            path = path,
                            color = Color(0xFFFFD600),
                            style = Stroke(width = 4f)
                        )
                    }
                }

                // Official Industrial Stamp (TIMBRE DE FABRICACIÓN SKM INDUSTRIAL)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (blueprint.isSigned) Color(0xF0022C22) else Color(0xF0450A0A))
                        .border(
                            width = 2.dp,
                            color = if (blueprint.isSigned) Color(0xFF22C55E) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (blueprint.isSigned) Icons.Default.Verified else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (blueprint.isSigned) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (blueprint.isSigned) "TIMBRE: APTO PARA FABRICACIÓN" else "TIMBRE: PENDIENTE DE APROBACIÓN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (blueprint.isSigned) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = if (blueprint.isSigned) Color(0xFF15803D) else Color(0xFF991B1B),
                            thickness = 1.dp
                        )
                        Text(
                            text = "NOMBRE: ${if (blueprint.isSigned) (signerName ?: "Revisor Técnico") else "PENDIENTE"}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "RUT: ${if (blueprint.isSigned) (signerRut ?: "12.345.678-9") else "--"}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = "CARGO: ${if (blueprint.isSigned) (signerRole ?: "Jefe de Taller Mecánico") else "--"}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCBD5E1), fontSize = 9.sp)
                        )
                        Text(
                            text = "FIRMA: ${if (blueprint.isSigned) "${blueprint.signatureHash ?: "BIO-VALIDATED"} (${blueprint.signatureDate ?: "FECHA"})" else "NO REGISTRADA"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (blueprint.isSigned) Color(0xFF86EFAC) else Color(0xFFFECACA),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Canvas Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (blueprint.isSigned) Icons.Default.CheckCircle else Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (blueprint.isSigned) Color(0xFF059669) else Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (blueprint.isSigned) "Plano Firmado (1/1 Firma Única Registrada)" else "Timbrar con Biometría (Única Vez)",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                    )
                }

                Button(
                    onClick = onSignClick,
                    enabled = !blueprint.isSigned,
                    modifier = Modifier.testTag("sign_blueprint_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (blueprint.isSigned) Color(0xFF64748B) else Color(0xFF2563EB),
                        disabledContainerColor = Color(0xFF059669),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (blueprint.isSigned) Icons.Default.Lock else Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (blueprint.isSigned) "Timbrado Completo (Firma Única)" else "Aplicar Timbre y Firma", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
