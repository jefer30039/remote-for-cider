package cider.remote.app.ui.library

import androidx.compose.ui.res.pluralStringResource
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cider.remote.CiderApi
import cider.remote.CiderRemote
import cider.remote.Page
import cider.remote.RemoteSignal
import androidx.compose.ui.platform.LocalContext
import cider.remote.app.app
import cider.remote.app.data.rememberCached
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.app.ui.bleed
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.builtins.ListSerializer
import cider.remote.app.ui.AppleIcons
import cider.remote.app.ui.Route
import cider.remote.app.ui.components.AnimatedCover
import cider.remote.app.ui.components.LightStatusBarIcons
import cider.remote.app.ui.components.asBackground
import cider.remote.app.ui.components.rememberArtworkColor
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.CircleButton
import cider.remote.app.ui.components.ConnectionMenuButton
import cider.remote.app.ui.components.EmptyState
import cider.remote.app.ui.components.SongMenu
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import cider.remote.app.ui.components.MenuRow
import cider.remote.app.ui.components.GlassMenu
import cider.remote.app.ui.components.LargeTitle
import cider.remote.app.ui.components.ListRow
import cider.remote.app.ui.components.Monogram
import cider.remote.app.ui.components.PillButton
import cider.remote.app.ui.components.SwipeQueueActions
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.formatTime
import cider.remote.model.AmItem
import cider.remote.model.AnimatedCover
import cider.remote.model.Artwork
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Medidas de Apple Music para Android (dp): teléfono (521 dpi) / tablet (Xiaomi Pad 7 Pro).
private val wide: Boolean @Composable get() = LocalWindowLayout.current.expanded
private val Margin: Dp @Composable get() = LocalWindowLayout.current.margin
private val GridMargin: Dp @Composable get() = LocalWindowLayout.current.gridMargin
private val GridGap: Dp @Composable get() = LocalWindowLayout.current.gridGap
private val PinGap: Dp @Composable get() = if (wide) 20.dp else 12.dp

/** Cuadrícula con margen y separación reales; los elementos de ancho completo usan [fullBleed]. */
@Composable
private fun gridPadding(base: PaddingValues) = PaddingValues(
    start = GridMargin, end = GridMargin,
    top = base.calculateTopPadding(), bottom = base.calculateBottomPadding(),
)

private fun LazyGridScope.fullBleed(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.bleed(LocalWindowLayout.current.gridMargin)) { content() }
    }

// ---------- Inicio de la biblioteca ----------

