package com.nufo.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.nufo.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nufo.app.data.ThemeMode

val Green = Color(0xFF2E7D32)
val GreenDeep = Color(0xFF1B5E20)
val GradeA = Color(0xFF1B5E20)
val GradeB = Color(0xFF4CAF50)
val GradeC = Color(0xFFFFC107)
val GradeD = Color(0xFFFF9800)
val GradeE = Color(0xFFF44336)

fun gradeColor(grade: String?) = when (grade?.lowercase()) {
    "a" -> GradeA; "b" -> GradeB; "c" -> GradeC; "d" -> GradeD; "e" -> GradeE
    else -> Color(0xFF9CA3AF)
}

fun novaColor(nova: Int?) = when (nova) { 1 -> GradeA; 2 -> GradeB; 3 -> GradeD; 4 -> GradeE; else -> Color(0xFF9CA3AF) }

/** Darker grade colours for text, readable (≥ 4.5:1) on light cards; the bright ones are for fills only. */
@androidx.compose.runtime.Composable
fun scoreTextColor(score: Int): Color = if (MaterialTheme.colorScheme.background.luminanceBelowHalf()) scoreColor(score) else when {
    score >= 80 -> GradeA; score >= 60 -> Color(0xFF2E7D32); score >= 40 -> Color(0xFF8A5A00); score >= 20 -> Color(0xFFB23C00); else -> Color(0xFFC62828)
}

private fun Color.luminanceBelowHalf() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

fun scoreColor(score: Int) = when {
    score >= 80 -> GradeA; score >= 60 -> GradeB; score >= 40 -> GradeC; score >= 20 -> GradeD; else -> GradeE
}

/** Colors not covered by Material roles: soft card shadow and the per-nutrient tints. */
@Immutable
data class NufoColors(
    val card: Color,
    val shadow: Color,
    val textSecondary: Color,
    val hairline: Color,
    val tintProtein: Color,
    val tintCarbs: Color,
    val tintFat: Color,
    val tintFiber: Color,
)

private val LightNufo = NufoColors(
    card = Color.White, shadow = Color(0x14101828), textSecondary = Color(0xFF6B7280), hairline = Color(0xFFE8ECE8),
    tintProtein = Color(0xFFE8F5E9), tintCarbs = Color(0xFFFFF7E0), tintFat = Color(0xFFFFEEE3), tintFiber = Color(0xFFEAF4FB),
)
private val DarkNufo = NufoColors(
    card = Color(0xFF18201A), shadow = Color(0x33000000), textSecondary = Color(0xFF9CA3AF), hairline = Color(0xFF263028),
    tintProtein = Color(0xFF1E3322), tintCarbs = Color(0xFF3A3220), tintFat = Color(0xFF3A2A20), tintFiber = Color(0xFF1E2E3A),
)

val LocalNufoColors = staticCompositionLocalOf { LightNufo }
/** True when the user turned animations off system-wide; decorative motion then snaps to its end state. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** One spring for everything that moves, so the whole app feels like one material. */
fun <T> nufoSpring() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/** Inter (variable, Latin + Greek). One family keeps Greek and Latin text visually identical. */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = tracking.sp)

private val Typo = Typography(
    displaySmall = style(34, FontWeight.Bold, 40, -1.0),
    headlineMedium = style(26, FontWeight.Bold, 32, -0.6),
    headlineSmall = style(21, FontWeight.SemiBold, 27, -0.35),
    titleLarge = style(18, FontWeight.SemiBold, 24, -0.2),
    titleMedium = style(15, FontWeight.SemiBold, 21, -0.1),
    bodyLarge = style(16, FontWeight.Normal, 24, -0.1),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(14, FontWeight.SemiBold, 18),
    labelMedium = style(12, FontWeight.Medium, 16, 0.1),
    labelSmall = style(11, FontWeight.Medium, 14, 0.3),
)
@Composable
fun NufoTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF66BB6A), onPrimary = Color(0xFF0B1F0D), primaryContainer = Color(0xFF1E3322),
        onPrimaryContainer = Color(0xFFC8E6C9), secondary = Color(0xFF66BB6A), secondaryContainer = Color(0xFF1E3322),
        onSecondaryContainer = Color(0xFFC8E6C9), background = Color(0xFF0E130F), onBackground = Color(0xFFF1F5F1),
        surface = Color(0xFF0E130F), onSurface = Color(0xFFF1F5F1), surfaceVariant = Color(0xFF18201A),
        onSurfaceVariant = Color(0xFF9CA3AF), outline = Color(0xFF334036), error = GradeE,
    ) else lightColorScheme(
        primary = Green, onPrimary = Color.White, primaryContainer = Color(0xFFE8F5E9), onPrimaryContainer = GreenDeep,
        secondary = Green, secondaryContainer = Color(0xFFE8F5E9), onSecondaryContainer = GreenDeep,
        background = Color(0xFFF8FAF8), onBackground = Color(0xFF111827), surface = Color(0xFFF8FAF8),
        onSurface = Color(0xFF111827), surfaceVariant = Color(0xFFEFF3EF), onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFD9E0D9), error = Color(0xFFD32F2F),
    )
    val resolver = LocalContext.current.contentResolver
    val reduce = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    CompositionLocalProvider(LocalNufoColors provides if (dark) DarkNufo else LightNufo, LocalReduceMotion provides reduce) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typo,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp),
            ),
            content = content,
        )
    }
}