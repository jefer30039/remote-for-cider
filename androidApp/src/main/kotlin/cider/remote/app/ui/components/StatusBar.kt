package cider.remote.app.ui.components

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Peticiones de iconos claros activas. Con un contador y no con "guardar y restaurar el
 * anterior" cada pantalla: si la página de álbum o de artista de debajo se desmontaba con la
 * hoja de reproducción abierta, restauraba su valor anterior (iconos oscuros) encima de la hoja.
 */
private object LightIconRequests {
    var count = 0
    /** Lo que había antes de la primera petición (lo que pone el tema). */
    var baseline = false
}

/** Iconos claros en la barra de estado mientras esté en pantalla (fondos siempre oscuros). */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (LightIconRequests.count == 0) LightIconRequests.baseline = controller.isAppearanceLightStatusBars
        LightIconRequests.count++
        controller.isAppearanceLightStatusBars = false
        // Al volver a primer plano el sistema puede reponer el estilo del tema: se reafirma.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && LightIconRequests.count > 0) controller.isAppearanceLightStatusBars = false
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            LightIconRequests.count = (LightIconRequests.count - 1).coerceAtLeast(0)
            controller.isAppearanceLightStatusBars = if (LightIconRequests.count > 0) false else LightIconRequests.baseline
        }
    }
}
