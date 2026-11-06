package cider.remote.app.ui.components

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cider.remote.app.ui.Apple
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

/** Vidrio líquido con shaders (AGSL); antes de Android 13 se usa el desenfoque de Haze. */
val LiquidGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Lo que hay detrás de las piezas de vidrio: el contenido se graba en una capa
 * ([glassSource]) y cada pieza ([glass]) la vuelve a dibujar desplazada a su
 * sitio, con el desenfoque y la lente encima.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer, internal val haze: HazeState) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(IntSize.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    val haze = rememberHazeState()
    // Probado (2026-11-06): hacer esta capa de composición (Offscreen) para que las piezas
    // lean una textura en vez de volver a ejecutar la lista de dibujo no cambió nada medible.
    return remember(layer, haze) { GlassBackdrop(layer, haze) }
}

/**
 * Marca el contenido que se ve a través del vidrio. El modificador se recuerda: con
 * lambdas nuevas en cada recomposición el nodo de dibujo se invalidaba y la página
 * entera se volvía a grabar aunque no hubiera cambiado nada.
 */
@Composable
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    if (!LiquidGlassSupported) return hazeSource(backdrop.haze)
    return this.then(remember(backdrop) {
        Modifier.onGloballyPositioned { backdrop.origin = it.positionInRoot(); backdrop.size = it.size }
            .drawWithContent {
                backdrop.layer.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop.layer)
            }
    })
}

/**
 * Parámetros de my-lglass (mismos nombres y unidades, px de interfaz → dp).
 * Por defecto, el bloque "Golden Gate" del centro de control (refracción
 * negativa, luz arriba, sombra interior) con los velos de sus tarjetas de
 * notificación, pensados para que el texto encima se lea.
 */
data class GlassStyle(
    val blur: Dp = 18.dp,
    val brightness: Float = 1f,
    val refraction: Float = -3.5f,
    val bezel: Dp = 14.dp,
    val dispersion: Float = 0.06f,
    val saturation: Float = 1.4f,
    val tint: Color = Color(0.07f, 0.07f, 0.09f, 0.52f),
    val tintLight: Color = Color(0.07f, 0.07f, 0.09f, 0.6f),
    val lightAngle: Float = -90f,
    val rim: Float = 0.35f,
    val rimWidth: Dp = 1.5.dp,
    val rimOpposite: Float = 0.9f,
    val innerShadow: Float = 0.12f,
    val innerShadowWidth: Dp = 7.dp,
    val edgeLine: Float = 0.45f,
    val outline: Float = 0f,
) {
    companion object {
        @Composable
        fun current(): GlassStyle = if (Apple.colors.isDark) Dark else Light

        /** Para botones pequeños: la lente de las barras deformaría todo el círculo. */
        @Composable
        fun button(): GlassStyle = current().copy(refraction = -1.6f, bezel = 8.dp, blur = 12.dp)

        /**
         * Lente transparente que se desliza entre pestañas (como la demo de Kyant
         * Backdrop): sin desenfoque ni velo, borde que refracta con separación de colores.
         */
        val Lens = GlassStyle(
            blur = 0.dp, brightness = 1.04f, refraction = -1.8f, bezel = 16.dp, dispersion = 0.3f, saturation = 1.1f,
            tint = Color.Transparent, tintLight = Color.Transparent, lightAngle = -60f,
            rim = 0.55f, rimOpposite = 0.9f, innerShadow = 0.05f, innerShadowWidth = 6.dp, edgeLine = 0.12f,
        )

        /**
         * Velos adaptables (iOS 26): el shader mezcla [tint] y [tintLight] según la
         * luminancia de lo que hay detrás, así el vidrio es oscuro sobre contenido
         * oscuro y blanquecino sobre contenido claro, sea cual sea el tema. Sombreado
         * y saturación siguen el tema (tarjetas de my-lglass).
         */
        val Dark = GlassStyle(tintLight = Color(1f, 1f, 1f, 0.55f))
        val Light = GlassStyle(
            saturation = 1.5f,
            tint = Color(0.07f, 0.07f, 0.09f, 0.52f), tintLight = Color(1f, 1f, 1f, 0.55f),
            innerShadow = 0.04f, edgeLine = 0.15f,
        )
    }
}

/**
 * Material de las piezas flotantes (barra de pestañas, miniplayer). Aplicar
 * después de clip(). [corner] por defecto: cápsula.
 */
