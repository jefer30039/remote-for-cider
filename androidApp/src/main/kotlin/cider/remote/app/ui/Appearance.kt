package cider.remote.app.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Cómo se ve la app: **completo** (vidrio líquido y desenfoques, como se diseñó) o **rendimiento** (superficies
 * lisas y sin desenfoques: es lo que más cuesta dibujar, ver "Rendimiento" en las notas del proyecto).
 */
enum class LookMode { Full, Performance }

/**
 * Modo actual, leído desde cualquier sitio: en composición (recompone al cambiar) o al dibujar (redibuja).
 * La primera vez que se abre la app (también al actualizar a la versión que lo trae) no hay modo elegido:
 * se usa el sugerido para el dispositivo y MainScreen enseña [AppearanceChooser] para que el usuario
 * sepa que hay dos y elija.
 */
object Appearance {
    private const val Prefs = "apariencia"
    private const val Key = "modo"

    var mode by mutableStateOf(LookMode.Full)
        private set
    /** Falso hasta que el usuario elige: entonces se enseña el selector. */
    var chosen by mutableStateOf(true)
        private set
    /** Selector abierto desde el menú ⋮ (la primera vez lo abre [chosen] = falso). */
    var chooserOpen by mutableStateOf(false)
    var suggested = LookMode.Full
        private set

    val lite: Boolean get() = mode == LookMode.Performance

    fun init(context: Context) {
        suggested = suggest(context)
        val saved = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(Key, null)
        val parsed = saved?.let { runCatching { LookMode.valueOf(it) }.getOrNull() }
        mode = parsed ?: suggested
        chosen = parsed != null
    }

    /** Aplica sin guardar (el selector enseña el modo en vivo mientras se elige). */
    fun preview(m: LookMode) { mode = m }

    fun choose(context: Context, m: LookMode) {
        mode = m
        chosen = true
        chooserOpen = false
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putString(Key, m.name).apply()
    }

    /**
     * Rendimiento si el dispositivo es modesto: antes de Android 13 (sin shaders AGSL, el vidrio va con otro
     * desenfoque), poca memoria, o un núcleo principal por debajo de ~2,75 GHz (gama media: Snapdragon 7s/
     * Exynos 1380/Dimensity 7000…). El POCO F7 (8s Gen 4, 3,2 GHz) y la Xiaomi Pad 7 Pro (8s Gen 3, 3,0 GHz),
     * en los que se diseñó, quedan en completo; aun así ahí el vidrio ya es el suelo de lo que cuesta un fotograma.
     */
    fun suggest(context: Context): LookMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LookMode.Performance
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            if (am.isLowRamDevice) return LookMode.Performance
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            // Los de 8 GB declaran ~7,3 GB.
            if (info.totalMem in 1 until 7_000_000_000L) return LookMode.Performance
        }
        val maxKhz = maxCpuKhz()
        if (maxKhz in 1 until 2_750_000) return LookMode.Performance
        return LookMode.Full
    }

    private fun maxCpuKhz(): Long = runCatching {
        java.io.File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty()
            .maxOfOrNull { java.io.File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() ?: 0L } ?: 0L
    }.getOrDefault(0L)
}
