package com.example.document.data

import com.example.document.drive.DriveRestClient
import com.example.document.model.DriveConfiguration
import com.example.document.model.PlanComment
import com.example.document.model.ReviewMarkupInput
import com.example.document.model.ReviewMarkupType
import com.example.document.model.ReviewPoint
import com.example.document.model.SessionUser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WorkflowCommentRepository(
    private val drive: DriveRestClient = DriveRestClient()
) {
    private data class CommentStore(
        val fileId: String,
        val comments: List<PlanComment>
    )

    suspend fun loadForDocument(
        user: SessionUser,
        accessToken: String,
        configuration: DriveConfiguration,
        documentId: String
    ): List<PlanComment> {
        if (!configuration.isConfigured || configuration.systemFolderId.isBlank()) return emptyList()
        return reloadVisible(user, accessToken, configuration, documentId)
    }

    suspend fun addDraft(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        documentId: String,
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        width: Float
    ): List<PlanComment> = addMarkupDraft(
        user = user,
        configuration = configuration,
        accessToken = accessToken,
        documentId = documentId,
        input = ReviewMarkupInput(
            pageIndex = pageIndex,
            type = ReviewMarkupType.TEXT,
            text = text,
            x = x,
            y = y,
            width = width,
            height = 0.10f
        )
    )

    suspend fun addMarkupDraft(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        documentId: String,
        input: ReviewMarkupInput
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para crear observaciones en este proyecto." }
        require(configuration.privateFolderId.isNotBlank()) { "No se encontró tu carpeta privada de trabajo." }
        require(user.profile.active) { "Tu usuario está desactivado." }

        val cleanText = validateMarkup(input.type, input.text, input.points)
        val store = loadPrivateDrafts(accessToken, configuration, createIfMissing = true)
        val now = System.currentTimeMillis()
        val comment = PlanComment(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            pageIndex = input.pageIndex.coerceAtLeast(0),
            text = cleanText,
            x = input.x.coerceIn(0f, 1f),
            y = input.y.coerceIn(0f, 1f),
            width = input.width.coerceIn(0.01f, 1f),
            height = input.height.coerceIn(0.01f, 1f),
            endX = input.endX.coerceIn(0f, 1f),
            endY = input.endY.coerceIn(0f, 1f),
            markupType = input.type,
            colorArgb = input.colorArgb,
            strokeWidth = input.strokeWidth.coerceIn(0.001f, 0.04f),
            opacity = input.opacity.coerceIn(0.08f, 1f),
            points = input.points.take(MAX_POINTS).map { point ->
                ReviewPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
            },
            authorName = user.profile.displayName.ifBlank { user.displayName },
            authorEmail = user.email,
            published = false,
            createdAt = now,
            updatedAt = now
        )
        saveStore(accessToken, store.fileId, store.comments + comment)
        return reloadVisible(user, accessToken, configuration, documentId)
    }

    suspend fun publish(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        comment: PlanComment
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para publicar observaciones." }
        require(!comment.published) { "La observación ya está publicada." }

        val drafts = loadPrivateDrafts(accessToken, configuration, createIfMissing = true)
        val existing = drafts.comments.firstOrNull { it.id == comment.id }
            ?: error("El borrador ya no existe en tu espacio privado.")
        require(existing.authorEmail.equals(user.email, ignoreCase = true)) {
            "Solo el autor puede publicar este borrador."
        }

        val publishedStore = loadPublished(accessToken, configuration, createIfMissing = true)
        val now = System.currentTimeMillis()
        val publishedComment = existing.copy(
            published = true,
            publishedAt = now,
            updatedAt = now
        )
        saveStore(
            accessToken,
            publishedStore.fileId,
            publishedStore.comments.filterNot { it.id == existing.id } + publishedComment
        )
        saveStore(accessToken, drafts.fileId, drafts.comments.filterNot { it.id == existing.id })
        return reloadVisible(user, accessToken, configuration, existing.documentId)
    }

    suspend fun update(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        comment: PlanComment
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para modificar observaciones." }
        val cleanText = validateMarkup(comment.markupType, comment.text, comment.points)
        val store = if (comment.published) {
            loadPublished(accessToken, configuration, createIfMissing = true)
        } else {
            loadPrivateDrafts(accessToken, configuration, createIfMissing = true)
        }
        val existing = store.comments.firstOrNull { it.id == comment.id }
            ?: error("La observación ya no existe. Actualiza el plano.")
        require(existing.canBeModifiedBy(user.email, user.isAdmin)) {
            "Solo el autor o un administrador puede modificar esta observación."
        }
        if (!existing.published) {
            require(existing.authorEmail.equals(user.email, ignoreCase = true)) {
                "Los borradores privados solo pueden ser editados por su autor."
            }
        }
        val updatedComment = existing.copy(
            text = cleanText,
            pageIndex = comment.pageIndex.coerceAtLeast(0),
            x = comment.x.coerceIn(0f, 1f),
            y = comment.y.coerceIn(0f, 1f),
            width = comment.width.coerceIn(0.01f, 1f),
            height = comment.height.coerceIn(0.01f, 1f),
            endX = comment.endX.coerceIn(0f, 1f),
            endY = comment.endY.coerceIn(0f, 1f),
            markupType = comment.markupType,
            colorArgb = comment.colorArgb,
            strokeWidth = comment.strokeWidth.coerceIn(0.001f, 0.04f),
            opacity = comment.opacity.coerceIn(0.08f, 1f),
            points = comment.points.take(MAX_POINTS).map { ReviewPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) },
            updatedAt = System.currentTimeMillis()
        )
        saveStore(
            accessToken,
            store.fileId,
            store.comments.map { if (it.id == existing.id) updatedComment else it }
        )
        return reloadVisible(user, accessToken, configuration, existing.documentId)
    }

    suspend fun delete(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        comment: PlanComment
    ): List<PlanComment> {
        require(configuration.canEdit) { "No tienes permiso para eliminar observaciones." }
        val store = if (comment.published) {
            loadPublished(accessToken, configuration, createIfMissing = true)
        } else {
            loadPrivateDrafts(accessToken, configuration, createIfMissing = true)
        }
        val existing = store.comments.firstOrNull { it.id == comment.id }
            ?: return reloadVisible(user, accessToken, configuration, comment.documentId)
        require(existing.canBeModifiedBy(user.email, user.isAdmin)) {
            "Solo el autor o un administrador puede eliminar esta observación."
        }
        if (!existing.published) {
            require(existing.authorEmail.equals(user.email, ignoreCase = true)) {
                "Los borradores privados solo pueden ser eliminados por su autor."
            }
        }
        saveStore(accessToken, store.fileId, store.comments.filterNot { it.id == existing.id })
        return reloadVisible(user, accessToken, configuration, existing.documentId)
    }

    private suspend fun reloadVisible(
        user: SessionUser,
        accessToken: String,
        configuration: DriveConfiguration,
        documentId: String
    ): List<PlanComment> {
        val published = loadPublished(
            accessToken,
            configuration,
            createIfMissing = configuration.canEdit
        ).comments
        val drafts = loadPrivateDrafts(
            accessToken,
            configuration,
            createIfMissing = true
        ).comments.filter { it.authorEmail.equals(user.email, ignoreCase = true) }
        return (published + drafts)
            .filter { it.documentId == documentId }
            .distinctBy { it.id }
            .sortedWith(compareBy<PlanComment> { it.pageIndex }.thenBy { it.createdAt })
    }

    private fun validateMarkup(
        type: ReviewMarkupType,
        text: String,
        points: List<ReviewPoint>
    ): String {
        val clean = text.trim()
        require(clean.length <= 1200) { "La observación supera los 1.200 caracteres." }
        if (type == ReviewMarkupType.TEXT) {
            require(clean.isNotBlank()) { "Escribe una observación." }
        }
        if (type == ReviewMarkupType.FREEHAND || type == ReviewMarkupType.HIGHLIGHT) {
            require(points.size >= 2) { "El trazo es demasiado corto." }
        }
        return clean
    }

    private suspend fun loadPublished(
        accessToken: String,
        configuration: DriveConfiguration,
        createIfMissing: Boolean
    ): CommentStore {
        var file = drive.findFileByName(
            accessToken,
            configuration.systemFolderId,
            PUBLISHED_FILE,
            DriveRestClient.JSON_MIME
        )
        if (file != null) {
            return CommentStore(file.id, readComments(accessToken, file.id, defaultPublished = true))
        }

        val legacy = drive.findFileByName(
            accessToken,
            configuration.systemFolderId,
            LEGACY_FILE,
            DriveRestClient.JSON_MIME
        )
        val legacyComments = legacy?.let {
            readComments(accessToken, it.id, defaultPublished = true).map { comment ->
                comment.copy(
                    published = true,
                    publishedAt = comment.publishedAt.takeIf { value -> value > 0L } ?: comment.updatedAt
                )
            }
        }.orEmpty()

        if (createIfMissing) {
            file = drive.createTextFile(
                accessToken,
                configuration.systemFolderId,
                PUBLISHED_FILE,
                DriveRestClient.JSON_MIME,
                commentsJson(legacyComments)
            )
            return CommentStore(file.id, legacyComments)
        }

        return if (legacy != null) CommentStore(legacy.id, legacyComments) else CommentStore("", emptyList())
    }

    private suspend fun loadPrivateDrafts(
        accessToken: String,
        configuration: DriveConfiguration,
        createIfMissing: Boolean
    ): CommentStore {
        if (configuration.privateFolderId.isBlank()) return CommentStore("", emptyList())
        var file = drive.findFileByName(
            accessToken,
            configuration.privateFolderId,
            PRIVATE_DRAFTS_FILE,
            DriveRestClient.JSON_MIME
        )
        if (file == null && createIfMissing) {
            file = drive.createTextFile(
                accessToken,
                configuration.privateFolderId,
                PRIVATE_DRAFTS_FILE,
                DriveRestClient.JSON_MIME,
                commentsJson(emptyList())
            )
        }
        if (file == null) return CommentStore("", emptyList())
        return CommentStore(file.id, readComments(accessToken, file.id, defaultPublished = false))
    }

    private suspend fun readComments(
        accessToken: String,
        fileId: String,
        defaultPublished: Boolean
    ): List<PlanComment> {
        val text = drive.readTextFile(accessToken, fileId)
        if (text.isBlank()) return emptyList()
        val array = JSONObject(text).optJSONArray("comments") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val pointsArray = item.optJSONArray("points") ?: JSONArray()
                val points = buildList {
                    for (pointIndex in 0 until pointsArray.length()) {
                        val point = pointsArray.optJSONObject(pointIndex) ?: continue
                        add(
                            ReviewPoint(
                                x = point.optDouble("x", 0.0).toFloat(),
                                y = point.optDouble("y", 0.0).toFloat()
                            )
                        )
                    }
                }
                val type = runCatching {
                    ReviewMarkupType.valueOf(item.optString("markupType", ReviewMarkupType.TEXT.name))
                }.getOrDefault(ReviewMarkupType.TEXT)
                add(
                    PlanComment(
                        id = item.optString("id"),
                        documentId = item.optString("documentId"),
                        pageIndex = item.optInt("pageIndex"),
                        text = item.optString("text"),
                        x = item.optDouble("x", 0.08).toFloat(),
                        y = item.optDouble("y", 0.10).toFloat(),
                        width = item.optDouble("width", 0.36).toFloat(),
                        height = item.optDouble("height", 0.10).toFloat(),
                        endX = item.optDouble("endX", 0.30).toFloat(),
                        endY = item.optDouble("endY", 0.20).toFloat(),
                        markupType = type,
                        colorArgb = item.optLong("colorArgb", 0xFFFF6A00L).toInt(),
                        strokeWidth = item.optDouble("strokeWidth", 0.004).toFloat(),
                        opacity = item.optDouble("opacity", 1.0).toFloat(),
                        points = points,
                        authorName = item.optString("authorName"),
                        authorEmail = item.optString("authorEmail"),
                        published = if (item.has("published")) item.optBoolean("published") else defaultPublished,
                        publishedAt = item.optLong("publishedAt"),
                        createdAt = item.optLong("createdAt"),
                        updatedAt = item.optLong("updatedAt")
                    )
                )
            }
        }
    }

    private suspend fun saveStore(
        accessToken: String,
        fileId: String,
        comments: List<PlanComment>
    ) {
        require(fileId.isNotBlank()) { "No se encontró el archivo de observaciones." }
        drive.updateTextFile(accessToken, fileId, DriveRestClient.JSON_MIME, commentsJson(comments))
    }

    private fun commentsJson(comments: List<PlanComment>): String {
        val array = JSONArray()
        comments.sortedBy { it.createdAt }.forEach { comment ->
            val points = JSONArray()
            comment.points.forEach { point ->
                points.put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
            }
            array.put(
                JSONObject()
                    .put("id", comment.id)
                    .put("documentId", comment.documentId)
                    .put("pageIndex", comment.pageIndex)
                    .put("text", comment.text)
                    .put("x", comment.x.toDouble())
                    .put("y", comment.y.toDouble())
                    .put("width", comment.width.toDouble())
                    .put("height", comment.height.toDouble())
                    .put("endX", comment.endX.toDouble())
                    .put("endY", comment.endY.toDouble())
                    .put("markupType", comment.markupType.name)
                    .put("colorArgb", comment.colorArgb.toLong())
                    .put("strokeWidth", comment.strokeWidth.toDouble())
                    .put("opacity", comment.opacity.toDouble())
                    .put("points", points)
                    .put("authorName", comment.authorName)
                    .put("authorEmail", comment.authorEmail)
                    .put("published", comment.published)
                    .put("publishedAt", comment.publishedAt)
                    .put("createdAt", comment.createdAt)
                    .put("updatedAt", comment.updatedAt)
            )
        }
        return JSONObject()
            .put("version", 4)
            .put("updatedAt", System.currentTimeMillis())
            .put("comments", array)
            .toString(2)
    }

    companion object {
        private const val PUBLISHED_FILE = "comentarios-publicados.json"
        private const val PRIVATE_DRAFTS_FILE = "comentarios-borradores.json"
        private const val LEGACY_FILE = "comentarios-planos.json"
        private const val MAX_POINTS = 3000
    }
}