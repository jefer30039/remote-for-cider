package cider.remote

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Punto de entrada para Swift: arma el cliente HTTP (motor Darwin), [CiderApi], [CiderEvents] y
 * [CiderRemote] con su propio ámbito de corrutinas. Swift no puede crear un `CoroutineScope`, así
 * que la vida del remoto va ligada a esta clase: [close] lo para todo.
 */
class CiderSession(host: String, port: Int, token: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http: HttpClient = ciderHttpClient()

    val endpoint = CiderEndpoint(host, port)
    val api = CiderApi(http, endpoint, token)
    val remote = CiderRemote(api, CiderEvents(http, endpoint), scope)

    fun start() = remote.start()

    fun close() {
        remote.stop()
        scope.cancel()
        http.close()
    }
}

/** Emparejamiento sin sesión (aún no hay token). */
class CiderPairingClient {
    private val http: HttpClient = ciderHttpClient()

    suspend fun request(host: String, port: Int, appName: String): PairingResult =
        CiderPairing(http).request(
            CiderEndpoint(host, port), appName,
            setOf(Scope.Playback, Scope.Queue, Scope.Library, Scope.Audio),  // los mismos que Android
        )

    fun close() = http.close()
}
