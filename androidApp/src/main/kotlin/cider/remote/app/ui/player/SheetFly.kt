package cider.remote.app.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.pow

/**
 * Vuelo de la carátula al abrir y cerrar la hoja (Apple Music en Android): sale de la miniatura
 * del miniplayer y crece hasta su sitio en la hoja, y al cerrar vuelve. [outer] va en una caja
 * que ocupa el sitio final (solo mide) y [inner] en su contenido (la capa que se transforma):
 * separados porque las coordenadas de un nodo incluyen la transformación de su propia capa.
 */
@Stable
class SheetFly(val outer: Modifier, val inner: Modifier)

/** Coordenadas de la raíz de la hoja, dentro de su capa de traslación; las fija NowPlayingSheet. */
class SheetRoot { var coordinates: LayoutCoordinates? by mutableStateOf(null, referentialEqualityPolicy()) }

/**
 * Medido en las grabaciones (tarjeta cuadrada, "POPPER!"): el borde superior y el tamaño van con el
 * progreso de la hoja y el centro se adelanta en horizontal (1 − (1 − p)²: a mitad de recorrido ya
 * está casi centrada), así que la trayectoria es una curva. Con portada animada ([pinLeft], "Suzuki")
 * lo que vuela es la hoja del vídeo entera, pegada a la izquierda: el ancho se adelanta (completo al
 * 63 % del recorrido) y por eso su parte baja llega siempre al borde inferior de la pantalla; va
 * con las esquinas redondeadas ([corner], en pantalla).
 * [restScale]: escala de la carátula en reposo (se encoge al pausar).
 */
@Composable
fun rememberSheetFly(
    sheet: SheetState,
    root: SheetRoot,
    pinLeft: Boolean = false,
    corner: Dp? = null,
    restScale: () -> Float = { 1f },
): SheetFly = remember(sheet, root, pinLeft, corner) {
    // Estados: la capa se recalcula en cuanto se conocen (el primer fotograma se coloca antes de medirlos).
    var own: LayoutCoordinates? by mutableStateOf(null, referentialEqualityPolicy())
    val outer = Modifier.onGloballyPositioned { own = it }
    val inner = Modifier.graphicsLayer {
        val k = restScale()
        val p = sheet.progress
        val thumb = sheet.thumb
        val base = root.coordinates
        val me = own
        transformOrigin = TransformOrigin(0f, 0f)
        val flying = p < 1f && thumb != null && base != null && me != null && base.isAttached && me.isAttached
        val rest = if (flying) base!!.localBoundingBoxOf(me!!, clipBounds = false) else null
        // Hoja a medio camino y aún sin medidas (su primer fotograma): mejor nada que la carátula en su sitio final.
        alpha = if (p < 1f && thumb != null && (base == null || me == null)) 0f else 1f
        if (rest == null || rest.width <= 0f || thumb == null) {
            // En su sitio: solo la escala de reposo, desde el centro.
            scaleX = k
            scaleY = k
            translationX = size.width * (1f - k) / 2f
            translationY = size.height * (1f - k) / 2f
            clip = false
            shape = RectangleShape
        } else {
            val finalWidth = rest.width * k
            val finalLeft = rest.left + (rest.width - finalWidth) / 2f
            val finalTop = rest.top + rest.height * (1f - k) / 2f
            val grow = if (pinLeft) 1f - (1f - (p / 0.63f).coerceAtMost(1f)).pow(1.3f) else p
            val width = lerp(thumb.width, finalWidth, grow)
            val scale = width / rest.width
            val left = if (pinLeft) lerp(thumb.left, finalLeft, p)
            else lerp(thumb.center.x, finalLeft + finalWidth / 2f, 1f - (1f - p) * (1f - p)) - width / 2f
            val top = lerp(thumb.top, finalTop, p)
            scaleX = scale
            scaleY = scale
            translationX = left - rest.left
            // `rest` está en coordenadas de la hoja, que ahora mismo va desplazada `offsetPx` hacia abajo.
            translationY = top - sheet.offsetPx - rest.top
            if (corner != null) {
                clip = true
                // El radio se da en pantalla; la capa va escalada.
                shape = RoundedCornerShape(lerp(5.dp.toPx(), corner.toPx(), p) / scale)
            }
        }
    }
    SheetFly(outer, inner)
}

/**
 * Lo que no es la carátula (fondo, título, controles) aparece fundiéndose con la hoja. Sin capa
 * fuera de pantalla: la opacidad se aplica a cada trazo (casi nada se solapa).
 */
fun Modifier.sheetFade(sheet: SheetState): Modifier = graphicsLayer {
    alpha = sheet.contentAlpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
