package com.example.document.drive

import com.example.document.model.DocumentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    data class DriveUser(
        val permissionId: String,
        val email: String,
        val displayName: String
    )

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val webViewLink: String = ""
    )

    data class FolderInfo(
        val id: String,
        val name: String,
        val webViewLink: String,
        val canEdit: Boolean,
        val canAddChildren: Boolean,
        val canDownload: Boolean
    )

    suspend fun getCurrentUser(accessToken: String): DriveUser = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/about"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress,permissionId)")
            .build()
        val json = executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        )
        val user = json.getJSONObject("user")
        DriveUser(
            permissionId = user.optString("permissionId"),
            email = user.optString("emailAddress"),
            displayName = user.optString("displayName").ifBlank {
                user.optString("emailAddress").substringBefore('@')
            }
        )
    }

    suspend fun getFolderInfo(accessToken: String, folderId: String): FolderInfo =
        withContext(Dispatchers.IO) {
            val url = "https://www.googleapis.com/drive/v3/files/$folderId"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter(
                    "fields",
                    "id,name,mimeType,webViewLink,capabilities(canEdit,canAddChildren,canDownload)"
                )
                .build()
            val json = executeJson(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
            )
            require(json.optString("mimeType") == FOLDER_MIME) {
                "El enlace seleccionado no corresponde a una carpeta de Google Drive."
            }
            val capabilities = json.optJSONObject("capabilities") ?: JSONObject()
            FolderInfo(
                id = json.getString("id"),
                name = json.optString("name", "Carpeta de planos"),
                webViewLink = json.optString("webViewLink"),
                canEdit = capabilities.optBoolean("canEdit"),
                canAddChildren = capabilities.optBoolean("canAddChildren"),
                canDownload = capabilities.optBoolean("canDownload", true)
            )
        }

    suspend fun findOrCreateFolder(
        accessToken: String,
        parentId: String,
        name: String
    ): DriveFile = withContext(Dispatchers.IO) {
        findFileByName(accessToken, parentId, name, FOLDER_MIME)
            ?: createMetadataFile(
                accessToken = accessToken,
                parentId = parentId,
                name = name,
                mimeType = FOLDER_MIME
            )
    }

    suspend fun findFileByName(
        accessToken: String,
        parentId: String,
        name: String,
        mimeType: String? = null
    ): DriveFile? = withContext(Dispatchers.IO) {
        val query = buildString {
            append("'")
            append(escapeQuery(parentId))
            append("' in parents and name = '")
            append(escapeQuery(name))
            append("' and trashed = false")
            if (!mimeType.isNullOrBlank()) {
                append(" and mimeType = '")
                append(escapeQuery(mimeType))
                append("'")
            }
        }
        val url = "https://www.googleapis.com/drive/v3/files"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("includeItemsFromAllDrives", "true")
            .addQueryParameter("pageSize", "10")
            .addQueryParameter("fields", "files(id,name,mimeType,webViewLink)")
            .build()
        val json = executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        )
        val files = json.optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) null else files.getJSONObject(0).toDriveFile()
    }

    suspend fun uploadPdf(
        accessToken: String,
        folderId: String,
        fileName: String,
        bytes: ByteArray
    ): DriveFile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "El PDF está vacío." }
        require(bytes.size <= MAX_PDF_BYTES) { "El PDF supera el límite de 40 MB de la aplicación." }
        createContentFile(accessToken, folderId, fileName, PDF_MIME, bytes)
    }

    suspend fun createTextFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        content: String
    ): DriveFile = withContext(Dispatchers.IO) {
        createContentFile(
            accessToken,
            folderId,
            fileName,
            mimeType,
            content.toByteArray(Charsets.UTF_8)
        )
    }

    suspend fun readTextFile(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("alt", "media")
                .addQueryParameter("supportsAllDrives", "true")
                .build()
            executeText(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
            )
        }

    suspend fun updateTextFile(
        accessToken: String,
        fileId: String,
        mimeType: String,
        content: String
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        val json = executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .patch(content.toRequestBody(mimeType.toMediaType()))
                .build()
        )
        json.toDriveFile()
    }

    suspend fun renameFile(
        accessToken: String,
        fileId: String,
        newName: String
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        val json = executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .patch(JSONObject().put("name", newName).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
        json.toDriveFile()
    }

    suspend fun downloadPdf(
        accessToken: String,
        driveFileId: String,
        target: File
    ): File = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$driveFileId"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("alt", "media")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        ).execute().use { response ->
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
        val file = createMetadataFile(accessToken, folderId, spreadsheetName, SHEET_MIME)
        renameFirstSheet(accessToken, file.id)
        file
    }

    suspend fun rewriteControlSpreadsheet(
        accessToken: String,
        spreadsheetId: String,
        documents: List<DocumentRecord>
    ) = withContext(Dispatchers.IO) {
        clearControlSheet(accessToken, spreadsheetId)

        val values = JSONArray().put(
            JSONArray(
                listOf(
                    "Código",
                    "Archivo PDF",
                    "Revisión",
                    "Firmado",
                    "Estado",
                    "Firmado por",
                    "Correo firmante",
                    "Fecha firma",
                    "Método de confirmación",
                    "Enlace Drive",
                    "Última actualización"
                )
            )
        )

        documents.sortedWith(compareBy<DocumentRecord> { it.code }.thenBy { it.fileName })
            .forEach { document ->
                values.put(
                    JSONArray(
                        listOf(
                            document.code,
                            document.fileName,
                            document.revision,
                            if (document.signed) "SÍ" else "NO",
                            document.status,
                            document.signedByName,
                            document.signedByEmail,
                            formatTimestamp(document.signedAt),
                            document.signatureMethod,
                            document.driveWebViewLink,
                            formatTimestamp(document.updatedAt)
                        )
                    )
                )
            }

        val endRow = values.length().coerceAtLeast(1)
        val payload = JSONObject()
            .put("range", "Control!A1:K$endRow")
            .put("majorDimension", "ROWS")
            .put("values", values)

        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Control!A1:K$endRow"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("valueInputOption", "USER_ENTERED")
            .build()
        executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
    }

    private fun createMetadataFile(
        accessToken: String,
        parentId: String,
        name: String,
        mimeType: String
    ): DriveFile {
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", mimeType)
            .put("parents", JSONArray().put(parentId))
        val url = "https://www.googleapis.com/drive/v3/files"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        return executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(metadata.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        ).toDriveFile()
    }

    private fun createContentFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): DriveFile {
        val boundary = "skm-${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", mimeType)
            .put("parents", JSONArray().put(folderId))
            .toString()

        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val bodyBytes = ByteArray(prefix.size + bytes.size + suffix.size)
        prefix.copyInto(bodyBytes, 0)
        bytes.copyInto(bodyBytes, prefix.size)
        suffix.copyInto(bodyBytes, prefix.size + bytes.size)

        val url = "https://www.googleapis.com/upload/drive/v3/files"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        return executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(bodyBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
        ).toDriveFile()
    }

    private fun renameFirstSheet(accessToken: String, spreadsheetId: String) {
        val updateProperties = JSONObject()
            .put("properties", JSONObject().put("sheetId", 0).put("title", "Control"))
            .put("fields", "title")
        val payload = JSONObject().put(
            "requests",
            JSONArray().put(JSONObject().put("updateSheetProperties", updateProperties))
        )
        executeJson(
            Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
    }

    private fun clearControlSheet(accessToken: String, spreadsheetId: String) {
        executeJson(
            Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/Control!A:K:clear")
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
    }

    private fun executeJson(request: Request): JSONObject {
        val text = executeText(request)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun executeText(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Google API respondió ${response.code}: $body")
            }
            return body
        }
    }

    private fun JSONObject.toDriveFile(): DriveFile = DriveFile(
        id = getString("id"),
        name = optString("name"),
        mimeType = optString("mimeType"),
        webViewLink = optString("webViewLink")
    )

    private fun escapeQuery(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CL")).format(Date(timestamp))
    }

    companion object {
        const val JSON_MIME = "application/json"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val SHEET_MIME = "application/vnd.google-apps.spreadsheet"
        private const val PDF_MIME = "application/pdf"
        private const val MAX_PDF_BYTES = 40 * 1024 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
