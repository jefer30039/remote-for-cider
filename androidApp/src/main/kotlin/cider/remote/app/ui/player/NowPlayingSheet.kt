package cider.remote.app.ui.player

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import cider.remote.app.ui.components.rememberArtworkPalette
import kotlinx.coroutines.delay
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Colores de la hoja: blanco sobre el color de la carátula. */
private val Primary = Color.White
private val Secondary = Color.White.copy(alpha = 0.62f)
private val TimeColor = Color.White.copy(alpha = 0.45f)
private val Tertiary = Color.White.copy(alpha = 0.25f)
private val Chip = Color.White.copy(alpha = 0.16f)

enum class SheetMode { Artwork, Lyrics, Queue }

private fun Color.lighter(f: Float) = Color((red * f).coerceAtMost(1f), (green * f).coerceAtMost(1f), (blue * f).coerceAtMost(1f), alpha)

/**
 * Fondo de la hoja: color de la carátula, más luminoso que el de las páginas
 * (Apple Music: #7A1C10 para "Obsessed"); blanco al 62 % encima ≥ 4.5:1.
 */
fun sheetBackground(accent: Color?): Color = (accent ?: Color(0xFF2C2C2E)).asBackground(targetLuminance = 0.045f, saturationBoost = 1.15f)

@Composable
fun NowPlayingSheet(remote: CiderRemote, state: PlayerState, sheet: SheetState, initialMode: SheetMode = SheetMode.Artwork) {
    var mode by remember { mutableStateOf(initialMode) }
    val wide = LocalWindowLayout.current.expanded
    // Color de fondo: el dominante de la paleta calibrada con la app oficial (brillo × 0,7); hasta que
    // se calcula, el de `bgColor` oscurecido.
    val palette = rememberArtworkPalette(state.track?.artwork)
    val background by animateColorAsState(palette?.firstOrNull() ?: sheetBackground(rememberArtworkColor(state.track?.artwork)), tween(700), label = "fondo")

    BackHandler(enabled = sheet.isOpen) { if (mode != SheetMode.Artwork) mode = SheetMode.Artwork else sheet.close() }
    LightStatusBarIcons()

    val dismissDrag = Modifier.sheetDrag(sheet)
    val corner = if (sheet.position.value > 0f) 24.dp else 0.dp
    // Portada animada (ya consultada de antemano por PrefetchNowPlaying): la hoja abre con su forma final.
    val catalogId = state.track?.catalogId
    val cover by produceState(AnimatedCovers.peek(catalogId), catalogId) {
        value = AnimatedCovers.peek(catalogId)
        value = AnimatedCovers.load(remote, catalogId)
    }
    val video = if (wide) null else cover?.tall

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = sheet.position.value * size.height }
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(background),
    ) {
        // Fondo: campo de colores de la carátula con animación lenta (Apple Music), también en tablet;
        // con letra o cola, más oscuro. Con portada animada a la vista, Apple no anima el fondo: queda el
        // color plano de la carátula (el degradado de la caja), y el vídeo se funde en él.
        if (video == null || mode != SheetMode.Artwork) {
            ColorField(palette, background, dim = if (mode != SheetMode.Artwork) (if (wide) 0.25f else 0.38f) else 0f)
        }
        // Tablet: la portada animada cuadrada dentro de la tarjeta (iPad); el fondo sigue siendo el campo de color.
        if (wide) TabletSheet(remote, state, mode, { mode = it }, dismissDrag, cover?.square)
        else PhoneSheet(remote, state, mode, { mode = it }, background, dismissDrag, video)
        // Asa para indicar que se puede deslizar hacia abajo.
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp)
                .size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.45f)),
        )
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
) {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        // Portada animada: 3:4 a todo el ancho, detrás de todo y fundida con el fondo.
        AnimatedVisibility(mode == SheetMode.Artwork && video != null, enter = fadeIn(), exit = fadeOut()) {
            HeroArtwork(state.track?.artwork, video, background, width * 4 / 3, dismissDrag)
        }
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(mode, Modifier.weight(1f).fillMaxWidth(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "modo") { m ->
                when (m) {
                    SheetMode.Artwork -> Column(Modifier.fillMaxSize()) {
                        // Sin vídeo, tarjeta centrada a 24 dp de los lados y 44 dp sobre el título.
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(dismissDrag).statusBarsPadding()) {
                            if (video == null) {
                                val side = minOf(width - 48.dp, maxHeight - 44.dp - 34.dp)
                                // Como iOS: la carátula se encoge al pausar y vuelve con un muelle al reproducir.
                                val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.78f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "pausa")
                                ArtworkImage(
                                    state.track?.artwork, side, px = SheetArtworkPx, preview = PreviewArtworkPx, shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp)
                                        .graphicsLayer { scaleX = scale; scaleY = scale }
                                        .shadow(24.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color.Black),
                                )
                            }
                        }
                        Box(Modifier.padding(start = 33.dp, end = 22.dp)) { TitleRow(remote, state) }
                    }
                    SheetMode.Lyrics, SheetMode.Queue -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        CompactHeader(remote, state, dismissDrag)
                        Box(Modifier.weight(1f)) {
                            if (m == SheetMode.Lyrics) LyricsView(remote, state, Modifier.fillMaxSize())
                            else QueuePanel(remote, state, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 33.dp)) {
                Spacer(Modifier.height(8.dp))
                Progress(remote, state)
                Spacer(Modifier.height(10.dp))
                Transport(remote, state)
                Spacer(Modifier.height(25.dp))
                VolumeRow(remote, state)
                BottomBar(remote, state, mode, onMode, Modifier.fillMaxWidth())
                Spacer(Modifier.height(max(nav, 16.dp) + 25.dp))
            }
        }
    }
}

