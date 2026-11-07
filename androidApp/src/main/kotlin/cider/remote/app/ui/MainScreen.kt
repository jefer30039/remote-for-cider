package cider.remote.app.ui

import cider.remote.app.ui.liquid.LiquidBottomTab
import cider.remote.app.ui.liquid.LiquidBottomTabs
import cider.remote.app.ui.liquid.CompactBarHeight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cider.remote.BrowsePage
import cider.remote.app.ui.home.BrowseScreen
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.semantics.contentDescription
import cider.remote.app.ui.player.SheetMode
import cider.remote.app.ui.player.WideMiniPlayer
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cider.remote.CiderRemote
import cider.remote.app.data.SavedConnection
import cider.remote.app.ui.components.CircleButton
import cider.remote.app.ui.components.TintedIcon
import cider.remote.app.ui.components.ConnectionMenuButton
import cider.remote.app.ui.components.glass
import cider.remote.app.ui.components.GlassBackdrop
import cider.remote.app.ui.components.glassSource
import cider.remote.app.ui.components.LocalGlassBackdrop
import cider.remote.app.ui.components.GlassCircleButton
import cider.remote.app.ui.components.glassFade
import cider.remote.app.ui.components.glassEdge
import cider.remote.app.ui.components.rememberGlassBackdrop
import cider.remote.app.ui.components.rememberSharedBackdrop
import cider.remote.app.ui.components.LocalPageHidden
import androidx.compose.runtime.CompositionLocalProvider
import cider.remote.app.ui.components.LiquidGlassSupported
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import cider.remote.app.ui.components.rememberBackdropSampler
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.home.HomeScreen
import cider.remote.app.ui.library.ArtistScreen
import cider.remote.app.ui.library.CollectionScreen
import cider.remote.app.ui.library.LibraryHome
import androidx.compose.material.icons.outlined.MoreVert
import cider.remote.app.ui.library.PlayMenu
import cider.remote.app.ui.library.LibraryList
import cider.remote.app.ui.player.MiniPlayer
import cider.remote.app.ui.player.NowPlayingSheet
import cider.remote.app.ui.player.PrefetchNowPlaying
import cider.remote.app.ui.player.rememberSheetState
import cider.remote.app.ui.player.sheetDrag
import cider.remote.app.ui.search.CategoryScreen
import cider.remote.app.ui.search.SearchScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector, val root: Route) {
    Home(R.string.tab_home, AppleIcons.TabHome, Route.Home),
    New(R.string.tab_new, AppleIcons.TabNew, Route.New),
    Radio(R.string.tab_radio, AppleIcons.TabRadio, Route.Radio),
    Library(R.string.tab_library, AppleIcons.TabLibrary, Route.LibraryHome),
    Search(R.string.tab_search, AppleIcons.TabSearch, Route.Search),
}

/** Medidas tomadas de Apple Music para Android (dp). */
private val TabBarHeight = 64.dp
private val MiniHeight = 50.dp
private val BarGap = 6.dp
private val SideMargin = 20.dp

/** Relleno de contenido que deja libre la cabecera con el botón de volver. */
val TopButtonsHeight = 58.dp

