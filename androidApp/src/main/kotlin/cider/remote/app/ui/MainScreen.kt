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
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.foundation.layout.aspectRatio
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
import kotlinx.coroutines.flow.first
import cider.remote.app.ui.components.glassEdge
import cider.remote.app.ui.components.rememberGlassBackdrop
import cider.remote.app.ui.components.rememberSharedBackdrop
import cider.remote.app.ui.components.LocalPageHidden
import cider.remote.app.ui.components.glassFadeVeil
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

internal enum class Tab(@StringRes val label: Int, val icon: ImageVector, val root: Route) {
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
    // Transiciones de iOS entre páginas y estado guardado de cada una (al volver, la lista sigue donde estaba).
    val saveable = rememberSaveableStateHolder()
    val navScope = rememberCoroutineScope()
    val pages = remember { PageStack(navScope, stacks, saveable) { tab } }
    val pagesSettled = remember { derivedStateOf { pages.transition == null } }
    val back: () -> Unit = { pages.pop() }
    // Volver a tocar la pestaña activa vuelve a su raíz; ya en la raíz, desplaza al inicio.
    val scrollToTop = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val select: (Tab) -> Unit = { selected ->
        if (selected == tab) {
            if (stacks.getValue(tab).size > 1) pages.popToRoot() else scrollToTop.intValue++
        } else pages.settle()
        tab = selected
    }
    // `null`: como se dejó (tablet: tocar la canción del miniplayer vuelve a abrir la hoja con la letra o la
    // cola si estaban a la vista al cerrarla).
    val openSheet: (SheetMode?) -> Unit = { if (it != null) sheetMode = it; sheet.open(tap = true) }

    BackHandler(enabled = sidebar) { sidebar = false }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // Atrás predictivo: la página sigue al dedo, como el deslizamiento desde el borde de iOS.
    // Estado de la hoja derivado: `sheet.isOpen`/`sheet.visible` leen su posición, que cambia en cada
    // fotograma del gesto; leídos directamente recomponían toda esta pantalla a cada fotograma.
    val sheetOpen by remember(sheet) { derivedStateOf { sheet.isOpen } }
    val sheetVisible by remember(sheet) { derivedStateOf { sheet.visible } }
    // Teléfono: cerrada vuelve a la carátula (deslizar el miniplayer la abre siempre así); la tablet recuerda
    // si se cerró con la letra o la cola a la vista.
    LaunchedEffect(sheetVisible, wide) { if (!sheetVisible && !wide) sheetMode = SheetMode.Artwork }
    PredictiveBackHandler(enabled = !sheetOpen && !sidebar && stack.size > 1) { events ->
        pages.predictiveBack(events, rootSize.width.toFloat(), rootSize.height.toFloat())
    }

