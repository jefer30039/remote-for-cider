package cider.remote.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cider.remote.app.R
import cider.remote.app.ui.Appearance
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LookMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Aviso del modo de la app ([Appearance]), con el diseño del diálogo de la demo de Kyant (AndroidLiquidGlass,
 * `DialogContent`, Apache 2.0): tarjeta de vidrio de esquinas de 48 dp sobre la página oscurecida, título, texto
 * y dos cápsulas. La primera vez (tras emparejar o al actualizar) dice qué modo se activó para el dispositivo;
 * "Cambiar" pasa al otro **en vivo** (la propia tarjeta deja de ser de vidrio en rendimiento) y "Aceptar" lo
 * guarda. Desde el menú ⋮ › Rendimiento es el mismo; salir sin aceptar deja el modo que había.
 */
@Composable
fun AppearanceDialog(backdrop: Backdrop?) {
    val context = LocalContext.current
    val firstRun = remember { !Appearance.chosen }
    val before = remember { Appearance.mode }
    val mode = Appearance.mode
    val lite = mode == LookMode.Performance
    val dark = Apple.colors.isDark
    val content = if (dark) Color.White else Color.Black
    val accent = Apple.colors.accent
    val container = if (dark) Color(0xFF121212).copy(0.4f) else Color(0xFFFAFAFA).copy(0.6f)
    val dim = if (dark) Color(0xFF121212).copy(0.56f) else Color(0xFF29293A).copy(0.23f)
    val accept = { Appearance.choose(context, mode) }
    // La primera vez, salir es quedarse con el modo que se ve; desde el menú, volver al que había.
    val dismiss = { if (firstRun) accept() else { Appearance.preview(before); Appearance.chooserOpen = false } }
    BackHandler { dismiss() }
    val shape = RoundedCornerShape(48.dp)
    Box(
        Modifier.fillMaxSize().background(dim).pointerInput(Unit) { detectTapGestures { dismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(40.dp)
                .then(
                    // Lisa y opaca: sin desenfoque, lo de detrás se leía a través de la tarjeta.
                    if (lite || backdrop == null) Modifier.shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f))
                        .clip(shape).background(if (dark) Color(0xFF1C1C1E) else Color(0xFFF9F9F9))
                    else Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(48.dp) },
                        effects = {
                            vibrancy()
                            blur(if (dark) 8.dp.toPx() else 16.dp.toPx())
                            lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(container) },
                    ),
                )
                .fillMaxWidth()
                // Los toques dentro de la tarjeta no la cierran.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            BasicText(
                stringResource(if (lite) R.string.look_performance_on else R.string.look_full_on),
                Modifier.padding(28.dp, 24.dp, 28.dp, 12.dp),
                style = Apple.type.body.copy(color = content, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium),
            )
            val suggested = if (mode == Appearance.suggested) stringResource(R.string.look_suggested_this)
            else stringResource(R.string.look_suggested_other, stringResource(if (Appearance.suggested == LookMode.Performance) R.string.look_performance else R.string.look_full))
            BasicText(
                stringResource(if (lite) R.string.look_performance_body else R.string.look_full_body) + "\n\n" +
                    suggested + " " + stringResource(R.string.look_change_later),
                // Como la demo: en oscuro el texto se suma a lo de detrás ("plus lighter").
                Modifier.then(if (dark && !lite) Modifier.graphicsLayer(blendMode = BlendMode.Plus) else Modifier)
                    .padding(28.dp, 0.dp, 28.dp, 12.dp),
                style = Apple.type.body.copy(color = content.copy(alpha = 0.68f), fontSize = 15.sp, lineHeight = 20.sp),
            )
            Row(
                Modifier.padding(24.dp, 12.dp, 24.dp, 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogCapsule(stringResource(R.string.look_change), content, if (lite) content.copy(alpha = 0.08f) else container.copy(alpha = 0.2f)) {
                    Appearance.preview(if (lite) LookMode.Full else LookMode.Performance)
                }
                DialogCapsule(stringResource(R.string.look_accept), Color.White, accent) { accept() }
            }
        }
    }
}

@Composable
private fun RowScope.DialogCapsule(label: String, color: Color, background: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(Capsule()).background(background)
            .combinedClickableCompat(onClick = onClick)
            .semantics { role = Role.Button }
            .height(48.dp).weight(1f).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Apple.type.body.copy(color = color, fontSize = 16.sp), maxLines = 1)
    }
}
