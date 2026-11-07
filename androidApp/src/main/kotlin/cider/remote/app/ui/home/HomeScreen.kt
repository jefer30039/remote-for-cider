package cider.remote.app.ui.home

import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import cider.remote.app.ui.components.AnimatedCover
import cider.remote.app.ui.components.CoverPlayerPool
import cider.remote.app.ui.components.rememberCoverPlayerPool
import cider.remote.app.ui.components.asBackground
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cider.remote.CiderRemote
import cider.remote.HomeItem
import cider.remote.BrowsePage
import cider.remote.HomeShelf
import cider.remote.browse
import cider.remote.ShelfKind
import androidx.compose.ui.platform.LocalContext
import cider.remote.app.app
import cider.remote.app.data.rememberCached
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LocalWindowLayout
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.builtins.ListSerializer
import cider.remote.app.ui.Route
import cider.remote.app.ui.components.CircleButton
import cider.remote.app.ui.components.ConnectionMenuButton
import cider.remote.app.ui.components.EmptyState
import cider.remote.app.ui.components.LargeTitle
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.library.PlayMenu
import cider.remote.home
import cider.remote.model.Artwork
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Medidas de Apple Music para Android (dp): teléfono / tablet (Xiaomi Pad 7 Pro).
private val wide: Boolean @Composable get() = LocalWindowLayout.current.expanded
private val Margin: Dp @Composable get() = LocalWindowLayout.current.margin
private val Gap: Dp @Composable get() = if (wide) 20.dp else 12.dp
private val HeroWidth: Dp @Composable get() = if (wide) 253.dp else 240.dp
private val CoverSize: Dp @Composable get() = if (wide) 198.dp else 156.dp

/** Inicio de Apple Music ("listen-now"): estanterías de recomendaciones personales. */
@Composable
fun HomeScreen(remote: CiderRemote, host: String, contentPadding: PaddingValues, onOpen: (Route) -> Unit, onForget: () -> Unit) {
    val offset = remember { ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("xxx")) }
    ShelvesScreen(remote, host, "inicio", Route.Home.label(), contentPadding, onOpen, onForget) { remote.api.home(offset) }
}

/** Pestañas Nuevo y Radio: las páginas editoriales de Apple Music. */
@Composable
fun BrowseScreen(remote: CiderRemote, host: String, page: BrowsePage, title: String, contentPadding: PaddingValues, onOpen: (Route) -> Unit, onForget: () -> Unit) {
    ShelvesScreen(remote, host, "browse-${page.grouping}", title, contentPadding, onOpen, onForget) { remote.api.browse(page) }
}

@Composable
private fun ShelvesScreen(
    remote: CiderRemote,
    host: String,
    cacheKey: String,
    title: String,
    contentPadding: PaddingValues,
    onOpen: (Route) -> Unit,
    onForget: () -> Unit,
    load: suspend () -> List<HomeShelf>,
) {
    val cache = LocalContext.current.app.contentCache
    var failure by remember { mutableStateOf<Throwable?>(null) }
    val cached by cache.rememberCached(host, cacheKey, ListSerializer(HomeShelf.serializer())) {
        runCatching { load() }.onFailure { failure = it }.getOrThrow()
    }
    // Sin caché y con fallo: mostrar el error; sin caché y sin fallo: cargando.
    val shelves: Result<List<HomeShelf>>? = cached?.let { Result.success(it) } ?: failure?.let { Result.failure(it) }
    // En tablet no hay título grande: las estanterías empiezan bajo la barra superior.
    val showTitle = !wide
    // Reproductores de las portadas animadas, compartidos por toda la pantalla; en pausa
    // mientras la lista se desplaza y hasta 600 ms después de pararse (cada fotograma de vídeo
    // obliga a HWUI a importar la textura y redibujar el vidrio: en reposo da igual, al desplazar no).
    val players = rememberCoverPlayerPool()
    val listState = rememberLazyListState()
    cider.remote.app.ui.ScrollToTopEffect { listState.animateScrollToItem(0) }
    LaunchedEffect(listState, players) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) players.paused = true else { delay(600); players.paused = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        if (showTitle) item {
            LargeTitle(title)  // el botón ⋮ flota encima (MainScreen)
        }
        val result = shelves
        val list = result?.getOrNull()
        when {
            result == null -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apple.colors.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            list == null -> item { EmptyState(stringResource(R.string.home_load_error), result.exceptionOrNull()?.message) }
            else -> items(list, key = { (it.title ?: "") + it.kind + it.items.first().id }) { shelf ->
                Shelf(remote, shelf, onOpen, players)
            }
        }
    }
}