    val hasTrack = state.track != null
    // El miniplayer está siempre: sin canción o sin conexión lo dice él mismo (y sin canción no abre la hoja).
    val showMini = true
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Apple Music deja la barra a 16 dp del borde, encima del asa de gestos.
    val barBottom: Dp = if (nav > 32.dp) nav + 8.dp else max(nav, 16.dp)
    val bottom = if (wide) barBottom + (if (showMini) WideMiniHeight else 0.dp) + 24.dp
    else barBottom + TabBarHeight + (if (showMini) MiniHeight + BarGap else 0.dp) + 24.dp
    // En tablet la barra de pestañas va arriba (cápsula de 46 dp) y no hay título grande.
    val wideTop = top + 10.dp + WideBarHeight + 18.dp

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
    // Un solo tono para todo el grupo inferior (barra o pastilla y miniplayer), decidido sobre la
    // franja entera: con tonos distintos a la vez se veía raro.
    val darkBottom = sampler.dark["abajo"] ?: themeDark
    val darkMini = darkBottom
    val darkBar = darkBottom
    val darkTop = sampler.dark["arriba"] ?: themeDark
    val darkBack = sampler.dark["volver"] ?: themeDark
    // Tablet: barra de pestañas superior, con su propia zona de muestreo.
    val darkWideBar = sampler.dark["barra"] ?: themeDark
    val wideBarLabel = animateColorAsState(if (darkWideBar) Color.White else Color.Black, tween(250), label = "textoBarraArriba")
    val wideBarVeil = androidx.compose.runtime.rememberUpdatedState(if (darkWideBar) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f))
    val backLabel = animateColorAsState(if (darkBack) Color.White else Color.Black, tween(250), label = "textoVolver")
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
    // Transición de Apple Music al abrir y cerrar la hoja (ver SheetState): el grupo de vidrio de abajo
    // (barra y miniplayer; en tablet, solo el miniplayer) baja hasta salir de la pantalla en el primer
    // 30 % del recorrido —lo que mide, con margen para la sombra del vidrio— y la carátula vuela desde
    // la miniatura del miniplayer. Con más recorrido va con alfa 0 y el sistema no lo dibuja (sus
    // desenfoques costaban hilo de render); todo se lee al dibujar o al colocar, sin recomponer.
    val bottomGroupPx = with(density) {
        (barBottom + (if (wide) WideMiniHeight else TabBarHeight + if (showMini) MiniHeight + BarGap else 0.dp) + 48.dp).toPx()
    }
    val thumbInsetPx = with(density) { ((if (wide) WideMiniHeight - WideThumbSize else MiniHeight - 32.dp) / 2).toPx() }
    androidx.compose.runtime.SideEffect { sheet.barsTravel = bottomGroupPx }
    // Origen del vuelo de la carátula: la miniatura del miniplayer en reposo (se descuenta lo que hayan
    // bajado las barras). En cuanto la hoja se mueve, la miniatura se oculta: la que se ve es la que vuela.
    val thumbModifier = remember(sheet, thumbInsetPx) {
        Modifier.onGloballyPositioned { c ->
            // Sin recortar a la pantalla (boundsInRoot recorta): con la hoja abierta las barras están fuera.
            val at = c.positionInRoot()
            val top = at.y - sheet.barsShift.roundToInt()
            sheet.setThumb(Rect(at.x, top, at.x + c.size.width, top + c.size.height), miniTop = top - thumbInsetPx)
        }.graphicsLayer { alpha = if (sheet.position.value < 0.97f) 0f else 1f }
    }
    // Fondo de vidrio para todo lo que cuelga de aquí (también los ⋮ y sus menús, que viven fuera de la página).
    CompositionLocalProvider(LocalGlassBackdrop provides haze) {
    Box(Modifier.fillMaxSize().background(Apple.colors.background).onSizeChanged { sheet.height = it.height.toFloat(); rootSize = it }) {
      // Con la hoja de reproducción abierta del todo, lo de debajo no se ve: con alfa 0
      // el sistema no lo dibuja (ni la página ni el vidrio), y vuelve en cuanto se arrastra.
      // Las portadas animadas de la página se pausan mientras tanto.
      val pageHidden = remember { derivedStateOf { sheet.position.value <= 0f } }
      CompositionLocalProvider(LocalPageHidden provides pageHidden) {
      Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (sheet.position.value <= 0f) 0f else 1f }) {
        val topEntry = pages.top(tab)
        val route = topEntry.route
        val nested = topEntry.nested
        val transition = pages.transition
        val open: (Route) -> Unit = { pages.push(it) }
        // Enlaces de Apple Music tocados o compartidos (AppleMusicLinks): una canción suena y abre la hoja;
        // un álbum, playlist o artista abre su página; lo demás (emisoras…) se manda tal cual a Cider.
        // Se recogen en un solo efecto: si la clave fuera el enlace, vaciarlo reiniciaría el efecto y
        // cancelaría la consulta a medias (salía "no se pudo abrir").
        val linkContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(remote) { AppleMusicLinks.pending.collect { link ->
            if (link == null) return@collect
            AppleMusicLinks.pending.value = null
            val target = AppleMusicLinks.parse(link)
            val ok = runCatching {
                val song = target?.song ?: target?.id?.takeIf { target.kind == "song" }
                when {
                    song != null -> { remote.api.playItem("songs", song); delay(600); sheet.open(tap = true) }
                    target?.kind == "album" || target?.kind == "playlist" -> {
                        val type = target.kind + "s"
                        val item = remote.api.catalogItem(type, target.id)
                        open(Route.Collection(type, target.id, item?.name.orEmpty(), item?.attributes?.artistName.takeIf { type == "albums" }, item?.artwork))
                    }
                    target?.kind == "artist" -> open(Route.Artist(target.id, remote.api.catalogItem("artists", target.id)?.name.orEmpty(), catalog = true))
                    else -> remote.api.playUrl(link)
                }
            }.isSuccess
            if (!ok) android.widget.Toast.makeText(linkContext, linkContext.getString(cider.remote.app.R.string.link_failed), android.widget.Toast.LENGTH_SHORT).show()
        } }
        // Las páginas con portada (álbum, artista) dibujan bajo la barra de estado.
        fun listPadding(nested: Boolean) = PaddingValues(top = if (wide) wideTop else top + if (nested) TopButtonsHeight else 0.dp, bottom = bottom)
        val heroPadding = PaddingValues(bottom = bottom)
        // Opacidad de lo que depende de la página (botones de cabecera) durante una transición:
        // se funde con el mismo progreso con que se desliza la página.
        fun shownFor(test: (PageEntry) -> Boolean): () -> Float = {
            val t = pages.transition
            if (t == null) (if (test(pages.top(tab))) 1f else 0f)
            else {
                val c = t.cover()
                val under = if (test(t.under)) 1f else 0f
                val over = if (test(t.over)) 1f else 0f
                // Zoom: los botones de vidrio de una página y otra no se cruzan (los de la que queda debajo
                // se van en la primera mitad y los de la de arriba llegan en la segunda): cada botón de
                // vidrio a la vista son dos capas con desenfoque por fotograma.
                if (t.zoom != null && under != over) (if (over > under) ((c - 0.5f) / 0.5f) else (1f - c / 0.5f)).coerceIn(0f, 1f)
                else lerp(under, over, c)
            }
        }
        fun anyShows(test: (PageEntry) -> Boolean) = test(topEntry) || (transition != null && (test(transition.over) || test(transition.under)))
        // El fondo va dentro de la fuente: el vidrio tiene que ver algo opaco detrás.
        // Cuánto se ha desplazado la pantalla actual (para el borde de arriba). Solo se
        // lee en composición si pasa el umbral (derivedStateOf): leer el valor directo
        // recomponía toda la pantalla en cada píxel de desplazamiento.
        val scrolled = pages.scrolledOf(topEntry.key)
        val pastEdge by remember(topEntry.key) { derivedStateOf { scrolled.floatValue > 4f } }
        // Barra minimizada (iOS 26): al desplazar hacia abajo se encoge; al subir o al llegar arriba, vuelve.
        val compactState = remember(route) { mutableStateOf(false) }
        val dragDeadZone = with(density) { 18.dp.toPx() }
        // Dónde está el dedo (para cerrar arrastrando un álbum abierto con zoom); se observa sin consumir.
        val finger = remember { mutableStateOf(Offset.Zero) }
        val scrollWatch = remember(topEntry.key) {
            object : NestedScrollConnection {
                // Cerrar deslizando hacia abajo un álbum o playlist abierto con zoom (iOS): con la lista arriba
                // del todo, lo que la lista no puede subir mueve la página hacia su carátula.
                // La página pasa a ser una tarjeta suelta bajo el dedo (ZoomPose.drag) y al soltar vuela a su
                // carátula o vuelve a pantalla completa.
                var pulled = 0f
                var startX = 0f
                fun pull(dy: Float) {
                    pulled = (pulled + dy).coerceAtLeast(0f)
                    val page = androidx.compose.ui.geometry.Size(rootSize.width.toFloat().coerceAtLeast(1f), rootSize.height.toFloat().coerceAtLeast(1f))
                    pages.zoomDragTo(ZoomPose.drag(pages.dragPivot, (pulled - dragDeadZone).coerceAtLeast(0f), finger.value.x - startX, page))
                }
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!pages.dragging || source != NestedScrollSource.UserInput) return Offset.Zero
                    pull(available.y)
                    return Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (!pages.dragging) return androidx.compose.ui.unit.Velocity.Zero
                    // Como iOS: decide dónde estaría la tarjeta 0,12 s después con la velocidad que lleva
                    // (un tirón corto y rápido cierra; soltar subiendo, no).
                    val h = rootSize.height.toFloat().coerceAtLeast(1f)
                    val close = pages.dragScale - 0.67f * 0.12f * available.y / h < 0.905f
                    pulled = 0f
                    pages.zoomDragEnd(close)
                    return available
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0.5f && !pages.dragging && pages.zoomDragStart(finger.value)) {
                        pulled = 0f
                        startX = finger.value.x
                        pull(available.y)
                        return Offset(0f, available.y)
                    }
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
                val bandTop = floor - when {
                    wide -> WideMiniHeight.toPx()
                    compact -> CompactBarHeight.toPx()
                    else -> (TabBarHeight + if (showMini) MiniHeight + BarGap else 0.dp).toPx()
                }
                val pieces = if (wide) {
                    val miniW = minOf(WideMiniMaxWidth.toPx(), w - 2 * SideMargin.toPx())
                    val miniL = (w - miniW) / 2
                    val barW = (WideMenuButton + WideBarGap + WideTabWidth * Tab.entries.size + 8.dp).toPx()
                    val barL = (w - barW) / 2
                    val barT = (top + 10.dp).toPx()
                    mapOf(
                        "abajo" to rect(miniL, bandTop, miniL + miniW, floor),
                        "barra" to rect(barL, barT, barL + barW, barT + WideBarHeight.toPx()),
                    )
                } else mapOf("abajo" to rect(left, bandTop, right, floor))
                sampler.regions = pieces +
                    ("arriba" to rect(w - (SideMargin + 40.dp).toPx(), (top + 13.dp).toPx(), w - SideMargin.toPx(), (top + 53.dp).toPx())) +
                    ("volver" to rect(SideMargin.toPx(), (top + 13.dp).toPx(), (SideMargin + 40.dp).toPx(), (top + 53.dp).toPx()))
            }
            delay(450)
            sampler.request()
        }
        // Luminancia detrás de las piezas: un poco después de cambiar de pantalla (el contenido llega asíncrono).
        LaunchedEffect(route) {
            for (ms in listOf(150L, 500L, 1200L, 2500L)) {
                delay(ms)
                // Nunca en mitad de una transición: cada muestra retiene el hilo de render 5–10 ms.
                androidx.compose.runtime.snapshotFlow { pages.transition == null }.first { it }
                sampler.request()
            }
        }
        // Borde de desplazamiento bajo la barra de estado: solo con contenido debajo, como iOS
        // (las páginas de álbum empiezan con su carátula arriba). En todas las pantallas (iOS 26);
        // sobre álbum y artista, con el color de fondo de esa página.
        val edge = animateFloatAsState(if (pastEdge) 1f else 0f, label = "borde")
        val pageColor = pages.colorOf(topEntry.key).value
        // Durante una transición el borde va con ella: el de la página de debajo aparece a medida que la de
        // arriba la destapa (y al revés), con el color de la página a la que pertenece. Antes saltaba al de
        // la página nueva en cuanto empezaba el cambio: al cerrar un álbum aparecía de golpe sobre él.
        val edgeBase = Apple.colors.background
        val edgeOf = { e: PageEntry -> pages.scrolledOf(e.key).floatValue > 4f }
        val edgeAlpha = remember(transition, edge) {
            {
                val t = transition
                if (t == null) edge.value else lerp(if (edgeOf(t.under)) 1f else 0f, if (edgeOf(t.over)) 1f else 0f, t.cover())
            }
        }
        val edgeColor = remember(transition, pageColor, edgeBase) {
            {
                val t = transition
                if (t == null) pageColor ?: edgeBase
                else {
                    val under = pages.colorOf(t.under.key).value ?: edgeBase
                    val over = pages.colorOf(t.over.key).value ?: edgeBase
                    when {
                        edgeOf(t.under) && edgeOf(t.over) -> androidx.compose.ui.graphics.lerp(under, over, t.cover())
                        edgeOf(t.under) -> under
                        else -> over
                    }
                }
            }
        }
        val edgeVisible by remember(edgeAlpha) { derivedStateOf { edgeAlpha() > 0f } }
        val edgeHeight = top + if (wide) 88.dp else 56.dp
        Box(
            Modifier.fillMaxSize().glassSource(haze)
                .then(if (pageLayer != null) Modifier.layerBackdrop(pageLayer) else Modifier)
                .background(Apple.colors.background).nestedScroll(scrollWatch)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.firstOrNull()?.let { finger.value = it.position }
                    }
                },
        ) { CompositionLocalProvider(LocalOpenRoute provides open, LocalGlassBackdrop provides haze, LocalScrollToTop provides scrollToTop, cider.remote.app.ui.components.LocalPageSettled provides pagesSettled) {
            // Durante una transición se componen las dos páginas (la de debajo primero). En reposo,
            // la de arriba y, oculta y sin colocar, la anterior: así no se desmonta al terminar de
            // abrir (soltar Inicio con sus vídeos costaba ~50 ms) y volver empieza sin componer nada.
            // `key` conserva cada página al pasar de una lista a otra.
            val entries = pages.entries(tab)
            // Zoom: la página de atrás se aleja y deja ver un marco, del color de esa página (su desenfoque
            // se derrama en él, ver ZoomBackdrop).
            if (transition?.zoom != null) Box(Modifier.fillMaxSize().background(pages.colorOf(transition.under.key).value ?: Apple.colors.background))
            val shown = if (transition != null) listOf(transition.under, transition.over)
            else listOfNotNull(entries.getOrNull(entries.size - 2), topEntry)
            for (entry in shown) key(entry.key) {
                val role = when {
                    transition == null -> if (entry == topEntry) 0 else 2
                    entry == transition.over -> 1
                    else -> -1
                }
                val progress = transition?.progress
                val zoom = transition?.zoom
                // La página se mueve con una propiedad de su capa: la sombra y el oscurecido van en
                // capas hermanas que solo cambian de opacidad. Dibujarlos alrededor del contenido
                // obligaba a volver a grabar la página entera en cada fotograma (8–10 ms de hilo UI).
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val p = progress?.value?.coerceIn(0f, 1f) ?: 1f
                            if (zoom != null && role == 1) {
                                // Zoom (iOS): un rectángulo redondeado que va de la carátula tocada a la pantalla
                                // (o a la tarjeta que se arrastra), con la página dentro escalada a su ancho.
                                // El progreso puede pasar un poco de la carátula al aterrizar (rebote).
                                val raw = (progress?.value ?: 1f).coerceIn(-0.08f, 1f)
                                val t = Zoom.shape(raw, transition!!.opening, transition.landing)
                                val card = Zoom.card(zoom.live(), size, t, transition.opening, transition.pose, transition.poseAmount.value)
                                val sc = (card.width / size.width).coerceAtLeast(0.01f)
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                scaleX = sc; scaleY = sc
                                translationX = card.left
                                translationY = card.top
                                clip = true
                                shape = ZoomClip(card.height / sc, Zoom.corner(zoom, t) / sc)
                                // Sombra de la tarjeta mientras no llena la pantalla; se va al llegar a la carátula.
                                val lifted = card.width < size.width - 1f
                                shadowElevation = if (lifted) 24.dp.toPx() / sc else 0f
                                // Entra poco a poco al despegarse de la pantalla (no de golpe en el primer fotograma).
                                val away = ((size.width - card.width) / (size.width * 0.1f)).coerceIn(0f, 1f)
                                val shade = Color.Black.copy(alpha = 0.5f * (t / 0.1f).coerceIn(0f, 1f) * away)
                                ambientShadowColor = shade
                                spotShadowColor = shade
                            } else if (zoom != null && role == -1) {
                                // La de atrás se aleja un poco mientras la de arriba la tapa (iOS). Sin recorte:
                                // sus copias desenfocadas y el oscurecido se salen de la página para llenar la pantalla.
                                val sc = lerp(1f, Zoom.UnderScale, transition!!.cover())
                                scaleX = sc; scaleY = sc
                            } else translationX = when (role) {
                                1 -> size.width * (1f - p)
                                -1 -> -size.width * PageStack.UnderShift * p
                                else -> 0f
                            }
                            // Oculta: colocada pero transparente (el sistema no la dibuja y conserva
                            // su grabación: al volver, el primer fotograma no tiene que grabarla entera).
                            alpha = if (role == 2) 0f else 1f
                            // La de debajo solo se ve a la izquierda de la que entra: recortada a esa
                            // franja, el vidrio no repinta la página entera en cada fotograma.
                            if (role == -1 && zoom == null) {
                                clip = true
                                shape = UnderPageClip(p)
                            }
                        }
                        .then(
                            if (role == 2) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                                // Ni toques ni desplazamientos para la oculta.
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            } else Modifier,
                        ),
                ) {
                    if (role == 1 && zoom == null) Box(
                        // Sombra en el borde izquierdo de la página que entra (iOS).
                        Modifier.fillMaxHeight().width(14.dp).offset(x = (-14).dp)
                            .graphicsLayer { alpha = progress?.value?.coerceIn(0f, 1f) ?: 1f }
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)))),
                    )
                    // Zoom: la página de atrás se desenfoca mientras la de arriba crece (iOS). Sus copias
                    // desenfocadas están en caché (ZoomBackdrop) y aquí solo se funden; cuando una la tapa
                    // del todo, la página nítida deja de dibujarse.
                    val zoomUnder = role == -1 && zoom != null
                    val backdrop = rememberZoomBackdrop()
                    val nearAlpha = remember(transition) { { ZoomBlur.near(transition?.cover() ?: 1f) } }
                    val farAlpha = remember(transition) { { ZoomBlur.far(transition?.cover() ?: 1f) } }
                    val underColor = pages.colorOf(entry.key).value ?: Apple.colors.background
                    val flyingAnchor = if (role == -1) zoom?.anchor else null
                    DisposableEffect(flyingAnchor) {
                        flyingAnchor?.flying = true
                        onDispose { flyingAnchor?.flying = false }
                    }
                    val backdropSource = remember(backdrop, underColor, zoom) {
                        Modifier.zoomBackdropSource(backdrop, ZoomNearBlur, ZoomBackBlur, underColor, zoom)
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .then(
                                if (zoomUnder) Modifier.graphicsLayer { alpha = if (ZoomBlur.covered(transition?.cover() ?: 1f)) 0f else 1f }
                                    .then(backdropSource)
                                else Modifier,
                            )
                            .background(Apple.colors.background),
                    ) {
                    saveable.SaveableStateProvider(entry.key) {
                        CompositionLocalProvider(
                            cider.remote.app.ui.LocalPageBackground provides pages.colorOf(entry.key),
                            // La oculta pausa sus portadas animadas.
                            // También la de atrás durante un zoom: va desenfocada, y cada fotograma de sus vídeos
                            // obligaría a rehacer las copias desenfocadas.
                            LocalPageHidden provides if (role == 2 || (role == -1 && zoom != null)) AlwaysHidden else pageHidden,
                        ) {
                            val r = entry.route
                            val pad = listPadding(entry.nested)
                            when (r) {
                                Route.Home -> HomeScreen(remote, connection.endpoint.host, pad, open, onForget)
                                Route.New -> BrowseScreen(remote, connection.endpoint.host, BrowsePage.New, r.label(), pad, open, onForget)
                                Route.Radio -> BrowseScreen(remote, connection.endpoint.host, BrowsePage.Radio, r.label(), pad, open, onForget)
                                Route.LibraryHome -> LibraryHome(remote, connection.endpoint.host, pad, open, onForget)
                                Route.Search -> SearchScreen(remote, pad, open)
                                Route.Playlists, Route.Artists, Route.Albums, Route.Songs -> LibraryList(remote, r, pad, open)
                                is Route.Collection -> CollectionScreen(remote, r, heroPadding)
                                is Route.Artist -> ArtistScreen(remote, connection.endpoint.host, r, heroPadding, open)
                                is Route.Category -> CategoryScreen(remote, r, pad, open)
                            }
                        }
                    }
                    }
                    // Zoom: encima, la carátula tocada, que se funde con la página en el primer tercio del
                    // recorrido; así el primer fotograma es la tarjeta tal cual.
                    val zoomArt = (entry.route as? Route.Collection)?.artwork
                    // Solo desde carátulas cuadradas (las tarjetas 3:4 de Inicio llevan otra imagen).
                    val squareSource = zoom != null && kotlin.math.abs(zoom.rect.width - zoom.rect.height) < 3f
                    if (role == 1 && squareSource && zoomArt != null) coil3.compose.AsyncImage(
                        model = zoomArt.sized(400), contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                            val p = progress?.value?.coerceIn(0f, 1f) ?: 1f
                            val k = (p / 0.35f).coerceIn(0f, 1f)
                            alpha = 1f - k * k * (3f - 2f * k)
                            // Una sola imagen: la opacidad se aplica al dibujarla. Con capa aparte (lo normal con
                            // alfa < 1) era un búfer del ancho de la pantalla por fotograma, y otro por cada pieza
                            // de vidrio que lee la página.
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        },
                    )
                    if (zoomUnder) {
                        Box(Modifier.fillMaxSize().zoomBackdropLevel(backdrop, far = false, nearAlpha))
                        Box(Modifier.fillMaxSize().zoomBackdropLevel(backdrop, far = true, farAlpha))
                    }
                    // La de debajo se oscurece al quedar tapada.
                    if (role == -1) Box(
                        Modifier.fillMaxSize()
                            // Un solo rectángulo: opacidad al dibujarlo. Con capa aparte era un búfer a pantalla
                            // completa por fotograma, y otro por cada pieza de vidrio que lee la página.
                            .graphicsLayer {
                                alpha = PageStack.UnderDim * (transition?.cover() ?: 1f)
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                            }
                            // En un zoom la página se aleja: el oscurecido se sale de ella para no dejar un marco.
                            .drawBehind {
                                val mx = if (zoom != null) size.width * 0.1f else 0f
                                val my = if (zoom != null) size.height * 0.1f else 0f
                                drawRect(Color.Black, Offset(-mx, -my), androidx.compose.ui.geometry.Size(size.width + 2 * mx, size.height + 2 * my))
                            },
                    )
                }
            }
        }
            // Velo del borde superior dentro de la capa de la página: el vidrio de arriba lo ve
            // (si no, enseñaba el contenido nítido en medio de la franja lavada). Capa propia:
            // su fundido no regraba la página.
            if (edgeVisible) Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(edgeHeight)
                    .graphicsLayer { alpha = edgeAlpha() }.glassFadeVeil(edgeColor),
            )
        }

        // Borde de desplazamiento bajo la barra de estado: los desenfoques van encima de la página;
        // el velo, dentro de ella (ver arriba).
        if (edgeVisible) Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(edgeHeight)
                .graphicsLayer { alpha = edgeAlpha() }.glassFade(haze, pageColor ?: Apple.colors.background, withVeil = false),
        )

        if (anyShows { it.nested }) {
            // De vidrio, como el ⋮ de las raíces; el icono sigue el tono de lo que hay detrás
            // (negro sobre carátulas claras, blanco sobre oscuras). Se funde al volver a la raíz.
            val backShown = remember(transition, topEntry) { shownFor { it.nested } }
            GlassCircleButton(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), onClick = back,
                modifier = Modifier.padding(start = SideMargin, top = top + 13.dp).graphicsLayer { alpha = backShown(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }.skipWhenClear { backShown() },
                tint = backLabel.value, backdrop = haze,
            )
        }
        // El ⋮ de álbum/playlist también aquí, fuera de la página: un botón de vidrio dentro
        // de la página dibujaría la página dentro de sí misma (ciclo en HWUI, desbordamiento de pila).
        val menuEntry = listOfNotNull(topEntry, transition?.over, transition?.under).firstOrNull { it.route is Route.Collection }
        val menuRoute = menuEntry?.route as? Route.Collection
        if (menuRoute != null) Box(
            Modifier.align(Alignment.TopEnd).padding(top = top + 13.dp, end = SideMargin)
                .graphicsLayer { alpha = shownFor { it.route is Route.Collection }(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }.skipWhenClear { shownFor { it.route is Route.Collection }() },
        ) {
            var menu by remember { mutableStateOf(false) }
            GlassCircleButton(Icons.Outlined.MoreVert, stringResource(R.string.action_more), { menu = true }, tint = topLabel.value, backdrop = haze)
            PlayMenu(remote, menu, { menu = false }, menuRoute.kind, menuRoute.id, menuRoute.title)
        }

        // Desvanecido del contenido bajo las barras flotantes, en vez de un fondo sólido (suave: hasta el 50 % del color de fondo, 40 % negro en álbum y artista).
        // En la página de álbum el fondo es siempre oscuro: el desvanecido también.
        // Suave: el vidrio ya se adapta a lo que tiene detrás; antes llegaba al 90 % a mitad de la franja.
        val baseColor = Apple.colors.background
        val overColor = transition?.let { pages.colorOf(it.over.key) }
        val underColor = transition?.let { pages.colorOf(it.under.key) }
        Box(
            // Capa propia: se repinta en cada fotograma de una transición sin arrastrar lo demás.
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom).graphicsLayer().drawBehind {
                val t = transition
                val c = if (t == null || overColor == null || underColor == null) pageColor ?: baseColor
                else androidx.compose.ui.graphics.lerp(underColor.value ?: baseColor, overColor.value ?: baseColor, t.cover())
                drawRect(Brush.verticalGradient(0.25f to Color.Transparent, 1f to c.copy(alpha = 0.5f)))
            },
        )

        // Teléfono: el ⋮ de las raíces queda fijo arriba a la derecha, a la altura del título grande.
        val rootMenu: (PageEntry) -> Boolean = { !it.nested && it.route != Route.Search }
        if (!wide && anyShows(rootMenu)) Box(Modifier.align(Alignment.TopEnd).padding(top = top + 16.dp, end = SideMargin).graphicsLayer { alpha = shownFor(rootMenu)(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }.skipWhenClear { shownFor(rootMenu)() }) {
            ConnectionMenuButton(connection.endpoint.host, onForget, backdrop = haze, tint = { topLabel.value })
        }

        if (wide) {
            // La misma barra de vidrio líquido que el teléfono (lente que se arrastra entre pestañas,
            // tono adaptado a lo que hay detrás), con textos; el ≡ queda a su izquierda.
            val wideLabel = remember { { wideBarLabel.value } }
            val wideVeil = remember { { wideBarVeil.value } }
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = top + 10.dp).height(WideBarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidebarButton(haze, wideLabel) { sidebar = true }
                Spacer(Modifier.width(WideBarGap))
                WideTabBar(tab, pageBackdrop, select, label = wideLabel, container = wideVeil)
            }
            // Opciones de conexión arriba a la derecha en las raíces.
            if (anyShows(rootMenu)) Box(Modifier.align(Alignment.TopEnd).padding(top = top + 13.dp, end = SideMargin).graphicsLayer { alpha = shownFor(rootMenu)(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }.skipWhenClear { shownFor(rootMenu)() }) {
                ConnectionMenuButton(connection.endpoint.host, onForget, backdrop = haze)
            }
            if (showMini) WideMiniPlayer(
                remote, state, haze, onOpen = openSheet,
                label = { miniLabel.value }, container = { miniVeil.value }, thumb = thumbModifier,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barBottom)
                    .widthIn(max = WideMiniMaxWidth).fillMaxWidth().padding(horizontal = SideMargin)
                    // Se coloca (no una capa trasladada): así el vidrio sigue leyendo lo que tiene detrás.
                    .offset { IntOffset(0, sheet.barsShift.roundToInt()) }
                    .graphicsLayer { alpha = if (sheet.progress >= 0.3f) 0f else 1f }
                    .then(if (hasTrack) Modifier.sheetDrag(sheet) else Modifier),
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
                    .height(TabBarHeight + if (showMini) MiniHeight + BarGap else 0.dp)
                    // Se colocan (no una capa trasladada): así el vidrio sigue leyendo lo que tiene detrás.
                    .offset { IntOffset(0, sheet.barsShift.roundToInt()) }
                    .graphicsLayer { alpha = if (sheet.progress >= 0.3f) 0f else 1f },
            ) {
                if (showMini) MiniPlayer(
                    remote, state, onOpen = { openSheet(SheetMode.Artwork) }, haze = haze,
                    compact = { minimize.value }, label = { miniLabel.value }, container = { miniVeil.value },
                    thumb = thumbModifier,
                    modifier = Modifier.height(MiniHeight).layout { measurable, constraints ->
                        val p = minimize.value.coerceIn(0f, 1f)
                        val shift = (pillShift.toPx() * p).roundToInt()
                        val width = (constraints.maxWidth - shift).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        val down = ((TabBarHeight + BarGap).toPx() * p).roundToInt()
                        layout(constraints.maxWidth, placeable.height) { placeable.place(shift, down) }
                    }.then(if (hasTrack) Modifier.sheetDrag(sheet) else Modifier),
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

        // La página se oscurece bajo la hoja mientras sube (la hoja es translúcida al principio).
        Box(Modifier.fillMaxSize().drawBehind { val a = sheet.scrim; if (a > 0f) drawRect(Color.Black, alpha = a) })

        // Deslizar el miniplayer hacia arriba la abre siguiendo al dedo (Apple Music).
        // Desde la hoja, "ir al álbum/artista" la cierra y abre la página en la pestaña actual.
        if (sheetVisible && hasTrack) CompositionLocalProvider(LocalOpenRoute provides { r -> sheet.close(); pages.push(r) }, LocalGlassBackdrop provides haze) {
            NowPlayingSheet(remote, state, sheet, mode = sheetMode, onMode = { sheetMode = it })
        }
    }
    }
}

