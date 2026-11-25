package dev.vtoppp.vtop.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, DARK, AMOLED, LIGHT }

/** The app accent: the platform's Material colours, or one the user picked. */
sealed interface AppPalette {
    /** Material You: the wallpaper-derived colours the platform provides. */
    data object Material : AppPalette

    /** A colour the user picked in the accent picker. */
    data class Custom(val seed: Color) : AppPalette {
        val displayName: String get() = "Custom"
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "vtop_theme_prefs"
    private const val KEY_PALETTE = "selected_palette"
    private const val KEY_ACCENT = "accent_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val ACCENT_MATERIAL = "MATERIAL"
    private const val ACCENT_CUSTOM = "CUSTOM"
    val DefaultAccentSeed: Color = Color(0xFF6B4EA2)
    val DefaultBlueSeed: Color = Color(0xFF0B57D0)
    private val legacySeeds = mapOf(
        "GoogleBlue" to DefaultBlueSeed,
        "Emerald" to Color(0xFF006D44),
        "Violet" to DefaultAccentSeed,
        "Coral" to Color(0xFFA04000),
    )
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BLUR = "blur_enabled"
    private const val KEY_LOCK_ENABLED = "lock_enabled"

    fun getThemeMode(context: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getBlurEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_BLUR, true)

    fun setBlurEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_BLUR, enabled).apply()
    }

    fun getLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_LOCK_ENABLED, true)

    fun setLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    fun getPalette(context: Context): AppPalette {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ACCENT, null) == ACCENT_CUSTOM) {
            return AppPalette.Custom(Color(prefs.getInt(KEY_ACCENT_COLOR, DefaultAccentSeed.toArgb())))
        }
        // Migration: the old preset palettes become the custom accent they had.
        val legacy = legacySeeds[prefs.getString(KEY_PALETTE, null)]
        return legacy?.let { AppPalette.Custom(it) } ?: AppPalette.Material
    }

    fun setPalette(context: Context, palette: AppPalette) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (palette) {
            is AppPalette.Material -> prefs.edit()
                .putString(KEY_ACCENT, ACCENT_MATERIAL)
                .remove(KEY_ACCENT_COLOR)
                .apply()
            is AppPalette.Custom -> prefs.edit()
                .putString(KEY_ACCENT, ACCENT_CUSTOM)
                .putInt(KEY_ACCENT_COLOR, palette.seed.toArgb())
                .apply()
        }
    }
}

// Blue Palette
private val LightBlueColors = lightColorScheme(
    primary = ThemePreferences.DefaultBlueSeed,
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

val LocalBlurEnabled = androidx.compose.runtime.compositionLocalOf { true }

// Ponytail: true-black surfaces for OLED panels; dynamic/static hues kept
private fun ColorScheme.amoledSurface(): ColorScheme = copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF090909),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF131313),
    surfaceContainerHighest = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFF232323),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF2C2C2C),
)

/** Hue in degrees, saturation and value in 0..1. Compose has no HSV accessors. */
fun Color.hsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return floatArrayOf(
        (hue + 360f) % 360f,
        if (max == 0f) 0f else delta / max,
        max,
    )
}

/** The accent roles, tonally derived from the picked seed. */
private fun ColorScheme.withAccent(seed: Color, dark: Boolean): ColorScheme {
    val hsv = seed.hsv()
    val h = hsv[0]
    val s = hsv[1]
    return copy(
        primary = Color.hsl(h, s, if (dark) 0.80f else 0.40f),
        onPrimary = Color.hsl(h, s, if (dark) 0.22f else 0.99f),
        primaryContainer = Color.hsl(h, s, if (dark) 0.32f else 0.90f),
        onPrimaryContainer = Color.hsl(h, s, if (dark) 0.90f else 0.18f),
    )
}

val VtopShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VtopTheme(
    palette: AppPalette = AppPalette.Material,
    themeMode: ThemeMode = ThemeMode.DARK,
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val amoled = themeMode == ThemeMode.AMOLED
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val base: ColorScheme = when (palette) {
        is AppPalette.Material ->
            if (Build.VERSION.SDK_INT >= 31) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkBlueColors else LightBlueColors
            }
        is AppPalette.Custom ->
            (if (darkTheme) DarkBlueColors else LightBlueColors).withAccent(palette.seed, darkTheme)
    }
    val colorScheme: ColorScheme = if (amoled) base.amoledSurface() else base

    androidx.compose.runtime.CompositionLocalProvider(LocalBlurEnabled provides blurEnabled) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = VtopShapes,
            content = content,
        )
    }
}
