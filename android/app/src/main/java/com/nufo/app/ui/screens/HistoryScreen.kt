package com.nufo.app.ui.screens

import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.nufo.app.data.mealKcal
import com.nufo.app.data.isMeal
import com.nufo.app.data.mealDays
import com.nufo.app.data.dayName
import com.nufo.app.data.DayName
import com.nufo.app.data.MealDay
import com.nufo.app.ui.components.PillSwitch
import androidx.compose.foundation.layout.heightIn
import com.nufo.app.ui.components.NufoToastHost
import com.nufo.app.ui.theme.Motion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
fun HistoryScreen(vm: NufoViewModel, onOpen: (Product) -> Unit, onScan: () -> Unit, onPhoto: () -> Unit) {
    val loaded by vm.history.collectAsStateWithLifecycle()
    val history = loaded.orEmpty()
    // Two diaries in one tab: what you scanned or searched, and the plates you photographed, by day.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val products = remember(history) { history.filterNot { it.isMeal } }
    val days = remember(history) { mealDays(history) }
    val shown = if (tab == 0) products.size else days.size
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
            // Fixed height: "Clear all" sets the row's height, so when it faded out the title (and the screen below it)
            // jumped up a few pixels in one frame.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                AnimatedVisibility(history.isNotEmpty(), enter = fadeIn(Motion.fadeInSpec()), exit = fadeOut(Motion.fadeOutSpec())) {
                    TextButton({ confirmClear = true }) { Text(stringResource(R.string.history_clear_all)) }
                }
            }
            PillSwitch(
                listOf(stringResource(R.string.history_tab_search), stringResource(R.string.history_tab_meals)), tab, { tab = it },
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )
            // Tab, opening, empty and filled all cross-fade, so switching or deleting the last item eases into place.
            // State: 0 opening, 1 / 2 products empty / filled, 11 / 12 meals empty / filled.
            val state = when { loaded == null -> 0; tab == 0 -> if (shown == 0) 1 else 2; else -> if (shown == 0) 11 else 12 }
            AnimatedContent(state, transitionSpec = { Motion.crossfade() }, label = "history", modifier = Modifier.weight(1f)) { kind ->
                when (kind) {
                    11 -> Box {
                        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.meals_empty_title), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.meals_empty_body), style = MaterialTheme.typography.bodyLarge,
                                color = LocalNufoColors.current.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(20.dp))
                            Button(onPhoto, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.meals_photo)) }
                        }
                    }
                    12 -> Column {
                        Text(stringResource(R.string.history_swipe), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp))
                        MealDays(days, onOpen = onOpen, onRemove = ::remove)
                    }
                    0 -> Box {
                        // Database still opening: skeleton rows, never a false "no scans yet".
                        Column(Modifier.padding(20.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(4) { Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(22.dp)).background(LocalNufoColors.current.hairline)) }
                        }
                    }
                    1 -> Box {
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
                    }
                    else -> Column {
                        Text(stringResource(R.string.history_swipe), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp))
                        LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(products, key = { _, p -> p.key }) { i, p ->
                                SwipeRow(p, { onOpen(p) }, { remove(p) }, Modifier.animateItem().enterStagger(i))
                            }
                        }
                    }
                }
            }

        }
        NufoToastHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(pluralStringResource(R.plurals.history_clear_body, history.size, history.size)) },
        confirmButton = { TextButton({ vm.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.clear), color = GradeE) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Swipe left (or TalkBack's custom action) removes the row; shared by products and meals so both behave alike. */
@Composable
private fun SwipeToRemove(onDelete: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val removeLabel = stringResource(R.string.history_remove_action)
    // Not saveable on purpose: the list keeps saved state per row key, so a row restored by Undo used to come back
    // "already swiped away" and remove itself again. A swipe is a gesture in the moment; nothing to restore.
    val state = remember { androidx.compose.material3.SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.5f }) }
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
    ) { content() }
}

@Composable
private fun SwipeRow(p: Product, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val na = stringResource(R.string.n_a)
    val (sp, sc, sf) = Triple(stringResource(R.string.short_p), stringResource(R.string.short_c), stringResource(R.string.short_f))
    SwipeToRemove(onDelete, modifier) {
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
                val score = remember(p) { com.nufo.app.data.Scoring.score(p)?.score }
                val scoreCd = score?.let { stringResource(R.string.nufo_score_cd, it) } ?: stringResource(R.string.score_insufficient)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(score?.let { scoreColor(it) } ?: LocalNufoColors.current.hairline)
                        .clearAndSetSemantics { contentDescription = scoreCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(score?.toString() ?: "–", style = MaterialTheme.typography.titleMedium, color = if (score != null) Color.White else LocalNufoColors.current.textSecondary)
                }
            }
        }
    }
}

/** The meal diary: a section per day ("Σήμερα", the date quieter beside it), newest first. */
@Composable
private fun MealDays(days: List<MealDay>, onOpen: (Product) -> Unit, onRemove: (Product) -> Unit) {
    val today = remember { java.time.LocalDate.now() }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        days.forEachIndexed { di, day ->
            item(key = "day-${day.date}") {
                DayHeader(day, today, Modifier.animateItem().padding(top = if (di == 0) 0.dp else 14.dp, bottom = 2.dp))
            }
            itemsIndexed(day.meals, key = { _, m -> m.key }) { i, m ->
                MealRow(m, { onOpen(m) }, { onRemove(m) }, Modifier.animateItem().enterStagger(di * 3 + i))
            }
        }
    }
}

@Composable
private fun DayHeader(day: MealDay, today: java.time.LocalDate, modifier: Modifier) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val name = when (dayName(day.date, today)) {
        DayName.Today -> stringResource(R.string.day_today)
        DayName.Yesterday -> stringResource(R.string.day_yesterday)
        else -> day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase(locale) }
    }
    // The date sits beside the day, quieter: people think in days, not dates. A past year is spelled out.
    val pattern = if (day.date.year == today.year) "d MMMM" else "d MMMM yyyy"
    val date = day.date.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(8.dp))
        Text(date, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary,
            modifier = Modifier.padding(bottom = 3.dp).weight(1f))
        Text("${day.kcal} kcal", style = MaterialTheme.typography.labelLarge, color = LocalNufoColors.current.textSecondary)
    }
}

@Composable
private fun MealRow(m: Product, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val time = remember(m.lastUpdated) {
        java.time.Instant.ofEpochMilli(m.lastUpdated).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }
    SwipeToRemove(onDelete, modifier) {
        NufoCard(Modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = stringResource(R.string.open_product, m.displayName())) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // A plate photo fills its tile; the packshot thumbnail (white frame, fitted) is for packaged products.
                coil3.compose.AsyncImage(
                    m.imageUrl, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val foods = m.recipe.joinToString(" · ") { it.name }.ifEmpty { m.ingredientsText.orEmpty() }
                    if (foods.isNotEmpty()) Text(foods, style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(listOfNotNull(time, m.mealKcal?.let { "$it kcal" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
