package com.nufo.app.ui.screens

import com.nufo.app.ui.PhotoCorners
import com.nufo.app.ui.components.waited
import com.nufo.app.ui.components.WaitCaption
import com.nufo.app.ui.components.Wait
import com.nufo.app.ui.theme.Motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nufo.app.NufoViewModel
import com.nufo.app.typedBarcode
import androidx.compose.material3.OutlinedButton
import com.nufo.app.ui.photoKey
import com.nufo.app.R
import com.nufo.app.ui.categoryLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nufo.app.data.ALLERGENS
import com.nufo.app.data.SEARCH_CATEGORIES
import com.nufo.app.data.SearchFilters
import com.nufo.app.data.SearchHit
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.components.enterStagger
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.gradeColor
import com.nufo.app.ui.theme.novaColor
import com.nufo.app.ui.theme.nufoSpring

@Composable
fun SearchScreen(vm: NufoViewModel, onOpen: (SearchHit) -> Unit, onBarcode: (String) -> Unit) {
    val s by vm.search.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val typedBarcode = typedBarcode(s.query)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            NufoCard(Modifier.weight(1f), corner = 18.dp) {
                TextField(
                    s.query, vm::setQuery,
                    placeholder = { Text(stringResource(R.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (s.query.isNotEmpty()) IconButton({ vm.setQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); typedBarcode?.let(onBarcode) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            NufoCard(corner = 18.dp, onClick = { showFilters = !showFilters }, onClickLabel = stringResource(if (showFilters) R.string.filters_hide else R.string.filters_show)) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    BadgedBox(badge = { if (s.filters.isActive) Badge() }) {
                        Icon(Icons.Outlined.Tune, null, tint = if (showFilters || s.filters.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        AnimatedVisibility(showFilters, enter = expandVertically(nufoSpring()) + fadeIn(Motion.fadeInSpec()), exit = shrinkVertically(nufoSpring()) + fadeOut(Motion.fadeOutSpec())) {
            Filters(s.filters, settings?.allergenAlerts.orEmpty(), vm::setFilters)
        }
        AnimatedVisibility(typedBarcode != null, enter = expandVertically(nufoSpring()) + fadeIn(Motion.fadeInSpec()), exit = shrinkVertically(nufoSpring()) + fadeOut(Motion.fadeOutSpec())) {
            var last by remember { mutableStateOf("") }
            if (typedBarcode != null) last = typedBarcode
            NufoCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), onClick = { onBarcode(last) }, onClickLabel = stringResource(R.string.search_open_barcode, last)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.search_open_barcode, last), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LocalNufoColors.current.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(3.dp).fillMaxWidth()) {
            // Refreshing results already on screen: a thin bar, only if the refresh is not near-instant.
            // A first load gets skeleton rows below instead, never both.
            if (s.loading && s.hits.isNotEmpty() && waited(Wait.SHOW_AFTER_MS)) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(50)))
            }
        }
        when {
            s.query.isBlank() || typedBarcode != null -> if (typedBarcode == null) Hint(stringResource(R.string.search_hint))
            s.error != null -> Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text(stringResource(R.string.search_error), style = MaterialTheme.typography.bodyLarge, color = LocalNufoColors.current.textSecondary)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(vm::retrySearch, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.retry)) }
            }
            // First load: skeleton rows instead of a blank screen.
            s.loading && s.hits.isEmpty() -> if (waited(Wait.SHOW_AFTER_MS)) {
                Column(Modifier.padding(20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WaitCaption(
                        when {
                            waited(Wait.SLOW_AFTER_MS) -> stringResource(R.string.loading_slow)
                            waited(Wait.CAPTION_AFTER_MS) -> stringResource(R.string.search_loading)
                            else -> null
                        },
                    )
                    repeat(5) { HitSkeleton(Modifier.enterStagger(it)) }
                }
            }
            s.searched && s.hits.isEmpty() -> Hint(
                when {
                    s.offline -> stringResource(R.string.search_offline_none, s.query)
                    s.filters.isActive -> stringResource(R.string.search_none_filtered, s.query)
                    else -> stringResource(R.string.search_none, s.query)
                },
            )
            else -> LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.offline) item(key = "offline") {
                    Text(stringResource(R.string.search_offline), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
                }
                itemsIndexed(s.hits, key = { i, h -> "${h.source}:${h.barcode ?: h.name}:$i" }) { i, h ->
                    HitRow(h, { onOpen(h) }, Modifier.animateItem().enterStagger(i))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = LocalNufoColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp))
}

@Composable
private fun Filters(f: SearchFilters, alerts: Set<String>, onChange: (SearchFilters) -> Unit) {
    Column(Modifier.padding(top = 14.dp).animateContentSize()) {
        FilterRow(stringResource(R.string.f_origin)) {
            FilterChip(f.greekOnly, { onChange(f.copy(greekOnly = !f.greekOnly)) }, { Text(stringResource(R.string.f_greek_only)) },
                leadingIcon = { Text("GR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) })
        }
        FilterRow(stringResource(R.string.f_category)) {
            SEARCH_CATEGORIES.forEach { tag ->
                FilterChip(f.category == tag, { onChange(f.copy(category = if (f.category == tag) null else tag)) }, { Text(stringResource(categoryLabel(tag))) })
            }
        }
        FilterRow(stringResource(R.string.nutriscore)) {
            listOf("a", "b", "c", "d", "e").forEach { g ->
                FilterChip(g in f.grades, { onChange(f.copy(grades = if (g in f.grades) f.grades - g else f.grades + g)) }, { Text(g.uppercase()) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(gradeColor(g))) })
            }
        }
        FilterRow(stringResource(R.string.nova)) {
            (1..4).forEach { n ->
                FilterChip(n in f.nova, { onChange(f.copy(nova = if (n in f.nova) f.nova - n else f.nova + n)) }, { Text("NOVA $n") },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(novaColor(n))) })
            }
        }
        FilterRow(stringResource(R.string.f_diet)) {
            FilterChip(f.vegetarian, { onChange(f.copy(vegetarian = !f.vegetarian)) }, { Text(stringResource(R.string.diet_vegetarian)) })
            FilterChip(f.vegan, { onChange(f.copy(vegan = !f.vegan)) }, { Text(stringResource(R.string.diet_vegan)) })
            val mine = alerts.intersect(ALLERGENS.toSet())
            FilterChip(
                f.excludeAllergens.isNotEmpty(),
                { onChange(f.copy(excludeAllergens = if (f.excludeAllergens.isEmpty()) mine else emptySet())) },
                { Text(stringResource(if (mine.isEmpty()) R.string.f_set_allergens else R.string.f_exclude_mine)) },
                enabled = mine.isNotEmpty(),
            )
        }
        if (f.needsOffTags) Text(
            stringResource(R.string.f_note),
            style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FilterRow(title: String, chips: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
}

@Composable
private fun HitRow(h: SearchHit, onClick: () -> Unit, modifier: Modifier) {
    val soldGr = stringResource(R.string.sold_in_greece)
    NufoCard(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.open_product, h.name)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductThumb(h.imageUrl, h.name, Modifier.size(64.dp), PhotoCorners(14.dp), sharedKey = photoKey(h.barcode, h.name))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(h.brand, h.caloriesPer100g?.let { stringResource(R.string.kcal_per_100g, it.toInt().toString()) }).joinToString(" · ").ifEmpty { h.source },
                    style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (h.soldInGreece) {
                        Text("GR", style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp, vertical = 1.dp)
                                .semantics { contentDescription = soldGr })
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(h.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (h.nutriscoreGrade != null) gradeColor(h.nutriscoreGrade) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(h.nutriscoreGrade?.uppercase() ?: "–", style = MaterialTheme.typography.titleMedium,
                    color = if (h.nutriscoreGrade != null) Color.White else LocalNufoColors.current.textSecondary)
            }
        }
    }
}

@Composable
private fun HitSkeleton(modifier: Modifier) {
    val c = LocalNufoColors.current.hairline
    NufoCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(c))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.75f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c))
            }
        }
    }
}