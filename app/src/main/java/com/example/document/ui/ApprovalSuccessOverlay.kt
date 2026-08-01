package com.example.document.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.document.model.DocumentRecord
import com.example.ui.theme.SkmOrange
import com.example.ui.theme.SkmSuccess
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private data class ApprovalParticle(
    val side: Int,
    val lane: Float,
    val delay: Float,
    val travel: Float,
    val size: Float,
    val color: Color
)

/** Se muestra solamente después de que Drive confirma y guarda la aprobación. */
@Composable
fun ApprovalSuccessOverlay(document: DocumentRecord, onFinished: () -> Unit) {
    val progress = remember(document.id, document.updatedAt) { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val colors = remember {
        listOf(
            SkmOrange,
            SkmSuccess,
            Color(0xFF1565C0),
            Color(0xFFFFC107),
            Color(0xFF7B1FA2)
        )
    }
    val particles = remember(document.id, document.updatedAt) {
        List(38) { index ->
            ApprovalParticle(
                side = if (index % 2 == 0) -1 else 1,
                lane = ((index * 37) % 100) / 100f,
                delay = ((index * 11) % 25) / 100f,
                travel = 0.30f + ((index * 17) % 28) / 100f,
                size = 5f + ((index * 7) % 8),
                color = colors[index % colors.size]
            )
        }
    }

    LaunchedEffect(document.id, document.updatedAt) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1550, easing = FastOutSlowInEasing))
        delay(180)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            particles.forEach { particle ->
                val local = ((p - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (local > 0f && local < 1f) {
                    val startX = if (particle.side < 0) size.width * 0.01f else size.width * 0.99f
                    val direction = if (particle.side < 0) 1f else -1f
                    val x = startX + direction * size.width * particle.travel * local
                    val baseY = size.height * (0.16f + particle.lane * 0.62f)
                    val y = baseY + sin((local * PI * 2.1 + particle.lane * 4.8)).toFloat() * size.height * 0.045f + local * size.height * 0.12f
                    val alpha = (1f - local).coerceIn(0f, 1f)
                    drawRect(
                        particle.color.copy(alpha = alpha),
                        topLeft = Offset(x - particle.size / 2f, y - particle.size / 2f),
                        size = Size(particle.size, particle.size * 1.65f)
                    )
                }
            }
        }

        val p = progress.value
        val fadeIn = (p / 0.20f).coerceIn(0f, 1f)
        val fadeOut = ((1f - p) / 0.12f).coerceIn(0f, 1f)
        val scale = 0.84f + 0.16f * fadeIn
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 370.dp)
                .graphicsLayer {
                    alpha = fadeIn * fadeOut
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(25.dp),
            color = Color.White.copy(alpha = 0.98f),
            shadowElevation = 18.dp
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 21.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = SkmSuccess, modifier = Modifier.size(62.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "Aprobado", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                Text(
                    if (document.completed) "Plano apto para fabricación" else "Aprobación registrada",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 13.dp)
                )
                Text(
                    "${document.code} · Rev ${document.revision}",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
