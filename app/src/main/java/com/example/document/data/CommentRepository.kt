package com.example.document.data

import com.example.document.drive.DriveRestClient
import com.example.document.model.DriveConfiguration
import com.example.document.model.PlanComment
import com.example.document.model.SessionUser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CommentRepository(
    private val drive: DriveRestClient = DriveRestClient()
) {
    suspend fun loadForDocument(
        accessToken: String,
        configuration: DriveConfiguration,
        documentId: String
    ): List<PlanComment> {
        if (!configuration.isConfigured || configuration.systemFolderId.isBlank()) return emptyList()
        val (_, comments) = loadAll(accessToken, configuration, createIfMissing = configuration.canEdit)
        return comments.filter { it.documentId == documentId }
            .sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })
    }

    suspend fun addComment(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        documentId: String,
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        width: Float
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para guardar comentarios en esta carpeta." }
        require(user.profile.active) { "Tu usuario está desactivado." }
        val cleanText = text.trim()
        require(cleanText.isNotBlank()) { "Escribe un comentario." }
        require(cleanText.length <= 1200) { "El comentario supera los 1.200 caracteres." }

        val (fileId, comments) = loadAll(accessToken, configuration, createIfMissing = true)
        val now = System.currentTimeMillis()
        val comment = PlanComment(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            pageIndex = pageIndex.coerceAtLeast(0),
            text = cleanText,
            x = x.coerceIn(0f, 0.90f),
            y = y.coerceIn(0f, 0.92f),
            width = width.coerceIn(0.22f, 0.62f),
            authorName = user.profile.displayName.ifBlank { user.displayName },
            authorEmail = user.email,
            createdAt = now,
            updatedAt = now
        )
        val updated = comments + comment
        saveAll(accessToken, fileId, updated)
        return updated.filter { it.documentId == documentId }
            .sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })
    }

    suspend fun updateComment(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        comment: PlanComment
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para modificar comentarios." }
        val cleanText = comment.text.trim()
        require(cleanText.isNotBlank()) { "El comentario no puede quedar vacío." }
        require(cleanText.length <= 1200) { "El comentario supera los 1.200 caracteres." }

        val (fileId, comments) = loadAll(accessToken, configuration, createIfMissing = true)
        val existing = comments.firstOrNull { it.id == comment.id }
            ?: error("El comentario ya no existe. Actualiza el plano.")
        require(existing.canBeModifiedBy(user.email, user.isAdmin)) {
            "Solo el autor o un administrador puede modificar este comentario."
        }
        val updatedComment = existing.copy(
            text = cleanText,
            pageIndex = comment.pageIndex.coerceAtLeast(0),
            x = comment.x.coerceIn(0f, 0.90f),
            y = comment.y.coerceIn(0f, 0.92f),
            width = comment.width.coerceIn(0.22f, 0.62f),
            updatedAt = System.currentTimeMillis()
        )
        val updated = comments.map { if (it.id == existing.id) updatedComment else it }
        saveAll(accessToken, fileId, updated)
        return updated.filter { it.documentId == existing.documentId }
            .sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })
    }

    suspend fun deleteComment(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        comment: PlanComment
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para eliminar comentarios." }
        val (fileId, comments) = loadAll(accessToken, configuration, createIfMissing = true)
        val existing = comments.firstOrNull { it.id == comment.id }
            ?: return comments.filter { it.documentId == comment.documentId }
        require(existing.canBeModifiedBy(user.email, user.isAdmin)) {
            "Solo el autor o un administrador puede eliminar este comentario."
        }
        val updated = comments.filterNot { it.id == existing.id }
        saveAll(accessToken, fileId, updated)
        return updated.filter { it.documentId == existing.documentId }
            .sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })
    }

    private suspend fun loadAll(
        accessToken: String,
        configuration: DriveConfiguration,
        createIfMissing: Boolean
    ): Pair<String, List<PlanComment>> {
        var file = drive.findFileByName(
            accessToken,
            configuration.systemFolderId,
            COMMENTS_FILE,
            DriveRestClient.JSON_MIME
        )
        if (file == null && createIfMissing) {
            file = drive.createTextFile(
                accessToken,
                configuration.systemFolderId,
                COMMENTS_FILE,
                DriveRestClient.JSON_MIME,
                commentsJson(emptyList())
            )
        }
        if (file == null) return "" to emptyList()
        val text = drive.readTextFile(accessToken, file.id)
        if (text.isBlank()) return file.id to emptyList()
        val array = JSONObject(text).optJSONArray("comments") ?: JSONArray()
        val comments = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PlanComment(
                        id = item.optString("id"),
                        documentId = item.optString("documentId"),
                        pageIndex = item.optInt("pageIndex"),
                        text = item.optString("text"),
                        x = item.optDouble("x", 0.08).toFloat(),
                        y = item.optDouble("y", 0.10).toFloat(),
                        width = item.optDouble("width", 0.36).toFloat(),
                        authorName = item.optString("authorName"),
                        authorEmail = item.optString("authorEmail"),
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt")
                    )
                )
            }
        }
        return file.id to comments
    }

    private suspend fun saveAll(
        accessToken: String,
        fileId: String,
        comments: List<PlanComment>
    ) {
        require(fileId.isNotBlank()) { "No se encontró el archivo compartido de comentarios." }
        drive.updateTextFile(
            accessToken,
            fileId,
            DriveRestClient.JSON_MIME,
            commentsJson(comments)
        )
    }

    private fun commentsJson(comments: List<PlanComment>): String {
        val array = JSONArray()
        comments.sortedBy { it.createdAt }.forEach { comment ->
            array.put(
                JSONObject()
                    .put("id", comment.id)
                    .put("documentId", comment.documentId)
                    .put("pageIndex", comment.pageIndex)
                    .put("text", comment.text)
                    .put("x", comment.x.toDouble())
                    .put("y", comment.y.toDouble())
                    .put("width", comment.width.toDouble())
                    .put("authorName", comment.authorName)
                    .put("authorEmail", comment.authorEmail)
                    .put("createdAt", comment.createdAt)
                    .put("updatedAt", comment.updatedAt)
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("updatedAt", System.currentTimeMillis())
            .put("comments", array)
            .toString(2)
    }

    companion object {
        private const val COMMENTS_FILE = "comentarios-planos.json"
    }
}
