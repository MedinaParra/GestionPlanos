package com.example.document.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.document.data.DocumentRepository
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.DriveWorkspace
import com.example.document.model.SessionUser
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserProfile
import com.example.document.model.UserRole
import com.example.document.model.WorkflowSettings
import com.example.document.notifications.ReviewReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


data class DocumentUiState(
    val session: SessionUser? = null,
    val configuration: DriveConfiguration = DriveConfiguration(),
    val documents: List<DocumentRecord> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val settings: WorkflowSettings = WorkflowSettings(),
    val driveConnected: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val previewDocument: DocumentRecord? = null,
    val previewFile: File? = null,
    val signingDocument: DocumentRecord? = null,
    val signingFile: File? = null,
    val profilePhotoFile: File? = null,
    val profileSignatureFile: File? = null
)

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val documentRepository = DocumentRepository(application)
    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()
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
            applyWorkspace(
                documentRepository.refresh(token, _uiState.value.configuration, session),
                "Información actualizada desde Drive."
            )
            loadProfileAssets()
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
            applyWorkspace(workspace, "Carpeta principal y estructura del flujo configuradas.")
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
            applyWorkspace(workspace, "Plano cargado con sello NO APTO PARA FABRICACIÓN.")
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
            _uiState.update { it.copy(signingDocument = document, signingFile = target) }
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
            _uiState.update { it.copy(signingDocument = null, signingFile = null) }
            applyWorkspace(
                workspace,
                workspace.documents.firstOrNull { it.id == document.id }?.let {
                    if (it.completed) "Revisión finalizada: PDF APTO PARA FABRICACIÓN creado."
                    else "Firma agregada. El turno pasó al siguiente revisor."
                } ?: "Firma registrada."
            )
        }
    }

    fun openPdf(document: DocumentRecord) {
        launchBusy {
            val safeName = document.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(getApplication<Application>().cacheDir, safeName)
            documentRepository.downloadPdf(
                requireDriveToken(),
                document.currentPdfFileId.ifBlank { document.driveFileId },
                target
            )
            _uiState.update { it.copy(previewDocument = document, previewFile = target) }
        }
    }

    fun closePdf() {
        _uiState.update { it.copy(previewDocument = null, previewFile = null) }
    }

    fun signOut() {
        driveAccessToken = null
        _uiState.value = DocumentUiState(message = "Sesión de Drive cerrada.")
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
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
        _uiState.update {
            it.copy(
                session = workspace.session,
                configuration = workspace.configuration,
                documents = workspace.documents,
                users = workspace.users,
                settings = workspace.settings,
                driveConnected = true,
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
