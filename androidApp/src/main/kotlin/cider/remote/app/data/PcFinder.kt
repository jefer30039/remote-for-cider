package cider.remote.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Busca el PC con Cider en la red del dispositivo, probando el puerto del puente
 * en cada dirección de la subred. Así la app sigue funcionando si cambia la IP del PC.
 */
class PcFinder(private val context: Context, private val http: HttpClient) {

    /**
     * Con [token], solo vale el Cider que acepte ese token (el emparejado).
     * Sin token, cualquier Cider que responda "falta token" (para emparejar).
     * Devuelve la IP o null si no aparece en [budgetMs].
     */
    suspend fun find(port: Int, token: String?, preferred: String? = null, budgetMs: Long = 8_000): String? {
        val candidates = candidates(preferred)
        if (candidates.isEmpty()) return null
        val found = CompletableDeferred<String>()
        withTimeoutOrNull(budgetMs) {
            coroutineScope {
                val gate = Semaphore(48)
                val jobs = candidates.map { ip ->
                    launch {
                        gate.withPermit {
                            if (!found.isCompleted && isCider(ip, port, token)) found.complete(ip)
                        }
                    }
                }
                launch {
                    jobs.forEach { it.join() }
                    found.complete("")
                }
                found.await()
                // Encontrado (o agotado): cortar las sondas que sigan en marcha.
                coroutineContext.cancelChildren()
            }
        }
        return found.takeIf { it.isCompleted }?.getCompleted()?.ifEmpty { null }
    }

    private suspend fun isCider(ip: String, port: Int, token: String?): Boolean = runCatching {
        val response = http.get("http://$ip:$port/api/v2/client/info") {
            if (token != null) header("apptoken", token)
            timeout { connectTimeoutMillis = 600; requestTimeoutMillis = 1_200; socketTimeoutMillis = 1_200 }
        }
        val body = response.bodyAsText()
        if (token != null) response.status.value == 200 && "framework" in body
        else response.status.value == 403 && "UNAUTHORIZED_APP_TOKEN" in body
    }.getOrDefault(false)

    /**
     * Direcciones de las redes locales del dispositivo, primero [preferred]:
     * la Wi-Fi/Ethernet activa y también las que comparte, como su **hotspot**
     * (el PC conectado al hotspot del teléfono es un caso normal; entonces la red
     * activa del teléfono son los datos móviles). Se excluyen datos móviles y VPN.
     * Subredes mayores que /22 se limitan a la /24 propia para no tardar demasiado.
     */
    private fun candidates(preferred: String?): List<String> {
        val subnets = mutableListOf<Pair<Long, Int>>() // (dirección propia, prefijo)
        // Red activa, si es Wi-Fi o Ethernet.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        ) {
            cm.getLinkProperties(network)?.linkAddresses?.filter { it.address is Inet4Address }
                ?.forEach { subnets += it.address.toLong() to it.prefixLength }
        }
        // Resto de interfaces locales: hotspot (wlan*, ap*, swlan*), anclaje USB/Bluetooth…
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback && LOCAL_IFACE.matches(it.name) }
                .flatMap { it.interfaceAddresses }
                .filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
                .forEach { subnets += it.address.toLong() to it.networkPrefixLength.toInt() }
        }
        val hosts = subnets.distinct().flatMap { (own, prefixLength) ->
            val prefix = prefixLength.coerceIn(22, 30)
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val net = own and mask
            val size = 1L shl (32 - prefix)
            (1 until size - 1).map { net + it }.filter { it != own }
                // Primero las más cercanas a la propia: el PC suele estar cerca.
                .sortedBy { kotlin.math.abs(it - own) }
        }.map { ip -> listOf(24, 16, 8, 0).joinToString(".") { ((ip shr it) and 0xFF).toString() } }
        return (listOfNotNull(preferred) + hosts).distinct()
    }

    private fun java.net.InetAddress.toLong(): Long =
        address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }

    private companion object {
        /** Wi-Fi, hotspot, Ethernet y anclaje USB/Bluetooth; no datos móviles (rmnet, ccmni) ni VPN (tun). */
        val LOCAL_IFACE = Regex("""^(wlan|swlan|ap|softap|eth|rndis|usb|ncm|bt-pan|bnep).*""")
    }
}
