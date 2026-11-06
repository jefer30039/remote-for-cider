package cider.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Envelope<T>(val data: T, val meta: PageMeta? = null)

@Serializable
data class PageMeta(val offset: Int, val limit: Int, val total: Int)

@Serializable
data class ApiError(val code: String, val message: String = "")

@Serializable
data class Artwork(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bgColor: String? = null,
    val textColor1: String? = null,
    val textColor2: String? = null,
) {
    /**
     * La API v2 de Cider da URLs ya con tamaño (`600x600bb.png`); el proxy de
     * Apple Music las da como plantilla (`{w}x{h}bb.{f}`). Sirve para ambas.
     */
    fun sized(px: Int): String? = sized(px, px)

    /** Ancho y alto distintos: para las carátulas editoriales verticales (3:4), que si no el servidor recorta a cuadrado. */
    fun sized(w: Int, h: Int): String? = url
        ?.replace("{w}", w.toString())?.replace("{h}", h.toString())?.replace("{f}", "jpg")
        ?.replace(SIZE_REGEX, "/${w}x${h}$1")

    /** Recorte de w × h que elige el servidor de Apple (`sr`): para carátulas editoriales panorámicas. */
    fun cropped(w: Int, h: Int): String? = url
        ?.replace("bb.{f}", "sr.{f}")?.replace("{c}", "sr")
        ?.replace("{w}", w.toString())?.replace("{h}", h.toString())?.replace("{f}", "jpg")

    private companion object {
        val SIZE_REGEX = Regex("""/\d+x\d+(\w*\.(?:png|jpe?g|webp))$""")
    }
}

@Serializable
data class PlayParams(val id: String, val kind: String, val catalogId: String? = null)

/** Atributos de una canción (now-playing y elementos de la cola). */
@Serializable
data class Track(
    val name: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val artwork: Artwork? = null,
    val durationInMillis: Long = 0,
    val playParams: PlayParams? = null,
    val url: String? = null,
    val hasLyrics: Boolean = false,
    val hasTimeSyncedLyrics: Boolean = false,
    val inLibrary: Boolean? = null,
    val audioTraits: List<String> = emptyList(),
    /** "explicit" o "clean"; Apple no lo envía en las canciones sin clasificar. */
    val contentRating: String? = null,
) {
    val isExplicit: Boolean get() = contentRating == "explicit"

    /** Id del catálogo; las canciones de la biblioteca traen uno `i.…` en `id` y el bueno en `catalogId`. */
    val catalogId: String?
        get() = playParams?.catalogId ?: playParams?.id?.takeUnless { it.startsWith("i.") }
}

@Serializable
data class PlaybackTime(
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val remaining: Double = 0.0,
)

@Serializable
enum class RepeatMode {
    @SerialName("none") None,
    @SerialName("one") One,
    @SerialName("all") All,
}

/** `GET /playback`. */
@Serializable
data class PlaybackSnapshot(
    val state: String = "paused",
    val nowPlaying: Track? = null,
    val time: PlaybackTime? = null,
    val volume: Double = 1.0,
    val repeatMode: RepeatMode = RepeatMode.None,
    val shuffleMode: Boolean = false,
    val autoplay: Boolean = false,
    val rate: Double = 1.0,
) {
    val isPlaying: Boolean get() = state == "playing"
}

@Serializable
data class QueueTrack(
    val id: String,
    val type: String,
    val attributes: Track? = null,
)

@Serializable
data class QueueItem(val track: QueueTrack)

@Serializable
data class QueuePage(val items: List<QueueItem>, val position: Int)

@Serializable
data class QueuePosition(val position: Int, val total: Int)

@Serializable
data class Items<T>(val items: List<T>)

@Serializable
data class LibraryPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val canEdit: Boolean = false,
    val trackCount: Int? = null,
    val artwork: Artwork? = null,
)

