package androidx.compose.ui.input.pointer

/**
 * Compatibilidad de importación para versiones de Compose donde consume()
 * es miembro de PointerInputChange y no una función importable.
 * El miembro real siempre tiene prioridad sobre esta extensión.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun PointerInputChange.consume() = Unit
