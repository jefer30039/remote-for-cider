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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
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

sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object None : LyricsLoad
    data class Ready(val lyrics: Lyrics) : LyricsLoad
}

/**
 * Letras ya pedidas, en memoria. Antes cada vez que se abría la letra se volvía a pedir (~0,3 s con el indicador
 * girando): el paso de la carátula a la letra parecía más largo que la vuelta. La hoja precarga la de la canción
 * que suena ([preload]).
 */
object LyricsCache {
    private val cache = android.util.LruCache<String, LyricsLoad>(12)
    fun peek(catalogId: String): LyricsLoad? = cache.get(catalogId)
    suspend fun load(remote: CiderRemote, catalogId: String): LyricsLoad {
        peek(catalogId)?.let { return it }
        // Por sílaba si Apple la tiene (karaoke); si no, por línea.
        val lyrics = runCatching { remote.api.lyrics(catalogId, syllable = true) }.getOrNull()
            ?: runCatching { remote.api.lyrics(catalogId) }.getOrNull()
        val result = lyrics?.let { LyricsLoad.Ready(it) } ?: LyricsLoad.None
        // Solo las encontradas: "sin letra" puede ser un fallo de red y se reintenta la próxima vez.
        if (lyrics != null) cache.put(catalogId, result)
        return result
    }
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
    val load by produceState(catalogId?.let { LyricsCache.peek(it) } ?: LyricsLoad.Loading, catalogId) {
        if (catalogId == null) { value = LyricsLoad.None; return@produceState }
        LyricsCache.peek(catalogId)?.let { value = it; return@produceState }
        value = LyricsLoad.Loading
        value = LyricsCache.load(remote, catalogId)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val l = load) {
            LyricsLoad.Loading -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
            LyricsLoad.None -> Text(stringResource(R.string.lyrics_none), style = Apple.type.headline, color = Color.White.copy(alpha = 0.62f))
            is LyricsLoad.Ready -> if (l.lyrics.synced) SyncedLyrics(remote, state, l.lyrics, anchorTop) else PlainLyrics(l.lyrics, anchorTop)
        }
    }
}

/**
 * Letra sin tiempos: lista desplazable, todo en blanco. Empieza donde va la línea activa de las sincronizadas
 * ([anchorTop], justo bajo la cabecera): la vista llega por detrás de la cabecera y, con el margen de antes
 * (8 dp), la primera línea quedaba tapada por la carátula y el título. Al desplazarla pasa por debajo y se funde.
 */
