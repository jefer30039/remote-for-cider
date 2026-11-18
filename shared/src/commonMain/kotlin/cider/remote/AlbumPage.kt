package cider.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Lo que Apple Music enseña debajo de las canciones de un álbum del catálogo: el pie (Apple Digital
 * Master, fecha de lanzamiento y copyright) y las estanterías (vídeos musicales, más para ver, más del
 * artista, aparece en, quizás también te interese…), con los títulos localizados que manda Apple.
 */
@Serializable
data class AlbumExtras(
    /** `AAAA-MM-DD`. */
    val releaseDate: String?,
    val copyright: String?,
    /** `isMasteredForItunes`: Apple pone "Apple Digital Master" con su icono. */
    val digitalMaster: Boolean,
    val shelves: List<HomeShelf>,
)

/** Vistas de un álbum, en el orden de Apple Music para iOS. */
private val AlbumViews = listOf(
    "other-versions", "related-videos", "video-extras", "audio-extras", "more-by-artist", "appears-on", "you-might-also-like",
)

/** Lo que se sabe abrir o reproducir: vídeos musicales en Cider, vídeos subidos en el teléfono. */
private val AlbumOpenable = setOf("albums", "playlists", "artists", "stations", "songs", "music-videos", "uploaded-videos")

suspend fun CiderApi.albumExtras(catalogId: String): AlbumExtras {
    val path = "v1/catalog/\$STOREFRONT/albums/$catalogId?views=${AlbumViews.joinToString(",")}&art%5Burl%5D=f"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val album = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw IllegalStateException("Album $catalogId not found")
    val a = album.obj("attributes")
    val views = album.obj("views")
    val shelves = AlbumViews.mapNotNull { name ->
        val view = views?.obj(name) ?: return@mapNotNull null
        val items = view["data"]?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val item = o.toHomeItem(preferNotes = false)?.takeIf { it.type in AlbumOpenable } ?: return@mapNotNull null
            val attributes = o.obj("attributes")
            when (item.type) {
                // Vídeo subido: MP4 completo, el de mejor calidad que haya; sin ninguno no se puede reproducir.
                "uploaded-videos" -> {
                    val tokens = attributes?.obj("assetTokens")
                    listOf("1080pHdVideo", "720pHdVideo", "sdVideo", "sd480pVideo").firstNotNullOfOrNull { tokens?.str(it) }
                        ?.let { item.copy(videoUrl = it) }
                }
                // Vídeo musical: Cider no los reproduce por la API (`play-item` y `play-url` vacían la cola y no
                // suena nada; el completo lleva DRM). Queda la vista previa de 30 s (HLS en HD o el MP4).
                "music-videos" -> {
                    val preview = attributes?.get("previews")?.jsonArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                    (preview?.str("hlsUrl") ?: preview?.str("url"))?.let { item.copy(videoUrl = it) }
                }
                else -> item
            }
        }
        if (items.isEmpty()) return@mapNotNull null
        val videos = items.all { it.type == "music-videos" || it.type == "uploaded-videos" }
        HomeShelf(view.obj("attributes")?.str("title"), if (videos) ShelfKind.Videos else ShelfKind.Covers, items, view = name)
    }
    return AlbumExtras(
        releaseDate = a?.str("releaseDate"),
        copyright = a?.str("copyright")?.takeIf { it.isNotBlank() },
        digitalMaster = (a?.get("isMasteredForItunes") as? JsonPrimitive)?.content == "true",
        shelves = shelves,
    )
}
