package com.nufo.app.ui.components

import com.nufo.app.ui.theme.LocalAppReady

import com.nufo.app.ui.theme.Motion
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nufo.app.R
import com.nufo.app.data.Level
import com.nufo.app.ui.theme.GradeA
import com.nufo.app.ui.theme.GradeC
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.LocalReduceMotion
import com.nufo.app.ui.theme.gradeColor
import com.nufo.app.ui.theme.novaColor
import com.nufo.app.ui.theme.nufoSpring
import com.nufo.app.ui.theme.scoreColor

/** Card with a soft shadow and a crisp hairline edge. When clickable it sinks slightly under the finger. */
@Composable
fun NufoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    corner: Dp = 22.dp,
    color: Color = LocalNufoColors.current.card,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, nufoSpring(), label = "press")
    val shape = RoundedCornerShape(corner)
    val c = LocalNufoColors.current
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(color)
            .then(if (border) Modifier.border(BorderStroke(1.dp, c.hairline), shape) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(interaction, null, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                else Modifier
            ),
        content = content,
    )
}

/**
 * Fades and lifts content in the first time a screen shows it, staggered by [index]. Coming back to the
 * screen (back navigation, tab switch) shows it at rest: an entrance that replays on every return is noise.
 */
@Composable
fun Modifier.enterStagger(index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    var played by rememberSaveable { mutableStateOf(reduce) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    // Waits for the opening hand-off, so the first screen rises in view rather than under the splash.
    val ready = LocalAppReady.current
    LaunchedEffect(ready) {
        if (!ready || played) return@LaunchedEffect
        progress.animateTo(1f, tween(Motion.BASE, delayMillis = Motion.STAGGER * index.coerceAtMost(10), easing = Motion.EaseOut))
        played = true
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1 - progress.value) * 24f
    }
}

/**
 * A–E (or 1–4) strip in the style of the official Nutri-Score badge: every grade is visible,
 * the product's grade is enlarged. With no grade, all segments stay muted and a dash is shown.
 */
@Composable
fun GradeScale(
    title: String,
    values: List<String>,
    active: String?,
    colorOf: (String) -> Color,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val muted = LocalNufoColors.current.hairline
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(if (active == null) null else "$title ${active.uppercase()}", description).joinToString(". ")
                .ifEmpty { title }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(92.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (active == null) muted.copy(alpha = 0.6f) else Color.Transparent).padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                values.forEachIndexed { i, v ->
                    val isActive = v.equals(active, ignoreCase = true)
                    val size by animateDpAsState(if (isActive) 34.dp else 24.dp, nufoSpring(), label = "seg")
                    val bg by animateColorAsState(if (active == null) muted else colorOf(v).copy(alpha = if (isActive) 1f else 0.22f), label = "segBg")
                    Box(
                        Modifier.padding(horizontal = 1.dp).size(width = if (isActive) 34.dp else 26.dp, height = size)
                            .clip(RoundedCornerShape(if (isActive) 10.dp else 6.dp)).background(bg)
                            .then(if (isActive) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            v.uppercase(),
                            // Inactive letters use the text colour so they stay legible on pale yellow/orange in both themes.
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold, fontSize = if (isActive) 17.sp else 12.sp,
                        )
                    }
                }
            }
            if (active == null) {
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.not_available), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            }
        }
        if (description != null && active != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = LocalNufoColors.current.textSecondary,
                modifier = Modifier.padding(start = 92.dp, top = 4.dp))
        }
    }
}

@Composable
fun NutriScoreScale(grade: String?, modifier: Modifier = Modifier) = GradeScale(
    stringResource(R.string.nutriscore), listOf("a", "b", "c", "d", "e"), grade, ::gradeColor,
    stringResource(R.string.nutri_explain), modifier,
)

@Composable
fun NovaScale(group: Int?, modifier: Modifier = Modifier) = GradeScale(
    stringResource(R.string.nova), listOf("1", "2", "3", "4"), group?.toString(), { novaColor(it.toInt()) },
    group?.let { stringResource(com.nufo.app.ui.novaLabel(it)) }, modifier,
)

@Composable
fun EcoScoreScale(grade: String?, modifier: Modifier = Modifier) = GradeScale(
    stringResource(R.string.ecoscore), listOf("a", "b", "c", "d", "e"), grade, ::gradeColor,
    stringResource(R.string.eco_explain), modifier,
)

/** Animated ring that sweeps to the Nufo Score. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, diameter: Dp = 96.dp, stroke: Dp = 9.dp) {
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) score / 100f else 0f) }
    LaunchedEffect(score) { sweep.animateTo(score / 100f, tween(if (reduce) 0 else Motion.REVEAL, easing = Motion.EaseOut)) }
    val color = scoreColor(score)
    val track = LocalNufoColors.current.hairline
    val cd = stringResource(R.string.nufo_score_cd, score)
    Box(modifier.size(diameter).clearAndSetSemantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = stroke.toPx()
            val arcSize = Size(size.width - w, size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * sweep.value, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CalligraphNumber(score.toString(), style = MaterialTheme.typography.headlineMedium)
            Text("/ 100", style = MaterialTheme.typography.labelSmall, color = LocalNufoColors.current.textSecondary)
        }
    }
}

/** icon + label + value + unit, as in the reference nutrition cards. */
@Composable
fun NutrientCard(
    icon: ImageVector,
    label: String,
    value: String?,
    unit: String,
    tint: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    val na = stringResource(R.string.not_available)
    NufoCard(modifier) {
        Column(Modifier.padding(14.dp).clearAndSetSemantics { contentDescription = "$label ${value?.let { "$it $unit" } ?: na}" }) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            if (value == null) {
                Text(na, style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
            } else Row(verticalAlignment = Alignment.Bottom) {
                CalligraphNumber(value, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Small rounded chip with an icon, used for provenance ("Source", "Updated", "Complete"). */
@Composable
fun TrustChip(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.09f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelLarge,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

fun levelColor(level: Level) = when (level) { Level.Low -> GradeA; Level.Medium -> GradeC; Level.High -> GradeE }

@Composable
fun LevelDot(level: Level, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(levelColor(level)))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Formats a nutrient amount stored in grams for display: g at or above 1 (and 0), else mg / µg. */
fun formatGrams(grams: Double): Pair<String, String> = when {
    grams == 0.0 || grams >= 1 -> fmt(grams) to "g"
    grams >= 0.001 -> fmt(grams * 1000) to "mg"
    else -> fmt(grams * 1_000_000) to "µg"
}

/** Locale-aware: "1.5" in English, "1,5" in Greek. */
fun fmt(v: Double): String {
    val s = if (v >= 100) "%.0f".format(v) else "%.1f".format(v)
    return s.removeSuffix(".0").removeSuffix(",0")
}