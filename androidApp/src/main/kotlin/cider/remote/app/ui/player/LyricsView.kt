package cider.remote.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cider.remote.CiderRemote
import cider.remote.LyricLine
import cider.remote.Lyrics
import cider.remote.PlayerState
import cider.remote.app.R
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LocalWindowLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object None : LyricsLoad
    data class Ready(val lyrics: Lyrics) : LyricsLoad
}

@Composable
fun LyricsView(
    remote: CiderRemote,
    state: PlayerState,
    modifier: Modifier = Modifier,
    /** La parte de arriba de la línea activa queda a esta distancia del borde de la vista (Apple Music: justo bajo la cabecera). */
    anchorTop: androidx.compose.ui.unit.Dp? = 48.dp,
) {
    // Las canciones de la biblioteca traen un id `i.…`; las letras van por el del catálogo.
    val catalogId = state.track?.catalogId
    val load by produceState<LyricsLoad>(LyricsLoad.Loading, catalogId) {
        value = LyricsLoad.Loading
        value = if (catalogId == null) LyricsLoad.None else {
            // Por sílaba si Apple la tiene (karaoke); si no, por línea.
            (runCatching { remote.api.lyrics(catalogId, syllable = true) }.getOrNull()
                ?: runCatching { remote.api.lyrics(catalogId) }.getOrNull())
                ?.let { LyricsLoad.Ready(it) } ?: LyricsLoad.None
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val l = load) {
            LyricsLoad.Loading -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
            LyricsLoad.None -> Text(stringResource(R.string.lyrics_none), style = Apple.type.headline, color = Color.White.copy(alpha = 0.62f))
            is LyricsLoad.Ready -> if (l.lyrics.synced) SyncedLyrics(remote, state, l.lyrics, anchorTop) else PlainLyrics(l.lyrics)
        }
    }
}

/** Letra sin tiempos: lista desplazable, todo en blanco. */
@Composable
private fun PlainLyrics(lyrics: Lyrics) {
    val style = lyricStyle()
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 240.dp), modifier = Modifier.fillMaxWidth()) {
        items(lyrics.lines) { line ->
            BasicText(line.text, style = style.copy(color = Color.White), modifier = Modifier.fillMaxWidth().padding(horizontal = SidePadding, vertical = 9.dp))
        }
    }
}

@Composable
private fun lyricStyle() = Apple.type.title1.copy(
    // Medido en Apple Music (teléfono, 521 ppp): mayúsculas de 24,6 dp → 34 sp, renglón de 40 dp.
    fontSize = if (LocalWindowLayout.current.expanded) 38.sp else 34.sp,
    lineHeight = if (LocalWindowLayout.current.expanded) 45.sp else 40.sp,
    fontWeight = FontWeight.Bold,
)

/** Margen izquierdo (Apple: 33 dp) y separación entre líneas (Apple: 34–36 dp). */
private val SidePadding = 33.dp
private val LineGap = 34.dp

/** La lista sube un poco antes de que empiece la línea (la nueva ya está en su sitio al cantarse). */
private const val Lead = 0.3

/**
 * Escala de las líneas que no suenan. En el vídeo de Cider, 82 %; en Apple Music todas miden
 * igual: un crecimiento sutil conserva la animación sin que las inactivas parezcan pequeñas.
 */
private const val InactiveScale = 0.92f

/** Parte aún no cantada de la línea activa, y la línea activa ya terminada. */
private const val Unsung = 0.45f

/** Altura de la línea activa (su centro) en la vista: como en el vídeo de referencia, algo por encima del centro. */
private const val Anchor = 0.38f

/** Borde suave del barrido de color de las sílabas. */
private val WipeEdge = 10.dp

/**
 * Letra sincronizada al estilo de Apple Music (y Cider): la línea activa se rellena de blanco
 * sílaba a sílaba con un borde suave; al cambiar de línea la lista sube con un muelle sin rebote,
 * la línea nueva crece del 82 % a su tamaño, la anterior se desvanece hacia arriba desenfocándose
 * y las siguientes van más tenues y desenfocadas cuanto más lejos están.
 *
 * Todo sale de una posición de foco continua: posiciones, escalas, opacidades y desenfoques se
 * calculan al colocar (sin recomponer) y el barrido se dibuja en cada fotograma solo en la línea
 * activa (sin recomponer). Se puede arrastrar para recorrer la letra; a los 3 s vuelve a seguirla.
 */
