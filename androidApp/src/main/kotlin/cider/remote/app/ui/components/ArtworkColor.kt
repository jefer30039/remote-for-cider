package cider.remote.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import cider.remote.model.Artwork
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<String, Color>()

/**
 * Color de acento de una carátula, como el que Apple Music pone de fondo.
 * Usa `bgColor` si Apple lo envía; si no (la biblioteca de Cider no lo trae),
 * el color medio de la imagen. Null mientras se calcula.
 */
@Composable
fun rememberArtworkColor(artwork: Artwork?): Color? {
    val context = LocalContext.current
    val given = artwork?.bgColor?.let { runCatching { Color(("ff$it").toLong(16).toInt()) }.getOrNull() }
    val url = artwork?.sized(120)
    val known = given ?: url?.let { cache[it] }
    // produceState solo usa el valor inicial la primera vez: al cambiar de canción
    // hay que fijarlo a mano o se queda el color de la anterior. Mientras se calcula
    // el nuevo se mantiene el anterior, para que el cambio sea un fundido y no un salto a gris.
    val color by produceState(known, given, url) {
        if (known != null || url == null) { value = known; return@produceState }
        value = withContext(Dispatchers.Default) { averageColor(context, url) }?.also { cache[url] = it }
    }
    return color
}

/** Color medio de la imagen: con él sale el mismo tono que Apple ("Obsessed" → rojo, no el naranja del texto). */
private suspend fun averageColor(context: Context, url: String): Color? = runCatching {
    val request = ImageRequest.Builder(context).data(url).size(120).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val pixel = Bitmap.createScaledBitmap(result.image.toBitmap(), 1, 1, true).getPixel(0, 0)
    Color(pixel)
}.getOrNull()

/**
 * Lleva un color a la luminancia de los fondos de Apple Music conservando el tono
 * ("Obsessed", bgColor #B51B00 → ~#580D00 frente al #5C0100 de Apple).
 * Con luminancia 0,022 el blanco encima da unos 14:1.
 */
fun Color.asBackground(targetLuminance: Float = 0.022f, saturationBoost: Float = 1.25f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    // Las carátulas vivas se avivan; las apagadas tienden al neutro, como hace Apple ("Torn").
    hsv[1] = (hsv[1] * if (hsv[1] > 0.45f) saturationBoost else 0.6f).coerceAtMost(1f)
    var c = Color(android.graphics.Color.HSVToColor(hsv))
    repeat(120) { if (c.luminance() > targetLuminance) c = Color(c.red * 0.975f, c.green * 0.975f, c.blue * 0.975f) }
    return c
}

private val paletteCache = ConcurrentHashMap<String, List<Color>>()

/**
 * Cuatro colores dominantes de la carátula (k-medias sobre 20 × 20 px), del más al menos
 * presente, ajustados al fondo de la hoja como hace Apple Music.
 * Null mientras se calcula; mientras tanto se usa la paleta anterior.
 */
@Composable
fun rememberArtworkPalette(artwork: Artwork?): List<Color>? {
    val context = LocalContext.current
    val url = artwork?.sized(64)
    val known = url?.let { paletteCache[it] }
    val palette by produceState(known, url) {
        if (known != null || url == null) { value = known; return@produceState }
        value = withContext(Dispatchers.Default) { extractPalette(context, url) }?.also { paletteCache[url] = it }
    }
    return palette
}

private suspend fun extractPalette(context: Context, url: String): List<Color>? = runCatching {
    val request = ImageRequest.Builder(context).data(url).size(64).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val bmp = Bitmap.createScaledBitmap(result.image.toBitmap(), 20, 20, true)
    val px = IntArray(400); bmp.getPixels(px, 0, 20, 0, 0, 20, 20)
    val pts = px.map { floatArrayOf(((it shr 16) and 0xFF) / 255f, ((it shr 8) and 0xFF) / 255f, (it and 0xFF) / 255f) }
    // k-medias (k = 4) con semillas repartidas por brillo.
    val sorted = pts.sortedBy { it[0] * 0.2126f + it[1] * 0.7152f + it[2] * 0.0722f }
    val centers = Array(4) { sorted[(sorted.size - 1) * (2 * it + 1) / 8].clone() }
    val counts = IntArray(4)
    repeat(10) {
        val sums = Array(4) { FloatArray(3) }; counts.fill(0)
        for (q in pts) {
            var best = 0; var bd = Float.MAX_VALUE
            for (k in 0 until 4) { val c = centers[k]; val d = (q[0] - c[0]).let { it * it } + (q[1] - c[1]).let { it * it } + (q[2] - c[2]).let { it * it }; if (d < bd) { bd = d; best = k } }
            counts[best]++; for (i in 0..2) sums[best][i] += q[i]
        }
        for (k in 0 until 4) if (counts[k] > 0) for (i in 0..2) centers[k][i] = sums[k][i] / counts[k]
    }
    (0 until 4).filter { counts[it] > 0 }.sortedByDescending { counts[it] }.map { k ->
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb((centers[k][0] * 255).toInt(), (centers[k][1] * 255).toInt(), (centers[k][2] * 255).toInt()), hsv)
        // Medido en Apple Music: el color se conserva, con el brillo a ~0,63 (lavanda 0,68 → 0,43;
        // cielo 0,56 → 0,36) y la saturación algo subida (0,09 → 0,13); los grises siguen grises.
        hsv[1] = (hsv[1] * 1.3f + 0.02f).coerceAtMost(0.8f)
        hsv[2] = (hsv[2] * 0.63f).coerceIn(0.1f, 0.5f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}.getOrNull()

/**
 * Fondo de las páginas de álbum y artista a partir del color de la carátula, como Apple Music
 * (medido en "Replay 2026": tono de la carátula, saturación ~0,9, brillo 0,28 en oscuro y
 * 0,70 en claro). Las carátulas grises siguen grises.
 */
fun Color.pageBackground(dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    if (hsv[1] > 0.2f) hsv[1] = (hsv[1] * 1.2f).coerceAtMost(0.92f)
    hsv[2] = if (dark) 0.28f else 0.70f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Degradado de [from] (fracción del alto) al borde inferior hacia [color], con curva suave:
 * el fundido de la portada al fondo de la página, largo y sin escalón (Apple lo extiende por
 * casi la mitad inferior del vídeo).
 */
fun fadeInto(color: Color, from: Float = 0.45f, steps: Int = 12): androidx.compose.ui.graphics.Brush {
    val stops = Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val eased = t * t * (3 - 2 * t)
        (from + (1 - from) * t) to color.copy(alpha = eased)
    }
    return androidx.compose.ui.graphics.Brush.verticalGradient(*stops)
}
