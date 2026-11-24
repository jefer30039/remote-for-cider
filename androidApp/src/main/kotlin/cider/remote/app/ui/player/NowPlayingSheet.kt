package cider.remote.app.ui.player

import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import cider.remote.HomeItem
import cider.remote.SongLinks
import cider.remote.app.ui.LocalOpenRoute
import cider.remote.app.ui.home.toRoute
import cider.remote.songLinks
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import cider.remote.app.ui.components.rememberArtworkPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import cider.remote.app.app
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.max
import cider.remote.model.RepeatMode
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.sp
import cider.remote.app.ui.AppleIcons
import cider.remote.app.ui.LocalWindowLayout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import cider.remote.app.ui.components.glassSource
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cider.remote.CiderRemote
import cider.remote.PlayerState
import cider.remote.RemoteSignal
import cider.remote.app.ui.Apple
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.ThinSlider
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.app.ui.formatTime
import cider.remote.model.Artwork
import cider.remote.model.AudioQuality
import cider.remote.app.ui.components.AnimatedCover
import cider.remote.app.ui.components.LightStatusBarIcons
import cider.remote.app.ui.components.asBackground
import cider.remote.app.ui.components.rememberArtworkColor
import cider.remote.app.ui.components.rememberBottomColor
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** Colores de la hoja: blanco sobre el color de la carátula. */
private val Primary = Color.White
private val Secondary = Color.White.copy(alpha = 0.62f)
private val TimeColor = Color.White.copy(alpha = 0.45f)
private val Tertiary = Color.White.copy(alpha = 0.25f)
private val Chip = Color.White.copy(alpha = 0.16f)


enum class SheetMode { Artwork, Lyrics, Queue }

/** Colores de texto e iconos teñidos con el fondo (ver [SheetTint]); los da [NowPlayingSheet]. */
private val LocalSheetTint = androidx.compose.runtime.compositionLocalOf { cider.remote.app.ui.components.SheetTint.Neutral }

/** Tiempos e iconos de la hoja: su blanco translúcido se suma al fondo (vibrancia de iOS) si el tinte lo pide. */
@Composable
private fun Modifier.plusBlend(): Modifier =
    if (LocalSheetTint.current.plus) graphicsLayer { blendMode = androidx.compose.ui.graphics.BlendMode.Plus } else this

private fun Color.lighter(f: Float) = Color((red * f).coerceAtMost(1f), (green * f).coerceAtMost(1f), (blue * f).coerceAtMost(1f), alpha)

/**
 * Fondo de la hoja: color de la carátula, más luminoso que el de las páginas
 * (Apple Music: #7A1C10 para "Obsessed"); blanco al 62 % encima ≥ 4.5:1.
 */
fun sheetBackground(accent: Color?): Color = (accent ?: Color(0xFF2C2C2E)).asBackground(targetLuminance = 0.045f, saturationBoost = 1.15f)

/** [mode] lo guarda quien la abre: en tablet la hoja vuelve a abrirse como se dejó (con la letra o la cola a la vista). */
@Composable
fun NowPlayingSheet(remote: CiderRemote, state: PlayerState, sheet: SheetState, mode: SheetMode, onMode: (SheetMode) -> Unit) {
    val wide = LocalWindowLayout.current.expanded
    // Color de fondo: el dominante de la paleta calibrada con la app oficial (brillo × 0,7); hasta que
    // se calcula, el de `bgColor` oscurecido.
    val palette = rememberArtworkPalette(state.track?.artwork)
    val target = palette?.firstOrNull() ?: sheetBackground(rememberArtworkColor(state.track?.artwork))
    val background by animateColorAsState(target, tween(700), label = "fondo")

    // Derivados: la posición de la hoja cambia en cada fotograma del gesto y leerla aquí recomponía la hoja entera.
    val open by remember(sheet) { derivedStateOf { sheet.isOpen } }
    val moved by remember(sheet) { derivedStateOf { sheet.position.value > 0f } }
    // Teléfono: atrás vuelve de la letra o la cola a la carátula. Tablet: van al lado, así que atrás cierra
    // la hoja y la letra sigue ahí al volver a abrirla.
    BackHandler(enabled = open) { if (!wide && mode != SheetMode.Artwork) onMode(SheetMode.Artwork) else sheet.close() }
    LightStatusBarIcons()
    // Con la letra a la vista la pantalla no se apaga (como Apple Music).
    val view = LocalView.current
    DisposableEffect(view, mode) {
        val lyrics = mode == SheetMode.Lyrics
        if (lyrics) view.keepScreenOn = true
        onDispose { if (lyrics) view.keepScreenOn = false }
    }

    val dismissDrag = Modifier.sheetDrag(sheet)
    val corner = if (moved) 24.dp else 0.dp
    // Portada animada (ya consultada de antemano por PrefetchNowPlaying): la hoja abre con su forma final.
    val catalogId = state.track?.catalogId
    val cover by produceState(AnimatedCovers.peek(catalogId), catalogId) {
        value = AnimatedCovers.peek(catalogId)
        value = AnimatedCovers.load(remote, catalogId)
    }
    val video = if (wide) null else cover?.tall
    // Lo que queda reflejado bajo los controles (la parte baja del fotograma fijo del vídeo): si es claro,
    // se oscurece más (ver HeroArtwork). Los colores del texto los da el propio fondo (SheetTint.Vibrant).
    val under = rememberBottomColor(if (video != null) cover?.heroPreview() ?: state.track?.artwork?.sized(PreviewArtworkPx) else null)
    // Luminancia de la franja baja del vídeo: desde 0,5 se oscurece bajo los controles (ver HeroArtwork).
    val light = under?.let { ((it.luminance() - 0.5f) / 0.3f).coerceIn(0f, 1f) } ?: 0f
    val tint = cider.remote.app.ui.components.SheetTint.Vibrant
    // Con portada animada: dónde cae la carátula fija dentro del vídeo (de ahí sale la pieza al abrir la hoja).
    val context = LocalContext.current
    val squareTop by produceState(AnimatedCovers.peekSquareTop(cover), state.track?.artwork?.url, cover) {
        value = AnimatedCovers.peekSquareTop(cover)
        if (video != null) value = AnimatedCovers.squareTop(context, state.track?.artwork, cover)
    }

    val root = remember { SheetRoot() }
    // Fondo de vidrio propio: los menús de la hoja ("…" y el del equipo) se ven sobre la hoja, no sobre la
    // página de debajo. Dentro de la hoja ninguna pieza lee este fondo (se dibujaría dentro de sí misma);
    // solo los menús emergentes, que viven en otra ventana.
    val sheetGlass = cider.remote.app.ui.components.rememberGlassBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(cider.remote.app.ui.components.LocalGlassBackdrop provides sheetGlass, LocalSheetTint provides tint) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = sheet.offsetPx }
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .glassSource(sheetGlass)
            // La hoja aparece fundiéndose (ver SheetState); la carátula no.
            .drawBehind { drawRect(background, alpha = sheet.contentAlpha) },
    ) {
        // Referencia para el vuelo de la carátula: la hoja sin su traslación.
        Spacer(Modifier.matchParentSize().onGloballyPositioned { root.coordinates = it })
        // Fondo: campo de colores de la carátula con animación lenta (Apple Music), también en tablet;
        // con letra o cola, más oscuro. Con portada animada a la vista, Apple no anima el fondo: queda el
        // color plano de la carátula (el degradado de la caja), y el vídeo se funde en él.
        // Con portada animada a la vista no hay campo: debajo va el borde del vídeo estirado (HeroArtwork).
        if (video == null || mode != SheetMode.Artwork) Box(Modifier.fillMaxSize().sheetFade(sheet)) {
            ColorField(palette, background, dim = if (mode != SheetMode.Artwork) (if (wide) 0.25f else 0.38f) else 0f)
        }
        // Tablet: la portada animada cuadrada dentro de la tarjeta (iPad); el fondo sigue siendo el campo de color.
        // En vertical usa la disposición del teléfono (carátula grande arriba, controles abajo y la letra o la
        // cola a todo el ancho): con la columna de horizontal sobraba media pantalla y todo quedaba apretado.
        val portrait = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }
        // Imagen fija del vídeo cuadrado: con ella se calibra el color del vídeo en la tablet (VideoColor).
        val squareRef = cover?.squarePreview?.sized(480, 480)
        if (wide && !portrait) TabletSheet(remote, state, mode, onMode, dismissDrag, cover?.square, sheet, root, squareRef)
        else PhoneSheet(
            remote, state, mode, onMode, background, dismissDrag, video, sheet, root, squareVideo = if (wide) cover?.square else null,
            squareRef = if (wide) squareRef else null,
            videoPreview = if (video != null) cover?.heroPreview() else null, squareTop = squareTop,
            light = if (video != null) light else 0f, under = under,
        )
        // Asa para indicar que se puede deslizar hacia abajo.
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp).sheetFade(sheet)
                .size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)),
        )
    }
    }
}

