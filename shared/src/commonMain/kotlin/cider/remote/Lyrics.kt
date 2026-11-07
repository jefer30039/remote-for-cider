package cider.remote

/** Una línea de letra; [words] solo viene en letras por sílaba. */
data class LyricLine(
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

/**
 * Sílaba o palabra con su tiempo. Las sílabas de una misma palabra van seguidas ("con", "ver",
 * "sation"); [spaceAfter] marca el final de palabra (en el TTML, un espacio fuera de las etiquetas).
 */
data class LyricWord(val startSec: Double, val endSec: Double, val text: String, val spaceAfter: Boolean = false)

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
            // Sin coros de fondo (x-bg), traducciones ni romanizaciones, respetando el anidamiento.
            val main = stripRoleSpans(body)
            val words = if (timing == "Word") parseWords(main) else emptyList()
            LyricLine(
                startSec = beginRegex.find(attrs)?.let { parseTime(it.groupValues[1]) } ?: 0.0,
                endSec = endRegex.find(attrs)?.let { parseTime(it.groupValues[1]) } ?: 0.0,
                // Con sílabas, el texto se arma con ellas: así sus posiciones coinciden con las del texto.
                text = if (words.isNotEmpty()) words.joinToString("") { it.text + if (it.spaceAfter) " " else "" }.trim() else clean(main),
                words = words,
            )
        }.filter { it.text.isNotBlank() }.toList()
        return Lyrics(lines, synced = timing != "None")
    }

    private val openSpanRegex = Regex("""<span\b([^>]*)>""")

    /** Quita los `<span ttm:role="x-…">` con todo su contenido, contando los `<span>` anidados. */
    internal fun stripRoleSpans(body: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val open = openSpanRegex.find(body, i)
            if (open == null) { out.append(body, i, body.length); break }
            if (!roleRegex.containsMatchIn(open.groupValues[1])) {
                out.append(body, i, open.range.last + 1); i = open.range.last + 1; continue
            }
            out.append(body, i, open.range.first)
            var depth = 1
            var j = open.range.last + 1
            while (depth > 0 && j < body.length) {
                val nextOpen = body.indexOf("<span", j).let { if (it < 0) Int.MAX_VALUE else it }
                val nextClose = body.indexOf("</span>", j).let { if (it < 0) Int.MAX_VALUE else it }
                if (nextClose == Int.MAX_VALUE) { j = body.length; break }
                if (nextOpen < nextClose) { depth++; j = nextOpen + 5 } else { depth--; j = nextClose + 7 }
            }
            i = j
        }
        return out.toString()
    }

    /** Sílabas de primer nivel; un espacio tras la etiqueta marca el final de palabra. */
    private fun parseWords(body: String): List<LyricWord> {
        val matches = spanRegex.findAll(body).toList()
        return matches.mapNotNull { s ->
            val attrs = s.groupValues[1]
            val begin = beginRegex.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val end = endRegex.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val after = body.getOrNull(s.range.last + 1)
            LyricWord(parseTime(begin), parseTime(end), decode(s.groupValues[2].replace(tagRegex, "")), spaceAfter = after != null && after.isWhitespace())
        }
    }

    private fun clean(s: String) = decode(s.replace(tagRegex, "")).replace(Regex("""\s+"""), " ").trim()

    private fun decode(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    /** `12.345`, `1:02.345` o `1:02:03.4`. */
    internal fun parseTime(value: String): Double =
        value.removeSuffix("s").split(':').fold(0.0) { acc, part -> acc * 60 + (part.toDoubleOrNull() ?: 0.0) }
}
