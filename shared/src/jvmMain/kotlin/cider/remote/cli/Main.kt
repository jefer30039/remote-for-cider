package cider.remote.cli

import cider.remote.CiderApi
import cider.remote.CiderEndpoint
import cider.remote.CiderEvents
import cider.remote.ciderHttpClient
import cider.remote.BrowsePage
import cider.remote.artistPage
import cider.remote.browse
import cider.remote.home
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Herramienta de desarrollo. Lee el token de ~/.config/cider-remote/token-dev.
 *   estado            snapshot de reproducción
 *   cola              primeros elementos de la cola
 *   biblioteca        primeras playlists, álbumes, artistas y canciones
 *   letra [silabas]   resumen de la letra actual
 *   eventos [n]       imprime n eventos del socket (por defecto 20)
 *   nuevo | radio     estanterías de esas pestañas de Apple Music
 *   artista <id>      página de un artista del catálogo
 * Host: variable CIDER_HOST (por defecto 127.0.0.1).
 */
fun main(args: Array<String>) = runBlocking {
    val endpoint = CiderEndpoint(System.getenv("CIDER_HOST") ?: "127.0.0.1")
    val token = File(System.getProperty("user.home"), ".config/cider-remote/token-dev").readText().trim()
    val client = ciderHttpClient()
    val api = CiderApi(client, endpoint, token)
    when (args.firstOrNull() ?: "estado") {
        "estado" -> {
            println(api.clientInfo())
            println(api.playback())
        }
        "cola" -> {
            val (page, total) = api.queue(limit = 10)
            println("posición ${page.position} de $total")
            page.items.forEachIndexed { i, it ->
                val t = it.track.attributes
                println("  $i ${t?.name} — ${t?.artistName}")
            }
        }
        "artista" -> {
            val p = api.artistPage(args.getOrElse(1) { "1031397873" })
            println("${p.name} | logo=${p.logo != null} heroTall=${p.heroTall != null} heroWide=${p.heroWide != null} géneros=${p.genres}")
            println("origen=${p.origin} nacimiento=${p.bornOrFormed} bio=${p.bio?.take(80)}")
            println("último: ${p.latest?.name} (${p.latest?.type}) ${p.latest?.subtitle}")
            println("${p.topSongsTitle}: " + p.topSongs.take(3).joinToString { "${it.name} [${it.subtitle}]${if (it.explicit) " E" else ""}" })
            p.shelves.forEach { println("  ${it.title} (${it.items.size}): " + it.items.take(3).joinToString { "${it.type}:${it.name}" }) }
        }
        "nuevo", "radio" -> {
            api.browse(if (args[0] == "nuevo") BrowsePage.New else BrowsePage.Radio).forEach { shelf ->
                println("${shelf.kind} ${shelf.title} (${shelf.items.size})")
                shelf.items.take(3).forEach { println("    ${it.type} ${it.name} — ${it.reason} / ${it.subtitle} wide=${it.wideArtwork?.cropped(1200, 750)?.take(60)}") }
            }
        }
        "biblioteca" -> {
            api.playlists(limit = 3).let { println("playlists (${it.total}): ${it.items.map { p -> p.name }}") }
            api.albums(limit = 3).let { println("álbumes (${it.total}): ${it.items.map { a -> a.name }}") }
            api.artists(limit = 3).let { println("artistas (${it.total}): ${it.items.map { a -> a.name }}") }
            api.songs(limit = 3).let { println("canciones (${it.total}): ${it.items.map { s -> s.name }}") }
        }
        "letra" -> {
            val id = api.nowPlaying().catalogId ?: error("sin pista del catálogo")
            val lyrics = api.lyrics(id, syllable = args.getOrNull(1) == "silabas")
            if (lyrics == null) println("sin letra") else {
                println("${lyrics.lines.size} líneas, sincronizada=${lyrics.synced}")
                lyrics.lines.take(3).forEach { println("  [${it.startSec}] ${it.text.take(40)} (${it.words.size} palabras)") }
            }
        }
        "animada" -> {
            val t = api.nowPlaying()
            println("${t.name}: " + (t.catalogId?.let { api.animatedCoverForSong(it) } ?: "sin portada animada"))
            println("Torn: " + api.animatedCover("albums", "1871594546"))
        }
        "inicio" -> api.home("-06:00").forEach { sh ->
            println("[${sh.kind}] ${sh.title}: " + sh.items.take(3).joinToString { "${it.name} (${it.type}, ${it.reason ?: "-"}, tall=${it.tallArtwork != null})" })
        }
        "categorias" -> api.searchCategories().forEach { println("  ${it.name} ${it.bgColor} ${it.imageUrl?.take(60)}") }
        "pins" -> api.libraryPins().forEach { println("  ${it.type} ${it.name} ${it.artwork?.sized(200)}") }
        "recientes" -> api.recentlyAdded(6).forEach { println("  ${it.type} ${it.name} ${it.attributes?.artwork?.sized(300)}") }
        "buscar" -> api.search(args.drop(1).joinToString(" "), 3).let { r ->
            listOf("canciones" to r.songs, "álbumes" to r.albums, "artistas" to r.artists, "playlists" to r.playlists)
                .forEach { (k, v) -> println("$k: ${v?.data?.map { it.name }}") }
        }
        "eventos" -> {
            val n = args.getOrNull(1)?.toIntOrNull() ?: 20
            CiderEvents(client, endpoint).stream().take(n).collect { println(it) }
        }
        else -> println("Comando desconocido: ${args.first()}")
    }
    client.close()
}
