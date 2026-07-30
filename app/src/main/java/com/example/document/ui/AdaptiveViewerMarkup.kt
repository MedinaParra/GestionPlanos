package com.example.document.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.document.model.PlanComment
import com.example.document.model.ReviewMarkupInput
import com.example.document.model.ReviewMarkupType
import com.example.document.model.ReviewPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class AdaptiveViewerTool(val label: String, val markupType: ReviewMarkupType?) {
    HAND("Navegar", null),
    SELECT("Seleccionar", null),
    TEXT("Texto", ReviewMarkupType.TEXT),
    FREEHAND("Lápiz", ReviewMarkupType.FREEHAND),
    HIGHLIGHT("Resaltador", ReviewMarkupType.HIGHLIGHT),
    LINE("Línea", ReviewMarkupType.LINE),
    ARROW("Flecha", ReviewMarkupType.ARROW),
    RECTANGLE("Rectángulo", ReviewMarkupType.RECTANGLE),
    ELLIPSE("Elipse", ReviewMarkupType.ELLIPSE),
    CLOUD("Nube", ReviewMarkupType.CLOUD);

    val editsDocument: Boolean get() = markupType != null
}

internal data class AdaptiveMarkup(
    val source: PlanComment,
    val clientId: String,
    val type: ReviewMarkupType,
    val text: String,
    val x: Float,
    val y: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
    val height: Float,
    val colorArgb: Int,
    val strokeWidth: Float,
    val opacity: Float,
    val points: List<ReviewPoint>
)

internal data class AdaptiveDrawingPreview(
    val type: ReviewMarkupType,
    val start: ReviewPoint,
    val end: ReviewPoint,
    val points: List<ReviewPoint> = emptyList()
)

internal fun AdaptiveMarkup.toAdaptiveInput(): ReviewMarkupInput = ReviewMarkupInput(
    pageIndex = source.pageIndex,
    type = type,
    text = text,
    x = x,
    y = y,
    endX = endX,
    endY = endY,
    width = width,
    height = height,
    colorArgb = colorArgb,
    strokeWidth = strokeWidth,
    opacity = opacity,
    points = points
)

internal fun encodeAdaptiveMarkup(input: ReviewMarkupInput, clientId: String): String {
    val points = JSONArray()
    input.points.forEach { point ->
        points.put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
    }
    return ADAPTIVE_MARKUP_PREFIX + JSONObject()
        .put("clientId", clientId)
        .put("type", input.type.name)
        .put("text", input.text)
        .put("endX", input.endX.toDouble())
        .put("endY", input.endY.toDouble())
        .put("height", input.height.toDouble())
        .put("colorArgb", input.colorArgb.toLong())
        .put("strokeWidth", input.strokeWidth.toDouble())
        .put("opacity", input.opacity.toDouble())
        .put("points", points)
        .toString()
}

internal fun decodeAdaptiveMarkup(comment: PlanComment): AdaptiveMarkup {
    if (!comment.text.startsWith(ADAPTIVE_MARKUP_PREFIX)) {
        return AdaptiveMarkup(
            source = comment,
            clientId = comment.id,
            type = comment.markupType,
            text = comment.text,
            x = comment.x,
            y = comment.y,
            endX = comment.endX,
            endY = comment.endY,
            width = comment.width,
            height = comment.height,
            colorArgb = comment.colorArgb,
            strokeWidth = comment.strokeWidth,
            opacity = comment.opacity,
            points = comment.points
        )
    }
    return runCatching {
        val json = JSONObject(comment.text.removePrefix(ADAPTIVE_MARKUP_PREFIX))
        val pointsArray = json.optJSONArray("points") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointsArray.length()) {
                val item = pointsArray.optJSONObject(index) ?: continue
                add(ReviewPoint(item.optDouble("x").toFloat(), item.optDouble("y").toFloat()))
            }
        }
        AdaptiveMarkup(
            source = comment,
            clientId = json.optString("clientId", comment.id),
            type = runCatching {
                ReviewMarkupType.valueOf(json.optString("type", comment.markupType.name))
            }.getOrDefault(comment.markupType),
            text = json.optString("text"),
            x = comment.x,
            y = comment.y,
            endX = json.optDouble("endX", comment.endX.toDouble()).toFloat(),
            endY = json.optDouble("endY", comment.endY.toDouble()).toFloat(),
            width = comment.width,
            height = json.optDouble("height", comment.height.toDouble()).toFloat(),
            colorArgb = json.optLong("colorArgb", comment.colorArgb.toLong()).toInt(),
            strokeWidth = json.optDouble("strokeWidth", comment.strokeWidth.toDouble()).toFloat(),
            opacity = json.optDouble("opacity", comment.opacity.toDouble()).toFloat(),
            points = points
        )
    }.getOrElse {
        AdaptiveMarkup(
            comment,
            comment.id,
            comment.markupType,
            comment.text,
            comment.x,
            comment.y,
            comment.endX,
            comment.endY,
            comment.width,
            comment.height,
            comment.colorArgb,
            comment.strokeWidth,
            comment.opacity,
            comment.points
        )
    }
}

