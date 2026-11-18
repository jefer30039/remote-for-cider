package cider.remote.app.ui.library

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.sp
import cider.remote.CiderApi
import cider.remote.albumArtist
import cider.remote.app.ui.home.toRoute
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
import cider.remote.app.ui.components.pageBackground
import cider.remote.app.ui.components.fadeInto
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
import kotlinx.coroutines.async
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
    val homeGrid = rememberLazyGridState()
    cider.remote.app.ui.ScrollToTopEffect { homeGrid.animateScrollToItem(0) }
    // Agregados recientemente: la primera página sale de la caché y las siguientes, de 10 en 10, al
    // acercarse al final (Apple da hasta 150, como en su app). Las anteriores solo viven en memoria.
    val older = remember { mutableStateListOf<AmItem>() }
    var olderEnd by remember { mutableStateOf(false) }
    var olderLoading by remember { mutableStateOf(false) }
    val recentAll = recent?.let { first -> (first + older).distinctBy { it.type + it.id } }
    val nearEnd by remember {
        derivedStateOf {
            val info = homeGrid.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, recent != null, older.size) {
        val first = recent ?: return@LaunchedEffect
        if (!nearEnd || olderEnd || olderLoading || first.size < RecentPage) return@LaunchedEffect
        olderLoading = true
        runCatching { remote.api.recentlyAdded(RecentPage, offset = first.size + older.size) }
            .onSuccess { page -> older += page; if (page.size < RecentPage) olderEnd = true }
            .onFailure { olderEnd = true }
        olderLoading = false
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns(160.dp, 2)),
        state = homeGrid,
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
        val items = recentAll
        if (items == null) fullBleed { Loading() }
        else {
            items(items, key = { it.type + it.id }) { item ->
                AlbumTile(
                    title = item.name,
                    subtitle = item.attributes?.artistName ?: item.attributes?.curatorName,
                    artwork = item.artwork,
                    onClick = { item.toRoute()?.let(onOpen) },
                )
            }
            if (olderLoading) fullBleed { Loading() }
        }
    }
}

/** Agregados recientemente por petición: el máximo que acepta Apple. */
private const val RecentPage = 10

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
    // Solo si la carga tarda: un indicador girando obliga a repintar el vidrio en cada fotograma
    // (también durante la transición de entrada) y en cargas rápidas solo parpadea.
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(600); show = true }
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        if (show) CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        else Spacer(Modifier.size(24.dp))
    }
}

/**
 * Canciones de los álbumes y playlists abiertos hace poco: al volver a abrir uno, la lista está
 * desde el primer fotograma (que se compone antes de que la página empiece a moverse) y se
 * refresca detrás.
 */