/**
 * Tablet (Apple Music en iPad): columna con la carátula y los controles; sola y
 * centrada en modo carátula, a la izquierda con la letra o la cola a la derecha.
 * Abajo, el dispositivo a la izquierda y letra/cola a la derecha.
 */
@Composable
private fun TabletSheet(remote: CiderRemote, state: PlayerState, mode: SheetMode, onMode: (SheetMode) -> Unit, dismissDrag: Modifier, squareVideo: String?) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val w = maxWidth
        val h = maxHeight
        val panel = mode != SheetMode.Artwork
        // Vertical (iPad en vertical): carátula grande (72 % del ancho) y la columna centrada en el
        // alto, encima de los botones de abajo; antes quedaba limitada al 40 % del ancho, pegada arriba
        // y con media pantalla vacía. Con letra o cola, la disposición de horizontal.
        val portrait = h > w
        // Proporciones del iPad en horizontal: carátula 0,54 del alto, columna centrada en el 23 % del ancho.
        val landscapeArt = minOf(h * 0.54f, h - 340.dp, w * 0.4f).coerceAtLeast(160.dp)
        val art by animateDpAsState(
            if (portrait && !panel) minOf(w * 0.72f, h - 460.dp).coerceAtLeast(160.dp) else landscapeArt, label = "caratula",
        )
        val center by animateDpAsState(if (panel) w * 0.23f else w / 2, label = "columna")
        // Alto aproximado de lo que va bajo la carátula (título, progreso, controles, volumen).
        val below = 250.dp
        val topPad by animateDpAsState(
            if (portrait && !panel) ((h - 64.dp - art - below) / 2).coerceAtLeast(24.dp) else h * 0.07f, label = "arriba",
        )
        Column(
            Modifier.width(art).offset { IntOffset((center - art / 2).roundToPx(), 0) }.padding(top = topPad),
        ) {
            Box(Modifier.then(dismissDrag)) {
                val scale by animateFloatAsState(if (state.isPlaying) 1f else 0.78f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "pausa")
                val cardShape = RoundedCornerShape(10.dp)
                val artPx = with(androidx.compose.ui.platform.LocalDensity.current) { art.roundToPx() }
                // Portada animada cuadrada (la de la tarjeta del iPad), con la carátula fija debajo hasta
                // que el vídeo pinta; se reproduce a la resolución de la tarjeta, sin sonido.
                AnimatedCover(
                    squareVideo,
                    Modifier.size(art).graphicsLayer { scaleX = scale; scaleY = scale }
                        .shadow(24.dp, cardShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(cardShape),
                    maxVideoSize = androidx.compose.ui.unit.IntSize(artPx, artPx),
                ) {
                    ArtworkImage(state.track?.artwork, art, px = WideSheetArtworkPx, preview = PreviewArtworkPx, shape = cardShape)
                }
            }
            Spacer(Modifier.height(16.dp))
            TitleRow(remote, state, compact = true)
            Spacer(Modifier.height(4.dp))
            Progress(remote, state)
            Transport(remote, state, compact = true)
            VolumeRow(remote, state)
        }
        AnimatedVisibility(
            panel, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).width(w * 0.5f).fillMaxHeight().padding(top = 24.dp, bottom = 64.dp, end = 24.dp),
        ) {
            when (mode) {
                SheetMode.Lyrics -> LyricsView(remote, state, Modifier.fillMaxSize())
                SheetMode.Queue -> QueuePanel(remote, state, Modifier.fillMaxSize())
                SheetMode.Artwork -> Unit
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset { IntOffset((center - art / 2 - 12.dp).roundToPx(), 0) }.padding(bottom = 8.dp)) {
            OutputButton(remote)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 8.dp)) {
            ModeButtons(mode, onMode, spacing = 0.dp)
        }
    }
}