@Composable
fun Modifier.glass(backdrop: GlassBackdrop, corner: Dp = 1000.dp, style: GlassStyle? = null, over: GlassBackdrop? = null): Modifier {
    val dark = Apple.colors.isDark
    if (!LiquidGlassSupported) return hazeGlass(backdrop.haze, dark)
    val chosen = style ?: if (dark) GlassStyle.Dark else GlassStyle.Light
    val blurred = rememberGraphicsLayer()
    val layer = rememberGraphicsLayer()
    // Recordado: el nodo de dibujo solo se invalida cuando cambia algo de verdad.
    return this.then(remember(backdrop, corner, chosen, over, blurred, layer) { glassPiece(backdrop, corner, chosen, over, blurred, layer) })
}

private fun glassPiece(backdrop: GlassBackdrop, corner: Dp, chosen: GlassStyle, over: GlassBackdrop?, blurred: GraphicsLayer, layer: GraphicsLayer): Modifier {
    val glass = LiquidGlass()
    var position by mutableStateOf(Offset.Zero)
    return Modifier.onGloballyPositioned { position = it.positionInRoot() }.drawWithContent {
        val margin = ceil(
            (-chosen.refraction).coerceAtLeast(0f) * chosen.bezel.toPx() * (1 + chosen.dispersion) + chosen.blur.toPx(),
        ) + 2f
        // Captura: la pieza con su margen, pero sin salirse de la fuente. Fuera no hay
        // contenido y el desenfoque mezclaría esa transparencia en el borde (la barra
        // salía cortada abajo, junto al borde de la pantalla); así repite la última fila.
        val shift = position - backdrop.origin
        val src = backdrop.size
        val left = floor(shift.x - margin).coerceAtLeast(0f)
        val top = floor(shift.y - margin).coerceAtLeast(0f)
        val right = ceil(shift.x + size.width + margin).coerceAtMost(src.width.toFloat())
        val bottom = ceil(shift.y + size.height + margin).coerceAtMost(src.height.toFloat())
        if (right <= left || bottom <= top) return@drawWithContent drawContent()
        val area = IntSize((right - left).toInt(), (bottom - top).toInt())
        val origin = Offset(shift.x - left, shift.y - top)
        // Desenfoque y lente en capas separadas: encadenados con createChainEffect, la
        // salida del desenfoque desplaza las coordenadas del shader (la forma salía corrida).
        // Sin desenfoque (lente transparente) la capa pasa tal cual.
        blurred.renderEffect = if (chosen.blur > 0.dp) glass.blur(this, chosen) else null
        blurred.record(area) {
            translate(-left, -top) {
                drawLayer(backdrop.layer)
                // Otra fuente encima (la barra de pestañas, para la lente que se mueve sobre ella).
                if (over != null) translate(over.origin.x - backdrop.origin.x, over.origin.y - backdrop.origin.y) { drawLayer(over.layer) }
            }
        }
        layer.renderEffect = glass.lens(this, chosen, size, origin, corner.toPx(), area)
        layer.record(area) { drawLayer(blurred) }
        translate(-origin.x, -origin.y) { drawLayer(layer) }
        drawContent()
    }
}

/**
 * Luminancia de lo que se ve en cada zona de vidrio (para poner textos e iconos en
 * blanco o negro, como iOS 26). Copia asíncrona con PixelCopy de un recorte de la
 * ventana a 16 × 8 px: no bloquea el hilo de UI ni vuelve a dibujar nada. Como el
 * vidrio deja pasar el fondo desenfocado (velo oscuro sobre oscuro, claro sobre claro),
 * su luminancia sigue a la del contenido. Se pide al desplazar, con ≥ 150 ms entre lecturas.
 */
@Stable
class BackdropSampler internal constructor(private val window: android.view.Window?) {
    /** Zonas a medir, en px de la ventana, por clave. */
    var regions: Map<String, Rect> = emptyMap()
    val luminance = mutableStateMapOf<String, Float>()
    /** Verdadero si la zona es oscura (textos en blanco), con histéresis para no parpadear en tonos medios. */
    val dark = mutableStateMapOf<String, Boolean>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var busy = false
    private var pending = false

    private var lastAt = 0L
    private var scheduled = false

