package com.example.document.data

import com.example.document.drive.DriveRestClient
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.DriveWorkspace
import com.example.document.model.SessionUser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DocumentRepository(
    private val drive: DriveRestClient = DriveRestClient()
) {
    suspend fun connect(accessToken: String): DriveWorkspace {
        val driveUser = drive.getCurrentUser(accessToken)
        val privateFolder = drive.findOrCreateFolder(
            accessToken = accessToken,
            parentId = "root",
            name = PRIVATE_FOLDER_NAME
        )

        val existingConfigFile = drive.findFileByName(
            accessToken = accessToken,
            parentId = privateFolder.id,
            name = PRIVATE_CONFIG_FILE,
            mimeType = DriveRestClient.JSON_MIME
        )
        val configFile = existingConfigFile ?: drive.createTextFile(
            accessToken = accessToken,
            folderId = privateFolder.id,
            fileName = PRIVATE_CONFIG_FILE,
            mimeType = DriveRestClient.JSON_MIME,
            content = emptyConfigurationJson(privateFolder.id)
        )

        var configuration = if (existingConfigFile == null) {
            DriveConfiguration(
                privateFolderId = privateFolder.id,
                configFileId = configFile.id
            )
        } else {
            parseConfiguration(drive.readTextFile(accessToken, configFile.id)).copy(
                privateFolderId = privateFolder.id,
                configFileId = configFile.id
            )
        }

        var documents = emptyList<DocumentRecord>()
        if (configuration.folderId.isNotBlank()) {
            runCatching {
                resolveWorkspaceFiles(accessToken, configuration)
            }.onSuccess { resolved ->
                configuration = resolved
                documents = loadDocuments(accessToken, resolved)
                savePrivateConfiguration(accessToken, resolved)
            }.onFailure {
                configuration = configuration.copy(
                    folderId = "",
                    folderName = "",
                    spreadsheetId = "",
                    indexFileId = "",
                    canEdit = false
                )
                savePrivateConfiguration(accessToken, configuration)
            }
        }

        val session = SessionUser(
            permissionId = driveUser.permissionId,
            email = driveUser.email,
            displayName = driveUser.displayName,
            canEdit = configuration.canEdit
        )
        return DriveWorkspace(session, configuration, documents)
    }

    suspend fun refresh(
        accessToken: String,
        current: DriveConfiguration,
        session: SessionUser
    ): DriveWorkspace {
        if (!current.isConfigured) {
            return DriveWorkspace(session.copy(canEdit = false), current, emptyList())
        }
        val configuration = resolveWorkspaceFiles(accessToken, current)
        savePrivateConfiguration(accessToken, configuration)
        return DriveWorkspace(
            session = session.copy(canEdit = configuration.canEdit),
            configuration = configuration,
            documents = loadDocuments(accessToken, configuration)
        )
    }

    suspend fun configureDriveFolder(
        user: SessionUser,
        current: DriveConfiguration,
        folderInput: String,
        folderName: String,
        accessToken: String
    ): DriveWorkspace {
        val folderId = extractDriveFolderId(folderInput)
        require(folderId.isNotBlank()) { "No se pudo reconocer el ID de la carpeta de Drive." }
        val folder = drive.getFolderInfo(accessToken, folderId)

        var indexFile = drive.findFileByName(
            accessToken,
            folder.id,
            SHARED_INDEX_FILE,
            DriveRestClient.JSON_MIME
        )
        if (indexFile == null && folder.canAddChildren) {
            indexFile = drive.createTextFile(
                accessToken,
                folder.id,
                SHARED_INDEX_FILE,
                DriveRestClient.JSON_MIME,
                emptyDocumentIndexJson()
            )
        }
        require(indexFile != null) {
            "La carpeta es de solo lectura y todavía no contiene el índice $SHARED_INDEX_FILE."
        }

        var spreadsheet = drive.findFileByName(
            accessToken,
            folder.id,
            SPREADSHEET_NAME,
            DriveRestClient.SHEET_MIME
        )
        if (spreadsheet == null && folder.canAddChildren) {
            spreadsheet = drive.createControlSpreadsheet(accessToken, folder.id, SPREADSHEET_NAME)
        }

        val now = System.currentTimeMillis()
        val configuration = current.copy(
            folderId = folder.id,
            folderName = folderName.trim().ifBlank { folder.name },
            spreadsheetId = spreadsheet?.id.orEmpty(),
            spreadsheetName = spreadsheet?.name ?: SPREADSHEET_NAME,
            indexFileId = indexFile.id,
            canEdit = folder.canEdit && folder.canAddChildren,
            updatedAt = now,
            updatedBy = user.email
        )
        savePrivateConfiguration(accessToken, configuration)
        val documents = loadDocuments(accessToken, configuration)
        if (configuration.canEdit && configuration.spreadsheetId.isNotBlank()) {
            drive.rewriteControlSpreadsheet(accessToken, configuration.spreadsheetId, documents)
        }
        return DriveWorkspace(user.copy(canEdit = configuration.canEdit), configuration, documents)
    }

    suspend fun uploadDocument(
        user: SessionUser,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>,
        accessToken: String,
        code: String,
        revision: String,
        sourceFileName: String,
        pdfBytes: ByteArray
    ): DriveWorkspace {
        require(configuration.canEdit) { "Tu cuenta tiene permiso de lectura, pero no de escritura en esta carpeta." }
        require(configuration.isConfigured) { "Primero debes configurar la carpeta compartida de Drive." }
        val cleanCode = code.trim().uppercase()
        require(cleanCode.isNotBlank()) { "La codificación del plano es obligatoria." }
        require(cleanCode.length <= 120) { "La codificación del plano es demasiado larga." }
        val cleanRevision = revision.trim().uppercase().ifBlank { "A" }
        require(cleanRevision.length <= 30) { "La revisión es demasiado larga." }
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
            originalFileName = sourceFileName,
            fileName = driveFile.name,
            revision = cleanRevision,
            status = "PENDIENTE",
            driveFileId = driveFile.id,
            driveWebViewLink = driveFile.webViewLink,
            uploadedByName = user.displayName,
            uploadedByEmail = user.email,
            uploadedAt = now,
            signed = false,
            updatedAt = now
        )
        val updated = (documents + record).sortedByDescending { it.updatedAt }
        val resolvedConfiguration = saveDocuments(accessToken, configuration, updated)
        return DriveWorkspace(user.copy(canEdit = true), resolvedConfiguration, updated)
    }

    suspend fun updateRevision(
        user: SessionUser,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>,
        accessToken: String,
        document: DocumentRecord,
        newRevision: String
    ): DriveWorkspace {
        require(configuration.canEdit) { "No tienes permiso para modificar archivos en esta carpeta." }
        val revision = newRevision.trim().uppercase()
        require(revision.isNotBlank()) { "La revisión no puede quedar vacía." }
        require(revision.length <= 30) { "La revisión es demasiado larga." }
        val newFileName = buildManagedFileName(
            document.code,
            revision,
            document.originalFileName.ifBlank { document.fileName }
        )
        val renamed = drive.renameFile(accessToken, document.driveFileId, newFileName)
        val now = System.currentTimeMillis()
        val updatedDocument = document.copy(
            revision = revision,
            fileName = renamed.name,
            driveWebViewLink = renamed.webViewLink.ifBlank { document.driveWebViewLink },
            updatedAt = now
        )
        val updated = documents.replace(updatedDocument)
        val resolvedConfiguration = saveDocuments(accessToken, configuration, updated)
        return DriveWorkspace(user.copy(canEdit = true), resolvedConfiguration, updated)
    }

    suspend fun markSigned(
        user: SessionUser,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>,
        accessToken: String,
        document: DocumentRecord,
        signed: Boolean,
        signatureMethod: String
    ): DriveWorkspace {
        require(configuration.canEdit) { "No tienes permiso para firmar documentos en esta carpeta." }
        val now = System.currentTimeMillis()
        val updatedDocument = if (signed) {
            document.copy(
                signed = true,
                status = "FIRMADO",
                signedByName = user.displayName,
                signedByEmail = user.email,
                signedAt = now,
                signatureMethod = signatureMethod,
                updatedAt = now
            )
        } else {
            document.copy(
                signed = false,
                status = "PENDIENTE",
                signedByName = "",
                signedByEmail = "",
                signedAt = 0L,
                signatureMethod = "",
                updatedAt = now
            )
        }
        val updated = documents.replace(updatedDocument)
        val resolvedConfiguration = saveDocuments(accessToken, configuration, updated)
        return DriveWorkspace(user.copy(canEdit = true), resolvedConfiguration, updated)
    }

    suspend fun downloadPdf(
        accessToken: String,
        driveFileId: String,
        target: File
    ): File = drive.downloadPdf(accessToken, driveFileId, target)

    fun extractDriveFolderId(input: String): String {
        val value = input.trim()
        val patterns = listOf(
            Regex("/folders/([a-zA-Z0-9_-]+)"),
            Regex("[?&]id=([a-zA-Z0-9_-]+)"),
            Regex("^([a-zA-Z0-9_-]{10,})$")
        )
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1) }.orEmpty()
    }

    private suspend fun resolveWorkspaceFiles(
        accessToken: String,
        current: DriveConfiguration
    ): DriveConfiguration {
        val folder = drive.getFolderInfo(accessToken, current.folderId)
        val indexFile = current.indexFileId.takeIf { it.isNotBlank() }?.let { id ->
            runCatching { drive.readTextFile(accessToken, id); id }.getOrNull()
        } ?: drive.findFileByName(
            accessToken,
            folder.id,
            SHARED_INDEX_FILE,
            DriveRestClient.JSON_MIME
        )?.id.orEmpty()

        val resolvedIndexId = if (indexFile.isBlank() && folder.canAddChildren) {
            drive.createTextFile(
                accessToken,
                folder.id,
                SHARED_INDEX_FILE,
                DriveRestClient.JSON_MIME,
                emptyDocumentIndexJson()
            ).id
        } else {
            indexFile
        }
        require(resolvedIndexId.isNotBlank()) {
            "No existe el índice documental en la carpeta compartida."
        }

        val spreadsheetId = current.spreadsheetId.takeIf { it.isNotBlank() }
            ?: drive.findFileByName(
                accessToken,
                folder.id,
                SPREADSHEET_NAME,
                DriveRestClient.SHEET_MIME
            )?.id.orEmpty()

        return current.copy(
            folderName = current.folderName.ifBlank { folder.name },
            indexFileId = resolvedIndexId,
            spreadsheetId = spreadsheetId,
            canEdit = folder.canEdit && folder.canAddChildren
        )
    }

    private suspend fun loadDocuments(
        accessToken: String,
        configuration: DriveConfiguration
    ): List<DocumentRecord> {
        if (configuration.indexFileId.isBlank()) return emptyList()
        val json = JSONObject(drive.readTextFile(accessToken, configuration.indexFileId))
        val array = json.optJSONArray("documents") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                add(parseDocument(array.getJSONObject(index)))
            }
        }.sortedByDescending { it.updatedAt }
    }

    private suspend fun saveDocuments(
        accessToken: String,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>
    ): DriveConfiguration {
        require(configuration.canEdit) { "No tienes permiso de escritura en la carpeta compartida." }
        var indexId = configuration.indexFileId
        if (indexId.isBlank()) {
            indexId = drive.createTextFile(
                accessToken,
                configuration.folderId,
                SHARED_INDEX_FILE,
                DriveRestClient.JSON_MIME,
                documentIndexJson(documents)
            ).id
        } else {
            drive.updateTextFile(
                accessToken,
                indexId,
                DriveRestClient.JSON_MIME,
                documentIndexJson(documents)
            )
        }
        val resolved = configuration.copy(indexFileId = indexId)
        if (resolved.spreadsheetId.isNotBlank()) {
            drive.rewriteControlSpreadsheet(accessToken, resolved.spreadsheetId, documents)
        }
        savePrivateConfiguration(accessToken, resolved)
        return resolved
    }

    private suspend fun savePrivateConfiguration(
        accessToken: String,
        configuration: DriveConfiguration
    ) {
        require(configuration.configFileId.isNotBlank()) { "No se encontró el archivo privado de configuración." }
        drive.updateTextFile(
            accessToken,
            configuration.configFileId,
            DriveRestClient.JSON_MIME,
            configurationJson(configuration)
        )
    }

    private fun configurationJson(configuration: DriveConfiguration): String = JSONObject()
        .put("version", 1)
        .put("privateFolderId", configuration.privateFolderId)
        .put("folderId", configuration.folderId)
        .put("folderName", configuration.folderName)
        .put("spreadsheetId", configuration.spreadsheetId)
        .put("spreadsheetName", configuration.spreadsheetName)
        .put("indexFileId", configuration.indexFileId)
        .put("updatedAt", configuration.updatedAt)
        .put("updatedBy", configuration.updatedBy)
        .toString(2)

    private fun emptyConfigurationJson(privateFolderId: String): String = configurationJson(
        DriveConfiguration(privateFolderId = privateFolderId)
    )

    private fun parseConfiguration(text: String): DriveConfiguration {
        if (text.isBlank()) return DriveConfiguration()
        val json = JSONObject(text)
        return DriveConfiguration(
            privateFolderId = json.optString("privateFolderId"),
            folderId = json.optString("folderId"),
            folderName = json.optString("folderName"),
            spreadsheetId = json.optString("spreadsheetId"),
            spreadsheetName = json.optString("spreadsheetName", SPREADSHEET_NAME),
            indexFileId = json.optString("indexFileId"),
            updatedAt = json.optLong("updatedAt"),
            updatedBy = json.optString("updatedBy")
        )
    }

    private fun documentIndexJson(documents: List<DocumentRecord>): String {
        val array = JSONArray()
        documents.forEach { document ->
            array.put(
                JSONObject()
                    .put("id", document.id)
                    .put("code", document.code)
                    .put("originalFileName", document.originalFileName)
                    .put("fileName", document.fileName)
                    .put("revision", document.revision)
                    .put("status", document.status)
                    .put("driveFileId", document.driveFileId)
                    .put("driveWebViewLink", document.driveWebViewLink)
                    .put("uploadedByName", document.uploadedByName)
                    .put("uploadedByEmail", document.uploadedByEmail)
                    .put("uploadedAt", document.uploadedAt)
                    .put("signed", document.signed)
                    .put("signedByName", document.signedByName)
                    .put("signedByEmail", document.signedByEmail)
                    .put("signedAt", document.signedAt)
                    .put("signatureMethod", document.signatureMethod)
                    .put("updatedAt", document.updatedAt)
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("updatedAt", System.currentTimeMillis())
            .put("documents", array)
            .toString(2)
    }

    private fun emptyDocumentIndexJson(): String = documentIndexJson(emptyList())

    private fun parseDocument(json: JSONObject): DocumentRecord = DocumentRecord(
        id = json.optString("id"),
        code = json.optString("code"),
        originalFileName = json.optString("originalFileName"),
        fileName = json.optString("fileName"),
        revision = json.optString("revision", "A"),
        status = json.optString("status", "PENDIENTE"),
        driveFileId = json.optString("driveFileId"),
        driveWebViewLink = json.optString("driveWebViewLink"),
        uploadedByName = json.optString("uploadedByName"),
        uploadedByEmail = json.optString("uploadedByEmail"),
        uploadedAt = json.optLong("uploadedAt"),
        signed = json.optBoolean("signed"),
        signedByName = json.optString("signedByName"),
        signedByEmail = json.optString("signedByEmail"),
        signedAt = json.optLong("signedAt"),
        signatureMethod = json.optString("signatureMethod"),
        updatedAt = json.optLong("updatedAt")
    )

    private fun List<DocumentRecord>.replace(updated: DocumentRecord): List<DocumentRecord> =
        map { if (it.id == updated.id) updated else it }.sortedByDescending { it.updatedAt }

    private fun buildManagedFileName(code: String, revision: String, original: String): String {
        fun safePart(value: String, fallback: String): String = value
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .take(120)
            .ifBlank { fallback }

        val safeCode = safePart(code, "PLANO")
        val safeRevision = safePart(revision, "A")
        val safeOriginal = safePart(original.substringBeforeLast('.'), "documento")
        return "${safeCode}_REV-${safeRevision}_${safeOriginal}.pdf"
    }

    companion object {
        private const val PRIVATE_FOLDER_NAME = "GestionPlanosSKM-Privado"
        private const val PRIVATE_CONFIG_FILE = "claves-configuracion.json"
        private const val SHARED_INDEX_FILE = "control-documental.json"
        private const val SPREADSHEET_NAME = "Control de Documentos SKM"
    }
}
