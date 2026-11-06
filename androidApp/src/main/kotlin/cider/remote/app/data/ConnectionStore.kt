package cider.remote.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cider.remote.CiderEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Conexión guardada: a qué Cider apuntar y con qué token. */
data class SavedConnection(val endpoint: CiderEndpoint, val token: String)

private val Context.dataStore by preferencesDataStore("conexion")

// TODO: cifrar el token con Android Keystore antes de publicar.
class ConnectionStore(private val context: Context) {
    private val host = stringPreferencesKey("host")
    private val port = intPreferencesKey("port")
    private val token = stringPreferencesKey("token")

    val connection: Flow<SavedConnection?> = context.dataStore.data.map { prefs ->
        val h = prefs[host] ?: return@map null
        val t = prefs[token] ?: return@map null
        SavedConnection(CiderEndpoint(h, prefs[port] ?: CiderEndpoint.DEFAULT_PORT), t)
    }

    suspend fun save(connection: SavedConnection) {
        context.dataStore.edit {
            it[host] = connection.endpoint.host
            it[port] = connection.endpoint.port
            it[token] = connection.token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