/**
 * Fondo de Apple Music (teléfono y tablet): cuatro manchas radiales enormes con los colores
 * dominantes de la carátula, derivando muy despacio (ciclos de 29–43 s; medido en la app
 * oficial: 2–3/255 por canal cada 0,5 s), sobre el color más presente. Se dibuja a ~24 fps
 * solo mientras la hoja está compuesta; la paleta nueva se funde en 1,2 s al cambiar de canción.
 */
@Composable
private fun ColorField(palette: List<Color>?, fallback: Color, dim: Float) {
    val colors = palette?.takeIf { it.isNotEmpty() } ?: listOf(fallback.lighter(0.8f), fallback, fallback.lighter(1.25f), fallback.lighter(0.6f))
    val time = remember { mutableLongStateOf(0L) }
    val shown = remember { mutableStateOf(colors) }
    val previous = remember { mutableStateOf(colors) }
    val switchedAt = remember { mutableLongStateOf(0L) }
    if (shown.value !== colors) { previous.value = shown.value; shown.value = colors; switchedAt.longValue = time.longValue }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) { withFrameNanos { now -> time.longValue = now - start }; delay(38) }
    }
    val periods = floatArrayOf(31f, 37f, 43f, 29f)
    val phases = floatArrayOf(0f, 1.9f, 3.7f, 5.1f)
    Box(Modifier.fillMaxSize().drawBehind {
        val t = time.longValue / 1e9f
        val mix = ((time.longValue - switchedAt.longValue) / 1.2e9f).coerceIn(0f, 1f)
        fun colorAt(i: Int): Color {
            val a = previous.value.getOrElse(i) { previous.value.last() }
            val b = shown.value.getOrElse(i) { shown.value.last() }
            return androidx.compose.ui.graphics.lerp(a, b, mix)
        }
        drawRect(colorAt(0))
        val w = size.width; val h = size.height
        for (i in 0 until 4) {
            val th = t * (2f * Math.PI.toFloat() / periods[i]) + phases[i]
            val cx = w * (0.5f + 0.5f * kotlin.math.cos(th))
            val cy = h * (0.42f + 0.38f * kotlin.math.sin(th * 1.3f + phases[i]))
            val r = w * (0.95f + 0.15f * kotlin.math.sin(th * 0.7f))
            val c = colorAt((i + 1) % 4)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        // Apenas más oscuro abajo (en Apple el fondo es casi uniforme; el oscurecido lo ponen los colores oscuros de la paleta).
        drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f)))
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    })
}

/**
 * Teléfono (Apple Music en iPhone): los controles van a distancias fijas del borde
 * inferior y la carátula ocupa lo que queda arriba, así el título y los botones no
 * se mueven tenga o no portada animada, ni al pasar a letra o cola.
 * Medidas tomadas de una captura de iPhone (centros desde abajo, en pt): título 355,
 * progreso 289, controles 199, volumen 112, botones de abajo 64.
 */
@Composable
private fun PhoneSheet(
    remote: CiderRemote,
    state: PlayerState,
    mode: SheetMode,
    onMode: (SheetMode) -> Unit,
    background: Color,
    dismissDrag: Modifier,
    video: String?,
    sheet: SheetState,
    root: SheetRoot,
    /** Tablet en vertical: portada animada cuadrada dentro de la tarjeta, y su imagen fija (calibración del color). */
    squareVideo: String? = null,
    squareRef: String? = null,
    /** Fotograma fijo del vídeo (imagen de espera) y altura a la que cae en él la carátula fija (fracción del ancho). */
    videoPreview: String? = null,
    squareTop: Float? = null,
    /** Lo clara que es la parte baja del vídeo (0–1): oscurece más bajo los controles. */
    light: Float = 0f,
    /** Color de la parte baja del vídeo (fondo en modo rendimiento, ver HeroArtwork). */
    under: Color? = null,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fade = Modifier.sheetFade(sheet)
    // Margen lateral de título y controles: más ancho en tablet (en vertical), donde además todo va
    // alineado a él. En teléfono, las medidas de Apple Music: la tarjeta de la carátula 9 dp más afuera y
    // la cabecera y la cola con sus márgenes propios.
    val tablet = LocalWindowLayout.current.expanded
    val edge = if (tablet) 64.dp else 33.dp
    // Lo que hay que añadir a una pieza cuyo contenido empieza a [inner] del borde para llevarlo a [edge].
    fun extra(inner: Dp): Dp = if (tablet) edge - inner else 0.dp
    // Con la letra a la vista y sonando, los controles se repliegan a los 4 s sin tocar nada y la
    // letra ocupa la pantalla (Apple Music); cualquier toque los devuelve.
    var touches by remember { mutableIntStateOf(0) }
    var controlsHidden by remember { mutableStateOf(false) }
    // Carátula que vuela entre la tarjeta grande y la cabecera pequeña de letra y cola (ver ArtSwap).
    val swap = remember { ArtSwap(if (mode == SheetMode.Artwork) 0f else 1f) }
    LaunchedEffect(mode) { swap.go(if (mode == SheetMode.Artwork) 0f else 1f) }
    // La letra de la canción que suena, pedida de antemano: al abrirla ya está y aparece con el vuelo.
    val lyricsId = state.track?.catalogId
    LaunchedEffect(lyricsId) { if (lyricsId != null) LyricsCache.load(remote, lyricsId) }
    LaunchedEffect(mode, touches, state.isPlaying) {
        controlsHidden = false
        if (mode == SheetMode.Lyrics && state.isPlaying) { delay(4000); controlsHidden = true }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Solo observa (no consume): los toques siguen llegando a la letra y a los botones.
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) touches++
                }
            }
        },
    ) {
        val width = maxWidth
        // Portada animada: 3:4 a todo el ancho, detrás de todo y fundida con el fondo.
        // Al pasar entre la letra o la cola y la carátula, el propio vídeo vuela desde la miniatura de la cabecera
        // (o hacia ella) como al abrir la hoja: SheetFly con el progreso del cambio. Sigue compuesto mientras dura.
        // Antes entraba con su fundido a toda la pantalla mientras la carátula crecía encima (dos portadas).
        swap.video = video != null
        swap.sheetRoot = root
        // Al terminar de ir a la letra, el vuelo suelta la pieza un fotograma antes de que salga de la composición
        // y se veía en su sitio a pantalla completa: fuera de la carátula y sin vuelo, no se dibuja.
        // Con la letra o la cola a la vista sigue compuesta, en pausa y sin dibujarse: sacarla de la composición y
        // volver a crearla (reproductor, vista del vídeo) era lo que más tardaba al volver a la carátula, y el
        // vídeo se recargaba: se veía un segundo el fotograma fijo y luego arrancaba de cero. Ahora continúa donde
        // estaba. No se rebobina al ocultarla: hay vídeos que empiezan con un destello ("SOUR") y caía en pleno vuelo.
        val heroShown = mode == SheetMode.Artwork
        val heroVisible = heroShown || swap.active
        if (video != null) Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = if (heroShown || swap.active) 1f else 0f }
                .drawWithContent { if (heroShown || swap.active) drawContent() },
        ) {
            // Al abrir y cerrar vuela desde la miniatura, pegada a la izquierda: sale cuadrada, con la carátula fija,
            // y se alarga hasta la hoja entera (vídeo y su reflejo).
            HeroArtwork(
                state.track?.artwork, video, videoPreview, width * 4 / 3, if (heroVisible) dismissDrag else Modifier,
                rememberSheetFly(sheet, root, pinLeft = true, corner = 24.dp, videoAspect = 4f / 3f, squareTop = squareTop, swap = swap),
                light = light, under = under, playing = heroVisible,
            )
        }
        Column(Modifier.fillMaxSize()) {
            // La carátula vuela de la tarjeta grande a la cabecera pequeña (letra/cola) y vuelve, como al abrir
            // la hoja (ArtSwap); el resto se funde.
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { swap.root = it }) {
            // Modo rendimiento: sin fundido (los dos modos a la vez son dos capas fuera de pantalla casi del tamaño
            // de la hoja durante un cuarto de segundo); el modo nuevo aparece ya y la carátula vuela encima.
            val liteSwap = cider.remote.app.ui.Appearance.lite
            AnimatedContent(
                mode, Modifier.fillMaxSize(),
                transitionSpec = {
                    if (liteSwap) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    else fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                },
                label = "modo",
            ) { m ->
                when (m) {
                    SheetMode.Artwork -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        // Sin vídeo, tarjeta centrada a 24 dp de los lados y 44 dp sobre el título.
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(dismissDrag).statusBarsPadding()) {
                            if (video == null) {
                                val side = minOf(width - (if (tablet) edge else 24.dp) * 2, maxHeight - 44.dp - 34.dp)
                                // Como iOS: la carátula se encoge al pausar y vuelve con un muelle al reproducir.
                                val scale = animateFloatAsState(if (state.isPlaying) 1f else 0.78f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "pausa")
                                // Al abrir y cerrar la hoja vuela desde la miniatura del miniplayer (la escala de pausa va en la misma capa).
                                val fly = rememberSheetFly(sheet, root, restScale = { scale.value })
                                swap.bigScale = { scale.value }
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp)
                                        .onGloballyPositioned { swap.measureBig(it) }
                                        .graphicsLayer { alpha = if (swap.active) 0f else 1f }.then(fly.outer),
                                ) {
                                    val cardShape = RoundedCornerShape(10.dp)
                                    val card = fly.inner.shadow(24.dp, cardShape, ambientColor = Color.Black, spotColor = Color.Black)
                                    if (squareVideo != null) {
                                        val sidePx = with(LocalDensity.current) { side.roundToPx() }
                                        AnimatedCover(squareVideo, Modifier.size(side).then(card).clip(cardShape), maxVideoSize = IntSize(sidePx, sidePx), reference = squareRef) {
                                            ArtworkImage(state.track?.artwork, side, px = SheetArtworkPx, preview = PreviewArtworkPx, shape = cardShape)
                                        }
                                    } else ArtworkImage(state.track?.artwork, side, px = SheetArtworkPx, preview = PreviewArtworkPx, shape = cardShape, modifier = card)
                                }
                            }
                        }
                        // Medido contra Apple Music (iOS 26): botones 3,5 dp más adentro y el título 1,5 dp más arriba.
                        Box(Modifier.padding(start = edge, end = edge - 7.5.dp).offset(y = (-1.5).dp).then(fade)) { TitleRow(remote, state) }
                    }
                    }
                    SheetMode.Lyrics, SheetMode.Queue -> Box(Modifier.fillMaxSize()) {
                        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        if (m == SheetMode.Lyrics) {
                            // La letra sube por debajo de la cabecera y se desvanece al llegar a ella (Apple
                            // Music): la vista empieza arriba y la línea activa queda donde estaba.
                            val fadeFrom = with(LocalDensity.current) { (statusTop + 72.dp).toPx() }
                            val fadeTo = with(LocalDensity.current) { (statusTop + CompactHeaderHeight + 14.dp).toPx() }
                            // Modo rendimiento: la letra se corta en seco al borde de la cabecera. La máscara es una capa
                            // fuera de pantalla del tamaño de la vista que se rehace en cada fotograma mientras suena.
                            val cutAt = with(LocalDensity.current) { (statusTop + CompactHeaderHeight).toPx() }
                            LyricsView(
                                remote, state,
                                Modifier.fillMaxSize().padding(horizontal = extra(LyricsSidePadding)).then(fade)
                                    .then(
                                        if (liteSwap) Modifier.drawWithContent { clipRect(top = cutAt) { this@drawWithContent.drawContent() } }
                                        else Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = fadeFrom, endY = fadeTo),
                                                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                                )
                                            },
                                    ),
                                anchorTop = statusTop + CompactHeaderHeight + 34.dp,
                            )
                        } else Column(
                            // La lista trae 8 dp a la izquierda y sus asas quedan a 18 dp del borde: en teléfono se
                            // alinea con la cabecera (carátula a 28 dp, botón "…" a 24 dp); antes iba pegada al borde.
                            Modifier.fillMaxSize().padding(top = statusTop + CompactHeaderHeight)
                                .padding(start = if (tablet) extra(8.dp) else 20.dp, end = if (tablet) extra(18.dp) else 6.dp).then(fade),
                        ) {
                            QueuePanel(remote, state, Modifier.fillMaxSize())
                        }
                        Box(Modifier.statusBarsPadding().padding(start = extra(28.dp), end = extra(24.dp))) {
                            CompactHeader(
                                remote, state, dismissDrag,
                                artwork = Modifier.onGloballyPositioned { swap.measureSmall(it) }.graphicsLayer { alpha = if (swap.active) 0f else 1f },
                                onArtwork = { onMode(SheetMode.Artwork) },
                                fly = rememberSheetFly(sheet, root), fade = fade,
                            )
                        }
                    }
                }
            }
            // Encima de los dos modos: la carátula que vuela entre la tarjeta y la cabecera.
            // Con portada animada vuela el propio vídeo (arriba); la carátula sola, solo sin él.
            if (video == null) ArtSwapOverlay(swap, state.track?.artwork)
            }
            AnimatedVisibility(
                !(controlsHidden && mode == SheetMode.Lyrics),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(Modifier.padding(horizontal = edge).then(fade)) {
                    Spacer(Modifier.height(8.dp))
                    Progress(remote, state)
                    // Medido contra Apple Music (iOS 26, "The Mirror"): barra a 21 dp de los tiempos y 94 dp sobre el
                    // centro de los controles; con 10 dp quedaban 7,6 dp más abajo (y los tiempos, 16).
                    Spacer(Modifier.height(26.dp))
                    Transport(remote, state)
                    Spacer(Modifier.height(25.dp))
                    VolumeRow(remote, state)
                    BottomBar(remote, state, mode, onMode, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(max(nav, 16.dp) + 25.dp))
                }
            }
        }
    }
}

