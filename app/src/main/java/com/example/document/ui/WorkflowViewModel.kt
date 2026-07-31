package com.example.document.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.document.data.DocumentRepository
import com.example.document.data.WorkflowActionRepository
import com.example.document.data.WorkflowCommentRepository
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.DriveWorkspace
import com.example.document.model.PlanComment
import com.example.document.model.SessionUser
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.example.document.model.UserRole
import com.example.document.model.WorkflowEvent
import com.example.document.model.WorkflowEventType
import com.example.document.model.WorkflowSettings
import com.example.document.notifications.ReviewReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class WorkflowViewModel(application: Application) : AndroidViewModel(application) {
    private val documentRepository = DocumentRepository(application)
    private val commentRepository = WorkflowCommentRepository()
    private val actionRepository = WorkflowActionRepository()

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    private val _timeline = MutableStateFlow<List<WorkflowEvent>>(emptyList())
    val timeline: StateFlow<List<WorkflowEvent>> = _timeline.asStateFlow()

    private var driveAccessToken: String? = null

    fun setDriveAccessToken(token: String) {
        driveAccessToken = token
        launchBusy {
            val workspace = documentRepository.connect(token)
            applyWorkspace(workspace, "Google Drive conectado.")
            loadProfileAssets()
        }
    }

    fun reportDriveAuthorizationError(error: Throwable) {
        _uiState.update { it.copy(error = error.userMessage(), driveConnected = false, busy = false) }
    }

    fun reportActionError(message: String) {
        _uiState.update { it.copy(error = message, busy = false) }
    }

    fun refreshDashboard() {
        val token = driveAccessToken ?: return
        val session = _uiState.value.session ?: return
        launchBusy {
            val workspace = documentRepository.refresh(token, _uiState.value.configuration, session)
            applyWorkspace(workspace, "Información actualizada desde Drive.")
            loadProfileAssets()
            reloadPreviewData()
        }
    }

    fun configureDriveFolder(folderInput: String, folderName: String) {
        launchBusy {
            val workspace = documentRepository.configureDriveFolder(
                requireSession(),
                _uiState.value.configuration,
                folderInput,
                folderName,
                requireDriveToken()
            )
            applyWorkspace(workspace, "Carpeta principal y flujo configurados.")
        }
    }

    fun uploadPdf(uri: Uri, otNumber: String, code: String, revision: String) {
        launchBusy {
            val resolver = getApplication<Application>().contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: "plano.pdf"
            require(fileName.lowercase().endsWith(".pdf")) { "Solo se pueden cargar archivos PDF." }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No fue posible leer el PDF seleccionado.")
            require(bytes.size <= 40 * 1024 * 1024) { "El PDF supera el límite de 40 MB." }
            val workspace = documentRepository.uploadDocument(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                documents = _uiState.value.documents,
                users = _uiState.value.users,
                settings = _uiState.value.settings,
                accessToken = requireDriveToken(),
                otNumber = otNumber,
                code = code,
                revision = revision,
                sourceFileName = fileName,
                pdfBytes = bytes
            )
            applyWorkspace(workspace, "Plano cargado y enviado automáticamente a revisión.")
        }
    }

    fun saveOwnProfile(
        displayName: String,
        rut: String,
        position: String,
        placement: SignaturePlacement,
        photoUri: Uri?,
        signaturePng: ByteArray?
    ) {
        launchBusy {
            val resolver = getApplication<Application>().contentResolver
            val photoBytes = photoUri?.let { resolver.openInputStream(it)?.use { input -> input.readBytes() } }
            val photoMime = photoUri?.let { resolver.getType(it) }
            val workspace = documentRepository.updateOwnProfile(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                displayName = displayName,
                rut = rut,
                position = position,
                placement = placement,
                photoBytes = photoBytes,
                photoMime = photoMime,
                signaturePng = signaturePng
            )
            applyWorkspace(workspace, "Perfil y firma actualizados.")
            loadProfileAssets()
        }
    }

    fun updateUserByAdmin(
        targetEmail: String,
        role: UserRole,
        active: Boolean,
        requiredSigner: Boolean
    ) {
        launchBusy {
            val workspace = documentRepository.updateUserByAdmin(
                requireSession(),
                _uiState.value.configuration,
                requireDriveToken(),
                targetEmail,
                role,
                active,
                requiredSigner
            )
            applyWorkspace(workspace, "Permisos de usuario actualizados.")
        }
    }

    fun updateWorkflowSettings(reviewDays: Int) {
        launchBusy {
            val current = _uiState.value.settings.copy(reviewDays = reviewDays)
            val workspace = documentRepository.updateWorkflowSettings(
                requireSession(),
                _uiState.value.configuration,
                requireDriveToken(),
                current
            )
            applyWorkspace(workspace, "Plazo de revisión actualizado.")
        }
    }

    fun prepareSignature(document: DocumentRecord) {
        launchBusy {
            require(document.canBeSignedBy(requireSession().email)) {
                val current = document.currentReviewerEmail
                if (current.isBlank()) "El documento ya está finalizado." else "El turno corresponde a $current."
            }
            val target = File(getApplication<Application>().cacheDir, "sign_${document.id}.pdf")
            documentRepository.downloadPdf(
                requireDriveToken(),
                document.currentPdfFileId.ifBlank { document.driveFileId },
                target
            )
            _uiState.update {
                it.copy(
                    signingDocument = document,
                    signingFile = target,
                    previewDocument = null,
                    previewFile = null,
                    previewComments = emptyList(),
                    approvalCelebrationDocument = null
                )
            }
            _timeline.value = emptyList()
        }
    }

    fun cancelSignaturePlacement() {
        _uiState.update { it.copy(signingDocument = null, signingFile = null) }
    }

    fun signAfterDeviceAuthentication(
        document: DocumentRecord,
        placement: SignaturePlacement,
        signatureMethod: String
    ) {
        launchBusy {
            val workspace = documentRepository.signDocument(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                documentId = document.id,
                placement = placement,
                signatureMethod = signatureMethod
            )
            val updated = workspace.documents.firstOrNull { it.id == document.id } ?: document
            _uiState.update {
                it.copy(
                    signingDocument = null,
                    signingFile = null,
                    previewDocument = null,
                    previewFile = null,
                    previewComments = emptyList(),
                    approvalCelebrationDocument = updated
                )
            }
            _timeline.value = emptyList()
            applyWorkspace(
                workspace,
                if (updated.completed) {
                    "Revisión finalizada: PDF APTO PARA FABRICACIÓN creado."
                } else {
                    "Aprobación registrada. El turno pasó al siguiente revisor."
                }
            )
        }
    }

    fun clearApprovalCelebration() {
        _uiState.update { it.copy(approvalCelebrationDocument = null) }
    }

    fun requestChanges(document: DocumentRecord, reason: String) {
        launchBusy {
            val token = requireDriveToken()
            val session = requireSession()
            actionRepository.requestChanges(
                user = session,
                configuration = _uiState.value.configuration,
                accessToken = token,
                documentId = document.id,
                reason = reason
            )
            val workspace = documentRepository.refresh(token, _uiState.value.configuration, session)
            applyWorkspace(workspace, "Cambios solicitados. El administrador debe cargar una nueva revisión.")
            reloadPreviewData()
        }
    }

    fun openPdf(document: DocumentRecord) {
        launchBusy {
            val safeName = document.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(getApplication<Application>().cacheDir, safeName)
            val token = requireDriveToken()
            documentRepository.downloadPdf(
                token,
                document.currentPdfFileId.ifBlank { document.driveFileId },
                target
            )
            val comments = commentRepository.loadForDocument(
                requireSession(),
                token,
                _uiState.value.configuration,
                document.id
            )
            _uiState.update {
                it.copy(previewDocument = document, previewFile = target, previewComments = comments)
            }
            _timeline.value = buildTimeline(document, comments, token)
        }
    }

    fun addComment(
        document: DocumentRecord,
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        width: Float
    ) {
        launchBusy {
            val comments = commentRepository.addDraft(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                documentId = document.id,
                pageIndex = pageIndex,
                text = text,
                x = x,
                y = y,
                width = width
            )
            _uiState.update { it.copy(previewComments = comments, message = "Comentario guardado como borrador.") }
            refreshTimelineForPreview(comments)
        }
    }

    fun publishComment(comment: PlanComment) {
        launchBusy {
            val comments = commentRepository.publish(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                comment = comment
            )
            _uiState.update { it.copy(previewComments = comments, message = "Comentario publicado para todos los revisores.") }
            refreshTimelineForPreview(comments)
        }
    }

    fun updateComment(comment: PlanComment) {
        launchBusy {
            val comments = commentRepository.update(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                comment = comment
            )
            _uiState.update { it.copy(previewComments = comments, message = "Comentario actualizado.") }
            refreshTimelineForPreview(comments)
        }
    }

    fun deleteComment(comment: PlanComment) {
        launchBusy {
            val comments = commentRepository.delete(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                comment = comment
            )
            _uiState.update { it.copy(previewComments = comments, message = "Comentario eliminado.") }
            refreshTimelineForPreview(comments)
        }
    }

    fun closePdf() {
        _uiState.update {
            it.copy(previewDocument = null, previewFile = null, previewComments = emptyList())
        }
        _timeline.value = emptyList()
    }

    fun signOut() {
        driveAccessToken = null
        _uiState.value = DocumentUiState(message = "Sesión de Drive cerrada.")
        _timeline.value = emptyList()
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    private suspend fun reloadPreviewData() {
        val previewId = _uiState.value.previewDocument?.id ?: return
        val fresh = _uiState.value.documents.firstOrNull { it.id == previewId } ?: return
        val token = requireDriveToken()
        val comments = commentRepository.loadForDocument(
            requireSession(),
            token,
            _uiState.value.configuration,
            previewId
        )
        _uiState.update { it.copy(previewDocument = fresh, previewComments = comments) }
        _timeline.value = buildTimeline(fresh, comments, token)
    }

    private suspend fun refreshTimelineForPreview(comments: List<PlanComment>) {
        val document = _uiState.value.previewDocument ?: return
        _timeline.value = buildTimeline(document, comments, requireDriveToken())
    }

    private suspend fun buildTimeline(
        document: DocumentRecord,
        comments: List<PlanComment>,
        token: String
    ): List<WorkflowEvent> {
        val events = mutableListOf<WorkflowEvent>()
        events += WorkflowEvent(
            id = "upload-${document.id}",
            documentId = document.id,
            type = WorkflowEventType.UPLOADED,
            actorName = document.uploadedByName,
            actorEmail = document.uploadedByEmail,
            detail = "Rev ${document.revision} enviada a revisión",
            createdAt = document.uploadedAt
        )
        document.approvals.forEachIndexed { index, approval ->
            events += WorkflowEvent(
                id = "approval-${document.id}-$index",
                documentId = document.id,
                type = WorkflowEventType.APPROVED,
                actorName = approval.name,
                actorEmail = approval.email,
                detail = "Aprobó y firmó como ${approval.position}",
                createdAt = approval.signedAt
            )
        }
        comments.forEach { comment ->
            events += WorkflowEvent(
                id = "comment-${comment.id}",
                documentId = document.id,
                type = if (comment.published) WorkflowEventType.COMMENT_PUBLISHED else WorkflowEventType.COMMENT_DRAFTED,
                actorName = comment.authorName,
                actorEmail = comment.authorEmail,
                detail = if (comment.published) "Publicó una observación en la hoja ${comment.pageIndex + 1}" else "Guardó un borrador en la hoja ${comment.pageIndex + 1}",
                createdAt = if (comment.publishedAt > 0L) comment.publishedAt else comment.createdAt
            )
        }
        events += actionRepository.loadExtraEvents(token, _uiState.value.configuration, document.id)
        if (document.completed) {
            events += WorkflowEvent(
                id = "complete-${document.id}",
                documentId = document.id,
                type = WorkflowEventType.COMPLETED,
                actorName = document.signedByName,
                actorEmail = document.signedByEmail,
                detail = "Documento final APTO PARA FABRICACIÓN",
                createdAt = document.updatedAt
            )
        }
        return events.filter { it.createdAt > 0L }.distinctBy { it.id }.sortedBy { it.createdAt }
    }

    private suspend fun loadProfileAssets() {
        val session = _uiState.value.session ?: return
        val token = driveAccessToken ?: return
        val photo = session.profile.photoFileId.takeIf { it.isNotBlank() }?.let { id ->
            runCatching {
                documentRepository.downloadAsset(token, id, File(getApplication<Application>().cacheDir, "profile_${session.permissionId}.img"))
            }.getOrNull()
        }
        val signature = session.profile.signatureFileId.takeIf { it.isNotBlank() }?.let { id ->
            runCatching {
                documentRepository.downloadAsset(token, id, File(getApplication<Application>().cacheDir, "signature_${session.permissionId}.png"))
            }.getOrNull()
        }
        _uiState.update { it.copy(profilePhotoFile = photo, profileSignatureFile = signature) }
    }

    private fun applyWorkspace(workspace: DriveWorkspace, message: String) {
        _uiState.update { current ->
            val previewId = current.previewDocument?.id
            val freshPreview = previewId?.let { id -> workspace.documents.firstOrNull { it.id == id } }
            current.copy(
                session = workspace.session,
                configuration = workspace.configuration,
                documents = workspace.documents,
                users = workspace.users,
                settings = workspace.settings,
                driveConnected = true,
                previewDocument = freshPreview ?: current.previewDocument,
                message = message,
                error = null
            )
        }
        ReviewReminderWorker.updateCache(
            getApplication(),
            workspace.session.email,
            workspace.documents,
            workspace.settings
        )
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun requireSession(): SessionUser =
        _uiState.value.session ?: error("Conecta primero tu cuenta de Google Drive.")

    private fun requireDriveToken(): String =
        driveAccessToken ?: error("Conecta Google Drive antes de continuar.")

    private fun Throwable.userMessage(): String {
        val raw = message?.trim().orEmpty()
        return raw.ifBlank { "Ocurrió un error inesperado." }
    }
}
