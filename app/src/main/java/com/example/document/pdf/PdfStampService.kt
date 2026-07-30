package com.example.document.pdf

import android.content.Context
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
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class PdfStampService(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun addNoAptoWatermark(pdfBytes: ByteArray): ByteArray = edit(pdfBytes) { document, page ->
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.18f
            }
            stream.setGraphicsStateParameters(graphicsState)
            stream.setNonStrokingColor(210, 25, 25)
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA_BOLD, min(width, height) * 0.085f)
            stream.setTextMatrix(
                Matrix.getRotateInstance(
                    Math.toRadians(38.0),
                    width * 0.11f,
                    height * 0.34f
                )
            )
            stream.showText("NO APTO PARA FABRICACION")
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
        val blockWidth = (pageWidth * placement.width.coerceIn(0.18f, 0.46f))
        val blockHeight = blockWidth * 0.48f
        val x = (pageWidth * placement.x.coerceIn(0.01f, 0.98f)).coerceIn(4f, pageWidth - blockWidth - 4f)
        val topY = pageHeight * (1f - placement.y.coerceIn(0.02f, 0.95f))
        val y = (topY - blockHeight).coerceIn(4f, pageHeight - blockHeight - 4f)
        val image = PDImageXObject.createFromByteArray(document, signaturePng, "firma-${profile.email}")
        val date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CL")).format(Date(signedAt))
        val time = SimpleDateFormat("HH:mm:ss", Locale("es", "CL")).format(Date(signedAt))

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.setStrokingColor(25, 70, 130)
            stream.setNonStrokingColor(255, 255, 255)
            stream.setLineWidth(1.2f)
            stream.addRect(x, y, blockWidth, blockHeight)
            stream.fillAndStroke()

            val imageWidth = blockWidth * 0.42f
            val imageHeight = blockHeight * 0.42f
            stream.drawImage(image, x + 6f, y + blockHeight - imageHeight - 7f, imageWidth, imageHeight)

            stream.setNonStrokingColor(20, 55, 105)
            drawLine(stream, "FIRMADO / REVISADO", x + blockWidth * 0.46f, y + blockHeight - 15f, 8.5f, true)
            drawLine(stream, safe(profile.displayName), x + blockWidth * 0.46f, y + blockHeight - 29f, 7.2f, true)
            drawLine(stream, safe(profile.position), x + blockWidth * 0.46f, y + blockHeight - 41f, 6.5f, false)
            drawLine(stream, "RUT: ${safe(profile.rut)}", x + 6f, y + 24f, 6.5f, false)
            drawLine(stream, "Fecha: $date  Hora: $time", x + 6f, y + 12f, 6.5f, false)
            drawLine(stream, "SKM INDUSTRIAL", x + blockWidth * 0.62f, y + 4f, 6.2f, true)
        }
    }

    fun addAptoParaFabricacion(pdfBytes: ByteArray): ByteArray = edit(pdfBytes) { document, page ->
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        val stampWidth = width * 0.245f
        val stampHeight = stampWidth * 0.31f
        val x = (width - stampWidth - width * 0.055f).coerceAtLeast(width * 0.50f)
        val y = (height * 0.17f).coerceAtLeast(18f)
        val radius = stampHeight / 2f

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.96f
                strokingAlphaConstant = 0.96f
            }
            stream.setGraphicsStateParameters(graphicsState)
            stream.setStrokingColor(0, 118, 220)
            stream.setNonStrokingColor(255, 255, 255)
            stream.setLineWidth((stampHeight * 0.025f).coerceAtLeast(1.3f))
            addRoundedRect(stream, x, y, stampWidth, stampHeight, radius)
            stream.fillAndStroke()

            stream.setNonStrokingColor(0, 118, 220)
            val font = PDType1Font.HELVETICA_BOLD
            val fontSize = stampHeight * 0.245f
            drawCentered(stream, font, "APTO PARA", x, y + stampHeight * 0.57f, stampWidth, fontSize)
            drawCentered(stream, font, "FABRICACIÓN", x, y + stampHeight * 0.24f, stampWidth, fontSize)
        }
    }

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
        stream.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, size)
        stream.newLineAtOffset(x, y)
        stream.showText(safe(text).take(55))
        stream.endText()
    }

    private fun safe(value: String): String = value
        .replace('–', '-')
        .replace('—', '-')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
}