    /** Pide una lectura; como mucho una cada [MIN_INTERVAL_MS], con una lectura final pendiente si llegan más. */
    fun request() {
        if (busy) { pending = true; return }
        val wait = MIN_INTERVAL_MS - (android.os.SystemClock.uptimeMillis() - lastAt)
        if (wait > 0) {
            if (!scheduled) { scheduled = true; handler.postDelayed({ scheduled = false; request() }, wait) }
            return
        }
        val w = window ?: return
        val entries = regions.entries.toList().ifEmpty { return }
        busy = true
        lastAt = android.os.SystemClock.uptimeMillis()
        var left = entries.size
        for ((key, rect) in entries) {
            val bitmap = android.graphics.Bitmap.createBitmap(16, 8, android.graphics.Bitmap.Config.ARGB_8888)
            val src = android.graphics.Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            runCatching {
                android.view.PixelCopy.request(w, src, bitmap, { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        val lum = meanLuminance(bitmap)
                        luminance[key] = lum
                        // Banda muerta estrecha: texto negro sobre vidrio de 0,35–0,42 no se leía.
                        val tone = when { lum < 0.42f -> true; lum > 0.5f -> false; else -> dark[key] }
                        if (tone != null && tone != dark[key]) dark[key] = tone
                    }
                    if (--left == 0) done()
                }, handler)
            }.onFailure { if (--left == 0) done() }
        }
    }

    private fun done() {
        busy = false
        if (pending) { pending = false; request() }
    }

    // Cada copia retiene el RenderThread 5–10 ms: a 9 por segundo se notaban tirones; a 2,5 no.
    private companion object { const val MIN_INTERVAL_MS = 400L }

    private fun meanLuminance(bitmap: android.graphics.Bitmap): Float {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var sum = 0.0
        for (c in px) sum += (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
        return (sum / px.size).toFloat()
    }
}

@Composable
fun rememberBackdropSampler(): BackdropSampler {
    val window = androidx.activity.compose.LocalActivity.current?.window
    return remember(window) { BackdropSampler(window) }
}

/** Shader y efecto de una pieza; el efecto se rehace solo si cambian el tamaño o el estilo. */
private class LiquidGlass {
    private var shader: RuntimeShader? = null
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null
    // Clave del efecto en caché (sin asignar listas en cada dibujo).
    private var keyStyle: GlassStyle? = null
    private var keySize = Size.Unspecified
    private var keyOrigin = Offset.Unspecified
    private var keyCorner = -1f
    private var keyArea = IntSize.Zero

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun lens(
        density: androidx.compose.ui.unit.Density,
        s: GlassStyle,
        size: Size,
        origin: Offset,
        corner: Float,
        area: IntSize,
    ): androidx.compose.ui.graphics.RenderEffect {
        cached?.let {
            if (keyStyle == s && keySize == size && keyOrigin == origin && keyCorner == corner && keyArea == area) return it
        }
        keyStyle = s; keySize = size; keyOrigin = origin; keyCorner = corner; keyArea = area
        val sh = shader ?: RuntimeShader(LIQUID_GLASS_AGSL).also { shader = it }
        with(density) {
            sh.setFloatUniform("size", size.width, size.height)
            sh.setFloatUniform("origin", origin.x, origin.y)
            sh.setFloatUniform("valid", 0.5f, 0.5f, area.width - 0.5f, area.height - 0.5f)
            sh.setFloatUniform("corner", corner)
            sh.setFloatUniform("bezel", s.bezel.toPx())
            sh.setFloatUniform("refraction", s.refraction)
            sh.setFloatUniform("dispersion", s.dispersion)
            sh.setFloatUniform("saturation", s.saturation)
            sh.setFloatUniform("brightness", s.brightness)
            sh.setFloatUniform("tint", s.tint.red, s.tint.green, s.tint.blue, s.tint.alpha)
            sh.setFloatUniform("tintLight", s.tintLight.red, s.tintLight.green, s.tintLight.blue, s.tintLight.alpha)
            val a = Math.toRadians(s.lightAngle.toDouble())
            sh.setFloatUniform("light", cos(a).toFloat(), sin(a).toFloat())
            sh.setFloatUniform("rim", s.rim)
            sh.setFloatUniform("rimWidth", s.rimWidth.toPx())
            sh.setFloatUniform("rimOpposite", s.rimOpposite)
            sh.setFloatUniform("innerShadow", s.innerShadow)
            sh.setFloatUniform("innerShadowWidth", s.innerShadowWidth.toPx())
            sh.setFloatUniform("edgeLine", s.edgeLine)
            sh.setFloatUniform("outline", s.outline)
            return android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "backdrop").asComposeRenderEffect()
                .also { cached = it }
        }
    }

    private var blurKey: Float = -1f
    private var blurEffect: androidx.compose.ui.graphics.RenderEffect? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun blur(density: androidx.compose.ui.unit.Density, s: GlassStyle): androidx.compose.ui.graphics.RenderEffect {
        val px = with(density) { s.blur.toPx() }
        blurEffect?.let { if (blurKey == px) return it }
        return android.graphics.RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP).asComposeRenderEffect()
            .also { blurEffect = it; blurKey = px }
    }
}

