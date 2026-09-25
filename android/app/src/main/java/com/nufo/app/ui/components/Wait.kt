package com.nufo.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * How long waits are presented, everywhere:
 * - nothing for the first [SHOW_AFTER_MS], so an answer that is almost instant never flashes a loader;
 * - after [CAPTION_AFTER_MS], a line saying what is actually happening (real steps, not a spinner loop);
 * - after [SLOW_AFTER_MS], the line admits it is slow;
 * - at 15 s the request gives up (FoodRepository.GIVE_UP_MS) and the user gets a saved copy or Retry.
 */
object Wait {
    const val SHOW_AFTER_MS = 400L
    const val CAPTION_AFTER_MS = 1_000L
    const val SLOW_AFTER_MS = 7_000L
}

/** False until [ms] after this first composed. Compose it only while the wait is running. */
@Composable
fun waited(ms: Long): Boolean {
    var passed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(ms); passed = true }
    return passed
}

/** The line under a loader; crossfades as the text changes and is announced politely by screen readers. */
@Composable
fun WaitCaption(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.height(20.dp)) {
        AnimatedContent(text, transitionSpec = { Motion.crossfade() }, label = "waitCaption") { t ->
            if (t != null) Text(
                t, style = MaterialTheme.typography.labelLarge, color = LocalNufoColors.current.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}