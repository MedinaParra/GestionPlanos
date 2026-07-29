package com.example.document.data

import com.example.document.drive.DriveRestClient
import com.example.document.model.ApprovalSignature
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.SessionUser
import com.example.document.model.WorkflowEvent
import com.example.document.model.WorkflowEventType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WorkflowActionRepository(
    private val drive: DriveRestClient = DriveRestClient()
) {
    suspend fun requestChanges(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        documentId: String,
        reason: String
    ) {
        require(configuration.canEdit) { "No tienes permiso de escritura en la carpeta principal." }
        val cleanReason = reason.trim()
        require(cleanReason.length in 5..1200) { "Describe los cambios solicitados en al menos 5 caracteres." }

        val indexText = drive.readTextFile(accessToken, configuration.indexFileId)
        val root = JSONObject(indexText)
        val array = root.optJSONArray("documents") ?: JSONArray()
        var target: JSONObject? = null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("id") == documentId) {
                target = item
                break
            }
        }
        val documentJson = target ?: error("El plano ya no existe. Actualiza la información.")
        val document = parseDocument(documentJson)
        require(document.canRequestChangesBy(user.email)) {
            val current = document.currentReviewerEmail
            if (current.isBlank()) "La revisión ya terminó." else "El turno corresponde a $current."
        }

        val now = System.currentTimeMillis()
        documentJson
            .put("status", "CAMBIOS_SOLICITADOS")
            .put("changeReason", cleanReason)
            .put("changesRequestedByName", user.profile.displayName.ifBlank { user.displayName })
            .put("changesRequestedByEmail", user.email)
            .put("changesRequestedAt", now)
            .put("dueAt", 0L)
            .put("updatedAt", now)

        root.put("version", maxOf(root.optInt("version", 2), 3))
        root.put("updatedAt", now)
        drive.updateTextFile(accessToken, configuration.indexFileId, DriveRestClient.JSON_MIME, root.toString(2))

        if (configuration.spreadsheetId.isNotBlank()) {
            val updatedDocuments = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(parseDocument(item))
                }
            }
            drive.rewriteControlSpreadsheet(accessToken, configuration.spreadsheetId, updatedDocuments)
        }

        appendEvent(
            accessToken = accessToken,
            configuration = configuration,
            event = WorkflowEvent(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                type = WorkflowEventType.CHANGES_REQUESTED,
                actorName = user.profile.displayName.ifBlank { user.displayName },
                actorEmail = user.email,
                detail = cleanReason,
                createdAt = now
            )
        )
    }

    suspend fun loadExtraEvents(
        accessToken: String,
        configuration: DriveConfiguration,
        documentId: String
    ): List<WorkflowEvent> {
        if (!configuration.isConfigured || configuration.systemFolderId.isBlank()) return emptyList()
        val file = drive.findFileByName(
            accessToken,
            configuration.systemFolderId,
            HISTORY_FILE,
            DriveRestClient.JSON_MIME
        ) ?: return emptyList()
        val root = JSONObject(drive.readTextFile(accessToken, file.id))
        val array = root.optJSONArray("events") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("documentId") != documentId) continue
                add(
                    WorkflowEvent(
                        id = item.optString("id"),
                        documentId = item.optString("documentId"),
                        type = runCatching {
                            WorkflowEventType.valueOf(item.optString("type"))
                        }.getOrDefault(WorkflowEventType.UPLOADED),
                        actorName = item.optString("actorName"),
                        actorEmail = item.optString("actorEmail"),
                        detail = item.optString("detail"),
                        createdAt = item.optLong("createdAt")
                    )
                )
            }
        }.sortedBy { it.createdAt }
    }

    private suspend fun appendEvent(
        accessToken: String,
        configuration: DriveConfiguration,
        event: WorkflowEvent
    ) {
        var file = drive.findFileByName(
            accessToken,
            configuration.systemFolderId,
            HISTORY_FILE,
            DriveRestClient.JSON_MIME
        )
        if (file == null) {
            file = drive.createTextFile(
                accessToken,
                configuration.systemFolderId,
                HISTORY_FILE,
                DriveRestClient.JSON_MIME,
                JSONObject().put("version", 1).put("events", JSONArray()).toString(2)
            )
        }
        val root = JSONObject(drive.readTextFile(accessToken, file.id))
        val array = root.optJSONArray("events") ?: JSONArray().also { root.put("events", it) }
        array.put(
            JSONObject()
                .put("id", event.id)
                .put("documentId", event.documentId)
                .put("type", event.type.name)
                .put("actorName", event.actorName)
                .put("actorEmail", event.actorEmail)
                .put("detail", event.detail)
                .put("createdAt", event.createdAt)
        )
        root.put("updatedAt", System.currentTimeMillis())
        drive.updateTextFile(accessToken, file.id, DriveRestClient.JSON_MIME, root.toString(2))
    }

    private fun parseDocument(json: JSONObject): DocumentRecord {
        val reviewerArray = json.optJSONArray("requiredReviewerEmails") ?: JSONArray()
        val reviewers = buildList {
            for (index in 0 until reviewerArray.length()) add(reviewerArray.optString(index))
        }
        val approvalsArray = json.optJSONArray("approvals") ?: JSONArray()
        val approvals = buildList {
            for (index in 0 until approvalsArray.length()) {
                val item = approvalsArray.optJSONObject(index) ?: continue
                add(
                    ApprovalSignature(
                        email = item.optString("email"),
                        name = item.optString("name"),
                        rut = item.optString("rut"),
                        position = item.optString("position"),
                        signedAt = item.optLong("signedAt"),
                        method = item.optString("method"),
                        signatureFileId = item.optString("signatureFileId"),
                        signedPdfFileId = item.optString("signedPdfFileId")
                    )
                )
            }
        }
        return DocumentRecord(
            id = json.optString("id"),
            otNumber = json.optString("otNumber"),
            code = json.optString("code"),
            originalFileName = json.optString("originalFileName"),
            fileName = json.optString("fileName"),
            revision = json.optString("revision", "0"),
            status = json.optString("status", "EN_REVISIÓN"),
            driveFileId = json.optString("driveFileId"),
            driveWebViewLink = json.optString("driveWebViewLink"),
            originalPdfFileId = json.optString("originalPdfFileId"),
            currentPdfFileId = json.optString("currentPdfFileId").ifBlank { json.optString("driveFileId") },
            finalPdfFileId = json.optString("finalPdfFileId"),
            otFolderId = json.optString("otFolderId"),
            revisionFolderId = json.optString("revisionFolderId"),
            originalFolderId = json.optString("originalFolderId"),
            reviewFolderId = json.optString("reviewFolderId"),
            signaturesFolderId = json.optString("signaturesFolderId"),
            finalFolderId = json.optString("finalFolderId"),
            uploadedByName = json.optString("uploadedByName"),
            uploadedByEmail = json.optString("uploadedByEmail"),
            uploadedAt = json.optLong("uploadedAt"),
            dueAt = json.optLong("dueAt"),
            requiredReviewerEmails = reviewers,
            currentReviewerIndex = json.optInt("currentReviewerIndex"),
            approvals = approvals,
            signed = json.optBoolean("signed"),
            signedByName = json.optString("signedByName"),
            signedByEmail = json.optString("signedByEmail"),
            signedAt = json.optLong("signedAt"),
            signatureMethod = json.optString("signatureMethod"),
            updatedAt = json.optLong("updatedAt")
        )
    }

    companion object {
        private const val HISTORY_FILE = "historial-flujo.json"
    }
}
