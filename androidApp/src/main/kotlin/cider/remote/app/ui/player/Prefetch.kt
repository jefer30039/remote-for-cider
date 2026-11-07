package cider.remote.app.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import cider.remote.CiderRemote
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.model.AnimatedCover
import cider.remote.model.Track
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/** Tamaños con que la hoja pide la carátula (tarjeta, tablet, vídeo, fondo). */
internal const val SheetArtworkPx = 1000
internal const val WideSheetArtworkPx = 800
internal const val HeroArtworkPx = 1200
/** La paleta del fondo se saca de esta miniatura. */
internal const val SheetBackgroundPx = 64
/** El del miniplayer: casi siempre ya está en memoria y sirve de anticipo. */
internal const val PreviewArtworkPx = 120

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
}

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
    if (!wide && AnimatedCovers.load(remote, track.catalogId)?.tall != null) enqueue(HeroArtworkPx)
}
