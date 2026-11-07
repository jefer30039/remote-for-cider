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