/**
 * Tablet en horizontal (Apple Music en iPad): columna con la carátula y los controles; sola y
 * centrada en modo carátula, a la izquierda con la letra o la cola a la derecha.
 * Abajo, el dispositivo a la izquierda y letra/cola a la derecha.
 * En vertical la tablet usa la disposición del teléfono ([PhoneSheet]).
 */
@Composable
private fun TabletSheet(
    remote: CiderRemote, state: PlayerState, mode: SheetMode, onMode: (SheetMode) -> Unit, dismissDrag: Modifier, squareVideo: String?,
    sheet: SheetState, root: SheetRoot, squareRef: String? = null,
) {
    // Al abrir y cerrar la hoja la carátula vuela desde la miniatura del miniplayer y el resto se funde.
    val fade = Modifier.sheetFade(sheet)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val w = maxWidth
        val h = maxHeight
        val panel = mode != SheetMode.Artwork
        // Proporciones del iPad en horizontal: carátula 0,54 del alto.
        val art = minOf(h * 0.54f, h - 340.dp, w * 0.4f).coerceAtLeast(160.dp)
        // Con letra o cola, la columna va centrada en la mitad izquierda y el panel empieza en el centro:
        // el margen del borde a la carátula, el de la carátula a la letra y el de la letra al borde
        // derecho son iguales (antes la columna quedaba más cerca del borde que de la letra).
        val margin = ((w / 2 - art) / 2).coerceAtLeast(24.dp)
        val center by animateDpAsState(if (panel) margin + art / 2 else w / 2, label = "columna")
        val topPad = h * 0.07f
        Column(
            Modifier.width(art).offset { IntOffset((center - art / 2).roundToPx(), 0) }.padding(top = topPad),
        ) {
            val scale = animateFloatAsState(if (state.isPlaying) 1f else 0.78f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "pausa")
            // La escala de pausa va en la misma capa que el vuelo.
            val fly = rememberSheetFly(sheet, root, restScale = { scale.value })
            Box(
                Modifier.then(dismissDrag).then(if (panel) Modifier.combinedClickableCompat(onClick = { onMode(SheetMode.Artwork) }) else Modifier)
                    .then(fly.outer),
            ) {
                val cardShape = RoundedCornerShape(10.dp)
                val artPx = with(androidx.compose.ui.platform.LocalDensity.current) { art.roundToPx() }
                // Portada animada cuadrada (la de la tarjeta del iPad), con la carátula fija debajo hasta
                // que el vídeo pinta; se reproduce a la resolución de la tarjeta, sin sonido.
                AnimatedCover(
                    squareVideo,
                    Modifier.size(art).then(fly.inner)
                        .shadow(24.dp, cardShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(cardShape),
                    maxVideoSize = androidx.compose.ui.unit.IntSize(artPx, artPx),
                    reference = squareRef,
                ) {
                    ArtworkImage(state.track?.artwork, art, px = WideSheetArtworkPx, preview = PreviewArtworkPx, shape = cardShape)
                }
            }
            Column(fade) {
                Spacer(Modifier.height(16.dp))
                TitleRow(remote, state, compact = true)
                Spacer(Modifier.height(4.dp))
                Progress(remote, state)
                Transport(remote, state, compact = true)
                VolumeRow(remote, state)
            }
        }
        AnimatedVisibility(panel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize().padding(bottom = 64.dp).then(fade)) {
            when (mode) {
                // La línea activa de la letra y el título de la cola arrancan a la altura del borde superior
                // de la carátula; el texto empieza en el centro de la pantalla y acaba a `margin` del borde.
                SheetMode.Lyrics -> LyricsView(
                    remote, state,
                    Modifier.fillMaxSize().padding(start = w / 2 - LyricsSidePadding, end = (margin - LyricsSidePadding).coerceAtLeast(0.dp)),
                    anchorTop = (topPad - 8.dp).coerceAtLeast(0.dp),
                )
                SheetMode.Queue -> QueuePanel(
                    remote, state,
                    Modifier.fillMaxSize().padding(start = w / 2 - 8.dp, end = (margin - 18.dp).coerceAtLeast(0.dp), top = (topPad - 18.dp).coerceAtLeast(0.dp)),
                )
                SheetMode.Artwork -> Unit
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset { IntOffset((center - art / 2 - 12.dp).roundToPx(), 0) }.padding(bottom = 8.dp).then(fade)) {
            OutputButton(remote)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 8.dp).then(fade)) {
            ModeButtons(mode, onMode, spacing = 0.dp)
        }
    }
}

