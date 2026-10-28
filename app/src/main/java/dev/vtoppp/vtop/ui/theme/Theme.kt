package dev.vtoppp.vtop.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppPalette(val displayName: String, val seedColor: Color) {
    Dynamic("System", Color(0xFF4285F4)),
    GoogleBlue("Blue", Color(0xFF0B57D0)),
    Emerald("Emerald", Color(0xFF006D44)),
    Violet("Violet", Color(0xFF6B4EA2)),
    Coral("Sunset", Color(0xFFA04000)),
}

object ThemePreferences {
    private const val PREFS_NAME = "vtop_theme_prefs"
    private const val KEY_PALETTE = "selected_palette"

    fun getPalette(context: Context): AppPalette {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PALETTE, AppPalette.Dynamic.name)
        return runCatching { AppPalette.valueOf(name ?: AppPalette.Dynamic.name) }
            .getOrDefault(AppPalette.Dynamic)
    }

    fun setPalette(context: Context, palette: AppPalette) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PALETTE, palette.name)
            .apply()
    }
}

// Blue Palette
private val LightBlueColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF00639B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0),
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

private val DarkBlueColors = darkColorScheme(
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

// Emerald Palette
private val LightEmeraldColors = lightColorScheme(
    primary = Color(0xFF006D44),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FF8C7),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4D6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    surface = Color(0xFFF6FAF5),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainer = Color(0xFFECF2EC),
    surfaceContainerHigh = Color(0xFFE6ECE6),
    surfaceContainerHighest = Color(0xFFE0E7E0),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFBFC9C0),
)

private val DarkEmeraldColors = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003821),
    primaryContainer = Color(0xFF005132),
    onPrimaryContainer = Color(0xFF8FF8C7),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFA5CDDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C0),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF313632),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
)

// Violet Palette
private val LightVioletColors = lightColorScheme(
    primary = Color(0xFF6B4EA2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF250059),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFEDE7F2),
    surfaceContainerHighest = Color(0xFFE7E1EC),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
)

private val DarkVioletColors = darkColorScheme(
    primary = Color(0xFFD4BBFF),
    onPrimary = Color(0xFF3B1D71),
    primaryContainer = Color(0xFF523688),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    surface = Color(0xFF151218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2C292F),
    surfaceContainerHighest = Color(0xFF36343A),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
)

// Sunset/Coral Palette
private val LightCoralColors = lightColorScheme(
    primary = Color(0xFFA04000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3B0F00),
    secondary = Color(0xFF77574E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFF6C5D2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E1A7),
    onTertiaryContainer = Color(0xFF231B00),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainer = Color(0xFFF9EAE6),
    surfaceContainerHigh = Color(0xFFF4E4DF),
    surfaceContainerHighest = Color(0xFFEEDFD9),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
)

private val DarkCoralColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5B1E00),
    primaryContainer = Color(0xFF813100),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD8C58D),
    onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF53451A),
    onTertiaryContainer = Color(0xFFF6E1A7),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceContainer = Color(0xFF271D1A),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F),
    outline = Color(0xFFA08C86),
    outlineVariant = Color(0xFF53433F),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VtopTheme(
    palette: AppPalette = AppPalette.Dynamic,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when (palette) {
        AppPalette.Dynamic -> {
            if (Build.VERSION.SDK_INT >= 31) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkBlueColors else LightBlueColors
            }
        }
        AppPalette.GoogleBlue -> if (darkTheme) DarkBlueColors else LightBlueColors
        AppPalette.Emerald -> if (darkTheme) DarkEmeraldColors else LightEmeraldColors
        AppPalette.Violet -> if (darkTheme) DarkVioletColors else LightVioletColors
        AppPalette.Coral -> if (darkTheme) DarkCoralColors else LightCoralColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
