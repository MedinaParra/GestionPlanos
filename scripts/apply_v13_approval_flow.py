from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: se esperaba 1 coincidencia y se encontraron {count}")
    return text.replace(old, new, 1)


# 1) El visor debe iniciar la ubicación de firma inmediatamente, sin confeti previo.
viewer_path = "app/src/main/java/com/example/document/ui/CorporatePdfViewerV7.kt"
viewer = read(viewer_path)
viewer = replace_once(
    viewer,
    "onApprove = { panel = V7Panel.NONE; celebrating = true },",
    "onApprove = { panel = V7Panel.NONE; onApprove() },",
    "Aprobación directa desde el visor",
)
viewer = viewer.replace(
    "La animación confirma la acción; luego continúa el flujo de firma habitual.",
    "Primero ubica tu firma y confirma con biometría. La celebración aparece solo después de guardar la aprobación.",
)
write(viewer_path, viewer)


# 2) Prioridad de pantallas: ubicación de firma > visor > panel principal.
workflow_app_path = "app/src/main/java/com/example/document/ui/WorkflowDocumentApp.kt"
workflow_app = '''package com.example.document.ui

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
'''
write(workflow_app_path, workflow_app)


# 3) Estado transitorio para celebrar solamente una aprobación ya guardada.
state_path = "app/src/main/java/com/example/document/ui/DocumentViewModel.kt"
state_text = read(state_path)
state_text = replace_once(
    state_text,
    "    val signingFile: File? = null,\n    val profilePhotoFile: File? = null,",
    "    val signingFile: File? = null,\n    val approvalCelebrationDocument: DocumentRecord? = null,\n    val profilePhotoFile: File? = null,",
    "Estado de celebración posterior",
)
write(state_path, state_text)


# 4) El ViewModel cierra el visor al preparar la firma y vuelve al inicio tras guardar.
vm_path = "app/src/main/java/com/example/document/ui/WorkflowViewModel.kt"
vm = read(vm_path)
vm = replace_once(
    vm,
    "            _uiState.update { it.copy(signingDocument = document, signingFile = target) }",
    """            _uiState.update {
                it.copy(
                    signingDocument = document,
                    signingFile = target,
                    previewDocument = null,
                    previewFile = null,
                    previewComments = emptyList(),
                    approvalCelebrationDocument = null
                )
            }
            _timeline.value = emptyList()""",
    "Transición automática a ubicación de firma",
)

signature_pattern = re.compile(
    r"    fun signAfterDeviceAuthentication\(\n.*?\n    fun requestChanges\(",
    re.DOTALL,
)
match = signature_pattern.search(vm)
if not match:
    raise RuntimeError("No se encontró signAfterDeviceAuthentication en WorkflowViewModel")
new_signature_block = '''    fun signAfterDeviceAuthentication(
        document: DocumentRecord,
        placement: SignaturePlacement,
        signatureMethod: String
    ) {
        launchBusy {
            val workspace = documentRepository.signDocument(
                user = requireSession(),
                configuration = _uiState.value.configuration,
                accessToken = requireDriveToken(),
                documentId = document.id,
                placement = placement,
                signatureMethod = signatureMethod
            )
            val updated = workspace.documents.firstOrNull { it.id == document.id } ?: document
            _uiState.update {
                it.copy(
                    signingDocument = null,
                    signingFile = null,
                    previewDocument = null,
                    previewFile = null,
                    previewComments = emptyList(),
                    approvalCelebrationDocument = updated
                )
            }
            _timeline.value = emptyList()
            applyWorkspace(
                workspace,
                if (updated.completed) {
                    "Revisión finalizada: PDF APTO PARA FABRICACIÓN creado."
                } else {
                    "Aprobación registrada. El turno pasó al siguiente revisor."
                }
            )
        }
    }

    fun clearApprovalCelebration() {
        _uiState.update { it.copy(approvalCelebrationDocument = null) }
    }

    fun requestChanges('''
vm = vm[:match.start()] + new_signature_block + vm[match.end():]
write(vm_path, vm)


