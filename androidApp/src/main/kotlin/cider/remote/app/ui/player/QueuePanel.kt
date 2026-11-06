package cider.remote.app.ui.player

import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cider.remote.CiderRemote
import cider.remote.PlayerState
import cider.remote.RemoteSignal
import cider.remote.app.ui.Apple
import cider.remote.app.ui.components.ArtworkImage
import cider.remote.app.ui.components.combinedClickableCompat
import cider.remote.model.QueueItem
import cider.remote.model.RepeatMode
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Cuántas siguientes se muestran: cada elemento de la cola pesa ~7 KB. */
private const val WINDOW = 100

private data class Row(val key: String, val index: Int, val item: QueueItem)

/** "A continuación": lo que sigue tras la canción actual. */
@Composable
fun QueuePanel(remote: CiderRemote, state: PlayerState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Row>?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }

    suspend fun reload() {
        runCatching {
            val start = remote.api.queuePosition().position + 1
            val (page, _) = remote.api.queue(offset = start, limit = WINDOW)
            rows = page.items.mapIndexed { i, it -> Row("${start + i}:${it.track.id}", start + i, it) }
        }
    }
    LaunchedEffect(remote) {
        reload()
        remote.signals.filter { it is RemoteSignal.QueueChanged }.collect { if (dragFrom < 0) reload() }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows?.toMutableList()?.apply { add(to.index - 1, removeAt(from.index - 1)) }
    }

    LazyColumn(state = listState, modifier = modifier) {
        item(key = "cabecera") {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.up_next), style = Apple.type.title3, color = Color.White, modifier = Modifier.weight(1f))
                Toggle(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), state.shuffle) { remote.toggleShuffle() }
                Toggle(
                    if (state.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    stringResource(when (state.repeat) { RepeatMode.None -> R.string.repeat_off; RepeatMode.All -> R.string.repeat_all; RepeatMode.One -> R.string.repeat_one }),
                    state.repeat != RepeatMode.None,
                ) { remote.cycleRepeat() }
            }
        }
        val list = rows
        if (list != null && list.isEmpty()) item(key = "vacio") {
            Text(
                stringResource(R.string.queue_empty), style = Apple.type.subhead, color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(8.dp),
            )
        }
        items(list.orEmpty(), key = { it.key }) { row ->
            ReorderableItem(reorder, key = row.key) { dragging ->
                val dismiss = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismiss.currentValue) {
                    if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        rows = rows?.filterNot { it.key == row.key }
                        runCatching { remote.api.queueRemove(row.index) }
                        reload()
                    }
                }
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        // Solo mientras se desliza: la fila es transparente sobre el fondo de la hoja.
                        if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF3B30)),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Filled.Delete, stringResource(R.string.action_remove), tint = Color.White, modifier = Modifier.padding(end = 20.dp)) }
                    },
                ) {
                    val t = row.item.track.attributes
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dragging) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                            .combinedClickableCompat(onClick = { scope.launch { runCatching { remote.api.queueJump(row.index) } } })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(t?.artwork, 48.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t?.name ?: row.item.track.id, style = Apple.type.body, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t?.artistName.orEmpty(), style = Apple.type.subhead, color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val reorderDescription = stringResource(R.string.action_reorder)
                        Box(
                            Modifier.size(44.dp).draggableHandle(
                                onDragStarted = { dragFrom = row.index },
                                onDragStopped = {
                                    // Los índices originales son consecutivos desde el primero cargado.
                                    val current = rows.orEmpty()
                                    val start = current.minOfOrNull { it.index } ?: 0
                                    val to = current.indexOfFirst { it.key == row.key }.takeIf { it >= 0 }?.let { start + it }
                                    val from = dragFrom
                                    dragFrom = -1
                                    if (to != null && to != from) scope.launch {
                                        runCatching { remote.api.queueMove(from, to) }
                                        reload()
                                    }
                                },
                            ).semantics { contentDescription = reorderDescription; role = Role.Button },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.DragHandle, null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        item(key = "fin") { Box(Modifier.height(16.dp)) }
    }
}

/** Interruptor en cápsula sobre el fondo de color: activo en blanco. */
@Composable
private fun Toggle(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).combinedClickableCompat(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Switch },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 40.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
                .background(if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) Color.Black.copy(alpha = 0.75f) else Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
