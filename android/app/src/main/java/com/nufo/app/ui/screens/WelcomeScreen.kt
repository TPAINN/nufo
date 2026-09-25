package com.nufo.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import com.nufo.app.ui.theme.LocalAppReady
import com.nufo.app.ui.theme.Motion
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nufo.app.R
import com.nufo.app.ui.AppLanguage
import com.nufo.app.ui.components.Calligraph
import com.nufo.app.ui.components.TegakiLogo
import com.nufo.app.ui.currentDataLang
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.LocalReduceMotion

// Intro choreography on one clock (ms after the opening hand-off). Elements overlap instead of waiting
// for each other, so the button is usable at ~1.1 s rather than after the whole signature is written.
private const val GLOW_AT = 0
private const val FEATURES_AT = 250
private const val FEATURE_GAP = 90
private const val TAGLINE_AT = 900
private const val CTA_AT = 1100
private const val INTRO_MS = 1800

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val ready = LocalAppReady.current
    // Saved across the activity restart that a language switch causes, so switching never replays the intro.
    var played by rememberSaveable { mutableStateOf(false) }
    val clock = remember { Animatable(if (reduce || played) INTRO_MS.toFloat() else 0f) }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        if (clock.value < INTRO_MS) clock.animateTo(INTRO_MS.toFloat(), tween(INTRO_MS, easing = LinearEasing))
        played = true
    }
    /** 0..1 progress of a segment that starts at [at] ms, eased with the app curve. Read only inside draw/layer blocks. */
    fun progress(at: Int, duration: Int = Motion.SLOW) = Motion.EaseOut.transform(((clock.value - at) / duration).coerceIn(0f, 1f))

    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Soft green glow behind the signature; it blooms in as the first stroke starts.
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            val g = progress(GLOW_AT, Motion.SLOW * 2)
            alpha = g; scaleX = 0.85f + 0.15f * g; scaleY = scaleX
        }) {
            drawCircle(
                Brush.radialGradient(listOf(primary.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.33f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width / 2, size.height * 0.33f),
            )
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (AppLanguage.supported) {
                LanguageSwitch(Modifier.align(Alignment.End).padding(top = 12.dp).graphicsLayer { alpha = progress(GLOW_AT) })
            }
            Spacer(Modifier.weight(0.8f))
            TegakiLogo(Modifier.width(240.dp), color = primary, animate = !played, speed = 1.35f)
            Spacer(Modifier.height(14.dp))
            val showTagline by remember { derivedStateOf { clock.value >= TAGLINE_AT } }
            Box(Modifier.height(28.dp)) {
                if (showTagline) Calligraph(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium, color = LocalNufoColors.current.textSecondary, enterOnAppear = !played)
            }
            Spacer(Modifier.weight(0.55f))
            val features = listOf(
                Triple(FeatureGesture.Scan, R.string.welcome_f1_title, R.string.welcome_f1_body),
                Triple(FeatureGesture.Confirm, R.string.welcome_f2_title, R.string.welcome_f2_body),
                Triple(FeatureGesture.Lock, R.string.welcome_f3_title, R.string.welcome_f3_body),
            )
            features.forEachIndexed { i, (gesture, title, body) ->
                val at = FEATURES_AT + i * FEATURE_GAP
                Feature(
                    gesture, stringResource(title), stringResource(body),
                    // The icon gesture starts as the row settles.
                    gestureProgress = { ((clock.value - at - Motion.BASE) / 600f).coerceIn(0f, 1f) },
                    modifier = Modifier.graphicsLayer {
                        val r = progress(at)
                        alpha = r; translationY = (1 - r) * 24.dp.toPx()
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.graphicsLayer {
                    val c = progress(CTA_AT)
                    alpha = c; translationY = (1 - c) * 32.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                ) { Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.welcome_legal), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
/** Ελληνικά | English, applied instantly through Android's per-app language. */
@Composable
private fun LanguageSwitch(modifier: Modifier) {
    val context = LocalContext.current
    val current = currentDataLang()
    Row(
        modifier.clip(RoundedCornerShape(50)).background(LocalNufoColors.current.card).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("el" to "Ελληνικά", "en" to "English").forEach { (tag, label) ->
            val selected = current == tag
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { if (!selected) AppLanguage.set(context, tag) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** What each feature icon acts out once, as its row lands: a scan, a confirmation, a lock closing. */
private enum class FeatureGesture(val icon: ImageVector) { Scan(Icons.Outlined.QrCodeScanner), Confirm(Icons.Outlined.Verified), Lock(Icons.Outlined.Lock) }

@Composable
private fun Feature(gesture: FeatureGesture, title: String, body: String, gestureProgress: () -> Float, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(gesture.icon, null, tint = primary, modifier = Modifier.graphicsLayer {
                val t = gestureProgress()
                when (gesture) {
                    // Pulse: up to 118 % quickly, back to rest.
                    FeatureGesture.Confirm -> {
                        val s = if (t < 0.3f) Motion.EaseOut.transform(t / 0.3f) else 1f - Motion.EaseInOut.transform((t - 0.3f) / 0.7f)
                        scaleX = 1f + 0.18f * s; scaleY = scaleX
                    }
                    // Swings shut from -16 degrees.
                    FeatureGesture.Lock -> rotationZ = -16f * (1f - Motion.EaseOut.transform(t))
                    FeatureGesture.Scan -> Unit
                }
            })
            if (gesture == FeatureGesture.Scan) {
                // A scan line sweeps the icon top to bottom, fading at both ends.
                Box(
                    Modifier.width(26.dp).height(2.dp).graphicsLayer {
                        val t = gestureProgress()
                        translationY = (Motion.EaseInOut.transform(t) - 0.5f) * 26.dp.toPx()
                        alpha = if (t <= 0f || t >= 1f) 0f else minOf(t, 1f - t) * 4f
                    }.background(primary, RoundedCornerShape(1.dp)),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
}