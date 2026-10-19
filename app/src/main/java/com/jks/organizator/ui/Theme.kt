@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.jks.organizator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jks.organizator.R

object C {
    val bg = Color(0xFF0E1513)
    val surface = Color(0xFF162320)
    val surface2 = Color(0xFF1D2E2A)
    val line = Color(0xFF263733)
    val text = Color(0xFFE9F0ED)
    val text2 = Color(0xFFC9D6D1)
    val muted = Color(0xFF93A8A1)
    val gold = Color(0xFFE0B95C)
    val teal = Color(0xFF62C4AB)
    val danger = Color(0xFFE58A7B)

    val scale = listOf(
        Color(0xFF1A2825), Color(0xFF1B332E), Color(0xFF21463F),
        Color(0xFF2D6B5E), Color(0xFF3F9380), Color(0xFF62C4AB)
    )
}

private fun fam(res: Int, weights: List<Int>) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

val DmSans = fam(R.font.dmsans, listOf(400, 500, 600, 700))
val Serif = fam(R.font.newsreader, listOf(400, 500, 600))

private val tab = "tnum"

object T {
    val body = TextStyle(fontFamily = DmSans, fontSize = 14.sp, color = C.text, fontFeatureSettings = tab)
    val small = TextStyle(fontFamily = DmSans, fontSize = 12.sp, color = C.muted, fontFeatureSettings = tab)
    val label = TextStyle(fontFamily = DmSans, fontSize = 11.sp, color = C.muted, fontWeight = FontWeight.W600, letterSpacing = 0.8.sp)
    val title = TextStyle(fontFamily = Serif, fontSize = 26.sp, color = C.text, fontWeight = FontWeight.W500)
    val h2 = TextStyle(fontFamily = Serif, fontSize = 20.sp, color = C.text, fontWeight = FontWeight.W500)
    val big = TextStyle(fontFamily = Serif, fontSize = 40.sp, color = C.text, fontWeight = FontWeight.W600, fontFeatureSettings = tab)
    val medium = TextStyle(fontFamily = DmSans, fontSize = 15.sp, color = C.text, fontWeight = FontWeight.W500, fontFeatureSettings = tab)
    val arabic = TextStyle(fontSize = 24.sp, color = C.text, lineHeight = 40.sp)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val cs = darkColorScheme(
        primary = C.gold, onPrimary = C.bg, secondary = C.teal, onSecondary = C.bg,
        background = C.bg, onBackground = C.text, surface = C.surface, onSurface = C.text,
        surfaceVariant = C.surface2, onSurfaceVariant = C.text2, outline = C.line, error = C.danger,
        surfaceContainerHigh = C.surface, surfaceContainerHighest = C.surface2, surfaceContainer = C.surface,
        surfaceContainerLow = C.surface, surfaceContainerLowest = C.bg, primaryContainer = C.surface2, onPrimaryContainer = C.text
    )
    val base = Typography()
    val ty = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = DmSans), displayMedium = base.displayMedium.copy(fontFamily = DmSans),
        displaySmall = base.displaySmall.copy(fontFamily = DmSans), headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = Serif), headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
        titleLarge = base.titleLarge.copy(fontFamily = Serif), titleMedium = base.titleMedium.copy(fontFamily = DmSans),
        titleSmall = base.titleSmall.copy(fontFamily = DmSans), bodyLarge = base.bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = DmSans), bodySmall = base.bodySmall.copy(fontFamily = DmSans),
        labelLarge = base.labelLarge.copy(fontFamily = DmSans), labelMedium = base.labelMedium.copy(fontFamily = DmSans),
        labelSmall = base.labelSmall.copy(fontFamily = DmSans)
    )
    MaterialTheme(colorScheme = cs, typography = ty, content = content)
}
