package cider.remote.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * La capa de la página ([GlassBackdrop]) como fuente para la barra de pestañas de
 * Kyant. Antes la página se grababa dos veces por fotograma (`glassSource` y
 * `layerBackdrop`); así las piezas de vidrio propias y la barra leen la misma capa.
 * Hace lo mismo que `LayerBackdrop` de Kyant: deshace la escala de [layerBlock]
 * (la barra crece al tocarla) y desplaza la capa a la posición de la pieza.
 */
@Stable
class SharedBackdrop internal constructor(private val glass: GlassBackdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    private val probe = ScaleProbe()

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val c = coordinates ?: return
        if (!c.isAttached) return
        // En pantalla, como glass.origin: así también vale desde otra ventana (menús emergentes).
        val offset = c.positionOnScreen() - glass.origin
        withTransform({
            if (layerBlock != null) {
                probe.prepare(size, density)
                probe.layerBlock()
                if (probe.scaleX != 0f && probe.scaleY != 0f) scale(1f / probe.scaleX, 1f / probe.scaleY, Offset.Zero)
            }
            translate(-offset.x, -offset.y)
        }) { drawLayer(glass.layer) }
    }
}

@Composable
fun rememberSharedBackdrop(glass: GlassBackdrop): Backdrop = remember(glass) { SharedBackdrop(glass) }

/** Ejecuta un `layerBlock` solo para leer la escala que aplica. */
private class ScaleProbe : GraphicsLayerScope {
    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    fun prepare(size: Size, density: Density) {
        this.size = size; this.density = density.density; fontScale = density.fontScale
        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f; rotationZ = 0f
    }
}
