package cider.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Mensaje crudo del canal `API:Playback`: `{type, data}`. */
data class RawEvent(val type: String, val data: JsonElement)

sealed interface SocketState {
    data object Connecting : SocketState
    data object Connected : SocketState
    data class Disconnected(val cause: Throwable?) : SocketState
}

/**
 * Cliente mínimo de Socket.IO (Engine.IO v4, solo transporte WebSocket).
 *
 * Cider emite los eventos sin pedir token. El flujo se reconecta solo
 * hasta que se cancela quien lo recoge.
 */
class CiderEvents(
    private val client: HttpClient,
    private val endpoint: CiderEndpoint,
    private val json: Json = CiderJson,
) {
    sealed interface Item {
        data class State(val state: SocketState) : Item
        data class Event(val event: RawEvent) : Item
    }

    fun stream(retryDelayMs: Long = 2_000): Flow<Item> = channelFlow {
        while (true) {
            send(Item.State(SocketState.Connecting))
            val cause = runCatching {
                client.webSocket("${endpoint.wsBase}/socket.io/?EIO=4&transport=websocket") {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        when {
                            // Engine.IO OPEN: abrir el namespace por defecto.
                            text.startsWith("0") -> send(Frame.Text("40"))
                            // Engine.IO PING → PONG.
                            text == "2" -> send(Frame.Text("3"))
                            // Socket.IO CONNECT confirmado.
                            text.startsWith("40") -> this@channelFlow.send(Item.State(SocketState.Connected))
                            // Socket.IO EVENT: 42["canal", payload]
                            text.startsWith("42") -> parseEvent(text.substring(2))?.let {
                                this@channelFlow.send(Item.Event(it))
                            }
                        }
                    }
                }
            }.exceptionOrNull()
            send(Item.State(SocketState.Disconnected(cause)))
            delay(retryDelayMs)
        }
    }

    private fun parseEvent(payload: String): RawEvent? = runCatching {
        val array = json.parseToJsonElement(payload) as JsonArray
        if (array.getOrNull(0)?.jsonPrimitive?.content != CHANNEL) return null
        val body = array.getOrNull(1) as? JsonObject ?: return null
        val type = body["type"]?.jsonPrimitive?.content ?: return null
        RawEvent(type, body["data"] ?: JsonNull)
    }.getOrNull()

    companion object {
        const val CHANNEL = "API:Playback"
    }
}
