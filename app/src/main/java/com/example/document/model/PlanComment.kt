package com.example.document.model

data class PlanComment(
    val id: String = "",
    val documentId: String = "",
    val pageIndex: Int = 0,
    val text: String = "",
    val x: Float = 0.08f,
    val y: Float = 0.10f,
    val width: Float = 0.36f,
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
}
