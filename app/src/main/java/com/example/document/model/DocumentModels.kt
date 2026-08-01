package com.example.document.model

enum class UserRole {
    ADMIN,
    REVIEWER,
    USER;

    companion object {
        fun from(value: String?): UserRole = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: USER
    }
}

data class SignaturePlacement(
    val x: Float = 0.70f,
    val y: Float = 0.78f,
    val width: Float = 0.16f
)

data class UserProfile(
    val permissionId: String = "",
    val email: String = "",
    val displayName: String = "",
    val rut: String = "",
    val position: String = "",
    val photoFileId: String = "",
    val signatureFileId: String = "",
    val role: UserRole = UserRole.ADMIN,
    val active: Boolean = true,
    val requiredSigner: Boolean = false,
    val placement: SignaturePlacement = SignaturePlacement(),
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L
) {
    val profileComplete: Boolean
        get() = displayName.isNotBlank() && rut.isNotBlank() && position.isNotBlank() && signatureFileId.isNotBlank()
}

data class SessionUser(
    val permissionId: String = "",
    val email: String = "",
    val displayName: String = "",
    val canEdit: Boolean = false,
    val profile: UserProfile = UserProfile()
) {
    val isAdmin: Boolean get() = profile.role == UserRole.ADMIN
}

data class WorkflowSettings(
    val reviewDays: Int = 2,
    val morningHour: Int = 8,
    val afternoonHour: Int = 15,
    val escalationAfterHours: Int = 36
)

data class ApprovalSignature(
    val email: String = "",
    val name: String = "",
    val rut: String = "",
    val position: String = "",
    val signedAt: Long = 0L,
    val method: String = "",
    val signatureFileId: String = "",
    val signedPdfFileId: String = "",
    val placement: SignaturePlacement = SignaturePlacement()
)

data class DriveConfiguration(
    val privateFolderId: String = "",
    val configFileId: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val spreadsheetId: String = "",
    val spreadsheetName: String = "Control de Documentos SKM",
    val indexFileId: String = "",
    val usersFileId: String = "",
    val settingsFileId: String = "",
    val systemFolderId: String = "",
    val canEdit: Boolean = false,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
) {
    val isConfigured: Boolean get() = folderId.isNotBlank()
}

data class DocumentRecord(
    val id: String = "",
    val otNumber: String = "",
    val code: String = "",
    val originalFileName: String = "",
    val fileName: String = "",
    val revision: String = "0",
    val status: String = "EN_REVISIÓN",
    val driveFileId: String = "",
    val driveWebViewLink: String = "",
    val originalPdfFileId: String = "",
    val currentPdfFileId: String = "",
    val finalPdfFileId: String = "",
    val otFolderId: String = "",
    val revisionFolderId: String = "",
    val originalFolderId: String = "",
    val reviewFolderId: String = "",
    val signaturesFolderId: String = "",
    val finalFolderId: String = "",
    val uploadedByName: String = "",
    val uploadedByEmail: String = "",
    val uploadedAt: Long = 0L,
    val dueAt: Long = 0L,
    val requiredReviewerEmails: List<String> = emptyList(),
    val currentReviewerIndex: Int = 0,
    val approvals: List<ApprovalSignature> = emptyList(),
    val signed: Boolean = false,
    val signedByName: String = "",
    val signedByEmail: String = "",
    val signedAt: Long = 0L,
    val signatureMethod: String = "",
    val updatedAt: Long = 0L
) {
    val currentReviewerEmail: String
        get() = requiredReviewerEmails.getOrNull(currentReviewerIndex).orEmpty()
    val completed: Boolean
        get() = status == "APTO_PARA_FABRICACIÓN"
    fun canBeSignedBy(email: String): Boolean =
        status == "EN_REVISIÓN" && currentReviewerEmail.equals(email, ignoreCase = true)
}

data class DriveWorkspace(
    val session: SessionUser,
    val configuration: DriveConfiguration,
    val documents: List<DocumentRecord>,
    val users: List<UserProfile> = emptyList(),
    val settings: WorkflowSettings = WorkflowSettings()
)
