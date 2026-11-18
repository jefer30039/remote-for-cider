package cider.remote.app.ui

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Enlaces de Apple Music que llegan a la app: tocados en otra app (`https://music.apple.com/...`)
 * o compartidos como texto. MainActivity los deja en [pending] y MainScreen los atiende en cuanto
 * hay conexión con Cider.
 */
object AppleMusicLinks {
    val pending = MutableStateFlow<String?>(null)

    private val url = Regex("""https?://(?:geo\.)?music\.apple\.com/\S+""")

    fun from(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        return url.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
    }

    /** Qué abre un enlace: `kind` es el de la ruta de Apple (album, playlist, artist, song…). */
    data class Target(val kind: String, val id: String, val song: String?)

    /** `https://music.apple.com/{tienda}/{tipo}/{nombre?}/{id}?i={canción}`. */
    fun parse(link: String): Target? {
        val uri = runCatching { android.net.Uri.parse(link) }.getOrNull() ?: return null
        val parts = uri.pathSegments.orEmpty()
        val kinds = setOf("album", "playlist", "artist", "song", "station", "music-video", "curator")
        val k = parts.indexOfFirst { it in kinds }.takeIf { it >= 0 } ?: return null
        val id = parts.drop(k + 1).lastOrNull() ?: return null
        return Target(parts[k], id, uri.getQueryParameter("i"))
    }
}
