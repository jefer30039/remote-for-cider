package cider.remote.app.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Transición de zoom de iOS 18+ (`navigationTransition(.zoom)`), la de Apple Music al abrir un álbum
 * o una playlist: la página nace del rectángulo de la carátula tocada y crece hasta llenar la pantalla;
 * al volver se encoge hasta esa misma carátula. [rect] en coordenadas de la raíz; [corner] en px.
 */
@Immutable
data class ZoomFrom(val rect: Rect, val corner: Float, val screenCorner: Float, val anchor: ZoomAnchor? = null) {
    /**
     * Con la carátula donde está **ahora**: si la página de atrás se desplaza durante la transición, la
     * tarjeta la sigue en vez de quedarse flotando donde estaba al tocarla. Leído en un bloque de capa,
     * lo vuelve a ejecutar cuando la carátula se mueve.
     */
    fun live(): ZoomFrom {
        val now = anchor?.rectInRoot() ?: return this
        return if (now == rect) this else copy(rect = now)
    }
}

/**
 * La carátula de origen en la pantalla, viva mientras siga compuesta. [moved] cambia cuando se recoloca
 * (se escribe al colocar, antes de dibujar: quien lo lee se actualiza en el mismo fotograma).
 */
@Stable
class ZoomAnchor {
    internal var coordinates: LayoutCoordinates? = null
    internal var watched = false
    private val moved = mutableIntStateOf(0)

    /**
     * Su página está detrás de un zoom en curso. La tarjeta quita entonces la onda de pulsación: la del
     * sistema se anima ~0,5 s en el hilo de render y, al ensuciar la página en cada fotograma, obligaba a
     * rehacer las copias desenfocadas del fondo durante toda la apertura.
     */
    var flying by mutableStateOf(false)
        internal set

    /**
     * La página de atrás de la transición en curso y dónde cae la carátula dentro de ella (px enteros).
     * [local] solo cambia si la carátula se mueve **dentro** de su página: [moved] salta también en cada
     * fotograma en que la página cambia de escala, y con él la página se regrababa entera.
     */
    internal var page: LayoutCoordinates? = null
        set(value) { field = value; locate() }
    var local by mutableStateOf<Rect?>(null)
        private set

    private fun locate() {
        val c = coordinates?.takeIf { it.isAttached }
        val p = page?.takeIf { it.isAttached }
        val now = if (c == null || p == null) null else {
            val at = p.localPositionOf(c, Offset.Zero)
            Rect(at.x.roundToInt().toFloat(), at.y.roundToInt().toFloat(), (at.x + c.size.width).roundToInt().toFloat(), (at.y + c.size.height).roundToInt().toFloat())
        }
        // Con holgura: la posición pasa por la escala de la página y vuelve, y un valor al borde de medio
        // píxel no debe bailar entre dos enteros (cada cambio regraba la página).
        val old = local
        val same = now != null && old != null && kotlin.math.abs(now.left - old.left) <= 1f && kotlin.math.abs(now.top - old.top) <= 1f &&
            kotlin.math.abs(now.right - old.right) <= 1f && kotlin.math.abs(now.bottom - old.bottom) <= 1f
        if (!same && now != old) local = now
    }

    internal fun placed(c: LayoutCoordinates) {
        coordinates = c
        if (watched) { moved.intValue++; locate() }
    }

    private fun attached(): LayoutCoordinates? {
        moved.intValue
        return coordinates?.takeIf { it.isAttached }
    }

    /** En coordenadas de la raíz (incluye la escala de su página), o null si ya no está. */
    fun rectInRoot(): Rect? = attached()?.let { Rect(it.positionInRoot(), it.size.toSize()) }

}

/**
 * Última carátula tocada. Las tarjetas la apuntan al apoyar el dedo (sin consumir el toque) y
 * [PageStack.push] la recoge si la página se abre enseguida; así no hace falta pasar el rectángulo
 * por cada `onClick`.
 */
object ZoomSources {
    private var last: ZoomFrom? = null
    private var time = 0L

    fun record(from: ZoomFrom) {
        last = from
        time = SystemClock.uptimeMillis()
    }

    /** La carátula tocada hace menos de 1,5 s (un toque largo o un menú no cuentan), y la olvida. */
    fun take(): ZoomFrom? {
        val from = last?.takeIf { SystemClock.uptimeMillis() - time < 1500 }
        last = null
        return from
    }
}

/**
 * Origen del zoom de una tarjeta: [cover] va sobre la carátula (de ahí nace la página) y [tile] sobre todo
 * lo que se pulsa para abrirla (carátula y textos), para que tocar el título también abra con zoom.
 */
@Stable
class ZoomSource internal constructor(private val anchor: ZoomAnchor, private val radius: Float, private val screenCorner: Float) {
    val cover: Modifier = Modifier.onGloballyPositioned { anchor.placed(it) }

