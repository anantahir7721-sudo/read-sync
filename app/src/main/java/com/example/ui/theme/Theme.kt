package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

private val PageStreakLightColorScheme = lightColorScheme(
    primary = EmberTerracotta,
    onPrimary = Color.White,
    primaryContainer = EmberTerracottaContainer,
    onPrimaryContainer = OnEmberTerracottaContainer,
    secondary = LibraryForest,
    onSecondary = Color.White,
    secondaryContainer = LibraryForestContainer,
    onSecondaryContainer = OnLibraryForestContainer,
    tertiary = OchreGold,
    onTertiary = Color.White,
    tertiaryContainer = OchreGoldContainer,
    onTertiaryContainer = OnOchreGoldContainer,
    background = WarmParchment,
    onBackground = InkEspresso,
    surface = WarmParchment,
    onSurface = InkEspresso,
    surfaceVariant = SoftPaper,
    onSurfaceVariant = MutedWalnut,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ElevatedPaper,
    surfaceContainer = SoftPaper,
    surfaceContainerHigh = Color(0xFFEBE2D5),
    surfaceContainerHighest = Color(0xFFE3D8C8),
    outline = SandDivider,
    outlineVariant = Color(0xFFECE3D6),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonAlertContainer,
    onErrorContainer = OnCrimsonAlertContainer
)

private val PageStreakDarkColorScheme = darkColorScheme(
    primary = LuminousEmber,
    onPrimary = OnLuminousEmber,
    primaryContainer = LuminousEmberContainer,
    onPrimaryContainer = OnLuminousEmberContainer,
    secondary = SageEmerald,
    onSecondary = OnSageEmerald,
    secondaryContainer = SageEmeraldContainer,
    onSecondaryContainer = OnSageEmeraldContainer,
    tertiary = WarmAmberGold,
    onTertiary = OnWarmAmberGold,
    tertiaryContainer = WarmAmberGoldContainer,
    onTertiaryContainer = OnWarmAmberGoldContainer,
    background = MidnightObsidian,
    onBackground = ParchmentText,
    surface = MidnightObsidian,
    onSurface = ParchmentText,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = MutedParchment,
    surfaceContainerLowest = Color(0xFF0E0C0A),
    surfaceContainerLow = MidnightCard,
    surfaceContainer = MidnightElevated,
    surfaceContainerHigh = Color(0xFF312B25),
    surfaceContainerHighest = Color(0xFF3C342D),
    outline = MidnightDivider,
    outlineVariant = Color(0xFF2B2520),
    error = SoftCoralError,
    onError = OnSoftCoralError,
    errorContainer = SoftCoralErrorContainer,
    onErrorContainer = OnSoftCoralErrorContainer
)

val PageStreakShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PageStreakTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) PageStreakDarkColorScheme else PageStreakLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PageStreakShapes,
        content = content
    )
}
