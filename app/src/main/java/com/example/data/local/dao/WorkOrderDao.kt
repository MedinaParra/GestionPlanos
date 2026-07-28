package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderDao {
    // Work Orders
    @Query("SELECT * FROM work_orders ORDER BY createdAt DESC")
    fun getAllWorkOrders(): Flow<List<WorkOrderEntity>>

    @Query("SELECT * FROM work_orders WHERE id = :id")
    fun getWorkOrderById(id: String): Flow<WorkOrderEntity?>

    @Query("SELECT * FROM work_orders WHERE id = :id")
    suspend fun getWorkOrderByIdDirect(id: String): WorkOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkOrder(workOrder: WorkOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkOrders(workOrders: List<WorkOrderEntity>)

    @Update
    suspend fun updateWorkOrder(workOrder: WorkOrderEntity)

    @Query("DELETE FROM work_orders WHERE id = :id")
    suspend fun deleteWorkOrderById(id: String)

    @Query("DELETE FROM work_orders")
    suspend fun deleteAllWorkOrders()

    @Query("DELETE FROM blueprints")
    suspend fun deleteAllBlueprints()

    @Query("DELETE FROM signature_logs")
    suspend fun deleteAllSignatureLogs()

    // Blueprints
    @Query("SELECT * FROM blueprints WHERE workOrderId = :workOrderId")
    fun getBlueprintsForWorkOrder(workOrderId: String): Flow<List<BlueprintEntity>>

    @Query("SELECT * FROM blueprints WHERE workOrderId = :workOrderId")
    suspend fun getBlueprintsForWorkOrderDirect(workOrderId: String): List<BlueprintEntity>

    @Query("SELECT * FROM blueprints WHERE id = :blueprintId")
    suspend fun getBlueprintById(blueprintId: String): BlueprintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprint(blueprint: BlueprintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprints(blueprints: List<BlueprintEntity>)

    @Update
    suspend fun updateBlueprint(blueprint: BlueprintEntity)

    // Approver Users
    @Query("SELECT * FROM approver_users")
    fun getAllApproverUsers(): Flow<List<ApproverUserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproverUser(user: ApproverUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproverUsers(users: List<ApproverUserEntity>)

    // Signature Logs
    @Query("SELECT * FROM signature_logs ORDER BY timestamp DESC")
    fun getAllSignatureLogs(): Flow<List<SignatureLogEntity>>

    @Query("SELECT * FROM signature_logs WHERE workOrderId = :workOrderId ORDER BY timestamp DESC")
    fun getSignatureLogsForWorkOrder(workOrderId: String): Flow<List<SignatureLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignatureLog(log: SignatureLogEntity)

    // Admin Settings
    @Query("SELECT * FROM admin_settings WHERE id = 1")
    fun getAdminSettings(): Flow<AdminSettingsEntity?>

    @Query("SELECT * FROM admin_settings WHERE id = 1")
    suspend fun getAdminSettingsDirect(): AdminSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminSettings(settings: AdminSettingsEntity)
}
