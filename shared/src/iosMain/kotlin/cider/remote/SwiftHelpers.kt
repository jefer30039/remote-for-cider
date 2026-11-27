package cider.remote

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Atajos para Swift: lo que en Kotlin se escribe con `Pair`, constructores de JSON o funciones de
 * nivel superior de librerías que el framework no exporta. Solo iOS; Android no los usa.
 */

/** Una canción (u otro elemento) que pedir a Cider: tipo de la API (`songs`, `library-songs`…) e id. */
data class PlayRequest(val type: String, val id: String)

/** AutoMix de Cider (`/audio/automix`), o null si no se pudo leer. */
suspend fun CiderApi.automixEnabled(): Boolean? =
    runCatching { audioSetting("automix")["enabled"]?.jsonPrimitive?.content == "true" }.getOrNull()

suspend fun CiderApi.setAutomix(enabled: Boolean) =
    setAudioSetting("automix", buildJsonObject { put("enabled", enabled) })

/** Ajuste de audio con un solo campo booleano (`crossfade`, `atmos`…). */
suspend fun CiderApi.setAudioFlag(name: String, field: String, value: Boolean) =
    setAudioSetting(name, buildJsonObject { put(field, value) })

/** Campo de un ajuste de audio como texto (`"true"`, `"5"`, `"binaural"`…), o null. */
suspend fun CiderApi.audioField(name: String, field: String): String? =
    runCatching { audioSetting(name)[field]?.jsonPrimitive?.content }.getOrNull()

/**
 * Reproduce [start] y encola detrás las siguientes de [items] en orden (ver
 * [CiderRemote.playThenQueue]). Cider no puede empezar un álbum en la canción N.
 */
fun CiderRemote.playFrom(items: List<PlayRequest>, start: Int) {
    val first = items.getOrNull(start) ?: return
    playThenQueue(first.type, first.id, items.drop(start + 1).take(100).map { it.type to it.id })
}

suspend fun CiderApi.addToPlaylist(playlistId: String, items: List<PlayRequest>) =
    playlistAddTracks(playlistId, items.map { it.type to it.id })

/** `repeat` es palabra reservada en Swift. */
val PlayerState.repeatMode: cider.remote.model.RepeatMode get() = repeat

/**
 * Playlists de una categoría de Buscar (curador de Apple Music). Apple responde 400 con `limit` > 25
 * (`curatorPlaylists` de common pide 50): mientras se corrige allí, iOS usa esta.
 */
suspend fun CiderApi.curatorPlaylistsPage(id: String): List<cider.remote.model.AmItem> =
    CiderJson.decodeFromString(
        cider.remote.model.Envelope.serializer(cider.remote.model.AmList.serializer()),
        rawGet("v1/catalog/\$STOREFRONT/apple-curators/$id/playlists?limit=25"),
    ).data.data
