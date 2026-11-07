package cider.remote.app.ui.components

import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import cider.remote.CiderRemote
import cider.remote.SongLinks
import cider.remote.app.R
import cider.remote.app.ui.Apple
import cider.remote.app.ui.AppleIcons
import cider.remote.app.ui.LocalOpenRoute
import cider.remote.app.ui.home.toRoute
import cider.remote.model.LibraryPlaylist
import cider.remote.songLinks
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Álbum, artista y enlace por canción, para no consultar cada vez que se abre el menú. */
private val linksCache = ConcurrentHashMap<String, SongLinks>()

/**
 * Menú de canción como el de Apple Music en iOS 27: fila de acciones rápidas
 * (reproducir, a continuación, compartir) y lista con añadir a playlist, al final de la
 * cola, ir al álbum e ir al artista. Lo que Cider no permite (añadir a la biblioteca o
 * marcar favorita una canción que no suena, crear emisora, créditos) no aparece.
 */
@Composable
fun SongMenu(remote: CiderRemote, expanded: Boolean, onDismiss: () -> Unit, type: String, id: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val open = LocalOpenRoute.current
    var picking by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<LibraryPlaylist>?>(null) }
    // Una consulta al abrir el menú; sirve para compartir y para ir al álbum o al artista.
    val links by produceState(linksCache[id], expanded, id) {
        if (expanded && value == null) value = runCatching { remote.api.songLinks(id) }.getOrNull()?.also { linksCache[id] = it }
    }
    LaunchedEffect(expanded) { if (!expanded) picking = false }
    fun run(block: suspend () -> Unit) { onDismiss(); scope.launch { runCatching { block() } } }

    GlassMenu(expanded, onDismiss) {
        if (picking) {
            PlaylistPicker(playlists, onBack = { picking = false }) { playlist ->
                run {
                    val ok = runCatching { remote.api.playlistAddTracks(playlist.id, listOf(type to id)) }.isSuccess
                    Toast.makeText(
                        context, if (ok) context.getString(R.string.playlist_added, playlist.name) else context.getString(R.string.playlist_add_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            return@GlassMenu
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
            QuickAction(AppleIcons.Play, stringResource(R.string.action_play)) { run { remote.api.playItem(type, id) } }
            QuickAction(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.action_play_next)) { run { remote.api.queueAddNext(type, id) } }
            val url = links?.url
            if (url != null) QuickAction(Icons.Rounded.Share, stringResource(R.string.action_share)) { onDismiss(); share(context, url) }
        }
        HorizontalDivider(color = Apple.colors.separator)
        MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.action_add_to_playlist)) {
            picking = true
            if (playlists == null) scope.launch {
                playlists = runCatching { remote.api.playlists(limit = 100).items.filter { it.canEdit } }.getOrDefault(emptyList())
            }
        }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.action_play_last)) { run { remote.api.queueAddLater(type, id) } }
        val album = links?.album
        val artist = links?.artist
        if (album != null || artist != null) HorizontalDivider(color = Apple.colors.separator)
        album?.let { MenuRow(Icons.Rounded.Album, stringResource(R.string.action_go_to_album), it.name) { onDismiss(); it.toRoute()?.let(open) } }
        artist?.let { MenuRow(Icons.Rounded.Person, stringResource(R.string.action_go_to_artist), it.name) { onDismiss(); it.toRoute()?.let(open) } }
    }
}

/**
 * Menú emergente de vidrio líquido, con el mismo vidrio de Kyant que la barra de
 * pestañas (vibrancia, lente de 24 dp y brillo del canto; desenfoque algo mayor, 14 dp). El menú vive
 * en otra ventana, pero [SharedBackdrop] localiza la página por coordenadas de pantalla.
 * Sin fondo disponible, color plano.
 */
@Composable
fun GlassMenu(expanded: Boolean, onDismiss: () -> Unit, width: Dp = 292.dp, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlassBackdrop.current
    val backdrop = glass?.let { rememberSharedBackdrop(it) }
    // Mismo velo que la barra de Kyant.
    val surface = if (Apple.colors.isDark) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    DropdownMenu(
        expanded, onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp), shadowElevation = 12.dp,
        containerColor = if (backdrop != null) Color.Transparent else Apple.colors.bar,
        modifier = Modifier.width(width).then(
            if (backdrop != null) Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(22.dp) },
                effects = {
                    vibrancy()
                    blur(14.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default },
                onDrawSurface = { drawRect(surface) },
            ) else Modifier,
        ),
        content = content,
    )
}

/** Acción rápida: círculo con icono y etiqueta debajo (fila de arriba del menú de iOS). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Apple.colors.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Apple.colors.label, modifier = Modifier.size(20.dp))
        }
        Text(label, style = Apple.type.caption, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Fila de menú: icono, título y subtítulo opcional. */
@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).combinedClickableCompat(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Apple.colors.label, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = Apple.type.body, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Apple.type.footnote, color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Lista de playlists editables de la biblioteca; sustituye al menú hasta elegir una. */
@Composable
private fun PlaylistPicker(playlists: List<LibraryPlaylist>?, onBack: () -> Unit, onPick: (LibraryPlaylist) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack, size = 36.dp, background = Color.Transparent, border = null)
        Text(stringResource(R.string.action_add_to_playlist), style = Apple.type.headline.copy(fontWeight = FontWeight.SemiBold), color = Apple.colors.label)
    }
    HorizontalDivider(color = Apple.colors.separator)
    when {
        playlists == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        playlists.isEmpty() -> Text(
            stringResource(R.string.playlists_none_editable), style = Apple.type.subhead, color = Apple.colors.secondaryLabel,
            modifier = Modifier.padding(16.dp),
        )
        else -> Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            playlists.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().combinedClickableCompat(onClick = { onPick(p) }).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(p.artwork, 36.dp, shape = RoundedCornerShape(4.dp), px = 120)
                    Text(p.name, style = Apple.type.body, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}
