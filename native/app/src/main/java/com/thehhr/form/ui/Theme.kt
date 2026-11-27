@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thehhr.form.R

/** Accent presets from the web app (base, ink). */
val Accents = linkedMapOf(
    "lime" to (Color(0xFFC6F432) to Color(0xFF111400)),
    "cyan" to (Color(0xFF64D2FF) to Color(0xFF0B1C26)),
    "red" to (Color(0xFFFF453A) to Color.White),
    "blue" to (Color(0xFF0A84FF) to Color.White),
    "green" to (Color(0xFF30D158) to Color(0xFF131314)),
    "orange" to (Color(0xFFFF9F0A) to Color(0xFF131314)),
    "purple" to (Color(0xFFBF5AF2) to Color.White),
    "pink" to (Color(0xFFFF375F) to Color.White),
)
internal val Complement = mapOf("lime" to "cyan", "cyan" to "lime", "red" to "blue", "blue" to "red",
    "green" to "orange", "orange" to "green", "purple" to "pink", "pink" to "purple")

/** True when the app is drawing its dark scheme (set by [FormTheme]). */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Fixed data colours (macros, success), each with a darker variant that keeps contrast on light surfaces. */
object FormColors {
    val Carbs: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFA8C7FA) else Color(0xFF2F5DA8)
    val Fats: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFFFB951) else Color(0xFF8A5300)
    val Positive: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF87D99A) else Color(0xFF1E6E35)
}

private fun grotesk(weight: Int) = Font(R.font.space_grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Space Grotesk is the brand display face (from the web app); Roboto stays for body text. */
val Grotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

private fun TextStyle.g(weight: Int = 600) = copy(fontFamily = Grotesk, fontWeight = FontWeight(weight))

private fun formTypography(): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.g(500), displayMedium = t.displayMedium.g(500), displaySmall = t.displaySmall.g(500),
        headlineLarge = t.headlineLarge.g(), headlineMedium = t.headlineMedium.g(), headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(),
        displayLargeEmphasized = t.displayLargeEmphasized.g(700), displayMediumEmphasized = t.displayMediumEmphasized.g(700),
        displaySmallEmphasized = t.displaySmallEmphasized.g(700), headlineLargeEmphasized = t.headlineLargeEmphasized.g(700),
        headlineMediumEmphasized = t.headlineMediumEmphasized.g(700), headlineSmallEmphasized = t.headlineSmallEmphasized.g(700),
        titleLargeEmphasized = t.titleLargeEmphasized.g(700), titleMediumEmphasized = t.titleMediumEmphasized.g(700),
    )
}

/** Expressive leans on bigger, softer corners. */
private val FormShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Darkens [c] toward black until it reaches [ratio] contrast on white (accent used as text on light surfaces). */
fun onLight(c: Color, ratio: Double = 4.5): Color {
    var x = c
    repeat(40) {
        if ((1.05) / (x.luminance() + 0.05) >= ratio) return x
        x = lerp(x, Color.Black, 0.06f)
    }
    return x
}

internal fun darkScheme(primary: Color, onPrimary: Color, tertiary: Color): ColorScheme {
    val surface = Color(0xFF15161A)
    return darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.24f).compositeOver(surface), onPrimaryContainer = primary,
        // One accent rules: secondary roles (nav indicator, selected chips, toggles) are tints of it.
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = primary.copy(alpha = 0.18f).compositeOver(surface), onSecondaryContainer = primary,
        // The complement is kept for rare highlights (supersets, PRs, chart selection).
        tertiary = tertiary, onTertiary = Color(0xFF111111),
        tertiaryContainer = tertiary.copy(alpha = 0.2f).compositeOver(surface), onTertiaryContainer = tertiary,
        background = Color(0xFF0A0B0D), onBackground = Color(0xFFF5F5F2),
        surface = Color(0xFF0A0B0D), onSurface = Color(0xFFF5F5F2),
        surfaceVariant = Color(0xFF1C1D22), onSurfaceVariant = Color(0xFFC4C4BE),
        surfaceContainerLowest = Color(0xFF07080A), surfaceContainerLow = Color(0xFF111215), surfaceContainer = surface,
        surfaceContainerHigh = Color(0xFF1C1D22), surfaceContainerHighest = Color(0xFF26272C), surfaceBright = Color(0xFF2E2F35),
        outline = Color(0xFF5D5D56), outlineVariant = Color(0xFF3A3B40),
        error = Color(0xFFFF5449), onError = Color(0xFF2D0001), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFF5F5F2), inverseOnSurface = Color(0xFF15161A), inversePrimary = onLight(primary), scrim = Color.Black,
    )
}