/** Alto de la cabecera de letra/cola: 30 dp arriba, carátula de 70 dp, 20 dp abajo. */
private val CompactHeaderHeight = 120.dp

/**
 * Portada animada a todo el ancho (3:4) con lo que Apple Music pone debajo: la parte baja del
 * vídeo **reflejada** en su borde inferior y estirada, con un desenfoque que crece hacia abajo,
 * en tiempo real. Ajustado a una captura de Apple Music ("Mercury" de Steve Lacy, 2026-11-16)
 * con el fotograma original del vídeo: reflejo estirado ×1,8; junto al vídeo un desenfoque suave
 * (σ ≈ 0,035 × ancho: la camiseta blanca del final del vídeo se sigue reconociendo) que entre el
 * 85 % y el 120 % del alto del vídeo pasa a uno muy ancho (σ ≈ 0,22 × ancho: abajo queda el rojo
 * de la cara, no el gris de la última franja, que es lo que daba estirar solo el borde). Error
 * del modelo 7/255 (con un solo desenfoque ancho, 12; estirando la última franja, peor aún).
 * El vídeo (un TextureView) se graba en una capa y se vuelve a dibujar con su reflejo en dos
 * capas a 1/4 de resolución; encima, el vídeo nítido se funde entre el 76 % y el 92 % de su alto
 * (en arco, ver [BlurArcCenter]) y un velo oscuro crece desde el borde del vídeo hasta ~0,37 abajo (Apple no oscurece el vídeo).
 */
