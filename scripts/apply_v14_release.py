from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def write(path, text):
    (ROOT / path).write_text(text, encoding="utf-8")

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

# 1) Version
path = "app/build.gradle.kts"
text = read(path)
text = replace_once(text, 'versionCode = 13\n    versionName = "13.0.0"', 'versionCode = 14\n    versionName = "14.0.0"', "version")
write(path, text)

# 2) Model: smaller signature default and persisted placement for each approval
path = "app/src/main/java/com/example/document/model/DocumentModels.kt"
text = read(path)
text = text.replace(
    'val x: Float = 0.62f,\n    val y: Float = 0.73f,\n    val width: Float = 0.30f',
    'val x: Float = 0.70f,\n    val y: Float = 0.78f,\n    val width: Float = 0.16f',
    1
)
text = replace_once(
    text,
    '    val signatureFileId: String = "",\n    val signedPdfFileId: String = ""\n)',
    '    val signatureFileId: String = "",\n    val signedPdfFileId: String = "",\n    val placement: SignaturePlacement = SignaturePlacement()\n)',
    "approval placement model",
)
write(path, text)

# 3) Stamp service: clean final PDF, transparent stamps, centered NO APTO and free-space placement
path = "app/src/main/java/com/example/document/pdf/PdfStampService.kt"
pdf_service = r'''package com.example.document.pdf

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
'''
write(path, pdf_service)

# 4) Signature placement UI: 7%-32%, transparent preview and no shadow
path = "app/src/main/java/com/example/document/ui/ApprovalSignaturePlacementScreen.kt"
text = read(path)
text = text.replace('profile.placement.width.coerceIn(0.14f, 0.44f)', 'profile.placement.width.coerceIn(0.07f, 0.32f)')
text = text.replace('valueRange = 0.14f..0.44f', 'valueRange = 0.07f..0.32f')
text = text.replace('val scaleFactor = (width / 0.30f).coerceIn(0.48f, 1.50f)', 'val scaleFactor = (width / 0.20f).coerceIn(0.35f, 1.60f)')
text = text.replace('val stampHeightPx = stampWidthPx * 0.55f', 'val stampHeightPx = stampWidthPx * 0.46f')
text = text.replace('color = Color.White.copy(alpha = 0.95f),', 'color = Color.White.copy(alpha = 0.26f),')
text = text.replace('border = androidx.compose.foundation.BorderStroke((1.2f * scaleFactor).dp, SkmOrange),', 'border = androidx.compose.foundation.BorderStroke((1.0f * scaleFactor).dp, Color(0xAA1976D2)),')
text = text.replace('shadowElevation = 5.dp', 'shadowElevation = 0.dp')
write(path, text)

# 5) Document repository: persist placement and build final from clean original
path = "app/src/main/java/com/example/document/data/DocumentRepository.kt"
text = read(path)
text = replace_once(
    text,
    '''            signatureFileId = user.profile.signatureFileId,
            signedPdfFileId = signedCopy.id
        )''',
    '''            signatureFileId = user.profile.signatureFileId,
            signedPdfFileId = signedCopy.id,
            placement = placement
        )''',
    "approval creation",
)
text = replace_once(
    text,
    '''        val updatedDocument = if (isFinal) {
            val finalBytes = pdfStamp.addAptoParaFabricacion(stamped)
            val finalFile = binary.upload(''',
    '''        val updatedDocument = if (isFinal) {
            // El PDF final se reconstruye desde el original limpio: así desaparece
            // completamente la marca provisional NO APTO.
            var cleanFinalBytes = binary.downloadBytes(accessToken, document.originalPdfFileId)
            approvals.forEach { savedApproval ->
                val savedSignature = binary.downloadBytes(accessToken, savedApproval.signatureFileId)
                val savedProfile = UserProfile(
                    email = savedApproval.email,
                    displayName = savedApproval.name,
                    rut = savedApproval.rut,
                    position = savedApproval.position,
                    signatureFileId = savedApproval.signatureFileId
                )
                cleanFinalBytes = pdfStamp.addApprovalSignature(
                    cleanFinalBytes,
                    savedSignature,
                    savedProfile,
                    savedApproval.placement,
                    savedApproval.signedAt
                )
            }
            val finalBytes = pdfStamp.addAptoParaFabricacion(cleanFinalBytes)
            val finalFile = binary.upload(''',
    "clean final reconstruction",
)
text = replace_once(
    text,
    '''                            .put("signatureFileId", approval.signatureFileId)
                            .put("signedPdfFileId", approval.signedPdfFileId)''',
    '''                            .put("signatureFileId", approval.signatureFileId)
                            .put("signedPdfFileId", approval.signedPdfFileId)
                            .put("placementX", approval.placement.x.toDouble())
                            .put("placementY", approval.placement.y.toDouble())
                            .put("placementWidth", approval.placement.width.toDouble())''',
    "serialize approval placement",
)
text = replace_once(
    text,
    '''                        signatureFileId = item.optString("signatureFileId"),
                        signedPdfFileId = item.optString("signedPdfFileId")
                    )''',
    '''                        signatureFileId = item.optString("signatureFileId"),
                        signedPdfFileId = item.optString("signedPdfFileId"),
                        placement = SignaturePlacement(
                            x = item.optDouble("placementX", 0.70).toFloat(),
                            y = item.optDouble("placementY", 0.78).toFloat(),
                            width = item.optDouble("placementWidth", 0.16).toFloat()
                        )
                    )''',
    "parse approval placement",
)
write(path, text)

