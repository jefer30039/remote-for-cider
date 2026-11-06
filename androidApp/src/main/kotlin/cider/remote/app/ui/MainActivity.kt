package cider.remote.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cider.remote.app.app
import cider.remote.app.playback.PlaybackService
import cider.remote.app.session.SessionState
import cider.remote.app.ui.pairing.PairingScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CiderTheme {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalWindowLayout provides WindowLayout(maxWidth, maxHeight)) {
                        Surface(Modifier.fillMaxSize()) { Root() }
                    }
                }
            }
        }
    }

    @Composable
    private fun Root() {
        val session = app.session
        val state by session.state.collectAsStateWithLifecycle()
        when (val s = state) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            SessionState.NotPaired -> PairingScreen(onPaired = session::save)
            is SessionState.Paired -> {
                LaunchedEffect(s.remote) { PlaybackService.start(this@MainActivity) }
                MainScreen(remote = s.remote, connection = s.connection, onForget = session::forget)
            }
        }
    }
}
