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

/** "Jadran": teal sea and warm sand; calm enough for daily study. */
private val JadranDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF5ED6CF),
    onPrimary = Color(0xFF00302E),
    primaryContainer = Color(0xFF12403F),
    onPrimaryContainer = Color(0xFFC9EFEA),
    secondary = Color(0xFFFFC27A),
    onSecondary = Color(0xFF432700),
    secondaryContainer = Color(0xFF5A3A12),
    onSecondaryContainer = Color(0xFFFFE3B3),
    tertiary = Color(0xFF9CC2FF),
    onTertiary = Color(0xFF00305F),
    tertiaryContainer = Color(0xFF1D3F6B),
    onTertiaryContainer = Color(0xFFD6E4FF),
    background = Color(0xFF0B1719),
    onBackground = Color(0xFFE4F2F0),
    surface = Color(0xFF0B1719),
    onSurface = Color(0xFFE4F2F0),
    surfaceVariant = Color(0xFF1E3133),
    onSurfaceVariant = Color(0xFF93ADAB),
    surfaceContainerLowest = Color(0xFF081113),
    surfaceContainerLow = Color(0xFF0F1E20),
    surfaceContainer = Color(0xFF132326),
    surfaceContainerHigh = Color(0xFF1A2C2F),
    surfaceContainerHighest = Color(0xFF223639),
    outline = Color(0xFF3B5457),
    outlineVariant = Color(0xFF253A3C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val JadranLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B8A86),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEFEA),
    onPrimaryContainer = Color(0xFF063B40),
    secondary = Color(0xFF9A5B12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE3B3),
    onSecondaryContainer = Color(0xFF3A2200),
    tertiary = Color(0xFF2B6CC4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E4FF),
    onTertiaryContainer = Color(0xFF0B2A5C),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF0F2226),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF0F2226),
    surfaceVariant = Color(0xFFDCE8E6),
    onSurfaceVariant = Color(0xFF4D6468),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F6F5),
    surfaceContainer = Color(0xFFEAF2F1),
    surfaceContainerHigh = Color(0xFFE4EDEC),
    surfaceContainerHighest = Color(0xFFDEE8E7),
    outline = Color(0xFF708A89),
    outlineVariant = Color(0xFFC9DAD8),
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
    der = Color(0xFF7FB0FF), die = Color(0xFFFF8597), das = Color(0xFF63D39C),
    good = Color(0xFF63D39C), bad = Color(0xFFFF8597)
)
private val ArticlesLight = ArticlePalette(
    der = Color(0xFF2B6CC4), die = Color(0xFFD2475A), das = Color(0xFF1E9460),
    good = Color(0xFF1E9460), bad = Color(0xFFD2475A)
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
        dark -> JadranDark
        else -> JadranLight
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
