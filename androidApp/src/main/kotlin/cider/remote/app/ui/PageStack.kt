package cider.remote.app.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Una pantalla de la pila de una pestaña. [key] identifica su estado guardado (desplazamiento, color). */
@Immutable
data class PageEntry(val key: String, val route: Route, val index: Int) {
    val nested: Boolean get() = index > 0
}

/**
 * Transición entre dos páginas al estilo de UINavigationController (iOS): [over] entra
 * o sale por la derecha y [under] se desplaza un poco a la izquierda, oscurecida.
 * [progress] = 1 con [over] tapando del todo; 0 con [over] fuera de la pantalla.
 */
@Stable
class PageTransition(val over: PageEntry, val under: PageEntry, initial: Float, val zoom: ZoomFrom? = null, val opening: Boolean = false) {
    // Umbral fino: con el de serie (0,01) la animación se daba por acabada a un 1 % del final y los
    // últimos ~13 px se recorrían de un salto.
    val progress = Animatable(initial, visibilityThreshold = PageStack.Threshold)

    /** Zoom: tarjeta suelta bajo el dedo (cerrar arrastrando) y cuánto queda de ella (1 → 0 al cancelar). */
    var pose by mutableStateOf<ZoomPose?>(null)
    val poseAmount = Animatable(1f, visibilityThreshold = PageStack.Threshold)
    /** Tras soltar un gesto la tarjeta aterriza en la carátula con un muelle con algo de rebote (iOS). */
    internal var landing = false

    internal val spring get() = when {
        zoom == null -> PageStack.Spring
        landing -> PageStack.ZoomLanding
        else -> PageStack.ZoomSpring
    }

    /** Cuánto tapa la de arriba a la de debajo (0–1), para lo que depende de ello: oscurecido, botones, desenfoque. */
    fun cover(): Float = if (zoom != null) Zoom.backdrop(progress.value, pose, poseAmount.value) else progress.value.coerceIn(0f, 1f)
}

/**
 * Pilas de navegación de las pestañas con transiciones animadas. Las páginas que salen
 * de la pila olvidan su estado guardado; las que quedan debajo lo conservan (al volver,
 * la lista sigue donde estaba, como en iOS).
 */
