package cider.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtmlParserTest {
    @Test
    fun lineTiming() {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" itunes:timing="Line"><body><div begin="1.0" end="1:05.5">""" +
            """<p begin="1.000" end="3.5" itunes:key="L1">Primera &amp; línea</p>""" +
            """<p begin="1:02.250" end="1:05.5" itunes:key="L2">Segunda</p></div></body></tt>"""
        val lyrics = TtmlParser.parse(ttml)
        assertTrue(lyrics.synced)
        assertEquals(listOf("Primera & línea", "Segunda"), lyrics.lines.map { it.text })
        assertEquals(62.25, lyrics.lines[1].startSec)
        assertEquals(0, lyrics.indexAt(2.0))
        assertEquals(1, lyrics.indexAt(70.0))
        assertEquals(-1, lyrics.indexAt(0.5))
    }

    @Test
    fun wordTimingIgnoresBackgroundVocals() {
        val ttml = """<tt itunes:timing="Word"><body><div>""" +
            """<p begin="1" end="3"><span begin="1" end="2">Hola</span> <span begin="2" end="3">mundo</span>""" +
            """<span ttm:role="x-bg"><span begin="2" end="3">(eco)</span></span></p></div></body></tt>"""
        val line = TtmlParser.parse(ttml).lines.single()
        assertEquals("Hola mundo", line.text)
        assertEquals(listOf("Hola", "mundo"), line.words.map { it.text })
    }

    @Test
    fun unsynced() {
        val lyrics = TtmlParser.parse("""<tt itunes:timing="None"><body><div><p>Sin tiempo</p></div></body></tt>""")
        assertEquals(-1, lyrics.indexAt(10.0))
        assertEquals("Sin tiempo", lyrics.lines.single().text)
    }

    @Test
    fun artworkResize() {
        val a = cider.remote.model.Artwork(url = "https://x/abc.jpg/600x600bb.png")
        assertEquals("https://x/abc.jpg/1200x1200bb.png", a.sized(1200))
        val t = cider.remote.model.Artwork(url = "https://x/abc.jpg/{w}x{h}bb.{f}")
        assertEquals("https://x/abc.jpg/300x300bb.jpg", t.sized(300))
    }
}
