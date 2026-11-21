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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 * [cover] (solo con portada animada): para una caja cuadrada con la carátula fija, arriba del todo
 * dentro de [inner]; la lleva sobre lo que se ve de la pieza y la funde con el recorrido.
 */
@Stable
class SheetFly(val outer: Modifier, val inner: Modifier, val cover: Modifier? = null)

/** Coordenadas de la raíz de la hoja, dentro de su capa de traslación; las fija NowPlayingSheet. */
class SheetRoot { var coordinates: LayoutCoordinates? by mutableStateOf(null, referentialEqualityPolicy()) }

/** Un instante del vuelo: escala y esquina superior izquierda de lo que se ve (en la raíz) y franja visible de la capa (en sus unidades). */
private class Flight(val scale: Float, val left: Float, val top: Float, val cropTop: Float, val cropBottom: Float)

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

/**
 * Medido en las grabaciones (tarjeta cuadrada, "POPPER!"): el borde superior y el tamaño van con el
 * progreso de la hoja y el centro se adelanta en horizontal (1 − (1 − p)²: a mitad de recorrido ya
 * está casi centrada), así que la trayectoria es una curva. Con portada animada ([pinLeft], "Suzuki")
 * lo que vuela es la hoja del vídeo entera, pegada a la izquierda: el ancho se adelanta (completo al
 * 63 % del recorrido); va con las esquinas redondeadas ([corner], en pantalla).
 * [restScale]: escala de la carátula en reposo (se encoge al pausar).
 * [videoAspect] (alto / ancho del vídeo, que va arriba de la caja): la forma también vuela. La pieza
 * sale siendo la miniatura —un cuadrado con la carátula fija ([SheetFly.cover])— y se alarga poco a
 * poco: su borde inferior baja del de la miniatura al de la pantalla en la primera mitad del
 * recorrido mientras la carátula se funde con el vídeo (primer 30 %). Antes la caja entera (vídeo y
 * reflejo, 2,2 veces más alta que ancha) se escalaba al ancho de la miniatura y desde el primer
 * fotograma era una columna alta encima del miniplayer y de la barra. Qué cuadrado del vídeo coincide
 * con la carátula cambia con el álbum (el de arriba en "Un Verano Sin Ti", el central en "Short n'
 * Sweet" o "Heavy Serenade"); por eso se enseña la carátula y no un recorte fijo del vídeo, y la pieza
 * sale de ese cuadrado: [squareTop] es a qué altura del vídeo empieza (fracción del ancho; sin él, el
 * central), para que la carátula se funda con la misma imagen y no con otra desplazada.
 */
