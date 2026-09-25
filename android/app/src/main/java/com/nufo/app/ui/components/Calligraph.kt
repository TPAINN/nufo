package com.nufo.app.ui.components

import com.nufo.app.ui.theme.Motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import com.nufo.app.ui.theme.LocalReduceMotion

/*
 * Native port of Calligraph (calligraph.raphaelsalaja.com): fluid, per-character text transitions.
 * - Text mode: changed characters rise in with a short stagger, like Calligraph's default.
 * - Number mode: each digit is a slot that rolls up or down toward its new value.
 * Screen readers get the plain final string, never the per-glyph pieces.
 */

private val glyphSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Calligraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    /** When true the text writes itself in on first appearance instead of popping in. */
    enterOnAppear: Boolean = true,
    staggerMs: Int = 28,
) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(if (enterOnAppear && !reduce) "" else text) }
    LaunchedEffect(text) { shown = text }
    // Words (with their trailing space) are the wrapping unit, so long names break between words like normal text.
    val words = Regex("""\S+\s*|\s+""").findAll(text).map { it.range }.toList() +
        listOfNotNull((text.length until shown.length).takeIf { !it.isEmpty() })
    FlowRow(modifier.clearAndSetSemantics { contentDescription = text }) {
        for (word in words) Row(verticalAlignment = Alignment.Bottom) {
            for (i in word) {
                val ch = shown.getOrNull(i)?.toString() ?: ""
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val delay = if (reduce) 0 else i * staggerMs
                        (slideInVertically(glyphSpring) { it / 2 } + fadeIn(Motion.fadeInSpec(delay)) + scaleIn(tween(Motion.BASE, delay, Motion.EaseOut), 0.9f))
                            .togetherWith(slideOutVertically(glyphSpring) { -it / 2 } + fadeOut(Motion.fadeOutSpec()))
                    },
                    label = "glyph$i",
                ) { c -> Text(c, style = style, color = color) }
            }
        }
    }
}

/** Rolling-digit number, e.g. calories. Non-digits (".", ",", "%") morph like text. */
@Composable
fun CalligraphNumber(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val reduce = LocalReduceMotion.current
    // Start every digit at 0 so numbers count up into place on first appearance.
    var shown by remember { mutableStateOf(if (reduce) value else value.map { if (it.isDigit()) '0' else it }.joinToString("")) }
    LaunchedEffect(value) { shown = value }
    Row(modifier.clearAndSetSemantics { contentDescription = value }, verticalAlignment = Alignment.Bottom) {
        shown.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState.isDigit() && initialState.isDigit() && targetState > initialState
                    val dir = if (up) 1 else -1
                    val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.8f, stiffness = 260f + i * 40f)
                    (slideInVertically(spec) { dir * it } + fadeIn(Motion.fadeInSpec()))
                        .togetherWith(slideOutVertically(spec) { -dir * it } + fadeOut(Motion.fadeOutSpec()))
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}