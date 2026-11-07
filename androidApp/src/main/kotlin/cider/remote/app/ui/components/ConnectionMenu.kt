package cider.remote.app.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cider.remote.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import cider.remote.app.data.UpdateChecker
import cider.remote.app.data.UpdateStatus
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cider.remote.app.app
import cider.remote.app.ui.Apple

/**
 * Botón ⋮ con la conexión: a qué PC está conectado, buscarlo en la red (si cambió
 * de IP), cambiar la dirección a mano conservando el token, u olvidarlo.
 */
@Composable
fun ConnectionMenuButton(host: String, onForget: () -> Unit, backdrop: GlassBackdrop? = null, tint: (() -> Color)? = null) {
    val session = LocalContext.current.app.session
    val searching by session.searching.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val updates = LocalContext.current.app.updates
    val updateStatus by updates.status.collectAsStateWithLifecycle(UpdateStatus(true, 0L, null))
    val uriHandler = LocalUriHandler.current
    Box {
        GlassCircleButton(Icons.Outlined.MoreVert, stringResource(R.string.action_more), onClick = { menu = true },
            tint = tint?.invoke() ?: Apple.colors.label, backdrop = backdrop ?: LocalGlassBackdrop.current)
        GlassMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (searching) stringResource(R.string.connection_searching) else stringResource(R.string.connection_connected_to, host), color = Apple.colors.secondaryLabel) },
                leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = {}, enabled = false,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_find)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                enabled = !searching,
                onClick = { menu = false; session.searchNow() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_change_address)) },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { menu = false; editing = true },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_forget)) },
                leadingIcon = { Icon(Icons.Filled.LinkOff, null) },
                onClick = { menu = false; onForget() },
            )
            updateStatus.update?.let { update ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.update_available, update.version), color = Apple.colors.accent) },
                    leadingIcon = { Icon(Icons.Outlined.SystemUpdate, null, tint = Apple.colors.accent) },
                    onClick = { menu = false; uriHandler.openUri(update.pageUrl) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about_title)) },
                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                onClick = { menu = false; about = true },
            )
        }
    }
    if (editing) HostDialog(host, onDismiss = { editing = false }, onSave = { session.changeHost(it); editing = false })
    if (about) AboutDialog(updates, updateStatus, onDismiss = { about = false })
}

/** Versión, aviso de proyecto no oficial, enlace al código y actualizaciones. */
@Composable
private fun AboutDialog(updates: UpdateChecker, status: UpdateStatus, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    // null = sin resultado todavía; true/false = resultado de "Comprobar ahora"
    var checked by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_version, updates.installedVersion)) },
        text = {
            Column {
                Text(stringResource(R.string.about_disclaimer))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_source),
                    color = Apple.colors.accent,
                    modifier = Modifier.clickable { uriHandler.openUri(UpdateChecker.REPO_URL) },
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_toggle))
                        Text(stringResource(R.string.update_toggle_hint), color = Apple.colors.secondaryLabel, style = Apple.type.footnote)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = status.enabled, onCheckedChange = { scope.launch { updates.setEnabled(it) } })
                }
                if (status.enabled) {
                    Spacer(Modifier.height(12.dp))
                    val update = status.update
                    when {
                        update != null -> Text(
                            stringResource(R.string.update_download, update.version),
                            color = Apple.colors.accent,
                            modifier = Modifier.clickable { uriHandler.openUri(update.pageUrl) },
                        )
                        checked == false -> Text(stringResource(R.string.update_check_failed), color = Apple.colors.secondaryLabel)
                        checked == true -> Text(stringResource(R.string.update_up_to_date), color = Apple.colors.secondaryLabel)
                        else -> Text(
                            stringResource(R.string.update_check_now),
                            color = if (checking) Apple.colors.secondaryLabel else Apple.colors.accent,
                            modifier = Modifier.clickable(enabled = !checking) {
                                checking = true
                                scope.launch { checked = updates.checkNow(); checking = false }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun HostDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_address_title)) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it.trim() }, singleLine = true,
                label = { Text(stringResource(R.string.connection_address_label)) },
                supportingText = { Text(stringResource(R.string.connection_address_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
