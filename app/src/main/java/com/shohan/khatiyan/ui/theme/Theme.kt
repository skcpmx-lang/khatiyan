package com.shohan.khatiyan.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * খতিয়ান ships LIGHT MODE ONLY (Phase 5). There is intentionally no
 * darkColorScheme and the system dark setting is never consulted — the app
 * displays the same paper-warm palette on every device.
 */
private val KhatiyanLightScheme = lightColorScheme(
    primary = KhatiyanBrand.Primary,
    onPrimary = Color(0xFFFDFDF8),
    primaryContainer = Color(0xFFCFE8D9),
    onPrimaryContainer = KhatiyanBrand.Deep,
    inversePrimary = Color(0xFF9BD6B7),
    secondary = KhatiyanBrand.Gold,
    onSecondary = Color(0xFFFFFBF0),
    secondaryContainer = Color(0xFFF6E6BF),
    onSecondaryContainer = Color(0xFF4A3703),
    tertiary = KhatiyanBrand.Info,
    onTertiary = Color(0xFFF2FAFD),
    tertiaryContainer = KhatiyanBrand.InfoBg,
    onTertiaryContainer = Color(0xFF073241),
    error = KhatiyanBrand.Danger,
    onError = Color(0xFFFFF8F7),
    errorContainer = KhatiyanBrand.DangerBg,
    onErrorContainer = Color(0xFF5C1310),
    background = KhatiyanBrand.Paper,
    onBackground = KhatiyanBrand.Ink,
    surface = KhatiyanBrand.PaperCard,
    onSurface = KhatiyanBrand.Ink,
    surfaceVariant = Color(0xFFE8EADD),
    onSurfaceVariant = KhatiyanBrand.InkMuted,
    outline = Color(0xFF6F7F74),
    outlineVariant = KhatiyanBrand.Hairline,
    scrim = Color(0xFF000000),
)

@Composable
fun KhatiyanTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = KhatiyanLightScheme,
        typography = KhatiyanTypography,
        shapes = KhatiyanShapes,
        content = content,
    )
}
