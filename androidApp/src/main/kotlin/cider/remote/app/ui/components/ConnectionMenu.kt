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
        }
    }
    if (editing) HostDialog(host, onDismiss = { editing = false }, onSave = { session.changeHost(it); editing = false })
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
