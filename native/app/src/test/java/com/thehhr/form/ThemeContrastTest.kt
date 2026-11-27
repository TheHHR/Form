package com.thehhr.form

import androidx.compose.ui.graphics.luminance
import com.thehhr.form.ui.Accents
import com.thehhr.form.ui.onLight
import org.junit.Assert.assertTrue
import org.junit.Test

/** Light mode uses darkened accents as text: every preset must reach 4.5:1 on white. */
class ThemeContrastTest {
    @Test fun accentsReadableOnWhite() {
        for ((name, pair) in Accents) {
            val ratio = 1.05 / (onLight(pair.first).luminance() + 0.05)
            assertTrue("$name contrast $ratio", ratio >= 4.5)
        }
    }
}
