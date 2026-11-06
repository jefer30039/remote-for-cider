package cider.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** Permisos que se pueden pedir al emparejar. */
enum class Scope(val wire: String) {
    Playback("playback"),
    Queue("queue"),
    Library("library"),
    Audio("audio"),
    Account("account"),
}

sealed interface PairingResult {
    data class Approved(val token: String, val scopes: List<String>) : PairingResult
    /** El usuario pulsó "denegar". Tras 3 en 5 min, Cider bloquea 15 min. */
    data object Denied : PairingResult
    /** Nadie respondió al diálogo en 2 minutos. */
    data object TimedOut : PairingResult
    /** Ya hay otro diálogo abierto o hay que esperar ([retryAfterSec]). */
    data class TryLater(val code: String, val retryAfterSec: Int?) : PairingResult
    data class Failed(val error: Throwable) : PairingResult
}

/**
 * Pide un token a Cider. Cider muestra un diálogo y la petición queda
 * abierta hasta que el usuario responde (máx. 2 min).
 */
class CiderPairing(private val client: HttpClient) {

    @Serializable
    private data class Request(
        @SerialName("app_name") val appName: String,
        val scopes: List<String>,
    )

    @Serializable
    private data class Granted(val token: String, val scopes: List<String>)

    @Serializable
    private data class Response(val data: Granted)

    suspend fun request(
        endpoint: CiderEndpoint,
        appName: String,
        scopes: Set<Scope>,
    ): PairingResult = try {
        val response = client.post("${endpoint.httpBase}/api/v2/auth/request") {
            contentType(ContentType.Application.Json)
            setBody(Request(appName, scopes.map { it.wire }))
            timeout { requestTimeoutMillis = 150_000; socketTimeoutMillis = 150_000 }
        }
        if (response.status.isSuccess()) {
            val granted = response.body<Response>().data
            PairingResult.Approved(granted.token, granted.scopes)
        } else {
            val e = response.toCiderException()
            when (e.code) {
                "AUTH_REQUEST_DENIED" -> PairingResult.Denied
                "AUTH_REQUEST_TIMEOUT" -> PairingResult.TimedOut
                else -> if (e.status == 409 || e.status == 429) {
                    PairingResult.TryLater(e.code, response.headers["Retry-After"]?.toIntOrNull())
                } else {
                    PairingResult.Failed(e)
                }
            }
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Throwable) {
        PairingResult.Failed(e)
    }
}
