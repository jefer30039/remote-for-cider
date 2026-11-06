package cider.remote

/** Una línea de letra; [words] solo viene en letras por sílaba. */
data class LyricLine(
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

data class LyricWord(val startSec: Double, val endSec: Double, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Índice de la línea que suena en [positionSec], o -1. */
    fun indexAt(positionSec: Double): Int {
        if (!synced) return -1
        return lines.indexOfLast { it.startSec <= positionSec }
    }
}

/**
 * Parser mínimo del TTML de Apple Music (`itunes:timing` Line / Word / None).
 * No hay parser XML en common, así que se recorre con expresiones regulares:
 * el formato de Apple es estable y sin anidamientos raros dentro de `<p>`.
 */
object TtmlParser {
    private val timingRegex = Regex("""itunes:timing="(\w+)"""")
    private val pRegex = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val spanRegex = Regex("""<span\b([^>]*)>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val beginRegex = Regex("""\bbegin="([^"]+)"""")
    private val endRegex = Regex("""\bend="([^"]+)"""")
    private val roleRegex = Regex("""ttm:role="([^"]+)"""")
    private val tagRegex = Regex("""<[^>]+>""")

    fun parse(ttml: String): Lyrics {
        val timing = timingRegex.find(ttml)?.groupValues?.get(1) ?: "None"
        val lines = pRegex.findAll(ttml).map { p ->
            val attrs = p.groupValues[1]
            val body = p.groupValues[2]
            val words = if (timing == "Word") parseWords(body) else emptyList()
            LyricLine(
                startSec = beginRegex.find(attrs)?.let { parseTime(it.groupValues[1]) } ?: 0.0,
                endSec = endRegex.find(attrs)?.let { parseTime(it.groupValues[1]) } ?: 0.0,
                text = clean(body.replace(spanWithRole(), "")),
                words = words,
            )
        }.filter { it.text.isNotBlank() }.toList()
        return Lyrics(lines, synced = timing != "None")
    }

    private fun spanWithRole() = Regex("""<span\b[^>]*ttm:role="x-(?:bg|translation|roman)[^"]*"[^>]*>.*?</span>""", RegexOption.DOT_MATCHES_ALL)

    private fun parseWords(body: String): List<LyricWord> =
        spanRegex.findAll(body).mapNotNull { s ->
            val attrs = s.groupValues[1]
            if (roleRegex.containsMatchIn(attrs)) return@mapNotNull null
            val begin = beginRegex.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val end = endRegex.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            LyricWord(parseTime(begin), parseTime(end), decode(s.groupValues[2].replace(tagRegex, "")))
        }.toList()

    private fun clean(s: String) = decode(s.replace(tagRegex, "")).replace(Regex("""\s+"""), " ").trim()

    private fun decode(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    /** `12.345`, `1:02.345` o `1:02:03.4`. */
    internal fun parseTime(value: String): Double =
        value.removeSuffix("s").split(':').fold(0.0) { acc, part -> acc * 60 + (part.toDoubleOrNull() ?: 0.0) }
}
