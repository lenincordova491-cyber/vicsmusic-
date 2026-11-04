package app.vicsmusic.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VicsPrimary,
    onPrimary = VicsBackgroundDark,
    primaryContainer = VicsPrimaryDark,
    onPrimaryContainer = VicsTextPrimaryDark,
    secondary = VicsSecondary,
    onSecondary = VicsBackgroundDark,
    tertiary = VicsTertiary,
    background = VicsBackgroundDark,
    onBackground = VicsTextPrimaryDark,
    surface = VicsSurfaceDark,
    onSurface = VicsTextPrimaryDark,
    surfaceVariant = VicsSurfaceContainerDark,
    onSurfaceVariant = VicsTextSecondaryDark,
    outline = VicsCardBorderDark,
    error = VicsError
)

private val LightColorScheme = lightColorScheme(
    primary = VicsPrimaryDark,
    onPrimary = VicsSurfaceLight,
    primaryContainer = VicsPrimaryLight,
    onPrimaryContainer = VicsTextPrimaryLight,
    secondary = VicsSecondary,
    onSecondary = VicsSurfaceLight,
    tertiary = VicsTertiary,
    background = VicsBackgroundLight,
    onBackground = VicsTextPrimaryLight,
    surface = VicsSurfaceLight,
    onSurface = VicsTextPrimaryLight,
    surfaceVariant = VicsSurfaceContainerLight,
    onSurfaceVariant = VicsTextSecondaryLight,
    outline = VicsCardBorderLight,
    error = VicsError
)

@Composable
fun VicsmusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VicsTypography,
        content = content
    )
}