@Composable
private fun HeroArtwork(artwork: Artwork?, video: String?, preview: String?, height: Dp, dragModifier: Modifier, fly: SheetFly, light: Float = 0f, under: Color? = null, playing: Boolean = true) {
    // Modo rendimiento: sin reflejo desenfocado; debajo, un degradado del color de la parte baja del vídeo
    // (con el mismo ajuste que el reflejo) que se oscurece hacia abajo, y el vídeo se funde en ese color.
    val lite = cider.remote.app.ui.Appearance.lite
    val base = under ?: Color(0xFF242424)
    val videoLayer = rememberGraphicsLayer()
    val near = rememberGraphicsLayer()
    val far = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().then(fly.outer)) {
    Box(Modifier.fillMaxSize().then(fly.inner)) {
        if (lite) Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val vh = height.toPx()
                val bottom = Color(base.red * 0.5f, base.green * 0.5f, base.blue * 0.5f)
                val brush = Brush.verticalGradient(listOf(base, bottom), startY = vh, endY = size.height)
                onDrawBehind { if (vh < size.height) drawRect(brush, topLeft = Offset(0f, vh), colorFilter = MirrorColor) }
            },
        ) else Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val k = MirrorScale
                val w = size.width
                val h = size.height
                val vh = height.toPx()
                val small = IntSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
                // La capa va a 1/4 y el radio de BlurEffect se convierte en σ = 0,577 · radio + 0,5.
                fun blur(sigma: Float): BlurEffect {
                    val radius = ((w * sigma * k - 0.5f) / 0.57735f).coerceAtLeast(1f)
                    return BlurEffect(radius, radius, TileMode.Clamp)
                }
                val nearBlur = blur(MirrorNearSigma)
                val farBlur = blur(MirrorFarSigma)
                // La suave se va en arco, con el mismo centro que el fundido del vídeo: en el eje, del 85 %
                // al 120 % del alto del vídeo.
                val nearMask = Brush.radialGradient(
                    *fadeStops((0.85f - BlurArcCenter) / (1.2f - BlurArcCenter), 1f, from = 1f, to = 0f, steps = 40),
                    center = Offset(w * k / 2f, vh * k * BlurArcCenter),
                    radius = vh * k * (1.2f - BlurArcCenter),
                )
                // El propio vídeo (bajo su fundido queda su versión desenfocada, alineada) y su reflejo
                // estirado: la fila y del vídeo cae en vh + (vh − y) · estirado.
                val reflected: DrawScope.() -> Unit = {
                    scale(k, k, pivot = Offset.Zero) {
                        clipRect(0f, 0f, w, vh) { drawLayer(videoLayer) }
                        clipRect(0f, vh, w, h) {
                            withTransform({
                                translate(0f, vh * (1f + MirrorStretch))
                                scale(1f, -MirrorStretch, pivot = Offset.Zero)
                            }) { drawLayer(videoLayer) }
                        }
                    }
                }
                onDrawBehind {
                    if (vh >= h || w <= 0f) return@onDrawBehind
                    far.renderEffect = farBlur
                    far.colorFilter = MirrorColor
                    near.colorFilter = MirrorColor
                    far.record(small) { reflected() }
                    // La capa suave solo se ve junto al vídeo: se recorta antes de desenfocarla.
                    near.renderEffect = nearBlur
                    near.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    near.record(small) {
                        reflected()
                        drawRect(nearMask, blendMode = BlendMode.DstIn)
                    }
                    // Solo donde se ven: por encima del 60 % del alto el vídeo nítido las tapa del todo (el arco
                    // de su fundido llega al 62 % en los bordes) y la suave acaba en el 120 % (mezclarlas a
                    // pantalla completa es lo que más cuesta).
                    clipRect(top = vh * 0.6f) {
                        scale(1f / k, 1f / k, pivot = Offset.Zero) { drawLayer(far) }
                    }
                    clipRect(top = vh * 0.6f, bottom = vh * 1.2f) {
                        scale(1f / k, 1f / k, pivot = Offset.Zero) { drawLayer(near) }
                    }
                }
            },
        )
        Box(
            Modifier.fillMaxWidth().height(height).then(dragModifier)
                .then(if (lite) Modifier.drawWithCache {
                    val fade = Brush.verticalGradient(0.72f to Color.Transparent, 1f to base, startY = 0f, endY = size.height)
                    onDrawWithContent { drawContent(); drawRect(fade, colorFilter = MirrorColor) }
                } else Modifier
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithCache {
                    // En arco: círculo centrado al 20 % del alto; en el eje se funde del 76 % al 92 % y en
                    // los bordes unos 11 puntos más arriba.
                    val mask = Brush.radialGradient(
                        *fadeStops((0.76f - BlurArcCenter) / (0.92f - BlurArcCenter), 1f, from = 1f, to = 0f, steps = 50),
                        center = Offset(size.width / 2f, size.height * BlurArcCenter),
                        radius = size.height * (0.92f - BlurArcCenter),
                    )
                    onDrawWithContent {
                        videoLayer.record { this@onDrawWithContent.drawContent() }
                        drawLayer(videoLayer)
                        // Debajo está su versión desenfocada, alineada.
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                }),
        ) {
            AnimatedCover(video, Modifier.fillMaxSize(), playing = playing, reference = preview) {
                AsyncImage(
                    model = artwork?.sized(PreviewArtworkPx),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Imagen de espera: el fotograma fijo del propio vídeo. Es su primer fotograma, así que cuando el
                // vídeo por fin carga empieza desde la misma imagen; la carátula ampliada (lo que había, y lo que
                // queda si Apple no da fotograma) tiene otro encuadre y el cambio se veía brusco. Una sola imagen
                // grande: va debajo del vídeo y se redibuja con cada fotograma suyo.
                AsyncImage(
                    model = preview ?: artwork?.sized(HeroArtworkPx),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Velo oscuro del borde del vídeo hacia abajo, casi lineal (tramo central de la curva): ~0,18 a media
        // altura de lo que queda y ~0,37 en el borde de la pantalla.
        // Con la parte baja del vídeo clara (nubes blancas de "DI MI NOMBRE": luminancia 0,92) el título, los
        // botones y los tiempos blancos no se leían: un velo más, desde el 60 % del vídeo, que a la altura del
        // título llega a 0,45 y abajo a 0,6, proporcional a [light] (0 hasta luminancia 0,5, 1 desde 0,8).
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val vh = height.toPx() / size.height
                val veil = fade(vh * 0.85f, 1.2f, from = 0f, to = 0.48f, steps = 24)
                val extra = if (light > 0f) Brush.verticalGradient(
                    0f to Color.Transparent,
                    vh * 0.6f to Color.Transparent,
                    vh * 0.9f to Color.Black.copy(alpha = 0.45f * light),
                    1f to Color.Black.copy(alpha = 0.6f * light),
                ) else null
                onDrawBehind {
                    if (vh < 1f) drawRect(veil)
                    extra?.let { drawRect(it) }
                }
            },
        )
        // Al salir del miniplayer (y al volver a él) la pieza es su miniatura: la carátula fija tapa el
        // vídeo y se funde con el recorrido; en reposo va con alfa 0 y no se dibuja.
        fly.cover?.let { cover ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f).then(cover)) {
                AsyncImage(model = artwork?.sized(PreviewArtworkPx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                AsyncImage(model = artwork?.sized(HeroArtworkPx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
    }
}

/**
 * Reflejo bajo la portada animada: resolución de las capas, estirado vertical y σ de los dos
 * desenfoques (× ancho): el suave de junto al vídeo y el ancho de abajo.
 */
private const val MirrorScale = 0.25f
private const val MirrorStretch = 1.8f
private const val MirrorNearSigma = 0.035f
private const val MirrorFarSigma = 0.22f


/**
 * El reflejo de Apple es más vivo y oscuro que el vídeo: saturación × 1,3 y brillo × 0,82. Comprobado
 * contra una captura real de iOS 26 con "Mercury" (convertida de Display P3 a sRGB) con el color del
 * vídeo ya corregido (`VideoColor`): entre el progreso y los controles (128, 66, 56) frente a
 * (124, 64, 55). La franja de abajo saturada aparte (×1,6) sobraba con el vídeo corregido y se quitó.
 */
private val MirrorColor = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix().apply {
        setToSaturation(1.3f)
        for (row in 0..2) for (col in 0..4) this[row, col] = this[row, col] * 0.82f
    },
)

/**
 * El desenfoque no empieza en una línea recta sino en un arco de círculo, más arriba en los lados
 * que en el centro (medido en un vídeo de Apple Music con "SOS" de SZA: la nitidez cae al 30 % en
 * el 77 % del alto del vídeo en los bordes y en el 87 % en el centro; encaja un círculo centrado
 * en el eje, al 20 % del alto). Centro del círculo como fracción del alto del vídeo.
 */
private const val BlurArcCenter = 0.2f

/**
 * Máscara vertical de opacidad [from] a [to] entre las fracciones [a] y [b] del alto, con
 * curva suave (con tramos rectos se ve una línea donde cambia la pendiente).
 */
private fun fade(a: Float, b: Float, from: Float, to: Float, steps: Int = 16): Brush =
    Brush.verticalGradient(*fadeStops(a, b, from, to, steps))

/** Los tramos de [fade], para usarlos también en un degradado radial (fracciones del radio). */
private fun fadeStops(a: Float, b: Float, from: Float, to: Float, steps: Int = 16): Array<Pair<Float, Color>> =
    Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val x = if (b <= a) (if (t >= a) 1f else 0f) else ((t - a) / (b - a)).coerceIn(0f, 1f)
        val e = x * x * (3 - 2 * x)
        t to Color.Black.copy(alpha = (from + (to - from) * e).coerceIn(0f, 1f))
    }

@Composable
private fun CompactHeader(
    remote: CiderRemote,
    state: PlayerState,
    dragModifier: Modifier,
    artwork: Modifier = Modifier,
    onArtwork: (() -> Unit)? = null,
    /** Vuelo de la carátula y fundido del resto al abrir y cerrar la hoja (teléfono). */
    fly: SheetFly? = null,
    fade: Modifier = Modifier,
) {
    val track = state.track
    Row(
        Modifier.fillMaxWidth().then(dragModifier).padding(start = 28.dp, end = 18.dp, top = 30.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (toAlbum, toArtist) = rememberSongNavigation(remote, state)
        // Tocar la carátula pequeña vuelve a la grande (Apple Music).
        val back = stringResource(R.string.action_show_artwork)
        Box(artwork.then(fly?.outer ?: Modifier)) {
            ArtworkImage(
                track?.artwork, 70.dp, shape = RoundedCornerShape(6.dp),
                modifier = (fly?.inner ?: Modifier).then(
                    if (onArtwork != null) Modifier.combinedClickableCompat(onClick = onArtwork).semantics { contentDescription = back; role = Role.Button }
                    else Modifier,
                ),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp).then(fade)) {
            TitleText(state, Apple.type.headline.copy(fontSize = 18.sp), Modifier.combinedClickableCompat(onClick = toAlbum))
            Text(track?.artistName.orEmpty(), style = Apple.type.body, color = LocalSheetTint.current.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.plusBlend().combinedClickableCompat(onClick = toArtist))
        }
        Box(fade) { MoreMenu(remote, state) }
    }
}

/**
 * Tocar el título abre el álbum de la canción actual y tocar el artista, su página
 * (consulta de Apple Music con `include=albums,artists`; la hoja se cierra al abrir).
 */
@Composable
private fun rememberSongNavigation(remote: CiderRemote, state: PlayerState): Pair<() -> Unit, () -> Unit> {
    val open = LocalOpenRoute.current
    val scope = rememberCoroutineScope()
    val id = state.track?.playParams?.id
    fun go(pick: (SongLinks) -> HomeItem?) {
        id ?: return
        scope.launch { runCatching { remote.api.songLinks(id) }.getOrNull()?.let(pick)?.toRoute()?.let(open) }
    }
    return Pair({ go { it.album } }, { go { it.artist } })
}

/** Título con la "E" de contenido explícito detrás, como Apple Music. */
@Composable
private fun TitleText(state: PlayerState, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val track = state.track
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            track?.name ?: stringResource(R.string.status_not_playing), style = style,
            color = LocalSheetTint.current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).plusBlend(),
        )
        if (track?.isExplicit == true) {
            val description = stringResource(R.string.explicit)
            val LocalSheetTintPlus = LocalSheetTint.current.plus
            // iOS 26: cuadro de blanco sumado al fondo (+45, como la pista de las barras) con la "E" recortada
            // (se ve el fondo a través) y seminegrita. La capa propia hace que el recorte solo afecte al cuadro.
            Box(
                Modifier.padding(start = 7.dp).size(15.dp)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        if (LocalSheetTintPlus) blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                    }
                    .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = if (LocalSheetTintPlus) 0.18f else 0.62f))
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "E", style = Apple.type.caption.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold), color = Color.Black,
                    modifier = Modifier.graphicsLayer { blendMode = androidx.compose.ui.graphics.BlendMode.DstOut },
                )
            }
        }
    }
}

@Composable
private fun TitleRow(remote: CiderRemote, state: PlayerState, compact: Boolean = false) {
    val (toAlbum, toArtist) = rememberSongNavigation(remote, state)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val size = if (compact) 17.sp else 22.sp
            // iOS 26: 24 dp entre las líneas de título y artista; aquí 26,4, y bajar el interlineado no mueve nada
            // (la fila mide lo que los botones de 44 dp), así que el artista se sube 2,2 dp.
            val line = size * 1.2f
            TitleText(state, Apple.type.title2.copy(fontSize = size, lineHeight = line, fontWeight = FontWeight.SemiBold), Modifier.combinedClickableCompat(onClick = toAlbum))
            Text(
                state.track?.artistName.orEmpty(), style = Apple.type.title3.copy(fontSize = size, lineHeight = line, fontWeight = FontWeight.Normal),
                color = LocalSheetTint.current.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.then(if (compact) Modifier else Modifier.offset(y = (-2.2).dp)).plusBlend().combinedClickableCompat(onClick = toArtist),
            )
        }
        // Favorita junto al "…" (iOS 26): centros a 46 dp.
        if (state.track != null) {
            val loved = state.inFavorites == true
            GlassCircle(
                if (loved) Icons.Filled.Star else Icons.Filled.StarBorder,
                stringResource(if (loved) R.string.action_unfavorite else R.string.action_favorite),
            ) { remote.toggleLove() }
            Spacer(Modifier.width(2.dp))
        }
        MoreMenu(remote, state)
    }
}

