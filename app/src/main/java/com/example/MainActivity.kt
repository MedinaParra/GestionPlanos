package com.example

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
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
import com.example.document.ui.DocumentApp
import com.example.document.ui.DocumentViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class MainActivity : FragmentActivity() {

    private val viewModel: DocumentViewModel by viewModels()
    private lateinit var authorizationClient: AuthorizationClient
    private var pendingSignatureDocument: DocumentRecord? = null

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
            viewModel.reportDriveAuthorizationError(
                IllegalStateException("La autorización de Google Drive fue cancelada.")
            )
            return@registerForActivityResult
        }

        runCatching {
            authorizationClient.getAuthorizationResultFromIntent(requireNotNull(activityResult.data))
        }.onSuccess(::deliverAuthorizationResult)
            .onFailure(viewModel::reportDriveAuthorizationError)
    }

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val document = pendingSignatureDocument
        pendingSignatureDocument = null
        if (result.resultCode == Activity.RESULT_OK && document != null) {
            viewModel.toggleSignedAfterDeviceAuthentication(
                document,
                "PIN, patrón o clave del teléfono"
            )
        } else {
            viewModel.reportActionError("La confirmación con el bloqueo del teléfono fue cancelada.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authorizationClient = Identity.getAuthorizationClient(this)

        setContent {
            MyApplicationTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                DocumentApp(
                    state = state,
                    onConnectDrive = ::requestDriveAuthorization,
                    onRefresh = viewModel::refreshDashboard,
                    onUploadPdf = viewModel::uploadPdf,
                    onOpenPdf = viewModel::openPdf,
                    onToggleSigned = ::requestDeviceAuthentication,
                    onUpdateRevision = viewModel::updateRevision,
                    onConfigureDrive = viewModel::configureDriveFolder,
                    onSignOut = viewModel::signOut,
                    onClosePdf = viewModel::closePdf,
                    onClearFeedback = viewModel::clearFeedback
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
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener(::deliverAuthorizationResult)
            .addOnFailureListener(viewModel::reportDriveAuthorizationError)
    }

    private fun deliverAuthorizationResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: run {
                    viewModel.reportDriveAuthorizationError(
                        IllegalStateException("Google no devolvió el diálogo de autorización.")
                    )
                    return
                }
            authorizationLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
            return
        }

        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) {
            viewModel.reportDriveAuthorizationError(
                IllegalStateException("Google no devolvió un token de acceso a Drive.")
            )
            return
        }
        viewModel.setDriveAccessToken(accessToken)
    }

    private fun requestDeviceAuthentication(document: DocumentRecord) {
        pendingSignatureDocument = document
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val status = BiometricManager.from(this).canAuthenticate(authenticators)
            if (status != BiometricManager.BIOMETRIC_SUCCESS) {
                pendingSignatureDocument = null
                viewModel.reportActionError(
                    "Configura una huella, rostro o PIN seguro en el teléfono antes de firmar."
                )
                return
            }
            showBiometricPrompt(document, authenticators, allowPinButton = false)
            return
        }

        val biometricStatus = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt(
                document,
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
                allowPinButton = true
            )
        } else {
            launchDeviceCredential(document)
        }
    }

    private fun showBiometricPrompt(
        document: DocumentRecord,
        authenticators: Int,
        allowPinButton: Boolean
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    pendingSignatureDocument = null
                    val method = if (
                        result.authenticationType ==
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
                    ) {
                        "PIN, patrón o clave del teléfono"
                    } else {
                        "Huella o biometría del teléfono"
                    }
                    viewModel.toggleSignedAfterDeviceAuthentication(document, method)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (allowPinButton && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        launchDeviceCredential(document)
                    } else {
                        pendingSignatureDocument = null
                        viewModel.reportActionError(errString.toString())
                    }
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (document.signed) "Quitar firma" else "Firmar plano")
            .setSubtitle("${document.code} · Revisión ${document.revision}")
            .setDescription(
                "Confirma tu identidad con la seguridad configurada en este teléfono."
            )
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(true)

        if (allowPinButton) {
            builder.setNegativeButtonText("Usar PIN del teléfono")
        }
        prompt.authenticate(builder.build())
    }

    @Suppress("DEPRECATION")
    private fun launchDeviceCredential(document: DocumentRecord) {
        pendingSignatureDocument = document
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            pendingSignatureDocument = null
            viewModel.reportActionError(
                "El teléfono no tiene PIN, patrón ni contraseña configurados."
            )
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            if (document.signed) "Quitar firma" else "Firmar plano",
            "Confirma el bloqueo del teléfono para continuar con ${document.code}."
        )
        if (intent == null) {
            pendingSignatureDocument = null
            viewModel.reportActionError("El teléfono no pudo abrir la confirmación de seguridad.")
            return
        }
        deviceCredentialLauncher.launch(intent)
    }

    companion object {
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    }
}
