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
    var updating by remember { mutableStateOf<cider.remote.app.data.Update?>(null) }
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
                    onClick = { menu = false; updating = update },
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
    if (about) AboutDialog(updates, updateStatus, onDismiss = { about = false }, onUpdate = { about = false; updating = it })
    updating?.let { UpdateDialog(it, onDismiss = { updating = null }) }
}

/** Versión, aviso de proyecto no oficial, enlace al código y actualizaciones. */
@Composable
private fun AboutDialog(updates: UpdateChecker, status: UpdateStatus, onDismiss: () -> Unit, onUpdate: (cider.remote.app.data.Update) -> Unit) {
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
                    cider.remote.app.ui.liquid.LiquidToggle(checked = status.enabled, onCheckedChange = { scope.launch { updates.setEnabled(it) } })
                }
                if (status.enabled) {
                    Spacer(Modifier.height(12.dp))
                    val update = status.update
                    when {
                        update != null -> Text(
                            stringResource(R.string.update_download, update.version),
                            color = Apple.colors.accent,
                            modifier = Modifier.clickable { onUpdate(update) },
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

/**
 * Actualización desde la app: notas de la versión, descarga con progreso e instalador de Android
 * (UpdateInstaller). Si Android no deja instalar desde la app, lleva al ajuste; siempre queda el
 * enlace a la página de la release.
 */
/** Primeras viñetas de las notas de la release en el idioma pedido (inglés antes de "## Novedades", español después). */
internal fun releaseNotes(body: String, spanish: Boolean, max: Int = 6): String {
    val lines = body.lines()
    val es = lines.indexOfFirst { it.trim().startsWith("## Novedades") }
    val part = when {
        es < 0 -> lines
        spanish -> lines.drop(es)
        else -> lines.take(es)
    }
    // De cada viñeta, su titular en negrita ("**Temporizador para dormir.** …") o, si no tiene, la primera frase.
    return part.filter { it.startsWith("- ") }.take(max).joinToString("\n") { line ->
        val text = line.removePrefix("- ").replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        val lead = Regex("""^\*\*(.+?)\*\*""").find(text)?.groupValues?.get(1)
            ?: text.replace("**", "").let { t -> t.indexOf(". ").takeIf { it > 0 }?.let { t.substring(0, it + 1) } ?: t }
        "• $lead"
    }
}

@Composable
private fun UpdateDialog(update: cider.remote.app.data.Update, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installer = context.app.installer
    val state by installer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { if (state !is cider.remote.app.data.InstallState.Downloading) { installer.reset(); onDismiss() } },
        title = { Text(stringResource(R.string.update_title, update.version)) },
        text = {
            Column {
                // Las notas de la release traen primero el inglés y desde "## Novedades" el español: se enseñan las
                // primeras viñetas del idioma de la app.
                val spanish = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "es"
                val notes = releaseNotes(update.notes, spanish)
                if (notes.isNotBlank()) Text(notes, style = Apple.type.footnote, color = Apple.colors.secondaryLabel)
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is cider.remote.app.data.InstallState.Downloading -> {
                        Text(stringResource(R.string.update_downloading), style = Apple.type.subhead)
                        Spacer(Modifier.height(8.dp))
                        if (s.progress != null) androidx.compose.material3.LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    cider.remote.app.data.InstallState.NeedsPermission -> Text(stringResource(R.string.update_needs_permission), style = Apple.type.subhead)
                    is cider.remote.app.data.InstallState.Failed -> Text(stringResource(R.string.update_failed), style = Apple.type.subhead, color = Apple.colors.secondaryLabel)
                    cider.remote.app.data.InstallState.Ready -> Text(stringResource(R.string.update_ready), style = Apple.type.subhead)
                    cider.remote.app.data.InstallState.Idle -> Unit
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.update_open_page), color = Apple.colors.accent, style = Apple.type.subhead,
                    modifier = Modifier.clickable { uriHandler.openUri(update.pageUrl) },
                )
            }
        },
        confirmButton = {
            val apk = update.apkUrl
            when (state) {
                is cider.remote.app.data.InstallState.Downloading -> Unit
                cider.remote.app.data.InstallState.NeedsPermission -> TextButton(onClick = { installer.reset(); installer.openPermissionSettings() }) {
                    Text(stringResource(R.string.update_allow))
                }
                cider.remote.app.data.InstallState.Ready -> TextButton(onClick = { installer.install() }) { Text(stringResource(R.string.update_install)) }
                else -> if (apk != null) TextButton(onClick = { scope.launch { installer.downloadAndInstall(apk) } }) {
                    Text(stringResource(R.string.update_install))
                }
            }
        },
        dismissButton = {
            if (state !is cider.remote.app.data.InstallState.Downloading) TextButton(onClick = { installer.reset(); onDismiss() }) { Text(stringResource(R.string.action_close)) }
        },
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
