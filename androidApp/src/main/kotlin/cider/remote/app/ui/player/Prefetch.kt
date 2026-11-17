package cider.remote.app.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import cider.remote.CiderRemote
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.model.AnimatedCover
import cider.remote.model.Artwork
import cider.remote.model.Track
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Tamaños con que la hoja pide la carátula (tarjeta, tablet, vídeo, fondo). */
internal const val SheetArtworkPx = 1000
internal const val WideSheetArtworkPx = 800
internal const val HeroArtworkPx = 1200
/** La paleta del fondo se saca de esta miniatura. */
internal const val SheetBackgroundPx = 64
/** El del miniplayer: casi siempre ya está en memoria y sirve de anticipo. */
internal const val PreviewArtworkPx = 120
/** Ancho con que se pide el fotograma fijo del vídeo vertical (3:4). */
private const val HeroPreviewPx = 1200

/**
 * Fotograma fijo del vídeo vertical de la portada animada, al tamaño de la hoja: la imagen de espera
 * de la hoja mientras el vídeo carga. Coincide con su primer fotograma (comprobado en cinco álbumes),
 * así que el vídeo empieza sin salto; la carátula fija ampliada, que es lo que había, puede tener otro
 * encuadre (en "Un Verano Sin Ti" o "GTA VI: The Album" el cambio se veía brusco).
 */
internal fun AnimatedCover.heroPreview(): String? = tallPreview?.sized(HeroPreviewPx, HeroPreviewPx * 4 / 3)

/** Portadas animadas ya consultadas por id de catálogo (también las que no tienen). */
internal object AnimatedCovers {
    private class Entry(val cover: AnimatedCover?)
    private val known = ConcurrentHashMap<String, Entry>()

    fun peek(catalogId: String?): AnimatedCover? = catalogId?.let { known[it]?.cover }

    suspend fun load(remote: CiderRemote, catalogId: String?): AnimatedCover? {
        if (catalogId == null) return null
        known[catalogId]?.let { return it.cover }
        // Un fallo de red no se recuerda: se reintenta la próxima vez.
        val cover = runCatching { remote.api.animatedCoverForSong(catalogId) }.getOrElse { return null }
        known[catalogId] = Entry(cover)
        return cover
    }

    // Dónde cae la carátula fija dentro del vídeo vertical, por URL del fotograma fijo.
    private val squareTops = ConcurrentHashMap<String, Float>()

    fun peekSquareTop(cover: AnimatedCover?): Float? = cover?.tallPreview?.url?.let { squareTops[it] }

    /**
     * A qué altura del vídeo vertical (fracción de su ancho, desde arriba) está el cuadrado que coincide
     * con la carátula fija: arriba del todo en unos álbumes ("Un Verano Sin Ti"), centrado en otros
     * ("Short n' Sweet"). De ahí sale la pieza que vuela desde la miniatura. Se busca comparando las dos
     * imágenes en pequeño; `null` si falta alguna.
     */
    suspend fun squareTop(context: Context, artwork: Artwork?, cover: AnimatedCover?): Float? {
        val preview = cover?.tallPreview ?: return null
        val key = preview.url ?: return null
        squareTops[key]?.let { return it }
        val top = withContext(Dispatchers.Default) {
            matchSquare(context, artwork?.sized(MatchPx) ?: return@withContext null, preview.sized(MatchPx, MatchPx * 4 / 3) ?: return@withContext null)
        } ?: return null
        squareTops[key] = top
        return top
    }
}

private const val MatchPx = 48

/** Desplazamiento vertical (en anchos) con que la carátula [art] encaja mejor sobre el fotograma 3:4 [preview]. */
private suspend fun matchSquare(context: Context, art: String, preview: String): Float? = runCatching {
    suspend fun pixels(url: String, w: Int, h: Int): IntArray? {
        val request = ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val bitmap = Bitmap.createScaledBitmap(result.image.toBitmap(), w, h, true)
        return IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    }
    val n = MatchPx
    val tall = n * 4 / 3
    val a = pixels(art, n, n) ?: return null
    val p = pixels(preview, n, tall) ?: return null
    var best = 0
    var bestError = Long.MAX_VALUE
    for (y0 in 0..tall - n) {
        var error = 0L
        for (i in 0 until n * n) {
            val u = a[i]
            val v = p[i + y0 * n]
            error += kotlin.math.abs(((u shr 16) and 0xFF) - ((v shr 16) and 0xFF)) +
                kotlin.math.abs(((u shr 8) and 0xFF) - ((v shr 8) and 0xFF)) + kotlin.math.abs((u and 0xFF) - (v and 0xFF))
        }
        if (error < bestError) { bestError = error; best = y0 }
    }
    best / n.toFloat()
}.getOrNull()

/**
 * Carga de antemano lo que pinta la hoja de reproducción para la canción actual y
 * las dos siguientes, esté abierta o no: al abrirla o al pasar de canción la
 * carátula sale de memoria en vez de quedarse un rato vacía.
 */
@Composable
fun PrefetchNowPlaying(remote: CiderRemote, track: Track?) {
    val context = LocalContext.current
    val wide = LocalWindowLayout.current.expanded
    LaunchedEffect(track?.artwork?.url, track?.catalogId, wide) {
        track ?: return@LaunchedEffect
        prefetch(context, remote, track, wide)
        // Las siguientes, después de dar paso a la actual.
        delay(1500)
        val next = runCatching {
            val start = remote.api.queuePosition().position + 1
            remote.api.queue(offset = start, limit = 2).first.items.mapNotNull { it.track.attributes }
        }.getOrDefault(emptyList())
        next.forEach { prefetch(context, remote, it, wide) }
    }
}

private suspend fun prefetch(context: Context, remote: CiderRemote, track: Track, wide: Boolean) {
    val artwork = track.artwork ?: return
    val loader = SingletonImageLoader.get(context)
    // Tamaño original (la URL ya lo fija): así vale para cualquier tamaño de pantalla.
    fun enqueue(px: Int) = artwork.sized(px)?.let {
        loader.enqueue(ImageRequest.Builder(context).data(it).size(Size.ORIGINAL).build())
    }
    enqueue(if (wide) WideSheetArtworkPx else SheetArtworkPx)
    enqueue(SheetBackgroundPx)
    enqueue(PreviewArtworkPx)
    // Con portada animada (teléfono): la carátula grande, el fotograma fijo del vídeo y dónde cae en él la carátula.
    val cover = if (wide) null else AnimatedCovers.load(remote, track.catalogId)
    if (cover?.tall != null) {
        enqueue(HeroArtworkPx)
        cover.heroPreview()?.let { loader.enqueue(ImageRequest.Builder(context).data(it).size(Size.ORIGINAL).build()) }
        AnimatedCovers.squareTop(context, artwork, cover)
    }
}
