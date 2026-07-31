package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.document.model.DocumentRecord
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.example.ui.theme.SkmGraphite
import com.example.ui.theme.SkmOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paso exclusivo entre la decisión de aprobar y la autenticación del teléfono.
 * Se representa como pantalla completa para que nunca quede oculto detrás del visor.
 */
@Composable
fun ApprovalSignaturePlacementScreen(
    file: File,
    document: DocumentRecord,
    profile: UserProfile,
    signatureFile: File?,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (SignaturePlacement) -> Unit
) {
    var pageBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var x by rememberSaveable(document.id) { mutableFloatStateOf(profile.placement.x) }
    var y by rememberSaveable(document.id) { mutableFloatStateOf(profile.placement.y) }
    var width by rememberSaveable(document.id) { mutableFloatStateOf(profile.placement.width.coerceIn(0.14f, 0.44f)) }
    val density = LocalDensity.current

    LaunchedEffect(file) {
        pageBitmap = renderApprovalPreview(file)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF1F3F5)),
        topBar = {
            Surface(shadowElevation = 5.dp, color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, "Cancelar ubicación")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Ubicar firma", fontWeight = FontWeight.Bold, color = SkmGraphite)
                        Text(
                            "OT ${document.otNumber} · ${document.code} · Rev ${document.revision}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 10.dp, color = Color.White) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tamaño completo", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${(width * 100).roundToInt()}%", color = SkmOrange, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = width,
                        onValueChange = { width = it },
                        valueRange = 0.14f..0.44f,
                        enabled = !busy
                    )
                    Button(
                        onClick = { onConfirm(SignaturePlacement(x, y, width)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Fingerprint, null)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(if (busy) "Guardando aprobación…" else "Aprobar con huella, rostro o PIN")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Arrastra el timbre completo a una zona libre. La barra modifica también la firma y las fuentes.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF24272B), RoundedCornerShape(12.dp))
                    .onGloballyPositioned { container = it.size },
                contentAlignment = Alignment.Center
            ) {
                val bitmap = pageBitmap
                if (bitmap == null) {
                    CircularProgressIndicator(color = SkmOrange)
                } else {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "Plano para ubicar firma",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    val containerWidth = container.width.toFloat().coerceAtLeast(1f)
                    val containerHeight = container.height.toFloat().coerceAtLeast(1f)
                    val fit = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
                    val pageWidthPx = bitmap.width * fit
                    val pageHeightPx = bitmap.height * fit
                    val pageLeftPx = (containerWidth - pageWidthPx) / 2f
                    val pageTopPx = (containerHeight - pageHeightPx) / 2f
                    val stampWidthPx = pageWidthPx * width
                    val stampHeightPx = stampWidthPx * 0.55f
                    val stampWidthDp = with(density) { stampWidthPx.toDp() }
                    val scaleFactor = (width / 0.30f).coerceIn(0.48f, 1.50f)
                    val maxX = (1f - width).coerceAtLeast(0f)
                    val maxY = (1f - stampHeightPx / pageHeightPx.coerceAtLeast(1f)).coerceAtLeast(0f)
                    val safeX = x.coerceIn(0f, maxX)
                    val safeY = y.coerceIn(0f, maxY)

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(stampWidthDp)
                            .offset {
                                IntOffset(
                                    (pageLeftPx + safeX * pageWidthPx).roundToInt(),
                                    (pageTopPx + safeY * pageHeightPx).roundToInt()
                                )
                            }
                            .pointerInput(container, width, busy) {
                                if (!busy) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        x = (x + drag.x / pageWidthPx.coerceAtLeast(1f)).coerceIn(0f, maxX)
                                        y = (y + drag.y / pageHeightPx.coerceAtLeast(1f)).coerceIn(0f, maxY)
                                    }
                                }
                            },
                        shape = RoundedCornerShape((5f * scaleFactor).dp),
                        color = Color.White.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke((1.2f * scaleFactor).dp, SkmOrange),
                        shadowElevation = 5.dp
                    ) {
                        Column(
                            Modifier.padding(
                                horizontal = (6f * scaleFactor).dp,
                                vertical = (4f * scaleFactor).dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "FIRMADO / REVISADO",
                                color = SkmOrange,
                                fontWeight = FontWeight.Black,
                                fontSize = (8.5f * scaleFactor).coerceAtLeast(5f).sp,
                                lineHeight = (9.5f * scaleFactor).coerceAtLeast(6f).sp,
                                textAlign = TextAlign.Center
                            )
                            HorizontalDivider(
                                Modifier.padding(vertical = (2f * scaleFactor).dp),
                                color = SkmOrange.copy(alpha = 0.55f)
                            )
                            if (signatureFile != null) {
                                AsyncImage(
                                    model = signatureFile,
                                    contentDescription = "Firma manual",
                                    modifier = Modifier.fillMaxWidth(0.78f).height((23f * scaleFactor).coerceAtLeast(12f).dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(
                                profile.displayName.ifBlank { profile.email },
                                color = SkmGraphite,
                                fontWeight = FontWeight.Bold,
                                fontSize = (8f * scaleFactor).coerceAtLeast(4.8f).sp,
                                lineHeight = (9f * scaleFactor).coerceAtLeast(5.5f).sp,
                                maxLines = 1
                            )
                            Text(
                                profile.position.ifBlank { "Cargo no informado" },
                                color = SkmGraphite,
                                fontSize = (7f * scaleFactor).coerceAtLeast(4.5f).sp,
                                lineHeight = (8f * scaleFactor).coerceAtLeast(5f).sp,
                                maxLines = 1
                            )
                            Text(
                                "RUT ${profile.rut.ifBlank { "no informado" }}",
                                color = SkmGraphite,
                                fontSize = (6.5f * scaleFactor).coerceAtLeast(4.2f).sp,
                                lineHeight = (7.5f * scaleFactor).coerceAtLeast(4.8f).sp,
                                maxLines = 1
                            )
                            Text(
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date()),
                                color = Color.Gray,
                                fontSize = (5.8f * scaleFactor).coerceAtLeast(4f).sp,
                                lineHeight = (6.8f * scaleFactor).coerceAtLeast(4.5f).sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun renderApprovalPreview(file: File): Bitmap = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                val scale = 1.8f
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
