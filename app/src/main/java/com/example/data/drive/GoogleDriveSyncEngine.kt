package com.example.data.drive

import com.example.data.local.entity.WorkOrderEntity
import com.example.data.local.entity.BlueprintEntity
import kotlinx.coroutines.delay
import java.util.UUID

object GoogleDriveSyncEngine {

    data class DriveSyncResult(
        val success: Boolean,
        val syncedOtCount: Int,
        val syncedBlueprintsCount: Int,
        val message: String
    )

    data class GoogleDriveUser(
        val email: String,
        val name: String,
        val isAuthenticated: Boolean,
        val driveSpaceUsedMb: Double = 142.5
    )

    fun getInitialGoogleUser(): GoogleDriveUser {
        return GoogleDriveUser(
            email = "exemdn@gmail.com",
            name = "Administrador de Operaciones",
            isAuthenticated = true
        )
    }

    suspend fun syncDriveFoldersAndBlueprints(baseFolder: String): DriveSyncResult {
        delay(1200) // Simulate network call to Google Drive API
        return DriveSyncResult(
            success = true,
            syncedOtCount = 5,
            syncedBlueprintsCount = 12,
            message = "Sincronizado exitosamente con $baseFolder"
        )
    }

    fun buildDriveFolderUrlForOt(otId: String): String {
        return "https://drive.google.com/drive/folders/1_${otId.replace("-", "_")}_FabricacionManto"
    }

    fun generateInitialMockWorkOrders(): List<WorkOrderEntity> {
        return emptyList()
    }

    fun generateInitialMockBlueprints(): List<BlueprintEntity> {
        return emptyList()
    }

    fun generateInitialApproverUsers(): List<com.example.data.local.entity.ApproverUserEntity> {
        return listOf(
            com.example.data.local.entity.ApproverUserEntity("USR-1", "", "Jefe de Taller Mecánico", "", "", "--", "JT", true, true),
            com.example.data.local.entity.ApproverUserEntity("USR-2", "", "Control de Calidad (QA/QC)", "", "", "--", "QA", true, true),
            com.example.data.local.entity.ApproverUserEntity("USR-3", "", "Ingeniero de Proyecto", "", "", "--", "IP", true, true),
            com.example.data.local.entity.ApproverUserEntity("USR-4", "", "Supervisor de Fabricación", "", "", "--", "SF", true, false),
            com.example.data.local.entity.ApproverUserEntity("USR-5", "", "Administrador de Operaciones", "", "", "--", "AO", true, true),
            com.example.data.local.entity.ApproverUserEntity("USR-6", "", "Auditor / Inspector Técnico Cliente", "", "", "--", "AI", true, true)
        )
    }
}
