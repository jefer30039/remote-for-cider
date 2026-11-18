package cider.remote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import cider.remote.app.ui.Apple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cider.remote.app.R

/**
 * Reproduce en el teléfono, a pantalla completa y con sonido, un vídeo que Cider no puede poner: los que
 * suben los artistas ("Más para ver") enteros y, de los vídeos musicales, la vista previa de 30 s
 * ([preview], que se indica arriba). Atrás o la ✕ lo cierran.
 */
@Composable
fun VideoPlayerDialog(url: String, title: String, preview: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; contentDescription = title } },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            )
            Box(
                Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)
                    .size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                    .combinedClickableCompat(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = Color.White, modifier = Modifier.size(22.dp))
            }
            if (preview) Text(
                stringResource(R.string.video_preview), style = Apple.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 22.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
