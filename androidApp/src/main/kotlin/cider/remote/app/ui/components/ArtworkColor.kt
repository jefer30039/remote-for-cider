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