private val WideMiniHeight = 63.dp
/** Miniatura de la carátula en el miniplayer de tablet. */
private val WideThumbSize = 40.dp

/**
 * No dibuja el contenido mientras [alpha] es 0. Una pieza de vidrio invisible seguía en el árbol y rehacía
 * sus capas (desenfoque y sombreado) en cada fotograma en que cambiaba la página de detrás.
 */
private fun Modifier.skipWhenClear(alpha: () -> Float): Modifier = drawWithContent { if (alpha() > 0f) drawContent() }

/** Desenfoque máximo de la página de atrás durante el zoom de álbumes y playlists, y el intermedio. */
private val ZoomBackBlur = 40.dp
private val ZoomNearBlur = 12.dp

/**
 * Desenfoque progresivo de la página de atrás con dos copias de radio fijo: opacidad de cada una según el
 * progreso del zoom. La suave entra en el primer 40 %; la fuerte, del 20 % al final. Mientras dura la
 * transición las dos llevan una opacidad mínima invisible: así sus texturas se crean en los fotogramas de
 * espera y no en mitad del movimiento.
 */
private object ZoomBlur {
    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    fun far(p: Float) = smooth((p - 0.2f) / 0.8f).coerceAtLeast(0.01f)
    fun near(p: Float) = if (far(p) >= 1f) 0f else smooth(p / 0.4f).coerceAtLeast(0.01f)
    /** La página nítida ya no se ve. */
    fun covered(p: Float) = smooth(p / 0.4f) >= 1f
}