@Composable
fun MainScreen(remote: CiderRemote, connection: SavedConnection, onForget: () -> Unit) {
    val state by remote.state.collectAsStateWithLifecycle()
    val layout = LocalWindowLayout.current
    val wide = layout.expanded
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val stacks = remember { Tab.entries.associateWith { mutableStateListOf(it.root) } }
    val sheet = rememberSheetState()
    var sheetMode by rememberSaveable { mutableStateOf(SheetMode.Artwork) }
    var sidebar by rememberSaveable { mutableStateOf(false) }
    val stack = stacks.getValue(tab)
    val back: () -> Unit = { stack.removeAt(stack.lastIndex) }
    val select: (Tab) -> Unit = { selected ->
        // Volver a tocar la pestaña activa vuelve a su raíz.
        if (selected == tab) stacks.getValue(tab).let { while (it.size > 1) it.removeAt(it.lastIndex) }
        tab = selected
    }
    val openSheet: (SheetMode) -> Unit = { sheetMode = it; sheet.open() }

    BackHandler(enabled = sidebar) { sidebar = false }
    BackHandler(enabled = !sheet.isOpen && !sidebar && stack.size > 1, onBack = back)

    val hasTrack = state.track != null
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Apple Music deja la barra a 16 dp del borde, encima del asa de gestos.
    val barBottom: Dp = if (nav > 32.dp) nav + 8.dp else max(nav, 16.dp)
    val bottom = if (wide) barBottom + (if (hasTrack) WideMiniHeight else 0.dp) + 24.dp
    else barBottom + TabBarHeight + (if (hasTrack) MiniHeight + BarGap else 0.dp) + 24.dp
    // En tablet la barra de pestañas va arriba (cápsula de 46 dp) y no hay título grande.
    val wideTop = top + 10.dp + 46.dp + 18.dp

    val haze = rememberGlassBackdrop()
    val sampler = rememberBackdropSampler()
    // Textos e iconos de las piezas de vidrio: blancos sobre contenido oscuro, negros sobre claro
    // (iOS 26), con histéresis para no parpadear en tonos medios.
    // Los colores animados se pasan como lambdas y se leen al dibujar: recomponer la barra de
    // Kyant en cada fotograma de la animación rehacía sus RenderEffect (5–10 ms de hilo UI).
    val themeDark = Apple.colors.isDark
    // Modo oscuro: textos y velo de la barra solo pasan a claro con vidrio casi blanco.
    sampler.darkBelow = if (themeDark) 0.62f else 0.42f
    sampler.lightAbove = if (themeDark) 0.75f else 0.5f
    // Cada pieza decide su propio texto: la pastilla puede estar sobre algo claro y el miniplayer sobre algo oscuro.
    val darkMini = sampler.dark["mini"] ?: themeDark
    val darkBar = sampler.dark["barra"] ?: themeDark
    val darkTop = sampler.dark["arriba"] ?: themeDark
    val miniLabel = animateColorAsState(if (darkMini) Color.White else Color.Black, tween(250), label = "textoMini")
    val barLabel = animateColorAsState(if (darkBar) Color.White else Color.Black, tween(250), label = "textoBarra")
    val topLabel = animateColorAsState(if (darkTop) Color.White else Color.Black, tween(250), label = "textoArriba")
    val barVeil = androidx.compose.runtime.rememberUpdatedState(if (darkBar) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f))
    val miniVeil = androidx.compose.runtime.rememberUpdatedState(if (darkMini) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f))
    // Fuente para la barra de pestañas de Kyant: la misma capa de la página que usan
    // las piezas propias (grabarla dos veces por fotograma costaba hilo de UI). Antes
    // de Android 13 no hay capa propia y se graba con layerBackdrop.
    val pageLayer = if (LiquidGlassSupported) null else rememberLayerBackdrop()
    val pageBackdrop: Backdrop = pageLayer ?: rememberSharedBackdrop(haze)
    PrefetchNowPlaying(remote, state.track)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // Fondo de vidrio para todo lo que cuelga de aquí (también los ⋮ y sus menús, que viven fuera de la página).
    CompositionLocalProvider(LocalGlassBackdrop provides haze) {
    Box(Modifier.fillMaxSize().background(Apple.colors.background).onSizeChanged { sheet.height = it.height.toFloat(); rootSize = it }) {
      // Con la hoja de reproducción abierta del todo, lo de debajo no se ve: con alfa 0
      // el sistema no lo dibuja (ni la página ni el vidrio), y vuelve en cuanto se arrastra.
      // Las portadas animadas de la página se pausan mientras tanto.
      val pageHidden = remember { derivedStateOf { sheet.position.value <= 0f } }
      CompositionLocalProvider(LocalPageHidden provides pageHidden) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (sheet.position.value <= 0f) 0f else 1f }) {
        val route = stack.last()
        val nested = stack.size > 1
        val open: (Route) -> Unit = { stack.add(it) }
        // Las páginas con portada (álbum, artista) dibujan bajo la barra de estado.
        val listPadding = PaddingValues(top = if (wide) wideTop else top + if (nested) TopButtonsHeight else 0.dp, bottom = bottom)
        val heroPadding = PaddingValues(bottom = bottom)
        // El fondo va dentro de la fuente: el vidrio tiene que ver algo opaco detrás.
        // Cuánto se ha desplazado la pantalla actual (para el borde de arriba). Solo se
        // lee en composición si pasa el umbral (derivedStateOf): leer el valor directo
        // recomponía toda la pantalla en cada píxel de desplazamiento.
        val scrolled = remember(route) { mutableFloatStateOf(0f) }
        val pastEdge by remember(route) { derivedStateOf { scrolled.floatValue > 4f } }
        // Barra minimizada (iOS 26): al desplazar hacia abajo se encoge; al subir o al llegar arriba, vuelve.
        val compactState = remember(route) { mutableStateOf(false) }
        val scrollWatch = remember(route) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    scrolled.floatValue = (scrolled.floatValue - consumed.y).coerceAtLeast(0f)
                    if (consumed.y < -2f && scrolled.floatValue > 24f) compactState.value = true
                    else if (consumed.y > 2f || scrolled.floatValue < 4f) compactState.value = false
                    return Offset.Zero
                }
                // Solo al parar el impulso (cada copia retiene el RenderThread 5–10 ms; durante el
                // desplazamiento se notaba). El fondo queda quieto y el cambio de tono no se ve llegar.
                override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    sampler.request()
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        val compact = compactState.value
        val minimize = animateFloatAsState(if (compact) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 350f), label = "minimizar")
        // Zonas que se muestrean: exactamente las piezas de vidrio en su disposición actual
        // (minimizada: solo la fila del miniplayer; si no, miniplayer + barra) y el botón ⋮.
        // Medir la franja desplegada con la barra minimizada metía página fundida a blanco y
        // daba "claro" con el vidrio oscuro. Tras el cambio se vuelve a leer, pasada la transición.
        LaunchedEffect(compact, hasTrack, rootSize, wide) {
            if (rootSize == androidx.compose.ui.unit.IntSize.Zero) return@LaunchedEffect
            with(density) {
                val w = rootSize.width.toFloat(); val h = rootSize.height.toFloat()
                val left = SideMargin.toPx(); val right = w - SideMargin.toPx(); val floor = h - barBottom.toPx()
                val rect = { l: Float, t: Float, r: Float, b: Float -> androidx.compose.ui.geometry.Rect(l, t, r, b) }
                val pieces = when {
                    wide -> mapOf("mini" to rect(left, floor - WideMiniHeight.toPx(), right, floor))
                    compact -> mapOf(
                        "barra" to rect(left, floor - CompactBarHeight.toPx(), left + CompactBarHeight.toPx(), floor),
                        "mini" to rect(left + (CompactBarHeight + BarGap).toPx(), floor - MiniHeight.toPx(), right, floor),
                    )
                    else -> mapOf(
                        "barra" to rect(left, floor - TabBarHeight.toPx(), right, floor),
                        "mini" to rect(left, floor - (TabBarHeight + BarGap + MiniHeight).toPx(), right, floor - (TabBarHeight + BarGap).toPx()),
                    )
                }
                sampler.regions = pieces + ("arriba" to rect(w - (SideMargin + 40.dp).toPx(), (top + 16.dp).toPx(), w - SideMargin.toPx(), (top + 56.dp).toPx()))
            }
            delay(450)
            sampler.request()
        }
        // Luminancia detrás de las piezas: un poco después de cambiar de pantalla (el contenido llega asíncrono).
        LaunchedEffect(route) { for (ms in listOf(150L, 500L, 1200L, 2500L)) { delay(ms); sampler.request() } }
        Box(
            Modifier.fillMaxSize().glassSource(haze)
                .then(if (pageLayer != null) Modifier.layerBackdrop(pageLayer) else Modifier)
                .background(Apple.colors.background).nestedScroll(scrollWatch),
        ) { CompositionLocalProvider(LocalOpenRoute provides open, LocalGlassBackdrop provides haze) { when (route) {
            Route.Home -> HomeScreen(remote, connection.endpoint.host, listPadding, open, onForget)
            Route.New -> BrowseScreen(remote, connection.endpoint.host, BrowsePage.New, route.label(), listPadding, open, onForget)
            Route.Radio -> BrowseScreen(remote, connection.endpoint.host, BrowsePage.Radio, route.label(), listPadding, open, onForget)
            Route.LibraryHome -> LibraryHome(remote, connection.endpoint.host, listPadding, open, onForget)
            Route.Search -> SearchScreen(remote, listPadding, open)
            Route.Playlists, Route.Artists, Route.Albums, Route.Songs -> LibraryList(remote, route, listPadding, open)
            is Route.Collection -> CollectionScreen(remote, route, heroPadding)
            is Route.Artist -> ArtistScreen(remote, connection.endpoint.host, route, heroPadding, open)
            is Route.Category -> CategoryScreen(remote, route, listPadding, open)
        } } }

        // Borde de desplazamiento bajo la barra de estado: solo con contenido debajo, como iOS
        // (las páginas de álbum empiezan con su carátula arriba).
        val edge = animateFloatAsState(if (pastEdge) 1f else 0f, label = "borde")
        val edgeVisible by remember { derivedStateOf { edge.value > 0f } }
        if (route !is Route.Collection && route !is Route.Artist && edgeVisible) Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + if (wide) 88.dp else 56.dp)
                .graphicsLayer { alpha = edge.value }.glassFade(haze, Apple.colors.background),
        )

        if (nested) {
            // Sobre la página de álbum (fondo de color, siempre oscuro) va translúcido y blanco.
            val onColor = route is Route.Collection || route is Route.Artist
            // De vidrio, como el ⋮ de las raíces; sobre álbum y artista, siempre blanco.
            GlassCircleButton(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onClick = back,
                modifier = Modifier.padding(start = SideMargin, top = top + 13.dp),
                tint = if (onColor) Color.White else Apple.colors.label, backdrop = haze,
            )
            // El ⋮ de álbum/playlist también aquí, fuera de la página: un botón de vidrio dentro
            // de la página dibujaría la página dentro de sí misma (ciclo en HWUI, desbordamiento de pila).
            if (route is Route.Collection) Box(Modifier.align(Alignment.TopEnd).padding(top = top + 13.dp, end = SideMargin)) {
                var menu by remember { mutableStateOf(false) }
                GlassCircleButton(Icons.Outlined.MoreVert, stringResource(R.string.action_more), { menu = true }, tint = Color.White, backdrop = haze)
                PlayMenu(remote, menu, { menu = false }, route.kind, route.id)
            }
        }

        // Desvanecido del contenido bajo las barras flotantes, en vez de un fondo sólido (suave: hasta el 50 % del color de fondo, 40 % negro en álbum y artista).
        // En la página de álbum el fondo es siempre oscuro: el desvanecido también.
        // Suave: el vidrio ya se adapta a lo que tiene detrás; antes llegaba al 90 % a mitad de la franja.
        val fade = if (route is Route.Collection || route is Route.Artist) Color.Black.copy(alpha = 0.4f) else Apple.colors.background.copy(alpha = 0.5f)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom)
                .background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to fade)),
        )

        // Teléfono: el ⋮ de las raíces queda fijo arriba a la derecha, a la altura del título grande.
        if (!wide && !nested && route != Route.Search) Box(Modifier.align(Alignment.TopEnd).padding(top = top + 16.dp, end = SideMargin)) {
            ConnectionMenuButton(connection.endpoint.host, onForget, backdrop = haze, tint = { topLabel.value })
        }

        if (wide) {
            TopTabBar(tab, haze, onMenu = { sidebar = true }, onSelect = select,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top + 10.dp))
            // Opciones de conexión arriba a la derecha en las raíces.
            if (!nested && route != Route.Search) Box(Modifier.align(Alignment.TopEnd).padding(top = top + 13.dp, end = SideMargin)) {
                ConnectionMenuButton(connection.endpoint.host, onForget, backdrop = haze)
            }
            if (hasTrack) WideMiniPlayer(
                remote, state, haze, onOpen = openSheet,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barBottom)
                    .widthIn(max = 686.dp).fillMaxWidth().padding(horizontal = SideMargin).sheetDrag(sheet),
            )
            AnimatedVisibility(sidebar, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                Sidebar(tab, haze, connection.endpoint.host, onSelect = { select(it); sidebar = false }, onClose = { sidebar = false }, onForget = onForget)
            }
        } else {
            // Barra minimizable (iOS 26): al desplazar hacia abajo la barra se encoge por la
            // izquierda hasta una pastilla con la pestaña activa y el miniplayer baja a su lado,
            // estrechado; al subir, vuelve. Las medidas se interpolan al medir, leyendo
            // `minimize`: no hay recomposición por fotograma.
            // Pastilla: círculo de la altura del miniplayer (iOS 26), alineada abajo con él.
            val pillShift = CompactBarHeight + BarGap
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = SideMargin, end = SideMargin, bottom = barBottom)
                    .height(TabBarHeight + if (hasTrack) MiniHeight + BarGap else 0.dp),
            ) {
                if (hasTrack) MiniPlayer(
                    remote, state, onOpen = { openSheet(SheetMode.Artwork) }, haze = haze,
                    compact = { minimize.value }, label = { miniLabel.value }, container = { miniVeil.value },
                    modifier = Modifier.height(MiniHeight).layout { measurable, constraints ->
                        val p = minimize.value.coerceIn(0f, 1f)
                        val shift = (pillShift.toPx() * p).roundToInt()
                        val width = (constraints.maxWidth - shift).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        val down = ((TabBarHeight + BarGap).toPx() * p).roundToInt()
                        layout(constraints.maxWidth, placeable.height) { placeable.place(shift, down) }
                    }.sheetDrag(sheet),
                )
                Box(
                    Modifier.align(Alignment.BottomStart).layout { measurable, constraints ->
                        val width = lerp(constraints.maxWidth, CompactBarHeight.roundToPx(), minimize.value.coerceIn(0f, 1f))
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                ) {
                    TabBar(
                        tab, pageBackdrop, select, label = { barLabel.value }, container = { barVeil.value },
                        compact = compact, minimize = { minimize.value }, onExpand = { compactState.value = false },
                    )
                }
            }
        }

      }
      }

        // Deslizar el miniplayer hacia arriba la abre siguiendo al dedo (Apple Music).
        // Desde la hoja, "ir al álbum/artista" la cierra y abre la página en la pestaña actual.
        if (sheet.visible && hasTrack) CompositionLocalProvider(LocalOpenRoute provides { r -> sheet.close(); stack.add(r) }, LocalGlassBackdrop provides haze) {
            NowPlayingSheet(remote, state, sheet, initialMode = sheetMode)
        }
    }
    }
}

