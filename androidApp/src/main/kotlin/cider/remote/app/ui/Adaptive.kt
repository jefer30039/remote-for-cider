package cider.remote.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Tamaño de ventana y medidas que dependen de él (HIG layout.md › Adaptability:
 * la disposición sigue al espacio disponible, no al dispositivo).
 * Medidas de tablet tomadas de Apple Music en una Xiaomi Pad 7 Pro (1164 × 777 dp).
 */
@Immutable
data class WindowLayout(val width: Dp, val height: Dp) {
    /** Tablet o ventana ancha: barra superior, sin título grande en las pestañas. */
    val expanded: Boolean get() = width >= 600.dp
    val landscape: Boolean get() = width > height

    /** Margen lateral del contenido. */
    val margin: Dp get() = if (expanded) 34.dp else 20.dp
    /** Margen y separación de las cuadrículas de portadas. */
    val gridMargin: Dp get() = if (expanded) 34.dp else 16.dp
    val gridGap: Dp get() = if (expanded) 20.dp else 16.dp

    /** Columnas que caben con celdas de al menos [minCell]; en teléfono, [phone]. */
    fun columns(minCell: Dp, phone: Int): Int =
        if (!expanded) phone else max(phone, ((width - gridMargin * 2 + gridGap) / (minCell + gridGap)).toInt())
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(400.dp, 800.dp) }

/** Deja que un elemento de ancho completo ignore el margen horizontal de su cuadrícula. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val width = constraints.maxWidth + extra * 2
    val p = measurable.measure(Constraints(width, width, constraints.minHeight, constraints.maxHeight))
    layout(constraints.maxWidth, p.height) { p.place(-extra, 0) }
}
