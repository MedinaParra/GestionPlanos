package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signature_logs")
data class SignatureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workOrderId: String,
    val blueprintId: String,
    val approverName: String,
    val approverRole: String,
    val approverRut: String = "",
    val biometricType: String, // "HUELLA_BIOMETRICA", "FACIAL_ID", "PIN_SEGURIDAD"
    val timestamp: Long,
    val signatureHash: String,
    val status: String, // "APROBADO", "RECHAZADO"
    val observations: String = ""
)
