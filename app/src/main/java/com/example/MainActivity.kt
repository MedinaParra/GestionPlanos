package com.example

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.document.model.DocumentRecord
import com.example.document.model.SignaturePlacement
import com.example.document.notifications.ReviewReminderWorker
import com.example.document.ui.WorkflowDocumentApp
import com.example.document.ui.WorkflowViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class MainActivity : FragmentActivity() {
    private val viewModel: WorkflowViewModel by viewModels()
    private lateinit var authorizationClient: AuthorizationClient
    private var pendingSignature: Pair<DocumentRecord, SignaturePlacement>? = null

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            viewModel.reportDriveAuthorizationError(IllegalStateException("La autorización de Google Drive fue cancelada."))
            return@registerForActivityResult
        }
        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(requireNotNull(result.data))
        }.onSuccess(::deliverAuthorizationResult)
            .onFailure(viewModel::reportDriveAuthorizationError)
    }

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingSignature
        pendingSignature = null
        if (result.resultCode == Activity.RESULT_OK && request != null) {
            viewModel.signAfterDeviceAuthentication(
                request.first,
                request.second,
                "PIN, patrón o clave del teléfono"
            )
        } else {
            viewModel.reportActionError("La confirmación con el bloqueo del teléfono fue cancelada.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authorizationClient = Identity.getAuthorizationClient(this)
        ReviewReminderWorker.schedule(this)
        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                val timeline = viewModel.timeline.collectAsStateWithLifecycle().value
                WorkflowDocumentApp(
                    state = state,
                    timeline = timeline,
                    onConnectDrive = ::requestDriveAuthorization,
                    onRefresh = viewModel::refreshDashboard,
                    onUploadPdf = viewModel::uploadPdf,
                    onOpenPdf = viewModel::openPdf,
                    onPrepareSignature = viewModel::prepareSignature,
                    onRequestSignature = ::requestDeviceAuthentication,
                    onRequestChanges = viewModel::requestChanges,
                    onConfigureDrive = viewModel::configureDriveFolder,
                    onSaveProfile = viewModel::saveOwnProfile,
                    onUpdateUser = viewModel::updateUserByAdmin,
                    onUpdateSettings = viewModel::updateWorkflowSettings,
                    onSignOut = viewModel::signOut,
                    onClosePdf = viewModel::closePdf,
                    onCancelSignaturePlacement = viewModel::cancelSignaturePlacement,
                    onClearFeedback = viewModel::clearFeedback,
                    onAddComment = viewModel::addComment,
                    onPublishComment = viewModel::publishComment,
                    onUpdateComment = viewModel::updateComment,
                    onDeleteComment = viewModel::deleteComment
                )
            }
        }
    }

    private fun requestDriveAuthorization() {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_SCOPE),
                    Scope(SHEETS_SCOPE)
                )
            )
            .filterByHostedDomain("skmindustrial.cl")
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener(::deliverAuthorizationResult)
            .addOnFailureListener(viewModel::reportDriveAuthorizationError)
    }

    private fun deliverAuthorizationResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: run {
                viewModel.reportDriveAuthorizationError(IllegalStateException("Google no devolvió el diálogo de autorización."))
                return
            }
            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            return
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            viewModel.reportDriveAuthorizationError(IllegalStateException("Google no devolvió un token de acceso a Drive."))
            return
        }
        viewModel.setDriveAccessToken(token)
    }

    private fun requestDeviceAuthentication(document: DocumentRecord, placement: SignaturePlacement) {
        pendingSignature = document to placement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                pendingSignature = null
                viewModel.reportActionError("Configura una huella, rostro o PIN seguro antes de firmar.")
                return
            }
            showBiometricPrompt(document, authenticators, false)
            return
        }
        val biometricStatus = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt(document, BiometricManager.Authenticators.BIOMETRIC_WEAK, true)
        } else {
            launchDeviceCredential(document)
        }
    }

    private fun showBiometricPrompt(document: DocumentRecord, authenticators: Int, allowPinButton: Boolean) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val request = pendingSignature
                    pendingSignature = null
                    if (request != null) {
                        val method = if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                            "PIN, patrón o clave del teléfono"
                        } else {
                            "Huella o biometría del teléfono"
                        }
                        viewModel.signAfterDeviceAuthentication(request.first, request.second, method)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (allowPinButton && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        launchDeviceCredential(document)
                    } else {
                        pendingSignature = null
                        viewModel.reportActionError(errString.toString())
                    }
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Aprobar y firmar plano")
            .setSubtitle("OT ${document.otNumber} · ${document.code} · Rev ${document.revision}")
            .setDescription("Confirma tu identidad para registrar la aprobación y aplicar tu timbre en todas las hojas.")
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(true)
        if (allowPinButton) builder.setNegativeButtonText("Usar PIN del teléfono")
        prompt.authenticate(builder.build())
    }

    @Suppress("DEPRECATION")
    private fun launchDeviceCredential(document: DocumentRecord) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            pendingSignature = null
            viewModel.reportActionError("El teléfono no tiene PIN, patrón ni contraseña configurados.")
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Aprobar y firmar plano",
            "Confirma el bloqueo del teléfono para continuar con ${document.code}."
        ) ?: run {
            pendingSignature = null
            viewModel.reportActionError("El teléfono no pudo abrir la confirmación de seguridad.")
            return
        }
        deviceCredentialLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    }
}
