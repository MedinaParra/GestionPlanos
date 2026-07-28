package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_settings")
data class AdminSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val googleDriveBaseFolder: String = "Google Drive / Fabricaciones Manto OT",
    val notificationEmails: String = "aprobaciones@empresa.com, taller.jefe@empresa.com, control.calidad@empresa.com",
    val autoSendEmailOnApproval: Boolean = true,
    val deadlineReminderHours: Int = 48,
    val localWebFormPort: Int = 8080,
    val enableBiometricRequirement: Boolean = true
)
