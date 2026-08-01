package com.example.document.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll as foundationVerticalScroll
import androidx.compose.ui.Modifier

/** Permite mantener el visor aislado sin depender de imports repetidos. */
internal fun Modifier.verticalScroll(state: ScrollState): Modifier = this.foundationVerticalScroll(state)