private val AlwaysHidden: androidx.compose.runtime.State<Boolean> = androidx.compose.runtime.mutableStateOf(true)

/**
 * Recorte de la página de debajo durante una transición: la franja visible a la izquierda de
 * la página que entra, en coordenadas de la propia página (que está desplazada a la izquierda).
 */
private class UnderPageClip(private val p: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val shift = size.width * PageStack.UnderShift * p
        val visible = size.width * (1f - p) + 1f
        return androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(shift, 0f, (shift + visible).coerceAtMost(size.width), size.height))
    }
    override fun equals(other: Any?) = other is UnderPageClip && other.p == p
    override fun hashCode() = p.hashCode()
}

/** Medidas de la barra superior de tablet: segmentos iguales (la lente de Kyant salta entre ellos). */
private val WideBarHeight = 52.dp
private val WideTabWidth = 104.dp
private val WideMenuButton = 40.dp
private val WideBarGap = 10.dp
private val WideMiniMaxWidth = 686.dp

/** ≡ de la barra lateral: botón de vidrio como los de cabecera; el tono se lee en su propio ámbito. */
@Composable
private fun SidebarButton(haze: GlassBackdrop, tint: () -> Color, onClick: () -> Unit) {
    GlassCircleButton(Icons.Filled.Menu, stringResource(R.string.action_sidebar), onClick = onClick, tint = tint(), backdrop = haze)
}