# 6) Workflow action parser also keeps placement
path = "app/src/main/java/com/example/document/data/WorkflowActionRepository.kt"
text = read(path)
text = text.replace(
    'import com.example.document.model.SessionUser\n',
    'import com.example.document.model.SessionUser\nimport com.example.document.model.SignaturePlacement\n',
    1
)
text = replace_once(
    text,
    '''                        signatureFileId = item.optString("signatureFileId"),
                        signedPdfFileId = item.optString("signedPdfFileId")
                    )''',
    '''                        signatureFileId = item.optString("signatureFileId"),
                        signedPdfFileId = item.optString("signedPdfFileId"),
                        placement = SignaturePlacement(
                            x = item.optDouble("placementX", 0.70).toFloat(),
                            y = item.optDouble("placementY", 0.78).toFloat(),
                            width = item.optDouble("placementWidth", 0.16).toFloat()
                        )
                    )''',
    "workflow parser placement",
)
write(path, text)

# 7) UI state carries an automatic reauthorization request
path = "app/src/main/java/com/example/document/ui/DocumentViewModel.kt"
text = read(path)
text = replace_once(
    text,
    '''    val driveConnected: Boolean = false,
    val busy: Boolean = false,''',
    '''    val driveConnected: Boolean = false,
    val authorizationRequestId: Long = 0L,
    val busy: Boolean = false,''',
    "ui auth request",
)
text = text.replace(
    '''            workspace.documents,
            workspace.settings
        )''',
    '''            workspace.documents,
            workspace.settings,
            workspace.configuration
        )'''
)
write(path, text)

# 8) Workflow VM: automatic 401 recovery and retry
path = "app/src/main/java/com/example/document/ui/WorkflowViewModel.kt"
text = read(path)
text = replace_once(
    text,
    '    private var driveAccessToken: String? = null\n',
    '''    private var driveAccessToken: String? = null
    private var pendingAuthorizedAction: (suspend () -> Unit)? = null
''',
    "pending authorized action",
)
text = replace_once(
    text,
    '''    fun setDriveAccessToken(token: String) {
        driveAccessToken = token
        launchBusy {
            val workspace = documentRepository.connect(token)
            applyWorkspace(workspace, "Google Drive conectado.")
            loadProfileAssets()
        }
    }''',
    '''    fun setDriveAccessToken(token: String) {
        driveAccessToken = token
        val retry = pendingAuthorizedAction
        pendingAuthorizedAction = null
        launchBusy(allowAuthorizationRetry = false) {
            if (retry != null) {
                retry()
            } else {
                val workspace = documentRepository.connect(token)
                applyWorkspace(workspace, "Google Drive conectado.")
                loadProfileAssets()
            }
        }
    }''',
    "set token retry",
)
text = replace_once(
    text,
    '''    fun reportDriveAuthorizationError(error: Throwable) {
        _uiState.update { it.copy(error = error.userMessage(), driveConnected = false, busy = false) }
    }''',
    '''    fun reportDriveAuthorizationError(error: Throwable) {
        pendingAuthorizedAction = null
        _uiState.update { it.copy(error = error.userMessage(), busy = false) }
    }''',
    "auth error",
)
text = text.replace(
    '''            workspace.documents,
            workspace.settings
        )''',
    '''            workspace.documents,
            workspace.settings,
            workspace.configuration
        )'''
)
text = replace_once(
    text,
    '''    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
            _uiState.update { it.copy(busy = false) }
        }
    }''',
    '''    private fun launchBusy(
        allowAuthorizationRetry: Boolean = true,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (error: Throwable) {
                if (allowAuthorizationRetry && error.isExpiredAuthorization()) {
                    pendingAuthorizedAction = block
                    driveAccessToken = null
                    _uiState.update {
                        it.copy(
                            authorizationRequestId = System.currentTimeMillis(),
                            message = "La sesión de Drive venció. Renovando autorización automáticamente…",
                            error = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = error.userMessage()) }
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun Throwable.isExpiredAuthorization(): Boolean {
        val details = generateSequence(this as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return details.contains("401") ||
            details.contains("unauthenticated") ||
            details.contains("invalid credentials") ||
            details.contains("authentication credentials")
    }''',
    "launch busy auth retry",
)
write(path, text)

