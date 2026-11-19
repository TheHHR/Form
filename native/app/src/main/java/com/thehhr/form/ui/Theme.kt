@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun FormTheme(accent: String, content: @Composable () -> Unit) {
    val (primary, onPrimary) = Accents[accent] ?: Accents.getValue("lime")
    val (secondary, onSecondary) = Accents.getValue(Complement[accent] ?: "cyan")
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.22f).compositeOver(Color(0xFF15161A)),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = Color(0xFF26272C),
        onSecondaryContainer = Color(0xFFF5F5F2),
        tertiary = FormColors.Carbs,
        background = Color(0xFF0A0B0D),
        onBackground = Color(0xFFF5F5F2),
        surface = Color(0xFF0A0B0D),
        onSurface = Color(0xFFF5F5F2),
        surfaceVariant = Color(0xFF1C1D22),
        onSurfaceVariant = Color(0xFFC4C4BE),
        surfaceContainerLowest = Color(0xFF07080A),
        surfaceContainerLow = Color(0xFF111215),
        surfaceContainer = Color(0xFF15161A),
        surfaceContainerHigh = Color(0xFF1C1D22),
        surfaceContainerHighest = Color(0xFF26272C),
        outline = Color(0xFF3A3B40),
        outlineVariant = Color(0xFF26272C),
        error = Color(0xFFFF5449),
        onError = Color(0xFF2D0001),
        inverseSurface = Color(0xFFF5F5F2),
        inverseOnSurface = Color(0xFF15161A),
        scrim = Color.Black,
    )
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
