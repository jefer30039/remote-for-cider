package cider.remote.app.ui.pairing

import androidx.compose.ui.platform.LocalResources
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cider.remote.CiderEndpoint
import cider.remote.CiderPairing
import cider.remote.PairingResult
import cider.remote.Scope
import cider.remote.app.app
import cider.remote.app.data.SavedConnection
import cider.remote.app.ui.Apple
import cider.remote.app.ui.components.combinedClickableCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Puerto del puente en el PC (puente/cider-puente@.service), no el de Cider. */
private const val BRIDGE_PORT = 10768

private val SCOPES = setOf(Scope.Playback, Scope.Queue, Scope.Library, Scope.Audio)

@Composable
fun PairingScreen(onPaired: (SavedConnection) -> Unit) {
    val http = LocalContext.current.app.http
    // Mensajes que se arman fuera de la composición (en corrutinas).
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(BRIDGE_PORT.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    // Buscar Cider en la red al abrir: rellena la IP si no la has tocado.
    val finder = LocalContext.current.app.pcFinder
    var hostEdited by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    fun scan(force: Boolean) = scope.launch {
        scanning = true
        val found = finder.find(port.toIntOrNull() ?: BRIDGE_PORT, token = null)
        scanning = false
        if (found != null && (force || !hostEdited)) host = found
        else if (found == null && force) message = resources.getString(R.string.pairing_not_found_network)
    }
    LaunchedEffect(Unit) { scan(force = false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val waiting = job?.isActive == true

    fun pair() {
        val endpoint = CiderEndpoint(host, port.toIntOrNull() ?: CiderEndpoint.DEFAULT_PORT)
        message = null
        job = scope.launch {
            val result = CiderPairing(http).request(endpoint, "Remote for Cider (Android)", SCOPES)
            job = null
            message = when (result) {
                is PairingResult.Approved -> { onPaired(SavedConnection(endpoint, result.token)); null }
                PairingResult.Denied -> resources.getString(R.string.pairing_denied)
                PairingResult.TimedOut -> resources.getString(R.string.pairing_timed_out)
                is PairingResult.TryLater -> result.retryAfterSec
                    ?.let { resources.getString(R.string.pairing_try_later_seconds, it) }
                    ?: resources.getString(R.string.pairing_try_later)
                is PairingResult.Failed -> resources.getString(R.string.pairing_failed, host, port)
            }
        }
    }

    // En tablet, contenido centrado con ancho de lectura (HIG layout.md › Adaptability).
    Box(Modifier.fillMaxSize().background(Apple.colors.groupedBackground), contentAlignment = Alignment.TopCenter) { Column(
        Modifier.fillMaxHeight().widthIn(max = 560.dp)
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.pairing_title), style = Apple.type.largeTitle, color = Apple.colors.label)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pairing_intro),
            style = Apple.type.body, color = Apple.colors.secondaryLabel,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (Apple.colors.isDark) Apple.colors.fill else Color.White)) {
            Field(stringResource(R.string.pairing_field_host), host, { host = it.trim(); hostEdited = true }, KeyboardType.Uri, enabled = !waiting)
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Apple.colors.separator))
            Field(stringResource(R.string.pairing_field_port), port, { port = it.filter(Char::isDigit) }, KeyboardType.Number, enabled = !waiting)
        }
        Text(
            stringResource(if (scanning) R.string.pairing_scanning else R.string.pairing_scan),
            style = Apple.type.subhead,
            color = if (scanning) Apple.colors.secondaryLabel else Apple.colors.accentText,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                .combinedClickableCompat(onClick = { if (!scanning && !waiting) { message = null; scan(force = true) } }),
        )
        message?.let {
            Row(Modifier.padding(top = 12.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Apple.colors.secondaryLabel, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(it, style = Apple.type.footnote, color = Apple.colors.label)
            }
        }
        Spacer(Modifier.weight(1f))
        if (waiting) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Apple.colors.secondaryLabel, strokeWidth = 2.dp)
                Text(stringResource(R.string.pairing_waiting), style = Apple.type.subhead, color = Apple.colors.secondaryLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
        val enabled = waiting || (host.isNotBlank() && port.isNotBlank())
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        waiting -> Apple.colors.fill
                        enabled -> if (Apple.colors.isDark) Apple.colors.accent else Apple.colors.accentText
                        else -> Apple.colors.fill
                    },
                )
                .combinedClickableCompat(onClick = { if (waiting) { job?.cancel(); job = null } else if (enabled) pair() }),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (waiting) R.string.action_cancel else R.string.action_connect),
                style = Apple.type.headline,
                color = when {
                    waiting -> Apple.colors.accentText
                    enabled -> Color.White
                    else -> Apple.colors.secondaryLabel
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
    } }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Apple.type.body, color = Apple.colors.label, modifier = Modifier.width(88.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, enabled = enabled,
            textStyle = Apple.type.body.copy(color = if (enabled) Apple.colors.label else Apple.colors.secondaryLabel),
            cursorBrush = SolidColor(Apple.colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.weight(1f),
        )
    }
}