private val WideMiniHeight = 63.dp

/**
 * Barra de pestañas de tablet (Apple Music en iPad/tablet): cápsula arriba en el
 * centro con ≡ (barra lateral), las pestañas en texto y la lupa al final.
 */
@Composable
private fun TopTabBar(selected: Tab, haze: GlassBackdrop, onMenu: () -> Unit, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Row(
        modifier.height(46.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape).glass(haze).glassEdge(shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIcon(Icons.Filled.Menu, stringResource(R.string.action_sidebar), active = false, onClick = onMenu)
        Tab.entries.filter { it != Tab.Search }.forEach { t ->
            val active = t == selected
            Box(
                Modifier.fillMaxHeight().clip(shape)
                    .background(if (active) Apple.colors.label.copy(alpha = 0.1f) else Color.Transparent)
                    .combinedClickableCompat(onClick = { onSelect(t) })
                    .semantics { role = Role.Tab; this.selected = active }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(t.label), style = Apple.type.headline.copy(fontWeight = FontWeight.Medium),
                    color = if (active) Apple.colors.accent else Apple.colors.label)
            }
        }
        TopBarIcon(AppleIcons.TabSearch, stringResource(R.string.tab_search), active = selected == Tab.Search) { onSelect(Tab.Search) }
    }
}

