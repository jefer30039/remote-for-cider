package cider.remote.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cider.remote.ArtistPage
import cider.remote.CiderRemote
import cider.remote.HomeItem
import cider.remote.app.R
import cider.remote.app.app
import cider.remote.app.data.rememberCached
import cider.remote.app.ui.Apple
import cider.remote.app.ui.AppleIcons
import cider.remote.app.ui.ForceDark
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.app.ui.Route
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.CircleButton
import cider.remote.app.ui.components.EmptyState
import cider.remote.app.ui.components.LightStatusBarIcons
import cider.remote.app.ui.components.ListRow
import cider.remote.app.ui.components.asBackground
import cider.remote.app.ui.components.pageBackground
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.home.ExplicitBadge
import cider.remote.app.ui.home.toRoute
import androidx.compose.material3.ExperimentalMaterial3Api
import cider.remote.app.ui.home.Shelf
import cider.remote.artistPage
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Canciones top que se muestran plegadas (Apple Music enseña cinco y "ver todo"). */
private const val TopSongsFolded = 5

/** Canciones que se encolan detrás de la elegida. */
private const val MaxFollowing = 50

/**
 * Página de artista como la de Apple Music en iOS 27: imagen editorial a todo el
 * ancho con el logo del artista, reproducir en el borde de la imagen (info y
 * aleatorio a los lados), último lanzamiento, canciones top, estanterías de Apple
 * (álbumes, sencillos, en vivo, aparece en, playlists, similares…) y biografía.
 * Los artistas solo de la biblioteca (sin ficha en el catálogo) caen en [ArtistAlbumsScreen].
 */
