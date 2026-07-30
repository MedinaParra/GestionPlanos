package com.example.document.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.document.model.DocumentRecord
import com.example.document.model.PlanComment
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserRole
import com.example.document.model.WorkflowEvent

/**
 * Mantiene la interfaz corporativa y reemplaza por completo la pantalla principal
 * mientras existe un PDF abierto. Así el visor no se superpone sobre el menú ni
 * deja visibles barras de la aplicación por debajo.
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
    val document = state.previewDocument
    val file = state.previewFile
    val session = state.session

    if (document != null && file != null && session != null) {
        CorporatePdfViewerV7(
            file = file,
            document = document,
            comments = state.previewComments,
            timeline = timeline,
            currentEmail = session.email,
            isAdmin = session.isAdmin,
            canComment = state.configuration.canEdit && session.profile.active,
            onClose = onClosePdf,
            onApprove = { onPrepareSignature(document) },
            onRequestChanges = { reason -> onRequestChanges(document, reason) },
            onAddComment = { page, text, x, y, width ->
                onAddComment(document, page, text, x, y, width)
            },
            onPublishComment = onPublishComment,
            onUpdateComment = onUpdateComment,
            onDeleteComment = onDeleteComment
        )
        return
    }

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
