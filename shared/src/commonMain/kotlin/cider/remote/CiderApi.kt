package cider.remote

import cider.remote.model.AmItem
import cider.remote.model.AnimatedCover
import cider.remote.model.AmList
import cider.remote.model.ApiError
import cider.remote.model.AudioQuality
import cider.remote.model.ClientInfo
import cider.remote.model.Envelope
import cider.remote.model.Items
import cider.remote.model.LibraryAlbum
import cider.remote.model.LibraryArtist
import cider.remote.model.LibraryPlaylist
import cider.remote.model.LibrarySong
import cider.remote.model.NowPlayingStatus
import cider.remote.model.PlaybackSnapshot
import cider.remote.model.QueuePage
import cider.remote.model.QueuePosition
import cider.remote.model.RepeatMode
import cider.remote.model.SearchCategory
import cider.remote.model.SearchEnvelope
import cider.remote.model.SearchResults
import cider.remote.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CiderException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception("$status $code: $message") {
    val isUnauthorized: Boolean get() = code == "UNAUTHORIZED_APP_TOKEN"
}

/** Página de resultados: elementos + total para seguir paginando. */
data class Page<T>(val items: List<T>, val offset: Int, val total: Int) {
    val hasMore: Boolean get() = offset + items.size < total
}

/** Cliente REST de `/api/v2`. Ver docs/api-v2.md. */
const val COMPANION_PORT = 10769

@kotlinx.serialization.Serializable
data class SpeakerMode(val bass: Boolean, val preset: String = "", val available: Boolean = false)

