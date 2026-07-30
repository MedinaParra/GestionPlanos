package com.example.document.model

enum class ReviewMarkupType {
    TEXT,
    FREEHAND,
    HIGHLIGHT,
    LINE,
    ARROW,
    RECTANGLE,
    ELLIPSE,
    CLOUD
}

data class ReviewPoint(
    val x: Float = 0f,
    val y: Float = 0f
)

data class ReviewMarkupInput(
    val pageIndex: Int,
    val type: ReviewMarkupType,
    val text: String = "",
    val x: Float = 0.08f,
    val y: Float = 0.10f,
    val endX: Float = 0.30f,
    val endY: Float = 0.20f,
    val width: Float = 0.22f,
    val height: Float = 0.10f,
    val colorArgb: Int = 0xFFFF6A00.toInt(),
    val strokeWidth: Float = 0.004f,
    val opacity: Float = 1f,
    val points: List<ReviewPoint> = emptyList()
)

data class PlanComment(
    val id: String = "",
    val documentId: String = "",
    val pageIndex: Int = 0,
    val text: String = "",
    val x: Float = 0.08f,
    val y: Float = 0.10f,
    val width: Float = 0.36f,
    val height: Float = 0.10f,
    val endX: Float = 0.30f,
    val endY: Float = 0.20f,
    val markupType: ReviewMarkupType = ReviewMarkupType.TEXT,
    val colorArgb: Int = 0xFFFF6A00.toInt(),
    val strokeWidth: Float = 0.004f,
    val opacity: Float = 1f,
    val points: List<ReviewPoint> = emptyList(),
    val authorName: String = "",
    val authorEmail: String = "",
    val published: Boolean = false,
    val publishedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun canBeModifiedBy(email: String, isAdmin: Boolean): Boolean =
        isAdmin || authorEmail.equals(email, ignoreCase = true)

    fun isVisibleTo(email: String, isAdmin: Boolean): Boolean =
        published || isAdmin || authorEmail.equals(email, ignoreCase = true)

    val isTextObservation: Boolean
        get() = markupType == ReviewMarkupType.TEXT

    val displayLabel: String
        get() = when (markupType) {
            ReviewMarkupType.TEXT -> "Observación"
            ReviewMarkupType.FREEHAND -> "Trazo libre"
            ReviewMarkupType.HIGHLIGHT -> "Resaltado"
            ReviewMarkupType.LINE -> "Línea"
            ReviewMarkupType.ARROW -> "Flecha"
            ReviewMarkupType.RECTANGLE -> "Rectángulo"
            ReviewMarkupType.ELLIPSE -> "Elipse"
            ReviewMarkupType.CLOUD -> "Nube de revisión"
        }
}