@Composable
fun LibraryHome(
    remote: CiderRemote,
    host: String,
    contentPadding: PaddingValues,
    onOpen: (Route) -> Unit,
    onForget: () -> Unit,
) {
    // Con caché: se pintan al instante con lo último guardado y se refrescan detrás.
    val cache = LocalContext.current.app.contentCache
    val amItems = ListSerializer(AmItem.serializer())
    val recent by cache.rememberCached(host, "recientes", amItems) { remote.api.recentlyAdded(10) }
    val pinsOrNull by cache.rememberCached(host, "pins", amItems) { remote.api.libraryPins() }
    val pins = pinsOrNull.orEmpty()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val layout = LocalWindowLayout.current
    // Teléfono: pins de 3 en 3 y recientes en 2 columnas. Tablet: lo que quepa con celdas de 166 dp.
    val pinsPerRow = if (layout.expanded) layout.columns(160.dp, 3) else 3
    val showTitle = !layout.expanded
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns(160.dp, 2)),
        contentPadding = gridPadding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showTitle) fullBleed {
            LargeTitle(Route.LibraryHome.label())  // el botón ⋮ flota encima (MainScreen)
        }
        pins.chunked(pinsPerRow).forEachIndexed { row, chunk ->
            fullBleed {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Margin).padding(top = if (row == 0) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(PinGap),
                ) {
                    chunk.forEach { pin ->
                        AlbumTile(
                            title = pin.name, subtitle = null, artwork = pin.artwork,
                            circle = pin.type.endsWith("artists"), centered = true, labelStyle = Apple.type.footnote.copy(fontSize = Apple.type.subhead.fontSize * 0.9f),
                            onClick = {
                                val route = pin.toRoute()
                                if (route != null) onOpen(route)
                                else scope.launch { runCatching { remote.api.playItem(pin.type, pin.id) } }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(pinsPerRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val sections = listOf(
            Route.Playlists to Icons.AutoMirrored.Filled.QueueMusic,
            Route.Artists to AppleIcons.Mic,
            Route.Albums to AppleIcons.Albums,
            Route.Songs to Icons.Filled.MusicNote,
        )
        sections.forEachIndexed { i, (route, icon) ->
            fullBleed { CategoryRow(icon, route.label(), showDivider = i < sections.lastIndex) { onOpen(route) } }
        }
        fullBleed {
            Text(
                stringResource(R.string.library_recently_added), style = Apple.type.title2, color = Apple.colors.label,
                modifier = Modifier.padding(start = Margin, end = Margin, top = 32.dp, bottom = 12.dp),
            )
        }
        val items = recent
        if (items == null) fullBleed { Loading() }
        else items(items, key = { it.type + it.id }) { item ->
            AlbumTile(
                title = item.name,
                subtitle = item.attributes?.artistName ?: item.attributes?.curatorName,
                artwork = item.artwork,
                onClick = { item.toRoute()?.let(onOpen) },
            )
        }
    }
}

/** Fila de categoría: icono monocromo a 29 dp del borde y texto a 66 dp (como Apple Music). */
@Composable
private fun CategoryRow(icon: ImageVector, label: String, showDivider: Boolean, onClick: () -> Unit) {
    ListRow(
        title = label,
        titleStyle = Apple.type.body.copy(fontSize = 18.sp, lineHeight = 22.sp),
        leading = { Icon(icon, null, tint = Apple.colors.label, modifier = Modifier.padding(start = 7.dp).size(24.dp)) },
        leadingWidth = 46.dp,
        showDivider = showDivider,
        minHeight = 42.dp,
        onClick = onClick,
    )
}

/** Pantalla que abre un recurso de Apple Music; null si se reproduce directamente (canciones). */
fun AmItem.toRoute(): Route? = when {
    type == "artists" -> Route.Artist(id, name, catalog = true)
    type == "library-artists" -> Route.Artist(id, name, catalog = false)
    type.endsWith("albums") || type.endsWith("playlists") -> Route.Collection(
        kind = type, id = id, title = name,
        subtitle = attributes?.artistName ?: attributes?.curatorName,
        artwork = artwork,
    )
    else -> null
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

/** Celda de cuadrícula: carátula y textos debajo. Esquinas de 6 dp, como Apple Music. */
@Composable
fun AlbumTile(
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    centered: Boolean = false,
    labelStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    Column(
        modifier.padding(bottom = 18.dp).combinedClickableCompat(onClick = onClick),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (circle && artwork == null) Monogram(title, 400.dp, Modifier.fillMaxSize())
            else ArtworkImage(
                artwork, 400.dp, Modifier.fillMaxSize(), px = 400,
                shape = if (circle) CircleShape else RoundedCornerShape(6.dp),
            )
        }
        Text(
            title, style = labelStyle ?: Apple.type.subhead, color = Apple.colors.label, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (!subtitle.isNullOrBlank()) Text(
            subtitle, style = Apple.type.subhead, color = Apple.colors.secondaryLabel, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Listas de la biblioteca ----------

private const val PAGE = 50

/** Canciones que se encolan detrás de la elegida al empezar a mitad de un álbum o playlist. */
private const val MAX_FOLLOWING = 100

/** Las rutas de la biblioteca aceptan como mucho 100 por página (Cider responde 400 si se pide más). */
private const val MAX_PAGE = 100

private data class Entry(val key: String, val route: Route?, val title: String, val subtitle: String?, val artwork: Artwork?, val playType: String, val id: String)

private suspend fun CiderApi.load(route: Route, offset: Int): Page<Entry> = when (route) {
    Route.Playlists -> playlists(offset, PAGE).map {
        Entry(it.id, Route.Collection("library-playlists", it.id, it.name, it.description, it.artwork), it.name, null, it.artwork, "library-playlists", it.id)
    }
    Route.Albums -> albums(offset, PAGE).map {
        Entry(it.id, Route.Collection("library-albums", it.id, it.name, it.artistName, it.artwork), it.name, it.artistName, it.artwork, "library-albums", it.id)
    }
    // Artistas desde Apple Music para tener foto; si la página viene llena puede haber más.
    Route.Artists -> libraryArtistsWithArt(offset, MAX_PAGE).let { list ->
        Page(
            list.map { Entry(it.id, Route.Artist(it.id, it.name, catalog = false), it.name, null, it.artwork, "library-artists", it.id) },
            offset, if (list.size < MAX_PAGE) offset + list.size else offset + list.size + 1,
        )
    }
    Route.Songs -> songs(offset, PAGE).map {
        Entry(it.id, null, it.name, it.artistName, it.artwork, "library-songs", it.id)
    }
    else -> Page(emptyList(), 0, 0)
}

private fun <T, R> Page<T>.map(f: (T) -> R) = Page(items.map(f), offset, total)

/** Playlists, artistas y canciones como lista; álbumes como cuadrícula de 2 columnas. */
@Composable
fun LibraryList(remote: CiderRemote, route: Route, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    val items = remember(route) { mutableStateListOf<Entry>() }
    var total by remember(route) { mutableStateOf<Int?>(null) }
    var loading by remember(route) { mutableStateOf(false) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    suspend fun loadMore() {
        if (loading || (total != null && items.size >= total!!)) return
        loading = true
        runCatching { remote.api.load(route, items.size) }
            .onSuccess { page ->
                items += page.items.filter { e -> items.none { it.key == e.key } }
                total = page.total; error = null
            }
            .onFailure { error = it.message }
        loading = false
    }
    val nearEnd by remember(route) {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - 12 }
    }
    LaunchedEffect(route, nearEnd) { if (nearEnd) loadMore() }

    val grid = route == Route.Albums
    val layout = LocalWindowLayout.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) layout.columns(160.dp, 2) else 1),
        state = gridState,
        contentPadding = if (grid) gridPadding(contentPadding) else contentPadding,
        horizontalArrangement = Arrangement.spacedBy(if (grid) GridGap else 0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val full: (@Composable () -> Unit) -> Unit = { c -> if (grid) fullBleed(c) else fullWidth(c) }
        full { LargeTitle(route.label()) }
        if (items.isEmpty() && error != null) full { EmptyState(stringResource(R.string.load_error), error) }
        if (items.isEmpty() && total == 0) full {
            EmptyState(stringResource(when (route) {
                Route.Playlists -> R.string.empty_playlists
                Route.Artists -> R.string.empty_artists
                Route.Albums -> R.string.empty_albums
                else -> R.string.empty_songs
            }))
        }
        items(items, key = { it.key }) { e ->
            if (grid) {
                AlbumTile(e.title, e.subtitle, e.artwork, onClick = { e.route?.let(onOpen) })
            } else {
                EntryRow(remote, e, onOpen)
            }
        }
        if (loading) full { Loading() }
    }
}

@Composable
private fun EntryRow(remote: CiderRemote, e: Entry, onOpen: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val isArtist = e.playType == "library-artists"
    val isSong = e.playType == "library-songs"
    Box {
        QueueSwipe(remote, e.playType, e.id, enabled = !isArtist) { ListRow(
            title = e.title,
            subtitle = e.subtitle,
            titleStyle = if (isSong) Apple.type.callout else Apple.type.body,
            leading = {
                when {
                    isArtist && e.artwork == null -> Monogram(e.title, 48.dp)
                    isArtist -> ArtworkImage(e.artwork, 48.dp, shape = CircleShape)
                    else -> ArtworkImage(e.artwork, if (isSong) 48.dp else 56.dp, shape = RoundedCornerShape(5.dp))
                }
            },
            leadingWidth = if (isSong || isArtist) 62.dp else 70.dp,
            minHeight = if (isSong || isArtist) 60.dp else 68.dp,
            trailing = if (isArtist) null else ({ MoreButton { menu = true } }),
            onClick = {
                val r = e.route
                if (r != null) onOpen(r) else scope.launch { runCatching { remote.api.playItem(e.playType, e.id) } }
            },
            onLongClick = if (isArtist) null else ({ menu = true }),
        ) }
        Box(Modifier.align(Alignment.TopEnd)) { PlayMenu(remote, menu, { menu = false }, e.playType, e.id) }
    }
}

/** Puntos verticales a la derecha de cada fila (abren el menú contextual). */
@Composable
fun MoreButton(tint: Color = Apple.colors.label, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).combinedClickableCompat(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_options), tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Deslizar a la derecha: reproducir a continuación / al final (ver SwipeQueueActions). */
@Composable
fun QueueSwipe(remote: CiderRemote, type: String, id: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val scope = rememberCoroutineScope()
    SwipeQueueActions(
        onPlayNext = { scope.launch { runCatching { remote.api.queueAddNext(type, id) } } },
        onPlayLast = { scope.launch { runCatching { remote.api.queueAddLater(type, id) } } },
        content = content,
    )
}

/** Menú contextual (pulsación larga o ⋮), como en Apple Music. */
@Composable
fun PlayMenu(remote: CiderRemote, expanded: Boolean, onDismiss: () -> Unit, type: String, id: String) {
    // Las canciones tienen el menú completo de iOS; álbumes y playlists, el corto.
    if (type.endsWith("songs")) { SongMenu(remote, expanded, onDismiss, type, id); return }
    val scope = rememberCoroutineScope()
    fun run(block: suspend () -> Unit) { onDismiss(); scope.launch { runCatching { block() } } }
    GlassMenu(expanded, onDismiss, width = 240.dp) {
        MenuRow(AppleIcons.Play, stringResource(R.string.action_play)) { run { remote.api.playCollection(type, id) } }
        MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.action_play_next)) { run { remote.api.queueAddNext(type, id) } }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.action_play_last)) { run { remote.api.queueAddLater(type, id) } }
    }
}

// ---------- Artista ----------

/** Reserva para artistas solo de la biblioteca (sin ficha en el catálogo): foto, nombre y sus álbumes. */
@Composable
fun ArtistAlbumsScreen(remote: CiderRemote, route: Route.Artist, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    val info by produceState<AmItem?>(null, route) {
        value = runCatching { remote.api.artistInfo(route.id, route.catalog) }.getOrNull()
    }
    val albums by produceState<List<Route.Collection>?>(null, route) {
        value = runCatching {
            if (route.catalog) remote.api.catalogArtistAlbums(route.id).mapNotNull { it.toRoute() as? Route.Collection }
            else allPages { remote.api.artistAlbums(route.id, it, MAX_PAGE) }.map {
                Route.Collection("library-albums", it.id, it.name, it.releaseDate?.take(4), it.artwork)
            }
        }.getOrDefault(emptyList())
    }
    // Apple Music reproduce todo el artista; aquí: primer álbum y el resto a la cola.
    fun play(shuffle: Boolean) = scope.launch {
        val list = albums.orEmpty().ifEmpty { return@launch }
        runCatching {
            remote.api.setShuffle(shuffle)
            remote.api.playCollection(list.first().kind, list.first().id, shuffle)
            list.drop(1).forEach { remote.api.queueAddLater(it.kind, it.id) }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(LocalWindowLayout.current.columns(162.dp, 2)),
        contentPadding = gridPadding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullBleed {
            Column(Modifier.fillMaxWidth().padding(horizontal = Margin), horizontalAlignment = Alignment.CenterHorizontally) {
                val art = info?.artwork
                Spacer(Modifier.height(24.dp))
                if (art != null) ArtworkImage(art, 120.dp, shape = CircleShape, px = 400) else Monogram(route.title, 120.dp)
                Text(
                    route.title, style = Apple.type.title1.copy(fontSize = Apple.type.title1.fontSize * (26f / 28f)),
                    color = Apple.colors.label, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp, bottom = 24.dp),
                )
                // Teléfono: a todo el ancho. Tablet: píldoras de 167 dp centradas (Apple Music).
                val pill = if (wide) Modifier.width(167.dp) else Modifier
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton(stringResource(R.string.action_play), Icons.Filled.PlayArrow, { play(false) }, if (wide) pill else Modifier.weight(1f))
                    PillButton(stringResource(R.string.action_shuffle), Icons.Filled.Shuffle, { play(true) }, if (wide) pill else Modifier.weight(1f))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        val list = albums
        when {
            list == null -> fullBleed { Loading() }
            list.isEmpty() -> fullBleed { EmptyState(stringResource(R.string.empty_albums)) }
            else -> items(list, key = { it.id }) { a ->
                AlbumTile(a.title, a.subtitle, a.artwork, onClick = { onOpen(a) })
            }
        }
    }
}

private suspend fun <T> allPages(load: suspend (offset: Int) -> Page<T>): List<T> {
    val all = mutableListOf<T>()
    do {
        val page = load(all.size)
        all += page.items
    } while (page.items.isNotEmpty() && all.size < page.total)
    return all
}

// ---------- Álbum / playlist ----------

private data class TrackRow(val type: String, val id: String, val number: Int?, val title: String, val artist: String?, val durationMs: Long?, val artwork: Artwork?)

private suspend fun CiderApi.tracks(c: Route.Collection): List<TrackRow> = when (c.kind) {
    "library-albums" -> allPages { albumSongs(c.id, it, MAX_PAGE) }
        .map { TrackRow("library-songs", it.id, it.trackNumber, it.name, it.artistName, it.durationMs, it.artwork) }
    "library-playlists" -> allPages { playlistSongs(c.id, it, MAX_PAGE) }
        .map { TrackRow("library-songs", it.id, null, it.name, it.artistName, it.durationMs, it.artwork) }
    else -> catalogTracks(c.kind, c.id).mapIndexed { i, t ->
        TrackRow(t.type, t.id, i + 1, t.name, t.attributes?.artistName, t.attributes?.durationInMillis, t.artwork)
    }
}

/**
 * Cabecera de álbum/playlist en tablet: carátula de 300 dp a la izquierda (con su
 * portada animada cuadrada si la hay) y título, artista, datos y botones a la derecha.
 */
@Composable
private fun WideCollectionHeader(
    route: Route.Collection,
    squareVideo: String?,
    count: Int?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddLater: () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.padding(start = Margin, end = Margin, top = top + 74.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedCover(
                squareVideo,
                Modifier.size(300.dp).shadow(18.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
            ) { ArtworkImage(route.artwork, 300.dp, px = 800, shape = RoundedCornerShape(8.dp)) }
            Column(Modifier.weight(1f).padding(start = 32.dp)) {
                Text(route.title, style = Apple.type.title1, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!route.subtitle.isNullOrBlank()) Text(
                    route.subtitle, style = Apple.type.title1.copy(fontWeight = FontWeight.Normal),
                    color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val meta = collectionMeta(route, count)
                Text(meta, style = Apple.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 8.dp))
                Row(
                    Modifier.padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), onShuffle, size = 48.dp,
                        background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                    PillButton(stringResource(R.string.action_play), Icons.Filled.PlayArrow, onPlay, Modifier.width(160.dp),
                        background = Color.White, content = Color.Black)
                    CircleButton(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_play_last), onAddLater, size = 48.dp,
                        background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                }
            }
        }
        Box(Modifier.padding(top = 40.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
    }
}

/** "Álbum • 12 canciones" bajo el título de la colección. */
@Composable
private fun collectionMeta(route: Route.Collection, count: Int?): String = listOfNotNull(
    stringResource(if (route.isPlaylist) R.string.label_playlist else R.string.label_album),
    count?.let { pluralStringResource(R.plurals.song_count, it, it) },
).joinToString(" • ")

private fun Color.darker(f: Float) = Color(red * f, green * f, blue * f, alpha)

@Composable
fun CollectionScreen(remote: CiderRemote, route: Route.Collection, contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val tracks by produceState<Result<List<TrackRow>>?>(null, route) {
        value = runCatching { remote.api.tracks(route) }
    }
    val cover by produceState<AnimatedCover?>(null, route) {
        value = runCatching { remote.api.animatedCover(route.kind, route.id) }.getOrNull()
    }
    // Color oficial de Apple (catálogo) si lo hay; si no, el color medio de la carátula.
    val official = cover?.bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() }
    val bg = (official ?: rememberArtworkColor(route.artwork) ?: Color(0xFF2C2C2E)).asBackground()
    val wideScreen = wide
    // En tablet la portada va en tarjeta (vídeo cuadrado); el vertical a todo el ancho sería enorme.
    val tall = cover?.tall?.takeUnless { wideScreen }
    // Con portada a todo el ancho, el título y la lista suben sobre el degradado.
    val heroOverlap = if (tall != null) (-64).dp else 0.dp
    val listOverlap = if (tall != null) (-40).dp else 0.dp
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    fun play(shuffle: Boolean, startAt: Int? = null) = scope.launch {
        runCatching {
            val list = tracks?.getOrNull()
            if (startAt == null || startAt <= 0 || list == null || startAt >= list.size) {
                remote.api.playCollection(route.kind, route.id, shuffle)
                return@runCatching
            }
            // La API de Cider no puede empezar una colección en la canción N (play-collection
            // arranca en la primera y saltar después se oye). Así que: reproducir la canción
            // tocada y encolar detrás las siguientes con "a continuación" en orden inverso,
            // para que queden en orden y por delante de lo que añada la reproducción automática.
            val chosen = list[startAt]
            remote.api.playItem(chosen.type, chosen.id)
            list.drop(startAt + 1).take(MAX_FOLLOWING).asReversed().forEach {
                remote.api.queueAddNext(it.type, it.id)
            }
        }
    }
    LightStatusBarIcons()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to bg.darker(0.6f), 0.35f to bg))) {
        LazyVerticalGrid(columns = GridCells.Fixed(1), contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            fullWidth {
                if (wideScreen) WideCollectionHeader(
                    route, cover?.square, tracks?.getOrNull()?.size,
                    onPlay = { play(false) }, onShuffle = { play(true) },
                    onAddLater = { scope.launch { runCatching { remote.api.queueAddLater(route.kind, route.id) } } },
                ) else
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tall != null) {
                        // Portada animada vertical (3:4) a todo el ancho; el título se apoya en el degradado.
                        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                            AnimatedCover(tall, Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = route.artwork?.sized(1200), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to bg)))
                        }
                    } else {
                        // Sin vídeo: tarjeta de 257 dp centrada, a 125 dp del borde superior.
                        Spacer(Modifier.height(125.dp))
                        ArtworkImage(
                            route.artwork, 257.dp, px = 800, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.shadow(18.dp, RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    Column(
                        Modifier.offset(y = heroOverlap).padding(horizontal = Margin),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(route.title, style = Apple.type.title2, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!route.subtitle.isNullOrBlank()) Text(
                            route.subtitle, style = Apple.type.title3.copy(fontWeight = FontWeight.Normal),
                            color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val count = tracks?.getOrNull()?.size
                        val meta = collectionMeta(route, count)
                        Text(
                            meta, style = Apple.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.62f), modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(
                            Modifier.padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircleButton(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), { play(true) }, size = 48.dp,
                                background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                            PillButton(stringResource(R.string.action_play), Icons.Filled.PlayArrow, { play(false) }, Modifier.width(160.dp),
                                background = Color.White, content = Color.Black)
                            CircleButton(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_play_last),
                                { scope.launch { runCatching { remote.api.queueAddLater(route.kind, route.id) } } }, size = 48.dp,
                                background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                        }
                        // Línea sobre la primera canción, de margen a margen.
                        Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
                    }
                }
            }
            val result = tracks
            val list = result?.getOrNull()
            when {
                result == null -> fullWidth { Box(Modifier.offset(y = listOverlap)) { Loading() } }
                list == null -> fullWidth { EmptyState(stringResource(R.string.songs_load_error), result.exceptionOrNull()?.message) }
                list.isEmpty() -> fullWidth { EmptyState(stringResource(R.string.empty_songs)) }
                else -> items(list.size) { i ->
                    val t = list[i]
                    var rowMenu by remember { mutableStateOf(false) }
                    Box(Modifier.offset(y = listOverlap)) {
                        QueueSwipe(remote, t.type, t.id) { ListRow(
                            title = t.title,
                            titleStyle = Apple.type.callout,
                            titleColor = Color.White,
                            subtitle = if (route.isPlaylist) t.artist else null,
                            leading = {
                                if (route.isPlaylist) ArtworkImage(t.artwork, 44.dp, shape = RoundedCornerShape(4.dp))
                                else Text(
                                    (t.number ?: (i + 1)).toString(), style = Apple.type.callout,
                                    color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(start = 8.dp),
                                )
                            },
                            leadingWidth = if (route.isPlaylist) 58.dp else 32.dp,
                            minHeight = if (route.isPlaylist) 60.dp else 48.dp,
                            dividerColor = Color.White.copy(alpha = 0.14f),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // En tablet, la duración antes del ⋮ (Apple Music en tablet).
                                    if (wideScreen) t.durationMs?.let {
                                        Text(formatTime(it / 1000.0), style = Apple.type.subhead.copy(fontSize = Apple.type.footnote.fontSize),
                                            color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(end = 12.dp))
                                    }
                                    MoreButton(Color.White.copy(alpha = 0.8f)) { rowMenu = true }
                                }
                            },
                            onClick = { play(false, startAt = i) },
                            onLongClick = { rowMenu = true },
                        ) }
                        Box(Modifier.align(Alignment.TopEnd)) { PlayMenu(remote, rowMenu, { rowMenu = false }, t.type, t.id) }
                    }
                }
            }
            if (!list.isNullOrEmpty()) fullWidth {
                val minutes = (list.sumOf { it.durationMs ?: 0L } / 60_000).toInt()
                Text(
                    stringResource(
                        R.string.collection_footer,
                        pluralStringResource(R.plurals.song_count, list.size, list.size),
                        pluralStringResource(R.plurals.minute_count, minutes, minutes),
                    ),
                    style = Apple.type.body, color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.offset(y = listOverlap).padding(start = Margin, top = 24.dp),
                )
            }
        }
        // El ⋮ de arriba a la derecha lo pone MainScreen (fuera de la página, como el de volver).
    }
}
