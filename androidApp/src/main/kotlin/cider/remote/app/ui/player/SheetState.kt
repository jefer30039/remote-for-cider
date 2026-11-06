package cider.remote.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

    /** Se dibuja mientras no esté del todo cerrada. */
    val visible get() = position.value < 1f
    /** Abierta o yendo hacia abierta (para el botón atrás y para guardar el estado). */
    val isOpen get() = position.targetValue == 0f

    // Posición del dedo, al día aunque las animaciones vayan en corrutinas: al soltar
    // se decide con ella (position.value aún no ha recibido los últimos movimientos).
    private var target = position.value

    fun open() { target = 0f; scope.launch { position.animateTo(0f, Settle) } }
    fun close() { target = 1f; scope.launch { position.animateTo(1f, Settle) } }

    fun dragBy(px: Float) {
        target = (target + px / height).coerceIn(0f, 1f)
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
        velocity > flingPx -> close()
        dragStart < 0.5f -> if (target > 0.2f) close() else open()
        else -> if (target < 0.8f) open() else close()
    }

    private companion object {
        val Settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
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
