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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class AdaptiveViewerTool(val label: String, val markupType: ReviewMarkupType?) {
    HAND("Navegar", null),
    SELECT("Seleccionar", null),
    TEXT("Texto", ReviewMarkupType.TEXT),
    SYMBOL("Símbolo", ReviewMarkupType.SYMBOL),
    DIMENSION("Cota", ReviewMarkupType.DIMENSION),
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
    val labelX: Float,
    val labelY: Float,
    val colorArgb: Int,
    val strokeWidth: Float,
    val opacity: Float,
    val points: List<ReviewPoint>
)

internal data class AdaptiveDrawingPreview(
    val type: ReviewMarkupType,
    val start: ReviewPoint,
    val end: ReviewPoint,
    val points: List<ReviewPoint> = emptyList(),
    val label: ReviewPoint = ReviewPoint(
        x = (start.x + end.x) / 2f,
        y = ((start.y + end.y) / 2f - 0.035f).coerceIn(0f, 1f)
    )
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
    labelX = labelX,
    labelY = labelY,
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
        .put("labelX", input.labelX.toDouble())
        .put("labelY", input.labelY.toDouble())
        .put("colorArgb", input.colorArgb.toLong())
        .put("strokeWidth", input.strokeWidth.toDouble())
        .put("opacity", input.opacity.toDouble())
        .put("points", points)
        .toString()
}

internal fun decodeAdaptiveMarkup(comment: PlanComment): AdaptiveMarkup {
    val fallbackLabelX = ((comment.x + comment.endX) / 2f).coerceIn(0f, 1f)
    val fallbackLabelY = (((comment.y + comment.endY) / 2f) - 0.035f).coerceIn(0f, 1f)
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
            labelX = fallbackLabelX,
            labelY = fallbackLabelY,
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
            labelX = json.optDouble("labelX", fallbackLabelX.toDouble()).toFloat(),
            labelY = json.optDouble("labelY", fallbackLabelY.toDouble()).toFloat(),
            colorArgb = json.optLong("colorArgb", comment.colorArgb.toLong()).toInt(),
            strokeWidth = json.optDouble("strokeWidth", comment.strokeWidth.toDouble()).toFloat(),
            opacity = json.optDouble("opacity", comment.opacity.toDouble()).toFloat(),
            points = points
        )
    }.getOrElse {
        AdaptiveMarkup(
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
            labelX = fallbackLabelX,
            labelY = fallbackLabelY,
            colorArgb = comment.colorArgb,
            strokeWidth = comment.strokeWidth,
            opacity = comment.opacity,
            points = comment.points
        )
    }
}

internal fun DrawScope.drawAdaptiveMarkup(markup: AdaptiveMarkup) {
    if (markup.type == ReviewMarkupType.TEXT || markup.type == ReviewMarkupType.SYMBOL) return
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
        ReviewMarkupType.DIMENSION -> drawAdaptiveDimension(markup, color, stroke)
        ReviewMarkupType.TEXT, ReviewMarkupType.SYMBOL -> Unit
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
        text = if (preview.type == ReviewMarkupType.DIMENSION) "COTA" else "",
        x = preview.start.x,
        y = preview.start.y,
        endX = preview.end.x,
        endY = preview.end.y,
        width = kotlin.math.abs(preview.end.x - preview.start.x),
        height = kotlin.math.abs(preview.end.y - preview.start.y),
        labelX = preview.label.x,
        labelY = preview.label.y,
        colorArgb = color.copy(alpha = 1f).toArgb(),
        strokeWidth = strokeWidth,
        opacity = opacity,
        points = preview.points
    )
    drawAdaptiveMarkup(temp)
}

private fun DrawScope.drawAdaptiveDimension(markup: AdaptiveMarkup, color: Color, stroke: Float) {
    val a = Offset(markup.x * size.width, markup.y * size.height)
    val b = Offset(markup.endX * size.width, markup.endY * size.height)
    val label = Offset(markup.labelX * size.width, markup.labelY * size.height)
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val nx = -uy
    val ny = ux
    val midpoint = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val offset = (label.x - midpoint.x) * nx + (label.y - midpoint.y) * ny
    val dimA = Offset(a.x + nx * offset, a.y + ny * offset)
    val dimB = Offset(b.x + nx * offset, b.y + ny * offset)

    drawLine(color, a, dimA, stroke * 0.72f, cap = StrokeCap.Square)
    drawLine(color, b, dimB, stroke * 0.72f, cap = StrokeCap.Square)
    drawLine(color, dimA, dimB, stroke, cap = StrokeCap.Square)
    drawAdaptiveArrowHead(color, dimA, atan2(dimB.y - dimA.y, dimB.x - dimA.x), stroke)
    drawAdaptiveArrowHead(color, dimB, atan2(dimA.y - dimB.y, dimA.x - dimB.x), stroke)
}

private fun DrawScope.drawAdaptiveArrow(color: Color, start: Offset, end: Offset, stroke: Float) {
    drawLine(color, start, end, stroke, cap = StrokeCap.Round)
    drawAdaptiveArrowHead(color, end, atan2(start.y - end.y, start.x - end.x), stroke)
}

private fun DrawScope.drawAdaptiveArrowHead(color: Color, tip: Offset, direction: Float, stroke: Float) {
    val length = (stroke * 6f).coerceAtLeast(18f)
    val spread = 0.50f
    val first = Offset(
        tip.x + length * cos(direction - spread),
        tip.y + length * sin(direction - spread)
    )
    val second = Offset(
        tip.x + length * cos(direction + spread),
        tip.y + length * sin(direction + spread)
    )
    drawLine(color, tip, first, stroke, cap = StrokeCap.Round)
    drawLine(color, tip, second, stroke, cap = StrokeCap.Round)
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
            ReviewMarkupType.LINE, ReviewMarkupType.ARROW, ReviewMarkupType.DIMENSION -> {
                adaptivePointSegmentDistance(
                    point,
                    ReviewPoint(markup.x, markup.y),
                    ReviewPoint(markup.endX, markup.endY)
                ) < 0.035f || adaptiveDistance(point, ReviewPoint(markup.labelX, markup.labelY)) < 0.06f
            }
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

internal fun adaptivePointDistance(a: ReviewPoint, b: ReviewPoint): Float = adaptiveDistance(a, b)

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
                val maxSide = 3600f
                val renderScale = min(3.0f, maxSide / maxOf(page.width, page.height).toFloat())
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

private const val ADAPTIVE_MARKUP_PREFIX = "@SKM_MARKUP_V3@"