@Composable
fun ArtistScreen(remote: CiderRemote, host: String, route: Route.Artist, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    // Id del catálogo: directo, o resuelto desde el artista de la biblioteca ("" = no lo tiene).
    val catalogId by produceState(if (route.catalog) route.id else null, route) {
        if (value == null) value = runCatching {
            remote.api.artistInfo(route.id, catalog = false)?.relationships?.catalog?.data?.firstOrNull()?.id
        }.getOrNull() ?: ""
    }
    when (val id = catalogId) {
        null -> ForceDark { Box(Modifier.fillMaxSize().background(Apple.colors.background)) { Loading() } }
        "" -> ArtistAlbumsScreen(remote, route, contentPadding, onOpen)
        else -> CatalogArtist(remote, host, id, route.title, contentPadding, onOpen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogArtist(remote: CiderRemote, host: String, catalogId: String, fallbackName: String, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    val cache = LocalContext.current.app.contentCache
    var failure by remember { mutableStateOf<Throwable?>(null) }
    val page by cache.rememberCached(host, "artist-$catalogId", ArtistPage.serializer()) {
        runCatching { remote.api.artistPage(catalogId) }.onFailure { failure = it }.getOrThrow()
    }
    val scope = rememberCoroutineScope()
    val wide = LocalWindowLayout.current.expanded
    val margin = LocalWindowLayout.current.margin
    val hero = page?.let { if (wide) it.heroWide ?: it.heroTall else it.heroTall ?: it.heroWide } ?: page?.artwork
    // Fondo del color de la imagen editorial, como en los álbumes.
    val bg = (hero?.bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() } ?: Color(0xFF2C2C2E)).pageBackground(dark = androidx.compose.foundation.isSystemInDarkTheme())
    val pageBackground = cider.remote.app.ui.LocalPageBackground.current
    androidx.compose.runtime.SideEffect { pageBackground.value = bg }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { pageBackground.value = null } }
    var showInfo by remember { mutableStateOf(false) }

    /** Reproduce las canciones top (Apple Music hace lo mismo con "Reproducir"), desde [startAt]. */
    fun play(shuffle: Boolean, startAt: Int = 0) = scope.launch {
        val songs = page?.topSongs.orEmpty()
        runCatching {
            if (songs.isEmpty()) {
                val first = page?.shelves?.firstOrNull()?.items?.firstOrNull() ?: return@runCatching
                remote.api.playCollection(first.type, first.id, shuffle)
                return@runCatching
            }
            remote.api.setShuffle(shuffle)
            val chosen = songs[startAt.coerceIn(songs.indices)]
            remote.playThenQueue(chosen.type, chosen.id, songs.drop(startAt + 1).take(MaxFollowing).map { it.type to it.id })
        }
    }

    LightStatusBarIcons()
    ForceDark {
        Box(Modifier.fillMaxSize().background(bg)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item {
                    Hero(page, fallbackName, bg, wide, onPlay = { play(false) }, onShuffle = { play(true) }, onInfo = { showInfo = true })
                }
                val p = page
                when {
                    p == null && failure != null -> item { EmptyState(stringResource(R.string.artist_load_error), failure?.message) }
                    p == null -> item { Loading() }
                    else -> {
                        p.latest?.let { latest -> item { LatestRelease(remote, latest, margin) { latest.toRoute()?.let(onOpen) } } }
                        if (p.topSongs.isNotEmpty()) item { TopSongs(remote, p.topSongsTitle, p.topSongs, margin) { play(false, startAt = it) } }
                        items(p.shelves.size, key = { p.shelves[it].title ?: it.toString() }) { Shelf(remote, p.shelves[it], onOpen) }
                        if (p.bio != null || p.origin != null || p.bornOrFormed != null) item { About(p, margin) }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
    if (showInfo && page != null) ModalBottomSheet(onDismissRequest = { showInfo = false }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(page!!.name, style = Apple.type.title2, color = Apple.colors.label)
            AboutBody(page!!, expanded = true, onToggle = null)
        }
    }
}

/**
 * Cabecera: imagen editorial (3:4 recortada a ~1:1,08 en teléfono, panorámica en tablet)
 * fundida con el fondo, el logo del artista encima y el botón de reproducir montado
 * sobre el borde inferior, con info y aleatorio a 76 dp de su centro (Apple Music).
 */
@Composable
private fun Hero(page: ArtistPage?, fallbackName: String, bg: Color, wide: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit, onInfo: () -> Unit) {
    val hero = page?.let { if (wide) it.heroWide ?: it.heroTall else it.heroTall ?: it.heroWide }
    val ratio = if (wide) 2.2f else 1f / 1.08f
    Box(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val model = if (wide) hero?.cropped(2000, 909) else hero?.cropped(1200, 1296)
            AsyncImage(
                model = model ?: page?.artwork?.sized(1200), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(cider.remote.app.ui.components.fadeInto(bg, from = 0.4f)))
            // Logo editorial (PNG con transparencia); si no hay, el nombre en grande.
            val logo = page?.logo
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp).fillMaxWidth(if (wide) 0.4f else 0.72f).height(72.dp)) {
                if (logo != null) AsyncImage(
                    model = logo.png(1200, 286), contentDescription = page.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                ) else Text(
                    page?.name ?: fallbackName, style = Apple.type.largeTitle.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        // A caballo sobre el borde de la imagen: la mitad del botón (32 dp) por fuera.
        Row(
            Modifier.align(Alignment.BottomCenter).offset(y = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Outlined.Info, stringResource(R.string.artist_info), onInfo, size = 44.dp,
                background = Color.White.copy(alpha = 0.18f), tint = Color.White, border = null, iconSize = 22.dp)
            CircleButton(AppleIcons.Play, stringResource(R.string.action_play), onPlay, size = 64.dp,
                background = Color.White, tint = Color.Black, border = null, iconSize = 28.dp)
            CircleButton(Icons.Rounded.Shuffle, stringResource(R.string.action_shuffle), onShuffle, size = 44.dp,
                background = Color.White.copy(alpha = 0.18f), tint = Color.White, border = null, iconSize = 22.dp)
        }
    }
}

/** Tarjeta del último lanzamiento: carátula, fecha, título y número de canciones. */
@Composable
private fun LatestRelease(remote: CiderRemote, item: HomeItem, margin: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val date = item.subtitleDate()
    Row(
        Modifier.padding(horizontal = margin, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f)).combinedClickableCompat(onClick = onClick, onLongClick = { menu = true })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(item.artwork, 76.dp, shape = RoundedCornerShape(6.dp), px = 300)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            date?.let { Text(it, style = Apple.type.caption, color = Apple.colors.secondaryLabel, maxLines = 1) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = Apple.type.headline, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.explicit) ExplicitBadge()
            }
            item.trackCount?.let { Text(pluralStringResource(R.plurals.song_count, it, it), style = Apple.type.caption, color = Apple.colors.secondaryLabel) }
        }
        Box { MoreButton(Apple.colors.secondaryLabel) { menu = true }; PlayMenu(remote, menu, { menu = false }, item.type, item.id) }
    }
}

/** Fecha de lanzamiento en el idioma del teléfono ("26 de mayo de 2026"). */
private fun HomeItem.subtitleDate(): String? = releaseDate?.let { raw ->
    runCatching { LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) }.getOrNull() ?: raw
}

