package com.example.document.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * El encabezado adaptativo se emite como hijo directo del Box raíz del visor.
 * En ese caso TopStart ya es la posición por defecto; esta compatibilidad evita
 * acoplar el componente interno al receptor BoxScope de la versión de Compose.
 */
internal fun Modifier.align(alignment: Alignment): Modifier = this
