package cider.remote.app.ui.player

import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import cider.remote.app.ui.AppleIcons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cider.remote.CiderRemote
import cider.remote.Link
import cider.remote.PlayerState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cider.remote.app.app
import cider.remote.app.ui.Apple
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.TintedIcon
import androidx.compose.foundation.text.BasicText
import cider.remote.app.ui.components.glass
import cider.remote.app.ui.components.glassEdge
import cider.remote.app.ui.components.rememberSharedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import cider.remote.app.ui.components.GlassBackdrop
import cider.remote.app.ui.components.combinedClickableCompat

/** Cápsula flotante sobre la barra de pestañas; al tocarla abre la hoja. */
@Composable
fun MiniPlayer(
    remote: CiderRemote,
    state: PlayerState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    haze: GlassBackdrop? = null,
    /** 0 = normal, 1 = en línea con la barra minimizada (iOS 26: solo play, sin siguiente). Leído al medir. */
    compact: () -> Float = { 0f },
    /** Color de texto e iconos (blanco o negro según lo que hay detrás); leído al dibujar. */
    label: (() -> Color)? = null,
    /** Velo del vidrio (el mismo que el de la barra de pestañas), leído al dibujar. */
    container: (() -> Color)? = null,
    /** Para la miniatura de la carátula (la hoja de reproducción la usa de origen de su transición). */
    thumb: Modifier = Modifier,
) {
    val shape = CircleShape
    val openDescription = stringResource(R.string.action_open_now_playing)
    val status = rememberMiniStatus(state, onOpen)
    // El mismo vidrio que la barra de pestañas (Kyant): vibrancia, desenfoque 8 dp, lente 24 dp,
    // brillo del canto y sombra propia, sobre la misma capa de la página.
    val backdrop = haze?.let { rememberSharedBackdrop(it) }
    val themeContainer = if (Apple.colors.isDark) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val veil: () -> Color = container ?: { themeContainer }
    val themeLabel = Apple.colors.label
    val themeSecondary = Apple.colors.secondaryLabel
    // Colores leídos al dibujar (ColorProducer y TintedIcon): la animación de tono no recompone.
    val primary: () -> Color = { label?.invoke() ?: themeLabel }
    val secondary: () -> Color = { label?.invoke()?.copy(alpha = 0.6f) ?: themeSecondary }
    Row(
        modifier.fillMaxWidth()
            .then(
                if (backdrop != null) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(veil()) },
                ) else Modifier.shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f))
                    .clip(shape).background(Apple.colors.miniPlayer).border(0.5.dp, Apple.colors.circleBorder, shape),
            )
            .combinedClickableCompat(onClick = status.onTap)
            .semantics { contentDescription = if (state.track != null) openDescription else listOfNotNull(status.title, status.subtitle).joinToString(". ") }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniArtwork(state, status, 32.dp, 120, thumb, secondary)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            BasicText(
                status.title, style = Apple.type.subhead.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                color = { primary() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            status.subtitle?.takeIf { it.isNotEmpty() }?.let {
                BasicText(
                    it, style = Apple.type.subhead.copy(fontSize = 14.sp, lineHeight = 17.sp),
                    color = { secondary() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Sin canción o sin conexión los botones se ven atenuados y no hacen nada (Apple Music).
        val buttons: () -> Color = if (status.active) primary else ({ primary().copy(alpha = 0.3f) })
        MiniButton(
            if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), size = 22.dp, tint = buttons,
            enabled = status.active, onClick = remote::togglePlay,
        )
        // Minimizado: el botón de siguiente se estrecha y desvanece.
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // El muelle rebota algo por encima de 1: acotar, o el ancho sale negativo.
                layout((placeable.width * (1f - compact().coerceIn(0f, 1f))).roundToInt(), placeable.height) { placeable.place(0, 0) }
            }.graphicsLayer { alpha = (1f - compact() * 2f).coerceIn(0f, 1f) },
        ) {
            MiniButton(AppleIcons.Forward, stringResource(R.string.action_next), tint = buttons, enabled = status.active, onClick = remote::next)
        }
    }
}

@Composable
private fun MiniButton(
    icon: ImageVector,
    description: String,
    tint: Color = Apple.colors.label,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit,
) {
    MiniButton(icon, description, tint = { tint }, size = size, enabled = true, onClick = onClick)
}

@Composable
private fun MiniButton(
    icon: ImageVector,
    description: String,
    tint: () -> Color,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).combinedClickableCompat(onClick = if (enabled) onClick else null)
            .semantics { contentDescription = description; role = Role.Button; if (!enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(icon, size, tint)
    }
}

/**
 * Miniplayer de tablet (Apple Music en Xiaomi Pad): cápsula ancha de 63 dp con
 * aleatorio · anterior · play · siguiente · repetir, la canción y, al final,
 * letra y cola (que abren la hoja en ese modo); tocar la canción la abre como se dejó (`null`).
 */
