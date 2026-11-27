package cider.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Álbum, artista y enlace de una canción, para los menús "Ir al álbum / artista" y "Compartir". */
@Serializable
data class SongLinks(
    val catalogId: String?,
    val url: String?,
    /** `albums` (catálogo) o `library-albums`. */
    val album: HomeItem?,
    /** `artists` (catálogo) o `library-artists`. */
    val artist: HomeItem?,
)

/** Las canciones de la biblioteca (`i.…`) se consultan en la biblioteca; el resto, en el catálogo. */
@Throws(Throwable::class)
suspend fun CiderApi.songLinks(id: String): SongLinks {
    val library = id.startsWith("i.")
    val path = if (library) "v1/me/library/songs/$id?include=albums,artists"
    else "v1/catalog/\$STOREFRONT/songs/$id?include=albums,artists"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val song = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw IllegalStateException("Song $id not found")
    val a = song.obj("attributes")
    val relationships = song.obj("relationships")
    fun first(name: String) = relationships?.obj(name)?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.toHomeItem(preferNotes = false)
    val catalogId = a?.obj("playParams")?.str("catalogId") ?: id.takeUnless { library }
    return SongLinks(
        catalogId = catalogId,
        url = a?.str("url") ?: catalogId?.let { "https://music.apple.com/song/$it" },
        album = first("albums"),
        artist = first("artists"),
    )
}

/**
 * Artista principal de un álbum (`albums` del catálogo o `library-albums`), como [HomeItem]
 * (`artists` o `library-artists`), para "ir al artista" desde la página del álbum.
 */
@Throws(Throwable::class)
suspend fun CiderApi.albumArtist(kind: String, id: String): HomeItem? {
    val path = if (kind == "library-albums") "v1/me/library/albums/$id?include=artists"
    else "v1/catalog/\$STOREFRONT/albums/$id?include=artists"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val album = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    return album.obj("relationships")?.obj("artists")?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.toHomeItem(preferNotes = false)
}

/**
 * Enlace de Apple Music de un álbum o playlist ([kind] como en las rutas: `albums`, `playlists`,
 * `library-albums`, `library-playlists`), para "Compartir". Las playlists de la biblioteca se buscan en el
 * catálogo por su `playParams.globalId`. Null si no tiene (playlist propia sin publicar, álbum subido).
 */
suspend fun CiderApi.collectionUrl(kind: String, id: String): String? {
    fun first(json: String) = CiderJson.parseToJsonElement(json).jsonObject["data"]?.jsonObject
        ?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
    val path = when (kind) {
        "library-albums" -> "v1/me/library/albums/$id/catalog"
        "library-playlists" -> {
            val global = first(rawGet("v1/me/library/playlists/$id"))?.obj("attributes")?.obj("playParams")?.str("globalId")
                ?: return null
            "v1/catalog/\$STOREFRONT/playlists/$global"
        }
        else -> "v1/catalog/\$STOREFRONT/$kind/$id"
    }
    return first(rawGet(path))?.obj("attributes")?.str("url")
}