    /** La tarjeta es el origen de un zoom en curso (su página está detrás): sin onda de pulsación. */
    val flying: Boolean get() = anchor.flying

    val tile: Modifier = Modifier.pointerInput(anchor) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val c = anchor.coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
            anchor.watched = true
            ZoomSources.record(ZoomFrom(Rect(c.positionInRoot(), c.size.toSize()), radius, screenCorner, anchor))
        }
    }
}

/** Origen del zoom para una tarjeta cuya carátula tiene esquinas de [corner]. */
@Composable
fun rememberZoomSource(corner: Dp): ZoomSource {
    val anchor = remember { ZoomAnchor() }
    // Al salir de la lista (o reutilizarse para otro elemento) deja de ser el origen de nadie.
    DisposableEffect(anchor) { onDispose { anchor.coordinates = null; anchor.page = null } }
    val density = LocalDensity.current
    val radius = with(density) { corner.toPx() }
    // Radio de las esquinas de la pantalla (Android 12+); sin él, uno parecido al de los teléfonos actuales.
    val view = androidx.compose.ui.platform.LocalView.current
    val screenCorner = remember(view) {
        val fromSystem = if (android.os.Build.VERSION.SDK_INT >= 31) view.rootWindowInsets
            ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() else null
        fromSystem?.takeIf { it > 0f } ?: with(density) { 40.dp.toPx() }
    }
    return remember(anchor, radius, screenCorner) { ZoomSource(anchor, radius, screenCorner) }
}

/** Una tarjeta que es toda ella carátula: origen del zoom y zona de pulsación a la vez. */
fun Modifier.zoomSource(corner: Dp): Modifier = composed {
    val source = rememberZoomSource(corner)
    this.then(source.cover).then(source.tile)
}

/**
 * La página como tarjeta suelta bajo el dedo mientras se cierra arrastrando (iOS): la pantalla escalada
 * [scale] alrededor del punto agarrado [pivot], que se ha movido [shift]. Coordenadas de la raíz.
 */
@Immutable
data class ZoomPose(val scale: Float, val pivot: Offset, val shift: Offset) {
    fun rect(page: Size): Rect {
        val left = pivot.x + shift.x - pivot.x * scale
        val top = pivot.y + shift.y - pivot.y * scale
        return Rect(left, top, left + page.width * scale, top + page.height * scale)
    }

    companion object {
        /** Goma de iOS: avanza cada vez menos y nunca pasa de [limit]. */
        private fun rubber(x: Float, limit: Float) = (1f - 1f / (x / limit + 1f)) * limit

        /**
         * Arrastre hacia abajo (medidas de la transición de zoom de iOS): [down] y [side] son lo que se ha
         * movido el dedo, en px. La escala baja 0,67 por alto de pantalla recorrido y desde medio alto se
         * frena hacia 0,37; el punto agarrado baja con el dedo algo retrasado y de lado lo sigue a poco más
         * de la mitad, con goma.
         */
        fun drag(pivot: Offset, down: Float, side: Float, page: Size): ZoomPose {
            val d = (down / page.height).coerceAtLeast(0f)
            val knee = 0.52f
            val travel = if (d <= knee) d else knee + rubber(d - knee, (1f - 0.37f) / 0.67f - knee)
            val drop = (d - 0.45f * d * d * d).coerceAtLeast(0f) * page.height
            val limit = page.width * 0.9f
            val x = kotlin.math.sign(side) * rubber(kotlin.math.abs(side) * 0.56f, limit)
            return ZoomPose(1f - 0.67f * travel, pivot, Offset(x, drop))
        }

        /** Gesto de atrás desde un borde: sigue al dedo de lado 1:1 y en vertical a menos de la mitad. */
        fun edge(pivot: Offset, dx: Float, dy: Float, page: Size): ZoomPose =
            ZoomPose(1f - 0.67f * (kotlin.math.abs(dx) / page.width).coerceIn(0f, 1f), pivot, Offset(dx, dy * 0.43f))
    }
}

/**
 * Geometría del zoom con progreso [t] (0 = en la carátula, 1 = pantalla completa, o la tarjeta suelta
 * [pose] si se está arrastrando) para una página de tamaño `page`. Como en iOS: no es una escala uniforme
 * sino un rectángulo que va de la carátula a la pantalla, y la página se dibuja dentro escalada a su
 * ancho y anclada arriba; los bordes de arriba y abajo van un poco por detrás de los laterales (eso le
 * quita la rigidez de mover todo con el mismo reloj) y las esquinas se redondean a ritmo constante.
 */
