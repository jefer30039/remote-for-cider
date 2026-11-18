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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Color de las portadas animadas.
 *
 * 1. **Corrección del dispositivo.** En el POCO F7 el vídeo (H.264/HEVC, BT.709, rango limitado) se
 *    pintaba en la TextureView como 0,93 × lo correcto + 22: negros levantados y menos contraste,
 *    como si no se expandiera del todo el rango 16–235 (medido con ffmpeg contra una captura,
 *    2026-11-18). Se calibra sola una vez por dispositivo: el `previewFrame` de Apple es el primer
 *    fotograma del vídeo, así que al pintarse ese fotograma se compara con la imagen y se ajusta
 *    una recta por canal (textura = a · imagen + b). En un teléfono que pinte bien sale la identidad.
 * 2. **Nada más.** Una captura real de iOS 26 ("Mercury", en Display P3 convertida a sRGB) coincide con
 *    la decodificación estándar (cara roja (203, 30, 39) frente a (203, 30, 37)). La curva más oscura
 *    "de iOS" (≈ 0,98 × − 16) salió de una imagen promocional retocada y dejaba las portadas demasiado
 *    saturadas: se quitó. La imagen de espera solo pierde su desfase, para que no salte al llegar el vídeo.
 */
object VideoColor {
    private const val PREFS = "color_video"
    private const val KEY = "ajuste_v2"
    /** a y b por canal (R, G, B): textura = a · correcto + b, en 0–255. Null = sin calibrar. */
    @Volatile var fit: FloatArray? = null
        private set
    private var loaded = false
    val version = mutableIntStateOf(0)

    fun load(context: android.content.Context) {
        if (loaded) return
        loaded = true
        context.getSharedPreferences(PREFS, 0).getString(KEY, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 6 }?.let { fit = it.toFloatArray() }
    }

