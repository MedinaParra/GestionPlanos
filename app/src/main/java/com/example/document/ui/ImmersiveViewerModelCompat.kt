package com.example.document.ui

import com.example.document.model.DocumentRecord

/** Estados mínimos que necesita el visor, mantenidos localmente para no acoplarlo al resto de la interfaz. */
internal val DocumentRecord.isUnderReview: Boolean
    get() = status == "EN_REVISIÓN"

internal val DocumentRecord.workflowStatusLabel: String
    get() = when (status) {
        "EN_REVISIÓN" -> "En revisión"
        "CAMBIOS_SOLICITADOS" -> "Cambios solicitados"
        "APTO_PARA_FABRICACIÓN" -> "Apto para fabricación"
        else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
