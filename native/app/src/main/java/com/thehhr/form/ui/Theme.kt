@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
private val Complement = mapOf("lime" to "cyan", "cyan" to "lime", "red" to "blue", "blue" to "red",
    "green" to "orange", "orange" to "green", "purple" to "pink", "pink" to "purple")

object FormColors {
    val Carbs = Color(0xFFA8C7FA)
    val Fats = Color(0xFFFFB951)
    val Positive = Color(0xFF87D99A)
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

@Composable
fun FormTheme(accent: String, content: @Composable () -> Unit) {
    val (primary, onPrimary) = Accents[accent] ?: Accents.getValue("lime")
    val tertiary = Accents.getValue(Complement[accent] ?: "cyan").first
    val surface = Color(0xFF15161A)
    val scheme = remember(accent) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.24f).compositeOver(surface),
            onPrimaryContainer = primary,
            // One accent rules: secondary roles (nav indicator, selected chips, toggles) are tints of it.
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primary.copy(alpha = 0.18f).compositeOver(surface),
            onSecondaryContainer = primary,
            // The complement is kept for rare highlights (supersets, PRs).
            tertiary = tertiary,
            onTertiary = Color(0xFF111111),
            tertiaryContainer = tertiary.copy(alpha = 0.2f).compositeOver(surface),
            onTertiaryContainer = tertiary,
            background = Color(0xFF0A0B0D),
            onBackground = Color(0xFFF5F5F2),
            surface = Color(0xFF0A0B0D),
            onSurface = Color(0xFFF5F5F2),
            surfaceVariant = Color(0xFF1C1D22),
            onSurfaceVariant = Color(0xFFC4C4BE),
            surfaceContainerLowest = Color(0xFF07080A),
            surfaceContainerLow = Color(0xFF111215),
            surfaceContainer = surface,
            surfaceContainerHigh = Color(0xFF1C1D22),
            surfaceContainerHighest = Color(0xFF26272C),
            surfaceBright = Color(0xFF2E2F35),
            outline = Color(0xFF5D5D56),
            outlineVariant = Color(0xFF3A3B40),
            error = Color(0xFFFF5449),
            onError = Color(0xFF2D0001),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            inverseSurface = Color(0xFFF5F5F2),
            inverseOnSurface = Color(0xFF15161A),
            inversePrimary = primary,
            scrim = Color.Black,
        )
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), shapes = FormShapes, typography = remember { formTypography() }, content = content)
}