/** Carátula (o portada animada) a todo el ancho que se funde con el fondo, como Apple Music en Android. */
@Composable
private fun HeroArtwork(artwork: Artwork?, video: String?, background: Color, height: androidx.compose.ui.unit.Dp, dragModifier: Modifier) {
    // El borde inferior se funde a transparente (máscara DstIn) para que debajo asome el campo de
    // colores animado, como en Apple Music; antes se fundía a un color plano.
    Box(
        Modifier.fillMaxWidth().height(height).then(dragModifier)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            },
    ) {
        AnimatedCover(video, Modifier.fillMaxSize()) {
            AsyncImage(
                model = artwork?.sized(PreviewArtworkPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = artwork?.sized(HeroArtworkPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CompactHeader(remote: CiderRemote, state: PlayerState, dragModifier: Modifier) {
    val track = state.track
    Row(
        Modifier.fillMaxWidth().then(dragModifier).padding(start = 28.dp, end = 18.dp, top = 30.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (toAlbum, toArtist) = rememberSongNavigation(remote, state)
        ArtworkImage(track?.artwork, 70.dp, shape = RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            TitleText(state, Apple.type.headline.copy(fontSize = 18.sp), Modifier.combinedClickableCompat(onClick = toAlbum))
            Text(track?.artistName.orEmpty(), style = Apple.type.body, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickableCompat(onClick = toArtist))
        }
        MoreMenu(remote, state)
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
            color = Primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (track?.isExplicit == true) {
            val description = stringResource(R.string.explicit)
            Box(
                Modifier.padding(start = 7.dp).size(15.dp).clip(RoundedCornerShape(3.dp)).background(Secondary)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text("E", style = Apple.type.caption.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold), color = Color.Black.copy(alpha = 0.75f))
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
            TitleText(state, Apple.type.title2.copy(fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.SemiBold), Modifier.combinedClickableCompat(onClick = toAlbum))
            Text(
                state.track?.artistName.orEmpty(), style = Apple.type.title3.copy(fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.Normal),
                color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickableCompat(onClick = toArtist),
            )
        }
        MoreMenu(remote, state)
    }
}

/** "…": favorita, biblioteca y aleatorio (Apple Music deja solo este botón junto al título). */
@Composable
private fun MoreMenu(remote: CiderRemote, state: PlayerState) {
    var menu by remember { mutableStateOf(false) }
    Box {
        GlassCircle(Icons.Filled.MoreHoriz, stringResource(R.string.action_more)) { menu = true }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            val loved = state.inFavorites == true
            DropdownMenuItem(
                text = { Text(stringResource(if (loved) R.string.action_unfavorite else R.string.action_favorite)) },
                leadingIcon = { Icon(if (loved) Icons.Filled.Star else Icons.Filled.StarBorder, null) },
                onClick = { menu = false; remote.toggleLove() },
            )
            when (state.inLibrary) {
                true -> DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_from_library)) }, onClick = { menu = false; remote.toggleInLibrary() })
                false -> DropdownMenuItem(text = { Text(stringResource(R.string.action_add_to_library)) }, onClick = { menu = false; remote.toggleInLibrary() })
                null -> Unit
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (state.shuffle) R.string.action_shuffle_off else R.string.action_shuffle_on)) },
                onClick = { menu = false; remote.toggleShuffle() },
            )
        }
    }
}

