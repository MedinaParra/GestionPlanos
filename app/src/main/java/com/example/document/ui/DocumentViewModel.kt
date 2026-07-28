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
    val driveConnected: Boolean = false,
    val busy: Boolean = false,
    val initialLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val previewDocument: DocumentRecord? = null,
    val previewFile: File? = null
)

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val documentRepository = DocumentRepository()

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    private var driveAccessToken: String? = null

    fun setDriveAccessToken(token: String) {
        driveAccessToken = token
        launchBusy {
            val workspace = documentRepository.connect(token)
            applyWorkspace(
                workspace,
                "Google Drive conectado. Se creó o recuperó tu carpeta privada de configuración."
            )
        }
    }

    fun reportDriveAuthorizationError(error: Throwable) {
        _uiState.update {
            it.copy(error = error.userMessage(), driveConnected = false, busy = false)
        }
    }

    fun reportActionError(message: String) {
        _uiState.update { it.copy(error = message, busy = false) }
    }

    fun refreshDashboard() {
        val token = driveAccessToken ?: return
        val session = _uiState.value.session ?: return
        launchBusy {
            val workspace = documentRepository.refresh(
                accessToken = token,
                current = _uiState.value.configuration,
                session = session
            )
            applyWorkspace(workspace, "Información actualizada desde Drive.")
        }
    }

    fun configureDriveFolder(folderInput: String, folderName: String) {
        launchBusy {
            val workspace = documentRepository.configureDriveFolder(
                user = requireSession(),
                current = _uiState.value.configuration,
                folderInput = folderInput,
                folderName = folderName,
                accessToken = requireDriveToken()
            )
            applyWorkspace(
                workspace,
                if (workspace.configuration.canEdit) {
                    "Carpeta configurada con permisos de lectura y escritura."
                } else {
                    "Carpeta configurada en modo de solo lectura."
                }
            )
        }
    }

    fun uploadPdf(uri: Uri, code: String, revision: String) {
        launchBusy {
            val resolver = getApplication<Application>().contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: "plano.pdf"
            require(fileName.lowercase().endsWith(".pdf")) { "Solo se pueden cargar archivos PDF." }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No fue posible leer el PDF seleccionado.")

            val workspace = documentRepository.uploadDocument(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                documents = _uiState.value.documents,
                accessToken = requireDriveToken(),
                code = code,
                revision = revision,
                sourceFileName = fileName,
                pdfBytes = bytes
            )
            applyWorkspace(workspace, "Plano cargado en Drive y registrado en el control documental.")
        }
    }

    fun updateRevision(document: DocumentRecord, newRevision: String) {
        launchBusy {
            val workspace = documentRepository.updateRevision(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                documents = _uiState.value.documents,
                accessToken = requireDriveToken(),
                document = document,
                newRevision = newRevision
            )
            applyWorkspace(workspace, "Revisión actualizada y PDF renombrado en Drive.")
        }
    }

    fun toggleSignedAfterDeviceAuthentication(document: DocumentRecord, signatureMethod: String) {
        launchBusy {
            val workspace = documentRepository.markSigned(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                documents = _uiState.value.documents,
                accessToken = requireDriveToken(),
                document = document,
                signed = !document.signed,
                signatureMethod = signatureMethod
            )
            applyWorkspace(
                workspace,
                if (document.signed) {
                    "Firma devuelta a pendiente después de validar el teléfono."
                } else {
                    "Documento marcado como firmado después de validar el teléfono."
                }
            )
        }
    }

    fun openPdf(document: DocumentRecord) {
        launchBusy {
            val safeName = document.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(getApplication<Application>().cacheDir, safeName)
            documentRepository.downloadPdf(
                accessToken = requireDriveToken(),
                driveFileId = document.driveFileId,
                target = target
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

    private fun applyWorkspace(workspace: DriveWorkspace, message: String) {
        _uiState.update {
            it.copy(
                session = workspace.session,
                configuration = workspace.configuration,
                documents = workspace.documents,
                driveConnected = true,
                message = message,
                error = null
            )
        }
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
