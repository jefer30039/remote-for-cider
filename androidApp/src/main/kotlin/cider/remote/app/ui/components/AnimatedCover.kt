package cider.remote.app.ui.components

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cider.remote.app.R

/**
 * Portada animada de Apple Music (HLS). Muestra [placeholder] (la carátula fija)
 * hasta que el vídeo pinta su primer fotograma y entonces lo funde encima.
 * Sin sonido, en bucle y en pausa mientras la app no está visible.
 */
/** Verdadero cuando la página está tapada (hoja de reproducción abierta del todo): los vídeos se pausan. */
val LocalPageHidden = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * [playing]: reproducir ahora (p. ej. solo cuando la tarjeta está a la vista); el
 * reproductor no se crea hasta la primera vez que se pide. [maxVideoSize]: tope de
 * resolución (px) para que ExoPlayer elija una variante HLS acorde al tamaño en pantalla.
 */
@OptIn(UnstableApi::class)
@Composable
fun AnimatedCover(
    url: String?,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    maxVideoSize: IntSize? = null,
    placeholder: @Composable () -> Unit,
) {
    Box(modifier) {
        placeholder()
        if (url == null) return@Box
        var wanted by remember(url) { mutableStateOf(playing) }
        LaunchedEffect(playing) { if (playing) wanted = true }
        if (!wanted) return@Box
        val hidden by LocalPageHidden.current
        val context = LocalContext.current
        var ready by remember(url) { mutableStateOf(false) }
        val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(450), label = "portada")
        val player = remember(url) {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                if (maxVideoSize != null) {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(maxVideoSize.width, maxVideoSize.height).build()
                }
                setMediaItem(MediaItem.fromUri(url))
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { ready = true }
                })
                prepare()
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        val active = playing && !hidden
        LifecycleResumeEffect(player, active) {
            if (active) player.play() else player.pause()
            onPauseOrDispose { player.pause() }
        }
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.animated_cover, null, false) as PlayerView).apply {
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )
    }
}
