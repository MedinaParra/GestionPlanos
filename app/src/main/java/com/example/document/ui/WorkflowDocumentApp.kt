package com.example.document.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.document.model.DocumentRecord
import com.example.document.model.PlanComment
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserRole
import com.example.document.model.WorkflowEvent

/**
 * Punto de entrada conservado para no modificar la lógica de Activity/ViewModel.
 * Toda la experiencia visual se delega a la interfaz corporativa adaptable.
 */
@Composable
fun WorkflowDocumentApp(
    state: DocumentUiState,
    timeline: List<WorkflowEvent>,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPdf: (Uri, String, String, String) -> Unit,
    onOpenPdf: (DocumentRecord) -> Unit,
    onPrepareSignature: (DocumentRecord) -> Unit,
    onRequestSignature: (DocumentRecord, SignaturePlacement) -> Unit,
    onRequestChanges: (DocumentRecord, String) -> Unit,
    onConfigureDrive: (String, String) -> Unit,
    onSaveProfile: (String, String, String, SignaturePlacement, Uri?, ByteArray?) -> Unit,
    onUpdateUser: (String, UserRole, Boolean, Boolean) -> Unit,
    onUpdateSettings: (Int) -> Unit,
    onSignOut: () -> Unit,
    onClosePdf: () -> Unit,
    onCancelSignaturePlacement: () -> Unit,
    onClearFeedback: () -> Unit,
    onAddComment: (DocumentRecord, Int, String, Float, Float, Float) -> Unit,
    onPublishComment: (PlanComment) -> Unit,
    onUpdateComment: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit
) {
    CorporateDocumentApp(
        state = state,
        timeline = timeline,
        onConnectDrive = onConnectDrive,
        onRefresh = onRefresh,
        onUploadPdf = onUploadPdf,
        onOpenPdf = onOpenPdf,
        onPrepareSignature = onPrepareSignature,
        onRequestSignature = onRequestSignature,
        onRequestChanges = onRequestChanges,
        onConfigureDrive = onConfigureDrive,
        onSaveProfile = onSaveProfile,
        onUpdateUser = onUpdateUser,
        onUpdateSettings = onUpdateSettings,
        onSignOut = onSignOut,
        onClosePdf = onClosePdf,
        onCancelSignaturePlacement = onCancelSignaturePlacement,
        onClearFeedback = onClearFeedback,
        onAddComment = onAddComment,
        onPublishComment = onPublishComment,
        onUpdateComment = onUpdateComment,
        onDeleteComment = onDeleteComment
    )
}
