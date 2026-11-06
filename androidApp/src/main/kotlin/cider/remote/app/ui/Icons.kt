package cider.remote.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Iconos que Material no tiene, dibujados como los SF Symbols que usa Apple Music. */
object AppleIcons {
    /** `square.stack`: álbumes. */
    val Albums: ImageVector by lazy {
        ImageVector.Builder("Albums", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Caja delantera con esquinas redondeadas.
                moveTo(7f, 9f); lineTo(17f, 9f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); lineTo(19f, 19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); lineTo(7f, 21f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); lineTo(5f, 11f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
                // Las dos tapas de detrás.
                moveTo(6.5f, 6.2f); lineTo(17.5f, 6.2f)
                moveTo(8.2f, 3.4f); lineTo(15.8f, 3.4f)
            }
        }.build()
    }

    /** `quote.bubble`: letra. */
    val Lyrics: ImageVector by lazy {
        ImageVector.Builder("Lyrics", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6f, 3.5f); lineTo(18f, 3.5f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, 3f); lineTo(21f, 14f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, 3f); lineTo(11f, 17f)
                lineTo(6.5f, 20.8f); lineTo(6.5f, 17f); lineTo(6f, 17f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, -3f); lineTo(3f, 6.5f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, -3f); close()
            }
            // Comillas "99": un punto relleno con la cola curvada hacia abajo a la izquierda.
            val quotes = listOf(9.2f, 14.8f)
            path(fill = SolidColor(Color.Black)) {
                quotes.forEach { cx ->
                    moveTo(cx - 1.8f, 9.3f)
                    arcToRelative(1.8f, 1.8f, 0f, true, true, 3.6f, 0f)
                    arcToRelative(1.8f, 1.8f, 0f, true, true, -3.6f, 0f)
                    close()
                }
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                quotes.forEach { cx ->
                    moveTo(cx + 1.65f, 9.5f)
                    quadTo(cx + 1.6f, 12.4f, cx - 1.1f, 13.4f)
                }
            }
        }.build()
    }

    /** `music.mic`: micrófono de mano en diagonal (artistas). */
    val Mic: ImageVector by lazy {
        ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Cabeza.
                moveTo(15.5f, 3.2f)
                arcToRelative(5.2f, 5.2f, 0f, true, true, 0f, 10.4f)
                arcToRelative(5.2f, 5.2f, 0f, true, true, 0f, -10.4f); close()
                // Cuerpo hacia abajo a la izquierda.
                moveTo(11.6f, 11.2f); lineTo(4.2f, 19.4f)
                lineTo(5.6f, 20.8f); lineTo(13.4f, 13.1f)
                // Cable.
                moveTo(4.9f, 20.1f); lineTo(3.2f, 21.8f)
            }
        }.build()
    }

    /** `text.line.first.and.arrowtriangle.forward`: reproducir a continuación (flecha arriba). */
    val PlayNext: ImageVector by lazy { queueIcon(arrowAtTop = true) }

    /** `text.line.last.and.arrowtriangle.forward`: reproducir al final (flecha abajo). */
    val PlayLast: ImageVector by lazy { queueIcon(arrowAtTop = false) }

    private fun queueIcon(arrowAtTop: Boolean) =
        ImageVector.Builder(if (arrowAtTop) "PlayNext" else "PlayLast", 24.dp, 24.dp, 24f, 24f).apply {
            val ys = listOf(5f, 9.7f, 14.3f, 19f)
            val arrowY = if (arrowAtTop) ys.first() else ys.last()
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                ys.forEach { y ->
                    // La línea de la flecha empieza más a la derecha para dejarle sitio.
                    moveTo(if (y == arrowY) 9f else 6f, y); lineTo(20.5f, y)
                }
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.8f, arrowY - 2.6f); lineTo(6.6f, arrowY); lineTo(2.8f, arrowY + 2.6f); close()
            }
        }.build()

    // Controles de reproducción como los SF Symbols rellenos: triángulos y barras con
    // las esquinas redondeadas (relleno + trazo con unión redonda del mismo color).
    private fun transport(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.6f, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    /** `play.fill` */
    val Play: ImageVector by lazy {
        transport("Play") { moveTo(6.5f, 3.6f); lineTo(20.6f, 12f); lineTo(6.5f, 20.4f); close() }
    }

    /** `pause.fill`: dos barras de esquinas redondeadas. */
    val Pause: ImageVector by lazy {
        ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                fun bar(x: Float) {
                    moveTo(x + 1.6f, 2f); lineTo(x + 4.4f, 2f)
                    arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, 1.6f); lineTo(x + 6f, 20.4f)
                    arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, 1.6f); lineTo(x + 1.6f, 22f)
                    arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, -1.6f); lineTo(x, 3.6f)
                    arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, -1.6f); close()
                }
                bar(4.5f); bar(13.5f)
            }
        }.build()
    }

    /** `forward.fill`: dos triángulos seguidos. */
    val Forward: ImageVector by lazy {
        transport("Forward") {
            moveTo(2.3f, 5.6f); lineTo(11.6f, 12f); lineTo(2.3f, 18.4f); close()
            moveTo(12.4f, 5.6f); lineTo(21.7f, 12f); lineTo(12.4f, 18.4f); close()
        }
    }

    /** `backward.fill` */
    val Backward: ImageVector by lazy {
        transport("Backward") {
            moveTo(21.7f, 5.6f); lineTo(12.4f, 12f); lineTo(21.7f, 18.4f); close()
            moveTo(11.6f, 5.6f); lineTo(2.3f, 12f); lineTo(11.6f, 18.4f); close()
        }
    }

    // Pestañas, como las de Apple Music (SF Symbols rellenos).
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    /** `house.fill`: la puerta es un hueco abierto por abajo. */
    val TabHome: ImageVector by lazy {
        icon("TabHome") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.9f, 3.4f); quadTo(12f, 2.5f, 13.1f, 3.4f)
                lineTo(20.4f, 9.4f); quadTo(21.1f, 10f, 21.1f, 10.9f)
                lineTo(21.1f, 19f); quadTo(21.1f, 20.9f, 19.2f, 20.9f)
                lineTo(14.7f, 20.9f); lineTo(14.7f, 15.8f); quadTo(14.7f, 14.5f, 13.4f, 14.5f)
                lineTo(10.6f, 14.5f); quadTo(9.3f, 14.5f, 9.3f, 15.8f); lineTo(9.3f, 20.9f)
                lineTo(4.8f, 20.9f); quadTo(2.9f, 20.9f, 2.9f, 19f)
                lineTo(2.9f, 10.9f); quadTo(2.9f, 10f, 3.6f, 9.4f); close()
            }
        }
    }

    /** `square.grid.2x2.fill` */
    val TabNew: ImageVector by lazy {
        icon("TabNew") {
            path(fill = SolidColor(Color.Black)) {
                for (x in listOf(2.8f, 13.2f)) for (y in listOf(2.8f, 13.2f)) {
                    val e = 8f; val r = 2.2f
                    moveTo(x + r, y); lineTo(x + e - r, y); quadTo(x + e, y, x + e, y + r)
                    lineTo(x + e, y + e - r); quadTo(x + e, y + e, x + e - r, y + e)
                    lineTo(x + r, y + e); quadTo(x, y + e, x, y + e - r)
                    lineTo(x, y + r); quadTo(x, y, x + r, y); close()
                }
            }
        }
    }

    /** `dot.radiowaves.left.and.right` */
    val TabRadio: ImageVector by lazy {
        icon("TabRadio") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.9f, 12f); arcToRelative(2.1f, 2.1f, 0f, true, true, 4.2f, 0f); arcToRelative(2.1f, 2.1f, 0f, true, true, -4.2f, 0f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(15.5f, 8.5f); arcTo(5f, 5f, 0f, false, true, 15.5f, 15.5f)
                moveTo(8.5f, 8.5f); arcTo(5f, 5f, 0f, false, false, 8.5f, 15.5f)
                moveTo(18f, 5.4f); arcTo(8.9f, 8.9f, 0f, false, true, 18f, 18.6f)
                moveTo(6f, 5.4f); arcTo(8.9f, 8.9f, 0f, false, false, 6f, 18.6f)
            }
        }
    }

    /** Biblioteca: pila de cuadros con una nota recortada en el de delante. */
    val TabLibrary: ImageVector by lazy {
        icon("TabLibrary") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
                moveTo(6.6f, 5.9f); lineTo(17.4f, 5.9f)
                moveTo(8.6f, 2.9f); lineTo(15.4f, 2.9f)
            }
            path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
                // Cuadro de delante.
                moveTo(6.4f, 8.6f); lineTo(17.6f, 8.6f); quadTo(20f, 8.6f, 20f, 11f)
                lineTo(20f, 19.2f); quadTo(20f, 21.6f, 17.6f, 21.6f)
                lineTo(6.4f, 21.6f); quadTo(4f, 21.6f, 4f, 19.2f)
                lineTo(4f, 11f); quadTo(4f, 8.6f, 6.4f, 8.6f); close()
                // Nota: cabeza, plica y bandera sin solaparse (si no, el par/impar las rellenaría).
                moveTo(9f, 17.6f); arcToRelative(1.95f, 1.95f, 0f, true, true, 3.9f, 0f); arcToRelative(1.95f, 1.95f, 0f, true, true, -3.9f, 0f); close()
                moveTo(12.9f, 17.6f); lineTo(12.9f, 11.6f); lineTo(14.2f, 11.6f); lineTo(14.2f, 17.6f); close()
                moveTo(14.2f, 11.6f); lineTo(16.6f, 11f); lineTo(16.6f, 12.7f); lineTo(14.2f, 13.3f); close()
            }
        }
    }

    /** `magnifyingglass` */
    val TabSearch: ImageVector by lazy {
        icon("TabSearch") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round) {
                moveTo(4.1f, 10.4f); arcToRelative(6.3f, 6.3f, 0f, true, true, 12.6f, 0f); arcToRelative(6.3f, 6.3f, 0f, true, true, -12.6f, 0f); close()
                moveTo(15.1f, 15.1f); lineTo(20.4f, 20.4f)
            }
        }
    }
}