# 9) Main activity reacts to silent refresh request
path = "app/src/main/java/com/example/MainActivity.kt"
text = read(path)
text = text.replace(
    'import androidx.lifecycle.compose.collectAsStateWithLifecycle\n',
    'import androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport androidx.compose.runtime.LaunchedEffect\n',
    1
)
text = replace_once(
    text,
    '''                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                val timeline = viewModel.timeline.collectAsStateWithLifecycle().value
                WorkflowDocumentApp(''',
    '''                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                val timeline = viewModel.timeline.collectAsStateWithLifecycle().value
                LaunchedEffect(state.authorizationRequestId) {
                    if (state.authorizationRequestId > 0L) requestDriveAuthorization()
                }
                WorkflowDocumentApp(''',
    "auto reauthorization effect",
)
write(path, text)

# 10) Background notifications with silent Drive sync
path = "app/src/main/java/com/example/document/notifications/ReviewReminderWorker.kt"
worker = r'''package com.example.document.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.document.drive.DriveRestClient
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.WorkflowSettings
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousIds = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
        val refreshed = refreshPendingFromDrive(prefs)
        val snapshot = refreshed ?: PendingSnapshot(
            count = prefs.getInt(KEY_PENDING, 0),
            oldest = prefs.getLong(KEY_OLDEST, 0L),
            ids = previousIds
        )

        if (refreshed != null) {
            saveSnapshot(prefs, refreshed)
            val newlyAssigned = refreshed.ids - previousIds
            if (newlyAssigned.isNotEmpty()) {
                postPendingNotification(applicationContext, refreshed.count, escalated = false, newAssignment = true)
            }
        }

        if (snapshot.count <= 0) return Result.success()

        val morning = prefs.getInt(KEY_MORNING, 8)
        val afternoon = prefs.getInt(KEY_AFTERNOON, 15)
        val escalationHours = prefs.getInt(KEY_ESCALATION, 36)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val escalated = snapshot.oldest > 0L &&
            now - snapshot.oldest >= escalationHours * 60L * 60L * 1000L
        val shouldNotify = hour == morning || hour == afternoon || escalated
        val hourBucket = now / (60L * 60L * 1000L)
        val lastBucket = prefs.getLong(KEY_LAST_NOTIFICATION_BUCKET, -1L)

        if (shouldNotify && lastBucket != hourBucket) {
            postPendingNotification(applicationContext, snapshot.count, escalated, newAssignment = false)
            prefs.edit().putLong(KEY_LAST_NOTIFICATION_BUCKET, hourBucket).apply()
        }
        return Result.success()
    }

    private suspend fun refreshPendingFromDrive(prefs: SharedPreferences): PendingSnapshot? {
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val indexFileId = prefs.getString(KEY_INDEX_FILE_ID, "").orEmpty()
        if (email.isBlank() || indexFileId.isBlank()) return null

        return runCatching {
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope(SHEETS_SCOPE)))
                .filterByHostedDomain("skmindustrial.cl")
                .build()
            val result = Identity.getAuthorizationClient(applicationContext)
                .authorize(request)
                .await()
            if (result.hasResolution()) {
                postReconnectNotification(applicationContext, prefs)
                return null
            }
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                postReconnectNotification(applicationContext, prefs)
                return null
            }

            val root = JSONObject(DriveRestClient().readTextFile(token, indexFileId))
            val documents = root.optJSONArray("documents") ?: JSONArray()
            val ids = linkedSetOf<String>()
            var oldest = 0L
            for (index in 0 until documents.length()) {
                val item = documents.optJSONObject(index) ?: continue
                if (item.optString("status") != "EN_REVISIÓN") continue
                val reviewers = item.optJSONArray("requiredReviewerEmails") ?: JSONArray()
                val reviewerIndex = item.optInt("currentReviewerIndex")
                val currentReviewer = reviewers.optString(reviewerIndex)
                if (!currentReviewer.equals(email, ignoreCase = true)) continue
                ids += item.optString("id")
                val uploadedAt = item.optLong("uploadedAt")
                if (uploadedAt > 0L && (oldest == 0L || uploadedAt < oldest)) oldest = uploadedAt
            }
            PendingSnapshot(ids.size, oldest, ids)
        }.getOrElse {
            null
        }
    }

    companion object {
        private const val PREFS = "skm_review_reminders"
        private const val KEY_PENDING = "pending"
        private const val KEY_OLDEST = "oldest"
        private const val KEY_PENDING_IDS = "pending_ids"
        private const val KEY_EMAIL = "email"
        private const val KEY_INDEX_FILE_ID = "index_file_id"
        private const val KEY_MORNING = "morning"
        private const val KEY_AFTERNOON = "afternoon"
        private const val KEY_ESCALATION = "escalation"
        private const val KEY_LAST_NOTIFICATION_BUCKET = "last_notification_bucket"
        private const val KEY_LAST_RECONNECT_NOTICE = "last_reconnect_notice"
        private const val CHANNEL_ID = "skm_pending_reviews_v2"
        private const val NOTIFICATION_ID = 1701
        private const val RECONNECT_NOTIFICATION_ID = 1702
        private const val UNIQUE_WORK = "skm-review-reminder-hourly-v2"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

        fun schedule(context: Context) {
            createChannel(context)
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun updateCache(
            context: Context,
            email: String,
            documents: List<DocumentRecord>,
            settings: WorkflowSettings,
            configuration: DriveConfiguration
        ) {
            val pending = documents.filter { it.canBeSignedBy(email) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousIds = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
            val currentIds = pending.mapTo(linkedSetOf()) { it.id }

            prefs.edit()
                .putInt(KEY_PENDING, pending.size)
                .putLong(KEY_OLDEST, pending.minOfOrNull { it.uploadedAt } ?: 0L)
                .putStringSet(KEY_PENDING_IDS, currentIds)
                .putString(KEY_EMAIL, email)
                .putString(KEY_INDEX_FILE_ID, configuration.indexFileId)
                .putInt(KEY_MORNING, settings.morningHour)
                .putInt(KEY_AFTERNOON, settings.afternoonHour)
                .putInt(KEY_ESCALATION, settings.escalationAfterHours)
                .apply()

            val newAssignments = currentIds - previousIds
            if (newAssignments.isNotEmpty()) {
                postPendingNotification(context, pending.size, escalated = false, newAssignment = true)
            }
            schedule(context)
        }

        private fun saveSnapshot(prefs: SharedPreferences, snapshot: PendingSnapshot) {
            prefs.edit()
                .putInt(KEY_PENDING, snapshot.count)
                .putLong(KEY_OLDEST, snapshot.oldest)
                .putStringSet(KEY_PENDING_IDS, snapshot.ids)
                .apply()
        }

        private fun postPendingNotification(
            context: Context,
            count: Int,
            escalated: Boolean,
            newAssignment: Boolean
        ) {
            createChannel(context)
            if (
                android.os.Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            val pendingIntent = appPendingIntent(context)
            val title = when {
                newAssignment -> "Nuevo plano asignado para revisión"
                escalated -> "Revisión de plano atrasada"
                else -> "Revisiones de planos pendientes"
            }
            val body = if (count == 1) {
                "Tienes 1 plano pendiente: revisa, comenta, aprueba o solicita cambios."
            } else {
                "Tienes $count planos pendientes: revisa, comenta, aprueba o solicita cambios."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private fun postReconnectNotification(context: Context, prefs: SharedPreferences) {
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST_RECONNECT_NOTICE, 0L)
            if (now - last < 12L * 60L * 60L * 1000L) return
            if (
                android.os.Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            createChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Reconecta Google Drive")
                .setContentText("Abre Gestión de Planos para renovar el acceso y continuar recibiendo revisiones.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(appPendingIntent(context))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(RECONNECT_NOTIFICATION_ID, notification)
            prefs.edit().putLong(KEY_LAST_RECONNECT_NOTICE, now).apply()
        }

        private fun appPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                1701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Revisiones de planos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos del sistema para nuevos planos, pendientes y revisiones atrasadas"
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
        }
    }

    private data class PendingSnapshot(
        val count: Int,
        val oldest: Long,
        val ids: Set<String>
    )
}
'''
write(path, worker)

# 11) Manifest and notification icon
path = "app/src/main/AndroidManifest.xml"
text = read(path)
text = replace_once(
    text,
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n',
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n',
    "boot permission",
)
write(path, text)

icon_path = ROOT / "app/src/main/res/drawable/ic_notification.xml"
icon_path.parent.mkdir(parents=True, exist_ok=True)
icon_path.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M6,2h8l4,4v5.1c-0.6,-0.3 -1.3,-0.5 -2,-0.6V7h-3V4H6v16h5.1c0.3,0.8 0.8,1.4 1.4,2H4V2h2zM16.5,12A4.5,4.5 0,1 1,16.5 21A4.5,4.5 0,0 1,16.5 12zM15.8,18.8l3.2,-3.2 -1,-1 -2.2,2.2 -1.2,-1.2 -1,1z" />
</vector>
''', encoding="utf-8")

print("v14 release changes applied")