/** Estantería de Apple Music (título y fila de portadas); también la usa la página de artista. */
@Composable
fun Shelf(remote: CiderRemote, shelf: HomeShelf, onOpen: (Route) -> Unit, players: CoverPlayerPool? = null) {
    val scope = rememberCoroutineScope()
    // Estaciones: se reproducen; el resto abre su página.
    val open: (HomeItem) -> Unit = { item ->
        val route = item.toRoute()
        if (route != null) onOpen(route) else scope.launch { runCatching { remote.api.playItem(item.type, item.id) } }
    }
    Column(Modifier.padding(top = 18.dp, bottom = 10.dp)) {
        shelf.title?.let {
            Text(
                it, style = Apple.type.title2, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Margin, end = Margin, bottom = 12.dp),
            )
        }
        when (shelf.kind) {
            ShelfKind.Hero -> {
                // Las portadas animadas solo se reproducen en las tarjetas al menos medio visibles.
                val state = rememberLazyListState()
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(horizontal = Margin),
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    itemsIndexed(shelf.items, key = { _, it -> it.type + it.id }) { i, item ->
                        val playing by remember(state) { derivedStateOf { state.mostlyVisible(i) } }
                        HeroCard(item, playing, players) { open(item) }
                    }
                }
            }
            ShelfKind.SuperHero -> shelf.items.firstOrNull()?.let { SuperHeroCard(it) { open(it) } }
            ShelfKind.Feature -> LazyRow(
                contentPadding = PaddingValues(horizontal = Margin),
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                items(shelf.items, key = { it.type + it.id }) {
                    FeatureCard(it, Modifier.fillParentMaxWidth(if (wide) 0.45f else 0.88f)) { open(it) }
                }
            }
            ShelfKind.Songs -> LazyRow(
                contentPadding = PaddingValues(horizontal = Margin),
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                // Columnas de cuatro canciones, como Apple Music.
                items(shelf.items.chunked(4), key = { it.first().id }) { column ->
                    Column(Modifier.fillParentMaxWidth(if (wide) 0.3f else 0.88f)) {
                        column.forEachIndexed { i, song -> SongRow(remote, song, divider = i < column.lastIndex) { open(song) } }
                    }
                }
            }
            ShelfKind.Covers -> {
                // "Artistas favoritos" son estaciones de artista: Apple las pinta en círculo
                // ("rtist" coincide con "Artistas" y con "Artists").
                val circles = shelf.items.all { it.type == "artists" || it.type == "library-artists" } ||
                    (shelf.items.all { it.type == "stations" } && shelf.title?.contains("rtist") == true)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Margin),
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    items(shelf.items, key = { it.type + it.id }) { CoverTile(remote, it, circles) { open(it) } }
                }
            }
        }
    }
}

fun HomeItem.toRoute(): Route? = when {
    type.endsWith("albums") || type.endsWith("playlists") ->
        Route.Collection(kind = type, id = id, title = name, subtitle = subtitle.takeIf { type.endsWith("albums") }, artwork = artwork)
    type == "artists" -> Route.Artist(id, name, catalog = true)
    type == "library-artists" -> Route.Artist(id, name, catalog = false)
    else -> null
}

private fun Artwork.color(): Color? = bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() }

/**
 * Tarjeta vertical 3:4 de 240 dp (Apple Music para Android, capturas del 2026-11-06).
 * Con arte editorial vertical (`superHeroTall`), la imagen llena la tarjeta: esas
 * imágenes ya traen abajo su franja de color para el texto, así que no se pone nada
 * encima. Sin él, la carátula cuadrada va arriba con margen de 16 dp sobre el color
 * de la carátula (atenuado si es muy claro). Texto: título blanco, subtítulo blanco al
 * 80 % y el motivo en un pastel del color de la tarjeta (Apple no usa los textColor
 * del JSON: en estas imágenes suelen ser casi negros).
 */
