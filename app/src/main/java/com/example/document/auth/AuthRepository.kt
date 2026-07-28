package com.example.document.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.document.model.SessionUser
import com.example.document.model.UserRole
import com.example.document.model.ViewerAccountResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val corporateDomain = BuildConfig.CORPORATE_DOMAIN.lowercase()
    private val viewerDomain = "viewer.skmindustrial.local"

    suspend fun restoreSession(): SessionUser? {
        val firebaseUser = auth.currentUser ?: return null
        val token = firebaseUser.getIdToken(true).await()
        var role = UserRole.from(token.claims["role"] as? String)
        val isGoogle = firebaseUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val email = firebaseUser.email.orEmpty().lowercase()

        if (isGoogle && email.endsWith("@$corporateDomain") && token.claims["role"] == null) {
            role = bootstrapCorporateUser(firebaseUser.uid, email, firebaseUser.displayName.orEmpty())
            firebaseUser.getIdToken(true).await()
        }

        return SessionUser(
            uid = firebaseUser.uid,
            email = firebaseUser.email.orEmpty(),
            displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: firebaseUser.email?.substringBefore('@').orEmpty(),
            role = role,
            isCorporateGoogleUser = isGoogle && email.endsWith("@$corporateDomain")
        )
    }

    suspend fun signInWithCorporateGoogle(activity: Activity): SessionUser {
        val clientId = resolveWebClientId(activity)
        val credentialManager = CredentialManager.create(activity)

        val credentialResult = try {
            credentialManager.getCredential(
                context = activity,
                request = googleRequest(clientId, filterAuthorized = true)
            )
        } catch (_: NoCredentialException) {
            credentialManager.getCredential(
                context = activity,
                request = googleRequest(clientId, filterAuthorized = false)
            )
        }

        val credential = credentialResult.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN
        ) { "Google no devolvió una credencial válida." }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val result = auth.signInWithCredential(firebaseCredential).await()
        val user = requireNotNull(result.user) { "No fue posible crear la sesión de Firebase." }
        val verifiedEmail = user.email.orEmpty().trim().lowercase()

        if (!verifiedEmail.endsWith("@$corporateDomain")) {
            auth.signOut()
            error("Solo se permiten cuentas corporativas @$corporateDomain.")
        }

        val role = bootstrapCorporateUser(
            uid = user.uid,
            email = verifiedEmail,
            displayName = user.displayName.orEmpty()
        )
        user.getIdToken(true).await()

        return SessionUser(
            uid = user.uid,
            email = verifiedEmail,
            displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: verifiedEmail.substringBefore('@'),
            role = role,
            isCorporateGoogleUser = true
        )
    }

    suspend fun signInViewer(username: String, password: String): SessionUser {
        val normalized = normalizeUsername(username)
        val result = auth.signInWithEmailAndPassword("$normalized@$viewerDomain", password).await()
        val user = requireNotNull(result.user) { "No fue posible iniciar la sesión de visualización." }
        val token = user.getIdToken(true).await()
        val role = UserRole.from(token.claims["role"] as? String)
        require(role == UserRole.VIEWER) { "La cuenta no tiene permisos de visualización." }

        return SessionUser(
            uid = user.uid,
            email = normalized,
            displayName = user.displayName?.takeIf { it.isNotBlank() } ?: normalized,
            role = UserRole.VIEWER,
            isCorporateGoogleUser = false
        )
    }

    suspend fun createViewerAccount(
        username: String,
        password: String,
        displayName: String
    ): ViewerAccountResult {
        val normalized = normalizeUsername(username)
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }

        val result = functions.getHttpsCallable("createViewerUser")
            .call(
                mapOf(
                    "username" to normalized,
                    "password" to password,
                    "displayName" to displayName.trim()
                )
            )
            .await()

        val data = result.data as? Map<*, *> ?: error("Respuesta inválida del servidor.")
        return ViewerAccountResult(
            username = data["username"]?.toString() ?: normalized,
            uid = data["uid"]?.toString().orEmpty()
        )
    }

    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private suspend fun bootstrapCorporateUser(
        uid: String,
        email: String,
        displayName: String
    ): UserRole {
        val response = functions.getHttpsCallable("bootstrapCorporateUser")
            .call(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName
                )
            )
            .await()
        val data = response.data as? Map<*, *> ?: error("Respuesta inválida del servidor.")
        return UserRole.from(data["role"]?.toString())
    }

    private fun googleRequest(clientId: String, filterAuthorized: Boolean): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setHostedDomainFilter(corporateDomain)
            .setFilterByAuthorizedAccounts(filterAuthorized)
            .setAutoSelectEnabled(false)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    private fun resolveWebClientId(activity: Activity): String {
        val id = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        require(id != 0) {
            "Falta google-services.json o el cliente OAuth web de Firebase."
        }
        return activity.getString(id)
    }

    private fun normalizeUsername(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9._-]{3,32}"))) {
            "El usuario debe tener 3 a 32 caracteres: letras, números, punto, guion o guion bajo."
        }
        return normalized
    }
}
