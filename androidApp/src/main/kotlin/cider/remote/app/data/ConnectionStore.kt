package cider.remote.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cider.remote.CiderEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Conexión guardada: a qué Cider apuntar y con qué token. */
data class SavedConnection(val endpoint: CiderEndpoint, val token: String)

private val Context.dataStore by preferencesDataStore("conexion")

/**
 * Dirección del PC y token de Cider. El token va cifrado con una clave AES-GCM de Android
 * Keystore (no sale del almacén seguro del teléfono ni viaja en las copias de seguridad); el que
 * quedó de versiones anteriores en claro se cifra la primera vez que se lee ([migrate]).
 */
class ConnectionStore(private val context: Context) {
    private val host = stringPreferencesKey("host")
    private val port = intPreferencesKey("port")
    /** Versiones anteriores: el token en claro. */
    private val legacyToken = stringPreferencesKey("token")
    private val sealedToken = stringPreferencesKey("token_cifrado")

    val connection: Flow<SavedConnection?> = context.dataStore.data.map { prefs ->
        val h = prefs[host] ?: return@map null
        val t = prefs[sealedToken]?.let { open(it) } ?: prefs[legacyToken] ?: return@map null
        SavedConnection(CiderEndpoint(h, prefs[port] ?: CiderEndpoint.DEFAULT_PORT), t)
    }

    suspend fun save(connection: SavedConnection) {
        val sealed = seal(connection.token)
        context.dataStore.edit {
            it[host] = connection.endpoint.host
            it[port] = connection.endpoint.port
            if (sealed != null) { it[sealedToken] = sealed; it.remove(legacyToken) } else it[legacyToken] = connection.token
        }
    }

    /** Cifra el token que quedó en claro de versiones anteriores (sin perder el emparejamiento). */
    suspend fun migrate() {
        context.dataStore.edit { prefs ->
            val plain = prefs[legacyToken] ?: return@edit
            val sealed = seal(plain) ?: return@edit
            prefs[sealedToken] = sealed
            prefs.remove(legacyToken)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    /** IV + texto cifrado en Base64; null si el almacén seguro falla (entonces se guarda como antes). */
    private fun seal(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(out, Base64.NO_WRAP)
    }.getOrNull()

    private fun open(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        }
        String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "token_cider"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
