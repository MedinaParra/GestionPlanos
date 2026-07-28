package com.example.document.model

data class SessionUser(
    val permissionId: String = "",
    val email: String = "",
    val displayName: String = "",
    val canEdit: Boolean = false
)

data class DriveConfiguration(
    val privateFolderId: String = "",
    val configFileId: String = "",
    val folderId: String = "",
    val folderName: String = "",
    val spreadsheetId: String = "",
    val spreadsheetName: String = "Control de Documentos SKM",
    val indexFileId: String = "",
    val canEdit: Boolean = false,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
) {
    val isConfigured: Boolean get() = folderId.isNotBlank()
}

data class DocumentRecord(
    val id: String = "",
    val code: String = "",
    val originalFileName: String = "",
    val fileName: String = "",
    val revision: String = "A",
    val status: String = "PENDIENTE",
    val driveFileId: String = "",
    val driveWebViewLink: String = "",
    val uploadedByName: String = "",
    val uploadedByEmail: String = "",
    val uploadedAt: Long = 0L,
    val signed: Boolean = false,
    val signedByName: String = "",
    val signedByEmail: String = "",
    val signedAt: Long = 0L,
    val signatureMethod: String = "",
    val updatedAt: Long = 0L
)

data class DriveWorkspace(
    val session: SessionUser,
    val configuration: DriveConfiguration,
    val documents: List<DocumentRecord>
)
