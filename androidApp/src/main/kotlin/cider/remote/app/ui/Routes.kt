package cider.remote.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import cider.remote.app.R
import cider.remote.model.Artwork

/** Pantallas dentro de cada pestaña. [kind] es el tipo de Apple Music para reproducir. */
sealed interface Route {
    /** Título visible: el recurso traducido en las fijas, el nombre del contenido en las demás. */
    @Composable
    fun label(): String

    /** Pantallas fijas, con título traducible. */
    sealed class Fixed(@StringRes val titleRes: Int) : Route {
        @Composable
        override fun label(): String = stringResource(titleRes)
    }

    data object Home : Fixed(R.string.tab_home)
    data object New : Fixed(R.string.tab_new)
    data object Radio : Fixed(R.string.tab_radio)
    data object LibraryHome : Fixed(R.string.tab_library)
    data object Playlists : Fixed(R.string.library_playlists)
    data object Artists : Fixed(R.string.library_artists)
    data object Albums : Fixed(R.string.library_albums)
    data object Songs : Fixed(R.string.library_songs)
    data object Search : Fixed(R.string.tab_search)

    /** Álbum o playlist, de la biblioteca (`library-*`) o del catálogo. */
    data class Collection(
        val kind: String,
        val id: String,
        val title: String,
        val subtitle: String?,
        val artwork: Artwork?,
    ) : Route {
        val isPlaylist: Boolean get() = kind.endsWith("playlists")

        @Composable
        override fun label(): String = title
    }

    /** Categoría de la búsqueda (curador de Apple Music): sus playlists. */
    data class Category(val id: String, val title: String) : Route {
        @Composable
        override fun label(): String = title
    }

    /** Álbumes de un artista; [catalog] distingue catálogo de biblioteca. */
    data class Artist(val id: String, val title: String, val catalog: Boolean) : Route {
        @Composable
        override fun label(): String = title
    }
}

/**
 * Abre una página en la pestaña actual desde cualquier sitio (menús de canción, hoja
 * de reproducción). Lo da MainScreen; la hoja de reproducción se cierra antes de abrir.
 */
val LocalOpenRoute = staticCompositionLocalOf<(Route) -> Unit> { {} }

/** Color de fondo de la página de color actual (álbum, artista), para el velo superior y el desvanecido inferior. */
val LocalPageBackground = staticCompositionLocalOf<androidx.compose.runtime.MutableState<androidx.compose.ui.graphics.Color?>> { androidx.compose.runtime.mutableStateOf(null) }

/** Contador que sube al volver a tocar la pestaña activa ya en su raíz: la pantalla raíz desplaza al inicio. */
val LocalScrollToTop = staticCompositionLocalOf<androidx.compose.runtime.State<Int>> { androidx.compose.runtime.mutableIntStateOf(0) }

/** Ejecuta [scrollToTop] cada vez que el usuario vuelve a tocar la pestaña activa estando en su raíz. */
@androidx.compose.runtime.Composable
fun ScrollToTopEffect(scrollToTop: suspend () -> Unit) {
    val signal = LocalScrollToTop.current
    val initial = androidx.compose.runtime.remember { signal.value }
    androidx.compose.runtime.LaunchedEffect(signal.value) { if (signal.value != initial) scrollToTop() }
}