@Composable
private fun HeroCard(item: HomeItem, playing: Boolean, players: CoverPlayerPool?, onClick: () -> Unit) {
    val tall = item.tallArtwork
    val base = (tall ?: item.artwork)?.color() ?: Color(0xFF2C2C2E)
    val density = LocalDensity.current
    Box(
        Modifier.width(HeroWidth).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp))
            .background(if (tall != null) base else base.heroCardBackground())
            .combinedClickableCompat(onClick = onClick),
    ) {
        if (tall != null) AnimatedCover(
            item.tallVideo, Modifier.fillMaxSize(), playing = playing, pool = players,
            maxVideoSize = with(density) { IntSize(HeroWidth.roundToPx(), (HeroWidth * 4 / 3).roundToPx()) },
        ) {
            AsyncImage(
                model = tall.sized(600, 800), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else AsyncImage(
            model = item.artwork?.sized(600), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
        )
        // Las imágenes fijas traen abajo su franja para el texto; los vídeos no siempre
        // (Sufjan Stevens): velo hacia el color oscurecido de la carátula en el tercio inferior.
        if (tall != null && item.tallVideo != null) Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to base.asBackground(0.03f).copy(alpha = 0.75f))),
        )
        // Medidas de la captura de Apple Music: título ~15 sp, motivo y subtítulo ~13 sp,
        // líneas juntas (18/16 sp) y 12 dp hasta el borde inferior; así el bloque no tapa
        // la parte baja de la imagen (el "2026" de "Éxitos virales").
        val small = Apple.type.footnote.copy(lineHeight = 16.sp)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            item.reason?.let {
                Text(it, style = small, color = base.heroReasonTint(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name, style = Apple.type.subhead.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (item.explicit) ExplicitBadge()
            }
            item.subtitle?.let {
                Text(it, style = small, color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Si el elemento [index] de la fila está al menos a la mitad dentro de la ventana. */
private fun LazyListState.mostlyVisible(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val start = maxOf(item.offset, info.viewportStartOffset)
    val end = minOf(item.offset + item.size, info.viewportEndOffset)
    return end - start > item.size / 2
}

/** "E" de contenido explícito, como la de Apple Music junto al título. */
@Composable
fun ExplicitBadge() {
    val description = stringResource(R.string.explicit)
    Box(
        Modifier.padding(start = 6.dp).size(15.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.62f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text("E", style = Apple.type.caption.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold), color = Color.Black.copy(alpha = 0.75f))
    }
}

/**
 * Pastel del color de la tarjeta para el motivo (medido en Apple Music: rojo → #ffbdba,
 * oliva → #fff9c9, azul marino → #d6eeff; sobre negro o gris, gris claro).
 */
private fun Color.heroReasonTint(): Color {
    // Gris o casi negro (poca diferencia entre canales): gris claro, sin inventar un tono.
    if (maxOf(red, green, blue) - minOf(red, green, blue) < 0.1f) return Color(0xFF9A9A9A)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = 0.35f; hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Fondo de la tarjeta sin arte vertical: el color de la carátula, ni muy claro ni muy oscuro (blanco → gris #9e9e9e). */
private fun Color.heroCardBackground(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    if (hsv[1] > 0.08f) hsv[1] = hsv[1].coerceAtLeast(0.35f)
    hsv[2] = hsv[2].coerceIn(0.3f, 0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Banner panorámico a todo el ancho. */
@Composable
private fun SuperHeroCard(item: HomeItem, onClick: () -> Unit) {
    val art = item.wideArtwork ?: item.artwork
    val tint = art?.color() ?: Color(0xFF2C2C2E)
    Box(
        Modifier.padding(horizontal = Margin).fillMaxWidth().aspectRatio(2.4f).clip(RoundedCornerShape(10.dp))
            .background(tint).combinedClickableCompat(onClick = onClick),
    ) {
        AsyncImage(
            model = art?.let { it.url?.replace("{w}", "1200")?.replace("{h}", "500")?.replace("{f}", "jpg")?.replace("{c}", "sr") },
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(item.name, style = Apple.type.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, style = Apple.type.footnote, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Portada cuadrada de 156 dp con título y subtítulo debajo; pulsación larga = menú. */
@Composable
private fun CoverTile(remote: CiderRemote, item: HomeItem, circle: Boolean, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val shape = if (circle) CircleShape else RoundedCornerShape(6.dp)
    Column(
        Modifier.width(CoverSize).combinedClickableCompat(
            onClick = onClick,
            onLongClick = if (item.type == "stations") null else ({ menu = true }),
        ),
    ) {
        Box {
            AsyncImage(
                model = item.artwork?.sized(400), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(CoverSize).clip(shape).background(Apple.colors.fill),
            )
            PlayMenu(remote, menu, { menu = false }, item.type, item.id)
        }
        Text(
            item.name, style = Apple.type.subhead, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        item.subtitle?.takeIf { item.type != "stations" }?.let {
            Text(it, style = Apple.type.subhead, color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Tarjeta destacada de Nuevo y Radio: etiqueta, título y subtítulo encima de una
 * imagen panorámica 16:10 (la carátula editorial recortada por el servidor de Apple).
 */
@Composable
private fun FeatureCard(item: HomeItem, modifier: Modifier, onClick: () -> Unit) {
    val art = item.wideArtwork
    Column(modifier.combinedClickableCompat(onClick = onClick)) {
        item.reason?.let {
            Text(
                it.uppercase(), style = Apple.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(item.name, style = Apple.type.title3, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.subtitle.orEmpty(), style = Apple.type.title3.copy(fontWeight = FontWeight.Normal),
            color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = art?.cropped(1200, 750) ?: item.artwork?.sized(750), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp))
                .background((art ?: item.artwork)?.color() ?: Apple.colors.fill),
        )
    }
}

/** Fila de canción (carátula de 48 dp, título y artista); tocar la reproduce, mantener abre el menú. */
@Composable
private fun SongRow(remote: CiderRemote, item: HomeItem, divider: Boolean, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(64.dp).combinedClickableCompat(onClick = onClick, onLongClick = { menu = true }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = item.artwork?.sized(150), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Apple.colors.fill),
            )
            PlayMenu(remote, menu, { menu = false }, item.type, item.id)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, style = Apple.type.subhead, color = Apple.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let { Text(it, style = Apple.type.subhead, color = Apple.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (divider) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(0.5.dp).background(Apple.colors.separator))
        }
    }
}