/**
 * Antes de Android 13: el contenido de detrás desenfocado, algo más saturado y
 * con un velo del tema (HIG liquid-glass.md › variante Regular).
 */
@Composable
private fun Modifier.hazeGlass(state: HazeState, dark: Boolean): Modifier {
    val veil = if (dark) Apple.colors.bar.copy(alpha = 0.62f) else Apple.colors.bar.copy(alpha = 0.7f)
    return hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0.05f)
            colorEffects(
                listOf(
                    HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) })),
                    HazeColorEffect.tint(veil),
                ),
            )
        },
    )
}

/** Contorno fino de las piezas de vidrio; con el shader sobra (ya dibuja su propio borde). */
@Composable
fun Modifier.glassEdge(shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (LiquidGlassSupported) this else border(0.5.dp, Apple.colors.circleBorder, shape)

/**
 * Borde de desplazamiento de iOS 26 bajo la barra de estado: el contenido se funde
 * con el color de fondo y se desenfoca cada vez más hacia arriba (desenfoque
 * progresivo con dos capas), sin borde visible.
 */
@Composable
fun Modifier.glassFade(backdrop: GlassBackdrop, tint: Color): Modifier {
    // Velo con curva suave: casi opaco arriba, nada abajo.
    val veil = fadeOut(tint, 0.96f, from = 0f, to = 1f)
    if (!LiquidGlassSupported) return drawBehind { drawRect(veil) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val soft = remember(density) { blurEffect(with(density) { 6.dp.toPx() }) }
    val strong = remember(density) { blurEffect(with(density) { 30.dp.toPx() }) }
    val softLayer = rememberGraphicsLayer()
    val strongLayer = rememberGraphicsLayer()
    val softMasked = rememberGraphicsLayer()
    val strongMasked = rememberGraphicsLayer()
    return this.then(remember(backdrop, veil, soft, strong, softLayer, strongLayer, softMasked, strongMasked) {
        glassFadePiece(backdrop, veil, soft, strong, softLayer, strongLayer, softMasked, strongMasked)
    })
}

private fun glassFadePiece(
    backdrop: GlassBackdrop,
    veil: Brush,
    soft: androidx.compose.ui.graphics.RenderEffect,
    strong: androidx.compose.ui.graphics.RenderEffect,
    softLayer: GraphicsLayer,
    strongLayer: GraphicsLayer,
    softMasked: GraphicsLayer,
    strongMasked: GraphicsLayer,
): Modifier {
    var position by mutableStateOf(Offset.Zero)
    val softMask = fadeOut(Color.Black, 1f, from = 0.2f, to = 1f)
    val strongMask = fadeOut(Color.Black, 1f, from = 0f, to = 0.85f)
    return Modifier.onGloballyPositioned { position = it.positionInRoot() }.drawWithContent {
        val area = IntSize(size.width.toInt(), size.height.toInt())
        val shift = position - backdrop.origin
        // Cada desenfoque con su máscara: el suave llega más abajo que el fuerte.
        fun masked(blurred: GraphicsLayer, effect: androidx.compose.ui.graphics.RenderEffect, out: GraphicsLayer, mask: Brush) {
            blurred.renderEffect = effect
            blurred.record(area) { translate(-shift.x, -shift.y) { drawLayer(backdrop.layer) } }
            out.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            out.record(area) {
                drawLayer(blurred)
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
            drawLayer(out)
        }
        // Los bordes rectos del contenido (una carátula que entra) no deben verse como una línea:
        // el fuerte cubre casi toda la franja y se desvanece despacio.
        masked(softLayer, soft, softMasked, softMask)
        masked(strongLayer, strong, strongMasked, strongMask)
        drawRect(veil)
        drawContent()
    }
}

/**
 * Degradado vertical de [alpha] a 0 entre [from] y [to] (fracciones del alto) con
 * curva suave (smoothstep) en muchos tramos. Con pocos tramos rectos el ojo ve una
 * línea donde cambia la pendiente (bandas de Mach), aunque el color sea continuo.
 */
private fun fadeOut(color: Color, alpha: Float, from: Float, to: Float, steps: Int = 24): Brush {
    val stops = Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
        val eased = x * x * (3 - 2 * x)
        t to color.copy(alpha = alpha * (1 - eased))
    }
    return Brush.verticalGradient(*stops)
}

private fun blurEffect(px: Float) =
    android.graphics.RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP).asComposeRenderEffect()