# 5) Conectar el cierre de la celebración desde la Activity.
activity_path = "app/src/main/java/com/example/MainActivity.kt"
activity = read(activity_path)
activity = replace_once(
    activity,
    "                    onClearFeedback = viewModel::clearFeedback,\n                    onAddComment = viewModel::addComment,",
    "                    onClearFeedback = viewModel::clearFeedback,\n                    onApprovalCelebrationFinished = viewModel::clearApprovalCelebration,\n                    onAddComment = viewModel::addComment,",
    "Callback de fin de celebración",
)
write(activity_path, activity)


# 6) El timbre escrito en el PDF debe escalar sus fuentes junto con la barra de porcentaje.
pdf_path = "app/src/main/java/com/example/document/pdf/PdfStampService.kt"
pdf = read(pdf_path)
pdf = replace_once(
    pdf,
    "        val blockHeight = blockWidth * 0.48f\n        val x =",
    "        val blockHeight = blockWidth * 0.48f\n        val stampScale = (placement.width.coerceIn(0.18f, 0.46f) / 0.30f).coerceIn(0.60f, 1.55f)\n        val x =",
    "Factor de escala del timbre PDF",
)
pdf = replace_once(
    pdf,
    '''            val imageWidth = blockWidth * 0.42f
            val imageHeight = blockHeight * 0.42f
            stream.drawImage(image, x + 6f, y + blockHeight - imageHeight - 7f, imageWidth, imageHeight)

            stream.setNonStrokingColor(20, 55, 105)
            drawLine(stream, "FIRMADO / REVISADO", x + blockWidth * 0.46f, y + blockHeight - 15f, 8.5f, true)
            drawLine(stream, safe(profile.displayName), x + blockWidth * 0.46f, y + blockHeight - 29f, 7.2f, true)
            drawLine(stream, safe(profile.position), x + blockWidth * 0.46f, y + blockHeight - 41f, 6.5f, false)
            drawLine(stream, "RUT: ${safe(profile.rut)}", x + 6f, y + 24f, 6.5f, false)
            drawLine(stream, "Fecha: $date  Hora: $time", x + 6f, y + 12f, 6.5f, false)
            drawLine(stream, "SKM INDUSTRIAL", x + blockWidth * 0.62f, y + 4f, 6.2f, true)''',
    '''            val imageWidth = blockWidth * 0.42f
            val imageHeight = blockHeight * 0.42f
            stream.drawImage(
                image,
                x + 6f * stampScale,
                y + blockHeight - imageHeight - 7f * stampScale,
                imageWidth,
                imageHeight
            )

            stream.setNonStrokingColor(20, 55, 105)
            drawLine(stream, "FIRMADO / REVISADO", x + blockWidth * 0.46f, y + blockHeight - 15f * stampScale, 8.5f * stampScale, true)
            drawLine(stream, safe(profile.displayName), x + blockWidth * 0.46f, y + blockHeight - 29f * stampScale, 7.2f * stampScale, true)
            drawLine(stream, safe(profile.position), x + blockWidth * 0.46f, y + blockHeight - 41f * stampScale, 6.5f * stampScale, false)
            drawLine(stream, "RUT: ${safe(profile.rut)}", x + 6f * stampScale, y + 24f * stampScale, 6.5f * stampScale, false)
            drawLine(stream, "Fecha: $date  Hora: $time", x + 6f * stampScale, y + 12f * stampScale, 6.5f * stampScale, false)
            drawLine(stream, "SKM INDUSTRIAL", x + blockWidth * 0.62f, y + 4f * stampScale, 6.2f * stampScale, true)''',
    "Escala de firmas y fuentes en PDF",
)
write(pdf_path, pdf)


# 7) Nueva versión instalable sobre v12.
build_path = "app/build.gradle.kts"
build = read(build_path)
build = replace_once(build, "versionCode = 12", "versionCode = 13", "versionCode")
build = replace_once(build, 'versionName = "12.0.0"', 'versionName = "13.0.0"', "versionName")
write(build_path, build)

print("Flujo v13 aplicado correctamente")