@Composable
private fun TopSongs(remote: CiderRemote, title: String?, songs: List<HomeItem>, margin: androidx.compose.ui.unit.Dp, onPlay: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) songs else songs.take(TopSongsFolded)
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            Modifier.padding(horizontal = margin).combinedClickableCompat(onClick = { expanded = !expanded }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title ?: stringResource(R.string.library_songs), style = Apple.type.title2, color = Apple.colors.label)
            if (songs.size > TopSongsFolded) Icon(
                Icons.Rounded.ChevronRight, if (expanded) stringResource(R.string.action_show_less) else stringResource(R.string.action_see_all),
                tint = Apple.colors.secondaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp),
            )
        }
        shown.forEachIndexed { i, song ->
            var menu by remember(song.id) { mutableStateOf(false) }
            Box {
                QueueSwipe(remote, song.type, song.id) {
                    ListRow(
                        title = song.name,
                        subtitle = song.subtitle,
                        leading = { ArtworkImage(song.artwork, 48.dp, shape = RoundedCornerShape(4.dp), px = 150) },
                        leadingWidth = 60.dp,
                        minHeight = 60.dp,
                        showDivider = i < shown.lastIndex,
                        trailing = { MoreButton(Apple.colors.secondaryLabel) { menu = true } },
                        onClick = { onPlay(i) },
                        onLongClick = { menu = true },
                    )
                }
                Box(Modifier.align(Alignment.TopEnd)) { PlayMenu(remote, menu, { menu = false }, song.type, song.id) }
            }
        }
    }
}

@Composable
private fun About(page: ArtistPage, margin: androidx.compose.ui.unit.Dp) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = margin).padding(top = 18.dp)) {
        Text(stringResource(R.string.artist_about), style = Apple.type.title2, color = Apple.colors.label, modifier = Modifier.padding(bottom = 10.dp))
        AboutBody(page, expanded, onToggle = { expanded = !expanded })
    }
}

/** Biografía (plegada a cuatro líneas si [onToggle] existe), origen, nacimiento y géneros. */
@Composable
private fun AboutBody(page: ArtistPage, expanded: Boolean, onToggle: (() -> Unit)?) {
    page.bio?.let { raw ->
        val bio = remember(raw) { bioText(raw) }
        Text(
            bio, style = Apple.type.body, color = Apple.colors.label,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.then(if (onToggle != null) Modifier.combinedClickableCompat(onClick = onToggle) else Modifier),
        )
    }
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        page.origin?.let { Fact(stringResource(R.string.artist_origin), it) }
        page.bornOrFormed?.let { Fact(stringResource(R.string.artist_born), it) }
        if (page.genres.isNotEmpty()) Fact(stringResource(R.string.label_genre), page.genres.joinToString(", "))
    }
}

/** Biografía de Apple con sus etiquetas: solo trae `<I>…</I>` (cursiva); cualquier otra se quita. */
private fun bioText(raw: String): AnnotatedString = buildAnnotatedString {
    val tag = Regex("<(/?)([A-Za-z]+)[^>]*>")
    var italic = false
    var from = 0
    for (m in tag.findAll(raw)) {
        append(raw.substring(from, m.range.first))
        val closing = m.groupValues[1] == "/"
        when (m.groupValues[2].lowercase()) {
            "i" -> if (!closing && !italic) { pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); italic = true }
                   else if (closing && italic) { pop(); italic = false }
            "br" -> append("\n")
        }
        from = m.range.last + 1
    }
    append(raw.substring(from))
    if (italic) pop()
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = Apple.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Apple.colors.secondaryLabel)
        Text(value, style = Apple.type.subhead, color = Apple.colors.label)
    }
}

private fun Color.darker(f: Float) = Color(red * f, green * f, blue * f, alpha)
