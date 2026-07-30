package com.example.document.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform as composeWithTransform

/** Mantiene el visor V7 independiente de imports de DrawScope en cada archivo. */
internal inline fun DrawScope.withTransform(
    noinline transformBlock: DrawTransform.() -> Unit,
    noinline drawBlock: DrawScope.() -> Unit
) {
    composeWithTransform(transformBlock, drawBlock)
}