@Serializable
data class LibraryAlbum(
    val id: String,
    val name: String,
    val artistName: String = "",
    val trackCount: Int? = null,
    val releaseDate: String? = null,
    val artwork: Artwork? = null,
)

@Serializable
data class LibraryArtist(
    val id: String,
    val name: String,
    val artwork: Artwork? = null,
)

@Serializable
data class LibrarySong(
    val id: String,
    val name: String,
    val artistName: String = "",
    val albumName: String = "",
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val artwork: Artwork? = null,
)

@Serializable
data class NowPlayingStatus(val inLibrary: Boolean, val rating: Int)

@Serializable
data class ClientInfo(
    val framework: String = "",
    val platform: String = "",
    val version: String = "",
    val port: Int = 0,
)

/** Línea de tiempo de `playbackTimeDidChange`. */
@Serializable
data class TimeAnchor(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val currentTime: Double,
    val duration: Double,
    val timestamp: Long,
    val rate: Double = 1.0,
)

@Serializable
data class PlaybackTimeEvent(
    val currentPlaybackTime: Double,
    val currentPlaybackDuration: Double,
    val isPlaying: Boolean,
    val anchor: TimeAnchor? = null,
)

/** Recurso de la API de Apple Music (vía `/amapi`). */
@Serializable
data class AmItem(
    val id: String,
    val type: String,
    val attributes: AmAttributes? = null,
    val relationships: AmRelationships? = null,
) {
    val name: String get() = attributes?.name.orEmpty()

    /** Los artistas de la biblioteca no traen foto; la del catálogo sí (`include=catalog`). */
    val artwork: Artwork?
        get() = attributes?.artwork ?: relationships?.catalog?.data?.firstOrNull()?.attributes?.artwork
}

@Serializable
data class AmRelationships(val catalog: AmCatalogRef? = null)

@Serializable
data class AmCatalogRef(val data: List<AmCatalogItem> = emptyList())

/** Versión plana para no anidar `AmItem` dentro de sí mismo. */
@Serializable
data class AmCatalogItem(val id: String, val type: String, val attributes: AmAttributes? = null)

@Serializable
data class AmAttributes(
    val name: String = "",
    val shortName: String? = null,
    val genreNames: List<String> = emptyList(),
    val trackCount: Int? = null,
    val artistName: String? = null,
    val curatorName: String? = null,
    val artwork: Artwork? = null,
    val durationInMillis: Long? = null,
    val releaseDate: String? = null,
)

@Serializable
data class AmList(val data: List<AmItem> = emptyList())

@Serializable
data class SearchResults(
    val songs: AmList? = null,
    val albums: AmList? = null,
    val artists: AmList? = null,
    val playlists: AmList? = null,
)

@Serializable
data class SearchEnvelope(val results: SearchResults = SearchResults())

/** `GET /playback/audio-quality`. */
@Serializable
data class AudioQuality(val flavor: String? = null, val flavorLabel: String? = null) {
    /** Etiqueta que Apple Music muestra como insignia, o null si es AAC normal. */
    val badge: String?
        get() {
            val f = flavor.orEmpty().lowercase()
            val l = flavorLabel.orEmpty()
            return when {
                "atmos" in f || "atmos" in l.lowercase() -> "Dolby Atmos"
                "lossless" in l.lowercase() || "alac" in f -> if ("hi-res" in l.lowercase()) "Hi-Res Lossless" else "Lossless"
                else -> null
            }
        }
}

/** Mosaico de "Explorar categorías" (portada de la búsqueda de Apple Music). */
data class SearchCategory(
    val id: String,
    val name: String,
    /** Imagen 4:1 con la persona centrada; se recorta al centro. */
    val imageUrl: String?,
    val bgColor: String?,
)

/**
 * Datos de catálogo de una carátula: portada animada (`editorialVideo`, listas HLS
 * sin autenticación) y el color de fondo oficial de Apple (`artwork.bgColor`).
 */
data class AnimatedCover(val square: String?, val tall: String?, val bgColor: String? = null)
