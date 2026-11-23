package cider.remote.app

import android.app.Application
import cider.remote.app.data.ConnectionStore
import cider.remote.app.data.ContentCache
import cider.remote.app.data.PcFinder
import cider.remote.app.data.UpdateChecker
import cider.remote.app.session.RemoteSession
import cider.remote.ciderHttpClient
import kotlinx.coroutines.launch

class CiderRemoteApp : Application() {
    val http by lazy { ciderHttpClient() }
    val connections by lazy { ConnectionStore(this) }
    val contentCache by lazy { ContentCache(this) }
    val pcFinder by lazy { PcFinder(this, http) }
    val updates by lazy { UpdateChecker(this, http) }
    val installer by lazy { cider.remote.app.data.UpdateInstaller(this, http) }
    val session by lazy { RemoteSession(http, connections, contentCache, pcFinder) }

    override fun onCreate() {
        super.onCreate()
        cider.remote.app.ui.Appearance.init(this)
        // El token de versiones anteriores estaba en claro: se cifra con Keystore (ConnectionStore).
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { connections.migrate() } }
    }
}

val android.content.Context.app: CiderRemoteApp
    get() = applicationContext as CiderRemoteApp
