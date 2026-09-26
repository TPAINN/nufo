package com.nufo.app.ui.screens

import com.nufo.app.ui.PhotoCorners
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nufo.app.NufoViewModel
import com.nufo.app.R
import com.nufo.app.ui.displayName
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nufo.app.data.Product
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.components.enterStagger
import com.nufo.app.ui.components.fmt
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.scoreColor
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: NufoViewModel, onOpen: (Product) -> Unit, onScan: () -> Unit) {
    val loaded by vm.history.collectAsStateWithLifecycle()
    val history = loaded.orEmpty()
    var confirmClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val removedFmt = stringResource(R.string.history_removed, "%s")
    val undo = stringResource(R.string.undo)
    fun remove(p: Product) {
        vm.delete(p)
        scope.launch {
            if (snackbar.showSnackbar(removedFmt.replace("%s", p.name), undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.restore(p)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton({ confirmClear = true }) { Text(stringResource(R.string.history_clear_all)) }
            }
            if (loaded == null) {
                // Database still opening: skeleton rows, never a false "no scans yet".
                Column(Modifier.padding(20.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(22.dp)).background(LocalNufoColors.current.hairline)) }
                }
            } else if (history.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyLarge,
                        color = LocalNufoColors.current.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Button(onScan, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.history_scan)) }
                }
            } else {
                Text(stringResource(R.string.history_swipe), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp))
                LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(history, key = { _, p -> p.key }) { i, p ->
                        SwipeRow(p, { onOpen(p) }, { remove(p) }, Modifier.animateItem().enterStagger(i))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(pluralStringResource(R.plurals.history_clear_body, history.size, history.size)) },
        confirmButton = { TextButton({ vm.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.clear), color = GradeE) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SwipeRow(p: Product, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val removeLabel = stringResource(R.string.history_remove_action)
    val na = stringResource(R.string.n_a)
    val (sp, sc, sf) = Triple(stringResource(R.string.short_p), stringResource(R.string.short_c), stringResource(R.string.short_f))
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            val bg by animateColorAsState(if (state.targetValue == SwipeToDismissBoxValue.EndToStart) GradeE else GradeE.copy(alpha = 0.4f), label = "swipeBg")
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(bg).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Outlined.Delete, null, tint = Color.White)
            }
        },
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onDelete(); true }) },
    ) {
        NufoCard(Modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = stringResource(R.string.open_product, p.displayName())) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(p, Modifier.size(64.dp), PhotoCorners(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val n = p.nutritionPer100g
                    Text(
                        listOf(
                            n.protein?.let { "$sp ${fmt(it)}g" } ?: "$sp $na",
                            n.carbs?.let { "$sc ${fmt(it)}g" } ?: "$sc $na",
                            n.fat?.let { "$sf ${fmt(it)}g" } ?: "$sf $na",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // Calories lead the last line, so the macro line always fits all three values.
                    Text(
                        (n.calories?.let { "${it.toInt()} kcal " } ?: "") + stringResource(R.string.history_per100, p.brand ?: p.source),
                        style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val scored = com.nufo.app.data.Scoring.canScore(p)
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (scored) scoreColor(p.nufoScore) else LocalNufoColors.current.hairline), contentAlignment = Alignment.Center) {
                    Text(if (scored) p.nufoScore.toString() else "–", style = MaterialTheme.typography.titleMedium, color = if (scored) Color.White else LocalNufoColors.current.textSecondary)
                }
            }
        }
    }
}