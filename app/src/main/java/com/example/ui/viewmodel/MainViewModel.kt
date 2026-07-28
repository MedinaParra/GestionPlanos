package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.drive.GoogleDriveSyncEngine
import com.example.data.excel.ExcelExporter
import com.example.data.local.entity.*
import com.example.data.repository.WorkOrderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WorkOrderRepository(application)

    // UI States
    val workOrders: StateFlow<List<WorkOrderEntity>> = repository.allWorkOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val approverUsers: StateFlow<List<ApproverUserEntity>> = repository.allApproverUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signatureLogs: StateFlow<List<SignatureLogEntity>> = repository.allSignatureLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminSettings: StateFlow<AdminSettingsEntity?> = repository.adminSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selected OT Detail
    private val _selectedOtId = MutableStateFlow<String?>(null)
    val selectedOtId: StateFlow<String?> = _selectedOtId.asStateFlow()

    val selectedWorkOrder: StateFlow<WorkOrderEntity?> = selectedOtId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getWorkOrder(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedBlueprints: StateFlow<List<BlueprintEntity>> = selectedOtId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getBlueprintsForOrder(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedLogs: StateFlow<List<SignatureLogEntity>> = selectedOtId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getLogsForOrder(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter state
    private val _selectedCategory = MutableStateFlow("TODOS") // "TODOS", "manto", "eje", "poleas", "sellos", "armado_taller", "ALERTAS"
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered Work Orders
    val filteredWorkOrders: StateFlow<List<WorkOrderEntity>> = combine(
        workOrders,
        selectedCategory,
        searchQuery
    ) { orders, category, query ->
        orders.filter { ot ->
            val matchesCategory = when (category) {
                "TODOS" -> true
                "ALERTAS" -> ot.isNearDeadline || ot.status == "PENDIENTE_FIRMA"
                else -> ot.category.lowercase() == category.lowercase()
            }
            val matchesQuery = query.isBlank() ||
                    ot.id.contains(query, ignoreCase = true) ||
                    ot.title.contains(query, ignoreCase = true) ||
                    ot.clientOrArea.contains(query, ignoreCase = true)

            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Google Drive User Status & User Mask Session
    private val _activeUserMask = MutableStateFlow<ApproverUserEntity?>(null)
    val activeUserMask: StateFlow<ApproverUserEntity?> = _activeUserMask.asStateFlow()

    private val _userMaskModalOpen = MutableStateFlow(false)
    val userMaskModalOpen: StateFlow<Boolean> = _userMaskModalOpen.asStateFlow()

    private val _googleDriveUser = MutableStateFlow(GoogleDriveSyncEngine.getInitialGoogleUser())
    val googleDriveUser: StateFlow<GoogleDriveSyncEngine.GoogleDriveUser> = _googleDriveUser.asStateFlow()

    private val _isSyncingDrive = MutableStateFlow(false)
    val isSyncingDrive: StateFlow<Boolean> = _isSyncingDrive.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // Biometric Modal State
    private val _biometricModalOpen = MutableStateFlow(false)
    val biometricModalOpen: StateFlow<Boolean> = _biometricModalOpen.asStateFlow()

    private val _activeBlueprintToSign = MutableStateFlow<BlueprintEntity?>(null)
    val activeBlueprintToSign: StateFlow<BlueprintEntity?> = _activeBlueprintToSign.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDatabaseIfEmpty()
        }
        viewModelScope.launch {
            approverUsers.collect { users ->
                if (_activeUserMask.value == null && users.isNotEmpty()) {
                    val firstPopulated = users.firstOrNull { it.name.isNotBlank() } ?: users.first()
                    setActiveUserMask(firstPopulated)
                }
            }
        }
    }

    fun clearAllWorkOrders() {
        viewModelScope.launch {
            repository.clearAllWorkOrders()
            _selectedOtId.value = null
        }
    }

    fun openUserMaskModal() {
        _userMaskModalOpen.value = true
    }

    fun closeUserMaskModal() {
        _userMaskModalOpen.value = false
    }

    fun setActiveUserMask(user: ApproverUserEntity) {
        _activeUserMask.value = user
        if (user.name.isNotBlank()) {
            _googleDriveUser.value = _googleDriveUser.value.copy(
                name = user.name,
                email = user.googleAccount.ifBlank { if (user.email.isNotBlank()) user.email else "exemdn@gmail.com" }
            )
        }
    }

    fun saveUserMask(
        id: String,
        name: String,
        roleTitle: String,
        rut: String,
        email: String,
        googleAccount: String
    ) {
        viewModelScope.launch {
            val initials = if (name.isNotBlank()) {
                name.split(" ").filter { it.isNotBlank() }.map { it.first() }.take(2).joinToString("").uppercase()
            } else "--"

            val updatedUser = ApproverUserEntity(
                id = id,
                name = name,
                roleTitle = roleTitle,
                email = email,
                googleAccount = googleAccount,
                rut = rut,
                avatarInitials = initials,
                biometricRegistered = true,
                isOnline = true
            )

            repository.saveApproverUser(updatedUser)
            setActiveUserMask(updatedUser)
            _userMaskModalOpen.value = false
        }
    }

    fun selectWorkOrder(otId: String) {
        _selectedOtId.value = otId
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openBiometricSigningDialog(blueprint: BlueprintEntity) {
        _activeBlueprintToSign.value = blueprint
        _biometricModalOpen.value = true
    }

    fun closeBiometricSigningDialog() {
        _biometricModalOpen.value = false
        _activeBlueprintToSign.value = null
    }

    fun executeBiometricSignature(
        approverName: String,
        approverRole: String,
        approverRut: String,
        biometricType: String,
        observations: String,
        onSuccess: (String) -> Unit
    ) {
        val blueprint = _activeBlueprintToSign.value ?: return
        viewModelScope.launch {
            val hash = repository.signBlueprintWithBiometric(
                workOrderId = blueprint.workOrderId,
                blueprintId = blueprint.id,
                approverName = approverName,
                approverRole = approverRole,
                approverRut = approverRut,
                biometricType = biometricType,
                notes = observations
            )
            _biometricModalOpen.value = false
            _activeBlueprintToSign.value = null
            onSuccess(hash)
        }
    }

    fun syncWithGoogleDrive() {
        viewModelScope.launch {
            _isSyncingDrive.value = true
            _syncMessage.value = null
            val baseFolder = adminSettings.value?.googleDriveBaseFolder ?: "Google Drive / Fabricaciones Manto OT"
            val result = GoogleDriveSyncEngine.syncDriveFoldersAndBlueprints(baseFolder)
            _isSyncingDrive.value = false
            _syncMessage.value = result.message
        }
    }

    fun toggleGoogleDriveAuth() {
        val current = _googleDriveUser.value
        _googleDriveUser.value = current.copy(isAuthenticated = !current.isAuthenticated)
    }

    fun createNewOtFromForm(
        otNumber: String,
        title: String,
        category: String,
        clientOrArea: String,
        deadlineDays: Int,
        pdfName: String
    ) {
        viewModelScope.launch {
            repository.addNewWorkOrder(
                otNumber = otNumber,
                title = title,
                category = category,
                clientOrArea = clientOrArea,
                deadlineDays = deadlineDays,
                pdfName = pdfName
            )
            _selectedOtId.value = otNumber
        }
    }

    fun updateAdminSettings(
        driveFolder: String,
        emails: String,
        autoSend: Boolean,
        deadlineHours: Int,
        biometricRequired: Boolean
    ) {
        viewModelScope.launch {
            repository.saveAdminSettings(
                driveFolder = driveFolder,
                emails = emails,
                autoSendEmail = autoSend,
                deadlineHours = deadlineHours,
                biometricRequired = biometricRequired
            )
        }
    }

    fun exportExcelReport(onFileReady: (java.io.File) -> Unit) {
        viewModelScope.launch {
            val orders = workOrders.value
            val approvers = approverUsers.value
            val logs = signatureLogs.value

            // Build blueprints map
            val blueprintsMap = mutableMapOf<String, List<BlueprintEntity>>()
            for (ot in orders) {
                val bps = repository.getBlueprintsForOrder(ot.id).firstOrNull() ?: emptyList()
                blueprintsMap[ot.id] = bps
            }

            val csvContent = ExcelExporter.generateCsvReport(orders, blueprintsMap, approvers, logs)
            val file = ExcelExporter.exportAndShareCsv(getApplication(), csvContent)
            onFileReady(file)
        }
    }
}
