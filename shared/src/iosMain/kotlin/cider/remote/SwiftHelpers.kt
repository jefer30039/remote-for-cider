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
@Throws(Throwable::class)
suspend fun CiderApi.automixEnabled(): Boolean? =
    runCatching { audioSetting("automix")["enabled"]?.jsonPrimitive?.content == "true" }.getOrNull()

@Throws(Throwable::class)
suspend fun CiderApi.setAutomix(enabled: Boolean) =
    setAudioSetting("automix", buildJsonObject { put("enabled", enabled) })

/** Ajuste de audio con un solo campo booleano (`crossfade`, `atmos`…). */
@Throws(Throwable::class)
suspend fun CiderApi.setAudioFlag(name: String, field: String, value: Boolean) =
    setAudioSetting(name, buildJsonObject { put(field, value) })

/** Campo de un ajuste de audio como texto (`"true"`, `"5"`, `"binaural"`…), o null. */
@Throws(Throwable::class)
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

@Throws(Throwable::class)
suspend fun CiderApi.addToPlaylist(playlistId: String, items: List<PlayRequest>) =
    playlistAddTracks(playlistId, items.map { it.type to it.id })

/** `repeat` es palabra reservada en Swift. */
val PlayerState.repeatMode: cider.remote.model.RepeatMode get() = repeat

/** Ajustes de audio del menú del equipo (null = Cider no lo da). */
data class AudioState(
    val crossfade: Boolean?,
    val crossfadeSeconds: Int,
    /** Atmos suena si `enabled` y la preferencia del dispositivo no es `off`. */
    val atmos: Boolean?,
    /** Modo de Atmos del dispositivo para volver a él al encenderlo (`binaural` en un PC estéreo). */
    val atmosMode: String,
    /** `off`, `game` o `antifatigue`; null si no está disponible. */
    val listeningMode: String?,
)

@Throws(Throwable::class)
suspend fun CiderApi.audioState(): AudioState {
    val c = runCatching { audioSetting("crossfade") }.getOrNull()
    val a = runCatching { audioSetting("atmos") }.getOrNull()
    val pref = (a?.get("devicePreferences") as? kotlinx.serialization.json.JsonObject)?.get("default")?.jsonPrimitive?.content
    val l = runCatching { audioSetting("listening-mode") }.getOrNull()
    return AudioState(
        crossfade = c?.get("enabled")?.jsonPrimitive?.content?.let { it == "true" },
        crossfadeSeconds = c?.get("durationSec")?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 5,
        atmos = a?.let { it["enabled"]?.jsonPrimitive?.content == "true" && pref != "off" },
        atmosMode = pref?.takeIf { it != "off" } ?: "binaural",
        listeningMode = l?.takeIf { it["available"]?.jsonPrimitive?.content != "false" }?.get("mode")?.jsonPrimitive?.content,
    )
}

@Throws(Throwable::class)
suspend fun CiderApi.setCrossfade(enabled: Boolean) = setAudioSetting("crossfade", buildJsonObject { put("enabled", enabled) })

@Throws(Throwable::class)
suspend fun CiderApi.setCrossfadeSeconds(seconds: Int) = setAudioSetting("crossfade", buildJsonObject { put("durationSec", seconds) })

/** Cider lo aplica desde la siguiente canción. */
@Throws(Throwable::class)
suspend fun CiderApi.setAtmos(on: Boolean, mode: String) = setAudioSetting("atmos", buildJsonObject {
    if (on) put("enabled", true)
    put("devicePreferences", buildJsonObject { put("default", if (on) mode else "off") })
})

@Throws(Throwable::class)
suspend fun CiderApi.setListeningMode(mode: String) = setAudioSetting("listening-mode", buildJsonObject { put("mode", mode) })
