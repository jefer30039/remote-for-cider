package cider.remote.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import cider.remote.CiderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché de contenido de Apple Music (pins, recientes, inicio) por PC.
 * Se muestra lo guardado al instante y se refresca en segundo plano.
 * Memoria para la sesión y JSON en disco para los arranques en frío.
 */
class ContentCache(context: Context) {
    private val root = File(context.filesDir, "cache")
    private val memory = ConcurrentHashMap<String, Any>()

    private fun file(scope: String, key: String) =
        File(File(root, scope.replace(Regex("[^A-Za-z0-9._-]"), "_")), "$key.json")

    @Suppress("UNCHECKED_CAST")
    fun <T> peek(scope: String, key: String): T? = memory["$scope/$key"] as T?

    suspend fun <T : Any> read(scope: String, key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        peek<T>(scope, key) ?: runCatching {
            CiderJson.decodeFromString(serializer, file(scope, key).readText())
        }.getOrNull()?.also { memory["$scope/$key"] = it }
    }

    suspend fun <T : Any> write(scope: String, key: String, serializer: KSerializer<T>, value: T) {
        memory["$scope/$key"] = value
        withContext(Dispatchers.IO) {
            runCatching {
                val f = file(scope, key)
                f.parentFile?.mkdirs()
                // Escritura atómica: un archivo a medias no debe romper el próximo arranque.
                val tmp = File(f.parentFile, "${f.name}.tmp")
                tmp.writeText(CiderJson.encodeToString(serializer, value))
                tmp.renameTo(f)
            }
        }
    }

    /** Borra lo guardado de un PC (al olvidarlo). */
    fun clear(scope: String) {
        memory.keys.removeIf { it.startsWith("$scope/") }
        file(scope, "x").parentFile?.deleteRecursively()
    }
}

/**
 * Valor con caché: empieza con lo guardado (memoria o disco) y lo sustituye
 * por [load] cuando llega. Si [load] falla, se queda lo guardado.
 */
@Composable
fun <T : Any> ContentCache.rememberCached(
    scope: String,
    key: String,
    serializer: KSerializer<T>,
    load: suspend () -> T,
): State<T?> = produceState(peek<T>(scope, key), scope, key) {
    // produceState solo usa el valor inicial la primera vez: con otra clave hay que releerlo.
    value = peek<T>(scope, key) ?: read(scope, key, serializer)
    runCatching { load() }.onSuccess {
        value = it
        write(scope, key, serializer, it)
    }
}
