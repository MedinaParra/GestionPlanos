package com.example.document.ui

import androidx.compose.runtime.Composable

/**
 * Package-local forwarding helper used by the document UI screens.
 * Keeping it here avoids coupling the screen file to Compose's saveable subpackage.
 */
@Composable
internal fun <T : Any> rememberSaveable(
    vararg inputs: Any?,
    init: () -> T
): T = androidx.compose.runtime.saveable.rememberSaveable(
    *inputs,
    init = init
)
