package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.document.ui.DocumentApp
import com.example.document.ui.DocumentViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentViewModel by viewModels()
    private lateinit var authorizationClient: AuthorizationClient

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authorizationClient = Identity.getAuthorizationClient(this)

        setContent {
            MyApplicationTheme {
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                DocumentApp(
                    state = state,
                    onGoogleSignIn = { viewModel.signInWithGoogle(this) },
                    onViewerSignIn = viewModel::signInViewer,
                    onConnectDrive = ::requestDriveAuthorization,
                    onRefresh = viewModel::refreshDashboard,
                    onUploadPdf = viewModel::uploadPdf,
                    onOpenPdf = viewModel::openPdf,
                    onToggleSigned = viewModel::toggleSigned,
                    onUpdateRevision = viewModel::updateRevision,
                    onConfigureDrive = viewModel::configureDriveFolder,
                    onCreateViewer = viewModel::createViewer,
                    onSignOut = { viewModel.signOut(this) },
                    onClosePdf = viewModel::closePdf,
                    onClearFeedback = viewModel::clearFeedback
                )
            }
        }
    }

    private fun requestDriveAuthorization() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_SCOPE),
                    Scope(SHEETS_SCOPE)
                )
            )
            .filterByHostedDomain(BuildConfig.CORPORATE_DOMAIN)
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

    companion object {
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    }
}
