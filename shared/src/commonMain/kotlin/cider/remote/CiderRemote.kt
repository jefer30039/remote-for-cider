package cider.remote

import cider.remote.model.PlaybackSnapshot
import cider.remote.model.PlaybackTimeEvent
import cider.remote.model.RepeatMode
import cider.remote.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeSource

/** Posición conocida en un instante; se interpola mientras suena. */
data class Progress(
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val playing: Boolean = false,
    val rate: Double = 1.0,
    val sampledAt: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
) {
    fun positionNow(): Double {
        if (!playing) return positionSec
        val elapsed = sampledAt.elapsedNow().inWholeMilliseconds / 1000.0
        val p = positionSec + elapsed * rate
        return if (durationSec > 0) p.coerceAtMost(durationSec) else p
    }
}

sealed interface Link {
    data object Connecting : Link
    data object Online : Link
    data class Offline(val reason: String?) : Link
}

data class PlayerState(
    val link: Link = Link.Connecting,
    val track: Track? = null,
    val progress: Progress = Progress(),
    val volume: Double = 1.0,
    val repeat: RepeatMode = RepeatMode.None,
    val shuffle: Boolean = false,
    val autoplay: Boolean = false,
    val inLibrary: Boolean? = null,
    val inFavorites: Boolean? = null,
    val queuePosition: Int = 0,
    val queueTotal: Int = 0,
) {
    val isPlaying: Boolean get() = progress.playing
}

/** Señales para que la UI recargue datos (p. ej. la cola). */
sealed interface RemoteSignal {
    data object QueueChanged : RemoteSignal
    data object TrackChanged : RemoteSignal
    data class Unauthorized(val error: CiderException) : RemoteSignal
}

/**
 * Estado en vivo de un Cider: snapshot REST al conectar + eventos del socket.
 * Los comandos actualizan el estado de forma optimista; el socket lo corrige.
 */
