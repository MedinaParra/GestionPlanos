package com.example.document.data

import com.example.document.drive.DriveRestClient
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.SessionUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DocumentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val drive: DriveRestClient = DriveRestClient()
) {
    suspend fun loadConfiguration(): DriveConfiguration {
        val snapshot = firestore.collection("settings")
            .document("documentManagement")
            .get()
            .await()
        if (!snapshot.exists()) return DriveConfiguration()
        return DriveConfiguration(
            folderId = snapshot.getString("folderId").orEmpty(),
            folderName = snapshot.getString("folderName").orEmpty(),
            spreadsheetId = snapshot.getString("spreadsheetId").orEmpty(),
            spreadsheetName = snapshot.getString("spreadsheetName")
                ?: "Control de Documentos SKM",
            updatedAt = snapshot.getLong("updatedAt") ?: 0L,
            updatedBy = snapshot.getString("updatedBy").orEmpty()
        )
    }

    suspend fun configureDriveFolder(
        user: SessionUser,
        folderInput: String,
        folderName: String,
        accessToken: String
    ): DriveConfiguration {
        require(user.isAdmin) { "Solo el administrador puede cambiar la carpeta de Drive." }
        val folderId = extractDriveFolderId(folderInput)
        require(folderId.isNotBlank()) { "No se pudo reconocer el ID de la carpeta de Drive." }

        val current = loadConfiguration()
        val spreadsheetName = current.spreadsheetName.ifBlank { "Control de Documentos SKM" }
        val spreadsheetId = if (current.folderId == folderId && current.spreadsheetId.isNotBlank()) {
            current.spreadsheetId
        } else {
            drive.createControlSpreadsheet(accessToken, folderId, spreadsheetName).id
        }

        val configuration = DriveConfiguration(
            folderId = folderId,
            folderName = folderName.trim().ifBlank { "Gestión de Planos SKM" },
            spreadsheetId = spreadsheetId,
            spreadsheetName = spreadsheetName,
            updatedAt = System.currentTimeMillis(),
            updatedBy = user.uid
        )

        firestore.collection("settings")
            .document("documentManagement")
            .set(configuration.toMap())
            .await()

        rewriteSpreadsheet(accessToken, configuration)
        return configuration
    }

    suspend fun loadDocuments(): List<DocumentRecord> {
        val snapshot = firestore.collection("documents")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { document ->
            DocumentRecord(
                id = document.id,
                code = document.getString("code").orEmpty(),
                fileName = document.getString("fileName").orEmpty(),
                revision = document.getString("revision") ?: "A",
                status = document.getString("status") ?: "PENDIENTE",
                driveFileId = document.getString("driveFileId").orEmpty(),
                driveWebViewLink = document.getString("driveWebViewLink").orEmpty(),
                uploadedByUid = document.getString("uploadedByUid").orEmpty(),
                uploadedByName = document.getString("uploadedByName").orEmpty(),
                uploadedAt = document.getLong("uploadedAt") ?: 0L,
                signed = document.getBoolean("signed") ?: false,
                signedByUid = document.getString("signedByUid").orEmpty(),
                signedByName = document.getString("signedByName").orEmpty(),
                signedAt = document.getLong("signedAt") ?: 0L,
                updatedAt = document.getLong("updatedAt") ?: 0L
            )
        }
    }

    suspend fun uploadDocument(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        code: String,
        revision: String,
        sourceFileName: String,
        pdfBytes: ByteArray
    ): DocumentRecord {
        require(user.canEdit) { "La cuenta es solo de visualización." }
        require(configuration.isConfigured) { "El administrador todavía no ha configurado Drive." }
        val cleanCode = code.trim().uppercase()
        require(cleanCode.isNotBlank()) { "La codificación del plano es obligatoria." }
        val cleanRevision = revision.trim().uppercase().ifBlank { "A" }
        val finalFileName = buildManagedFileName(cleanCode, cleanRevision, sourceFileName)
        val driveFile = drive.uploadPdf(
            accessToken = accessToken,
            folderId = configuration.folderId,
            fileName = finalFileName,
            bytes = pdfBytes
        )

        val now = System.currentTimeMillis()
        val record = DocumentRecord(
            id = UUID.randomUUID().toString(),
            code = cleanCode,
            fileName = driveFile.name,
            revision = cleanRevision,
            status = "PENDIENTE",
            driveFileId = driveFile.id,
            driveWebViewLink = driveFile.webViewLink,
            uploadedByUid = user.uid,
            uploadedByName = user.displayName,
            uploadedAt = now,
            signed = false,
            updatedAt = now
        )

        firestore.collection("documents").document(record.id).set(record.toMap()).await()
        writeAudit(user, record.id, "UPLOAD", "PDF cargado: ${record.fileName}")
        rewriteSpreadsheet(accessToken, configuration)
        return record
    }

    suspend fun updateRevision(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        document: DocumentRecord,
        newRevision: String
    ) {
        require(user.canEdit) { "La cuenta es solo de visualización." }
        val revision = newRevision.trim().uppercase()
        require(revision.isNotBlank()) { "La revisión no puede quedar vacía." }
        val now = System.currentTimeMillis()
        firestore.collection("documents").document(document.id)
            .update(
                mapOf(
                    "revision" to revision,
                    "updatedAt" to now
                )
            )
            .await()
        writeAudit(user, document.id, "REVISION", "Revisión cambiada de ${document.revision} a $revision")
        rewriteSpreadsheet(accessToken, configuration)
    }

    suspend fun markSigned(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        document: DocumentRecord,
        signed: Boolean
    ) {
        require(user.canEdit) { "La cuenta es solo de visualización." }
        val now = System.currentTimeMillis()
        val values = if (signed) {
            mapOf(
                "signed" to true,
                "status" to "FIRMADO",
                "signedByUid" to user.uid,
                "signedByName" to user.displayName,
                "signedAt" to now,
                "updatedAt" to now
            )
        } else {
            mapOf(
                "signed" to false,
                "status" to "PENDIENTE",
                "signedByUid" to "",
                "signedByName" to "",
                "signedAt" to 0L,
                "updatedAt" to now
            )
        }
        firestore.collection("documents").document(document.id).update(values).await()
        writeAudit(
            user,
            document.id,
            if (signed) "SIGN" else "UNSIGN",
            if (signed) "Documento marcado como firmado" else "Firma devuelta a pendiente"
        )
        rewriteSpreadsheet(accessToken, configuration)
    }

    suspend fun downloadPdf(
        accessToken: String,
        driveFileId: String,
        target: java.io.File
    ): java.io.File = drive.downloadPdf(accessToken, driveFileId, target)

    suspend fun rewriteSpreadsheet(
        accessToken: String,
        configuration: DriveConfiguration
    ) {
        if (configuration.spreadsheetId.isBlank()) return
        drive.rewriteControlSpreadsheet(
            accessToken = accessToken,
            spreadsheetId = configuration.spreadsheetId,
            documents = loadDocuments()
        )
    }

    fun extractDriveFolderId(input: String): String {
        val value = input.trim()
        val patterns = listOf(
            Regex("/folders/([a-zA-Z0-9_-]+)"),
            Regex("[?&]id=([a-zA-Z0-9_-]+)"),
            Regex("^([a-zA-Z0-9_-]{10,})$")
        )
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1) }.orEmpty()
    }

    private suspend fun writeAudit(
        user: SessionUser,
        documentId: String,
        action: String,
        detail: String
    ) {
        firestore.collection("auditLogs").add(
            mapOf(
                "documentId" to documentId,
                "action" to action,
                "detail" to detail,
                "actorUid" to user.uid,
                "actorName" to user.displayName,
                "actorEmail" to user.email,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    private fun buildManagedFileName(code: String, revision: String, original: String): String {
        val base = original.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        return "${code}_REV-${revision}_${base}.pdf"
    }

    private fun DriveConfiguration.toMap(): Map<String, Any> = mapOf(
        "folderId" to folderId,
        "folderName" to folderName,
        "spreadsheetId" to spreadsheetId,
        "spreadsheetName" to spreadsheetName,
        "updatedAt" to updatedAt,
        "updatedBy" to updatedBy
    )

    private fun DocumentRecord.toMap(): Map<String, Any> = mapOf(
        "code" to code,
        "fileName" to fileName,
        "revision" to revision,
        "status" to status,
        "driveFileId" to driveFileId,
        "driveWebViewLink" to driveWebViewLink,
        "uploadedByUid" to uploadedByUid,
        "uploadedByName" to uploadedByName,
        "uploadedAt" to uploadedAt,
        "signed" to signed,
        "signedByUid" to signedByUid,
        "signedByName" to signedByName,
        "signedAt" to signedAt,
        "updatedAt" to updatedAt
    )
}