/**
 * Barra de pestañas de tablet (Apple Music en iPadOS 26): la de vidrio líquido del teléfono
 * (LiquidBottomTabs de Kyant), arriba en el centro, con los nombres de las secciones y la lupa.
 */
@Composable
private fun WideTabBar(
    selected: Tab,
    backdrop: Backdrop,
    onSelect: (Tab) -> Unit,
    label: () -> Color,
    container: () -> Color,
) {
    val tabs = Tab.entries
    // Lambdas estables que leen estado (ver TabBar): el componente las lee desde corrutinas.
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val selectedIndex = remember { { tabs.indexOf(current) } }
    LiquidBottomTabs(
        selectedTabIndex = selectedIndex,
        onTabSelected = { i -> if (tabs[i] != current) select(tabs[i]) },
        backdrop = backdrop,
        tabsCount = tabs.size,
        modifier = Modifier.width(WideTabWidth * tabs.size + 8.dp),
        containerColor = container,
        onCurrentTabTapped = { onSelect(selected) },
        barHeight = WideBarHeight,
        lensHeight = WideBarHeight - 8.dp,
    ) {
        tabs.forEach { t ->
            val isSelected = t == selected
            val name = stringResource(t.label)
            LiquidBottomTab(
                { onSelect(t) },
                Modifier.semantics { role = Role.Tab; this.selected = isSelected; contentDescription = name },
            ) {
                // Colores leídos al dibujar: el cambio de tono no recompone la barra.
                if (t == Tab.Search) TintedIcon(t.icon, 24.dp, label)
                else androidx.compose.foundation.text.BasicText(
                    name, style = Apple.type.headline.copy(fontWeight = FontWeight.Medium),
                    color = { label() }, maxLines = 1,
                )
            }
        }
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
        onCurrentTabTapped = { onSelect(selected) },
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
