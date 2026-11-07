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
class PageTransition(val over: PageEntry, val under: PageEntry, initial: Float) {
    val progress = Animatable(initial)
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
        stack.add(route)
        // La página nueva se compone fuera de la pantalla en los primeros fotogramas (cuesta
        // 50–80 ms la primera vez); si la animación arrancara ya, daría un salto.
        start(PageTransition(top(), under, 0f), target = 1f, waitFrames = 2) {}
    }

    fun pop() {
        settle()
        if (stack.size < 2) return
        val over = top()
        stack.removeAt(stack.lastIndex)
        start(PageTransition(over, top(), 1f), target = 0f) { forget(listOf(over)) }
    }

    /** Vuelve a la raíz de la pestaña con una sola transición (la de arriba sobre la raíz). */
    fun popToRoot() {
        settle()
        if (stack.size < 2) return
        val removed = entries().drop(1)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        start(PageTransition(removed.last(), top(), 1f), target = 0f) { forget(removed) }
    }

    /**
     * Gesto de atrás predictivo: la página sigue al dedo; al soltar termina de salir y,
     * si se cancela, vuelve a su sitio. Desde el borde izquierdo sigue al dedo 1:1, como
     * el deslizamiento de iOS; desde el derecho, el progreso que da el sistema.
     */
    suspend fun predictiveBack(events: Flow<BackEventCompat>, width: Float) {
        settle()
        if (stack.size < 2) { events.collect {}; return }
        val list = entries()
        val t = PageTransition(list.last(), list[list.size - 2], 1f)
        transition = t
        var startX = Float.NaN
        try {
            events.collect { e ->
                val fraction = if (e.swipeEdge == BackEventCompat.EDGE_LEFT && width > 0f) {
                    if (startX.isNaN()) startX = e.touchX
                    (e.touchX - startX) / width
                } else e.progress
                t.progress.snapTo(1f - fraction.coerceIn(0f, 1f))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Cancelado: la página vuelve a taparlo todo.
            scope.launch {
                t.progress.animateTo(1f, Spring)
                if (transition === t) transition = null
            }
            throw e
        }
        stack.removeAt(stack.lastIndex)
        start(t, target = 0f, resetTransition = false) { forget(listOf(t.over)) }
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
            t.progress.animateTo(target, Spring)
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
        }
    }

    companion object {
        /** Muelle sin rebote de ~0,45 s, como el push de UINavigationController. */
        val Spring = spring<Float>(dampingRatio = 1f, stiffness = 220f)

        /** Cuánto se desplaza la página de debajo (fracción del ancho) y cuánto se oscurece. */
        const val UnderShift = 0.3f
        const val UnderDim = 0.12f
    }
}