class CiderApi(
    private val client: HttpClient,
    val endpoint: CiderEndpoint,
    private val token: String,
) {
    private val base = "${endpoint.httpBase}/api/v2"

    // --- Cliente ---
    suspend fun clientInfo(): ClientInfo = get("/client/info")

    // --- Reproducción ---
    suspend fun playback(): PlaybackSnapshot = get("/playback")
    suspend fun nowPlaying(): Track = get("/playback/now-playing")
    suspend fun audioQuality(): AudioQuality = get("/playback/audio-quality")
    suspend fun play() = command(HttpMethod.Post, "/playback/play")
    suspend fun pause() = command(HttpMethod.Post, "/playback/pause")
    suspend fun toggle() = command(HttpMethod.Post, "/playback/toggle")
    suspend fun next() = command(HttpMethod.Post, "/playback/next")

    /** Con [force] salta a la anterior aunque la canción vaya avanzada. */
    suspend fun previous(force: Boolean = false) =
        command(HttpMethod.Post, "/playback/previous", buildJsonObject { if (force) put("force", true) })

    suspend fun seek(seconds: Double) =
        command(HttpMethod.Post, "/playback/seek", buildJsonObject { put("position", seconds.coerceAtLeast(0.0)) })

    suspend fun setRepeat(mode: RepeatMode) =
        command(HttpMethod.Put, "/playback/repeat", buildJsonObject { put("mode", mode.wire) })

    suspend fun setShuffle(enabled: Boolean) =
        command(HttpMethod.Put, "/playback/shuffle", buildJsonObject { put("enabled", enabled) })

    suspend fun setAutoplay(enabled: Boolean) =
        command(HttpMethod.Put, "/playback/autoplay", buildJsonObject { put("enabled", enabled) })

    /** [type] estilo Apple Music: `songs`, `albums`, `playlists`, `library-songs`… */
    suspend fun playItem(type: String, id: String) =
        command(HttpMethod.Post, "/playback/play-item", typeId(type, id))

    suspend fun playCollection(type: String, id: String, shuffle: Boolean? = null) =
        command(HttpMethod.Post, "/playback/play-collection", buildJsonObject {
            put("type", type)
            put("id", id)
            if (shuffle != null) put("shuffle", shuffle)
        })

    // --- Cola ---
    suspend fun queue(offset: Int = 0, limit: Int = 50): Pair<QueuePage, Int> {
        val env: Envelope<QueuePage> = request(HttpMethod.Get, "/queue") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.body()
        return env.data to (env.meta?.total ?: env.data.items.size)
    }

    suspend fun queuePosition(): QueuePosition = get("/queue/position")
    suspend fun queueJump(index: Int) = command(HttpMethod.Post, "/queue/jump", buildJsonObject { put("index", index) })
    suspend fun queueAddNext(type: String, id: String) = command(HttpMethod.Post, "/queue/add-next", typeId(type, id))
    suspend fun queueAddLater(type: String, id: String) = command(HttpMethod.Post, "/queue/add-later", typeId(type, id))
    suspend fun queueMove(from: Int, to: Int) = command(HttpMethod.Post, "/queue/move", buildJsonObject {
        put("from", from)
        put("to", to)
    })
    suspend fun queueRemove(index: Int) = command(HttpMethod.Delete, "/queue/items/$index")
    suspend fun queueClear() = command(HttpMethod.Delete, "/queue")

    // --- Playlists (rutas propias de Cider, con permiso library) ---
    /**
     * Añade canciones al final de una playlist de la biblioteca. Las del catálogo se
     * importan antes a la biblioteca (`allowLibraryImport`). Límite de Cider: 10 escrituras/min.
     */
    suspend fun playlistAddTracks(playlistId: String, tracks: List<Pair<String, String>>) =
        command(HttpMethod.Post, "/playlists/$playlistId/tracks", buildJsonObject {
            put("allowLibraryImport", true)
            putJsonArray("tracks") { tracks.forEach { (type, id) -> addJsonObject { put("type", type); put("id", id) } } }
        })

    // --- Audio ---
    suspend fun volume(): Double = get<JsonObject>("/audio/volume")["volume"]!!.jsonPrimitive.content.toDouble()
    suspend fun setVolume(volume: Double) =
        command(HttpMethod.Patch, "/audio/volume", buildJsonObject { put("volume", volume.coerceIn(0.0, 1.0)) })

    // --- Biblioteca ---
    suspend fun nowPlayingStatus(): NowPlayingStatus = get("/library/now-playing/status")
    suspend fun addNowPlayingToLibrary() = command(HttpMethod.Post, "/library/now-playing/add")
    suspend fun removeNowPlayingFromLibrary() = command(HttpMethod.Delete, "/library/now-playing")
    suspend fun love() = command(HttpMethod.Post, "/library/now-playing/love")
    suspend fun dislike() = command(HttpMethod.Post, "/library/now-playing/dislike")
    suspend fun clearRating() = command(HttpMethod.Delete, "/library/now-playing/rating")

    suspend fun playlists(offset: Int = 0, limit: Int = 50): Page<LibraryPlaylist> = page("/library/playlists", offset, limit)
    suspend fun playlistSongs(id: String, offset: Int = 0, limit: Int = 50): Page<LibrarySong> =
        page("/library/playlists/$id/songs", offset, limit)
    suspend fun albums(offset: Int = 0, limit: Int = 50): Page<LibraryAlbum> = page("/library/albums", offset, limit)
    suspend fun albumSongs(id: String, offset: Int = 0, limit: Int = 50): Page<LibrarySong> =
        page("/library/albums/$id/songs", offset, limit)
    suspend fun artists(offset: Int = 0, limit: Int = 50): Page<LibraryArtist> = page("/library/artists", offset, limit)
    suspend fun artistAlbums(id: String, offset: Int = 0, limit: Int = 50): Page<LibraryAlbum> =
        page("/library/artists/$id/albums", offset, limit)
    suspend fun songs(offset: Int = 0, limit: Int = 50): Page<LibrarySong> = page("/library/songs", offset, limit)

    // --- Letras (proxy de Apple Music, scope library) ---
    /** [catalogId] = `playParams.id` de la pista. Devuelve null si no tiene letra. */
    suspend fun lyrics(catalogId: String, syllable: Boolean = false): Lyrics? {
        val kind = if (syllable) "syllable-lyrics" else "lyrics"
        val response = try {
            request(HttpMethod.Get, "/amapi/raw/v1/catalog/\$STOREFRONT/songs/$catalogId/$kind")
        } catch (e: CiderException) {
            if (e.code == "AMAPI_ERROR" && e.status == 404) return null
            throw e
        }
        val root = CiderJson.parseToJsonElement(response.bodyAsText()).jsonObject
        val ttml = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("attributes")?.jsonObject?.get("ttml")?.jsonPrimitive?.content
            ?: return null
        return TtmlParser.parse(ttml)
    }

    // --- Apple Music (proxy, scope library) ---
    /** Álbumes y playlists añadidos hace poco a la biblioteca. */
    suspend fun recentlyAdded(limit: Int = 10): List<AmItem> =
        amapiRaw("v1/me/library/recently-added?limit=${limit.coerceIn(1, 10)}")

    /** Elementos fijados ("pins") de la biblioteca, con la foto del catálogo para artistas. */
    suspend fun libraryPins(): List<AmItem> = amapiRaw("v1/me/library/pins?include=catalog")

    /** Artista con foto: de la biblioteca (`r.…`, foto del catálogo) o del catálogo. */
    suspend fun artistInfo(id: String, catalog: Boolean): AmItem? =
        if (catalog) amapiRaw("v1/catalog/\$STOREFRONT/artists/$id").firstOrNull()
        else amapiRaw("v1/me/library/artists/$id?include=catalog").firstOrNull()

    /** Artistas de la biblioteca con foto del catálogo (orden alfabético, máx. 100 por página). */
    suspend fun libraryArtistsWithArt(offset: Int, limit: Int = 100): List<AmItem> =
        amapiRaw("v1/me/library/artists?include=catalog&limit=${limit.coerceIn(1, 100)}&offset=$offset")

    /**
     * Portada animada de un álbum o playlist. [kind] como en las rutas: `albums`,
     * `playlists`, `library-albums`, `library-playlists`. Null si no tiene.
     */
    suspend fun animatedCover(kind: String, id: String): AnimatedCover? {
        val path = if (kind.startsWith("library-")) "v1/me/library/${kind.removePrefix("library-")}/$id/catalog?extend=editorialVideo"
        else "v1/catalog/\$STOREFRONT/$kind/$id?extend=editorialVideo"
        return editorialVideo(path)
    }

    /** Portada animada del álbum de una canción del catálogo. */
    suspend fun animatedCoverForSong(catalogSongId: String): AnimatedCover? =
        editorialVideo("v1/catalog/\$STOREFRONT/songs/$catalogSongId/albums?extend=editorialVideo")

    private suspend fun editorialVideo(path: String): AnimatedCover? {
        val root = CiderJson.parseToJsonElement(request(HttpMethod.Get, "/amapi/raw/$path").bodyAsText()).jsonObject
        val attributes = root["data"]?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("attributes")?.jsonObject ?: return null
        val video = attributes["editorialVideo"]?.jsonObject
        fun url(vararg keys: String) = keys.firstNotNullOfOrNull {
            video?.get(it)?.jsonObject?.get("video")?.jsonPrimitive?.content
        }
        val cover = AnimatedCover(
            square = url("motionDetailSquare", "motionSquareVideo1x1"),
            tall = url("motionDetailTall", "motionTallVideo3x4"),
            bgColor = attributes["artwork"]?.jsonObject?.get("bgColor")?.jsonPrimitive?.content,
        )
        return cover.takeIf { it.square != null || it.tall != null || it.bgColor != null }
    }

    /** Categorías de la portada de búsqueda (las de Apple Music: Radio, Dance, Éxitos…). */
    suspend fun searchCategories(): List<SearchCategory> {
        val path = "v1/recommendations/\$STOREFRONT?extend=editorialArtwork" +
            "&types=editorial-items,apple-curators,activities&name=search-landing"
        val root = CiderJson.parseToJsonElement(request(HttpMethod.Get, "/amapi/raw/$path").bodyAsText()).jsonObject
        val groups = root["data"]?.jsonObject?.get("data")?.jsonArray.orEmpty()
        return groups.flatMap { g ->
            g.jsonObject["relationships"]?.jsonObject?.get("contents")?.jsonObject?.get("data")?.jsonArray.orEmpty()
        }.mapNotNull { item ->
            val o = item.jsonObject
            if (o["type"]?.jsonPrimitive?.content != "apple-curators") return@mapNotNull null
            val a = o["attributes"]?.jsonObject ?: return@mapNotNull null
            val cover = a["editorialArtwork"]?.jsonObject?.get("subscriptionCover")?.jsonObject
            SearchCategory(
                id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = (a["shortName"] ?: a["name"])?.jsonPrimitive?.content ?: return@mapNotNull null,
                imageUrl = cover?.get("url")?.jsonPrimitive?.content
                    ?.replace("{w}", "1520")?.replace("{h}", "380")?.replace("{f}", "jpg"),
                bgColor = cover?.get("bgColor")?.jsonPrimitive?.content,
            )
        }.distinctBy { it.id }
    }

    /** Playlists de una categoría (curador de Apple Music). */
    suspend fun curatorPlaylists(id: String): List<AmItem> =
        amapiRaw("v1/catalog/\$STOREFRONT/apple-curators/$id/playlists?limit=50")

    /** Álbumes de un artista del catálogo. */
    suspend fun catalogArtistAlbums(artistId: String): List<AmItem> =
        amapiRaw("v1/catalog/\$STOREFRONT/artists/$artistId/albums?limit=50")

    /** Canciones de un álbum o playlist del catálogo (`albums` / `playlists`). */
    suspend fun catalogTracks(type: String, id: String): List<AmItem> =
        amapiRaw("v1/catalog/\$STOREFRONT/$type/$id/tracks?limit=100")

    suspend fun search(term: String, limit: Int = 10): SearchResults {
        val env: Envelope<SearchEnvelope> = request(HttpMethod.Get, "/amapi/search") {
            parameter("term", term)
            parameter("types", "songs,albums,artists,playlists")
            parameter("limit", limit.coerceIn(1, 25))
        }.body()
        return env.data.results
    }

    /** Respuesta cruda del proxy de Apple Music, para formas que no encajan en [AmItem]. */
    suspend fun rawGet(path: String): String = request(HttpMethod.Get, "/amapi/raw/$path").bodyAsText()

    private suspend fun amapiRaw(path: String): List<AmItem> =
        request(HttpMethod.Get, "/amapi/raw/$path").body<Envelope<AmList>>().data.data

    // --- Servicio auxiliar del PC (puente/companion.py, puerto 10769) ---
    /** Modo bocina de EasyEffects: presets "Bocina" (graves) y "Normal". Null si el servicio no está. */
    suspend fun speakerMode(): SpeakerMode? = runCatching {
        companion(HttpMethod.Get).body<SpeakerMode>()
    }.getOrNull()

    suspend fun setSpeakerBass(on: Boolean): SpeakerMode =
        companion(HttpMethod.Post, buildJsonObject { put("bass", on) }).body()

    private suspend fun companion(method: HttpMethod, body: JsonObject? = null): HttpResponse {
        val response = client.request("http://${endpoint.host}:$COMPANION_PORT/easyeffects") {
            this.method = method
            header("apptoken", token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            // Cada orden arranca la CLI de EasyEffects por Flatpak: tarda 1-2 s.
            timeout { requestTimeoutMillis = 30_000 }
        }
        if (!response.status.isSuccess()) throw response.toCiderException()
        return response
    }

    // --- Internos ---
    private suspend inline fun <reified T> get(path: String): T =
        request(HttpMethod.Get, path).body<Envelope<T>>().data

    private suspend inline fun <reified T> page(path: String, offset: Int, limit: Int): Page<T> {
        val env: Envelope<Items<T>> = request(HttpMethod.Get, path) {
            parameter("offset", offset)
            parameter("limit", limit)
        }.body()
        return Page(env.data.items, offset, env.meta?.total ?: env.data.items.size)
    }

    private suspend fun command(method: HttpMethod, path: String, body: JsonObject? = null) {
        request(method, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private suspend fun request(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val response = client.request(base + path) {
            this.method = method
            header("apptoken", token)
            block()
        }
        if (!response.status.isSuccess()) throw response.toCiderException()
        return response
    }

    private fun typeId(type: String, id: String) = buildJsonObject {
        put("type", type)
        put("id", id)
    }
}

internal val RepeatMode.wire: String
    get() = when (this) {
        RepeatMode.None -> "none"
        RepeatMode.One -> "one"
        RepeatMode.All -> "all"
    }

/** Cider usa dos formatos: `{error:{code,message}}` y `{error:"CODIGO"}`. */
internal suspend fun HttpResponse.toCiderException(): CiderException {
    val text = runCatching { bodyAsText() }.getOrDefault("")
    val error = runCatching { CiderJson.parseToJsonElement(text).jsonObject["error"] }.getOrNull()
    return when (error) {
        is JsonPrimitive -> CiderException(status.value, error.content, error.content)
        is JsonObject -> {
            val e = CiderJson.decodeFromJsonElement(ApiError.serializer(), error)
            CiderException(status.value, e.code, e.message)
        }
        else -> CiderException(status.value, "HTTP_${status.value}", text.take(200))
    }
}
