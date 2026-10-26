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

private val VividPurple = Color(0xFF6746F1)
private val CoralAccent = Color(0xFFFF5524)
private val SkySecondary = Color(0xFF4A8CF0)

private val LightExpressiveColors = lightColorScheme(
    primary = VividPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF19006B),
    secondary = SkySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2FF),
    onSecondaryContainer = Color(0xFF001B3F),
    tertiary = CoralAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDACF),
    onTertiaryContainer = Color(0xFF4A0009),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    outline = Color(0xFF7A7589),
    outlineVariant = Color(0xFFCBC3DC),
)

private val DarkExpressiveColors = darkColorScheme(
    primary = Color(0xFFCBBFF7),
    onPrimary = Color(0xFF4205D2),
    primaryContainer = Color(0xFF4D33C1),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFAFC6FF),
    onSecondary = Color(0xFF002A65),
    secondaryContainer = Color(0xFF00418C),
    onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFFFFB4A2),
    onTertiary = Color(0xFF5F1503),
    tertiaryContainer = Color(0xFF7E2E00),
    onTertiaryContainer = Color(0xFFFFDACF),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceContainer = Color(0xFF1D1D23),
    surfaceContainerHigh = Color(0xFF28282E),
    surfaceContainerHighest = Color(0xFF33333A),
    outline = Color(0xFF948E9F),
    outlineVariant = Color(0xFF49454F),
)

/**
 * Material 3 Expressive brand color treatment.
 *
 * Dynamic (wallpaper-derived) color is kept as `dynamicColor = false` by
 * default because androidx.compose material3's Expressive *color* API is not
 * published yet (as of material3 1.5.0-beta02); its dynamic functions
 * resolve to the legacy platform palettes (verified: pixel-identical output).
 * `dynamicColor = true` restores wallpaper-derived colors on S+ and forces
 * the Expressive motion/shape theme regardless.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VtopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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

        darkTheme -> DarkExpressiveColors
        else -> LightExpressiveColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
