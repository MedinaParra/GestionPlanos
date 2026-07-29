package com.example.document.data

import com.example.document.model.DocumentRecord

internal fun DocumentRecord.canRequestChangesBy(email: String): Boolean =
    status == "EN_REVISIÓN" && currentReviewerEmail.equals(email, ignoreCase = true)