/** "…": favorita, biblioteca y aleatorio (Apple Music deja solo este botón junto al título). */
@Composable
private fun MoreMenu(remote: CiderRemote, state: PlayerState) {
    var menu by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    val session = LocalContext.current.app.session
    val sleep by session.sleep.collectAsStateWithLifecycle()
    if (sleepDialog) SleepTimerDialog(onDismiss = { sleepDialog = false }) { minutes -> sleepDialog = false; session.setSleepTimer(minutes) }
    Box {
        GlassCircle(Icons.Filled.MoreHoriz, stringResource(R.string.action_more)) { menu = true }
        // De vidrio como los demás menús, con la paleta oscura: la hoja siempre es oscura.
        cider.remote.app.ui.ForceDark {
            cider.remote.app.ui.components.GlassMenu(menu, onDismiss = { menu = false }, width = 260.dp) {
                val loved = state.inFavorites == true
                cider.remote.app.ui.components.MenuRow(
                    if (loved) Icons.Filled.Star else Icons.Filled.StarBorder,
                    stringResource(if (loved) R.string.action_unfavorite else R.string.action_favorite),
                ) { menu = false; remote.toggleLove() }
                when (state.inLibrary) {
                    true -> cider.remote.app.ui.components.MenuRow(Icons.Filled.Delete, stringResource(R.string.action_remove_from_library)) { menu = false; remote.toggleInLibrary() }
                    false -> cider.remote.app.ui.components.MenuRow(Icons.Filled.Add, stringResource(R.string.action_add_to_library)) { menu = false; remote.toggleInLibrary() }
                    null -> Unit
                }
                cider.remote.app.ui.components.MenuRow(
                    Icons.Rounded.Shuffle,
                    stringResource(if (state.shuffle) R.string.action_shuffle_off else R.string.action_shuffle_on),
                ) { menu = false; remote.toggleShuffle() }
                cider.remote.app.ui.components.MenuRow(
                    Icons.Filled.Bedtime, stringResource(R.string.sleep_timer), sleepLabel(sleep),
                ) { menu = false; sleepDialog = true }
            }
        }
    }
}

/** Lo que queda del temporizador ("23 min", "Al acabar la canción") o "Desactivado"; se actualiza cada 20 s. */
@Composable
private fun sleepLabel(timer: cider.remote.app.session.SleepTimer?): String {
    val now by produceState(System.currentTimeMillis(), timer) {
        while (true) { value = System.currentTimeMillis(); delay(20_000) }
    }
    return when {
        timer == null -> stringResource(R.string.sleep_off)
        timer.endOfTrack -> stringResource(R.string.sleep_end_of_track)
        else -> stringResource(R.string.sleep_remaining, (((timer.endsAtMs - now) / 60_000.0).let { kotlin.math.ceil(it) }.toInt()).coerceAtLeast(1))
    }
}

/** Opciones del temporizador de apagado (pausa Cider). */
@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val options = listOf(
        15 to stringResource(R.string.sleep_minutes, 15), 30 to stringResource(R.string.sleep_minutes, 30),
        45 to stringResource(R.string.sleep_minutes, 45), 60 to stringResource(R.string.sleep_hour),
        0 to stringResource(R.string.sleep_end_of_track),
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Text(
                        // Color del texto principal: con el del cuerpo del diálogo parecían desactivadas.
                        label, style = Apple.type.body, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickableCompat(onClick = { onPick(minutes) }).padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.sleep_turn_off)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Círculo translúcido de 31 dp con glifo de 23 dp (iOS 26: estrella de 18 dp, "…" de 16) y área táctil de 44 dp. */
@Composable
private fun GlassCircle(icon: ImageVector, description: String, tint: Color = Primary, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(31.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun Progress(remote: CiderRemote, state: PlayerState) {
    val progress = state.progress
    var position by remember { mutableDoubleStateOf(progress.positionNow()) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(progress) {
        while (true) {
            position = progress.positionNow()
            if (!progress.playing) break
            // 10 veces por segundo basta (la barra avanza ~6 px/s): a cada fotograma mantenía la hoja a 120 fps.
            delay(100)
        }
    }
    // Se vuelve a leer cada 5 s: cambia también al activar o desactivar Atmos, no solo con la canción.
    val quality by produceState<AudioQuality?>(null, state.track?.playParams?.id) {
        while (true) {
            runCatching { remote.api.audioQuality() }.getOrNull()?.let { value = it }
            delay(5_000)
        }
    }
    val duration = progress.durationSec.coerceAtLeast(0.001)
    val shown = dragging?.let { it * duration } ?: position
    ThinSlider(
        modifier = Modifier.plusBlend(),
        value = dragging ?: (position / duration).toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { remote.seek(it * duration) }; dragging = null },
        activeColor = LocalSheetTint.current.fill,
        inactiveColor = LocalSheetTint.current.track,
        description = stringResource(R.string.playback_position, formatTime(shown), formatTime(duration)),
    )
    // Tiempos 8,5 dp más pegados a la barra (Apple: 21 dp entre centros): la caja se sube y ocupa eso menos.
    val TimeColor = LocalSheetTint.current.time
    val Secondary = LocalSheetTint.current.icon
    Box(Modifier.fillMaxWidth().layout { m, c ->
        val p = m.measure(c); val up = 8.5.dp.roundToPx()
        layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
    }) {
        // Medium: en una captura de iOS ("SOS") el grosor de los tiempos y de "Lossless" queda entre
        // Inter Regular y Medium (densidad de tinta 0,30–0,35; Inter SemiBold da 0,39–0,43).
        val timeStyle = Apple.type.caption.copy(fontWeight = FontWeight.Medium).tabular()
        Text(formatTime(shown), style = timeStyle, color = TimeColor, modifier = Modifier.align(Alignment.CenterStart).plusBlend())
        // Como iOS 26: logo y texto sin recuadro, en el color de los tiempos (sumado a lo de detrás).
        // Solo cuando Cider dice que suena así (en un PC sin Atmos, Cider reproduce AAC aunque la
        // canción tenga versión Atmos).
        // "Lossless" va en un recuadro translúcido con la onda, algo más claro que los tiempos (captura de
        // iOS con "SOS").
        quality?.badge?.let { badge ->
            // Logotipo oficial de Dolby Atmos (vector del SVG, res/drawable/dolby_atmos.xml) al ancho medido en
            // "MOON MUSiC" (iOS 26): 87,3 dp, apoyado en la línea base de los tiempos (la suya está al 76,9 %
            // de su alto). El texto invisible da esa línea base en una fila de la misma altura que los tiempos.
            if (badge == "Dolby Atmos") Row(Modifier.align(Alignment.Center).plusBlend()) {
                Text("0", style = timeStyle, color = Color.Transparent, modifier = Modifier.width(0.dp).alignByBaseline())
                Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.dolby_atmos), stringResource(R.string.audio_atmos), tint = TimeColor,
                    modifier = Modifier.size(87.3.dp, 12.33.dp).alignBy { (it.measuredHeight * (12.0280151f / 15.6427517f)).toInt() },
                )
            } else Row(
                // Medido en una captura ampliada de iOS (en múltiplos de la altura de mayúsculas, 8,7 dp aquí):
                // recuadro de 2,4 (21 dp), fondo +21 (blanco 8 %), logo 15 × 9,5 dp a 6,6 dp del borde y del
                // texto, que acaba a 7,2 dp del borde. Texto: Inter Medium (densidad 0,34 frente a 0,33).
                Modifier.align(Alignment.Center).plusBlend().height(21.dp).clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.08f)).padding(start = 6.6.dp, end = 7.2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.apple_lossless), null, tint = Secondary, modifier = Modifier.size(15.dp, 9.dp))
                Text(badge, style = timeStyle, color = Secondary, modifier = Modifier.padding(start = 6.6.dp))
            }
        }
        Text("\u2212" + formatTime(duration - shown), style = timeStyle, color = TimeColor, modifier = Modifier.align(Alignment.CenterEnd).plusBlend())
    }
}

private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

