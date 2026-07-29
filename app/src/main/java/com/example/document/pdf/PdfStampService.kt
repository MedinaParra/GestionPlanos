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
        val stampWidth = width * 0.42f
        val stampHeight = stampWidth * 0.20f
        val x = (width - stampWidth) / 2f
        val y = height * 0.48f
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            val graphicsState = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = 0.78f
                strokingAlphaConstant = 0.85f
            }
            stream.setGraphicsStateParameters(graphicsState)
            stream.setStrokingColor(0, 80, 185)
            stream.setNonStrokingColor(235, 245, 255)
            stream.setLineWidth(3f)
            stream.addRect(x, y, stampWidth, stampHeight)
            stream.fillAndStroke()
            stream.setNonStrokingColor(0, 65, 170)
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA_BOLD, stampWidth * 0.073f)
            stream.newLineAtOffset(x + stampWidth * 0.055f, y + stampHeight * 0.42f)
            stream.showText("APTO PARA FABRICACION")
            stream.endText()
        }
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
        stream.showText(text.take(55))
        stream.endText()
    }

    private fun safe(value: String): String = value
        .replace('–', '-')
        .replace('—', '-')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
}
