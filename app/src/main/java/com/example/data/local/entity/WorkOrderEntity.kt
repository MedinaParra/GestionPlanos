package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_orders")
data class WorkOrderEntity(
    @PrimaryKey val id: String, // e.g. "OT-2026-001"
    val title: String, // e.g. "Fabricación Polea Motriz 500mm"
    val category: String, // "manto", "eje", "poleas", "sellos", "armado_taller"
    val categoryDisplayName: String, // "Manto y Calderería", "Eje Mecanizado", "Polea Completa", "Sellos de Agua", "Planos de Armado"
    val clientOrArea: String, // "Mantenimiento Planta Coloso"
    val driveFolderUrl: String,
    val webViewerUrl: String,
    val status: String, // "PENDIENTE_FIRMA", "APROBADO", "RECHAZADO", "EN_PROCESO"
    val createdAt: Long, // epoch ms
    val deadlineTimestamp: Long, // epoch ms
    val signedCount: Int = 0,
    val totalApproversNeeded: Int = 6,
    val pdfFileUrl: String = "",
    val isNearDeadline: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
