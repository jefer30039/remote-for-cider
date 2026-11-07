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
 * Falso mientras la página entra o sale con la transición de navegación: las portadas animadas
 * no crean su reproductor hasta que la página está quieta (crear la vista de vídeo y decodificar
 * en plena transición costaba fotogramas; Apple Music también las arranca al asentarse).
 */
val LocalPageSettled = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

/**
 * [playing]: reproducir ahora (p. ej. solo cuando la tarjeta está a la vista); el
 * reproductor no se crea hasta la primera vez que se pide. [maxVideoSize]: tope de
 * resolución (px) para que ExoPlayer elija una variante HLS acorde al tamaño en pantalla.
 */
/**
 * Reproductores vivos mientras dura la pantalla que los usa (p. ej. Inicio): una fila
 * que sale y vuelve a entrar en pantalla reutiliza el suyo en vez de crear otro
 * (inicializar MediaCodec costaba ~28 ms y una ráfaga de texturas: fotogramas de 20 ms
 * cada vez que la estantería de portadas entraba o salía). Como mucho [capacity]; el
 * menos usado se libera.
 */
@OptIn(UnstableApi::class)
class CoverPlayerPool(private val context: android.content.Context, private val capacity: Int = 3) {
    private val players = LinkedHashMap<String, ExoPlayer>()
    /**
     * Pausa general mientras la lista se desplaza: cada fotograma de vídeo cuesta al
     * RenderThread (importar la textura en Vulkan y redibujar el vidrio); sumado al
     * desplazamiento, una de cada cuatro imágenes pasaba de 16,7 ms. Se reanuda al parar.
     */
    var paused by mutableStateOf(false)

    fun acquire(url: String, maxVideoSize: IntSize?): ExoPlayer {
        players.remove(url)?.let { players[url] = it; return it }
        while (players.size >= capacity) players.remove(players.keys.first())?.release()
        return ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            if (maxVideoSize != null) {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(maxVideoSize.width, maxVideoSize.height).build()
            }
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            players[url] = this
        }
    }

    fun releaseAll() { players.values.forEach { it.release() }; players.clear() }
}

/** Un pool que vive mientras la pantalla esté compuesta. */
@Composable
fun rememberCoverPlayerPool(capacity: Int = 3): CoverPlayerPool {
    val context = LocalContext.current
    val pool = remember { CoverPlayerPool(context, capacity) }
    DisposableEffect(pool) { onDispose { pool.releaseAll() } }
    return pool
}

@OptIn(UnstableApi::class)
@Composable
fun AnimatedCover(
    url: String?,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    maxVideoSize: IntSize? = null,
    /** Con pool, el reproductor sobrevive a la salida de pantalla; sin él, se crea y libera aquí. */
    pool: CoverPlayerPool? = null,
    placeholder: @Composable () -> Unit,
) {
    Box(modifier) {
        placeholder()
        if (url == null) return@Box
        val settled by LocalPageSettled.current
        var wanted by remember(url) { mutableStateOf(playing && settled) }
        LaunchedEffect(playing, settled) { if (playing && settled) wanted = true }
        if (!wanted) return@Box
        val hidden by LocalPageHidden.current
        val context = LocalContext.current
        var ready by remember(url) { mutableStateOf(false) }
        // Con el pool en pausa (la lista se desplaza) la vista del vídeo se funde a la carátula fija
        // en 120 ms y sale de la composición: una PlayerView/TextureView dentro de la lista, aunque
        // parada, costaba ~2 ms de hilo UI y un 20 % de fotogramas lentos al desplazar Inicio.
        // Al asentarse vuelve (el reproductor sigue en el pool) y se funde cuando pinta su fotograma.
        val paused = pool?.paused ?: false
        val shown = ready && !paused
        // Fundidos cortos: mientras el alfa no es 1 la tarjeta entera va en una capa aparte.
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 300 else 120), label = "portada")
        val attached = !paused || alpha > 0f
        val player = remember(url, pool) {
            pool?.acquire(url, maxVideoSize) ?: ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                if (maxVideoSize != null) {
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(maxVideoSize.width, maxVideoSize.height).build()
                }
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        }
        // El primer fotograma en esta superficie (también al volver con un reproductor del pool).
        DisposableEffect(player) {
            val listener = object : Player.Listener { override fun onRenderedFirstFrame() { ready = true } }
            player.addListener(listener)
            onDispose { player.removeListener(listener); if (pool == null) player.release() else player.pause() }
        }
        val active = playing && !hidden && !paused
        LifecycleResumeEffect(player, active) {
            if (active) player.play() else player.pause()
            onPauseOrDispose { player.pause() }
        }
        if (attached) DisposableEffect(Unit) { onDispose { ready = false } }
        if (attached) AndroidView(
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
