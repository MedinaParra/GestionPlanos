package com.example.document.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class PdfStampService(context: Context) {
    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    fun addNoAptoWatermark(pdfBytes: ByteArray): ByteArray = edit(pdfBytes) { document, page ->
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        val text = "NO APTO PARA FABRICACION"
        val font = PDType1Font.HELVETICA_BOLD
        val diagonal = hypot(width.toDouble(), height.toDouble()).toFloat()
        val maxTextLength = diagonal * 0.72f
        val calculatedSize = maxTextLength * 1000f / font.getStringWidth(text)
        val fontSize = min(min(width, height) * 0.075f, calculatedSize).coerceAtLeast(18f)
        val textWidth = font.getStringWidth(text) / 1000f * fontSize

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.12f
            }
            stream.setGraphicsStateParameters(graphicsState)
            stream.setNonStrokingColor(210, 25, 25)
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setTextMatrix(
                Matrix.getRotateInstance(
                    Math.toRadians(42.0),
                    width * 0.50f,
                    height * 0.50f
                )
            )
            stream.newLineAtOffset(-textWidth / 2f, -fontSize * 0.32f)
            stream.showText(text)
            stream.endText()
        }
    }

    fun addApprovalSignature(
        pdfBytes: ByteArray,
        signaturePng: ByteArray,
        profile: UserProfile,
        placement: SignaturePlacement,
        signedAt: Long
    ): ByteArray = edit(pdfBytes) { document, page ->
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val normalizedWidth = placement.width.coerceIn(0.07f, 0.34f)
        val blockWidth = pageWidth * normalizedWidth
        val blockHeight = blockWidth * 0.46f
        val stampScale = (normalizedWidth / 0.20f).coerceIn(0.35f, 1.70f)
        val x = (pageWidth * placement.x.coerceIn(0.0f, 0.99f))
            .coerceIn(3f, pageWidth - blockWidth - 3f)
        val topY = pageHeight * (1f - placement.y.coerceIn(0.0f, 0.99f))
        val y = (topY - blockHeight).coerceIn(3f, pageHeight - blockHeight - 3f)
        val image = PDImageXObject.createFromByteArray(document, signaturePng, "firma-${profile.email}")
        val date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CL")).format(Date(signedAt))
        val time = SimpleDateFormat("HH:mm:ss", Locale("es", "CL")).format(Date(signedAt))

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val fillState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.20f
            }
            stream.setGraphicsStateParameters(fillState)
            stream.setNonStrokingColor(255, 255, 255)
            stream.addRect(x, y, blockWidth, blockHeight)
            stream.fill()

            val inkState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.70f
                strokingAlphaConstant = 0.70f
            }
            stream.setGraphicsStateParameters(inkState)
            stream.setStrokingColor(25, 95, 170)
            stream.setLineWidth((0.95f * stampScale).coerceAtLeast(0.45f))
            stream.addRect(x, y, blockWidth, blockHeight)
            stream.stroke()

            val imageWidth = blockWidth * 0.40f
            val imageHeight = blockHeight * 0.42f
            stream.drawImage(
                image,
                x + 4f * stampScale,
                y + blockHeight - imageHeight - 5f * stampScale,
                imageWidth,
                imageHeight
            )

            stream.setNonStrokingColor(20, 70, 135)
            drawLine(stream, "FIRMADO / REVISADO", x + blockWidth * 0.44f, y + blockHeight - 11f * stampScale, 7.2f * stampScale, true)
            drawLine(stream, safe(profile.displayName), x + blockWidth * 0.44f, y + blockHeight - 22f * stampScale, 6.1f * stampScale, true)
            drawLine(stream, safe(profile.position), x + blockWidth * 0.44f, y + blockHeight - 31f * stampScale, 5.5f * stampScale, false)
            drawLine(stream, "RUT: ${safe(profile.rut)}", x + 4f * stampScale, y + 16f * stampScale, 5.2f * stampScale, false)
            drawLine(stream, "Fecha: $date  Hora: $time", x + 4f * stampScale, y + 7f * stampScale, 4.9f * stampScale, false)
        }
    }

    fun addAptoParaFabricacion(pdfBytes: ByteArray): ByteArray {
        val positions = findClearStampPositions(pdfBytes)
        require(pdfBytes.isNotEmpty()) { "El PDF está vacío." }

        PDDocument.load(pdfBytes).use { document ->
            val stripper = PDFTextStripper()
            document.pages.forEachIndexed { index, page ->
                val pageText = runCatching {
                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    stripper.getText(document)
                }.getOrDefault("")
                val normalized = normalize(pageText)
                if (normalized.contains("APTO PARA FABRICACION") && !normalized.contains("NO APTO PARA FABRICACION")) {
                    return@forEachIndexed
                }

                val width = page.mediaBox.width
                val height = page.mediaBox.height
                val position = positions.getOrNull(index) ?: ClearPosition(0.76f, 0.72f, 0.18f)
                val stampWidth = width * position.width
                val stampHeight = stampWidth * 0.30f
                val x = (width * position.x).coerceIn(4f, width - stampWidth - 4f)
                val topY = height * position.top
                val y = (height - topY - stampHeight).coerceIn(4f, height - stampHeight - 4f)
                val radius = stampHeight / 2f

                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    val fillState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = 0.12f
                    }
                    stream.setGraphicsStateParameters(fillState)
                    stream.setNonStrokingColor(255, 255, 255)
                    addRoundedRect(stream, x, y, stampWidth, stampHeight, radius)
                    stream.fill()

                    val inkState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = 0.62f
                        strokingAlphaConstant = 0.62f
                    }
                    stream.setGraphicsStateParameters(inkState)
                    stream.setStrokingColor(0, 118, 220)
                    stream.setNonStrokingColor(0, 118, 220)
                    stream.setLineWidth((stampHeight * 0.020f).coerceAtLeast(0.8f))
                    addRoundedRect(stream, x, y, stampWidth, stampHeight, radius)
                    stream.stroke()

                    val font = PDType1Font.HELVETICA_BOLD
                    val fontSize = stampHeight * 0.235f
                    drawCentered(stream, font, "APTO PARA", x, y + stampHeight * 0.57f, stampWidth, fontSize)
                    drawCentered(stream, font, "FABRICACIÓN", x, y + stampHeight * 0.25f, stampWidth, fontSize)
                }
            }
            document.documentCatalog.cosObject.setNeedToBeUpdated(true)
            document.documentInformation.cosObject.setItem(COSName.MOD_DATE, null)
            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun findClearStampPositions(pdfBytes: ByteArray): List<ClearPosition> {
        val temp = File.createTempFile("skm-final-", ".pdf", appContext.cacheDir)
        return try {
            temp.writeBytes(pdfBytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    buildList {
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val maxSide = 900f
                                val scale = min(maxSide / page.width.toFloat(), maxSide / page.height.toFloat()).coerceAtMost(1f)
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * scale).roundToInt().coerceAtLeast(1),
                                    (page.height * scale).roundToInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                add(selectClearPosition(bitmap, page.width >= page.height))
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            temp.delete()
        }
    }

    private fun selectClearPosition(bitmap: Bitmap, landscape: Boolean): ClearPosition {
        val stampWidth = if (landscape) 0.18f else 0.27f
        val stampHeight = stampWidth * 0.30f * bitmap.width / bitmap.height.toFloat()
        val candidates = listOf(
            ClearPosition(0.76f, 0.72f, stampWidth),
            ClearPosition(0.76f, 0.08f, stampWidth),
            ClearPosition(0.05f, 0.72f, stampWidth),
            ClearPosition(0.05f, 0.08f, stampWidth),
            ClearPosition(0.41f, 0.72f, stampWidth),
            ClearPosition(0.41f, 0.08f, stampWidth),
            ClearPosition(0.76f, 0.40f, stampWidth),
            ClearPosition(0.05f, 0.40f, stampWidth)
        )
        return candidates.minByOrNull { candidate ->
            inkRatio(bitmap, candidate.x, candidate.top, candidate.width, stampHeight)
        } ?: candidates.first()
    }

    private fun inkRatio(bitmap: Bitmap, xNorm: Float, topNorm: Float, widthNorm: Float, heightNorm: Float): Float {
        val left = (bitmap.width * xNorm).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * topNorm).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * (xNorm + widthNorm)).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * (topNorm + heightNorm)).roundToInt().coerceIn(top + 1, bitmap.height)
        var ink = 0
        var samples = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val color = bitmap.getPixel(x, y)
                val luminance = Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f
                if (luminance < 222f) ink++
                samples++
                x += 3
            }
            y += 3
        }
        return if (samples == 0) 1f else ink.toFloat() / samples
    }

    private data class ClearPosition(
        val x: Float,
        val top: Float,
        val width: Float
    )

    private fun addRoundedRect(
        stream: PDPageContentStream,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float
    ) {
        val k = 0.5522848f
        val r = radius.coerceAtMost(min(width, height) / 2f)
        stream.moveTo(x + r, y)
        stream.lineTo(x + width - r, y)
        stream.curveTo(x + width - r + r * k, y, x + width, y + r - r * k, x + width, y + r)
        stream.lineTo(x + width, y + height - r)
        stream.curveTo(x + width, y + height - r + r * k, x + width - r + r * k, y + height, x + width - r, y + height)
        stream.lineTo(x + r, y + height)
        stream.curveTo(x + r - r * k, y + height, x, y + height - r + r * k, x, y + height - r)
        stream.lineTo(x, y + r)
        stream.curveTo(x, y + r - r * k, x + r - r * k, y, x + r, y)
        stream.closePath()
    }

    private fun drawCentered(
        stream: PDPageContentStream,
        font: PDType1Font,
        text: String,
        x: Float,
        baselineY: Float,
        width: Float,
        fontSize: Float
    ) {
        val safeText = safe(text)
        val textWidth = font.getStringWidth(safeText) / 1000f * fontSize
        stream.beginText()
        stream.setFont(font, fontSize)
        stream.newLineAtOffset(x + (width - textWidth) / 2f, baselineY)
        stream.showText(safeText)
        stream.endText()
    }

    private fun edit(pdfBytes: ByteArray, action: (PDDocument, PDPage) -> Unit): ByteArray {
        require(pdfBytes.isNotEmpty()) { "El PDF está vacío." }
        PDDocument.load(pdfBytes).use { document ->
            document.pages.forEach { page -> action(document, page) }
            document.documentCatalog.cosObject.setNeedToBeUpdated(true)
            document.documentInformation.cosObject.setItem(COSName.MOD_DATE, null)
            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun drawLine(
        stream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        bold: Boolean
    ) {
        stream.beginText()
        stream.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, size.coerceAtLeast(2.8f))
        stream.newLineAtOffset(x, y)
        stream.showText(safe(text).take(55))
        stream.endText()
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.uppercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun safe(value: String): String = value
        .replace('–', '-')
        .replace('—', '-')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
}
