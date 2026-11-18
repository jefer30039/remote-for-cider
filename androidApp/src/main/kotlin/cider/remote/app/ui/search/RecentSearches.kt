package cider.remote.app.ui.search

import android.content.Context
import cider.remote.model.AmItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * "Recientes" de Buscar (Apple Music): lo último que se abrió o se puso desde los resultados, del
 * más nuevo al más viejo, como mucho [MAX]. Se guarda en el teléfono (SharedPreferences, JSON).
 */
object RecentSearches {
    private const val PREFS = "busquedas"
    private const val KEY = "recientes"
    private const val MAX = 12
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(AmItem.serializer())

    private val _items = MutableStateFlow<List<AmItem>>(emptyList())
    val items: StateFlow<List<AmItem>> = _items.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        _items.value = runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
    }

    fun add(context: Context, item: AmItem) {
        // Sin relaciones (pesan y no hacen falta para la fila).
        val slim = item.copy(relationships = null)
        _items.value = (listOf(slim) + _items.value.filterNot { it.id == item.id && it.type == item.type }).take(MAX)
        save(context)
    }

    fun clear(context: Context) {
        _items.value = emptyList()
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(serializer, _items.value)).apply()
    }
}
