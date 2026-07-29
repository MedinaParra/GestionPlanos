package com.example.document.data

import android.content.Context
import com.example.document.drive.DriveBinaryClient
import com.example.document.drive.DriveRestClient
import com.example.document.model.ApprovalSignature
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.DriveWorkspace
import com.example.document.model.SessionUser
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.example.document.model.UserRole
import com.example.document.model.WorkflowSettings
import com.example.document.pdf.PdfStampService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DocumentRepository(context: Context) {
    private val drive = DriveRestClient()
    private val binary = DriveBinaryClient()
    private val pdfStamp = PdfStampService(context)

    suspend fun connect(accessToken: String): DriveWorkspace {
        val driveUser = drive.getCurrentUser(accessToken)
        val privateFolder = drive.findOrCreateFolder(accessToken, "root", PRIVATE_FOLDER_NAME)
        val configFile = drive.findFileByName(
            accessToken, privateFolder.id, PRIVATE_CONFIG_FILE, DriveRestClient.JSON_MIME
        ) ?: drive.createTextFile(
            accessToken,
            privateFolder.id,
            PRIVATE_CONFIG_FILE,
            DriveRestClient.JSON_MIME,
            configurationJson(DriveConfiguration(privateFolderId = privateFolder.id))
        )
        var configuration = parseConfiguration(drive.readTextFile(accessToken, configFile.id)).copy(
            privateFolderId = privateFolder.id,
            configFileId = configFile.id
        )
        if (!configuration.isConfigured) {
            val temporaryProfile = UserProfile(
                permissionId = driveUser.permissionId,
                email = driveUser.email,
                displayName = driveUser.displayName,
                lastSeenAt = System.currentTimeMillis()
            )
            return DriveWorkspace(
                session = SessionUser(
                    permissionId = driveUser.permissionId,
                    email = driveUser.email,
                    displayName = driveUser.displayName,
                    canEdit = false,
                    profile = temporaryProfile
                ),
                configuration = configuration,
                documents = emptyList()
            )
        }

        configuration = resolveWorkspaceFiles(accessToken, configuration)
        var users = loadUsers(accessToken, configuration)
        val now = System.currentTimeMillis()
        val existing = users.firstOrNull { it.email.equals(driveUser.email, true) }
        val profile = if (existing == null) {
            val first = users.isEmpty()
            UserProfile(
                permissionId = driveUser.permissionId,
                email = driveUser.email,
                displayName = driveUser.displayName,
                role = if (first) UserRole.ADMIN else UserRole.USER,
                active = true,
                requiredSigner = first,
                firstSeenAt = now,
                lastSeenAt = now
            ).also {
                users = (users + it).sortedBy { user -> user.displayName.lowercase() }
                saveUsers(accessToken, configuration, users)
            }
        } else {
            existing.copy(
                permissionId = driveUser.permissionId.ifBlank { existing.permissionId },
                displayName = existing.displayName.ifBlank { driveUser.displayName },
                lastSeenAt = now
            ).also { updated ->
                users = users.replaceUser(updated)
                saveUsers(accessToken, configuration, users)
            }
        }
        savePrivateConfiguration(accessToken, configuration)
        return DriveWorkspace(
            session = SessionUser(
                permissionId = driveUser.permissionId,
                email = driveUser.email,
                displayName = profile.displayName,
                canEdit = configuration.canEdit,
                profile = profile
            ),
            configuration = configuration,
            documents = loadDocuments(accessToken, configuration),
            users = users,
            settings = loadSettings(accessToken, configuration)
        )
    }

    suspend fun refresh(
        accessToken: String,
        current: DriveConfiguration,
        session: SessionUser
    ): DriveWorkspace {
        if (!current.isConfigured) return DriveWorkspace(session, current, emptyList())
        val configuration = resolveWorkspaceFiles(accessToken, current)
        val users = loadUsers(accessToken, configuration)
        val profile = users.firstOrNull { it.email.equals(session.email, true) } ?: session.profile
        savePrivateConfiguration(accessToken, configuration)
        return DriveWorkspace(
            session = session.copy(
                displayName = profile.displayName.ifBlank { session.displayName },
                canEdit = configuration.canEdit,
                profile = profile
            ),
            configuration = configuration,
            documents = loadDocuments(accessToken, configuration),
            users = users,
            settings = loadSettings(accessToken, configuration)
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
        require(folder.canAddChildren) {
            "Para configurar por primera vez la carpeta debes tener permiso de editor."
        }
        val systemFolder = drive.findOrCreateFolder(accessToken, folder.id, SYSTEM_FOLDER_NAME)
        val indexFile = drive.findFileByName(
            accessToken, folder.id, SHARED_INDEX_FILE, DriveRestClient.JSON_MIME
        ) ?: drive.createTextFile(
            accessToken, folder.id, SHARED_INDEX_FILE, DriveRestClient.JSON_MIME, documentIndexJson(emptyList())
        )
        val usersFile = drive.findFileByName(
            accessToken, systemFolder.id, USERS_FILE, DriveRestClient.JSON_MIME
        ) ?: drive.createTextFile(
            accessToken, systemFolder.id, USERS_FILE, DriveRestClient.JSON_MIME, usersJson(emptyList())
        )
        val settingsFile = drive.findFileByName(
            accessToken, systemFolder.id, SETTINGS_FILE, DriveRestClient.JSON_MIME
        ) ?: drive.createTextFile(
            accessToken, systemFolder.id, SETTINGS_FILE, DriveRestClient.JSON_MIME, settingsJson(WorkflowSettings())
        )
        val spreadsheet = drive.findFileByName(
            accessToken, folder.id, SPREADSHEET_NAME, DriveRestClient.SHEET_MIME
        ) ?: drive.createControlSpreadsheet(accessToken, folder.id, SPREADSHEET_NAME)
        val configuration = current.copy(
            folderId = folder.id,
            folderName = folderName.trim().ifBlank { folder.name },
            systemFolderId = systemFolder.id,
            indexFileId = indexFile.id,
            usersFileId = usersFile.id,
            settingsFileId = settingsFile.id,
            spreadsheetId = spreadsheet.id,
            spreadsheetName = spreadsheet.name,
            canEdit = folder.canEdit && folder.canAddChildren,
            updatedAt = System.currentTimeMillis(),
            updatedBy = user.email
        )
        savePrivateConfiguration(accessToken, configuration)
        return connect(accessToken)
    }

    suspend fun updateOwnProfile(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        displayName: String,
        rut: String,
        position: String,
        placement: SignaturePlacement,
        photoBytes: ByteArray?,
        photoMime: String?,
        signaturePng: ByteArray?
    ): DriveWorkspace {
        require(configuration.isConfigured) { "Configura primero la carpeta principal." }
        val users = loadUsers(accessToken, configuration)
        val current = users.firstOrNull { it.email.equals(user.email, true) } ?: user.profile
        var photoFileId = current.photoFileId
        if (photoBytes != null && photoBytes.isNotEmpty()) {
            photoFileId = if (photoFileId.isBlank()) {
                binary.upload(
                    accessToken,
                    configuration.systemFolderId,
                    "foto_${safeToken(user.email)}.${if (photoMime == DriveBinaryClient.PNG_MIME) "png" else "jpg"}",
                    photoMime ?: DriveBinaryClient.JPEG_MIME,
                    photoBytes
                ).id
            } else {
                binary.update(accessToken, photoFileId, photoMime ?: DriveBinaryClient.JPEG_MIME, photoBytes).id
            }
        }
        var signatureFileId = current.signatureFileId
        if (signaturePng != null && signaturePng.isNotEmpty()) {
            signatureFileId = if (signatureFileId.isBlank()) {
                binary.upload(
                    accessToken,
                    configuration.systemFolderId,
                    "firma_${safeToken(user.email)}.png",
                    DriveBinaryClient.PNG_MIME,
                    signaturePng
                ).id
            } else {
                binary.update(accessToken, signatureFileId, DriveBinaryClient.PNG_MIME, signaturePng).id
            }
        }
        val updated = current.copy(
            displayName = displayName.trim().ifBlank { current.displayName.ifBlank { user.displayName } },
            rut = rut.trim(),
            position = position.trim(),
            photoFileId = photoFileId,
            signatureFileId = signatureFileId,
            placement = placement,
            lastSeenAt = System.currentTimeMillis()
        )
        val updatedUsers = users.replaceUser(updated)
        saveUsers(accessToken, configuration, updatedUsers)
        return refresh(accessToken, configuration, user.copy(profile = updated, displayName = updated.displayName))
    }

    suspend fun updateUserByAdmin(
        admin: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        targetEmail: String,
        role: UserRole,
        active: Boolean,
        requiredSigner: Boolean
    ): DriveWorkspace {
        require(admin.isAdmin) { "Solo el administrador puede cambiar permisos de usuarios." }
        val users = loadUsers(accessToken, configuration)
        val target = users.firstOrNull { it.email.equals(targetEmail, true) }
            ?: error("No se encontró el usuario.")
        val updated = target.copy(role = role, active = active, requiredSigner = requiredSigner)
        saveUsers(accessToken, configuration, users.replaceUser(updated))
        return refresh(accessToken, configuration, admin)
    }

    suspend fun updateWorkflowSettings(
        admin: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        settings: WorkflowSettings
    ): DriveWorkspace {
        require(admin.isAdmin) { "Solo el administrador puede cambiar los plazos." }
        require(settings.reviewDays in 1..30) { "El plazo debe estar entre 1 y 30 días." }
        drive.updateTextFile(
            accessToken,
            configuration.settingsFileId,
            DriveRestClient.JSON_MIME,
            settingsJson(settings)
        )
        return refresh(accessToken, configuration, admin)
    }

    suspend fun uploadDocument(
        user: SessionUser,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>,
        users: List<UserProfile>,
        settings: WorkflowSettings,
        accessToken: String,
        otNumber: String,
        code: String,
        revision: String,
        sourceFileName: String,
        pdfBytes: ByteArray
    ): DriveWorkspace {
        require(user.isAdmin) { "Solo un administrador puede subir planos al flujo oficial." }
        require(configuration.canEdit) { "Tu cuenta no tiene escritura en la carpeta principal." }
        val cleanOt = otNumber.filter { it.isDigit() }.ifBlank { error("Ingresa el número de OT.") }
        val cleanCode = code.trim().uppercase().ifBlank { error("Ingresa el código del plano.") }
        val cleanRevision = revision.trim().uppercase().removePrefix("REV").trim().ifBlank { "0" }
        val reviewers = users.filter { it.active && it.requiredSigner }.map { it.email }.distinct()
            .ifEmpty { listOf(user.email) }
        val otFolder = drive.findOrCreateFolder(accessToken, configuration.folderId, "OT $cleanOt")
        val revisionFolder = drive.findOrCreateFolder(accessToken, otFolder.id, "Rev $cleanRevision")
        val originalFolder = drive.findOrCreateFolder(accessToken, revisionFolder.id, "Original")
        val reviewFolder = drive.findOrCreateFolder(accessToken, revisionFolder.id, "Revision")
        val signaturesFolder = drive.findOrCreateFolder(accessToken, revisionFolder.id, "Firmas")
        val finalFolder = drive.findOrCreateFolder(accessToken, revisionFolder.id, "Final")
        val baseName = buildManagedFileName(cleanCode, cleanRevision, sourceFileName)
        val original = binary.upload(
            accessToken, originalFolder.id, "ORIGINAL_$baseName", DriveBinaryClient.PDF_MIME, pdfBytes
        )
        val noAptoBytes = pdfStamp.addNoAptoWatermark(pdfBytes)
        val review = binary.upload(
            accessToken, reviewFolder.id, "NO_APTO_$baseName", DriveBinaryClient.PDF_MIME, noAptoBytes
        )
        val now = System.currentTimeMillis()
        val record = DocumentRecord(
            id = UUID.randomUUID().toString(),
            otNumber = cleanOt,
            code = cleanCode,
            originalFileName = sourceFileName,
            fileName = review.name,
            revision = cleanRevision,
            status = "EN_REVISIÓN",
            driveFileId = review.id,
            driveWebViewLink = review.webViewLink,
            originalPdfFileId = original.id,
            currentPdfFileId = review.id,
            otFolderId = otFolder.id,
            revisionFolderId = revisionFolder.id,
            originalFolderId = originalFolder.id,
            reviewFolderId = reviewFolder.id,
            signaturesFolderId = signaturesFolder.id,
            finalFolderId = finalFolder.id,
            uploadedByName = user.profile.displayName.ifBlank { user.displayName },
            uploadedByEmail = user.email,
            uploadedAt = now,
            dueAt = now + settings.reviewDays * 24L * 60L * 60L * 1000L,
            requiredReviewerEmails = reviewers,
            currentReviewerIndex = 0,
            updatedAt = now
        )
        val updatedDocuments = (documents + record).sortedByDescending { it.updatedAt }
        saveDocuments(accessToken, configuration, updatedDocuments)
        return refresh(accessToken, configuration, user)
    }

    suspend fun signDocument(
        user: SessionUser,
        configuration: DriveConfiguration,
        accessToken: String,
        documentId: String,
        placement: SignaturePlacement,
        signatureMethod: String
    ): DriveWorkspace {
        require(configuration.canEdit) { "No tienes permiso de escritura en la carpeta." }
        require(user.profile.profileComplete) {
            "Completa nombre, cargo, RUT y firma manual en Mi perfil antes de firmar."
        }
        val freshDocuments = loadDocuments(accessToken, configuration)
        val document = freshDocuments.firstOrNull { it.id == documentId } ?: error("El plano ya no existe.")
        require(document.canBeSignedBy(user.email)) {
            val current = document.currentReviewerEmail
            if (current.isBlank()) "El documento ya terminó su revisión." else "El turno de firma corresponde a $current."
        }
        val signatureBytes = binary.downloadBytes(accessToken, user.profile.signatureFileId)
        val currentPdfBytes = binary.downloadBytes(accessToken, document.currentPdfFileId.ifBlank { document.driveFileId })
        val now = System.currentTimeMillis()
        val stamped = pdfStamp.addApprovalSignature(
            currentPdfBytes,
            signatureBytes,
            user.profile,
            placement,
            now
        )
        val ordinal = document.approvals.size + 1
        val signedCopy = binary.upload(
            accessToken,
            document.signaturesFolderId,
            "${ordinal.toString().padStart(2, '0')}_${safeToken(user.email)}_${buildManagedFileName(document.code, document.revision, document.originalFileName)}",
            DriveBinaryClient.PDF_MIME,
            stamped
        )
        val approval = ApprovalSignature(
            email = user.email,
            name = user.profile.displayName,
            rut = user.profile.rut,
            position = user.profile.position,
            signedAt = now,
            method = signatureMethod,
            signatureFileId = user.profile.signatureFileId,
            signedPdfFileId = signedCopy.id
        )
        val approvals = document.approvals + approval
        val isFinal = approvals.size >= document.requiredReviewerEmails.size
        val updatedDocument = if (isFinal) {
            val finalBytes = pdfStamp.addAptoParaFabricacion(stamped)
            val finalFile = binary.upload(
                accessToken,
                document.finalFolderId,
                "APTO_${buildManagedFileName(document.code, document.revision, document.originalFileName)}",
                DriveBinaryClient.PDF_MIME,
                finalBytes
            )
            document.copy(
                status = "APTO_PARA_FABRICACIÓN",
                fileName = finalFile.name,
                driveFileId = finalFile.id,
                currentPdfFileId = finalFile.id,
                finalPdfFileId = finalFile.id,
                driveWebViewLink = finalFile.webViewLink,
                approvals = approvals,
                currentReviewerIndex = document.requiredReviewerEmails.size,
                signed = true,
                signedByName = user.profile.displayName,
                signedByEmail = user.email,
                signedAt = now,
                signatureMethod = signatureMethod,
                updatedAt = now
            )
        } else {
            document.copy(
                status = "EN_REVISIÓN",
                fileName = signedCopy.name,
                driveFileId = signedCopy.id,
                currentPdfFileId = signedCopy.id,
                driveWebViewLink = signedCopy.webViewLink,
                approvals = approvals,
                currentReviewerIndex = document.currentReviewerIndex + 1,
                signed = false,
                signedByName = user.profile.displayName,
                signedByEmail = user.email,
                signedAt = now,
                signatureMethod = signatureMethod,
                updatedAt = now
            )
        }
        saveDocuments(accessToken, configuration, freshDocuments.replaceDocument(updatedDocument))
        return refresh(accessToken, configuration, user)
    }

    suspend fun downloadPdf(accessToken: String, driveFileId: String, target: File): File =
        binary.downloadToFile(accessToken, driveFileId, target)

    suspend fun downloadAsset(accessToken: String, driveFileId: String, target: File): File =
        binary.downloadToFile(accessToken, driveFileId, target)

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
        val systemFolder = current.systemFolderId.takeIf { it.isNotBlank() }
            ?: drive.findFileByName(accessToken, folder.id, SYSTEM_FOLDER_NAME, DriveRestClient.FOLDER_MIME)?.id
            ?: if (folder.canAddChildren) drive.findOrCreateFolder(accessToken, folder.id, SYSTEM_FOLDER_NAME).id else ""
        require(systemFolder.isNotBlank()) { "No se encontró la carpeta de sistema." }
        fun fileId(currentId: String, parent: String, name: String): String = currentId.takeIf { it.isNotBlank() }
            ?: runCatching { drive.findFileByName(accessToken, parent, name, DriveRestClient.JSON_MIME)?.id }.getOrNull().orEmpty()
        var indexId = fileId(current.indexFileId, folder.id, SHARED_INDEX_FILE)
        var usersId = fileId(current.usersFileId, systemFolder, USERS_FILE)
        var settingsId = fileId(current.settingsFileId, systemFolder, SETTINGS_FILE)
        if (folder.canAddChildren) {
            if (indexId.isBlank()) indexId = drive.createTextFile(
                accessToken, folder.id, SHARED_INDEX_FILE, DriveRestClient.JSON_MIME, documentIndexJson(emptyList())
            ).id
            if (usersId.isBlank()) usersId = drive.createTextFile(
                accessToken, systemFolder, USERS_FILE, DriveRestClient.JSON_MIME, usersJson(emptyList())
            ).id
            if (settingsId.isBlank()) settingsId = drive.createTextFile(
                accessToken, systemFolder, SETTINGS_FILE, DriveRestClient.JSON_MIME, settingsJson(WorkflowSettings())
            ).id
        }
        require(indexId.isNotBlank() && usersId.isNotBlank() && settingsId.isNotBlank()) {
            "Faltan archivos de control en la carpeta compartida."
        }
        val spreadsheetId = current.spreadsheetId.takeIf { it.isNotBlank() }
            ?: drive.findFileByName(accessToken, folder.id, SPREADSHEET_NAME, DriveRestClient.SHEET_MIME)?.id.orEmpty()
        return current.copy(
            folderName = current.folderName.ifBlank { folder.name },
            systemFolderId = systemFolder,
            indexFileId = indexId,
            usersFileId = usersId,
            settingsFileId = settingsId,
            spreadsheetId = spreadsheetId,
            canEdit = folder.canEdit && folder.canAddChildren
        )
    }

    private suspend fun loadDocuments(accessToken: String, configuration: DriveConfiguration): List<DocumentRecord> {
        if (configuration.indexFileId.isBlank()) return emptyList()
        val json = JSONObject(drive.readTextFile(accessToken, configuration.indexFileId))
        val array = json.optJSONArray("documents") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) add(parseDocument(array.getJSONObject(index)))
        }.sortedByDescending { it.updatedAt }
    }

    private suspend fun saveDocuments(
        accessToken: String,
        configuration: DriveConfiguration,
        documents: List<DocumentRecord>
    ) {
        drive.updateTextFile(
            accessToken,
            configuration.indexFileId,
            DriveRestClient.JSON_MIME,
            documentIndexJson(documents)
        )
        if (configuration.spreadsheetId.isNotBlank()) {
            drive.rewriteControlSpreadsheet(accessToken, configuration.spreadsheetId, documents)
        }
    }

    private suspend fun loadUsers(accessToken: String, configuration: DriveConfiguration): List<UserProfile> {
        val json = JSONObject(drive.readTextFile(accessToken, configuration.usersFileId))
        val array = json.optJSONArray("users") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) add(parseUser(array.getJSONObject(index)))
        }.sortedBy { it.displayName.lowercase() }
    }

    private suspend fun saveUsers(
        accessToken: String,
        configuration: DriveConfiguration,
        users: List<UserProfile>
    ) {
        drive.updateTextFile(
            accessToken,
            configuration.usersFileId,
            DriveRestClient.JSON_MIME,
            usersJson(users)
        )
    }

    private suspend fun loadSettings(accessToken: String, configuration: DriveConfiguration): WorkflowSettings =
        parseSettings(drive.readTextFile(accessToken, configuration.settingsFileId))

    private suspend fun savePrivateConfiguration(accessToken: String, configuration: DriveConfiguration) {
        drive.updateTextFile(
            accessToken,
            configuration.configFileId,
            DriveRestClient.JSON_MIME,
            configurationJson(configuration)
        )
    }

    private fun configurationJson(configuration: DriveConfiguration): String = JSONObject()
        .put("privateFolderId", configuration.privateFolderId)
        .put("folderId", configuration.folderId)
        .put("folderName", configuration.folderName)
        .put("spreadsheetId", configuration.spreadsheetId)
        .put("spreadsheetName", configuration.spreadsheetName)
        .put("indexFileId", configuration.indexFileId)
        .put("usersFileId", configuration.usersFileId)
        .put("settingsFileId", configuration.settingsFileId)
        .put("systemFolderId", configuration.systemFolderId)
        .put("updatedAt", configuration.updatedAt)
        .put("updatedBy", configuration.updatedBy)
        .toString(2)

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
            usersFileId = json.optString("usersFileId"),
            settingsFileId = json.optString("settingsFileId"),
            systemFolderId = json.optString("systemFolderId"),
            updatedAt = json.optLong("updatedAt"),
            updatedBy = json.optString("updatedBy")
        )
    }

    private fun usersJson(users: List<UserProfile>): String {
        val array = JSONArray()
        users.forEach { user ->
            array.put(
                JSONObject()
                    .put("permissionId", user.permissionId)
                    .put("email", user.email)
                    .put("displayName", user.displayName)
                    .put("rut", user.rut)
                    .put("position", user.position)
                    .put("photoFileId", user.photoFileId)
                    .put("signatureFileId", user.signatureFileId)
                    .put("role", user.role.name)
                    .put("active", user.active)
                    .put("requiredSigner", user.requiredSigner)
                    .put("placementX", user.placement.x.toDouble())
                    .put("placementY", user.placement.y.toDouble())
                    .put("placementWidth", user.placement.width.toDouble())
                    .put("firstSeenAt", user.firstSeenAt)
                    .put("lastSeenAt", user.lastSeenAt)
            )
        }
        return JSONObject().put("version", 2).put("users", array).toString(2)
    }

    private fun parseUser(json: JSONObject): UserProfile = UserProfile(
        permissionId = json.optString("permissionId"),
        email = json.optString("email"),
        displayName = json.optString("displayName"),
        rut = json.optString("rut"),
        position = json.optString("position"),
        photoFileId = json.optString("photoFileId"),
        signatureFileId = json.optString("signatureFileId"),
        role = UserRole.from(json.optString("role")),
        active = json.optBoolean("active", true),
        requiredSigner = json.optBoolean("requiredSigner", false),
        placement = SignaturePlacement(
            x = json.optDouble("placementX", 0.62).toFloat(),
            y = json.optDouble("placementY", 0.73).toFloat(),
            width = json.optDouble("placementWidth", 0.30).toFloat()
        ),
        firstSeenAt = json.optLong("firstSeenAt"),
        lastSeenAt = json.optLong("lastSeenAt")
    )

    private fun settingsJson(settings: WorkflowSettings): String = JSONObject()
        .put("reviewDays", settings.reviewDays)
        .put("morningHour", settings.morningHour)
        .put("afternoonHour", settings.afternoonHour)
        .put("escalationAfterHours", settings.escalationAfterHours)
        .toString(2)

    private fun parseSettings(text: String): WorkflowSettings {
        if (text.isBlank()) return WorkflowSettings()
        val json = JSONObject(text)
        return WorkflowSettings(
            reviewDays = json.optInt("reviewDays", 2),
            morningHour = json.optInt("morningHour", 8),
            afternoonHour = json.optInt("afternoonHour", 15),
            escalationAfterHours = json.optInt("escalationAfterHours", 36)
        )
    }

    private fun documentIndexJson(documents: List<DocumentRecord>): String {
        val array = JSONArray()
        documents.forEach { document ->
            val reviewers = JSONArray().apply { document.requiredReviewerEmails.forEach { put(it) } }
            val approvals = JSONArray().apply {
                document.approvals.forEach { approval ->
                    put(
                        JSONObject()
                            .put("email", approval.email)
                            .put("name", approval.name)
                            .put("rut", approval.rut)
                            .put("position", approval.position)
                            .put("signedAt", approval.signedAt)
                            .put("method", approval.method)
                            .put("signatureFileId", approval.signatureFileId)
                            .put("signedPdfFileId", approval.signedPdfFileId)
                    )
                }
            }
            array.put(
                JSONObject()
                    .put("id", document.id)
                    .put("otNumber", document.otNumber)
                    .put("code", document.code)
                    .put("originalFileName", document.originalFileName)
                    .put("fileName", document.fileName)
                    .put("revision", document.revision)
                    .put("status", document.status)
                    .put("driveFileId", document.driveFileId)
                    .put("driveWebViewLink", document.driveWebViewLink)
                    .put("originalPdfFileId", document.originalPdfFileId)
                    .put("currentPdfFileId", document.currentPdfFileId)
                    .put("finalPdfFileId", document.finalPdfFileId)
                    .put("otFolderId", document.otFolderId)
                    .put("revisionFolderId", document.revisionFolderId)
                    .put("originalFolderId", document.originalFolderId)
                    .put("reviewFolderId", document.reviewFolderId)
                    .put("signaturesFolderId", document.signaturesFolderId)
                    .put("finalFolderId", document.finalFolderId)
                    .put("uploadedByName", document.uploadedByName)
                    .put("uploadedByEmail", document.uploadedByEmail)
                    .put("uploadedAt", document.uploadedAt)
                    .put("dueAt", document.dueAt)
                    .put("requiredReviewerEmails", reviewers)
                    .put("currentReviewerIndex", document.currentReviewerIndex)
                    .put("approvals", approvals)
                    .put("signed", document.signed)
                    .put("signedByName", document.signedByName)
                    .put("signedByEmail", document.signedByEmail)
                    .put("signedAt", document.signedAt)
                    .put("signatureMethod", document.signatureMethod)
                    .put("updatedAt", document.updatedAt)
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("updatedAt", System.currentTimeMillis())
            .put("documents", array)
            .toString(2)
    }

    private fun parseDocument(json: JSONObject): DocumentRecord {
        val reviewerArray = json.optJSONArray("requiredReviewerEmails") ?: JSONArray()
        val reviewers = buildList { for (i in 0 until reviewerArray.length()) add(reviewerArray.optString(i)) }
        val approvalsArray = json.optJSONArray("approvals") ?: JSONArray()
        val approvals = buildList {
            for (i in 0 until approvalsArray.length()) {
                val item = approvalsArray.getJSONObject(i)
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

    private fun List<UserProfile>.replaceUser(updated: UserProfile): List<UserProfile> {
        val found = any { it.email.equals(updated.email, true) }
        return (if (found) map { if (it.email.equals(updated.email, true)) updated else it } else this + updated)
            .sortedBy { it.displayName.lowercase() }
    }

    private fun List<DocumentRecord>.replaceDocument(updated: DocumentRecord): List<DocumentRecord> =
        map { if (it.id == updated.id) updated else it }.sortedByDescending { it.updatedAt }

    private fun buildManagedFileName(code: String, revision: String, original: String): String {
        val safeCode = safeToken(code).ifBlank { "PLANO" }
        val safeRevision = safeToken(revision).ifBlank { "0" }
        val safeOriginal = safeToken(original.substringBeforeLast('.')).ifBlank { "documento" }
        return "${safeCode}_REV-${safeRevision}_${safeOriginal}.pdf"
    }

    private fun safeToken(value: String): String = value
        .trim()
        .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .take(120)

    companion object {
        private const val PRIVATE_FOLDER_NAME = "GestionPlanosSKM-Privado"
        private const val PRIVATE_CONFIG_FILE = "claves-configuracion.json"
        private const val SYSTEM_FOLDER_NAME = "GestionPlanos-Sistema"
        private const val SHARED_INDEX_FILE = "control-documental.json"
        private const val USERS_FILE = "usuarios.json"
        private const val SETTINGS_FILE = "configuracion-flujo.json"
        private const val SPREADSHEET_NAME = "Control de Documentos SKM"
    }
}
