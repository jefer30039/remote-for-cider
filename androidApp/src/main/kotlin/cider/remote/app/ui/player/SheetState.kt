package cider.remote.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Posición de la hoja de reproducción, compartida por el miniplayer (deslizar
 * hacia arriba la abre) y la propia hoja (deslizar hacia abajo la cierra): la
 * hoja sigue al dedo en los dos sentidos, como en Apple Music.
 * [position] va de 0 (abierta) a 1 (cerrada, fuera de la pantalla).
 */
@Stable
class SheetState(initiallyOpen: Boolean, private val scope: CoroutineScope, private val flingPx: Float) {
    val position = Animatable(if (initiallyOpen) 0f else 1f)
    /** Alto de la pantalla en px; lo fija quien dibuja la hoja. */
    var height = 1f

    /**
     * Transición de Apple Music en Android (medida en el teléfono, en dos grabaciones de pantalla
     * del 2026-11-16; en tablet, lo mismo con su miniplayer): la hoja sale de la altura del
     * miniplayer y aparece fundiéndose, su carátula vuela desde la miniatura del miniplayer hasta
     * su sitio, la página de debajo se oscurece y la barra y el miniplayer bajan y salen de la
     * pantalla. Todo es función de [progress], así que también sigue al dedo.
     */
    /** Miniatura del miniplayer en reposo (coordenadas de la raíz); origen del vuelo de la carátula. */
    var thumb: Rect? = null
        private set
    private var miniTop = 0f
    /** Cuánto bajan la barra y el miniplayer para salir de la pantalla, en px. */
    var barsTravel = 0f

    fun setThumb(bounds: Rect, miniTop: Float) {
        val old = thumb
        // Con tolerancia: el redondeo al colocar las barras mientras bajan no debe mover el origen.
        if (old == null || kotlin.math.abs(old.top - bounds.top) > 1.5f || kotlin.math.abs(old.left - bounds.left) > 1.5f ||
            kotlin.math.abs(old.width - bounds.width) > 1.5f
        ) {
            thumb = bounds
            this.miniTop = miniTop
        }
    }

    /** Recorrido de la hoja en px: desde el borde superior del miniplayer (o desde abajo, si aún no se ha medido). */
    val distance: Float get() = if (thumb != null) miniTop.coerceAtLeast(1f) else height
    /** Cuánto está desplazada la hoja hacia abajo ahora, en px. */
    val offsetPx: Float get() = position.value * distance
    /** 0 = cerrada, 1 = abierta. */
    val progress: Float get() = (1f - position.value).coerceIn(0f, 1f)
    /** Opacidad del fondo y los controles de la hoja (la carátula va siempre opaca). */
    val contentAlpha: Float get() = smooth((progress - 0.2f) / 0.6f)
    /** Oscurecido de la página de debajo. */
    val scrim: Float get() = 0.9f * progress
    /** Desplazamiento hacia abajo de la barra y el miniplayer, en px: ya han salido con un 30 % de recorrido. */
    val barsShift: Float get() = barsTravel * smooth(progress / 0.3f)

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /** Se dibuja mientras no esté del todo cerrada. */
    val visible get() = position.value < 1f
    /** Abierta o yendo hacia abierta (para el botón atrás y para guardar el estado). */
    val isOpen get() = position.targetValue == 0f

    // Posición del dedo, al día aunque las animaciones vayan en corrutinas: al soltar
    // se decide con ella (position.value aún no ha recibido los últimos movimientos).
    private var target = position.value

    /** [tap]: abierta con un toque (sale rápida y frena, como en Apple Music); si no, al soltar el dedo. */
    fun open(tap: Boolean = false) {
        target = 0f
        scope.launch {
            if (tap) {
                // La hoja tarda 2–4 fotogramas en componerse la primera vez y la curva sale rápida: sin
                // esperar, cuando por fin se dibuja ya va por más de la mitad y el vuelo no se ve.
                if (position.value >= 1f) {
                    position.snapTo(0.999f)
                    repeat(2) { withFrameNanos { } }
                }
                position.animateTo(0f, Tap)
            } else position.animateTo(0f, Settle)
        }
    }

    /** Cierra; [released] = al soltar el dedo (muelle), si no con la curva lenta de Apple Music (botón atrás, ir a otra página). */
    fun close(released: Boolean = false) {
        target = 1f
        scope.launch { position.animateTo(1f, if (!released) Close else Settle) }
    }

    fun dragBy(px: Float) {
        target = (target + px / distance).coerceIn(0f, 1f)
        val to = target
        scope.launch { position.snapTo(to) }
    }

    private var dragStart = 1f
    fun startDrag() { target = position.value; dragStart = target }

    /**
     * Al soltar: un gesto rápido decide; si no, basta con recorrer una quinta parte
     * desde donde empezó (cerrar desde abierta o abrir desde el miniplayer).
     */
    fun settle(velocity: Float) = when {
        velocity < -flingPx -> open()
        velocity > flingPx -> close(released = true)
        dragStart < 0.5f -> if (target > 0.2f) close(released = true) else open()
        else -> if (target < 0.8f) open() else close(released = true)
    }

    private companion object {
        val Settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
        // Medido en Apple Music. Al abrir con un toque sale rápida y frena como una exponencial de
        // constante ~95 ms: 37 % a los 40 ms, 67 % a los 107, 87 % a los 173 y 95 % a los 240.
        val Tap = tween<Float>(450, easing = { x -> ((1.0 - kotlin.math.exp(-x * 450.0 / 95.0)) / (1.0 - kotlin.math.exp(-450.0 / 95.0))).toFloat() })
        // Al cerrar va casi a velocidad constante y frena al final: 28 % a los 140 ms, 49 % a los 240,
        // 68 % a los 340, 82 % a los 440 y acaba hacia los 590.
        val Close = tween<Float>(580, easing = CubicBezierEasing(0.25f, 0.3f, 0.6f, 1f))
    }
}

@Composable
fun rememberSheetState(): SheetState {
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { 600.dp.toPx() }
    return rememberSaveable(saver = Saver(save = { it.isOpen }, restore = { SheetState(it, scope, flingPx) })) {
        SheetState(false, scope, flingPx)
    }
}

/**
 * Arrastre vertical que mueve la hoja (en el miniplayer y en las zonas de la hoja
 * que no desplazan). No usa `draggable`: si la hoja tarda en componerse justo al
 * empezar el gesto, Android junta los movimientos con el de soltar y `draggable`
 * descarta ese último tramo (un deslizamiento rápido no abría la hoja). Aquí cuenta
 * también el movimiento que llega con el dedo al levantarse.
 */
fun Modifier.sheetDrag(sheet: SheetState): Modifier = pointerInput(sheet) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.resetTracking()
        tracker.addPosition(down.uptimeMillis, down.position)
        val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            sheet.startDrag()
            sheet.dragBy(overSlop)
        } ?: return@awaitEachGesture
        tracker.addPointerInputChange(start)
        val id = start.id
        var last = start
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
            val dy = change.position.y - change.previousPosition.y
            if (dy != 0f) sheet.dragBy(dy)
            tracker.addPointerInputChange(change)
            change.consume()
            last = change
            if (!change.pressed) break
        }
        // Si el hilo se bloqueó, Android junta los movimientos con el de soltar y el
        // VelocityTracker da 0: entonces la velocidad media del gesto.
        val velocity = tracker.calculateVelocity().y.takeIf { it != 0f }
            ?: ((last.position.y - down.position.y) * 1000f / (last.uptimeMillis - down.uptimeMillis).coerceAtLeast(1)).toFloat()
        sheet.settle(velocity)
    }
}
