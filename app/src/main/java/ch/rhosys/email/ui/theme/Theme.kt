package ch.rhosys.email.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalCatppuccin = staticCompositionLocalOf { Mocha }

private fun CatppuccinColors.toColorScheme(isDark: Boolean) = if (isDark) {
    darkColorScheme(
        primary = mauve, onPrimary = crust, primaryContainer = surface0, onPrimaryContainer = text,
        secondary = blue, onSecondary = crust, secondaryContainer = surface0, onSecondaryContainer = text,
        tertiary = teal, onTertiary = crust,
        background = base, onBackground = text,
        surface = base, onSurface = text,
        surfaceVariant = surface0, onSurfaceVariant = subtext0,
        error = red, onError = crust, errorContainer = surface0, onErrorContainer = red,
        outline = overlay0, outlineVariant = surface1,
        inverseSurface = text, inverseOnSurface = base,
        scrim = crust,
    )
} else {
    lightColorScheme(
        primary = mauve, onPrimary = base, primaryContainer = surface0, onPrimaryContainer = text,
        secondary = blue, onSecondary = base, secondaryContainer = surface0, onSecondaryContainer = text,
        tertiary = teal, onTertiary = base,
        background = base, onBackground = text,
        surface = base, onSurface = text,
        surfaceVariant = surface0, onSurfaceVariant = subtext0,
        error = red, onError = base, errorContainer = surface0, onErrorContainer = red,
        outline = overlay0, outlineVariant = surface1,
        inverseSurface = text, inverseOnSurface = base,
        scrim = crust,
    )
}

private val EmailTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

/**
 * App-wide theme. [flavor] is user-selectable in Settings (decision #55); when
 * null it follows the system dark/light setting using Mocha (dark) or Latte (light).
 */
@Composable
fun EmailTheme(
    flavor: CatppuccinFlavor? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val resolvedFlavor = flavor ?: if (darkTheme) CatppuccinFlavor.MOCHA else CatppuccinFlavor.LATTE
    val palette = resolvedFlavor.palette()
    val colorScheme = palette.toColorScheme(resolvedFlavor.isDark)

    androidx.compose.runtime.CompositionLocalProvider(LocalCatppuccin provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EmailTypography,
            content = content,
        )
    }
}
