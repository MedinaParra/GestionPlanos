package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blueprints")
data class BlueprintEntity(
    @PrimaryKey val id: String, // e.g. "PLANO-OT101-01"
    val workOrderId: String,
    val fileName: String,
    val revision: String, // "Rev. B"
    val pdfPathOrUrl: String,
    val isSigned: Boolean = false,
    val status: String = "PENDIENTE", // "PENDIENTE", "APROBADO", "RECHAZADO"
    val signatureHash: String? = null,
    val signatureDate: String? = null,
    val signatureCanvasSvg: String? = null,
    val driveFileUrl: String = ""
)