internal object Zoom {
    /** Cuánto se retrasan arriba y abajo respecto a los lados, como fracción del recorrido (medido en iOS). */
    private const val OpenLag = 0.05f
    private const val CloseLag = 0.03f
    /** Fracción final del cierre que se da por recorrida (ver [shape]). */
    const val CloseCut = 0.015f
    /** Progreso con el que la página que se abre ya llena la pantalla (ver [shape] y PageStack.start). */
    const val OpenCut = 0.985f

    /**
     * Progreso de la tarjeta a partir del de la transición: llega a cada extremo un poco antes de que el
     * muelle acabe. La cola del muelle (el último 1,5 %, ~0,15 s) no se ve moverse, pero al cerrar dejaba
     * la carátula un pelín más grande que sus vecinas y al abrir retrasaba el final de la transición
     * (durante ese rato no se puede empezar a arrastrar para cerrar).
     */
    fun shape(raw: Float, opening: Boolean, landing: Boolean): Float = when {
        opening -> (raw / OpenCut).coerceAtMost(1f)
        !landing -> ((raw - CloseCut) / (1f - CloseCut)).coerceAtLeast(0f)
        else -> raw
    }
    /** Escala de la página de atrás con la de arriba a pantalla completa. */
    const val UnderScale = 0.914f

    fun card(from: ZoomFrom, page: Size, t: Float, opening: Boolean, pose: ZoomPose?, poseAmount: Float): Rect {
        val full = Rect(0f, 0f, page.width, page.height)
        val to = if (pose == null || poseAmount <= 0f) full else {
            val r = pose.rect(page)
            Rect(lerp(full.left, r.left, poseAmount), lerp(full.top, r.top, poseAmount), lerp(full.right, r.right, poseAmount), lerp(full.bottom, r.bottom, poseAmount))
        }
        val ty = t - (if (opening) OpenLag else CloseLag) * kotlin.math.sin(Math.PI.toFloat() * t.coerceIn(0f, 1f))
        return Rect(lerp(from.rect.left, to.left, t), lerp(from.rect.top, to.top, ty), lerp(from.rect.right, to.right, t), lerp(from.rect.bottom, to.bottom, ty))
    }

    fun corner(from: ZoomFrom, t: Float) = lerp(from.corner, from.screenCorner, t.coerceIn(0f, 1f))

    /**
     * Cuánto "está abierta" la página para lo que hay detrás (desenfoque, oscurecido, escala, botones):
     * el progreso, rebajado mientras se tira de la tarjeta hacia abajo (lo de atrás va volviendo).
     */
    fun backdrop(t: Float, pose: ZoomPose?, poseAmount: Float): Float {
        val open = t.coerceIn(0f, 1f)
        if (pose == null || poseAmount <= 0f) return open
        val pulled = (1f - (1f - pose.scale) / 0.67f * 1.1f).coerceIn(0.35f, 1f)
        return open * lerp(1f, pulled, poseAmount.coerceIn(0f, 1f))
    }
}

/** Recorte de la página que crece, en sus propias coordenadas (antes de escalarla): alto visible y radio. */
internal class ZoomClip(private val height: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = height.coerceIn(1f, size.height)
        return Outline.Rounded(RoundRect(Rect(0f, 0f, size.width, h), CornerRadius(radius.coerceIn(0f, minOf(size.width, h) / 2f))))
    }
    override fun equals(other: Any?) = other is ZoomClip && other.height == height && other.radius == radius
    override fun hashCode() = 31 * height.hashCode() + radius.hashCode()
}

/**
 * Fondo del zoom: la página de atrás desenfocada, **en caché**. Desenfocarla con un `BlurEffect` de radio
 * distinto en cada fotograma costaba decenas de pasadas de GPU por fotograma (el desenfoque se volvía a
 * calcular para la pantalla y para cada pieza de vidrio que la lee) y reservas de texturas en pleno
 * movimiento: la transición iba a 60 fps con tirones. Aquí la página se copia una vez a 1/4 de resolución
 * y se desenfoca a dos radios fijos ([near], [far]); cada copia queda en una capa fuera de pantalla que el
 * sistema solo rehace si la página cambia. El desenfoque progresivo es fundir la suave sobre la página y
 * la fuerte sobre la suave: por fotograma solo se componen texturas ya hechas.
 */
