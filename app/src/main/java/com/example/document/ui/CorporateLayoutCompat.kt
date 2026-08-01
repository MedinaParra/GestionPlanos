package com.example.document.ui

import androidx.compose.foundation.layout.PaddingValues as ComposePaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compatibilidad con la versión de Compose usada por el proyecto.
 * El valor solo se utiliza al calcular el margen externo antes de entrar
 * al alcance de BoxWithConstraints; el contenido usa el ancho real.
 */
internal val maxWidth: Dp = 0.dp

/** Permite expresar margen vertical general con un fondo inferior adicional. */
internal fun PaddingValues(vertical: Dp, bottom: Dp): ComposePaddingValues =
    ComposePaddingValues(start = 0.dp, top = vertical, end = 0.dp, bottom = bottom)