@Composable
private fun Transport(remote: CiderRemote, state: PlayerState, compact: Boolean = false) {
    if (compact) {
        // iPad: aleatorio · anterior · play · siguiente · repetir a lo ancho de la columna.
        val dim = Color.White.copy(alpha = 0.45f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Icons.Rounded.Shuffle, stringResource(R.string.action_shuffle), 20.dp, 44.dp, tint = if (state.shuffle) Primary else dim) { remote.toggleShuffle() }
            GlyphButton(AppleIcons.Backward, stringResource(R.string.action_previous), 36.dp, 56.dp, onClick = remote::previous)
            PlayPause(remote, state, 40.dp, 64.dp)
            GlyphButton(AppleIcons.Forward, stringResource(R.string.action_next), 36.dp, 56.dp, onClick = remote::next)
            GlyphButton(
                if (state.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.action_repeat),
                20.dp, 44.dp, tint = if (state.repeat != RepeatMode.None) Primary else dim,
            ) { remote.cycleRepeat() }
        }
        return
    }
    // iPhone: centros separados 107 pt; anterior/siguiente de 44 pt de ancho, pausa de 44 de alto.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(AppleIcons.Backward, stringResource(R.string.action_previous), 46.dp, squash = SkipSquash, onClick = remote::previous)
        PlayPause(remote, state, 46.dp)
        GlyphButton(AppleIcons.Forward, stringResource(R.string.action_next), 46.dp, squash = SkipSquash, onClick = remote::next)
    }
}

@Composable
private fun PlayPause(remote: CiderRemote, state: PlayerState, size: Dp, touch: Dp = 80.dp) = GlyphButton(
    if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
    stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), size, touch, onClick = remote::togglePlay,
)

/** Anterior/siguiente de iOS 26: el glifo, a 46 dp, sale 42 × 29 dp; Apple lo dibuja 38 × 22. */
private val SkipSquash = Offset(0.91f, 0.76f)

@Composable
private fun GlyphButton(icon: ImageVector, description: String, size: Dp, touch: Dp = 80.dp, tint: Color = Primary, squash: Offset? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(touch).clip(CircleShape).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size).then(if (squash != null) Modifier.graphicsLayer { scaleX = squash.x; scaleY = squash.y } else Modifier))
    }
}

/** Volumen de Cider, como la barra de volumen de Apple Music en iPhone. */
@Composable
private fun VolumeRow(remote: CiderRemote, state: PlayerState) {
    // Medido contra Apple Music (iOS 26): altavoz pequeño 4 dp más afuera, barra desde 24 dp del borde
    // del margen, altavoz grande algo menor y toda la fila 2,7 dp más abajo.
    val tint = LocalSheetTint.current
    Row(Modifier.fillMaxWidth().offset(y = 2.7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeMute, null, tint = tint.icon, modifier = Modifier.size(20.dp).offset(x = (-3.7).dp).plusBlend())
        ThinSlider(
            value = state.volume.toFloat(),
            onValueChange = { remote.setVolume(it.toDouble()) },
            activeColor = tint.fill,
            inactiveColor = tint.track,
            description = stringResource(R.string.volume_description, (state.volume * 100).roundToInt()),
            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 13.dp).plusBlend(),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = tint.icon, modifier = Modifier.size(19.dp).plusBlend())
    }
}

/**
 * Dónde suena la música (el botón de AirPlay de Apple Music): el icono del equipo.
 * Toca para ver su nombre y los graves de la bocina.
 */
@Composable
private fun OutputButton(remote: CiderRemote) {
    var open by remember { mutableStateOf(false) }
    // Nombre genérico del equipo; "Mac" si Cider corre en macOS.
    val platform by produceState<String?>(null, remote) {
        value = runCatching { remote.api.clientInfo().platform }.getOrNull()
    }
    val deviceName = stringResource(if (platform == "darwin") R.string.device_mac else R.string.device_computer)
    Box {
        ModeButton(Icons.Rounded.Laptop, stringResource(R.string.device_description, deviceName), selected = false) { open = true }
        cider.remote.app.ui.ForceDark {
            // Centrado sobre el botón (44 dp): si no, arranca en su borde izquierdo y Android lo empuja contra el
            // borde derecho de la pantalla.
            cider.remote.app.ui.components.GlassMenu(open, onDismiss = { open = false }, width = 280.dp, offset = androidx.compose.ui.unit.DpOffset((-118).dp, 0.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Laptop, null, tint = Apple.colors.accent, modifier = Modifier.size(22.dp))
                        Text(deviceName, style = Apple.type.headline, color = Apple.colors.label, modifier = Modifier.padding(start = 10.dp))
                    }
                    SpeakerBassRow(remote, open)
                    AudioSettingsRows(remote, open)
                }
            }
        }
    }
}

/**
 * Ajustes de audio de Cider (rutas `/audio/…`): crossfade con su duración, Dolby Atmos y modo de escucha.
 * Se leen al abrir el menú y se cambian al momento; si Cider falla, se vuelven a leer.
 */
@Composable
private fun AudioSettingsRows(remote: CiderRemote, visible: Boolean) {
    val scope = rememberCoroutineScope()
    var crossfade by remember { mutableStateOf<Boolean?>(null) }
    var seconds by remember { mutableStateOf(5f) }
    var atmos by remember { mutableStateOf<Boolean?>(null) }
    // Modo de Atmos del dispositivo antes de apagarlo ("binaural" en un PC con salida estéreo).
    var atmosMode by remember { mutableStateOf("binaural") }
    var mode by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        runCatching { remote.api.audioSetting("crossfade") }.getOrNull()?.let { c ->
            crossfade = c["enabled"]?.jsonPrimitive?.content == "true"
            c["durationSec"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { seconds = it }
        }
        // Lo que decide si suena Atmos es la preferencia del dispositivo (`devicePreferences.default`:
        // off | passthrough | standard | binaural), no solo `enabled`.
        runCatching { remote.api.audioSetting("atmos") }.getOrNull().let { a ->
            if (a == null) { atmos = null; return@let }
            val pref = (a["devicePreferences"] as? kotlinx.serialization.json.JsonObject)?.get("default")?.jsonPrimitive?.content
            if (pref != null && pref != "off") atmosMode = pref
            atmos = a["enabled"]?.jsonPrimitive?.content == "true" && pref != "off"
        }
        mode = runCatching { remote.api.audioSetting("listening-mode") }.getOrNull()
            ?.takeIf { it["available"]?.jsonPrimitive?.content != "false" }?.get("mode")?.jsonPrimitive?.content
    }
    LaunchedEffect(visible) { if (visible) load() }
    fun patch(name: String, body: kotlinx.serialization.json.JsonObject) {
        scope.launch { runCatching { remote.api.setAudioSetting(name, body) }.onFailure { load() } }
    }
    crossfade?.let { on ->
        SettingSwitch(stringResource(R.string.audio_crossfade), if (on) stringResource(R.string.audio_seconds, seconds.roundToInt()) else null, on) {
            crossfade = it
            patch("crossfade", kotlinx.serialization.json.buildJsonObject { put("enabled", it) })
        }
        // Barra fina como la de volumen (iOS), de 1 a 12 s en pasos de un segundo.
        if (on) ThinSlider(
            value = (seconds - 1f) / 11f,
            onValueChange = { seconds = (1f + it * 11f).roundToInt().toFloat() },
            onValueChangeFinished = { patch("crossfade", kotlinx.serialization.json.buildJsonObject { put("durationSec", seconds.roundToInt()) }) },
            activeColor = Apple.colors.accent,
            inactiveColor = Apple.colors.label.copy(alpha = 0.18f),
            description = stringResource(R.string.audio_seconds, seconds.roundToInt()),
        )
    }
    atmos?.let { on ->
        SettingSwitch(stringResource(R.string.audio_atmos), null, on) {
            atmos = it
            patch("atmos", kotlinx.serialization.json.buildJsonObject {
                if (it) put("enabled", true)
                put("devicePreferences", kotlinx.serialization.json.buildJsonObject { put("default", if (it) atmosMode else "off") })
            })
        }
    }
    mode?.let { current ->
        Text(stringResource(R.string.audio_listening_mode), style = Apple.type.body, color = Apple.colors.label, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in listOf("off" to R.string.audio_mode_off, "game" to R.string.audio_mode_game, "antifatigue" to R.string.audio_mode_antifatigue)) {
                val selected = current == value
                val text = stringResource(label)
                Box(
                    // Ancho según el texto ("Anti-fatigue" no cabe en un tercio).
                    Modifier.weight(text.length + 6f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Apple.colors.accent else Apple.colors.fill)
                        .combinedClickableCompat(onClick = {
                            mode = value
                            patch("listening-mode", kotlinx.serialization.json.buildJsonObject { put("mode", value) })
                        })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = Apple.type.footnote, color = if (selected) Color.White else Apple.colors.label, maxLines = 1)
                }
            }
        }
    }
}

