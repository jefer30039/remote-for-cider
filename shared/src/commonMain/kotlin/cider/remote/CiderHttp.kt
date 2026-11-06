package cider.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/** HttpClient con el motor de la plataforma (OkHttp en Android/JVM, Darwin en iOS). */
fun ciderHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(CiderJson) }
    install(WebSockets) { pingIntervalMillis = 20_000 }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
    }
}