internal fun DrawScope.drawAdaptiveMarkup(markup: AdaptiveMarkup) {
    if (markup.type == ReviewMarkupType.TEXT) return
    val color = Color(markup.colorArgb).copy(alpha = markup.opacity.coerceIn(0.08f, 1f))
    val stroke = (markup.strokeWidth * size.minDimension).coerceAtLeast(2f)
    val start = Offset(markup.x * size.width, markup.y * size.height)
    val end = Offset(markup.endX * size.width, markup.endY * size.height)
    val left = minOf(markup.x, markup.endX) * size.width
    val top = minOf(markup.y, markup.endY) * size.height
    val right = maxOf(markup.x, markup.endX) * size.width
    val bottom = maxOf(markup.y, markup.endY) * size.height

    when (markup.type) {
        ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> {
            val points = markup.points.map { Offset(it.x * size.width, it.y * size.height) }
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        ReviewMarkupType.LINE -> drawLine(color, start, end, stroke, cap = StrokeCap.Round)
        ReviewMarkupType.ARROW -> drawAdaptiveArrow(color, start, end, stroke)
        ReviewMarkupType.RECTANGLE -> drawRect(color, Offset(left, top), Size(right - left, bottom - top), style = Stroke(stroke))
        ReviewMarkupType.ELLIPSE -> drawOval(color, Offset(left, top), Size(right - left, bottom - top), style = Stroke(stroke))
        ReviewMarkupType.CLOUD -> drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 3f),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2f, stroke * 1.4f)))
        )
        ReviewMarkupType.TEXT -> Unit
    }
}

internal fun DrawScope.drawAdaptivePreview(
    preview: AdaptiveDrawingPreview,
    color: Color,
    strokeWidth: Float,
    opacity: Float
) {
    val temp = AdaptiveMarkup(
        source = PlanComment(),
        clientId = "preview",
        type = preview.type,
        text = "",
        x = preview.start.x,
        y = preview.start.y,
        endX = preview.end.x,
        endY = preview.end.y,
        width = kotlin.math.abs(preview.end.x - preview.start.x),
        height = kotlin.math.abs(preview.end.y - preview.start.y),
        colorArgb = color.copy(alpha = 1f).toArgb(),
        strokeWidth = strokeWidth,
        opacity = opacity,
        points = preview.points
    )
    drawAdaptiveMarkup(temp)
}

private fun DrawScope.drawAdaptiveArrow(color: Color, start: Offset, end: Offset, stroke: Float) {
    drawLine(color, start, end, stroke, cap = StrokeCap.Round)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val length = (stroke * 6f).coerceAtLeast(18f)
    val spread = 0.55f
    val first = Offset(
        end.x - length * cos(angle - spread),
        end.y - length * sin(angle - spread)
    )
    val second = Offset(
        end.x - length * cos(angle + spread),
        end.y - length * sin(angle + spread)
    )
    drawLine(color, end, first, stroke, cap = StrokeCap.Round)
    drawLine(color, end, second, stroke, cap = StrokeCap.Round)
}

private fun Color.toArgb(): Int = AndroidColor.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)

internal fun adaptiveHitTest(markups: List<AdaptiveMarkup>, point: ReviewPoint): PlanComment? {
    return markups.asReversed().firstOrNull { markup ->
        when (markup.type) {
            ReviewMarkupType.FREEHAND, ReviewMarkupType.HIGHLIGHT -> markup.points.any { adaptiveDistance(it, point) < 0.025f }
            ReviewMarkupType.LINE, ReviewMarkupType.ARROW -> adaptivePointSegmentDistance(
                point,
                ReviewPoint(markup.x, markup.y),
                ReviewPoint(markup.endX, markup.endY)
            ) < 0.025f
            else -> {
                val left = minOf(markup.x, markup.endX)
                val top = minOf(markup.y, markup.endY)
                val right = maxOf(markup.x + markup.width, markup.endX)
                val bottom = maxOf(markup.y + markup.height, markup.endY)
                point.x in (left - 0.02f)..(right + 0.02f) && point.y in (top - 0.02f)..(bottom + 0.02f)
            }
        }
    }?.source
}

private fun adaptiveDistance(a: ReviewPoint, b: ReviewPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun adaptivePointSegmentDistance(p: ReviewPoint, a: ReviewPoint, b: ReviewPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return adaptiveDistance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return adaptiveDistance(p, ReviewPoint(a.x + t * dx, a.y + t * dy))
}

internal suspend fun renderAdaptivePage(file: File, index: Int): Pair<Bitmap, Int> = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = index.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val maxSide = 3200f
                val renderScale = min(2.7f, maxSide / maxOf(page.width, page.height).toFloat())
                val bitmap = Bitmap.createBitmap(
                    (page.width * renderScale).roundToInt().coerceAtLeast(1),
                    (page.height * renderScale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap to renderer.pageCount
            }
        }
    }
}

private const val ADAPTIVE_MARKUP_PREFIX = "@SKM_MARKUP_V2@"