@Composable
private fun SyncedLyrics(remote: CiderRemote, state: PlayerState, lyrics: Lyrics, anchorTop: androidx.compose.ui.unit.Dp?) {
    val progress = state.progress
    // Reloj de la canción a cada fotograma mientras suena; parado, el último valor.
    val clock = remember { mutableDoubleStateOf(progress.positionNow()) }
    LaunchedEffect(progress) {
        clock.doubleValue = progress.positionNow()
        if (progress.playing) while (true) {
            withFrameNanos { }
            clock.doubleValue = progress.positionNow()
        }
    }
    // Solo cambia (y recompone) al pasar de línea.
    val active by remember(lyrics) { derivedStateOf { lyrics.indexAt(clock.doubleValue + Lead).coerceAtLeast(0) } }
    val focus = remember(lyrics) { Animatable(active.toFloat()) }
    LaunchedEffect(active) { focus.animateTo(active.toFloat(), spring(dampingRatio = 1f, stiffness = 110f)) }

    // Arrastre manual: congela el foco y desplaza; a los 3 s sin tocar vuelve a seguir la canción.
    val scope = rememberCoroutineScope()
    val manual = remember { Animatable(0f) }
    var userMode by remember { mutableStateOf(false) }
    val frozenFocus = remember { mutableFloatStateOf(0f) }
    var touches by remember { mutableIntStateOf(0) }
    val metrics = remember(lyrics, anchorTop) { LineMetrics(lyrics.lines.size, byTop = anchorTop != null) }
    val dragState = rememberDraggableState { delta -> scope.launch { manual.snapTo(manual.value + delta) } }
    LaunchedEffect(userMode, touches) {
        if (!userMode) return@LaunchedEffect
        delay(3000)
        // Mismo sitio visual con el foco de ahora, y vuelta suave a la línea que suena.
        manual.snapTo(manual.value + metrics.anchorAt(focus.value) - metrics.anchorAt(frozenFocus.floatValue))
        userMode = false
        manual.animateTo(0f, spring(dampingRatio = 1f, stiffness = 110f))
    }

    val style = lyricStyle()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { LineGap.toPx() }
    val sidePx = with(density) { SidePadding.roundToPx() }
    val blurUnit = with(density) { 1.4.dp.toPx() }
    val anchorTopPx = anchorTop?.let { with(density) { it.toPx() } }

    Layout(
        content = {
            lyrics.lines.forEachIndexed { i, line ->
                Box(
                    Modifier.clickable(interactionSource = null, indication = null) { remote.seek(line.startSec) },
                ) {
                    LyricText(line, active = i == active, clock = clock, style = style)
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clipToBounds()
            .draggable(
                dragState, Orientation.Vertical,
                onDragStarted = {
                    if (!userMode) { frozenFocus.floatValue = focus.value; userMode = true }
                    touches++
                },
                onDragStopped = { touches++ },
            ),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val lineConstraints = Constraints(maxWidth = (width - 2 * sidePx).coerceAtLeast(0))
        val placeables = measurables.map { it.measure(lineConstraints) }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.sumOf { it.height }
        layout(width, height) {
            val user = userMode
            val f = if (user) frozenFocus.floatValue else focus.value
            // Alturas con su escala de ahora: la línea que crece empuja a las de abajo.
            var y = 0f
            for (i in placeables.indices) {
                val s = scaleFor(i - f)
                metrics.top[i] = y
                metrics.height[i] = placeables[i].height * s
                y += (placeables[i].height + gapPx) * s
            }
            // Teléfono: la parte de arriba de la activa a distancia fija del borde (no se mueve si la
            // zona crece al ocultarse los controles); tablet: su centro al 38 % del alto.
            val offset = (anchorTopPx ?: (height * Anchor)) - metrics.anchorAt(f) + manual.value
            for (i in placeables.indices) {
                val top = metrics.top[i] + offset
                // Fuera de la vista no se coloca (ni se dibuja).
                if (top > height || top + metrics.height[i] < 0f) continue
                val d = i - f
                placeables[i].placeWithLayer(sidePx, top.toInt()) {
                    val s = scaleFor(d)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = if (user) (if (abs(d) < 0.5f) 1f else 0.5f) else alphaFor(d)
                    val blur = if (user) 0f else blurFor(d) * blurUnit
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                }
            }
        }
    }
}

/** Posiciones de la última colocación (para volver del arrastre sin salto). */
private class LineMetrics(n: Int, private val byTop: Boolean) {
    val top = FloatArray(n)
    val height = FloatArray(n)

    /** Punto de anclaje (arriba o centro) de la "línea" [f] (fraccionaria: entre dos líneas, interpolado). */
    fun anchorAt(f: Float): Float {
        if (top.isEmpty()) return 0f
        val i = floor(f).toInt().coerceIn(0, top.size - 1)
        val j = (i + 1).coerceAtMost(top.size - 1)
        val a = top[i] + if (byTop) 0f else height[i] / 2
        val b = top[j] + if (byTop) 0f else height[j] / 2
        return a + (b - a) * (f - i).coerceIn(0f, 1f)
    }
}

private fun scaleFor(d: Float): Float = 1f - (1f - InactiveScale) * abs(d).coerceAtMost(1f)

/** Activa entera; las pasadas se desvanecen hasta desaparecer; las siguientes, más tenues con la distancia. */
private fun alphaFor(d: Float): Float = when {
    d <= -1f -> 0f
    d < 0f -> 1f + d
    d < 1f -> 1f - (1f - 0.36f) * d
    d < 2f -> 0.36f - 0.10f * (d - 1f)
    d < 3f -> 0.26f - 0.08f * (d - 2f)
    else -> 0.18f
}

/** Desenfoque (en unidades de 1,4 dp): fuerte en la que se va, creciente en las siguientes. */
private fun blurFor(d: Float): Float = when {
    d < 0f -> -d * 4f
    else -> ((d - 0.5f) * 1.2f).coerceIn(0f, 3.5f)
}

/**
 * Una línea. Inactiva: texto blanco (la opacidad la pone su capa). Activa: el texto atenuado y,
 * encima, la parte cantada en blanco: líneas completas hasta la fila actual y, en ella, hasta la
 * posición de la sílaba que suena, con un borde suave. Se lee el reloj al dibujar: solo esta
 * línea se repinta en cada fotograma.
 */
@Composable
private fun LyricText(line: LyricLine, active: Boolean, clock: State<Double>, style: androidx.compose.ui.text.TextStyle) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Posición de cada sílaba en el texto de la línea (el analizador lo arma con ellas).
    val ranges = remember(line) {
        var at = 0
        line.words.map { w ->
            val r = at until at + w.text.length
            at = r.last + 1 + if (w.spaceAfter) 1 else 0
            r
        }
    }
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { WipeEdge.toPx() }
    BasicText(
        line.text,
        style = style.copy(color = Color.White),
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            val l = layout
            if (!active || l == null) { drawContent(); return@drawWithContent }
            val t = clock.value
            drawText(l, color = Color.White.copy(alpha = Unsung))
            // Terminada (o aún sin empezar): toda atenuada, como en la referencia antes de subir.
            if (t < line.startSec || t > line.endSec + 0.15) return@drawWithContent
            // Fila y posición horizontal hasta donde se ha cantado.
            var row: Int
            var x: Float
            if (line.words.isEmpty() || ranges.isEmpty()) {
                row = l.lineCount - 1; x = size.width + edge
            } else {
                val k = line.words.indexOfLast { it.startSec <= t }
                if (k < 0) return@drawWithContent
                val w = line.words[k]
                val r = ranges[k]
                if (r.isEmpty() || r.last >= l.layoutInput.text.length) { row = l.lineCount - 1; x = size.width + edge }
                else {
                    val p = ((t - w.startSec) / (w.endSec - w.startSec).coerceAtLeast(0.01)).coerceIn(0.0, 1.0).toFloat()
                    row = l.getLineForOffset(r.first)
                    val x0 = l.getBoundingBox(r.first).left
                    val x1 = l.getBoundingBox(r.last).right
                    x = x0 + (x1 - x0) * p
                }
            }
            drawIntoCanvas { canvas ->
                // El texto entero en blanco y luego se borra lo no cantado (el modo de mezcla del
                // texto no se respeta en todos los dispositivos; el de los rectángulos, sí).
                canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawText(l, color = Color.White)
                val top = l.getLineTop(row)
                val bottom = l.getLineBottom(row)
                // Fila actual: se conserva hasta x y se desvanece en el borde.
                drawRect(
                    Brush.horizontalGradient(0f to Color.White, 1f to Color.Transparent, startX = x - edge / 2, endX = x + edge / 2),
                    Offset(0f, top), Size(size.width, bottom - top), blendMode = BlendMode.DstIn,
                )
                // Filas siguientes: fuera.
                if (row < l.lineCount - 1) drawRect(Color.Black, Offset(0f, bottom), Size(size.width, size.height - bottom), blendMode = BlendMode.DstOut)
                canvas.restore()
            }
        },
    )
}