@Composable
private fun PlainLyrics(lyrics: Lyrics, anchorTop: androidx.compose.ui.unit.Dp?) {
    val style = lyricStyle()
    // La primera línea lleva 9 dp de margen propio arriba.
    val top = anchorTop?.let { (it - 9.dp).coerceAtLeast(8.dp) } ?: 8.dp
    LazyColumn(contentPadding = PaddingValues(top = top, bottom = 240.dp), modifier = Modifier.fillMaxSize()) {
        items(lyrics.lines) { line ->
            BasicText(line.text, style = style.copy(color = Color.White), modifier = Modifier.fillMaxWidth().padding(horizontal = LyricsSidePadding, vertical = 9.dp))
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
/** Margen lateral del texto de la letra dentro de su vista. */
internal val LyricsSidePadding = 33.dp
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
    // Líneas y pausas (los "…" de Cider). Solo cambia (y recompone) al pasar de elemento.
    val items = remember(lyrics) { lyricItems(lyrics) }
    val active by remember(items) {
        derivedStateOf {
            val t = clock.doubleValue
            var k = 0
            for (i in items.indices) { if (items[i].activeFrom <= t) k = i else break }
            k
        }
    }
    val focus = remember(items) { Animatable(active.toFloat()) }
    LaunchedEffect(active) { focus.animateTo(active.toFloat(), spring(dampingRatio = 1f, stiffness = 110f)) }
    // Hueco de cada pausa en la lista (Cider: se despliega en 0,8 s con salida exponencial al empezar y se
    // pliega en los últimos 0,6 s). Plegada no ocupa sitio ni cuenta como elemento para la distancia.
    val reveals = remember(items) { items.indices.filter { items[it] is GapItem }.associateWith { Animatable(0f) } }
    LaunchedEffect(active, reveals) {
        reveals.forEach { (i, a) ->
            launch {
                val gap = items[i] as GapItem
                if (i == active) {
                    launch { a.animateTo(1f, androidx.compose.animation.core.tween(800, easing = ExpoOut)) }
                    val untilFold = gap.end - 0.6 - clock.doubleValue
                    if (untilFold > 0) delay((untilFold * 1000).toLong())
                    a.animateTo(0f, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing))
                } else if (a.value > 0f) {
                    a.animateTo(0f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearEasing))
                }
            }
        }
    }

    // Arrastre manual: congela el foco y desplaza; a los 3 s sin tocar vuelve a seguir la canción.
    val scope = rememberCoroutineScope()
    val manual = remember { Animatable(0f) }
    var userMode by remember { mutableStateOf(false) }
    val frozenFocus = remember { mutableFloatStateOf(0f) }
    var touches by remember { mutableIntStateOf(0) }
    val metrics = remember(items, anchorTop) { LineMetrics(items.size, byTop = anchorTop != null) }
    val dragState = rememberDraggableState { delta -> scope.launch { manual.snapTo(manual.value + delta) } }
    LaunchedEffect(userMode, touches) {
        if (!userMode) return@LaunchedEffect
        delay(3000)
        // Mismo sitio visual con el foco de ahora, y vuelta suave a la línea que suena. La vuelta va
        // en el ámbito de la vista: al poner userMode a falso este efecto se reinicia y, dentro de él,
        // la animación se cancelaba nada más empezar (la letra se quedaba donde se dejó).
        manual.snapTo(manual.value + metrics.anchorAt(focus.value) - metrics.anchorAt(frozenFocus.floatValue))
        userMode = false
        scope.launch { manual.animateTo(0f, spring(dampingRatio = 1f, stiffness = 110f)) }
    }

    val style = lyricStyle()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { LineGap.toPx() }
    val sidePx = with(density) { LyricsSidePadding.roundToPx() }
    val blurUnit = with(density) { 1.4.dp.toPx() }
    val anchorTopPx = anchorTop?.let { with(density) { it.toPx() } }

    Layout(
        content = {
            items.forEachIndexed { i, item ->
                when (item) {
                    is LineItem -> Box(
                        Modifier.clickable(interactionSource = null, indication = null) { remote.seek(item.line.startSec) },
                    ) {
                        LyricText(item.line, phase = if (i == active) 0 else if (i < active) -1 else 1, clock = clock, style = style)
                    }
                    is GapItem -> InterludeDots(item, clock, style)
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
            // Posición de cada elemento contando las pausas solo por lo desplegadas que están: con la pausa
            // plegada, la línea de después queda a distancia 1 de la de antes (su opacidad y escala de siempre).
            var acc = 0f
            for (i in placeables.indices) {
                metrics.rank[i] = acc
                acc += reveals[i]?.value ?: 1f
            }
            val rf = metrics.rankAt(f)
            // Alturas con su escala de ahora: la línea que crece empuja a las de abajo.
            var y = 0f
            for (i in placeables.indices) {
                val s = scaleFor(metrics.rank[i] - rf)
                val r = reveals[i]?.value ?: 1f
                metrics.top[i] = y
                metrics.height[i] = placeables[i].height * s * r
                y += (placeables[i].height + gapPx) * s * r
            }
            // Teléfono: la parte de arriba de la activa a distancia fija del borde (no se mueve si la
            // zona crece al ocultarse los controles); tablet: su centro al 38 % del alto.
            val offset = (anchorTopPx ?: (height * Anchor)) - metrics.anchorAt(f) + manual.value
            for (i in placeables.indices) {
                val top = metrics.top[i] + offset
                // Fuera de la vista no se coloca (ni se dibuja).
                if (top > height || top + metrics.height[i] < 0f || metrics.height[i] <= 0f) continue
                val d = metrics.rank[i] - rf
                placeables[i].placeWithLayer(sidePx, top.toInt()) {
                    val s = scaleFor(d)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = if (user) (if (abs(d) < 0.5f) 1f else 0.5f) else alphaFor(d)
                    // Modo rendimiento: las demás líneas solo se atenúan, sin desenfoque.
                    val blur = if (user || cider.remote.app.ui.Appearance.lite) 0f else blurFor(d) * blurUnit
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
    /** Posición de cada elemento contando las pausas por lo desplegadas que están. */
    val rank = FloatArray(n)

    fun rankAt(f: Float): Float {
        if (rank.isEmpty()) return 0f
        val i = floor(f).toInt().coerceIn(0, rank.size - 1)
        val j = (i + 1).coerceAtMost(rank.size - 1)
        return rank[i] + (rank[j] - rank[i]) * (f - i).coerceIn(0f, 1f)
    }

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
private fun LyricText(line: LyricLine, phase: Int, clock: State<Double>, style: androidx.compose.ui.text.TextStyle) {
    val active = phase == 0
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edge = with(density) { WipeEdge.toPx() }
    val fontPx = with(density) { style.fontSize.toPx() }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 0)
    val fx = remember(line, layout) { layout?.let { SyllableFx(line, ranges, it, measurer, style, fontPx) } }
    val lite = cider.remote.app.ui.Appearance.lite
    BasicText(
        line.text,
        style = style.copy(color = Color.White),
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            val l = layout
            // Ya cantada: se queda alzada (Cider, `clp-cold-sung`) mientras se desvanece.
            if (phase < 0 && fx != null) { val content = this; translate(0f, -fx.liftPx) { content.drawContent() }; return@drawWithContent }
            if (!active || l == null) { drawContent(); return@drawWithContent }
            val t = clock.value
            fun pass(color: Color, glow: Boolean) = if (fx == null) drawText(l, color = color) else with(fx) { draw(l, t, color, glow && !lite) }
            pass(Color.White.copy(alpha = Unsung), glow = false)
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
                canvas.saveLayer(Rect(Offset(0f, -fontPx), Size(size.width, size.height + 2 * fontPx)), Paint())
                pass(Color.White, glow = true)
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

// ---------------------------------------------------------------------------------------------
// Efectos de la letra de Cider (reproductor de letras de Cider 4.0.9.1, `clp`): puntos en las pausas,
// sílabas que se alzan al cantarse y brillo con ola en las sostenidas. Se siguen los valores de Cider
// para que la app se vea como Cider en el escritorio (decisión del usuario, 2026-11-23); el detalle y
// las fuentes, en docs/letras-apple-music.md.
// ---------------------------------------------------------------------------------------------

/** Elemento de la lista de la letra: una línea o una pausa. [activeFrom]: desde cuándo es el elemento activo. */
private sealed interface LyricItem { val activeFrom: Double }
private class LineItem(val line: LyricLine) : LyricItem { override val activeFrom get() = line.startSec - Lead }
/** Pausa de [start] (fin de lo cantado antes; 0 en la introducción) a [end] (cuando sube la línea siguiente). */
private class GapItem(val start: Double, val end: Double) : LyricItem { override val activeFrom get() = start }

/** Pausa mínima con puntos (Cider: ≥ 5 s). También en la introducción; nunca tras la última línea. */
private const val InterludeMin = 5.0

private fun lyricItems(lyrics: Lyrics): List<LyricItem> {
    val out = ArrayList<LyricItem>(lyrics.lines.size + 4)
    var sungUntil = 0.0
    lyrics.lines.forEachIndexed { i, l ->
        // Desde el mayor final de todo lo anterior: las líneas se solapan.
        val from = if (i == 0) 0.0 else sungUntil
        val until = l.startSec - Lead
        if (l.startSec - from >= InterludeMin && until > from) out += GapItem(from, until)
        out += LineItem(l)
        sungUntil = maxOf(sungUntil, l.endSec)
    }
    return out
}

private val ExpoOut = androidx.compose.animation.core.Easing { x -> if (x >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * x).toFloat() }

/** easeInOutBack (la salida con rebote de los puntos de Cider). */
private fun easeInOutBack(x: Float): Float {
    val c = 1.70158f * 1.525f
    return if (x < 0.5f) (2 * x) * (2 * x) * ((c + 1) * 2 * x - c) / 2
    else ((2 * x - 2) * (2 * x - 2) * ((c + 1) * (x * 2 - 2) + c) + 2) / 2
}

/**
 * Tres puntos durante una pausa (Cider `updateDots`): grupo a escala 0,7 que respira ±5 % (ciclos de ~4,7 s que
 * caben enteros en la pausa), crece desde 0 en 2 s con salida exponencial y aparece entre 0,5 y 1 s; en los
 * últimos 0,75 s encoge con un pequeño rebote y en los últimos 0,375 s se desvanece. Cada punto pasa de 0,25 a 1
 * en su tercio de la pausa. Puntos de 0,34 em separados 0,22 em, pegados al margen del texto. Se lee el reloj
 * al dibujar: solo se repinta este elemento.
 */
@Composable
private fun InterludeDots(gap: GapItem, clock: State<Double>, style: androidx.compose.ui.text.TextStyle) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    // Alto de una fila de texto (con los 0,35 em de Cider arriba y abajo, casi lo mismo).
    val rowDp = with(density) { style.lineHeight.toDp() }
    androidx.compose.foundation.layout.Spacer(
        Modifier.fillMaxWidth().height(rowDp).drawBehind {
            val total = (gap.end - gap.start).coerceAtLeast(0.001)
            val n = clock.value - gap.start
            if (n <= 0 || n >= total) return@drawBehind
            val r = total / kotlin.math.ceil(total / 1.5)
            var scale = (kotlin.math.sin(1.5 * Math.PI - n / r * 2) / 20 + 1).toFloat()
            var alpha = 1f
            if (n < 2) scale *= ExpoOut.transform((n / 2).toFloat())
            alpha = if (n < 0.5) 0f else if (n < 1) ((n - 0.5) / 0.5).toFloat() else 1f
            val left = total - n
            if (left < 0.75) scale *= 1f - easeInOutBack(((0.75 - left) / 0.75 / 2).toFloat())
            if (left < 0.375) alpha *= (left / 0.375).toFloat().coerceIn(0f, 1f)
            scale = scale.coerceAtLeast(0f) * 0.7f
            if (scale <= 0f || alpha <= 0f) return@drawBehind
            val dot = 0.34f * fontPx
            val gapX = 0.22f * fontPx
            val groupW = 3 * dot + 2 * gapX
            val x0 = -0.15f * fontPx
            val cy = size.height / 2
            val s = (total - 0.75).coerceAtLeast(0.001)
            scale(scale, pivot = Offset(x0 + groupW / 2, cy)) {
                for (k in 0..2) {
                    val a = (((n - k * s / 3) * 3 / s) * 0.75).toFloat().coerceIn(0.25f, 1f)
                    drawCircle(Color.White.copy(alpha = alpha * a), radius = dot / 2, center = Offset(x0 + dot / 2 + k * (dot + gapX), cy))
                }
            }
        },
    )
}

/** Curvas de Cider: `ease-out` de CSS (alzado) y la del pico del brillo (sube con una, baja con otra). */
private val LiftEase = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val GlowUp = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.4f, 0.58f, 1f)
private val GlowDown = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.58f, 1f)
private fun glowCurve(x: Float): Float = if (x < 0.5f) GlowUp.transform(x / 0.5f) else 1f - GlowDown.transform((x - 0.5f) / 0.5f)

/**
 * Alzado y brillo de las sílabas de una línea (Cider `fxSpecs`):
 * - cada sílaba sube 0,05 em desde que empieza, en max(1 s, su duración), con `ease-out`, y se queda arriba;
 * - las de ≥ 1 s se animan letra a letra: escala hasta 1 + 0,1·intensidad sobre el centro de la letra, las letras
 *   se separan del centro de la sílaba y suben un poco, con un resplandor blanco; la ola recorre la sílaba en el
 *   40 % de su duración. Además cada letra flota 0,05 em (seno) desde 0,4 s antes y durante 1,4 veces ese tiempo.
 *   La intensidad crece con la duración (cúbica hasta 2–3 s, raíz después) y la sílaba que contiene el texto
 *   de la última de la línea va más fuerte y dura más.
 * La línea se dibuja por sílabas, cada una recortada a su caja y desplazada; las de brillo, letra a letra.
 */
private class SyllableFx(
    private val line: LyricLine,
    private val ranges: List<IntRange>,
    layout: TextLayoutResult,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    private val fontPx: Float,
) {
    val liftPx = 0.05f * fontPx
    private val text = layout.layoutInput.text.text
    /** Caja de cada sílaba (todas sus filas), en el diseño de la línea. */
    private val paths: List<androidx.compose.ui.graphics.Path?> = ranges.map { r ->
        if (r.isEmpty() || r.last >= text.length) null else layout.getPathForRange(r.first, r.last + 1)
    }
    private val prefix = HashMap<Int, androidx.compose.ui.graphics.Path>()
    private val suffix = HashMap<Int, androidx.compose.ui.graphics.Path>()

    private class Glyph(val layout: TextLayoutResult, val left: Float, val top: Float, val index: Int)
    private class Glow(val k: Int, val startMs: Double, val durMs: Double, val amount: Float, val glow: Float, val radiusEm: Float, val glyphs: List<Glyph>)

    private val glows: Map<Int, Glow> = run {
        val out = HashMap<Int, Glow>()
        val lastText = line.words.lastOrNull()?.text.orEmpty()
        line.words.forEachIndexed { k, w ->
            val d = (w.endSec - w.startSec) * 1000
            val r = ranges.getOrNull(k) ?: return@forEachIndexed
            if (d < 1000 || r.isEmpty() || r.last >= text.length) return@forEachIndexed
            var f = maxOf(1000.0, d)
            var p = f / 2000; p = if (p > 1) kotlin.math.sqrt(p) else p * p * p
            var m = f / 3000; m = if (m > 1) kotlin.math.sqrt(m) else m * m * m
            p *= 0.6; m *= 0.5
            if (lastText.isNotEmpty() && w.text.contains(lastText)) { p *= 1.6; m *= 1.5; f *= 1.2 }
            p = minOf(1.2, p)
            // Brillo: con la fórmula de Cider (m de arriba) una sílaba de 1,5 s apenas brillaba (opacidad 0,06) y el
            // usuario lo vio muy débil frente a una grabación de referencia ("Ain't In LA", 2026-11-23), donde la
            // palabra sostenida lleva un halo blanco claro de ~0,25 em. Fuerte desde 1 s y algo más con la duración.
            // Primer ajuste (0,6–0,9 y dos halos enteros): "ahora fue mucho"; a mitad de camino.
            m = (0.35 + 0.2 * ((d - 1000) / 1500).coerceIn(0.0, 1.0)) * (if (lastText.isNotEmpty() && w.text.contains(lastText)) 1.1 else 1.0)
            m = minOf(0.6, m)
            val chars = r.filter { !text[it].isWhitespace() }
            val glyphs = chars.mapIndexed { i, o ->
                val box = layout.getBoundingBox(o)
                val baseline = layout.getLineBaseline(layout.getLineForOffset(o))
                val g = measurer.measure(text.substring(o, o + 1), style)
                Glyph(g, box.left, baseline - g.firstBaseline, i)
            }
            if (glyphs.isNotEmpty()) out[k] = Glow(k, w.startSec * 1000, f, p.toFloat(), m.toFloat(), 0.22f, glyphs)
        }
        out
    }

    private fun lift(k: Int, tMs: Double): Float {
        val w = line.words[k]
        val x = ((tMs - w.startSec * 1000) / maxOf(1000.0, (w.endSec - w.startSec) * 1000)).coerceIn(0.0, 1.0).toFloat()
        return liftPx * LiftEase.transform(x)
    }

    /** La sílaba se dibuja letra a letra (su brillo o su flotación están en marcha). */
    private fun animating(g: Glow, tMs: Double): Boolean {
        val lastStart = g.startMs + g.durMs / 2.5 / g.glyphs.size * (g.glyphs.size - 1)
        return tMs > g.startMs - 400 && tMs < lastStart + maxOf(g.durMs, g.durMs * 1.4 - 400)
    }

    private fun union(from: Int, until: Int): androidx.compose.ui.graphics.Path {
        val p = androidx.compose.ui.graphics.Path()
        for (k in from until until) paths[k]?.let { p.addPath(it) }
        return p
    }

    fun androidx.compose.ui.graphics.drawscope.DrawScope.draw(l: TextLayoutResult, t: Double, color: Color, glow: Boolean) {
        val n = line.words.size
        if (n == 0 || paths.size != n) { drawText(l, color = color); return }
        val tMs = t * 1000
        // Sílabas que ya están arriba del todo (y sin efecto en marcha) al principio, y las que aún no han
        // empezado al final: un solo recorte para cada grupo (en caché); en medio, una a una.
        var a = 0
        while (a < n && lift(a, tMs) >= liftPx && glows[a]?.let { animating(it, tMs) } != true) a++
        var b = n
        while (b > a && tMs < line.words[b - 1].startSec * 1000 - 400) b--
        if (a > 0) clipPath(prefix.getOrPut(a) { union(0, a) }) { translate(0f, -liftPx) { drawText(l, color = color) } }
        if (b < n) clipPath(suffix.getOrPut(b) { union(b, n) }) { drawText(l, color = color) }
        for (k in a until b) {
            val g = glows[k]
            if (g != null && animating(g, tMs)) drawGlyphs(g, lift(k, tMs), tMs, color, glow)
            else paths[k]?.let { clipPath(it) { translate(0f, -lift(k, tMs)) { drawText(l, color = color) } } }
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyphs(g: Glow, lift: Float, tMs: Double, color: Color, glow: Boolean) {
        val count = g.glyphs.size
        for (glyph in g.glyphs) {
            val i = glyph.index
            val start = g.startMs + g.durMs / 2.5 / count * i
            val x = ((tMs - start) / g.durMs).toFloat()
            val e = if (x in 0f..1f) glowCurve(x) else 0f
            val sx = ((tMs - (start - 400)) / (g.durMs * 1.4)).toFloat()
            val float = if (sx in 0f..1f) kotlin.math.sin(sx * Math.PI).toFloat() * 0.05f * fontPx else 0f
            val sc = 1f + 0.1f * g.amount * e
            val dx = -0.03f * g.amount * e * (count / 2f - i) * fontPx
            val dy = -0.025f * g.amount * e * fontPx
            // Halo en dos capas: uno ancho (0,22 em) y otro ceñido (0,07 em), como el de la referencia.
            // Desenfoque de `text-shadow` (σ = r/2) a radio de Android (σ ≈ 0,577·radio + 0,5).
            fun shadow(em: Float, alpha: Float): androidx.compose.ui.graphics.Shadow? {
                if (!glow || e <= 0f) return null
                val blur = ((em * fontPx / 2 - 0.5f) / 0.577f).coerceAtLeast(0f)
                return if (blur > 0f) androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = (alpha * e).coerceIn(0f, 1f)), Offset.Zero, blur) else null
            }
            translate(glyph.left + dx, glyph.top + dy - lift - float) {
                val c = Offset(glyph.layout.size.width / 2f, glyph.layout.size.height / 2f)
                scale(sc, pivot = c) {
                    shadow(g.radiusEm, g.glow * 0.7f)?.let { drawText(glyph.layout, color = color, shadow = it) }
                    drawText(glyph.layout, color = color, shadow = shadow(0.07f, g.glow * 0.6f))
                }
            }
        }
    }
}