class CiderRemote(
    val api: CiderApi,
    private val events: CiderEvents,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _signals = MutableSharedFlow<RemoteSignal>(extraBufferCapacity = 8)
    val signals: SharedFlow<RemoteSignal> = _signals.asSharedFlow()

    private val volumeRequests = Channel<Double>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                // El último valor gana: el slider puede mandar muchos seguidos.
                volumeRequests.consumeAsFlow().collect { v -> runCommand { api.setVolume(v) } }
            }
            events.stream().collect { item ->
                when (item) {
                    is CiderEvents.Item.State -> onSocketState(item.state)
                    is CiderEvents.Item.Event -> onEvent(item.event)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Vuelve a pedir el snapshot completo. */
    fun refresh() {
        scope.launch { loadSnapshot() }
    }

    // --- Comandos ---
    fun togglePlay() {
        val playing = !_state.value.isPlaying
        _state.update { it.copy(progress = it.progress.rebased(playing = playing)) }
        launchCommand { if (playing) api.play() else api.pause() }
    }

    fun next() = launchCommand { api.next() }
    fun previous() = launchCommand { api.previous() }

    fun seek(seconds: Double) {
        _state.update { it.copy(progress = it.progress.copy(positionSec = seconds, sampledAt = TimeSource.Monotonic.markNow())) }
        launchCommand { api.seek(seconds) }
    }

    fun setVolume(volume: Double) {
        _state.update { it.copy(volume = volume) }
        volumeRequests.trySend(volume)
    }

    fun cycleRepeat() = setRepeat(
        when (_state.value.repeat) {
            RepeatMode.None -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.None
        },
    )

    fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
        launchCommand { api.setRepeat(mode) }
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffle
        _state.update { it.copy(shuffle = next) }
        launchCommand { api.setShuffle(next) }
    }

    fun toggleInLibrary() {
        val inLibrary = _state.value.inLibrary ?: return
        _state.update { it.copy(inLibrary = !inLibrary) }
        launchCommand { if (inLibrary) api.removeNowPlayingFromLibrary() else api.addNowPlayingToLibrary() }
    }

    fun toggleLove() {
        val loved = _state.value.inFavorites == true
        _state.update { it.copy(inFavorites = !loved) }
        launchCommand { if (loved) api.clearRating() else api.love() }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        scope.launch { runCommand(block) }
    }

    private suspend fun runCommand(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CiderException) {
            if (e.isUnauthorized) _signals.emit(RemoteSignal.Unauthorized(e))
            loadSnapshot()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Sin red: el socket marcará Offline y al volver se resincroniza.
        }
    }

    // --- Entrada ---
    private suspend fun onSocketState(socket: SocketState) {
        when (socket) {
            SocketState.Connecting -> _state.update {
                if (it.link is Link.Online) it else it.copy(link = Link.Connecting)
            }
            SocketState.Connected -> {
                _state.update { it.copy(link = Link.Online) }
                loadSnapshot()
            }
            is SocketState.Disconnected -> _state.update {
                it.copy(
                    link = Link.Offline(socket.cause?.message),
                    progress = it.progress.rebased(playing = false),
                )
            }
        }
    }

    private suspend fun loadSnapshot() {
        try {
            val snap = api.playback()
            val status = runCatching { api.nowPlayingStatus() }.getOrNull()
            val queue = runCatching { api.queuePosition() }.getOrNull()
            _state.update { it.applySnapshot(snap).copy(
                inLibrary = status?.inLibrary ?: snap.nowPlaying?.inLibrary,
                queuePosition = queue?.position ?: it.queuePosition,
                queueTotal = queue?.total ?: it.queueTotal,
            ) }
        } catch (e: CiderException) {
            if (e.isUnauthorized) _signals.emit(RemoteSignal.Unauthorized(e))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private suspend fun onEvent(event: RawEvent) {
        val data = event.data
        when (event.type) {
            "playbackStatus.playbackTimeDidChange" -> {
                val t = CiderJson.decodeFromJsonElement<PlaybackTimeEvent>(data)
                _state.update {
                    it.copy(progress = Progress(
                        positionSec = t.currentPlaybackTime,
                        durationSec = t.currentPlaybackDuration,
                        playing = t.isPlaying,
                        rate = t.anchor?.rate ?: it.progress.rate,
                    ))
                }
            }
            "playbackStatus.nowPlayingItemDidChange" -> {
                val track = CiderJson.decodeFromJsonElement<Track>(data)
                _state.update {
                    it.copy(
                        track = track,
                        inLibrary = track.inLibrary,
                        inFavorites = (data as? JsonObject)?.get("inFavorites")?.jsonPrimitive?.booleanOrNull,
                        progress = Progress(durationSec = track.durationInMillis / 1000.0, playing = it.isPlaying),
                    )
                }
                _signals.emit(RemoteSignal.TrackChanged)
            }
            "playbackStatus.playbackStateDidChange" -> {
                val obj = data as? JsonObject ?: return
                val playing = obj["state"]?.jsonPrimitive?.content == "playing"
                val anchor = obj["anchor"] as? JsonObject
                val position = anchor?.get("currentTime")?.jsonPrimitive?.doubleOrNull
                _state.update {
                    it.copy(progress = it.progress.rebased(playing = playing).let { p ->
                        if (position != null) p.copy(positionSec = position) else p
                    })
                }
            }
            "playbackStatus.nowPlayingStatusDidChange" -> {
                val obj = data as? JsonObject ?: return
                _state.update {
                    it.copy(
                        inLibrary = obj["inLibrary"]?.jsonPrimitive?.booleanOrNull ?: it.inLibrary,
                        inFavorites = obj["inFavorites"]?.jsonPrimitive?.booleanOrNull ?: it.inFavorites,
                    )
                }
            }
            "playerStatus.volumeDidChange" ->
                (data as? JsonPrimitive)?.doubleOrNull?.let { v -> _state.update { it.copy(volume = v) } }
            "playerStatus.repeatModeDidChange" ->
                (data as? JsonPrimitive)?.intOrNull?.let { m ->
                    _state.update { it.copy(repeat = RepeatMode.entries.getOrElse(m) { RepeatMode.None }) }
                }
            "playerStatus.shuffleModeDidChange" ->
                (data as? JsonPrimitive)?.intOrNull?.let { m -> _state.update { it.copy(shuffle = m == 1) } }
            "queueStatus.queueChanged" -> {
                val obj = data as? JsonObject ?: return
                _state.update {
                    it.copy(
                        queuePosition = obj["position"]?.jsonPrimitive?.intOrNull ?: it.queuePosition,
                        queueTotal = obj["total"]?.jsonPrimitive?.intOrNull ?: it.queueTotal,
                    )
                }
                _signals.emit(RemoteSignal.QueueChanged)
            }
        }
    }
}

private fun Progress.rebased(playing: Boolean) =
    copy(positionSec = positionNow(), playing = playing, sampledAt = TimeSource.Monotonic.markNow())

private fun PlayerState.applySnapshot(s: PlaybackSnapshot) = copy(
    track = s.nowPlaying,
    progress = Progress(
        positionSec = s.time?.currentTime ?: 0.0,
        durationSec = s.time?.duration ?: ((s.nowPlaying?.durationInMillis ?: 0) / 1000.0),
        playing = s.isPlaying,
        rate = s.rate,
    ),
    volume = s.volume,
    repeat = s.repeatMode,
    shuffle = s.shuffleMode,
    autoplay = s.autoplay,
)
