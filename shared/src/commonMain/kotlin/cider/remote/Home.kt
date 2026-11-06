package cider.remote

import cider.remote.model.Artwork
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cómo pinta Apple Music cada estantería del Inicio (`display.kind`). */
@Serializable
enum class ShelfKind {
    /** Tarjetas grandes verticales 3:4 ("Sugerencias destacadas para ti"). */
    Hero,
    /** Banner panorámico a todo el ancho. */
    SuperHero,
    /** Fila de portadas cuadradas ("Escuchado recientemente", "Pop"…). */
    Covers,
    /** Carrusel de tarjetas panorámicas con su etiqueta encima (arriba de Nuevo y Radio). */
    Feature,
    /** Canciones en columnas de cuatro filas ("Canciones nuevas destacadas"). */
    Songs,
}

@Serializable
data class HomeShelf(val title: String?, val kind: ShelfKind, val items: List<HomeItem>)

@Serializable
data class HomeItem(
    val id: String,
    /** Tipo de Apple Music: `albums`, `playlists`, `stations`, `library-playlists`… */
    val type: String,
    val name: String,
    val subtitle: String?,
    /** Motivo de la recomendación: "Novedad", "Hecho por ti"… */
    val reason: String?,
    val artwork: Artwork?,
    val tallArtwork: Artwork?,
    val wideArtwork: Artwork?,
    /** `contentRating == "explicit"`: Apple pone una "E" junto al título. */
    val explicit: Boolean = false,
    /** Portada animada vertical 3:4 (HLS) para las tarjetas grandes del Inicio. */
    val tallVideo: String? = null,
)

/** Inicio personal de Apple Music (`listen-now`), el mismo que muestra su app. */
suspend fun CiderApi.home(timezone: String = "+00:00"): List<HomeShelf> {
    val path = "v1/me/recommendations?name=listen-now&timezone=$timezone&with=friendsMix,library,social" +
        "&extend=editorialArtwork,plainEditorialNotes,editorialVideo&art%5Burl%5D=c,f"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val groups = root["data"]?.jsonObject?.get("data")?.jsonArray.orEmpty()
    return groups.mapNotNull { g ->
        val o = g.jsonObject
        val attrs = o["attributes"]?.jsonObject
        val kind = when (attrs?.obj("display")?.str("kind")) {
            "MusicNotesHeroShelf" -> ShelfKind.Hero
            "MusicSuperHeroShelf" -> ShelfKind.SuperHero
            else -> ShelfKind.Covers
        }
        val items = o.obj("relationships")?.obj("contents")?.get("data")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.toHomeItem() }
        if (items.isEmpty()) null
        else HomeShelf(attrs?.obj("title")?.str("stringForDisplay"), kind, items)
    }
}

/** Pestañas Nuevo (`music`) y Radio (`radio`) de Apple Music: agrupaciones editoriales. */
enum class BrowsePage(val grouping: String) { New("music"), Radio("radio") }

suspend fun CiderApi.browse(page: BrowsePage): List<HomeShelf> {
    val path = "v1/editorial/\$STOREFRONT/groupings?name=${page.grouping}&platform=web&tabs=subscriber" +
        "&extend=editorialArtwork,artistUrl,plainEditorialNotes&art%5Burl%5D=f"
    val root = CiderJson.parseToJsonElement(rawGet(path)).jsonObject
    val grouping = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()
    val elements = grouping.obj("relationships")?.obj("tabs")?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
        ?.obj("relationships")?.obj("children")?.get("data")?.jsonArray.orEmpty()
    return elements.mapNotNull { it.jsonObject.toBrowseShelf() }
}

/** Lo que la app sabe abrir o reproducir; vídeos, curadores y enlaces web se omiten. */
private val Openable = setOf("albums", "playlists", "stations", "songs")

/**
 * Tipos de elemento editorial (`editorialElementKind`) vistos en las agrupaciones:
 * 316 carrusel destacado, 488 emisoras en vivo (se pintan como portadas), 326 fila de
 * portadas, 327 canciones;
 * 322 y 391 son enlaces a otras páginas.
 */
private fun JsonObject.toBrowseShelf(): HomeShelf? {
    val a = obj("attributes") ?: return null
    val title = a.str("name")
    val relationships = obj("relationships")
    val shelf = when (val kind = a.str("editorialElementKind")) {
        "316" -> HomeShelf(title, ShelfKind.Feature, relationships?.obj("children")?.get("data")?.jsonArray.orEmpty().mapNotNull { child ->
            val c = child.jsonObject
            val ca = c.obj("attributes")
            val content = c.obj("relationships")?.obj("contents")?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
            val item = content?.toHomeItem()?.takeIf { it.type in Openable } ?: return@mapNotNull null
            item.copy(
                reason = ca?.str("designBadge") ?: item.reason,
                subtitle = ca?.str("designTag")?.takeIf { it.isNotBlank() } ?: item.subtitle,
                wideArtwork = ca?.obj("artwork")?.toArtwork() ?: item.wideArtwork,
            )
        })
        "326", "327", "488" -> {
            // Bajo las portadas Apple pone el artista o el curador, no la nota editorial.
            val items = relationships?.obj("contents")?.get("data")?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject.toHomeItem(preferNotes = false)?.takeIf { item -> item.type in Openable } }
            HomeShelf(title, if (kind == "327" || items.all { it.type == "songs" }) ShelfKind.Songs else ShelfKind.Covers, items)
        }
        else -> null
    }
    return shelf?.takeIf { it.items.isNotEmpty() }
}

private fun JsonObject.toHomeItem(preferNotes: Boolean = true): HomeItem? {
    val id = str("id") ?: return null
    val type = str("type") ?: return null
    val a = obj("attributes") ?: return null
    val meta = obj("meta")
    val editorial = a.obj("editorialArtwork")
    val notes = a.obj("plainEditorialNotes")
    return HomeItem(
        id = id,
        type = type,
        name = a.str("name") ?: return null,
        // Álbumes: el artista (como Apple Music); playlists y estaciones: la nota editorial corta.
        subtitle = meta?.obj("subtitle")?.str("stringForDisplay")
            ?: if (preferNotes && !type.endsWith("albums")) notes?.str("short") ?: a.str("artistName") ?: a.str("curatorName")
            else a.str("artistName") ?: a.str("curatorName") ?: notes?.str("short"),
        reason = meta?.obj("reason")?.str("stringForDisplay"),
        artwork = a.obj("artwork")?.toArtwork(),
        tallArtwork = (editorial?.obj("superHeroTall") ?: editorial?.obj("staticDetailTall"))?.toArtwork(),
        wideArtwork = (editorial?.obj("superHeroWide") ?: editorial?.obj("subscriptionHero") ?: editorial?.obj("storeFlowcase"))?.toArtwork(),
        explicit = a.str("contentRating") == "explicit",
        // Para la tarjeta 3:4 Apple usa el vídeo "3x4"; el de detalle (álbum) como reserva.
        tallVideo = a.obj("editorialVideo")?.let { v -> (v.obj("motionTallVideo3x4") ?: v.obj("motionDetailTall"))?.str("video") },
    )
}

/** Las carátulas editoriales traen ancho/alto a veces como texto: se leen a mano. */
private fun JsonObject.toArtwork() = Artwork(
    url = str("url"),
    width = str("width")?.toIntOrNull(),
    height = str("height")?.toIntOrNull(),
    bgColor = str("bgColor"),
    textColor1 = str("textColor1"),
    textColor2 = str("textColor2"),
)

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
