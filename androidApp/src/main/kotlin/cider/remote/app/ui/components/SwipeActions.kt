package cider.remote.app.ui.components

import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cider.remote.app.ui.AppleIcons
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Medidas y colores de Apple Music: botones de 98 dp, systemOrange y systemIndigo. */
private val ActionWidth = 98.dp
private val PlayLastColor = Color(0xFFFF9F0A)
private val PlayNextColor = Color(0xFF5E5CE6)

/**
 * Deslizar a la derecha descubre "Reproducir al final" (naranja, en el borde) y
 * "Reproducir a continuación" (morado). Deslizar del todo ejecuta la del borde.
 * Las dos acciones también están disponibles para lectores de pantalla.
 */
@Composable
fun SwipeQueueActions(
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val reveal = with(density) { (ActionWidth * 2).toPx() }
    val playNext = stringResource(R.string.action_play_next)
    val playLast = stringResource(R.string.action_play_last)

    BoxWithConstraints(
        modifier.fillMaxWidth().clipToBounds().semantics {
            customActions = listOf(
                CustomAccessibilityAction(playNext) { onPlayNext(); true },
                CustomAccessibilityAction(playLast) { onPlayLast(); true },
            )
        },
    ) {
        val full = with(density) { maxWidth.toPx() } * 0.6f
        fun settle(target: Float) = scope.launch { offset.animateTo(target, spring(stiffness = 600f)) }
        fun run(action: () -> Unit) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            action()
            settle(0f)
        }

        // Botones, solo en la franja descubierta.
        val shown = with(density) { offset.value.toDp() }
        Box(Modifier.matchParentSize()) { Row(Modifier.fillMaxHeight().width(shown)) {
            // Al pasar del umbral, el naranja ocupa toda la franja: soltar lo ejecuta.
            val pastFull = offset.value > full
            Box(
                Modifier.fillMaxHeight().width(if (pastFull) shown else shown / 2).background(PlayLastColor)
                    .combinedClickableCompat(onClick = { run(onPlayLast) }),
                contentAlignment = Alignment.Center,
            ) { Icon(AppleIcons.PlayLast, playLast, tint = Color.White, modifier = Modifier.size(26.dp)) }
            if (!pastFull) Box(
                Modifier.fillMaxHeight().width(shown / 2).background(PlayNextColor)
                    .combinedClickableCompat(onClick = { run(onPlayNext) }),
                contentAlignment = Alignment.Center,
            ) { Icon(AppleIcons.PlayNext, playNext, tint = Color.White, modifier = Modifier.size(26.dp)) }
        } }

        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, full * 1.4f)) }
                    },
                    onDragStopped = {
                        when {
                            offset.value > full -> run(onPlayLast)
                            offset.value > reveal / 2 -> settle(reveal)
                            else -> settle(0f)
                        }
                    },
                ),
        ) {
            content()
            // Abierta: tocar la fila la cierra en vez de reproducir.
            if (offset.value > 0f) Box(Modifier.matchParentSize().combinedClickableCompat(onClick = { settle(0f) }))
        }
    }
}