/** Fila con título, subtítulo opcional e interruptor (menú del equipo). */
@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // Filas de ~52 dp como las celdas de iOS (antes iban pegadas, 8 dp entre una y otra).
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Apple.type.body, color = Apple.colors.label)
            subtitle?.let { Text(it, style = Apple.type.footnote, color = Apple.colors.secondaryLabel) }
        }
        cider.remote.app.ui.liquid.LiquidToggle(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Modo bocina de EasyEffects (graves), a través del servicio auxiliar del PC.
 * Solo aparece si el servicio responde y los presets "Bocina" y "Normal" existen.
 */
@Composable
private fun SpeakerBassRow(remote: CiderRemote, visible: Boolean) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<cider.remote.SpeakerMode?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) mode = remote.api.speakerMode() }
    val current = mode?.takeIf { it.available } ?: return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.speaker_bass), style = Apple.type.body, color = Apple.colors.label)
            Text(
                if (busy) stringResource(R.string.speaker_changing) else stringResource(R.string.speaker_preset, current.preset.ifBlank { "—" }),
                style = Apple.type.footnote, color = Apple.colors.secondaryLabel,
            )
        }
        cider.remote.app.ui.liquid.LiquidToggle(
            checked = current.bass,
            enabled = !busy,
            onCheckedChange = { on ->
                busy = true
                mode = current.copy(bass = on)
                scope.launch {
                    mode = runCatching { remote.api.setSpeakerBass(on) }.getOrElse { current }
                    busy = false
                }
            },
        )
    }
}

/** Abajo (iPhone): letra · dispositivo · cola, con los centros separados 118 pt. */
@Composable
private fun BottomBar(remote: CiderRemote, state: PlayerState, mode: SheetMode, onMode: (SheetMode) -> Unit, modifier: Modifier = Modifier) {
    // Centros a 111 dp (iOS 26; antes 118).
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(63.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        ModeButton(AppleIcons.Lyrics, stringResource(R.string.action_lyrics), mode == SheetMode.Lyrics) {
            onMode(if (mode == SheetMode.Lyrics) SheetMode.Artwork else SheetMode.Lyrics)
        }
        OutputButton(remote)
        Box {
            ModeButton(Icons.AutoMirrored.Rounded.FormatListBulleted, stringResource(R.string.up_next), mode == SheetMode.Queue) {
                onMode(if (mode == SheetMode.Queue) SheetMode.Artwork else SheetMode.Queue)
            }
            RepeatShuffleBadge(state, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Letra y cola juntas (iPad, abajo a la derecha). */
@Composable
private fun ModeButtons(mode: SheetMode, onMode: (SheetMode) -> Unit, spacing: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        ModeButton(AppleIcons.Lyrics, stringResource(R.string.action_lyrics), mode == SheetMode.Lyrics) {
            onMode(if (mode == SheetMode.Lyrics) SheetMode.Artwork else SheetMode.Lyrics)
        }
        ModeButton(Icons.AutoMirrored.Rounded.FormatListBulleted, stringResource(R.string.up_next), mode == SheetMode.Queue) {
            onMode(if (mode == SheetMode.Queue) SheetMode.Artwork else SheetMode.Queue)
        }
    }
}

/** Insignia sobre el icono de la cola cuando hay repetición o aleatorio (iOS 26). */
@Composable
private fun RepeatShuffleBadge(state: PlayerState, modifier: Modifier) {
    val icon = when {
        state.repeat == RepeatMode.One -> Icons.Rounded.RepeatOne
        state.repeat == RepeatMode.All -> Icons.Rounded.Repeat
        state.shuffle -> Icons.Rounded.Shuffle
        else -> return
    }
    Box(
        modifier.offset(x = 6.dp, y = (-4).dp).size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(13.dp))
    }
}

/** Activo: cuadro claro de 34 dp con el glifo oscuro (como Apple Music). */
@Composable
private fun ModeButton(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color.White.copy(alpha = 0.85f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) Color.Black.copy(alpha = 0.7f) else LocalSheetTint.current.icon,
                modifier = Modifier.size(24.dp).then(if (selected) Modifier else Modifier.plusBlend()))
        }
    }
}

/**
 * Vuelo de la carátula entre la tarjeta grande (modo carátula) y la miniatura de la cabecera de letra y cola,
 * con el movimiento de abrir la hoja (SheetFly): tamaño y borde de arriba en línea recta, centro adelantado
 * (1 − (1 − t)², trayectoria curva) y la curva del toque (450 ms). Antes era una transición compartida de
 * Compose, que mueve los cuatro bordes en línea recta: la carátula parecía crecer desde la esquina izquierda,
 * y al ir a la letra ni se veía. [progress]: 0 = tarjeta grande, 1 = cabecera. Los rectángulos se guardan al
 * colocarse (la cabecera está siempre en el mismo sitio) y siguen sirviendo cuando su modo ya no se compone.
 */
@androidx.compose.runtime.Stable
class ArtSwap(initial: Float) {
    val progress = androidx.compose.animation.core.Animatable(initial)
    var active by mutableStateOf(false)
        private set
    var root: androidx.compose.ui.layout.LayoutCoordinates? = null
    var big by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set
    var small by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set
    /** La miniatura en coordenadas de la raíz de la hoja (las de SheetFly), para el vuelo de la portada animada. */
    var smallInSheet by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        private set
    var sheetRoot: SheetRoot? = null
    /** Con portada animada vuela el propio vídeo (SheetFly) y no hay tarjeta grande que medir. */
    var video = false
    /** Escala de pausa de la tarjeta grande (se encoge al 78 %), leída al dibujar. */
    var bigScale: () -> Float = { 1f }

    private fun boundsOf(c: androidx.compose.ui.layout.LayoutCoordinates): androidx.compose.ui.geometry.Rect? {
        val r = root ?: return null
        if (!r.isAttached || !c.isAttached) return null
        return r.localBoundingBoxOf(c, clipBounds = false).takeIf { it.width > 0f }
    }
    fun measureBig(c: androidx.compose.ui.layout.LayoutCoordinates) { boundsOf(c)?.let { if (it != big) big = it } }
    fun measureSmall(c: androidx.compose.ui.layout.LayoutCoordinates) {
        boundsOf(c)?.let { if (it != small) small = it }
        val r = sheetRoot?.coordinates
        if (r != null && r.isAttached && c.isAttached) r.localBoundingBoxOf(c, clipBounds = false).takeIf { it.width > 0f }?.let { if (it != smallInSheet) smallInSheet = it }
    }

    /**
     * Curva simétrica (f(1 − t) = 1 − f(t)): la ida es la vuelta al revés. Con la curva del toque de la hoja
     * (exponencial, casi todo el movimiento al principio) la vuelta parecía mucho más rápida que la ida: la
     * portada animada recupera todo el ancho al 63 % del recorrido, que al volver llegaba en ~90 ms y al ir
     * dejaba el encogerse para la cola lenta de la curva.
     */
    private val Curve = androidx.compose.animation.core.tween<Float>(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 0f, 0.67f, 1f))

    suspend fun go(target: Float) {
        if (progress.value == target && !progress.isRunning) return
        active = true
        try {
            // El modo nuevo se coloca en el siguiente fotograma: se espera a conocer los dos extremos (máx. ~100 ms).
            kotlinx.coroutines.withTimeoutOrNull(100) {
                androidx.compose.runtime.snapshotFlow { small != null && smallInSheet != null && (video || big != null) }.first { ready -> ready }
            }
            progress.animateTo(target, Curve)
        } finally {
            progress.snapTo(target)
            active = false
        }
    }
}

@Composable
private fun ArtSwapOverlay(swap: ArtSwap, artwork: Artwork?) {
    if (!swap.active) return
    val big = swap.big ?: return
    val small = swap.small ?: big
    val density = LocalDensity.current
    val bigCorner = with(density) { 10.dp.toPx() }
    val smallCorner = with(density) { 6.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset(big.left.roundToInt(), big.top.roundToInt()) }
            .size(with(density) { big.width.toDp() }, with(density) { big.height.toDp() })
            .graphicsLayer {
                // Tarjeta grande escalada por la pausa alrededor de su centro.
                val k = swap.bigScale()
                val bw = big.width * k
                val bx = big.center.x
                val by = big.center.y - big.height * k / 2f
                val t = 1f - swap.progress.value.coerceIn(0f, 1f)
                val w = androidx.compose.ui.util.lerp(small.width, bw, t)
                val top = androidx.compose.ui.util.lerp(small.top, by, t)
                val cx = androidx.compose.ui.util.lerp(small.center.x, bx, 1f - (1f - t) * (1f - t))
                val sc = (w / big.width).coerceAtLeast(0.01f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                scaleX = sc; scaleY = sc
                translationX = cx - w / 2f - big.left
                translationY = top - big.top
                clip = true
                shape = RoundedCornerShape(androidx.compose.ui.util.lerp(smallCorner, bigCorner, t) / sc)
            },
    ) {
        AsyncImage(model = artwork?.sized(SheetArtworkPx), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
