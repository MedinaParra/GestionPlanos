package com.example.document.model

enum class UserRole {
    ADMIN,
    EDITOR,
    VIEWER;

    companion object {
        fun from(value: String?): UserRole = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: VIEWER
    }
}

data class SessionUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val isCorporateGoogleUser: Boolean
) {
    val canEdit: Boolean get() = role == UserRole.ADMIN || role == UserRole.EDITOR
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}

data class DriveConfiguration(
    val folderId: String = "",
    val folderName: String = "",
    val spreadsheetId: String = "",
    val spreadsheetName: String = "Control de Documentos SKM",
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
) {
    val isConfigured: Boolean get() = folderId.isNotBlank()
}

data class DocumentRecord(
    val id: String = "",
    val code: String = "",
    val fileName: String = "",
    val revision: String = "A",
    val status: String = "PENDIENTE",
    val driveFileId: String = "",
    val driveWebViewLink: String = "",
    val previewStoragePath: String = "",
    val uploadedByUid: String = "",
    val uploadedByName: String = "",
    val uploadedAt: Long = 0L,
    val signed: Boolean = false,
    val signedByUid: String = "",
    val signedByName: String = "",
    val signedAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ViewerAccountResult(
    val username: String,
    val uid: String
)