@Composable
private fun TopBarIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().width(48.dp).clip(CircleShape)
            .background(if (active) Apple.colors.label.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Apple.colors.accent else Apple.colors.label, modifier = Modifier.size(24.dp))
    }
}

/** Barra lateral de tablet: panel de vidrio sobre el contenido, con las secciones y la conexión abajo. */
@Composable
private fun Sidebar(
    selected: Tab,
    haze: GlassBackdrop,
    host: String,
    onSelect: (Tab) -> Unit,
    onClose: () -> Unit,
    onForget: () -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        // Tocar fuera la cierra.
        Box(Modifier.fillMaxSize().combinedClickableCompat(onClick = onClose))
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        Column(
            Modifier.fillMaxHeight().width(320.dp).clip(shape).glass(haze, corner = 24.dp)
                .padding(top = top + 10.dp, start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            TopBarIcon(Icons.Filled.Menu, stringResource(R.string.action_close_sidebar), active = false, onClick = onClose)
            Spacer(Modifier.height(18.dp))
            Tab.entries.forEach { t ->
                val active = t == selected
                Row(
                    Modifier.fillMaxWidth().height(54.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(if (active) Apple.colors.accent else Color.Transparent)
                        .combinedClickableCompat(onClick = { onSelect(t) })
                        .semantics { role = Role.Tab; this.selected = active }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (active) Color.White else Apple.colors.label
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(26.dp))
                    Text(stringResource(t.label), style = Apple.type.body, color = color, modifier = Modifier.padding(start = 16.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.connection_connected_to, host), style = Apple.type.subhead, color = Apple.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 14.dp))
            Text(
                stringResource(R.string.connection_forget), style = Apple.type.body, color = Apple.colors.accentText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).combinedClickableCompat(onClick = onForget),
            )
        }
    }
}

