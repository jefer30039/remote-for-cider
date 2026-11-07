package cider.remote.app.ui.search

import androidx.annotation.StringRes
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cider.remote.CiderRemote
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.app.ui.bleed
import androidx.compose.ui.unit.Dp
import cider.remote.app.ui.Route
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.EmptyState
import cider.remote.app.ui.components.LargeTitle
import cider.remote.app.ui.components.ListRow
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.library.AlbumTile
import cider.remote.app.ui.library.PlayMenu
import cider.remote.app.ui.library.QueueSwipe
import cider.remote.app.ui.library.toRoute
import cider.remote.model.AmItem
import cider.remote.model.SearchCategory
import cider.remote.model.SearchResults
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Teléfono / tablet (Apple Music en Xiaomi Pad: 5 columnas de 114 dp de alto).
private val wide: Boolean @Composable get() = LocalWindowLayout.current.expanded
private val Margin: Dp @Composable get() = LocalWindowLayout.current.margin
private val TileGap: Dp @Composable get() = if (wide) 20.dp else 12.dp

@Composable
fun SearchScreen(remote: CiderRemote, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    var term by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val categories by produceState<List<SearchCategory>>(emptyList(), remote) {
        value = runCatching { remote.api.searchCategories() }.getOrDefault(emptyList())
    }
    val focus = LocalFocusManager.current

    // Busca mientras se escribe, con una pausa para no saturar a Cider.
    LaunchedEffect(term) {
        if (term.isBlank()) { results = null; return@LaunchedEffect }
        delay(350)
        runCatching { remote.api.search(term.trim(), 8) }
            .onSuccess { results = it; error = null }
            .onFailure { error = it.message }
    }

    val layout = LocalWindowLayout.current
    val margin = Margin
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    cider.remote.app.ui.ScrollToTopEffect { grid.animateScrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns(200.dp, 2)),
        state = grid,
        contentPadding = PaddingValues(start = margin, end = margin, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!layout.expanded) full { LargeTitle(Route.Search.label()) }
        full {
            Row(
                Modifier.padding(horizontal = Margin).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Apple.colors.fill).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Apple.colors.secondaryLabel, modifier = Modifier.size(22.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (term.isEmpty()) Text(stringResource(R.string.search_placeholder), style = Apple.type.body, color = Apple.colors.secondaryLabel, maxLines = 1)
                    BasicTextField(
                        value = term, onValueChange = { term = it }, singleLine = true,
                        textStyle = Apple.type.body.copy(color = Apple.colors.label),
                        cursorBrush = SolidColor(Apple.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (term.isNotEmpty()) Box(
                    Modifier.size(32.dp).clip(CircleShape).combinedClickableCompat(onClick = { term = "" }),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Cancel, stringResource(R.string.search_clear), tint = Apple.colors.secondaryLabel, modifier = Modifier.size(18.dp)) }
            }
        }
        full { Spacer(Modifier.height(18.dp)) }
        val r = results
        when {
            term.isBlank() -> items(categories.size, key = { "cat-" + categories[it].id }) { i ->
                CategoryTile(
                    categories[i],
                    onClick = { onOpen(Route.Category(categories[i].id, categories[i].name)) },
                    modifier = Modifier.padding(bottom = TileGap),
                )
            }
            error != null && r == null -> full { EmptyState(stringResource(R.string.search_error), error) }
            r == null -> full {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            listOf(r.songs, r.albums, r.artists, r.playlists).all { it?.data.isNullOrEmpty() } ->
                full { EmptyState(stringResource(R.string.search_no_results), stringResource(R.string.search_try_other)) }
            else -> {
                section(R.string.library_artists, r.artists?.data) { ResultRow(remote, it, onOpen) }
                section(R.string.library_albums, r.albums?.data) { ResultRow(remote, it, onOpen) }
                section(R.string.library_songs, r.songs?.data) { ResultRow(remote, it, onOpen) }
                section(R.string.library_playlists, r.playlists?.data) { ResultRow(remote, it, onOpen) }
            }
        }
    }
}

/** Elemento de ancho completo: ignora el margen horizontal de la cuadrícula. */
private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Margin)) { content() } }

/** Mosaico de categoría: 95 dp de alto, esquinas de 10 dp, nombre en negrita abajo a la izquierda. */
@Composable
private fun CategoryTile(category: SearchCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = category.bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() } ?: Apple.colors.fill
    Box(
        modifier.fillMaxWidth().height(if (wide) 114.dp else 95.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .combinedClickableCompat(onClick = onClick),
    ) {
        AsyncImage(
            model = category.imageUrl, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Text(
            category.name, style = Apple.type.headline.copy(fontWeight = if (wide) FontWeight.Medium else FontWeight.Bold), color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        )
    }
}

private fun LazyGridScope.section(@StringRes title: Int, items: List<AmItem>?, row: @Composable (AmItem) -> Unit) {
    if (items.isNullOrEmpty()) return
    item(key = "h-$title", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Margin)) {
        Text(stringResource(title), style = Apple.type.title3.copy(fontWeight = FontWeight.Bold), color = Apple.colors.label,
            modifier = Modifier.padding(start = Margin, top = 20.dp, bottom = 4.dp))
    } }
    items(items, key = { "$title-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(Margin)) { row(it) } }
}

@Composable
private fun ResultRow(remote: CiderRemote, item: AmItem, onOpen: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val a = item.attributes
    val subtitle = when (item.type) {
        "songs" -> listOfNotNull(stringResource(R.string.label_song), a?.artistName).joinToString(" · ")
        "albums" -> listOfNotNull(stringResource(R.string.label_album), a?.artistName).joinToString(" · ")
        "playlists" -> listOfNotNull(stringResource(R.string.label_playlist), a?.curatorName).joinToString(" · ")
        else -> stringResource(R.string.label_artist)
    }
    val circle = item.type == "artists"
    Box {
        QueueSwipe(remote, item.type, item.id, enabled = !circle) { ListRow(
            title = item.name,
            subtitle = subtitle,
            leading = { ArtworkImage(item.artwork, 48.dp, shape = if (circle) CircleShape else RoundedCornerShape(5.dp)) },
            leadingWidth = 62.dp,
            minHeight = 60.dp,
            onClick = {
                val route = item.toRoute()
                if (route != null) onOpen(route)
                else scope.launch { runCatching { remote.api.playItem(item.type, item.id) } }
            },
            onLongClick = if (circle) null else ({ menu = true }),
        ) }
        if (!circle) Box(Modifier.align(Alignment.TopEnd)) { PlayMenu(remote, menu, { menu = false }, item.type, item.id) }
    }
}

/** Playlists de una categoría, en cuadrícula de 2 columnas. */
@Composable
fun CategoryScreen(remote: CiderRemote, route: Route.Category, contentPadding: PaddingValues, onOpen: (Route) -> Unit) {
    val playlists by produceState<List<AmItem>?>(null, route) {
        value = runCatching { remote.api.curatorPlaylists(route.id) }.getOrDefault(emptyList())
    }
    val layout = LocalWindowLayout.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns(160.dp, 2)),
        contentPadding = PaddingValues(start = Margin, end = Margin, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(layout.gridGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        full { LargeTitle(route.label()) }
        val list = playlists
        when {
            list == null -> full {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            list.isEmpty() -> full { EmptyState(stringResource(R.string.empty_playlists)) }
            else -> items(list, key = { it.id }) { p ->
                AlbumTile(p.name, p.attributes?.curatorName, p.artwork, onClick = { p.toRoute()?.let(onOpen) })
            }
        }
    }
}