/** Light scheme: the accent stays the fill colour of containers; text/icon roles use a darkened accent for 4.5:1 contrast. */
internal fun lightScheme(accent: Color, tertiaryAccent: Color): ColorScheme {
    val ink = onLight(accent)
    val tInk = onLight(tertiaryAccent)
    val white = Color(0xFFFFFFFF)
    return lightColorScheme(
        primary = ink, onPrimary = white,
        primaryContainer = accent.copy(alpha = 0.32f).compositeOver(white), onPrimaryContainer = onLight(ink, 7.0),
        secondary = ink, onSecondary = white,
        secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(white), onSecondaryContainer = onLight(ink, 7.0),
        tertiary = tInk, onTertiary = white,
        tertiaryContainer = tertiaryAccent.copy(alpha = 0.26f).compositeOver(white), onTertiaryContainer = onLight(tInk, 7.0),
        background = Color(0xFFFAFAF7), onBackground = Color(0xFF1B1C18),
        surface = Color(0xFFFAFAF7), onSurface = Color(0xFF1B1C18),
        surfaceVariant = Color(0xFFE3E3DC), onSurfaceVariant = Color(0xFF46483F),
        surfaceContainerLowest = white, surfaceContainerLow = Color(0xFFF4F4F0), surfaceContainer = Color(0xFFEEEEEA),
        surfaceContainerHigh = Color(0xFFE8E8E3), surfaceContainerHighest = Color(0xFFE1E1DC), surfaceBright = Color(0xFFFAFAF7),
        outline = Color(0xFF76786E), outlineVariant = Color(0xFFC6C8BC),
        error = Color(0xFFBA1A1A), onError = white, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        inverseSurface = Color(0xFF30312C), inverseOnSurface = Color(0xFFF2F1EC), inversePrimary = accent, scrim = Color.Black,
    )
}

/** Every colour role eases to its new value, so accent and light/dark switches cross-fade instead of snapping. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = MotionScheme.expressive().slowEffectsSpec<Color>()
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "scheme").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary), primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondary = a(secondary), onSecondary = a(onSecondary), secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary), onTertiary = a(onTertiary), tertiaryContainer = a(tertiaryContainer), onTertiaryContainer = a(onTertiaryContainer),
        background = a(background), onBackground = a(onBackground), surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLowest = a(surfaceContainerLowest), surfaceContainerLow = a(surfaceContainerLow), surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh), surfaceContainerHighest = a(surfaceContainerHighest),
        outline = a(outline), outlineVariant = a(outlineVariant), inversePrimary = a(inversePrimary),
    )
}

/**
 * Form's theme. [dark] picks the scheme; [dynamic] (Android 12+) uses the wallpaper palette instead of the accent.
 */
@Composable
fun FormTheme(accent: String, dark: Boolean = true, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val (primary, onPrimary) = Accents[accent] ?: Accents.getValue("lime")
    val tertiary = Accents.getValue(Complement[accent] ?: "cyan").first
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val target = remember(accent, dark, dynamic) {
        when {
            dynamic && android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            dark -> darkScheme(primary, onPrimary, tertiary)
            else -> lightScheme(primary, tertiary)
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialExpressiveTheme(colorScheme = target.animated(), motionScheme = MotionScheme.expressive(), shapes = FormShapes,
            typography = remember { formTypography() }, content = content)
    }
}
