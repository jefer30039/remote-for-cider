package cider.remote.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cider.remote.app.ui.Apple
import cider.remote.app.ui.LocalWindowLayout
import cider.remote.model.Artwork
import coil3.compose.AsyncImage

/** Carátula con fondo de relleno mientras carga. */
@Composable
fun ArtworkImage(
    artwork: Artwork?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(if (size >= 120.dp) 10.dp else 6.dp),
    px: Int = 300,
    /** Tamaño menor que suele estar ya en memoria: se ve debajo mientras llega el grande. */
    preview: Int? = null,
) {
    Box(modifier.size(size).clip(shape).background(Apple.colors.fill)) {
        if (preview != null) AsyncImage(
            model = artwork?.sized(preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = artwork?.sized(px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Icono cuyo color se lee al dibujar ([tint]): cambiarlo no recompone ni crea capas
 * (un `graphicsLayer { colorFilter }` abre una capa fuera de pantalla por icono).
 */
@Composable
fun TintedIcon(icon: ImageVector, size: Dp, tint: () -> Color, modifier: Modifier = Modifier) {
    val painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(icon)
    Box(modifier.size(size).drawBehind { with(painter) { draw(this@drawBehind.size, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint())) } })
}

/** Monograma para artistas sin foto (la biblioteca de Cider no la trae). */
@Composable
fun Monogram(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Apple.colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = if (size > 80.dp) Apple.type.largeTitle else Apple.type.title3,
            color = Apple.colors.secondaryLabel,
        )
    }
}

/** Título grande de iOS con botones a la derecha. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = LocalWindowLayout.current.margin, end = LocalWindowLayout.current.margin, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Apple.type.largeTitle, color = Apple.colors.label, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** Botón circular de 44 dp con icono monocromo (como los de la cabecera de Apple Music). */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Apple.colors.circle,
    tint: Color = Apple.colors.label,
    size: Dp = 40.dp,
    border: Color? = Apple.colors.circleBorder,
    iconSize: Dp = size * 0.55f,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background)
            .then(if (border != null) Modifier.border(0.5.dp, border, CircleShape) else Modifier)
            .combinedClickableCompat(onClick = onClick)
            .semantics { this.contentDescription = contentDescription; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Fila de lista: elemento inicial, textos y separador alineado con el texto. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Apple.colors.label,
    leading: (@Composable () -> Unit)? = null,
    leadingWidth: Dp = 0.dp,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    minHeight: Dp = 44.dp,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    dividerColor: Color = Apple.colors.separator,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier.fillMaxWidth()
            .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(start = LocalWindowLayout.current.margin, end = LocalWindowLayout.current.margin - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.width(leadingWidth), contentAlignment = Alignment.CenterStart) { leading() }
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(title, style = titleStyle ?: Apple.type.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(
                    subtitle, style = Apple.type.subhead, color = Apple.colors.secondaryLabel,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) trailing()
        }
        if (showDivider) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = LocalWindowLayout.current.margin + leadingWidth, end = LocalWindowLayout.current.margin)
                    .fillMaxWidth().height(0.5.dp).background(dividerColor),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: (() -> Unit)?, onLongClick: (() -> Unit)? = null): Modifier =
    if (onClick == null && onLongClick == null) this
    else combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)

/**
 * Slider fino de iOS: sin pulgar, la pista se ensancha mientras se arrastra
 * (HIG materials.md: control transitorio). Accesible como barra de progreso ajustable.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    activeColor: Color,
    inactiveColor: Color,
    description: String,
) {
    var dragging by remember { mutableStateOf(false) }
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val thickness by animateDpAsState(if (dragging) 11.dp else 7.dp, label = "grosor")
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                setProgress { change(it.coerceIn(0f, 1f)); finished(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    change((down.position.x / size.width).coerceIn(0f, 1f))
                    drag(down.id) { c ->
                        change((c.position.x / size.width).coerceIn(0f, 1f))
                        c.consume()
                    }
                    dragging = false
                    finished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(inactiveColor)) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(activeColor))
        }
    }
}

/** Botón en píldora de 48 dp (Play/Shuffle de Apple Music). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Apple.colors.fill,
    content: Color = Apple.colors.label,
) {
    Row(
        modifier.height(48.dp).clip(CircleShape).background(background)
            .combinedClickableCompat(onClick = onClick)
            .semantics { role = Role.Button },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Text(text, style = Apple.type.headline, color = content, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Mensaje de estado vacío o de error, centrado. */
@Composable
fun EmptyState(title: String, message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = Apple.type.title3, color = Apple.colors.label)
        if (message != null) Text(message, style = Apple.type.subhead, color = Apple.colors.secondaryLabel)
    }
}
