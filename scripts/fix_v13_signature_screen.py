from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/example/document/ui/ApprovalSignaturePlacementScreen.kt"
text = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: se esperaba 1 coincidencia y se encontraron {count}")
    text = text.replace(old, new, 1)


# Weight se resuelve como miembro de RowScope/ColumnScope; no debe importarse.
text = text.replace("import androidx.compose.foundation.layout.weight\n", "")

replace_once(
    """                    val maxX = (1f - width).coerceAtLeast(0f)
                    val maxY = (1f - stampHeightPx / pageHeightPx.coerceAtLeast(1f)).coerceAtLeast(0f)
                    x = x.coerceIn(0f, maxX)
                    y = y.coerceIn(0f, maxY)

                    Surface(
                        modifier = Modifier
                            .width(stampWidthDp)
                            .offset {
                                IntOffset(
                                    (pageLeftPx + x * pageWidthPx).roundToInt(),
                                    (pageTopPx + y * pageHeightPx).roundToInt()
                                )
                            }""",
    """                    val maxX = (1f - width).coerceAtLeast(0f)
                    val maxY = (1f - stampHeightPx / pageHeightPx.coerceAtLeast(1f)).coerceAtLeast(0f)
                    val safeX = x.coerceIn(0f, maxX)
                    val safeY = y.coerceIn(0f, maxY)

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(stampWidthDp)
                            .offset {
                                IntOffset(
                                    (pageLeftPx + safeX * pageWidthPx).roundToInt(),
                                    (pageTopPx + safeY * pageHeightPx).roundToInt()
                                )
                            }""",
    "Posición del timbre desde la esquina de la hoja",
)

PATH.write_text(text, encoding="utf-8")
print("Pantalla de ubicación de firma corregida")