@Stable
internal class ZoomBackdrop(
    private val snap: GraphicsLayer,
    private val nearSource: GraphicsLayer, val near: GraphicsLayer,
    private val farSource: GraphicsLayer, val far: GraphicsLayer,
) {
    private var nearEffect: BlurEffect? = null
    private var farEffect: BlurEffect? = null
    private var radii = 0f

    /** Tamaño de la página dentro de las copias y margen que llevan alrededor, en px de la copia. */
    var inner by mutableStateOf(IntSize.Zero)
        private set
    var pad by mutableStateOf(IntOffset.Zero)
        private set

    /**
     * Graba las copias con el contenido que se está dibujando (se llama solo cuando la página se redibuja).
     * Llevan alrededor un margen del color de la página ([background]): la página de atrás se aleja y lo
     * que asoma por fuera es ese margen, con el desenfoque derramándose en él en vez de cortarse en el
     * borde de la página. [hole] es la carátula de origen, que no se dibuja (está volando).
     */
    fun ContentDrawScope.capture(nearRadius: Float, farRadius: Float, background: Color, hole: ZoomFrom?) {
        val content = IntSize((size.width * Scale).roundToInt().coerceAtLeast(1), (size.height * Scale).roundToInt().coerceAtLeast(1))
        val kx = content.width / size.width
        val ky = content.height / size.height
        val margin = IntOffset(kotlin.math.ceil(content.width * Margin).toInt(), kotlin.math.ceil(content.height * Margin).toInt())
        val small = IntSize(content.width + 2 * margin.x, content.height + 2 * margin.y)
        if (inner != content) inner = content
        if (pad != margin) pad = margin
        if (radii != nearRadius + farRadius * 1000f) {
            radii = nearRadius + farRadius * 1000f
            nearEffect = BlurEffect(nearRadius * kx, nearRadius * ky, TileMode.Clamp)
            farEffect = BlurEffect(farRadius * kx, farRadius * ky, TileMode.Clamp)
        }
        snap.record(small) {
            drawRect(background)
            translate(margin.x.toFloat(), margin.y.toFloat()) {
                scale(kx, ky, pivot = Offset.Zero) {
                    this@capture.drawContent()
                    if (hole != null) zoomHole(hole, background)
                }
            }
        }
        nearSource.renderEffect = nearEffect
        nearSource.record(small) { drawLayer(snap) }
        farSource.renderEffect = farEffect
        farSource.record(small) { drawLayer(snap) }
        // La capa de fuera guarda el resultado ya desenfocado como textura.
        near.compositingStrategy = CompositingStrategy.Offscreen
        near.record(small) { drawLayer(nearSource) }
        far.compositingStrategy = CompositingStrategy.Offscreen
        far.record(small) { drawLayer(farSource) }
    }

    companion object {
        const val Scale = 0.25f
        /** Margen de las copias por cada lado, como fracción de la página: cubre la pantalla con la página a [Zoom.UnderScale]. */
        const val Margin = 0.06f
    }
}

@Composable
internal fun rememberZoomBackdrop(): ZoomBackdrop {
    val a = rememberGraphicsLayer(); val b = rememberGraphicsLayer(); val c = rememberGraphicsLayer()
    val d = rememberGraphicsLayer(); val e = rememberGraphicsLayer()
    return remember { ZoomBackdrop(a, b, c, d, e) }
}

/** Sobre el contenido de la página de atrás: además de dibujarla, guarda sus copias desenfocadas. */
internal fun Modifier.zoomBackdropSource(backdrop: ZoomBackdrop, nearRadius: Dp, farRadius: Dp, background: Color, hole: ZoomFrom?): Modifier {
    return onPlaced { hole?.anchor?.page = it }.drawWithContent {
        drawContent()
        // La carátula de origen está volando (es la página que crece): su sitio queda vacío, como en iOS.
        // Donde esté ahora (la página puede desplazarse mientras tanto); si ya salió de la lista, sin hueco.
        val anchor = hole?.anchor
        val at = when {
            hole == null -> null
            anchor == null -> hole
            else -> anchor.local?.let { hole.copy(rect = it) }
        }
        if (at != null) zoomHole(at, background)
        with(backdrop) { capture(nearRadius.toPx(), farRadius.toPx(), background, at) }
    }
}

/** Tapa el sitio de la carátula de origen con el color de la página. */
private fun DrawScope.zoomHole(from: ZoomFrom, color: Color) =
    drawRoundRect(color, from.rect.topLeft, from.rect.size, CornerRadius(from.corner))

/** Una de las copias desenfocadas a pantalla completa, con la opacidad [alpha] leída al dibujar la capa. */
internal fun Modifier.zoomBackdropLevel(backdrop: ZoomBackdrop, far: Boolean, alpha: () -> Float): Modifier =
    graphicsLayer { this.alpha = alpha(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }
        .drawBehind {
            val inner = backdrop.inner
            val pad = backdrop.pad
            // La página de la copia sobre la página; el margen queda por fuera (la capa no recorta).
            if (inner.width > 0 && inner.height > 0) scale(size.width / inner.width, size.height / inner.height, pivot = Offset.Zero) {
                translate(-pad.x.toFloat(), -pad.y.toFloat()) { drawLayer(if (far) backdrop.far else backdrop.near) }
            }
        }
