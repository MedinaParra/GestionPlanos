package com.example.document.drive

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
import java.util.UUID

class DriveBinaryClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun upload(
        accessToken: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): DriveRestClient.DriveFile = withContext(Dispatchers.IO) {
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
        val payload = ByteArray(prefix.size + bytes.size + suffix.size)
        prefix.copyInto(payload, 0)
        bytes.copyInto(payload, prefix.size)
        suffix.copyInto(payload, prefix.size + bytes.size)

        val url = "https://www.googleapis.com/upload/drive/v3/files"
            .toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
        ).toDriveFile()
    }

    suspend fun update(
        accessToken: String,
        fileId: String,
        mimeType: String,
        bytes: ByteArray
    ): DriveRestClient.DriveFile = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId"
            .toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("fields", "id,name,mimeType,webViewLink")
            .build()
        executeJson(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .patch(bytes.toRequestBody(mimeType.toMediaType()))
                .build()
        ).toDriveFile()
    }

    suspend fun downloadBytes(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            .toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        client.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
        ).execute().use { response ->
            val body = response.body ?: error("Drive no devolvió contenido.")
            if (!response.isSuccessful) error("Drive respondió ${response.code}: ${body.string()}")
            body.bytes()
        }
    }

    suspend fun downloadToFile(accessToken: String, fileId: String, target: File): File {
        target.writeBytes(downloadBytes(accessToken, fileId))
        return target
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Google API respondió ${response.code}: $text")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun JSONObject.toDriveFile(): DriveRestClient.DriveFile = DriveRestClient.DriveFile(
        id = getString("id"),
        name = optString("name"),
        mimeType = optString("mimeType"),
        webViewLink = optString("webViewLink")
    )

    companion object {
        const val PNG_MIME = "image/png"
        const val JPEG_MIME = "image/jpeg"
        const val PDF_MIME = "application/pdf"
    }
}
