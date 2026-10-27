package io.github.deganegane.wortschatz.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.deganegane.wortschatz.R
import io.github.deganegane.wortschatz.ThemeMode

/** "Word treasure": warm gold on deep ink, paper tones in light mode. */
private val InkDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFF5BC4A),
    onPrimary = Color(0xFF3A2600),
    primaryContainer = Color(0xFF5A3F05),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9CC6FF),
    onSecondary = Color(0xFF00315C),
    secondaryContainer = Color(0xFF1D3657),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Color(0xFFFF9E8A),
    onTertiary = Color(0xFF5A1406),
    tertiaryContainer = Color(0xFF6B2A1C),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE9ECF4),
    surface = Color(0xFF0E1320),
    onSurface = Color(0xFFE9ECF4),
    surfaceVariant = Color(0xFF1F2738),
    onSurfaceVariant = Color(0xFFA7B0C4),
    surfaceContainerLowest = Color(0xFF0A0E18),
    surfaceContainerLow = Color(0xFF131929),
    surfaceContainer = Color(0xFF171E2F),
    surfaceContainerHigh = Color(0xFF1E2638),
    surfaceContainerHighest = Color(0xFF263044),
    outline = Color(0xFF3C4760),
    outlineVariant = Color(0xFF283147),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val InkLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF825800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2A1800),
    secondary = Color(0xFF2B5C93),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E3FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFFA23C28),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3D0600),
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF1B1C20),
    surface = Color(0xFFFBF7EF),
    onSurface = Color(0xFF1B1C20),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1E7),
    surfaceContainer = Color(0xFFF1EBE0),
    surfaceContainerHigh = Color(0xFFEBE5DA),
    surfaceContainerHighest = Color(0xFFE5DFD4),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD1C5B4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/** Classic gender colours (der blue, die red, das green), tuned per theme. */
data class ArticlePalette(val der: Color, val die: Color, val das: Color, val good: Color, val bad: Color) {
    fun of(article: String?): Color? = when (article) {
        "der" -> der
        "die" -> die
        "das" -> das
        else -> null
    }
}

private val ArticlesDark = ArticlePalette(
    der = Color(0xFF6FA8FF), die = Color(0xFFFF7A8A), das = Color(0xFF5ED39A),
    good = Color(0xFF5ED39A), bad = Color(0xFFFF8A80)
)
private val ArticlesLight = ArticlePalette(
    der = Color(0xFF1F5FC9), die = Color(0xFFC0303F), das = Color(0xFF16834E),
    good = Color(0xFF16834E), bad = Color(0xFFC0303F)
)

val LocalArticles = staticCompositionLocalOf { ArticlesDark }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/** Space Grotesk (OFL) for headings, numbers and German headwords; body text stays Roboto. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium),
        labelLarge = t.labelLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelMedium = t.labelMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun WortschatzTheme(dynamicColor: Boolean, themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> InkDark
        else -> InkLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalArticles provides if (dark) ArticlesDark else ArticlesLight) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
