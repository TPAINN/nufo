package com.nufo.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.Motion
import com.nufo.app.ui.theme.nufoSpring
import kotlinx.coroutines.delay

/**
 * The app's own toast, driven by a [SnackbarHostState] so callers keep `showSnackbar` and its undo result.
 * A card in the app's surface colour that springs up from the bottom and slides back down, instead of
 * Material's dark bar with its scale-in.
 */
@Composable
fun NufoToastHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val data = state.currentSnackbarData
    val a11y = LocalAccessibilityManager.current
    // Timing lives here because this replaces SnackbarHost, which owned it. Longer when the user asked for
    // longer timeouts in Android's accessibility settings, and when there is an action to reach.
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        val base = when (d.visuals.duration) {
            SnackbarDuration.Short -> 4_000L
            SnackbarDuration.Long -> 10_000L
            SnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        val hasAction = d.visuals.actionLabel != null
        delay(a11y?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = hasAction) ?: base)
        d.dismiss()
    }
    AnimatedContent(
        data,
        transitionSpec = {
            // Unclipped: it rises from behind the bottom bar instead of being cut off by a hard line at its own edge.
            ((slideInVertically(nufoSpring()) { it } + fadeIn(Motion.fadeInSpec())) togetherWith
                (slideOutVertically(nufoSpring()) { it / 2 } + fadeOut(Motion.fadeOutSpec()))) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomCenter,
        label = "toast",
        modifier = modifier,
    ) { d -> if (d != null) NufoToast(d) else Box(Modifier.fillMaxWidth()) }
}

@Composable
private fun NufoToast(data: SnackbarData) {
    val colors = LocalNufoColors.current
    val shape = RoundedCornerShape(18.dp)
    val action = data.visuals.actionLabel
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape).background(colors.card).border(1.dp, colors.hairline, shape)
            .heightIn(min = 56.dp).padding(start = 14.dp, end = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(if (action != null) Icons.Outlined.DeleteOutline else Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        if (action != null) TextButton(data::performAction) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else Spacer(Modifier.width(8.dp))
    }
}
