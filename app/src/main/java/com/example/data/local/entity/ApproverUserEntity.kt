package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "approver_users")
data class ApproverUserEntity(
    @PrimaryKey val id: String, // e.g. "USER-1"
    val name: String, // "Roberto Silva"
    val roleTitle: String, // "Jefe de Taller"
    val email: String, // "roberto.silva@fabricaciones.cl"
    val googleAccount: String, // "roberto.silva.google@gmail.com"
    val rut: String = "", // "12.345.678-9"
    val avatarInitials: String = "RS",
    val biometricRegistered: Boolean = true,
    val isOnline: Boolean = true
)
