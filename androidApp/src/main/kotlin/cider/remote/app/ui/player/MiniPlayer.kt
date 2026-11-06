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
) {
    val track = state.track
    val shape = CircleShape
    val openDescription = stringResource(R.string.action_open_now_playing)
    val themeLabel = Apple.colors.label
    val themeSecondary = Apple.colors.secondaryLabel
    // Colores leídos al dibujar (ColorProducer y TintedIcon): la animación de tono no recompone.
    val primary: () -> Color = { label?.invoke() ?: themeLabel }
    val secondary: () -> Color = { label?.invoke()?.copy(alpha = 0.6f) ?: themeSecondary }
    Row(
        modifier.fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape).then(if (haze != null) Modifier.glass(haze) else Modifier.background(Apple.colors.miniPlayer))
            .then(if (haze != null) Modifier.glassEdge(shape) else Modifier.border(0.5.dp, Apple.colors.circleBorder, shape))
            .combinedClickableCompat(onClick = onOpen)
            .semantics { contentDescription = openDescription }
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(track?.artwork, 32.dp, shape = RoundedCornerShape(5.dp), px = 120)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            BasicText(
                track?.name ?: stringResource(R.string.status_not_playing), style = Apple.type.subhead.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                color = { primary() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val searching by LocalContext.current.app.session.searching.collectAsStateWithLifecycle()
            val subtitle = when (state.link) {
                is Link.Offline -> stringResource(if (searching) R.string.status_searching_network else R.string.status_offline)
                Link.Connecting -> stringResource(R.string.status_connecting)
                Link.Online -> track?.artistName.orEmpty()
            }
            BasicText(
                subtitle, style = Apple.type.subhead.copy(fontSize = 14.sp, lineHeight = 17.sp),
                color = { secondary() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        MiniButton(
            if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), size = 22.dp, tint = primary, onClick = remote::togglePlay,
        )
        // Minimizado: el botón de siguiente se estrecha y desvanece.
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // El muelle rebota algo por encima de 1: acotar, o el ancho sale negativo.
                layout((placeable.width * (1f - compact().coerceIn(0f, 1f))).roundToInt(), placeable.height) { placeable.place(0, 0) }
            }.graphicsLayer { alpha = (1f - compact() * 2f).coerceIn(0f, 1f) },
        ) {
            MiniButton(AppleIcons.Forward, stringResource(R.string.action_next), tint = primary, onClick = remote::next)
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
) = MiniButton(icon, description, { tint }, size, onClick)

@Composable
private fun MiniButton(
    icon: ImageVector,
    description: String,
    tint: () -> Color,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(icon, size, tint)
    }
}

/**
 * Miniplayer de tablet (Apple Music en Xiaomi Pad): cápsula ancha de 63 dp con
 * aleatorio · anterior · play · siguiente · repetir, la canción y, al final,
 * letra y cola (que abren la hoja en ese modo).
 */
@Composable
fun WideMiniPlayer(
    remote: CiderRemote,
    state: PlayerState,
    haze: GlassBackdrop,
    onOpen: (SheetMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    val shape = CircleShape
    val dim = Apple.colors.label.copy(alpha = 0.45f)
    val openDescription = stringResource(R.string.action_open_now_playing)
    Row(
        modifier.height(63.dp)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape).glass(haze).glassEdge(shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), tint = if (state.shuffle) Apple.colors.accent else dim, size = 22.dp) { remote.toggleShuffle() }
        MiniButton(AppleIcons.Backward, stringResource(R.string.action_previous), size = 30.dp, onClick = remote::previous)
        MiniButton(
            if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), size = 26.dp, onClick = remote::togglePlay,
        )
        MiniButton(AppleIcons.Forward, stringResource(R.string.action_next), size = 30.dp, onClick = remote::next)
        MiniButton(
            if (state.repeat == cider.remote.model.RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, stringResource(R.string.action_repeat),
            tint = if (state.repeat != cider.remote.model.RepeatMode.None) Apple.colors.accent else dim, size = 22.dp,
        ) { remote.cycleRepeat() }
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(8.dp))
                .combinedClickableCompat(onClick = { onOpen(SheetMode.Artwork) })
                .semantics { contentDescription = openDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(track?.artwork, 40.dp, shape = RoundedCornerShape(5.dp), px = 160)
            Column(Modifier.padding(start = 12.dp)) {
                Text(track?.name.orEmpty(), style = Apple.type.headline, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artistName.orEmpty(), style = Apple.type.subhead, color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MiniButton(AppleIcons.Lyrics, stringResource(R.string.action_lyrics), size = 26.dp) { onOpen(SheetMode.Lyrics) }
        MiniButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.up_next), size = 26.dp) { onOpen(SheetMode.Queue) }
    }
}