    private fun save(context: android.content.Context, f: FloatArray) {
        fit = f
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, f.joinToString(",")).apply()
        version.intValue++
    }

    /** Lo que se quiere frente a lo correcto, por canal (pendiente, desplazamiento en 0–255): iOS pinta lo correcto. */
    private val Ios = floatArrayOf(1f, 0f, 1f, 0f, 1f, 0f)

    /**
     * El `previewFrame` no es exactamente el fotograma decodificado: sale más claro, igual en todos los
     * álbumes medidos ("22:22", "Short n' Sweet", "GTA VI": imagen ≈ fotograma + (8, 10, 9), pendiente ~1).
     */
    private val PreviewBias = floatArrayOf(8f, 10f, 9f)

    /** Matriz para la imagen de espera (el `previewFrame`): se le quita su desfase. */
    val placeholder: androidx.compose.ui.graphics.ColorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            Ios[0], 0f, 0f, 0f, Ios[1] - Ios[0] * PreviewBias[0],
            0f, Ios[2], 0f, 0f, Ios[3] - Ios[2] * PreviewBias[1],
            0f, 0f, Ios[4], 0f, Ios[5] - Ios[4] * PreviewBias[2],
            0f, 0f, 0f, 1f, 0f,
        )),
    )

    /** Matriz para la textura del vídeo: deshace la del dispositivo y aplica la de iOS. */
    fun videoMatrix(): android.graphics.ColorMatrix {
        val f = fit ?: floatArrayOf(1f, 0f, 1f, 0f, 1f, 0f)
        val m = FloatArray(20)
        for (c in 0..2) {
            val a = f[c * 2]; val b = f[c * 2 + 1]
            val k = Ios[c * 2]; val o = Ios[c * 2 + 1]
            m[c * 5 + c] = k / a
            m[c * 5 + 4] = o - k * b / a
        }
        m[18] = 1f
        return android.graphics.ColorMatrix(m)
    }

    /**
     * Compara el fotograma pintado en [view] con [reference] (el `previewFrame`, mismo encuadre) y guarda
     * el ajuste si es razonable. Una sola vez por dispositivo.
     */
    suspend fun calibrate(context: android.content.Context, view: android.view.TextureView, reference: String) {
        if (fit != null) return
        val w = 24; val h = 32
        val shot = runCatching { view.getBitmap(w, h) }.getOrNull() ?: return
        val ref = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(reference).size(w * 8, h * 8).allowHardware(false).build()
                (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        } ?: return
        val small = android.graphics.Bitmap.createScaledBitmap(ref, w, h, true)
        val a = IntArray(w * h); val r = IntArray(w * h)
        shot.getPixels(a, 0, w, 0, 0, w, h); small.getPixels(r, 0, w, 0, 0, w, h)
        val out = FloatArray(6)
        for (c in 0..2) {
            val shift = 16 - 8 * c
            var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0; var syy = 0.0
            for (i in a.indices) {
                val x = ((r[i] shr shift) and 0xFF).toDouble(); val y = ((a[i] shr shift) and 0xFF).toDouble()
                sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y
            }
            val n = a.size.toDouble()
            val varx = sxx - sx * sx / n; val cov = sxy - sx * sy / n; val vary = syy - sy * sy / n
            if (varx < n * 100) { android.util.Log.i("VideoColor", "fotograma demasiado plano para calibrar"); return }
            val slope = cov / varx
            val corr = cov / kotlin.math.sqrt(varx * vary)
            // El fotograma de la textura puede ir uno o dos por detrás o por delante: solo si casan bien.
            if (corr < 0.9) { android.util.Log.i("VideoColor", "no coincide con la imagen (r = $corr)"); return }
            // textura = a · imagen + b e imagen = correcto + desfase → textura = a · correcto + (b + a · desfase).
            out[c * 2] = slope.toFloat()
            out[c * 2 + 1] = ((sy - slope * sx) / n + slope * PreviewBias[c]).toFloat()
        }
        android.util.Log.i("VideoColor", "ajuste textura = a · correcto + b: ${out.joinToString()}")
        val sane = (0..2).all { out[it * 2] in 0.8f..1.2f && out[it * 2 + 1] in -40f..40f }
        if (!sane) return
        // Casi identidad: el dispositivo pinta bien.
        val identity = (0..2).all { kotlin.math.abs(out[it * 2] - 1f) < 0.03f && kotlin.math.abs(out[it * 2 + 1]) < 4f }
        save(context, if (identity) floatArrayOf(1f, 0f, 1f, 0f, 1f, 0f) else out)
    }
}

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
    /** El `previewFrame` del vídeo (su primer fotograma, mismo encuadre): sirve para calibrar el color ([VideoColor]). */
    reference: String? = null,
    placeholder: @Composable () -> Unit,
) {
    Box(modifier) {
        // La imagen de espera con el mismo aspecto que tendrá el vídeo (iOS).
        if (url != null) Box(Modifier.fillMaxSize().graphicsLayer(colorFilter = VideoColor.placeholder)) { placeholder() } else placeholder()
        if (url == null) return@Box
        val settled by LocalPageSettled.current
        var wanted by remember(url) { mutableStateOf(playing && settled) }
        LaunchedEffect(playing, settled) { if (playing && settled) wanted = true }
        if (!wanted) return@Box
        val hidden by LocalPageHidden.current
        val context = LocalContext.current
        remember { VideoColor.load(context) }
        val colorVersion by VideoColor.version
        var textureView by remember { mutableStateOf<android.view.TextureView?>(null) }
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
        // Calibración del color del dispositivo, una vez: con el primer fotograma (el de la imagen de referencia).
        if (reference != null && VideoColor.fit == null) LaunchedEffect(ready, textureView) {
            val view = textureView ?: return@LaunchedEffect
            if (!ready || player.currentPosition > 300) return@LaunchedEffect
            delay(60)
            VideoColor.calibrate(context, view, reference)
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
            update = { view ->
                view.player = player
                val tv = view.videoSurfaceView as? android.view.TextureView
                if (tv != null) {
                    if (textureView !== tv) textureView = tv
                    // Sin calibrar todavía, la textura va sin filtro (la calibración la lee tal cual).
                    colorVersion
                    tv.setLayerPaint(
                        if (VideoColor.fit == null && reference != null) null
                        else android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(VideoColor.videoMatrix()) },
                    )
                }
            },
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )
    }
}
