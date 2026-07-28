package com.example.document.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.document.auth.AuthRepository
import com.example.document.data.DocumentRepository
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
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
    val initialLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val previewDocument: DocumentRecord? = null,
    val previewFile: File? = null
)

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository()
    private val documentRepository = DocumentRepository()

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    private var driveAccessToken: String? = null

    init {
        viewModelScope.launch {
            runCatching { authRepository.restoreSession() }
                .onSuccess { session ->
                    _uiState.update { it.copy(session = session, initialLoading = false) }
                    if (session != null) refreshDashboard()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(initialLoading = false, error = error.userMessage())
                    }
                }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        launchBusy {
            val session = authRepository.signInWithCorporateGoogle(activity)
            _uiState.update { it.copy(session = session, message = "Sesión corporativa iniciada.") }
            refreshDashboard()
        }
    }

    fun signInViewer(username: String, password: String) {
        launchBusy {
            val session = authRepository.signInViewer(username, password)
            _uiState.update { it.copy(session = session, message = "Sesión de solo lectura iniciada.") }
            refreshDashboard()
        }
    }

    fun setDriveAccessToken(token: String) {
        driveAccessToken = token
        _uiState.update {
            it.copy(driveConnected = true, message = "Google Drive autorizado para esta sesión.")
        }
        refreshDashboard()
    }

    fun reportDriveAuthorizationError(error: Throwable) {
        _uiState.update { it.copy(error = error.userMessage(), driveConnected = false) }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            runCatching {
                val configuration = documentRepository.loadConfiguration()
                val documents = documentRepository.loadDocuments()
                configuration to documents
            }.onSuccess { (configuration, documents) ->
                _uiState.update {
                    it.copy(configuration = configuration, documents = documents)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.userMessage()) }
            }
        }
    }

    fun configureDriveFolder(folderInput: String, folderName: String) {
        launchBusy {
            val user = requireSession()
            val token = requireDriveToken()
            val configuration = documentRepository.configureDriveFolder(
                user = user,
                folderInput = folderInput,
                folderName = folderName,
                accessToken = token
            )
            _uiState.update {
                it.copy(
                    configuration = configuration,
                    message = "Carpeta y planilla de control configuradas."
                )
            }
            refreshDashboard()
        }
    }

    fun uploadPdf(uri: Uri, code: String, revision: String) {
        launchBusy {
            val user = requireSession()
            val token = requireDriveToken()
            val configuration = _uiState.value.configuration
            val resolver = getApplication<Application>().contentResolver
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                ?: "plano.pdf"
            require(fileName.lowercase().endsWith(".pdf")) { "Solo se pueden cargar archivos PDF." }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No fue posible leer el PDF seleccionado.")

            documentRepository.uploadDocument(
                user = user,
                configuration = configuration,
                accessToken = token,
                code = code,
                revision = revision,
                sourceFileName = fileName,
                pdfBytes = bytes
            )
            _uiState.update { it.copy(message = "Plano cargado y registrado en la planilla.") }
            refreshDashboard()
        }
    }

    fun updateRevision(document: DocumentRecord, newRevision: String) {
        launchBusy {
            documentRepository.updateRevision(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                document = document,
                newRevision = newRevision
            )
            _uiState.update { it.copy(message = "Revisión actualizada.") }
            refreshDashboard()
        }
    }

    fun toggleSigned(document: DocumentRecord) {
        launchBusy {
            documentRepository.markSigned(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                document = document,
                signed = !document.signed
            )
            _uiState.update {
                it.copy(message = if (document.signed) "Firma devuelta a pendiente." else "Documento marcado como firmado.")
            }
            refreshDashboard()
        }
    }

    fun openPdf(document: DocumentRecord) {
        launchBusy {
            val session = requireSession()
            val safeName = document.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val target = File(getApplication<Application>().cacheDir, safeName)
            val token = driveAccessToken

            if (session.canEdit && token != null && document.driveFileId.isNotBlank()) {
                runCatching {
                    documentRepository.downloadPdf(token, document.driveFileId, target)
                }.getOrElse {
                    documentRepository.downloadPreview(document.previewStoragePath, target)
                }
            } else {
                documentRepository.downloadPreview(document.previewStoragePath, target)
            }

            _uiState.update { it.copy(previewDocument = document, previewFile = target) }
        }
    }

    fun closePdf() {
        _uiState.update { it.copy(previewDocument = null, previewFile = null) }
    }

    fun createViewer(username: String, password: String, displayName: String) {
        launchBusy {
            require(requireSession().isAdmin) { "Solo el administrador puede crear visualizadores." }
            val result = authRepository.createViewerAccount(username, password, displayName)
            _uiState.update {
                it.copy(message = "Visualizador '${result.username}' creado correctamente.")
            }
        }
    }

    fun signOut(activity: Activity) {
        viewModelScope.launch {
            authRepository.signOut(activity)
            driveAccessToken = null
            _uiState.value = DocumentUiState(initialLoading = false)
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.userMessage()) }
                }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun requireSession(): SessionUser =
        _uiState.value.session ?: error("Debe iniciar sesión.")

    private fun requireDriveToken(): String =
        driveAccessToken ?: error("Conecte Google Drive antes de continuar.")

    private fun Throwable.userMessage(): String {
        val raw = message?.trim().orEmpty()
        return raw.ifBlank { "Ocurrió un error inesperado." }
    }
}
