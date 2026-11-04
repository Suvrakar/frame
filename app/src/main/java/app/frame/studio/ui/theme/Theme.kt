package app.frame.studio.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.frame.studio.data.ThemeMode

/** One calm accent across the app. */
val Indigo = Color(0xFF5B63F0)
val IndigoLight = Color(0xFF8A90FF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E4FF),
    onPrimaryContainer = Color(0xFF1C1F66),
    secondary = Color(0xFF6C7090),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E8F4),
    onSecondaryContainer = Color(0xFF262A40),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE4FF),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFEEF0F8),
    onBackground = Color(0xFF1B1D2B),
    surface = Color(0xFFF5F6FB),
    onSurface = Color(0xFF1B1D2B),
    surfaceVariant = Color(0xFFE3E5F0),
    onSurfaceVariant = Color(0xFF666A82),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF1F2F9),
    surfaceContainerHigh = Color(0xFFE9EBF5),
    surfaceContainerHighest = Color(0xFFE1E3EF),
    outline = Color(0xFF9A9EB5),
    outlineVariant = Color(0xFFD5D8E6),
    error = Color(0xFFD93A4A),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E4),
    onErrorContainer = Color(0xFF5C0A14),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF151866),
    primaryContainer = Color(0xFF2F348F),
    onPrimaryContainer = Color(0xFFE2E4FF),
    secondary = Color(0xFFAEB2CC),
    onSecondary = Color(0xFF242842),
    secondaryContainer = Color(0xFF2B2F48),
    onSecondaryContainer = Color(0xFFDCDFF2),
    tertiary = Color(0xFFB79BFF),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF41307A),
    onTertiaryContainer = Color(0xFFEDE4FF),
    background = Color(0xFF0D1020),
    onBackground = Color(0xFFE8E9F4),
    surface = Color(0xFF121528),
    onSurface = Color(0xFFE8E9F4),
    surfaceVariant = Color(0xFF242842),
    onSurfaceVariant = Color(0xFFA3A7C2),
    surfaceContainerLowest = Color(0xFF0A0C18),
    surfaceContainerLow = Color(0xFF151830),
    surfaceContainer = Color(0xFF191C34),
    surfaceContainerHigh = Color(0xFF20243E),
    surfaceContainerHighest = Color(0xFF282C48),
    outline = Color(0xFF6E7290),
    outlineVariant = Color(0xFF30344F),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF5C0A14),
    errorContainer = Color(0xFF7A1C28),
    onErrorContainer = Color(0xFFFFE1E4),
)

/** Colours for translucent "glass" surfaces, tuned per theme. */
@Immutable
data class GlassColors(
    val fill: Color,
    val fillStrong: Color,
    val border: Color,
    val shadow: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val isDark: Boolean,
)

private val LightGlass = GlassColors(
    fill = Color.White.copy(alpha = 0.62f),
    fillStrong = Color.White.copy(alpha = 0.82f),
    border = Color.White.copy(alpha = 0.9f),
    shadow = Color(0xFF5B63F0).copy(alpha = 0.16f),
    glowA = Color(0xFF8A90FF).copy(alpha = 0.32f),
    glowB = Color(0xFFB9A4FF).copy(alpha = 0.26f),
    glowC = Color(0xFF8FD3FF).copy(alpha = 0.22f),
    isDark = false,
)

private val DarkGlass = GlassColors(
    fill = Color.White.copy(alpha = 0.06f),
    fillStrong = Color.White.copy(alpha = 0.1f),
    border = Color.White.copy(alpha = 0.12f),
    shadow = Color.Black.copy(alpha = 0.5f),
    glowA = Color(0xFF5B63F0).copy(alpha = 0.34f),
    glowB = Color(0xFF8B5CF6).copy(alpha = 0.26f),
    glowC = Color(0xFF22D3EE).copy(alpha = 0.14f),
    isDark = true,
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

private val FrameTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.2.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    )
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** App theme: calm indigo accent, light or dark per the user's choice, glass surfaces. */
@Composable
fun FrameTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(mode)
    CompositionLocalProvider(LocalGlass provides if (dark) DarkGlass else LightGlass) {
        val colors = if (dark) DarkColors else LightColors
        MaterialTheme(colorScheme = colors, typography = FrameTypography) {
            // Text and icons without an explicit colour follow the theme. Without this they fall back
            // to Compose's default (black) on our transparent glass screens, unreadable in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}