@Stable
internal class PageStack(
    private val scope: CoroutineScope,
    private val stacks: Map<Tab, SnapshotStateList<Route>>,
    private val holder: SaveableStateHolder,
    private val tab: () -> Tab,
) {
    var transition by mutableStateOf<PageTransition?>(null)
        private set

    private var job: Job? = null
    private var pendingEnd: (() -> Unit)? = null
    private val colors = HashMap<String, MutableState<Color?>>()
    private val scrolls = HashMap<String, MutableFloatState>()
    /** Carátula de la que salió cada página abierta con zoom: al volver se encoge hasta ella. */
    private val zooms = HashMap<String, ZoomFrom>()

    private val stack get() = stacks.getValue(tab())

    fun entries(t: Tab = tab()): List<PageEntry> =
        stacks.getValue(t).mapIndexed { i, r -> PageEntry("${t.name}/$i/${r.hashCode()}", r, i) }

    fun top(t: Tab = tab()): PageEntry = entries(t).last()

    /** Color de fondo que publica la página (álbum, artista); propio de cada entrada. */
    fun colorOf(key: String): MutableState<Color?> = colors.getOrPut(key) { mutableStateOf(null) }

    /** Cuánto se ha desplazado cada página, para el borde superior (se conserva al volver). */
    fun scrolledOf(key: String): MutableFloatState = scrolls.getOrPut(key) { mutableFloatStateOf(0f) }

    fun push(route: Route) {
        // Doble toque sobre la misma tarjeta: una sola página.
        if (transition != null && stack.last() == route) return
        settle()
        val under = top()
        // Álbumes y playlists tocados en su carátula: zoom (Apple Music en iOS); lo demás, de lado.
        val zoom = ZoomSources.take()?.takeIf { route is Route.Collection }
        stack.add(route)
        val over = top()
        if (zoom != null) zooms[over.key] = zoom
        // La página nueva se compone fuera de la pantalla en los primeros fotogramas (cuesta
        // 50–80 ms la primera vez); si la animación arrancara ya, daría un salto.
        start(PageTransition(over, under, 0f, zoom, opening = true), target = 1f, waitFrames = 2) {}
    }

    fun pop() {
        settle()
        if (stack.size < 2) return
        val over = top()
        stack.removeAt(stack.lastIndex)
        val zoom = zooms[over.key]
        // Zoom: un fotograma de espera con la página aún tapándolo todo, para preparar el fondo desenfocado.
        start(PageTransition(over, top(), 1f, zoom), target = 0f, waitFrames = if (zoom != null) 1 else 0) { forget(listOf(over)) }
    }

    /** Vuelve a la raíz de la pestaña con una sola transición (la de arriba sobre la raíz). */
    fun popToRoot() {
        settle()
        if (stack.size < 2) return
        val removed = entries().drop(1)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        // Con una sola página encima, vuelve a su carátula; con varias, la de la raíz no es su origen.
        val zoom = if (removed.size == 1) zooms[removed.last().key] else null
        start(PageTransition(removed.last(), top(), 1f, zoom), target = 0f, waitFrames = if (zoom != null) 1 else 0) { forget(removed) }
    }

    /**
     * Gesto de atrás predictivo: la página sigue al dedo; al soltar termina de salir y,
     * si se cancela, vuelve a su sitio. Desde el borde izquierdo sigue al dedo 1:1, como
     * el deslizamiento de iOS; desde el derecho, el progreso que da el sistema.
     */
    suspend fun predictiveBack(events: Flow<BackEventCompat>, width: Float, height: Float = 0f) {
        settle()
        if (stack.size < 2) { events.collect {}; return }
        val list = entries()
        val t = PageTransition(list.last(), list[list.size - 2], 1f, zooms[list.last().key])
        transition = t
        var startX = Float.NaN
        var startY = Float.NaN
        try {
            events.collect { e ->
                if (startX.isNaN()) { startX = e.touchX; startY = e.touchY }
                if (t.zoom != null && width > 0f && height > 0f) {
                    // Zoom: la página se encoge bajo el dedo como una tarjeta suelta (iOS) y vuela a su
                    // carátula al soltar.
                    t.pose = ZoomPose.edge(Offset(startX, startY), e.touchX - startX, e.touchY - startY, Size(width, height))
                } else {
                    val fraction = if (e.swipeEdge == BackEventCompat.EDGE_LEFT && width > 0f) (e.touchX - startX) / width else e.progress
                    t.progress.snapTo(1f - fraction.coerceIn(0f, 1f))
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Cancelado: la página vuelve a taparlo todo.
            scope.launch {
                if (t.pose != null) t.poseAmount.animateTo(0f, ZoomCancel) else t.progress.animateTo(1f, t.spring)
                if (transition === t) transition = null
            }
            throw e
        }
        stack.removeAt(stack.lastIndex)
        t.landing = t.pose != null
        start(t, target = 0f, resetTransition = false) { forget(listOf(t.over)) }
    }

    private var drag: PageTransition? = null

    /** Si la página de arriba se abrió con zoom (se puede cerrar deslizando hacia abajo). */
    fun topZoomed(): Boolean = stack.size >= 2 && zooms.containsKey(top().key)

    /**
     * Deslizar hacia abajo en un álbum o playlist abierto con zoom (iOS): la página se encoge hacia su
     * carátula siguiendo al dedo ([zoomDragTo], progreso 1 → menos); al soltar, [zoomDragEnd] la cierra
     * o la devuelve a su sitio.
     */
    fun zoomDragStart(pivot: Offset): Boolean {
        if (transition != null || !topZoomed()) return false
        val list = entries()
        val t = PageTransition(list.last(), list[list.size - 2], 1f, zooms[list.last().key])
        t.pose = ZoomPose(1f, pivot, Offset.Zero)
        transition = t
        drag = t
        return true
    }

    val dragging: Boolean get() = drag != null

    /** Punto agarrado al empezar a arrastrar. */
    val dragPivot: Offset get() = drag?.pose?.pivot ?: Offset.Zero

    fun zoomDragTo(pose: ZoomPose) { drag?.pose = pose }

    /** Escala actual de la tarjeta que se arrastra (1 si no hay). */
    val dragScale: Float get() = drag?.pose?.scale ?: 1f

    fun zoomDragEnd(close: Boolean) {
        val t = drag ?: return
        drag = null
        if (close) {
            stack.removeAt(stack.lastIndex)
            t.landing = true
            start(t, target = 0f, resetTransition = false) { forget(listOf(t.over)) }
        } else scope.launch {
            t.poseAmount.animateTo(0f, ZoomCancel)
            if (transition === t) transition = null
        }
    }

    /** Termina ya la transición en curso (cambio de pestaña, navegación encadenada). */
    fun settle() {
        job?.cancel()
        job = null
        finish()
    }

    private fun start(t: PageTransition, target: Float, resetTransition: Boolean = true, waitFrames: Int = 0, onEnd: () -> Unit) {
        if (resetTransition) settle()
        transition = t
        pendingEnd = {
            if (transition === t) transition = null
            onEnd()
        }
        job = scope.launch {
            repeat(waitFrames) { androidx.compose.runtime.withFrameNanos { } }
            if (t.zoom != null && t.opening && target == 1f) {
                // Zoom al abrir: la página ya llena la pantalla en Zoom.OpenCut; la cola del muelle (~0,15 s)
                // solo retrasaba el final de la transición, y mientras dura no se puede arrastrar para cerrar.
                try {
                    t.progress.animateTo(target, t.spring) { if (value >= Zoom.OpenCut) throw ZoomSettled() }
                } catch (e: ZoomSettled) {
                    t.progress.snapTo(target)
                }
            } else t.progress.animateTo(target, t.spring)
            job = null
            finish()
        }
    }

    private fun finish() {
        val end = pendingEnd
        pendingEnd = null
        end?.invoke()
    }

    private fun forget(entries: List<PageEntry>) {
        for (e in entries) {
            holder.removeState(e.key)
            colors.remove(e.key)
            scrolls.remove(e.key)
            zooms.remove(e.key)
        }
    }

    companion object {
        /** Muelle sin rebote de ~0,45 s, como el push de UINavigationController. */
        val Spring = spring<Float>(dampingRatio = 1f, stiffness = 220f, visibilityThreshold = Threshold)

        /** Umbral para dar por terminada una transición: una fracción del recorrido que no se ve. */
        const val Threshold = 0.0005f

        /**
         * Zoom, con los muelles medidos en la transición de zoom de iOS (ω en rad/s → rigidez ω²):
         * abrir y cerrar con el botón, sin rebote (ω 19); aterrizar en la carátula tras soltar un gesto,
         * con un poco de rebote (ω 15, amortiguación 0,75: pasa ~3 % de largo y vuelve); cancelar el
         * gesto, rápido y casi sin rebote (ω 22, 0,9).
         */
        val ZoomSpring = spring<Float>(dampingRatio = 1f, stiffness = 361f, visibilityThreshold = Threshold)
        val ZoomLanding = spring<Float>(dampingRatio = 0.75f, stiffness = 225f, visibilityThreshold = Threshold)
        val ZoomCancel = spring<Float>(dampingRatio = 0.9f, stiffness = 484f, visibilityThreshold = Threshold)

        /** Cuánto se desplaza la página de debajo (fracción del ancho) y cuánto se oscurece. */
        const val UnderShift = 0.3f
        const val UnderDim = 0.12f
    }
}

/** Corta la animación de un zoom que ya llegó a la vista (ver PageStack.start). */
private class ZoomSettled : kotlin.coroutines.cancellation.CancellationException("zoom")