/** Círculo translúcido de 30 dp (Apple Music) con área táctil de 44 dp. */
@Composable
private fun GlassCircle(icon: ImageVector, description: String, tint: Color = Primary, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
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
    val quality by produceState<AudioQuality?>(null, state.track?.playParams?.id) {
        value = runCatching { remote.api.audioQuality() }.getOrNull()
    }
    val duration = progress.durationSec.coerceAtLeast(0.001)
    val shown = dragging?.let { it * duration } ?: position
    ThinSlider(
        value = dragging ?: (position / duration).toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { remote.seek(it * duration) }; dragging = null },
        activeColor = Color.White.copy(alpha = 0.72f),
        inactiveColor = Color.White.copy(alpha = 0.2f),
        description = stringResource(R.string.playback_position, formatTime(shown), formatTime(duration)),
    )
    Box(Modifier.fillMaxWidth()) {
        val timeStyle = Apple.type.caption.copy(fontWeight = FontWeight.SemiBold).tabular()
        Text(formatTime(shown), style = timeStyle, color = TimeColor, modifier = Modifier.align(Alignment.CenterStart))
        quality?.badge?.let { badge ->
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(5.dp)).background(Chip)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.GraphicEq, null, tint = Secondary, modifier = Modifier.size(13.dp))
                Text(badge, style = Apple.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Secondary, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text("-" + formatTime(duration - shown), style = timeStyle, color = TimeColor, modifier = Modifier.align(Alignment.CenterEnd))
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
        horizontalArrangement = Arrangement.spacedBy(27.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(AppleIcons.Backward, stringResource(R.string.action_previous), 46.dp, onClick = remote::previous)
        PlayPause(remote, state, 46.dp)
        GlyphButton(AppleIcons.Forward, stringResource(R.string.action_next), 46.dp, onClick = remote::next)
    }
}

@Composable
private fun PlayPause(remote: CiderRemote, state: PlayerState, size: Dp, touch: Dp = 80.dp) = GlyphButton(
    if (state.isPlaying) AppleIcons.Pause else AppleIcons.Play,
    stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play), size, touch, onClick = remote::togglePlay,
)

@Composable
private fun GlyphButton(icon: ImageVector, description: String, size: Dp, touch: Dp = 80.dp, tint: Color = Primary, onClick: () -> Unit) {
    Box(
        Modifier.size(touch).clip(CircleShape).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Volumen de Cider, como la barra de volumen de Apple Music en iPhone. */
@Composable
private fun VolumeRow(remote: CiderRemote, state: PlayerState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeMute, null, tint = Secondary, modifier = Modifier.size(20.dp))
        ThinSlider(
            value = state.volume.toFloat(),
            onValueChange = { remote.setVolume(it.toDouble()) },
            activeColor = Color.White.copy(alpha = 0.72f),
            inactiveColor = Color.White.copy(alpha = 0.2f),
            description = stringResource(R.string.volume_description, (state.volume * 100).roundToInt()),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Secondary, modifier = Modifier.size(22.dp))
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
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Column(Modifier.width(300.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Laptop, null, tint = Apple.colors.accent, modifier = Modifier.size(22.dp))
                    Text(deviceName, style = Apple.type.headline, color = Apple.colors.label, modifier = Modifier.padding(start = 10.dp))
                }
                SpeakerBassRow(remote, open)
            }
        }
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
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.speaker_bass), style = Apple.type.body, color = Apple.colors.label)
            Text(
                if (busy) stringResource(R.string.speaker_changing) else stringResource(R.string.speaker_preset, current.preset.ifBlank { "—" }),
                style = Apple.type.footnote, color = Apple.colors.secondaryLabel,
            )
        }
        androidx.compose.material3.Switch(
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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(70.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
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
            Icon(icon, null, tint = if (selected) Color.Black.copy(alpha = 0.7f) else Secondary, modifier = Modifier.size(24.dp))
        }
    }
}
