package cider.remote

import cider.remote.model.Artwork
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Página de un artista del catálogo, como la de Apple Music: cabecera editorial,
 * último lanzamiento, canciones top, estanterías (álbumes, sencillos, en vivo,
 * aparece en, playlists, similares…) y biografía.
 */
@Serializable
data class ArtistPage(
    val id: String,
    val name: String,
    /** Foto redonda del artista. */
    val artwork: Artwork?,
    /** Imagen editorial vertical 3:4 para la cabecera del teléfono. */
    val heroTall: Artwork?,
    /** Imagen editorial panorámica para la tablet. */
    val heroWide: Artwork?,
    /** Logo del artista en PNG con transparencia (`musicContentColorLogoTrimmed`). */
    val logo: Artwork?,
    val bio: String?,
    val origin: String?,
    val bornOrFormed: String?,
    val genres: List<String>,
    val latest: HomeItem?,
    /** Título de la sección "Top canciones" en el idioma del storefront. */
    val topSongsTitle: String?,
    val topSongs: List<HomeItem>,
    /** Estanterías con el título localizado que manda Apple, en el orden de su app. */
    val shelves: List<HomeShelf>,
)

/** Vistas de Apple Music que se piden, en el orden en que se muestran. */
private val Views = listOf(
    "latest-release", "top-songs", "full-albums", "singles", "live-albums", "compilation-albums",
    "appears-on-albums", "featured-playlists", "similar-artists", "more-to-hear",
)
private val ShelfViews = Views - setOf("latest-release", "top-songs")

/** Lo que la app sabe abrir o reproducir (vídeos y curadores no). */
private val Openable = setOf("albums", "playlists", "artists", "stations", "songs")

suspend fun CiderApi.artistPage(catalogId: String): ArtistPage {
    val path = "v1/catalog/\$STOREFRONT/artists/$catalogId?views=${Views.joinToString(",")}" +
        "&extend=editorialArtwork,artistBio,bornOrFormed,origin&art%5Burl%5D=f"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val artist = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw IllegalStateException("Artist $catalogId not found")
    val a = artist.obj("attributes") ?: throw IllegalStateException("Artist $catalogId has no attributes")
    val editorial = a.obj("editorialArtwork")
    val views = artist.obj("views")
    fun view(name: String) = views?.obj(name)
    fun items(name: String, preferNotes: Boolean = false) =
        view(name)?.get("data")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toHomeItem(preferNotes) }.filter { it.type in Openable }
    fun title(name: String) = view(name)?.obj("attributes")?.str("title")

    val topSongs = view("top-songs")?.get("data")?.jsonArray.orEmpty().mapNotNull { e ->
        val o = e.jsonObject
        val item = o.toHomeItem() ?: return@mapNotNull null
        val sa = o.obj("attributes")
        // Debajo del título: álbum y año, como Apple Music.
        val album = sa?.str("albumName")
        val year = sa?.str("releaseDate")?.take(4)
        item.copy(subtitle = listOfNotNull(album, year).joinToString(" · ").ifBlank { null })
    }
    return ArtistPage(
        id = artist.str("id") ?: catalogId,
        name = a.str("name").orEmpty(),
        artwork = a.obj("artwork")?.toArtwork(),
        heroTall = (editorial?.obj("superHeroTall") ?: editorial?.obj("subscriptionHero"))?.toArtwork(),
        heroWide = (editorial?.obj("superHeroWide") ?: editorial?.obj("subscriptionHero") ?: editorial?.obj("bannerUber"))?.toArtwork(),
        logo = editorial?.obj("musicContentColorLogoTrimmed")?.toArtwork(),
        bio = a.str("artistBio")?.takeIf { it.isNotBlank() },
        origin = a.str("origin")?.takeIf { it.isNotBlank() },
        bornOrFormed = a.str("bornOrFormed")?.takeIf { it.isNotBlank() },
        genres = a["genreNames"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
        latest = items("latest-release", preferNotes = false).firstOrNull(),
        topSongsTitle = title("top-songs"),
        topSongs = topSongs,
        shelves = ShelfViews.mapNotNull { name ->
            val list = items(name)
            if (list.isEmpty()) null else HomeShelf(title(name), ShelfKind.Covers, list)
        },
    )
}
