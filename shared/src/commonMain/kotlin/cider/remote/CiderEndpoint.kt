package cider.remote

import kotlinx.serialization.Serializable

/** Dónde escucha Cider (o el puente que lo reenvía). */
@Serializable
data class CiderEndpoint(
    val host: String,
    val port: Int = DEFAULT_PORT,
) {
    val httpBase: String get() = "http://$host:$port"
    val wsBase: String get() = "ws://$host:$port"

    companion object {
        const val DEFAULT_PORT = 10767
    }
}