@Composable
fun WideMiniPlayer(
    remote: CiderRemote,
    state: PlayerState,
    haze: GlassBackdrop,
    onOpen: (SheetMode?) -> Unit,
    modifier: Modifier = Modifier,
    /** Color de texto e iconos según lo que hay detrás (blanco o negro); leído al dibujar. */
    label: (() -> Color)? = null,
    /** Velo del vidrio, leído al dibujar. */
    container: (() -> Color)? = null,
    /** Para la miniatura de la carátula (origen de su vuelo al abrir la hoja). */
    thumb: Modifier = Modifier,
) {
    val openDescription = stringResource(R.string.action_open_now_playing)
    val status = rememberMiniStatus(state) { onOpen(null) }
    // El mismo vidrio que el miniplayer y la barra del teléfono (Kyant): vibrancia, desenfoque
    // 8 dp, lente 24 dp, brillo del canto y sombra propia, sobre la misma capa de la página.
    val backdrop = rememberSharedBackdrop(haze)
    val themeContainer = if (Apple.colors.isDark) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val veil: () -> Color = container ?: { themeContainer }
    val themeLabel = Apple.colors.label
    val accent = Apple.colors.accent
    // Colores leídos al dibujar: la animación de tono no recompone el reproductor.
    val primary: () -> Color = { label?.invoke() ?: themeLabel }
    val secondary: () -> Color = { primary().copy(alpha = 0.6f) }
    val dim: () -> Color = { primary().copy(alpha = 0.45f) }
    val off: () -> Color = { primary().copy(alpha = 0.3f) }
    val on = status.active
    val main: () -> Color = if (on) primary else off
    Row(
        modifier.height(63.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(veil()) },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shuffle = state.shuffle
        MiniButton(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), tint = { if (!on) off() else if (shuffle) accent else dim() }, size = 22.dp, enabled = on) { remote.toggleShuffle() }
        MiniButton(AppleIcons.Backward, stringResource(R.string.action_previous), tint = main, size = 30.dp, enabled = on, onClick = remote::previous)
        MiniButton(
            if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), tint = main, size = 26.dp, enabled = on, onClick = remote::togglePlay,
        )
        MiniButton(AppleIcons.Forward, stringResource(R.string.action_next), tint = main, size = 30.dp, enabled = on, onClick = remote::next)
        val repeat = state.repeat
        MiniButton(
            if (repeat == cider.remote.model.RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, stringResource(R.string.action_repeat),
            tint = { if (!on) off() else if (repeat != cider.remote.model.RepeatMode.None) accent else dim() }, size = 22.dp, enabled = on,
        ) { remote.cycleRepeat() }
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
                .combinedClickableCompat(onClick = status.onTap)
                .semantics { contentDescription = if (state.track != null) openDescription else listOfNotNull(status.title, status.subtitle).joinToString(". ") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniArtwork(state, status, 40.dp, 160, thumb, secondary)
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(status.title, style = Apple.type.headline, color = { primary() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                status.subtitle?.takeIf { it.isNotEmpty() }?.let {
                    BasicText(it, style = Apple.type.subhead, color = { secondary() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val hasTrack = state.track != null
        MiniButton(AppleIcons.Lyrics, stringResource(R.string.action_lyrics), tint = if (hasTrack) primary else off, size = 26.dp, enabled = hasTrack) { onOpen(SheetMode.Lyrics) }
        MiniButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.up_next), tint = if (hasTrack) primary else off, size = 26.dp, enabled = hasTrack) { onOpen(SheetMode.Queue) }
    }
}

/**
 * Lo que dice el miniplayer cuando no hay canción o no hay conexión (siempre está, como en Apple Music:
 * "Nada sonando" con la carátula gris y los botones atenuados). Sin conexión: tocarlo busca el PC.
 */
private class MiniStatus(val title: String, val subtitle: String?, val icon: ImageVector?, val active: Boolean, val onTap: () -> Unit)

@Composable
private fun rememberMiniStatus(state: PlayerState, onOpen: () -> Unit): MiniStatus {
    val session = LocalContext.current.app.session
    val searching by session.searching.collectAsStateWithLifecycle()
    val track = state.track
    val offline = state.link is Link.Offline
    val offlineTitle = stringResource(R.string.status_offline)
    val offlineSubtitle = stringResource(if (searching) R.string.status_searching_network else R.string.status_tap_to_search)
    return when {
        track != null -> MiniStatus(
            track.name,
            when (state.link) {
                is Link.Offline -> stringResource(if (searching) R.string.status_searching_network else R.string.status_offline)
                Link.Connecting -> stringResource(R.string.status_connecting)
                Link.Online -> track.artistName
            },
            null, active = !offline, onTap = onOpen,
        )
        offline -> MiniStatus(offlineTitle, offlineSubtitle, Icons.Filled.WifiOff, active = false, onTap = { session.searchNow() })
        state.link == Link.Connecting -> MiniStatus(stringResource(R.string.status_connecting), null, Icons.Filled.MusicNote, active = false, onTap = {})
        else -> MiniStatus(stringResource(R.string.status_not_playing), null, Icons.Filled.MusicNote, active = false, onTap = {})
    }
}

/** Carátula, o un cuadro gris con un icono cuando no hay canción. */
@Composable
private fun MiniArtwork(state: PlayerState, status: MiniStatus, size: androidx.compose.ui.unit.Dp, px: Int, thumb: Modifier, tint: () -> Color) {
    val icon = status.icon
    if (state.track != null || icon == null) ArtworkImage(state.track?.artwork, size, thumb, shape = RoundedCornerShape(5.dp), px = px)
    else Box(
        thumb.size(size).clip(RoundedCornerShape(5.dp)).background(Apple.colors.fill),
        contentAlignment = Alignment.Center,
    ) { TintedIcon(icon, size * 0.55f, tint) }
}