/**
 * Barra de pestañas de vidrio líquido (LiquidBottomTabs de Kyant): una lente que se
 * arrastra o salta entre pestañas, se agranda al tocarla y tiñe de acento lo que cubre.
 */
@Composable
private fun TabBar(
    selected: Tab,
    backdrop: Backdrop,
    onSelect: (Tab) -> Unit,
    label: () -> Color,
    container: () -> Color,
    compact: Boolean,
    minimize: () -> Float,
    onExpand: () -> Unit,
) {
    val tabs = Tab.entries
    // El componente lee la pestaña desde corrutinas lanzadas una sola vez: necesita
    // lambdas estables que lean estado, no el valor capturado en cada composición.
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val selectedIndex = remember { { tabs.indexOf(current) } }
    val expandedWidth = LocalWindowLayout.current.width - SideMargin * 2
    val index = tabs.indexOf(selected)
    LiquidBottomTabs(
        selectedTabIndex = selectedIndex,
        // Solo cambios hechos arrastrando la lente; los toques ya pasan por onSelect.
        onTabSelected = { i -> if (tabs[i] != current) select(tabs[i]) },
        backdrop = backdrop,
        tabsCount = tabs.size,
        compactProgress = minimize,
        containerColor = container,
        overlay = {
            // Icono de la pastilla: parte del sitio de su pestaña y acaba centrado (en acento, como iOS).
            // Tocar la pastilla vuelve a desplegar la barra.
            Box(
                Modifier.fillMaxSize()
                    .then(if (compact) Modifier.combinedClickableCompat(onClick = onExpand) else Modifier)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints())
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val p = minimize()
                            val tabW = (expandedWidth.toPx() - 8.dp.toPx()) / tabs.size
                            val x0 = 4.dp.toPx() + (index + 0.5f) * tabW
                            val y0 = 24.5.dp.toPx()
                            val cx = x0 + (constraints.maxWidth / 2f - x0) * p
                            val cy = y0 + (constraints.maxHeight / 2f - y0) * p
                            placeable.place((cx - placeable.width / 2f).roundToInt(), (cy - placeable.height / 2f).roundToInt())
                        }
                    },
            ) {
                Icon(
                    selected.icon, null, tint = Apple.colors.accent,
                    modifier = Modifier.size(25.dp).graphicsLayer { alpha = if (minimize() > 0f) 1f else 0f },
                )
            }
        },
    ) {
        tabs.forEach { t ->
            val isSelected = t == selected
            LiquidBottomTab(
                { onSelect(t) }, Modifier.semantics { role = Role.Tab; this.selected = isSelected },
                // La activa se sustituye por el icono de la pastilla en cuanto empieza; las demás se desvanecen.
                alpha = if (isSelected) ({ if (minimize() > 0f) 0f else 1f }) else ({ (1f - minimize() * 2.5f).coerceIn(0f, 1f) }),
            ) {
                // Color leído al dibujar (sin recomponer ni capas): la animación de tono sale gratis.
                TintedIcon(t.icon, 25.dp, label)
                androidx.compose.foundation.text.BasicText(
                    stringResource(t.label),
                    style = Apple.type.caption.copy(fontSize = Apple.type.caption.fontSize * (10f / 12f), fontWeight = FontWeight.SemiBold),
                    color = { label() },
                )
            }
        }
    }
}
