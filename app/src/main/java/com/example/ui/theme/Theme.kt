package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ClipForgeDarkColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = OnElectricCoral,
    primaryContainer = ElectricCoralContainer,
    onPrimaryContainer = OnElectricCoralContainer,
    secondary = WarmAmber,
    onSecondary = OnWarmAmber,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = OnWarmAmberContainer,
    tertiary = MintTeal,
    onTertiary = OnMintTeal,
    tertiaryContainer = MintTealContainer,
    onTertiaryContainer = OnMintTealContainer,
    background = ObsidianBg,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceHigh,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceHigh,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineVariant
)

private val ClipForgeLightColorScheme = lightColorScheme(
    primary = CrimsonPrimaryLight,
    onPrimary = OnElectricCoral,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = OnCrimsonContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnElectricCoral,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = TealTertiaryLight,
    onTertiary = OnElectricCoral,
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = OnTealContainerLight,
    background = PaperBg,
    onBackground = PaperTextPrimary,
    surface = PaperSurface,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperSurfaceHigh,
    onSurfaceVariant = PaperTextSecondary,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurfaceHigh,
    surfaceContainerHigh = PaperSurfaceHighest,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant
)

private val ClipForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ClipForgeDarkColorScheme else ClipForgeLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipForgeTypography,
        shapes = ClipForgeShapes,
        content = content
    )
}