private val recentTracks = android.util.LruCache<String, List<TrackRow>>(24)

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
    var creatingPlaylist by remember { mutableStateOf(false) }
    val listScope = rememberCoroutineScope()
    val listContext = androidx.compose.ui.platform.LocalContext.current
    if (creatingPlaylist) cider.remote.app.ui.components.PlaylistNameDialog(
        title = stringResource(R.string.playlist_new_title), initial = "", confirm = stringResource(R.string.action_create),
        onDismiss = { creatingPlaylist = false },
    ) { name ->
        creatingPlaylist = false
        listScope.launch {
            val created = runCatching { remote.api.createPlaylist(name) }.getOrNull()
            if (created == null) {
                android.widget.Toast.makeText(listContext, listContext.getString(R.string.playlist_create_failed), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            items.clear(); total = null; loadMore()
            onOpen(Route.Collection("library-playlists", created, name, null, null))
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) layout.columns(160.dp, 2) else 1),
        state = gridState,
        contentPadding = if (grid) gridPadding(contentPadding) else contentPadding,
        horizontalArrangement = Arrangement.spacedBy(if (grid) GridGap else 0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val full: (@Composable () -> Unit) -> Unit = { c -> if (grid) fullBleed(c) else fullWidth(c) }
        full { LargeTitle(route.label()) }
        // "Nueva playlist…" (Apple Music): la crea vacía y la abre.
        // Mismas medidas que las filas de playlists (EntryRow): carátula de 56 dp y texto a 70 dp.
        if (route == Route.Playlists) full {
            ListRow(
                title = stringResource(R.string.playlist_new),
                titleColor = Apple.colors.accent,
                leading = {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(5.dp)).background(Apple.colors.fill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = Apple.colors.accent, modifier = Modifier.size(28.dp))
                    }
                },
                leadingWidth = 70.dp,
                minHeight = 68.dp,
                onClick = { creatingPlaylist = true },
            )
        }
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
            title = RenamedPlaylists[e.id] ?: e.title,
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
        Box(Modifier.align(Alignment.TopEnd)) { PlayMenu(remote, menu, { menu = false }, e.playType, e.id, RenamedPlaylists[e.id] ?: e.title) }
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
/**
 * Nombres cambiados en esta sesión: la cabecera de la playlist y las listas (en caché) los
 * enseñan al momento sin esperar a recargar.
 */
val RenamedPlaylists = androidx.compose.runtime.mutableStateMapOf<String, String>()

@Composable
fun PlayMenu(remote: CiderRemote, expanded: Boolean, onDismiss: () -> Unit, type: String, id: String, name: String = "") {
    // Las canciones tienen el menú completo de iOS; álbumes y playlists, el corto.
    if (type.endsWith("songs")) { SongMenu(remote, expanded, onDismiss, type, id); return }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    fun run(block: suspend () -> Unit) { onDismiss(); scope.launch { runCatching { block() } } }
    // Playlists de la biblioteca: cambiar el nombre (Cider rechaza las inteligentes y las que no son tuyas).
    var renaming by remember { mutableStateOf(false) }
    if (renaming) cider.remote.app.ui.components.PlaylistNameDialog(
        title = stringResource(R.string.playlist_rename_title), initial = RenamedPlaylists[id] ?: name, confirm = stringResource(R.string.action_rename),
        onDismiss = { renaming = false },
    ) { name ->
        renaming = false
        scope.launch {
            val ok = runCatching { remote.api.renamePlaylist(id, name) }.isSuccess
            if (ok) RenamedPlaylists[id] = name
            android.widget.Toast.makeText(
                context, context.getString(if (ok) R.string.playlist_renamed else R.string.playlist_rename_failed), android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    GlassMenu(expanded, onDismiss, width = 240.dp) {
        MenuRow(AppleIcons.Play, stringResource(R.string.action_play)) { run { remote.api.playCollection(type, id) } }
        MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.action_play_next)) { run { remote.api.queueAddNext(type, id) } }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.action_play_last)) { run { remote.api.queueAddLater(type, id) } }
        if (type == "library-playlists") MenuRow(Icons.Filled.Edit, stringResource(R.string.playlist_rename)) { onDismiss(); renaming = true }
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

private data class TrackRow(
    val type: String, val id: String, val number: Int?, val title: String, val artist: String?, val durationMs: Long?, val artwork: Artwork?,
    /** Falso en las canciones del catálogo aún no disponibles (sin `playParams`): se ven atenuadas y no se tocan. */
    val available: Boolean = true,
)

private suspend fun CiderApi.tracks(c: Route.Collection): List<TrackRow> = when (c.kind) {
    "library-albums", "library-playlists" -> kotlinx.coroutines.coroutineScope {
        val album = c.kind == "library-albums"
        // Cider no dice cuáles no se pueden reproducir: se pregunta a Apple a la vez. Si falla, todas disponibles.
        val unplayable = async { runCatching { libraryUnplayable(if (album) "albums" else "playlists", c.id) }.getOrDefault(emptySet()) }
        val songs = allPages { if (album) albumSongs(c.id, it, MAX_PAGE) else playlistSongs(c.id, it, MAX_PAGE) }
        val off = unplayable.await()
        songs.map {
            TrackRow("library-songs", it.id, if (album) it.trackNumber else null, it.name, it.artistName, it.durationMs, it.artwork,
                available = it.id !in off)
        }
    }
    else -> catalogTracks(c.kind, c.id).mapIndexed { i, t ->
        TrackRow(t.type, t.id, i + 1, t.name, t.attributes?.artistName, t.attributes?.durationInMillis, t.artwork, available = t.attributes?.playParams != null)
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
    info: AnimatedCover?,
    trackTraits: List<String>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddLater: () -> Unit,
    onArtist: (() -> Unit)? = null,
    secondary: Color = Color.White.copy(alpha = 0.62f),
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.padding(start = Margin, end = Margin, top = top + 74.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedCover(
                squareVideo,
                Modifier.size(300.dp).shadow(18.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
                placeholderIsPreview = info?.squarePreview != null,
            ) {
                // Espera: el fotograma de muestra del vídeo cuadrado; sin él, la carátula fija.
                val preview = info?.squarePreview
                if (preview != null) AsyncImage(
                    model = preview.sized(800, 800), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                ) else ArtworkImage(route.artwork, 300.dp, px = 800, shape = RoundedCornerShape(8.dp))
            }
            Column(Modifier.weight(1f).padding(start = 32.dp)) {
                Text(RenamedPlaylists[route.id] ?: route.title, style = Apple.type.title1, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!route.subtitle.isNullOrBlank()) Text(
                    route.subtitle, style = Apple.type.title1.copy(fontWeight = FontWeight.Normal),
                    color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickableCompat(onClick = onArtist),
                )
                CollectionMeta(route, count, info, style = Apple.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = secondary,
                    modifier = Modifier.padding(top = 8.dp), trackTraits = trackTraits)
                Row(
                    Modifier.padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), onShuffle, size = 48.dp,
                        background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                    PillButton(stringResource(R.string.action_play), Icons.Filled.PlayArrow, onPlay, Modifier.width(160.dp),
                        background = Color.White, content = Color.Black)
                    CircleButton(AppleIcons.PlayLast, stringResource(R.string.action_play_last), onAddLater, size = 48.dp,
                        background = Color.White.copy(alpha = 0.1f), tint = Color.White, border = null, iconSize = 24.dp)
                }
            }
        }
        Box(Modifier.padding(top = 40.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
    }
}

/**
 * Línea bajo el título, como Apple Music: en álbumes "Género • año" y, si los tiene, el logotipo de
 * Dolby Atmos y la onda de Lossless / Hi-Res Lossless (rasgos del álbum de catálogo, [info]); en
 * playlists o mientras no llegan esos datos, "Álbum • 12 canciones".
 */
@Composable
private fun CollectionMeta(
    route: Route.Collection, count: Int?, info: AnimatedCover?, style: androidx.compose.ui.text.TextStyle,
    color: Color, modifier: Modifier = Modifier, textAlign: TextAlign? = null,
    /** Rasgos de las canciones (playlists: Apple no los da para la playlist). */
    trackTraits: List<String> = emptyList(),
) {
    val updated = info?.lastModified?.takeIf { route.isPlaylist }?.let { updatedLabel(it) }
    val parts = when {
        !route.isPlaylist && (info?.genre != null || info?.releaseYear != null) -> listOfNotNull(info.genre, info.releaseYear)
        updated != null -> listOf(updated)
        else -> listOfNotNull(
            stringResource(if (route.isPlaylist) R.string.label_playlist else R.string.label_album),
            count?.let { pluralStringResource(R.plurals.song_count, it, it) },
        )
    }
    val traits = info?.audioTraits.orEmpty() + trackTraits
    val atmos = "atmos" in traits
    val lossless = when {
        "hi-res-lossless" in traits -> "Hi-Res Lossless"
        "lossless" in traits -> "Lossless"
        else -> null
    }
    // Separador como Apple: espacio normal + muy fino (U+200A) a cada lado y el punto al 80 %, algo subido.
    fun androidx.compose.ui.text.AnnotatedString.Builder.sep() {
        append(" \u200A")
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.8.em, baselineShift = androidx.compose.ui.text.style.BaselineShift(0.12f))); append("•"); pop()
        append("\u200A ")
    }
    val text = androidx.compose.ui.text.buildAnnotatedString {
        parts.forEachIndexed { i, p -> if (i > 0) sep(); append(p) }
        if (atmos) { sep(); appendInlineContent("atmos", "Dolby Atmos") }
        if (lossless != null) { sep(); appendInlineContent("lossless", "Lossless"); append(" "); append(lossless) }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Medidas en em con Inter (mayúsculas = 0,727 em), tomadas de Apple Music para Android ("CAPRISONGS"):
    // Dolby Atmos = logotipo oficial (110,76 × 15,64, mayúsculas 12,02, línea base a 12,03) con mayúsculas
    // 1,08 veces las del texto, apoyado en la línea base (la "y" baja con un desplazamiento); Lossless =
    // logo de Apple Lossless (res/drawable/apple_lossless.xml) de 2,19 veces las mayúsculas de ancho.
    val inline = mapOf(
        "atmos" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(7.23.em, 1.022.em, androidx.compose.ui.text.PlaceholderVerticalAlign.AboveBaseline),
        ) {
            val drop = with(density) { (style.fontSize * 0.236f).toDp() }
            Icon(
                androidx.compose.ui.res.painterResource(R.drawable.dolby_atmos), null, tint = color,
                modifier = Modifier.fillMaxSize().offset(y = drop),
            )
        },
        "lossless" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(1.59.em, 0.954.em, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
        ) { Icon(androidx.compose.ui.res.painterResource(R.drawable.apple_lossless), null, tint = color, modifier = Modifier.fillMaxSize()) },
    )
    Text(text, style = style, color = color, modifier = modifier, textAlign = textAlign, inlineContent = inline)
}

/** "Actualizada hace 2 d" (Apple Music: "Updated 2d ago"); pasado un mes, la fecha. */
@Composable
private fun updatedLabel(iso: String): String? {
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return null
    val mins = java.time.Duration.between(then, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        mins < 1 -> stringResource(R.string.updated_now)
        mins < 60 -> stringResource(R.string.updated_ago, stringResource(R.string.ago_minutes, mins.toInt()))
        mins < 24 * 60 -> stringResource(R.string.updated_ago, stringResource(R.string.ago_hours, (mins / 60).toInt()))
        mins < 7 * 24 * 60 -> stringResource(R.string.updated_ago, stringResource(R.string.ago_days, (mins / (24 * 60)).toInt()))
        mins < 30 * 24 * 60 -> stringResource(R.string.updated_ago, stringResource(R.string.ago_weeks, (mins / (7 * 24 * 60)).toInt()))
        else -> stringResource(
            R.string.updated_on,
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .format(then.atZone(java.time.ZoneId.systemDefault())),
        )
    }
}

private fun Color.darker(f: Float) = Color(red * f, green * f, blue * f, alpha)

@Composable
fun CollectionScreen(remote: CiderRemote, route: Route.Collection, contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    // Tocar el nombre del artista (solo álbumes) abre su página; el artista se consulta al tocar.
    val openRoute = cider.remote.app.ui.LocalOpenRoute.current
    val toArtist: (() -> Unit)? = if (route.isPlaylist) null else ({
        scope.launch { runCatching { remote.api.albumArtist(route.kind, route.id) }.getOrNull()?.toRoute()?.let(openRoute) }
    })
    val tracksKey = "${route.kind}/${route.id}"
    val tracks by produceState<Result<List<TrackRow>>?>(recentTracks.get(tracksKey)?.let { Result.success(it) }, route) {
        val fresh = runCatching { remote.api.tracks(route) }
        fresh.getOrNull()?.let { recentTracks.put(tracksKey, it) }
        // Si ya había lista y la consulta falla, se queda la que había.
        if (fresh.isSuccess || value == null) value = fresh
    }
    val cover by produceState<AnimatedCover?>(null, route) {
        value = runCatching { remote.api.animatedCover(route.kind, route.id) }.getOrNull()
    }
    // Playlists: rasgos de audio de sus canciones (de la versión de catálogo; las de la biblioteca sin
    // catálogo no los tienen).
    val catalogPlaylist = if (!route.isPlaylist) null else if (route.kind == "playlists") route.id else cover?.catalogId
    val trackTraits by produceState(emptyList<String>(), catalogPlaylist) {
        value = catalogPlaylist?.let { runCatching { remote.api.playlistAudioTraits(it) }.getOrNull() }.orEmpty()
    }
    // Color oficial de Apple (catálogo) si lo hay; si no, el color medio de la carátula.
    val official = cover?.bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() }
    val bg = (official ?: rememberArtworkColor(route.artwork) ?: Color(0xFF2C2C2E)).pageBackground(dark = androidx.compose.foundation.isSystemInDarkTheme())
    val pageBackground = cider.remote.app.ui.LocalPageBackground.current
    androidx.compose.runtime.SideEffect { pageBackground.value = bg }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { pageBackground.value = null } }
    // Si el color llega cuando la página ya se ve (se calcula de la carátula), se funde en vez
    // de saltar. Se lee al dibujar: la página no se recompone en cada fotograma del fundido.
    val bgShown = androidx.compose.animation.animateColorAsState(bg, androidx.compose.animation.core.tween(350), label = "fondoAlbum")
    // Texto secundario: blanco translúcido, salvo en fondos claros (tema claro con carátulas
    // amarillas u ocres), donde pasa a un tono oscuro del fondo para seguir leyéndose.
    val secondary = remember(bg) { cider.remote.app.ui.components.readableOn(bg, Color.White.copy(alpha = 0.62f)) }
    val more = remember(bg) { cider.remote.app.ui.components.readableOn(bg, Color.White.copy(alpha = 0.8f), target = 3f) }
    val faint = remember(bg) { cider.remote.app.ui.components.readableOn(bg, Color.White.copy(alpha = 0.45f), target = 3f) }
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
            // tocada y encolar detrás las siguientes, en orden (ver CiderRemote.playThenQueue).
            val chosen = list[startAt]
            if (!chosen.available) return@runCatching
            remote.playThenQueue(chosen.type, chosen.id, list.drop(startAt + 1).filter { it.available }.take(MAX_FOLLOWING).map { it.type to it.id })
        }
    }
    LightStatusBarIcons()
    Box(Modifier.fillMaxSize()) {
        // Fondo en su propia capa: el fundido de color solo vuelve a pintar esta capa; dibujado
        // detrás de la lista obligaba a volver a grabar la página entera en cada fotograma.
        Box(Modifier.matchParentSize().graphicsLayer().drawBehind { drawRect(bgShown.value) })
        val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        // Desplazamiento de la cabecera (px); fuera de la pantalla, "muy lejos". Se lee al dibujar.
        val headerScroll: () -> Float = { if (grid.firstVisibleItemIndex == 0) grid.firstVisibleItemScrollOffset.toFloat() else 1e6f }
        LazyVerticalGrid(columns = GridCells.Fixed(1), state = grid, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            fullWidth {
                if (wideScreen) WideCollectionHeader(
                    route, cover?.square, tracks?.getOrNull()?.size, cover, trackTraits, onArtist = toArtist, secondary = secondary,
                    onPlay = { play(false) }, onShuffle = { play(true) },
                    onAddLater = { scope.launch { runCatching { remote.api.queueAddLater(route.kind, route.id) } } },
                ) else
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tall != null) {
                        // Portada animada vertical (3:4) a todo el ancho; el título se apoya en el degradado.
                        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).dissolveOnScroll(headerScroll, threshold = 170.dp, fadeLength = 450.dp)) {
                            // Espera: el fotograma de muestra del vídeo (mismo encuadre y color que su primer
                            // fotograma, como en la hoja); sin él, la carátula fija.
                            val tallPreview = cover?.tallPreview
                            AnimatedCover(tall, Modifier.fillMaxSize(), placeholderIsPreview = tallPreview != null) {
                                AsyncImage(
                                    model = tallPreview?.sized(1200, 1600) ?: route.artwork?.sized(1200), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawRect(fadeInto(bgShown.value)) })
                        }
                    } else {
                        // Sin vídeo: tarjeta de 257 dp centrada, a 125 dp del borde superior.
                        Spacer(Modifier.height(125.dp))
                        ArtworkImage(
                            route.artwork, 257.dp, px = 800, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.dissolveOnScroll(headerScroll, threshold = 105.dp, fadeLength = 200.dp)
                                .shadow(18.dp, RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    Column(
                        Modifier.offset(y = heroOverlap).padding(horizontal = Margin),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(RenamedPlaylists[route.id] ?: route.title, style = Apple.type.title2, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!route.subtitle.isNullOrBlank()) Text(
                            route.subtitle, style = Apple.type.title3.copy(fontWeight = FontWeight.Normal),
                            color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).combinedClickableCompat(onClick = toArtist).padding(horizontal = 6.dp),
                        )
                        val count = tracks?.getOrNull()?.size
                        // 10,3 sp: medido contra Apple Music para Android con el título a la misma escala.
                        CollectionMeta(
                            route, count, cover, style = Apple.type.caption.copy(fontSize = 10.3.sp, fontWeight = FontWeight.SemiBold),
                            color = secondary, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, trackTraits = trackTraits,
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
                            CircleButton(AppleIcons.PlayLast, stringResource(R.string.action_play_last),
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
                    // No disponibles (Apple Music): atenuadas, sin toque, deslizamiento ni menú.
                    Box(Modifier.offset(y = listOverlap).then(if (t.available) Modifier else Modifier.alpha(0.4f))) {
                        QueueSwipe(remote, t.type, t.id, enabled = t.available) { ListRow(
                            title = t.title,
                            titleStyle = Apple.type.callout,
                            titleColor = Color.White,
                            subtitleColor = secondary,
                            // En playlists siempre; en álbumes, cuando la canción es de otro artista (recopilaciones).
                            subtitle = if (route.isPlaylist || (t.artist != null && t.artist != route.subtitle)) t.artist else null,
                            leading = {
                                if (route.isPlaylist) ArtworkImage(t.artwork, 44.dp, shape = RoundedCornerShape(4.dp))
                                else Text(
                                    (t.number ?: (i + 1)).toString(), style = Apple.type.callout,
                                    color = faint, modifier = Modifier.padding(start = 8.dp),
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
                                            color = secondary, modifier = Modifier.padding(end = 12.dp))
                                    }
                                    MoreButton(more) { if (t.available) rowMenu = true }
                                }
                            },
                            onClick = if (t.available) ({ play(false, startAt = i) }) else null,
                            onLongClick = if (t.available) ({ rowMenu = true }) else null,
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
                    style = Apple.type.body, color = secondary,
                    modifier = Modifier.offset(y = listOverlap).padding(start = Margin, top = 24.dp),
                )
            }
            // Álbum de la biblioteca con solo parte de sus canciones: "Ver álbum completo" abre el del catálogo
            // (Apple Music, debajo del pie).
            val catalogAlbum = cover?.catalogId?.takeIf {
                route.kind == "library-albums" && !list.isNullOrEmpty() && (cover?.trackCount ?: Int.MAX_VALUE) > list.size
            }
            if (catalogAlbum != null) fullWidth {
                Column(Modifier.offset(y = listOverlap).padding(top = 20.dp)) {
                    Box(Modifier.padding(horizontal = Margin).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
                    Text(
                        stringResource(R.string.action_show_complete_album), style = Apple.type.body, color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickableCompat(onClick = {
                                openRoute(Route.Collection("albums", catalogAlbum, route.title, route.subtitle, route.artwork))
                            })
                            .padding(horizontal = Margin, vertical = 14.dp),
                    )
                    Box(Modifier.padding(horizontal = Margin).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
                }
            }
        }
        // El ⋮ de arriba a la derecha lo pone MainScreen (fuera de la página, como el de volver).
    }
}

/**
 * Carátula de la cabecera al desplazar (Apple Music, medido en dos grabaciones del usuario): sube al
 * 68 % de la velocidad de la página hasta [threshold] de desplazamiento y ahí se queda fija; a partir de
 * ahí se desenfoca poco a poco con el desplazamiento (radio de 0 a 36 dp en 70 dp de recorrido, curva
 * suave) y a la vez se funde con el fondo a lo largo de [fadeLength] mientras el texto pasa por encima.
 * El desenfoque se dibuja con margen alrededor para que se derrame fuera del borde de la carátula: dentro
 * de sus límites dejaba un rectángulo marcado sobre el fondo. Antes (descartado por brusco): pasar de una
 * copia nítida a otra borrosa en 160 ms al cruzar el umbral. El radio se redondea a pasos de 2 px para no
 * rehacer el efecto en cada píxel. Todo se lee al dibujar: desplazar no recompone la página.
 */
private class BlurCache {
    var radius = -1f
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
    fun get(r: Float): androidx.compose.ui.graphics.RenderEffect {
        if (r != radius) { radius = r; effect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) }
        return effect!!
    }
}

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

@Composable
private fun Modifier.dissolveOnScroll(scroll: () -> Float, threshold: androidx.compose.ui.unit.Dp, fadeLength: androidx.compose.ui.unit.Dp): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val fadePx = with(density) { fadeLength.toPx() }
    val rangePx = with(density) { 70.dp.toPx() }
    val maxBlurPx = with(density) { 36.dp.toPx() }
    val padPx = with(density) { 72.dp.toPx() }
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val cache = remember { BlurCache() }
    return this
        .graphicsLayer {
            val s = scroll().coerceAtLeast(0f)
            translationY = s - minOf(s, thresholdPx) * 0.68f
            alpha = 1f - smooth((s - thresholdPx) / fadePx)
            // Sin búfer intermedio: con alfa < 1 la capa se pintaba en uno del tamaño de la carátula y el
            // desenfoque que se derrama fuera quedaba cortado en recto.
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        }
        .drawWithContent {
            val k = smooth((scroll() - thresholdPx) / rangePx)
            val radius = kotlin.math.round(k * maxBlurPx / 2f) * 2f
            if (radius < 1f) { drawContent(); return@drawWithContent }
            val full = androidx.compose.ui.unit.IntSize(
                kotlin.math.ceil(size.width + 2 * padPx).toInt(), kotlin.math.ceil(size.height + 2 * padPx).toInt(),
            )
            layer.renderEffect = cache.get(radius)
            layer.record(full) { translate(padPx, padPx) { this@drawWithContent.drawContent() } }
            translate(-padPx, -padPx) { drawLayer(layer) }
        }
}