@Composable
fun rememberSheetFly(
    sheet: SheetState,
    root: SheetRoot,
    pinLeft: Boolean = false,
    corner: Dp? = null,
    restScale: () -> Float = { 1f },
    videoAspect: Float? = null,
    squareTop: Float? = null,
): SheetFly = remember(sheet, root, pinLeft, corner, videoAspect, squareTop) {
    // Estados: la capa se recalcula en cuanto se conocen (el primer fotograma se coloca antes de medirlos).
    var own: LayoutCoordinates? by mutableStateOf(null, referentialEqualityPolicy())
    val outer = Modifier.onGloballyPositioned { own = it }

    /** Sitio final de la caja en coordenadas de la hoja, o `null` si no está volando o aún no se conoce. */
    fun rest(): Rect? {
        val base = root.coordinates
        val me = own
        if (sheet.progress >= 1f || sheet.thumb == null || base == null || me == null || !base.isAttached || !me.isAttached) return null
        return base.localBoundingBoxOf(me, clipBounds = false).takeIf { it.width > 0f }
    }

    /** La capa mide lo que [rest] (la caja de [outer]); [margin]: cuánto sale la pieza por debajo de la pantalla (px). */
    fun flight(rest: Rect, k: Float, margin: Float): Flight? {
        val p = sheet.progress
        val thumb = sheet.thumb ?: return null
        val finalWidth = rest.width * k
        val finalLeft = rest.left + (rest.width - finalWidth) / 2f
        val finalTop = rest.top + rest.height * (1f - k) / 2f
        val grow = if (pinLeft) 1f - (1f - (p / 0.63f).coerceAtMost(1f)).pow(1.3f) else p
        val width = lerp(thumb.width, finalWidth, grow)
        val scale = width / rest.width
        // Pegada a la izquierda (portada animada): el borde izquierdo va al mismo ritmo que el ancho. Con el
        // izquierdo a ritmo constante y el ancho ya casi completo a mitad de recorrido, la portada se salía por
        // la derecha y luego volvía al centro.
        val left = if (pinLeft) lerp(thumb.left, finalLeft, grow)
        else lerp(thumb.center.x, finalLeft + finalWidth / 2f, 1f - (1f - p) * (1f - p)) - width / 2f
        val top = lerp(thumb.top, finalTop, p)
        if (videoAspect == null) return Flight(scale, left, top, 0f, rest.height)
        // Lo que se ve de la capa: arriba, del cuadrado del vídeo que coincide con la carátula a su borde; abajo, lo que quepa
        // hasta un borde que baja a ritmo constante del de la miniatura al de la pantalla (y un poco
        // más, para que las esquinas redondeadas de abajo salgan de ella).
        val cropTop = lerp(rest.width * (squareTop ?: ((videoAspect - 1f) / 2f)), 0f, smooth(p / FormEnd))
        val bottom = lerp(thumb.bottom, rest.bottom + margin, (p / FormEnd).coerceAtMost(1f))
        val cropBottom = (cropTop + (bottom - top).coerceAtLeast(thumb.height) / scale).coerceAtMost(rest.height)
        return Flight(scale, left, top, cropTop, cropBottom)
    }

    val inner = Modifier.graphicsLayer {
        val k = restScale()
        val p = sheet.progress
        transformOrigin = TransformOrigin(0f, 0f)
        val rest = rest()
        val f = rest?.let { flight(it, k, corner?.toPx() ?: 0f) }
        // Hoja a medio camino y aún sin medidas (su primer fotograma): mejor nada que la carátula en su sitio final.
        alpha = if (p < 1f && sheet.thumb != null && (root.coordinates == null || own == null)) 0f else 1f
        if (rest == null || f == null) {
            // En su sitio: solo la escala de reposo, desde el centro.
            scaleX = k
            scaleY = k
            translationX = size.width * (1f - k) / 2f
            translationY = size.height * (1f - k) / 2f
            clip = false
            shape = RectangleShape
        } else {
            scaleX = f.scale
            scaleY = f.scale
            translationX = f.left - rest.left
            // `rest` está en coordenadas de la hoja, que ahora mismo va desplazada `offsetPx` hacia abajo;
            // `f.top` es el borde superior de lo que se ve: la capa sube lo que se recorta por arriba.
            translationY = f.top - f.cropTop * f.scale - sheet.offsetPx - rest.top
            if (corner != null) {
                clip = true
                // El radio se da en pantalla; la capa va escalada.
                val radius = lerp(5.dp.toPx(), corner.toPx(), p) / f.scale
                shape = if (videoAspect != null) CropShape(f.cropTop, f.cropBottom, radius) else RoundedCornerShape(radius)
            }
        }
    }

    // Carátula fija sobre lo que se ve de la pieza: opaca al salir de la miniatura, se funde con el vídeo.
    // Solo propiedades de capa: medirla o colocarla con el progreso regrababa la capa de [inner] en cada
    // fotograma, y con ella el vídeo y sus dos desenfoques (120 veces por segundo en vez de 24).
    val cover = if (videoAspect == null) null else Modifier.graphicsLayer {
        val p = sheet.progress
        val f = if (p < CoverEnd) rest()?.let { flight(it, 1f, 0f) } else null
        compositingStrategy = CompositingStrategy.ModulateAlpha
        if (f == null) alpha = 0f
        else {
            alpha = 1f - smooth(p / CoverEnd)
            // La caja es cuadrada (ancho de la capa): crece desde arriba al centro hasta cubrir el alto que
            // se ve; lo que sobra a los lados lo recorta la capa de la pieza.
            val s = (f.cropBottom - f.cropTop) / size.height
            transformOrigin = TransformOrigin(0.5f, 0f)
            scaleX = s
            scaleY = s
            translationY = f.cropTop
        }
    }
    SheetFly(outer, inner, cover)
}

/** Con portada animada: fracción del recorrido en la que la pieza acaba de alargarse y en la que la carátula fija ya no se ve. */
private const val FormEnd = 0.5f
private const val CoverEnd = 0.3f

/** Rectángulo redondeado de [top] a [bottom] a todo el ancho de la capa: lo que se ve de la pieza que vuela. */
private class CropShape(private val top: Float, private val bottom: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, top, size.width, bottom.coerceAtLeast(top), CornerRadius(radius)))
}

/**
 * Lo que no es la carátula (fondo, título, controles) aparece fundiéndose con la hoja. Sin capa
 * fuera de pantalla: la opacidad se aplica a cada trazo (casi nada se solapa).
 */
fun Modifier.sheetFade(sheet: SheetState): Modifier = graphicsLayer {
    alpha = sheet.contentAlpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}
