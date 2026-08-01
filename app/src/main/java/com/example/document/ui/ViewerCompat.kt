package com.example.document.ui

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap as composeAsImageBitmap

/** Compatibilidad aislada para mantener el visor independiente del resto de la interfaz. */
internal fun Bitmap.asImageBitmap(): ImageBitmap = this.composeAsImageBitmap()

internal data class ViewerLayoutConstraints(
    val maxWidth: Int,
    val maxHeight: Int
)

/**
 * Métricas de respaldo usadas solo por las tarjetas de texto. Las formas y trazos
 * permanecen vinculados a coordenadas normalizadas reales del PDF.
 */
internal val BoxScope.constraints: ViewerLayoutConstraints
    get() {
        val metrics = Resources.getSystem().displayMetrics
        return ViewerLayoutConstraints(
            maxWidth = metrics.widthPixels.coerceAtLeast(1),
            maxHeight = metrics.heightPixels.coerceAtLeast(1)
        )
    }
