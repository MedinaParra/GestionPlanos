package com.example.document.drive

import com.example.document.model.DocumentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DriveRestClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    data class DriveFile(
        val id: String,
        val name: String,
        val webViewLink: String
    )

    suspend fun uploadPdf(
        accessToken: String,
        folderId: String,
        fileName: String,
        bytes: ByteArray
    ): DriveFile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "El PDF está vacío." }
        require(bytes.size <= MAX_PDF_BYTES) { "El PDF supera el límite de 40 MB de la aplicación." }

        val boundary = "skm-${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", PDF_MIME)
            .put("parents", JSONArray().put(folderId))
            .toString()

        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $PDF_MIME\r\n\r\n")
        }.toByteArray()
        val suffix = "\r\n--$boundary--\r\n".toByteArray()
        val bodyBytes = ByteArray(prefix.size + bytes.size + suffix.size)
        prefix.copyInto(bodyBytes, 0)
        bytes.copyInto(bodyBytes, prefix.size)
        suffix.copyInto(bodyBytes, prefix.size + bytes.size)

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,webViewLink")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        val json = executeJson(request)
        DriveFile(
            id = json.getString("id"),
            name = json.optString("name", fileName),
            webViewLink = json.optString("webViewLink")
        )
    }

    suspend fun downloadPdf(
        accessToken: String,
        driveFileId: String,
        target: File
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media&supportsAllDrives=true")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Drive respondió ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val body = response.body ?: error("Drive no devolvió el PDF.")
            target.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
        target
    }

    suspend fun createControlSpreadsheet(
        accessToken: String,
        folderId: String,
        spreadsheetName: String
    ): DriveFile = withContext(Dispatchers.IO) {
        val metadata = JSONObject()
            .put("name", spreadsheetName)
            .put("mimeType", SHEET_MIME)
            .put("parents", JSONArray().put(folderId))

        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id,name,webViewLink")
            .header("Authorization", "Bearer $accessToken")
            .post(metadata.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val json = executeJson(request)
        val file = DriveFile(
            id = json.getString("id"),
            name = json.optString("name", spreadsheetName),
            webViewLink = json.optString("webViewLink")
        )
        renameFirstSheet(accessToken, file.id)
        file
    }

    suspend fun rewriteControlSpreadsheet(
        accessToken: String,
        spreadsheetId: String,
        documents: List<DocumentRecord>
    ) = withContext(Dispatchers.IO) {
        clearControlSheet(accessToken, spreadsheetId)

        val values = JSONArray()
        values.put(JSONArray(listOf(
            "Código",
            "Archivo PDF",
            "Revisión",
            "Firmado",
            "Estado",
            "Firmado por",
            "Fecha firma",
            "Enlace Drive",
            "Última actualización"
        )))

        documents.sortedWith(compareBy<DocumentRecord> { it.code }.thenBy { it.fileName })
            .forEach { document ->
                values.put(JSONArray(listOf(
                    document.code,
                    document.fileName,
                    document.revision,
                    if (document.signed) "SÍ" else "NO",
                    document.status,
                    document.signedByName,
                    formatTimestamp(document.signedAt),
                    document.driveWebViewLink,
                    formatTimestamp(document.updatedAt)
                )))
            }

        val payload = JSONObject()
            .put("range", "Control!A1:I${values.length()}")
            .put("majorDimension", "ROWS")
            .put("values", values)

        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Control!A1:I${values.length()}?valueInputOption=USER_ENTERED")
            .header("Authorization", "Bearer $accessToken")
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeJson(request)
    }

    private fun renameFirstSheet(accessToken: String, spreadsheetId: String) {
        val updateProperties = JSONObject()
            .put("properties", JSONObject().put("sheetId", 0).put("title", "Control"))
            .put("fields", "title")
        val payload = JSONObject().put(
            "requests",
            JSONArray().put(JSONObject().put("updateSheetProperties", updateProperties))
        )

        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeJson(request)
    }

    private fun clearControlSheet(accessToken: String, spreadsheetId: String) {
        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Control!A:I:clear")
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Google API respondió ${response.code}: $body")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
    }

    companion object {
        private const val PDF_MIME = "application/pdf"
        private const val SHEET_MIME = "application/vnd.google-apps.spreadsheet"
        private const val MAX_PDF_BYTES = 40 * 1024 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
