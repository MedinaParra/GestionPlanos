package com.example.document.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap as composeAsImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/** Compatibilidad aislada para mantener el visor independiente del resto de la interfaz. */
internal fun Bitmap.asImageBitmap(): ImageBitmap = this.composeAsImageBitmap()

internal data class ViewerLayoutConstraints(
    val maxWidth: Int,
    val maxHeight: Int
)

/**
 * BoxScope no expone sus restricciones. El visor usa estas métricas únicamente para
 * posicionar tarjetas de texto; el lienzo gráfico usa coordenadas normalizadas reales.
 */
internal val BoxScope.constraints: ViewerLayoutConstraints
    @Composable get() {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        return with(density) {
            ViewerLayoutConstraints(
                maxWidth = configuration.screenWidthDp.dp.roundToPx(),
                maxHeight = configuration.screenHeightDp.dp.roundToPx()
            )
        }
    }
