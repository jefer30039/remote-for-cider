package cider.remote.app.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import cider.remote.CiderRemote
import cider.remote.Link
import cider.remote.PlayerState
import cider.remote.model.RepeatMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import android.util.Log

/**
 * Player de Media3 que no reproduce nada: refleja el estado de Cider para la
 * notificación y la pantalla de bloqueo, y reenvía los comandos.
 *
 * Truco: la lista tiene siempre [anterior, actual, siguiente] como marcadores,
 * para que Media3 muestre y despache los botones de anterior/siguiente.
 */
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class RemotePlayer(
    looper: Looper,
    remoteFlow: StateFlow<CiderRemote?>,
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remote: CiderRemote? = null
    private var current: PlayerState? = null
    private var startedPlayingAt: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        scope.launch {
            remoteFlow
                .flatMapLatest { r -> r?.state?.map { r to it } ?: flowOf(null) }
                .collect { pair ->
                    val wasPlaying = current?.isPlaying == true
                    remote = pair?.first
                    current = pair?.second
                    if (!wasPlaying && current?.isPlaying == true) startedPlayingAt = TimeSource.Monotonic.markNow()
                    invalidateState()
                }
        }
    }

    override fun getState(): State {
        val s = current
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setDeviceInfo(DEVICE_INFO)
        val track = s?.track
        if (s == null || track == null) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }
        val durationUs = (s.progress.durationSec * 1_000_000).toLong().takeIf { it > 0 } ?: androidx.media3.common.C.TIME_UNSET
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumName)
            .setArtworkUri(track.artwork?.sized(600)?.let(Uri::parse))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        val currentItem = MediaItemData.Builder("actual:${track.playParams?.id ?: track.name}")
            .setMediaItem(MediaItem.Builder().setMediaId(track.playParams?.id ?: track.name).setMediaMetadata(metadata).build())
            .setDurationUs(durationUs)
            .setIsSeekable(true)
            .build()
        val progress = s.progress
        val playing = progress.playing && s.link is Link.Online
        return builder
            .setPlaylist(listOf(placeholder("anterior"), currentItem, placeholder("siguiente")))
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setContentPositionMs { (progress.positionNow() * 1000).toLong() }
            .setRepeatMode(
                when (s.repeat) {
                    RepeatMode.None -> Player.REPEAT_MODE_OFF
                    RepeatMode.One -> Player.REPEAT_MODE_ONE
                    RepeatMode.All -> Player.REPEAT_MODE_ALL
                },
            )
            .setShuffleModeEnabled(s.shuffle)
            .setDeviceVolume((s.volume * MAX_VOLUME).roundToInt())
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val r = remote
        // HyperOS (systemui y com.milink.service) pausa nuestra sesión ~10 ms después
        // de que otra empiece a sonar; con Zorin/KDE Connect esa otra es el mismo
        // Cider, así que obedecer lo pausaría. Nadie pausa a mano tan rápido.
        val justStarted = startedPlayingAt?.elapsedNow()?.let { it < AUTO_PAUSE_WINDOW } == true
        if (!playWhenReady && justStarted) {
            Log.i(TAG, "Pausa ignorada: llegó justo después de empezar a sonar")
            return Futures.immediateVoidFuture()
        }
        if (r != null && playWhenReady != current?.isPlaying) r.togglePlay()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val r = remote ?: return Futures.immediateVoidFuture()
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> r.next()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> r.previous()
            else -> if (mediaItemIndex == 1 || mediaItemIndex == androidx.media3.common.C.INDEX_UNSET) {
                r.seek(positionMs.coerceAtLeast(0) / 1000.0)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val r = remote ?: return Futures.immediateVoidFuture()
        val target = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            else -> RepeatMode.None
        }
        r.setRepeat(target)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (current?.shuffle != shuffleModeEnabled) remote?.toggleShuffle()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        remote?.setVolume(deviceVolume.coerceIn(0, MAX_VOLUME) / MAX_VOLUME.toDouble())
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> = stepVolume(+1)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> = stepVolume(-1)

    private fun stepVolume(direction: Int): ListenableFuture<*> {
        val r = remote ?: return Futures.immediateVoidFuture()
        val now = ((current?.volume ?: 0.0) * MAX_VOLUME).roundToInt()
        r.setVolume((now + direction * VOLUME_STEP).coerceIn(0, MAX_VOLUME) / MAX_VOLUME.toDouble())
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    private fun placeholder(uid: String) = MediaItemData.Builder(uid)
        .setMediaItem(MediaItem.Builder().setMediaId(uid).build())
        .build()

    private companion object {
        const val TAG = "RemotePlayer"
        val AUTO_PAUSE_WINDOW = 1500.milliseconds
        const val MAX_VOLUME = 100
        const val VOLUME_STEP = 5

        val DEVICE_INFO: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(MAX_VOLUME)
            .build()

        val COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
        ).build()
    }
}
