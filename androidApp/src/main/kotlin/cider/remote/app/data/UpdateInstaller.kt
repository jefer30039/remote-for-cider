package cider.remote.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Estado de la descarga de una actualización. */
sealed interface InstallState {
    data object Idle : InstallState
    /** [progress] 0–1, o null si GitHub no dice el tamaño. */
    data class Downloading(val progress: Float?) : InstallState
    /** Descargada; falta que el usuario acepte en el instalador de Android. */
    data object Ready : InstallState
    /** Android no deja instalar apps de esta fuente: hay que permitirlo en Ajustes. */
    data object NeedsPermission : InstallState
    data class Failed(val message: String?) : InstallState
}

/**
 * Nivel 2 de actualizaciones: descarga el APK de la release de GitHub a la caché y lo abre con el
 * instalador de Android (FileProvider). Android comprueba que la firma sea la misma que la de la app
 * instalada, así que no se puede colar otro APK. Requiere el permiso "Instalar apps desconocidas".
 */
class UpdateInstaller(private val context: Context, private val http: HttpClient) {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val file get() = File(context.cacheDir, "actualizacion/remote-for-cider.apk")

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Ajustes > Instalar apps desconocidas, para esta app. */
    fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun downloadAndInstall(apkUrl: String) {
        if (!canInstall()) { _state.value = InstallState.NeedsPermission; return }
        _state.value = InstallState.Downloading(0f)
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                val target = file.also { it.parentFile?.mkdirs(); it.delete() }
                http.prepareGet(apkUrl) { timeout { requestTimeoutMillis = 300_000 } }.execute { response ->
                    if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
                    val total = response.contentLength()
                    val channel: io.ktor.utils.io.ByteReadChannel = response.body()
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    target.outputStream().use { out ->
                        while (true) {
                            val n = channel.readAvailable(buffer, 0, buffer.size)
                            if (n < 0) break
                            if (n == 0) { if (channel.isClosedForRead) break else continue }
                            out.write(buffer, 0, n)
                            read += n
                            _state.value = InstallState.Downloading(total?.takeIf { it > 0 }?.let { read.toFloat() / it })
                        }
                    }
                }
            }
        }
        ok.onFailure { _state.value = InstallState.Failed(it.message); return }
        _state.value = InstallState.Ready
        install()
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install() {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { _state.value = InstallState.Failed(it.message) }
    }

    fun reset() { _state.value = InstallState.Idle }
}
