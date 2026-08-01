package com.example.document.model

enum class WorkflowEventType {
    UPLOADED,
    COMMENT_DRAFTED,
    COMMENT_PUBLISHED,
    APPROVED,
    CHANGES_REQUESTED,
    COMPLETED
}

data class WorkflowEvent(
    val id: String = "",
    val documentId: String = "",
    val type: WorkflowEventType = WorkflowEventType.UPLOADED,
    val actorName: String = "",
    val actorEmail: String = "",
    val detail: String = "",
    val createdAt: Long = 0L
)

val DocumentRecord.isUnderReview: Boolean
    get() = status == "EN_REVISIÓN"

val DocumentRecord.changesRequested: Boolean
    get() = status == "CAMBIOS_SOLICITADOS"

val DocumentRecord.workflowStatusLabel: String
    get() = when (status) {
        "EN_REVISIÓN" -> "En revisión"
        "CAMBIOS_SOLICITADOS" -> "Cambios solicitados"
        "APTO_PARA_FABRICACIÓN" -> "Apto para fabricación"
        else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

fun DocumentRecord.canRequestChangesBy(email: String): Boolean =
    isUnderReview && currentReviewerEmail.equals(email, ignoreCase = true)
