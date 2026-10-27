package dev.vtoppp.vtop.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Google Material You signature color palette
private val GoogleBlue = Color(0xFF0B57D0)
private val GoogleBlueLight = Color(0xFFD3E3FD)
private val GoogleTeal = Color(0xFF00639B)
private val GoogleTealLight = Color(0xFFC2E7FF)
private val GoogleGreen = Color(0xFF146C2E)
private val GoogleGreenLight = Color(0xFFC4EED0)

private val LightGoogleColors = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = GoogleBlueLight,
    onPrimaryContainer = Color(0xFF041E49),
    secondary = GoogleTeal,
    onSecondary = Color.White,
    secondaryContainer = GoogleTealLight,
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = GoogleGreen,
    onTertiary = Color.White,
    tertiaryContainer = GoogleGreenLight,
    onTertiaryContainer = Color(0xFF072711),
    surface = Color(0xFFF8F9FA),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF444746),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFE1E8F0),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5),
)

private val DarkGoogleColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7FCFFF),
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFF6DD58C),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF28292A),
    surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VtopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkGoogleColors
        else -> LightGoogleColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
