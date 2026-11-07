package cider.remote.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Versión publicada en GitHub más nueva que la instalada. */
data class Update(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

/** Estado del comprobador para la interfaz. */
data class UpdateStatus(val enabled: Boolean, val lastCheck: Long, val update: Update?)

private val Context.updateStore by preferencesDataStore("actualizaciones")

/**
 * Nivel 1 de actualizaciones: una vez al día pregunta a GitHub por la última release
 * y, si su etiqueta es mayor que la versión instalada, guarda el aviso. La descarga
 * la hace el usuario desde la página de la release. Se puede desactivar.
 */
class UpdateChecker(private val context: Context, private val http: HttpClient) {
    private val enabled = booleanPreferencesKey("enabled")
    private val lastCheck = longPreferencesKey("lastCheck")
    private val latestVersion = stringPreferencesKey("latestVersion")
    private val latestPage = stringPreferencesKey("latestPage")
    private val latestApk = stringPreferencesKey("latestApk")
    private val latestNotes = stringPreferencesKey("latestNotes")

    val installedVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
    }

    val status: Flow<UpdateStatus> = context.updateStore.data.map { prefs ->
        val version = prefs[latestVersion]
        val page = prefs[latestPage]
        val update = if (version != null && page != null && compareVersions(version, installedVersion) > 0) {
            Update(version, page, prefs[latestApk], prefs[latestNotes] ?: "")
        } else null
        UpdateStatus(prefs[enabled] ?: true, prefs[lastCheck] ?: 0L, update)
    }

    suspend fun setEnabled(value: Boolean) {
        context.updateStore.edit {
            it[enabled] = value
            if (!value) {
                it.remove(latestVersion); it.remove(latestPage); it.remove(latestApk); it.remove(latestNotes)
            }
        }
    }

    /** Comprueba si está activado y la última consulta tiene más de un día. */
    suspend fun checkIfDue() {
        val prefs = context.updateStore.data.first()
        if (prefs[enabled] == false) return
        if (System.currentTimeMillis() - (prefs[lastCheck] ?: 0L) < CHECK_INTERVAL_MS) return
        checkNow()
    }

    /** Consulta GitHub ahora. Devuelve false si la consulta falló (sin red, límite…). */
    suspend fun checkNow(): Boolean {
        val release = runCatching { fetchLatest() }.getOrElse {
            println("UpdateChecker: $it")
            return false
        }
        context.updateStore.edit {
            it[lastCheck] = System.currentTimeMillis()
            it[latestVersion] = release.version
            it[latestPage] = release.pageUrl
            if (release.apkUrl != null) it[latestApk] = release.apkUrl else it.remove(latestApk)
            it[latestNotes] = release.notes
        }
        return true
    }

    private suspend fun fetchLatest(): Update {
        val text = http.get(LATEST_URL) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }.bodyAsText()
        val json = Json.parseToJsonElement(text).jsonObject
        val tag = json.getValue("tag_name").jsonPrimitive.content
        val apk = json["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        return Update(
            version = tag.removePrefix("v"),
            pageUrl = json.getValue("html_url").jsonPrimitive.content,
            apkUrl = apk,
            notes = json["body"]?.jsonPrimitive?.content?.trim() ?: "",
        )
    }

    companion object {
        const val REPO_URL = "https://github.com/jefer30039/remote-for-cider"
        private const val LATEST_URL = "https://api.github.com/repos/jefer30039/remote-for-cider/releases/latest"
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Compara "1.2.10" con "1.2.9" por tramos numéricos; los sufijos no numéricos cuentan como 0. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val pa = parts(a); val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
                if (c != 0) return c
            }
            return 0
        }
    }
}
