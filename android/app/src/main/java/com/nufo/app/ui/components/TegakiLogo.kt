package com.nufo.app.ui.components


import com.nufo.app.ui.theme.LocalAppReady
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nufo.app.ui.theme.LocalReduceMotion

/**
 * "Nufo" handwritten in Caveat, drawn stroke by stroke.
 *
 * The strokes are exported from Tegaki (github.com/gkurt/tegaki) with
 * `npx tegaki "Nufo" --font caveat --mode once --pressure 0`: each entry is the glyph skeleton,
 * its pen width, start time and duration in seconds. Tegaki's own easing curve is reused so the
 * native replay matches the web renderer.
 */
private class TegakiStroke(val begin: Float, val duration: Float, val width: Float, val points: String)

private val NUFO_STROKES = listOf(
    TegakiStroke(0f, 0.68f, 11.21f, "31.59 125.82 31.59 124.72 35.12 115.46 36.89 109.07 42.62 93.85 48.13 81.72 52.1 70.26 53.2 68.49 56.73 66.51 56.95 63.64 56.73 66.73 59.16 68.71 59.38 70.26 59.38 96.94 62.46 113.7 64.67 119.87 65.99 121.85 69.08 122.74 71.5 121.41 75.25 113.25 79.66 99.58 83.85 83.93 85.18 75.77 90.03 54.16"),
    TegakiStroke(0.78f, 0.22f, 10.12f, "101.58 89.11 101.38 90.44 98.82 95.46 96.87 100.07 95.23 104.98 94.62 108.16 94.41 112.66 94.72 113.89 96.05 115.63 97.18 116.15 98.31 116.25 102.09 115.43 103.73 114.61 107.21 111.85 108.75 110.21 111.93 108.98 113.05 105.5 115.3 100.38 116.53 96.79 116.94 93.92 117.86 91.77 118.07 89.73"),
    TegakiStroke(1.16f, 0.04f, 10.03f, "111.93 108.98 112.54 109.49 112.54 113.59 113.15 116.15 114.08 117.99 116.02 120.34"),
    TegakiStroke(1.3f, 0.18f, 10.17f, "128.24 130.6 127.6 126.35 128.24 119.34 130.79 106.6 133.55 96.19 136.52 93.21 144.39 92.58 149.7 91.51"),
    TegakiStroke(1.62f, 0.18f, 10.4f, "136.74 93 136.52 91.73 137.37 86.42 142.9 73.67 146.72 67.72 150.12 63.9 154.58 60.29 157.34 59.65 160.1 60.29 160.95 61.35 161.17 63.05 159.89 68.78"),
    TegakiStroke(1.9f, 0.28f, 9.68f, "158.66 112.76 158.96 108.78 159.75 106.29 162.54 100.71 166.82 94.74 169.31 92.15 171.8 90.26 173.09 89.56 176.98 88.87 180.46 89.86 181.76 91.26 182.45 94.94 182.25 98.22 181.76 100.81 181.16 102.5 179.37 105.99 177.18 109.27 173.69 113.36 170.51 115.94 167.52 117.74 166.03 118.23 163.34 118.63 161.94 118.33 160.35 117.34 159.16 115.25 158.66 112.76"),
)

// Tight crop of Tegaki's 201.3 x 180 viewBox around the ink.
private const val VIEW_X = 22f
private const val VIEW_Y = 48f
private const val VIEW_W = 170f
private const val VIEW_H = 92f
private val TOTAL = NUFO_STROKES.maxOf { it.begin + it.duration }
private val TegakiEase = CubicBezierEasing(0.33f, 0f, 0.15f, 1f)

private fun parse(points: String): Path {
    val v = points.trim().split(' ').map { it.toFloat() }
    return Path().apply {
        moveTo(v[0], v[1])
        for (i in 2 until v.size step 2) lineTo(v[i], v[i + 1])
    }
}

@Composable
fun TegakiLogo(
    modifier: Modifier = Modifier,
    color: Color,
    animate: Boolean = true,
    speed: Float = 1f,
    onFinished: () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val paths = remember { NUFO_STROKES.map { it to PathMeasure().apply { setPath(parse(it.points), false) } } }
    val clock = remember { Animatable(if (animate && !reduce) 0f else TOTAL) }
    // Writes only once the opening hand-off is done, so the first stroke is never drawn under the splash.
    val ready = LocalAppReady.current
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        if (clock.value < TOTAL) {
            clock.animateTo(TOTAL, tween((TOTAL * 1000 / speed).toInt(), easing = LinearEasing))
        }
        onFinished()
    }
    Canvas(
        modifier
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription = "Nufo" },
    ) {
        val s = size.width / VIEW_W
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(-VIEW_X, -VIEW_Y) {
                val t = clock.value
                for ((stroke, measure) in paths) {
                    val raw = ((t - stroke.begin) / stroke.duration).coerceIn(0f, 1f)
                    if (raw <= 0f) continue
                    val seg = Path()
                    measure.getSegment(0f, measure.length * TegakiEase.transform(raw), seg, true)
                    drawPath(seg, color, style = Stroke(stroke.width * 0.66f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
