package com.nufo.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.nufoSpring

/**
 * Two (or more) way switch: a green thumb slides under the chosen side on the app's spring and the labels cross-fade
 * between ink and white, like the language switch on the welcome screen. Each side is a tab for TalkBack.
 */
@Composable
fun PillSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var inner by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val segment = with(density) { (inner / options.size).toDp() }
    val x by animateDpAsState(segment * selected, nufoSpring(), label = "pillThumb")
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).background(LocalNufoColors.current.card)
            .padding(4.dp).onSizeChanged { inner = it.width },
    ) {
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val t by animateFloatAsState(if (on) 1f else 0f, nufoSpring(), label = "pillLabel")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .semantics { this.selected = on }
                        .clickable(role = Role.Tab, onClickLabel = label) { if (!on) onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onPrimary, t))
                }
            }
        }
    }
}
