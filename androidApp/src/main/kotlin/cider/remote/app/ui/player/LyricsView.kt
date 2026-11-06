package cider.remote.app.ui.player

import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import cider.remote.app.ui.Apple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import cider.remote.CiderRemote
import cider.remote.Lyrics
import cider.remote.PlayerState
import kotlinx.coroutines.delay

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object None : LyricsLoad
    data class Ready(val lyrics: Lyrics) : LyricsLoad
}

@Composable
fun LyricsView(remote: CiderRemote, state: PlayerState, modifier: Modifier = Modifier) {
    // Las canciones de la biblioteca traen un id `i.…`; las letras van por el del catálogo.
    val catalogId = state.track?.catalogId
    val load by produceState<LyricsLoad>(LyricsLoad.Loading, catalogId) {
        value = LyricsLoad.Loading
        value = if (catalogId == null) LyricsLoad.None else runCatching { remote.api.lyrics(catalogId) }
            .getOrNull()?.let { LyricsLoad.Ready(it) } ?: LyricsLoad.None
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val l = load) {
            LyricsLoad.Loading -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
            LyricsLoad.None -> Text(stringResource(R.string.lyrics_none), style = Apple.type.headline, color = Color.White.copy(alpha = 0.62f))
            is LyricsLoad.Ready -> SyncedLyrics(remote, state, l.lyrics)
        }
    }
}

@Composable
private fun SyncedLyrics(remote: CiderRemote, state: PlayerState, lyrics: Lyrics) {
    val listState = rememberLazyListState()
    var current by remember { mutableIntStateOf(-1) }
    val progress = state.progress
    LaunchedEffect(progress, lyrics) {
        while (true) {
            current = lyrics.indexAt(progress.positionNow())
            if (!progress.playing) break
            delay(100)
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(current) {
        // La línea activa queda arriba, con una línea anterior asomando (Apple Music).
        if (current >= 0) listState.animateScrollToItem(current, scrollOffset = -with(density) { 24.dp.roundToPx() })
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 240.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(lyrics.lines) { i, line ->
            val distance = if (current < 0 || !lyrics.synced) 0 else kotlin.math.abs(i - current)
            val alpha = when {
                !lyrics.synced || i == current -> 1f
                distance == 1 -> 0.36f
                distance == 2 -> 0.26f
                else -> 0.18f
            }
            Text(
                line.text,
                style = Apple.type.title1.copy(fontSize = 32.sp, lineHeight = 38.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (distance >= 2) Modifier.blur((distance - 1).coerceAtMost(3).dp) else Modifier)
                    .clickable(enabled = lyrics.synced) { remote.seek(line.startSec) }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            )
        }
    }
}
