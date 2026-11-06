package cider.remote.app

import android.app.Application
import cider.remote.app.data.ConnectionStore
import cider.remote.app.data.ContentCache
import cider.remote.app.data.PcFinder
import cider.remote.app.session.RemoteSession
import cider.remote.ciderHttpClient

class CiderRemoteApp : Application() {
    val http by lazy { ciderHttpClient() }
    val connections by lazy { ConnectionStore(this) }
    val contentCache by lazy { ContentCache(this) }
    val pcFinder by lazy { PcFinder(this, http) }
    val session by lazy { RemoteSession(http, connections, contentCache, pcFinder) }
}

val android.content.Context.app: CiderRemoteApp
    get() = applicationContext as CiderRemoteApp
