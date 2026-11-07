package cider.remote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cider.remote.app.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Tokens medidos sobre capturas de Apple Music para Android (HIG: color.md, typography.md).
 * Contrastes (WCAG):
 *  - accent #0090FC / negro 6.39:1, / barra #262626 4.61:1. Claro: #0070D0 / blanco 4.97:1.
 *  - secondaryLabel #98989F / negro 7.33:1; #6C6C70 / blanco 5.23:1.
 */
@Immutable
data class AppleColors(
    val background: Color,
    val groupedBackground: Color,
    /** Relleno de campos y botones en píldora (#1C1C1E en Apple Music). */
    val fill: Color,
    /** Relleno de los botones circulares de cabecera. */
    val circle: Color,
    val circleBorder: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val accentText: Color,
    val favorite: Color,
    /** Barra de pestañas y miniplayer flotantes. */
    val bar: Color,
    val miniPlayer: Color,
    val isDark: Boolean,
)

private val Light = AppleColors(
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    fill = Color(0xFFEFEFF0),
    circle = Color(0xFFF2F2F2),
    circleBorder = Color(0xFFE2E2E2),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF6C6C70),
    tertiaryLabel = Color(0xFFC4C4C7),
    separator = Color(0xFFD8D8DA),
    accent = Color(0xFF0070D0),
    accentText = Color(0xFF0070D0),
    favorite = Color(0xFFB38E00),
    bar = Color(0xF2F7F7F7),
    miniPlayer = Color(0xF5FBFBFB),
    isDark = false,
)

private val Dark = AppleColors(
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    fill = Color(0xFF1C1C1E),
    circle = Color(0xFF161616),
    circleBorder = Color(0xFF262626),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF48484A),
    separator = Color(0xFF373737),
    accent = Color(0xFF0090FC),
    accentText = Color(0xFF0090FC),
    favorite = Color(0xFFD4A800),
    bar = Color(0xF2262626),
    miniPlayer = Color(0xF5202021),
    isDark = true,
)

/**
 * Inter (OFL) como sustituto de SF Pro, que no se puede usar fuera de Apple.
 * Inter Display imita SF Pro Display (≥ 20 sp); Inter, SF Pro Text (< 20 sp).
 * Archivos estáticos por peso: los ejes del archivo variable no se aplicaban.
 */
private val InterText = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val InterDisplay = FontFamily(
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/** Escala de texto de iOS (tamaño Large, el predeterminado) en sp: escala con la letra del sistema. */
@Immutable
data class AppleType(
    val largeTitle: TextStyle = style(34, 41, FontWeight.Bold, tracking = -0.01),
    val title1: TextStyle = style(28, 34, FontWeight.Bold, tracking = -0.01),
    val title2: TextStyle = style(22, 28, FontWeight.Bold, tracking = -0.005),
    val title3: TextStyle = style(20, 25, FontWeight.SemiBold),
    val headline: TextStyle = style(17, 22, FontWeight.SemiBold),
    val body: TextStyle = style(17, 22, FontWeight.Normal),
    val callout: TextStyle = style(16, 21, FontWeight.Normal),
    val subhead: TextStyle = style(15, 20, FontWeight.Normal),
    val footnote: TextStyle = style(13, 18, FontWeight.Normal),
    val caption: TextStyle = style(12, 16, FontWeight.Normal),
)

private fun style(size: Int, leading: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = if (size >= 20) InterDisplay else InterText,
    fontSize = size.sp,
    lineHeight = leading.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val LocalAppleColors = staticCompositionLocalOf { Light }
private val LocalAppleType = staticCompositionLocalOf { AppleType() }

/** Paleta oscura pase lo que pase: páginas con fondo de color (álbum, artista), donde el texto va en blanco. */
@Composable
fun ForceDark(content: @Composable () -> Unit) = CompositionLocalProvider(LocalAppleColors provides Dark, content = content)

object Apple {
    val colors: AppleColors
        @Composable @ReadOnlyComposable get() = LocalAppleColors.current
    val type: AppleType
        @Composable @ReadOnlyComposable get() = LocalAppleType.current
}

@Composable
fun CiderTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Dark else Light
    val t = AppleType()
    // Los componentes de Material que queden (menús, campos) heredan los mismos tokens.
    val scheme = if (c.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White, background = c.background, onBackground = c.label,
            surface = c.background, onSurface = c.label, surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.fill, surfaceContainerHigh = Color(0xFF2C2C2E), outline = c.separator,
            outlineVariant = c.separator,
        )
    } else {
        lightColorScheme(
            primary = c.accentText, onPrimary = Color.White, background = c.background, onBackground = c.label,
            surface = c.background, onSurface = c.label, surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), outline = c.separator,
            outlineVariant = c.separator,
        )
    }
    val typography = Typography(
        displaySmall = t.largeTitle, headlineMedium = t.title1, headlineSmall = t.title2,
        titleLarge = t.title3, titleMedium = t.headline, titleSmall = t.subhead,
        bodyLarge = t.body, bodyMedium = t.subhead, bodySmall = t.footnote,
        labelLarge = t.headline, labelMedium = t.footnote, labelSmall = t.caption,
    )
    CompositionLocalProvider(LocalAppleColors provides c, LocalAppleType provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
