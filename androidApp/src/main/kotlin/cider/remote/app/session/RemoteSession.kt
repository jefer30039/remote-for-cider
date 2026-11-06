package cider.remote.app.session

import cider.remote.CiderApi
import cider.remote.CiderEvents
import cider.remote.CiderRemote
import cider.remote.RemoteSignal
import cider.remote.Link
import cider.remote.app.data.ConnectionStore
import cider.remote.app.data.PcFinder
import cider.remote.app.data.SavedConnection
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la app: sin conexión guardada, o con un [CiderRemote] vivo. */
sealed interface SessionState {
    data object Loading : SessionState
    data object NotPaired : SessionState
    data class Paired(val connection: SavedConnection, val remote: CiderRemote) : SessionState
}

/**
 * Mantiene un único [CiderRemote] por conexión guardada, compartido por la UI
 * y el servicio de medios. Vive lo que vive el proceso.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteSession(
    private val http: HttpClient,
    private val store: ConnectionStore,
    private val cache: cider.remote.app.data.ContentCache,
    private val finder: PcFinder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _searching = MutableStateFlow(false)
    /** True mientras se busca el PC en la red (la IP pudo cambiar). */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    val state: StateFlow<SessionState> = store.connection
        .distinctUntilChanged()
        .flatMapLatest { connection ->
            if (connection == null) flowOf<SessionState>(SessionState.NotPaired)
            else kotlinx.coroutines.flow.flow<SessionState> {
                val remoteScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val remote = CiderRemote(
                    CiderApi(http, connection.endpoint, connection.token),
                    CiderEvents(http, connection.endpoint),
                    remoteScope,
                )
                remote.start()
                remoteScope.launch {
                    // Token revocado en Cider: volver a emparejar.
                    remote.signals.filterIsInstance<RemoteSignal.Unauthorized>().collect { store.clear() }
                }
                remoteScope.launch {
                    // Sin conexión un rato: puede que el PC haya cambiado de IP. Buscarlo en la
                    // red con el mismo token y, si aparece en otra dirección, reconectar ahí.
                    remote.state.map { it.link is Link.Offline }.distinctUntilChanged().collectLatest { offline ->
                        if (!offline) return@collectLatest
                        delay(4_000)
                        while (true) {
                            if (relocate(connection)) return@collectLatest
                            delay(30_000)
                        }
                    }
                }
                try {
                    emit(SessionState.Paired(connection, remote))
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    remoteScope.cancel()
                }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    val remote: StateFlow<CiderRemote?> = state
        .map { (it as? SessionState.Paired)?.remote }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Busca el PC y, si está en otra IP, la guarda. True si cambió de dirección. */
    private suspend fun relocate(connection: SavedConnection): Boolean {
        if (_searching.value) return false
        _searching.value = true
        try {
            val host = finder.find(connection.endpoint.port, connection.token, preferred = connection.endpoint.host)
            if (host == null || host == connection.endpoint.host) return false
            store.save(connection.copy(endpoint = connection.endpoint.copy(host = host)))
            return true
        } finally {
            _searching.value = false
        }
    }

    /** Buscar el PC ahora (menú ⋮). */
    fun searchNow() {
        val paired = state.value as? SessionState.Paired ?: return
        scope.launch { relocate(paired.connection) }
    }

    /** Cambiar la dirección a mano conservando el token (no hace falta volver a emparejar). */
    fun changeHost(host: String) {
        val paired = state.value as? SessionState.Paired ?: return
        val clean = host.trim()
        if (clean.isEmpty() || clean == paired.connection.endpoint.host) return
        scope.launch { store.save(paired.connection.copy(endpoint = paired.connection.endpoint.copy(host = clean))) }
    }

    fun save(connection: SavedConnection) {
        scope.launch { store.save(connection) }
    }

    fun forget() {
        scope.launch {
            (state.value as? SessionState.Paired)?.let { cache.clear(it.connection.endpoint.host) }
            store.clear()
        }
    }
}
