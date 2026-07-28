package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.drive.GoogleDriveSyncEngine
import com.example.data.local.AppDatabase
import com.example.data.local.dao.WorkOrderDao
import com.example.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WorkOrderRepository(private val context: Context) {

    private val dao: WorkOrderDao = AppDatabase.getDatabase(context).workOrderDao()

    val allWorkOrders: Flow<List<WorkOrderEntity>> = dao.getAllWorkOrders()
    val allApproverUsers: Flow<List<ApproverUserEntity>> = dao.getAllApproverUsers()
    val allSignatureLogs: Flow<List<SignatureLogEntity>> = dao.getAllSignatureLogs()
    val adminSettings: Flow<AdminSettingsEntity?> = dao.getAdminSettings()

    suspend fun initializeDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        // Clear all initial/mock work orders, blueprints, and signature logs
        dao.deleteAllWorkOrders()
        dao.deleteAllBlueprints()
        dao.deleteAllSignatureLogs()

        val existingUsers = dao.getAllApproverUsers().firstOrNull()
        if (existingUsers.isNullOrEmpty()) {
            val initialUsers = GoogleDriveSyncEngine.generateInitialApproverUsers()
            val defaultSettings = AdminSettingsEntity()

            dao.insertApproverUsers(initialUsers)
            dao.insertAdminSettings(defaultSettings)
        }
    }

    suspend fun clearAllWorkOrders() = withContext(Dispatchers.IO) {
        dao.deleteAllWorkOrders()
        dao.deleteAllBlueprints()
        dao.deleteAllSignatureLogs()
    }

    suspend fun saveApproverUser(user: ApproverUserEntity) = withContext(Dispatchers.IO) {
        dao.insertApproverUser(user)
    }

    fun getWorkOrder(id: String): Flow<WorkOrderEntity?> = dao.getWorkOrderById(id)

    fun getBlueprintsForOrder(workOrderId: String): Flow<List<BlueprintEntity>> =
        dao.getBlueprintsForWorkOrder(workOrderId)

    fun getLogsForOrder(workOrderId: String): Flow<List<SignatureLogEntity>> =
        dao.getSignatureLogsForWorkOrder(workOrderId)

    suspend fun signBlueprintWithBiometric(
        workOrderId: String,
        blueprintId: String,
        approverName: String,
        approverRole: String,
        approverRut: String,
        biometricType: String,
        notes: String
    ): String = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(timestamp))
        val hash = "BIO-${UUID.randomUUID().toString().take(8).uppercase()}"

        // 1. Create Signature Log
        val log = SignatureLogEntity(
            workOrderId = workOrderId,
            blueprintId = blueprintId,
            approverName = approverName,
            approverRole = approverRole,
            approverRut = approverRut,
            biometricType = biometricType,
            timestamp = timestamp,
            signatureHash = hash,
            status = "APROBADO",
            observations = notes
        )
        dao.insertSignatureLog(log)

        // 2. Update Blueprint
        val blueprint = dao.getBlueprintById(blueprintId)
        if (blueprint != null) {
            val updatedBp = blueprint.copy(
                isSigned = true,
                status = "APROBADO",
                signatureHash = hash,
                signatureDate = formattedDate
            )
            dao.updateBlueprint(updatedBp)
        }

        // 3. Update Work Order signed count
        val workOrder = dao.getWorkOrderByIdDirect(workOrderId)
        if (workOrder != null) {
            val newSignedCount = (workOrder.signedCount + 1).coerceAtMost(workOrder.totalApproversNeeded)
            val newStatus = if (newSignedCount >= workOrder.totalApproversNeeded) "APROBADO" else "PENDIENTE_FIRMA"
            val updatedWo = workOrder.copy(
                signedCount = newSignedCount,
                status = newStatus
            )
            dao.updateWorkOrder(updatedWo)

            // Trigger email if complete and auto-send is active
            val settings = dao.getAdminSettingsDirect()
            if (newStatus == "APROBADO" && settings?.autoSendEmailOnApproval == true) {
                dispatchApprovalNotificationEmail(context, updatedWo, settings.notificationEmails)
            }
        }

        return@withContext hash
    }

    suspend fun addNewWorkOrder(
        otNumber: String,
        title: String,
        category: String,
        clientOrArea: String,
        deadlineDays: Int,
        pdfName: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val deadlineMs = now + (deadlineDays * 24 * 60 * 60 * 1000L)
        val categoryDisplayName = when (category) {
            "manto" -> "Manto y Calderería"
            "eje" -> "Eje Mecanizado"
            "poleas" -> "Polea Completa"
            "sellos" -> "Sellos de Agua"
            else -> "Plano de Armado Taller"
        }

        val driveFolderUrl = GoogleDriveSyncEngine.buildDriveFolderUrlForOt(otNumber)
        val webViewerUrl = "https://ais-dev-i6k66jrgpazelwlaieh43t-424958906519.us-west1.run.app/viewer?ot=$otNumber"

        val wo = WorkOrderEntity(
            id = otNumber,
            title = title,
            category = category,
            categoryDisplayName = categoryDisplayName,
            clientOrArea = clientOrArea,
            driveFolderUrl = driveFolderUrl,
            webViewerUrl = webViewerUrl,
            status = "PENDIENTE_FIRMA",
            createdAt = now,
            deadlineTimestamp = deadlineMs,
            signedCount = 0,
            totalApproversNeeded = 6,
            isNearDeadline = deadlineDays <= 2
        )
        dao.insertWorkOrder(wo)

        // Insert blueprint for new OT
        val bp = BlueprintEntity(
            id = "PL-${otNumber.replace("-", "")}-01",
            workOrderId = otNumber,
            fileName = if (pdfName.isNotBlank()) pdfName else "PLANO-$otNumber-GENERAL.pdf",
            revision = "Rev. A",
            pdfPathOrUrl = "$driveFolderUrl/$pdfName",
            isSigned = false,
            status = "PENDIENTE"
        )
        dao.insertBlueprint(bp)
    }

    suspend fun saveAdminSettings(
        driveFolder: String,
        emails: String,
        autoSendEmail: Boolean,
        deadlineHours: Int,
        biometricRequired: Boolean
    ) = withContext(Dispatchers.IO) {
        val settings = AdminSettingsEntity(
            id = 1,
            googleDriveBaseFolder = driveFolder,
            notificationEmails = emails,
            autoSendEmailOnApproval = autoSendEmail,
            deadlineReminderHours = deadlineHours,
            enableBiometricRequirement = biometricRequired
        )
        dao.insertAdminSettings(settings)
    }

    private fun dispatchApprovalNotificationEmail(
        context: Context,
        workOrder: WorkOrderEntity,
        recipientsStr: String
    ) {
        try {
            val emails = recipientsStr.split(",").map { it.trim() }.filter { it.contains("@") }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                if (emails.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
                }
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "✅ [OT APROBADA EN DRIVE] ${workOrder.id} - ${workOrder.title}"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    """
                    Estimado equipo de Taller y Fabricación,

                    La Orden de Trabajo ${workOrder.id} ha completado exitosamente las 6 firmas digitales con validación biométrica requeridas.

                    DATOS DE LA ORDEN DE TRABAJO:
                    -------------------------------------------------
                    • Código OT: ${workOrder.id}
                    • Trabajo: ${workOrder.title}
                    • Categoría: ${workOrder.categoryDisplayName}
                    • Área Destino: ${workOrder.clientOrArea}
                    • Estado: APROBADO COMPLETADO
                    • Enlace Google Drive: ${workOrder.driveFolderUrl}
                    • Visor Web PC: ${workOrder.webViewerUrl}

                    El archivo firmado y certificado biométricamente está listo para su inicio inmediato de fabricación en taller.

                    Atentamente,
                    Sistema Automatizado FirmaPlanos OT
                    """.trimIndent()
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
