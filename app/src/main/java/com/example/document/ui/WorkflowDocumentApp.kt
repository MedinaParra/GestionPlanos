package com.example.document.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.document.model.DocumentRecord
import com.example.document.model.PlanComment
import com.example.document.model.SignaturePlacement
import com.example.document.model.UserRole
import com.example.document.model.WorkflowEvent

/**
 * Orden de navegación del flujo:
 * panel -> visor -> ubicación de firma -> autenticación -> celebración -> panel.
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
    onApprovalCelebrationFinished: () -> Unit,
    onAddComment: (DocumentRecord, Int, String, Float, Float, Float) -> Unit,
    onPublishComment: (PlanComment) -> Unit,
    onUpdateComment: (PlanComment) -> Unit,
    onDeleteComment: (PlanComment) -> Unit
) {
    val session = state.session
    val signingDocument = state.signingDocument
    val signingFile = state.signingFile

    if (signingDocument != null && signingFile != null && session != null) {
        ApprovalSignaturePlacementScreen(
            file = signingFile,
            document = signingDocument,
            profile = session.profile,
            signatureFile = state.profileSignatureFile,
            busy = state.busy,
            onCancel = onCancelSignaturePlacement,
            onConfirm = { placement -> onRequestSignature(signingDocument, placement) }
        )
        return
    }

    val document = state.previewDocument
    val file = state.previewFile
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

    Box(Modifier.fillMaxSize()) {
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

        state.approvalCelebrationDocument?.let { approvedDocument ->
            ApprovalSuccessOverlay(
                document = approvedDocument,
                onFinished = onApprovalCelebrationFinished
            )
        }
    }
}
