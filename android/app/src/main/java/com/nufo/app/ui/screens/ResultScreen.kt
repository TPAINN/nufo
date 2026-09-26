package com.nufo.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import com.nufo.app.ui.components.waited
import com.nufo.app.ui.components.WaitCaption
import com.nufo.app.ui.components.Wait
import com.nufo.app.data.LookupStep
import com.nufo.app.ui.theme.Motion

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nufo.app.NufoViewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sell
import com.nufo.app.PricesState
import com.nufo.app.data.Identified
import com.nufo.app.R
import com.nufo.app.data.SearchHit
import com.nufo.app.ui.photoKey
import com.nufo.app.ResultState
import com.nufo.app.data.Diet
import com.nufo.app.data.Level
import com.nufo.app.data.Micronutrients
import com.nufo.app.data.Nutrition
import com.nufo.app.data.OffParser
import com.nufo.app.data.Product
import com.nufo.app.data.Scoring
import com.nufo.app.data.Units
import com.nufo.app.data.Verdict
import com.nufo.app.data.dietNote
import com.nufo.app.data.matchingAllergens
import com.nufo.app.ui.components.Calligraph
import com.nufo.app.ui.components.CalligraphNumber
import com.nufo.app.ui.components.EcoScoreScale
import com.nufo.app.ui.components.LevelDot
import com.nufo.app.ui.components.NovaScale
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.components.NutriScoreScale
import com.nufo.app.ui.components.NutrientCard
import com.nufo.app.ui.components.Pill
import com.nufo.app.ui.components.ScoreRing
import com.nufo.app.ui.components.SectionTitle
import com.nufo.app.ui.components.TrustChip
import com.nufo.app.ui.components.enterStagger
import com.nufo.app.ui.components.fmt
import com.nufo.app.ui.components.formatGrams
import com.nufo.app.ui.currentDataLang
import com.nufo.app.ui.dietNoteText
import com.nufo.app.ui.displayName
import com.nufo.app.ui.label
import com.nufo.app.ui.theme.GradeA
import com.nufo.app.ui.theme.GradeC
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.LocalReduceMotion
import com.nufo.app.ui.theme.nufoSpring
import com.nufo.app.ui.theme.scoreTextColor
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun ResultScreen(vm: NufoViewModel, onBack: () -> Unit, onSearch: (String) -> Unit) {
    val state by vm.result.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val saved = history.orEmpty()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            (state as? ResultState.Loaded)?.product?.let { p ->
                val isSaved = saved.any { it.key == p.key }
                IconButton({ vm.toggleSaved(p) }) {
                    AnimatedContent(isSaved, label = "saved") { s ->
                        Icon(
                            if (s) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            stringResource(if (s) R.string.remove_history else R.string.save_history),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val text = shareText(p)
                IconButton({
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), shareTitle))
                }) { Icon(Icons.Outlined.Share, shareTitle) }
            }
        }
        AnimatedContent(
            state,
            contentKey = { it::class },
            transitionSpec = { Motion.crossfade() },
            label = "result",
        ) { s ->
            when (s) {
                is ResultState.Loading -> LoadingSkeleton(s.preview, s.step)
                is ResultState.NotFound -> NotFound(s.barcode, s.identified, onSearch)
                is ResultState.Error -> Message(
                    if (s.offline) Icons.Outlined.WifiOff else Icons.Outlined.CloudOff,
                    stringResource(if (s.offline) R.string.error_offline_title else R.string.error_server_title),
                    stringResource(if (s.offline) R.string.error_offline_body else R.string.error_server_body),
                ) {
                    Button(s.retry, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.retry)) }
                }
                is ResultState.Loaded -> ProductDetail(
                    s.product, s.cachedAt, vm, settings?.units ?: Units.Metric, settings?.diet ?: Diet.None, settings?.allergenAlerts.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun shareText(p: Product) = buildString {
    append(p.displayName()); p.brand?.let { append(" — $it") }; append("\n")
    if (Scoring.canScore(p)) append(stringResource(R.string.share_score, p.nufoScore))
    p.nutriscoreGrade?.let { append(" · Nutri-Score ${it.uppercase()}") }
    p.novaGroup?.let { append(" · NOVA $it") }
    p.nutritionPer100g.calories?.let { append("\n" + stringResource(R.string.share_kcal, it.toInt())) }
    append("\n" + stringResource(R.string.trust_source, p.source))
}

@Composable
private fun NotFound(barcode: String?, identified: Identified?, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    Message(
        if (identified != null) Icons.Outlined.Info else Icons.Outlined.SearchOff,
        stringResource(if (identified != null) R.string.identified_title else R.string.not_found_title),
        when {
            identified != null -> stringResource(R.string.identified_body, identified.source, identified.name)
            barcode != null -> stringResource(R.string.not_found_body, barcode)
            else -> stringResource(R.string.not_found_body_generic)
        },
    ) {
        if (identified != null) {
            ProductThumb(identified.imageUrl, identified.name, Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(16.dp))
            Button({ onSearch(identified.name) }, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.identified_search, identified.name)) }
        } else Button({ onSearch("") }, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.not_found_search)) }
        if (barcode != null) {
            Spacer(Modifier.height(10.dp))
            // Contributing on the Open Food Facts site makes the product available to everyone, Nufo included.
            OutlinedButton(
                { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://world.openfoodfacts.org/cgi/product.pl?type=add&code=$barcode"))) },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.not_found_add))
            }
        }
    }
}

@Composable
private fun Message(icon: ImageVector, title: String, body: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = LocalNufoColors.current.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

@Composable
private fun LoadingSkeleton(preview: SearchHit?, step: LookupStep?) {
    val reduce = LocalReduceMotion.current
    val pulse = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        if (!reduce) while (true) { pulse.animateTo(1f, tween(650)); pulse.animateTo(0.5f, tween(650)) }
    }
    // Placeholders stay hidden for the first moments, so a fast answer never flashes a skeleton.
    // The tapped product's own photo and name are content, not placeholders, and show at once.
    val placeholders by animateFloatAsState(if (waited(Wait.SHOW_AFTER_MS)) 1f else 0f, Motion.fadeInSpec(), label = "placeholders")
    val c = LocalNufoColors.current.hairline.copy(alpha = pulse.value * placeholders)
    val caption = when {
        waited(Wait.SLOW_AFTER_MS) -> stringResource(R.string.loading_slow)
        waited(Wait.CAPTION_AFTER_MS) -> stringResource(
            when (step) {
                LookupStep.Usda -> R.string.loading_step_usda
                LookupStep.OtherDatabases -> R.string.loading_step_other
                LookupStep.OpenFoodFacts, null -> R.string.loading_step_off
            },
        )
        else -> null
    }
    val cd = stringResource(R.string.loading_nutrition)
    Column(Modifier.padding(horizontal = 20.dp).semantics { contentDescription = cd }) {
        if (preview?.imageUrl != null) {
            // The row's photo lands in place at once, so the open transition reads as one continuous motion.
            NufoCard(Modifier.fillMaxWidth(), corner = 28.dp) {
                ProductThumb(preview.imageUrl, preview.name, Modifier.fillMaxWidth().height(260.dp), hero = true,
                    sharedKey = photoKey(preview.barcode, preview.name))
            }
        } else Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).background(c))
        Spacer(Modifier.height(18.dp))
        if (preview != null) Text(preview.name, style = MaterialTheme.typography.headlineMedium)
        else Box(Modifier.fillMaxWidth(0.7f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(c))
        WaitCaption(caption, Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.width(96.dp).height(28.dp).clip(RoundedCornerShape(50)).background(c)) }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(22.dp)).background(c))
    }
}
private enum class Portion { Per100, Serving, Bowl, Plate, Custom }
private enum class Tab(val label: Int) {
    Nutrition(R.string.tab_nutrition), Vitamins(R.string.tab_vitamins), Ingredients(R.string.tab_ingredients),
    Additives(R.string.tab_additives), Environment(R.string.tab_environment),
}

private const val BOWL_G = 250.0
private const val PLATE_G = 350.0

private fun weightLabel(grams: Double, units: Units) =
    if (units == Units.Imperial) "${fmt(grams / 28.3495)} oz" else "${fmt(grams)} g"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDetail(p: Product, cachedAt: Long?, vm: NufoViewModel, units: Units, diet: Diet, alerts: Set<String>) {
    val lang = currentDataLang()
    var portion by rememberSaveable(p.key) { mutableStateOf(Portion.Per100) }
    var customGrams by rememberSaveable(p.key) { mutableFloatStateOf(150f) }
    var tab by rememberSaveable(p.key) { mutableStateOf(Tab.Nutrition) }
    var method by remember { mutableStateOf(false) }
    val hasServing = p.servingGrams != null || p.nutritionPerServing != null
    val grams: Double? = when (portion) {
        Portion.Per100 -> 100.0
        Portion.Serving -> p.servingGrams
        Portion.Bowl -> BOWL_G
        Portion.Plate -> PLATE_G
        Portion.Custom -> customGrams.toDouble()
    }
    val nutrition: Nutrition = when {
        portion == Portion.Serving && p.servingGrams == null -> p.nutritionPerServing ?: p.nutritionPer100g
        else -> p.nutritionPer100g.scaled((grams ?: 100.0) / 100)
    }
    val portionText = when (portion) {
        Portion.Serving -> p.servingSize ?: grams?.let { weightLabel(it, units) } ?: stringResource(R.string.one_serving)
        else -> weightLabel(grams ?: 100.0, units)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 28.dp)) {
        NufoCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().enterStagger(0), corner = 28.dp) {
            // Without a photo a tall empty box looks unfinished; keep the placeholder compact.
            ProductThumb(p, Modifier.fillMaxWidth().height(if (p.imageUrl == null) 120.dp else 260.dp), hero = true)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp).enterStagger(1)) {
            Calligraph(p.displayName(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }, staggerMs = 12)
            val meta = listOfNotNull(p.brand, p.quantity).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = LocalNufoColors.current.textSecondary)
        }

        if (cachedAt != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.08f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(18.dp), tint = LocalNufoColors.current.textSecondary)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.cached_notice, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(cachedAt))),
                    style = MaterialTheme.typography.bodySmall, color = LocalNufoColors.current.textSecondary,
                )
            }
        }

        // Provenance: where the numbers come from, how fresh and how complete they are.
        Spacer(Modifier.height(12.dp))
        FlowRow(
            Modifier.padding(horizontal = 20.dp).enterStagger(2),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TrustChip(Icons.Outlined.Verified, stringResource(R.string.trust_source, p.source))
            if (p.lastUpdated > 0) TrustChip(
                Icons.Outlined.Schedule,
                stringResource(R.string.trust_updated, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.lastUpdated))),
            )
            p.completeness?.let { TrustChip(Icons.Outlined.DataUsage, stringResource(R.string.trust_completeness, (it * 100).toInt())) }
            if (p.soldInGreece) TrustChip(Icons.Outlined.Place, stringResource(R.string.sold_in_greece))
            if (p.source != OffParser.SOURCE && lang == "el") TrustChip(Icons.Outlined.Info, stringResource(R.string.usda_english), tint = GradeC)
        }

        val allergenHits = p.matchingAllergens(alerts)
        val note = dietNote(p, diet)
        if (allergenHits.isNotEmpty() || note != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(GradeE.copy(alpha = 0.1f)).padding(14.dp).enterStagger(3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.WarningAmber, null, tint = GradeE)
                Spacer(Modifier.width(10.dp))
                Column {
                    if (allergenHits.isNotEmpty()) Text(
                        stringResource(R.string.contains, allergenHits.joinToString { p.tagName(it, lang) }),
                        style = MaterialTheme.typography.titleMedium, color = GradeE,
                    )
                    note?.let { Text(dietNoteText(it), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ScoreCard(p, onMethod = { method = true }, modifier = Modifier.padding(horizontal = 20.dp).enterStagger(4))

        Spacer(Modifier.height(22.dp))
        SectionTitle(stringResource(R.string.portion), Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Portion.entries.filter { it != Portion.Serving || hasServing }.forEach { opt ->
                val label = when (opt) {
                    Portion.Per100 -> stringResource(if (units == Units.Imperial) R.string.portion_100g_imperial else R.string.portion_100g)
                    Portion.Serving -> p.servingGrams?.let { stringResource(R.string.portion_serving_w, weightLabel(it, units)) }
                        ?: stringResource(R.string.portion_serving)
                    Portion.Bowl -> stringResource(R.string.portion_bowl, weightLabel(BOWL_G, units))
                    Portion.Plate -> stringResource(R.string.portion_plate, weightLabel(PLATE_G, units))
                    Portion.Custom -> stringResource(R.string.portion_custom)
                }
                FilterChip(
                    portion == opt, { portion = opt }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White),
                )
            }
        }
        if (portion == Portion.Custom) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(weightLabel(customGrams.toDouble(), units), style = MaterialTheme.typography.titleMedium)
                Slider(customGrams, { customGrams = (it / 5).toInt() * 5f }, valueRange = 10f..1000f)
            }
        }

        Spacer(Modifier.height(6.dp))
        NufoCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calories_for, portionText), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
                    val kcal = nutrition.calories
                    if (kcal != null) Row(verticalAlignment = Alignment.Bottom) {
                        CalligraphNumber("%.0f".format(kcal), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.kcal), style = MaterialTheme.typography.titleMedium,
                            color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(bottom = 8.dp),
                        )
                    } else Text(stringResource(R.string.not_available), style = MaterialTheme.typography.headlineSmall, color = LocalNufoColors.current.textSecondary)
                }
                MacroDonut(nutrition)
            }
        }

        if (p.source == OffParser.SOURCE && p.barcode != null) {
            Spacer(Modifier.height(16.dp))
            PricesCard(vm, Modifier.padding(horizontal = 20.dp))
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEach { t -> TabPill(stringResource(t.label), tab == t) { tab = t } }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                // Same proportions as screen pushes, in the direction of the tapped tab.
                (slideInHorizontally(nufoSpring()) { dir * it / 8 } + fadeIn(Motion.fadeInSpec()))
                    .togetherWith(slideOutHorizontally(nufoSpring()) { -dir * it / 16 } + fadeOut(Motion.fadeOutSpec()))
            },
            label = "tab",
            modifier = Modifier.padding(horizontal = 20.dp),
        ) { t ->
            when (t) {
                Tab.Nutrition -> NutritionTab(p, nutrition, if (portion == Portion.Per100) null else portionText)
                Tab.Vitamins -> VitaminsTab(p, nutrition, lang)
                Tab.Ingredients -> IngredientsTab(p, lang)
                Tab.Additives -> AdditivesTab(p, lang)
                Tab.Environment -> EnvironmentTab(p, lang)
            }
        }

        Spacer(Modifier.height(26.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            val updated = if (p.lastUpdated > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.lastUpdated))
            else stringResource(R.string.not_available)
            Text(stringResource(R.string.footer_source, p.source, updated), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            Text(
                stringResource(if (p.source == OffParser.SOURCE) R.string.footer_odbl else R.string.footer_usda),
                style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
    if (method) MethodologySheet { method = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreCard(p: Product, onMethod: () -> Unit, modifier: Modifier) {
    NufoCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            val scored = Scoring.canScore(p)
            ScoreRing(p.nufoScore.takeIf { scored })
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nufo_score), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
                if (scored) Text(stringResource(Verdict.of(p.nufoScore).label()), style = MaterialTheme.typography.headlineSmall, color = scoreTextColor(p.nufoScore))
                else Text(stringResource(R.string.score_insufficient), style = MaterialTheme.typography.titleLarge, color = LocalNufoColors.current.textSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onMethod).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.how_calculated), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LocalNufoColors.current.hairline))
            // Without the official grades a high score only means "no red flags in the nutrients"; say so.
            if (!Scoring.canScore(p)) {
                Text(stringResource(R.string.score_insufficient_body), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
            } else if (p.nutriscoreGrade == null && p.novaGroup == null) {
                Text(stringResource(R.string.score_nutrients_only), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
            }
            NutriScoreScale(p.nutriscoreGrade)
            NovaScale(p.novaGroup)
            EcoScoreScale(p.ecoscoreGrade)
            Box(Modifier.fillMaxWidth().height(1.dp).background(LocalNufoColors.current.hairline))
            val reasons = if (Scoring.canScore(p)) Scoring.reasons(p) else emptyList()
            if (reasons.isNotEmpty()) {
                Text(stringResource(R.string.why_score), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { r ->
                        val good = r.delta > 0
                        val c = if (good) GradeA else GradeE
                        Text(
                            "${stringResource(r.reason.label())}  ${if (good) "+" else "−"}${abs(r.delta)}",
                            style = MaterialTheme.typography.labelMedium, color = c,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            } else Text(stringResource(R.string.no_reasons), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
}
@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else LocalNufoColors.current.card, nufoSpring(), label = "pillBg")
    val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurface, nufoSpring(), label = "pillFg")
    Text(
        label, color = fg, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun MacroDonut(n: Nutrition) {
    val p = (n.protein ?: 0.0) * 4
    val c = (n.carbs ?: 0.0) * 4
    val f = (n.fat ?: 0.0) * 9
    val total = p + c + f
    val colors = listOf(Color(0xFF43A047), Color(0xFFFFB300), Color(0xFFFF7043))
    val track = LocalNufoColors.current.hairline
    val reduce = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(Motion.REVEAL, easing = Motion.EaseOut)) }
    val cd = if (total > 0) stringResource(R.string.macro_split_cd, (p / total * 100).toInt(), (c / total * 100).toInt(), (f / total * 100).toInt())
    else stringResource(R.string.macro_split_na)
    val legend = listOf(R.string.macro_p, R.string.macro_c, R.string.macro_f)
    Column(Modifier.clearAndSetSemantics { contentDescription = cd }) {
        Canvas(Modifier.size(74.dp).align(Alignment.CenterHorizontally)) {
            val sw = 10.dp.toPx()
            val arc = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
            if (total > 0) {
                var start = -90f
                listOf(p, c, f).forEachIndexed { i, v ->
                    val sweep = (v / total * 360f).toFloat() * grow.value
                    if (sweep > 1f) drawArc(colors[i], start, sweep - 1f, false, tl, arc, style = Stroke(sw))
                    start += sweep
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        legend.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(l), style = MaterialTheme.typography.labelSmall, color = LocalNufoColors.current.textSecondary)
            }
        }
    }
}

/** A row of the EU nutrition declaration, optionally with an FSA traffic-light level. */
private data class DeclRow(
    val label: Int,
    val per100: Double?,
    val scaled: Double?,
    val kcal: Boolean = false,
    val indent: Boolean = false,
    val level: Level? = null,
)

private data class NutrientSpec(val icon: ImageVector, val label: Int, val grams: Double?, val tint: Color, val iconColor: Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutritionTab(p: Product, n: Nutrition, portionLabel: String?) {
    val t = LocalNufoColors.current
    val h = p.nutritionPer100g
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val cards = listOf(
            NutrientSpec(Icons.Outlined.Egg, R.string.n_protein, n.protein, t.tintProtein, Color(0xFF43A047)),
            NutrientSpec(Icons.Outlined.BakeryDining, R.string.n_carbs, n.carbs, t.tintCarbs, Color(0xFFFFA000)),
            NutrientSpec(Icons.Outlined.Opacity, R.string.n_fat, n.fat, t.tintFat, Color(0xFFFF7043)),
            NutrientSpec(Icons.Outlined.Grain, R.string.n_fiber, n.fiber, t.tintFiber, Color(0xFF1E88E5)),
        )
        cards.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { i, s ->
                    NutrientCard(s.icon, stringResource(s.label), s.grams?.let(::fmt), "g", s.tint, s.iconColor, Modifier.weight(1f).enterStagger(row * 2 + i))
                }
            }
        }

        // Mirrors the table printed on EU packaging, so every number can be checked against the pack.
        val rows = listOf(
            DeclRow(R.string.n_energy, h.calories, n.calories, kcal = true),
            DeclRow(R.string.n_fat, h.fat, n.fat, level = h.fat?.let(Scoring::fatLevel)),
            DeclRow(R.string.n_satfat, h.saturatedFat, n.saturatedFat, indent = true, level = h.saturatedFat?.let(Scoring::satFatLevel)),
            DeclRow(R.string.n_carbs, h.carbs, n.carbs),
            DeclRow(R.string.n_sugar, h.sugar, n.sugar, indent = true, level = h.sugar?.let(Scoring::sugarLevel)),
            DeclRow(R.string.n_fiber, h.fiber, n.fiber),
            DeclRow(R.string.n_protein, h.protein, n.protein),
            DeclRow(R.string.n_salt, h.salt, n.salt, level = h.salt?.let(Scoring::saltLevel)),
        )
        NufoCard(Modifier.fillMaxWidth().enterStagger(4)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.declaration), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.per_100g), style = MaterialTheme.typography.labelMedium, color = t.textSecondary,
                        textAlign = TextAlign.End, modifier = Modifier.width(84.dp),
                    )
                    if (portionLabel != null) Text(
                        stringResource(R.string.per_portion, portionLabel), style = MaterialTheme.typography.labelMedium,
                        color = t.textSecondary, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.width(92.dp),
                    )
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.onSurface))
                rows.forEachIndexed { i, r ->
                    DeclarationRow(r, portionLabel != null)
                    if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Level.entries.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelDot(l)
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(l.label()), style = MaterialTheme.typography.labelSmall, color = t.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.levels_note), style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
            }
        }

        val insights = Scoring.insights(p)
        if (insights.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.forEach { i -> Pill(stringResource(i.label()), if (i.positive) GradeA else GradeE) }
            }
        }
    }
}

@Composable
private fun DeclarationRow(r: DeclRow, twoColumns: Boolean) {
    val na = stringResource(R.string.n_a)
    fun cell(v: Double?): String = when {
        v == null -> na
        r.kcal -> "${"%.0f".format(v)} kcal"
        else -> "${fmt(v)} g" // EU declarations state macronutrients in grams, never mg
    }
    val levelText = r.level?.let { stringResource(it.label()) }
    val label = stringResource(r.label)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = listOfNotNull(label, cell(r.per100), levelText).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).padding(start = if (r.indent) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = if (r.indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f, fill = false),
            )
            r.level?.let { Spacer(Modifier.width(8.dp)); LevelDot(it) }
        }
        Text(cell(r.per100), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(84.dp))
        if (twoColumns) Text(
            cell(r.scaled), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.End, modifier = Modifier.width(92.dp),
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    NufoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { content() } }
}

@Composable
private fun VitaminsTab(p: Product, n: Nutrition, lang: String) = InfoCard {
    val rows = n.vitamins.entries + n.minerals.entries
    if (rows.isEmpty()) {
        Text(stringResource(R.string.no_micros, p.source), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    } else rows.forEachIndexed { i, (id, grams) ->
        val (v, unit) = formatGrams(grams)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Micronutrients.name(id, lang), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$v $unit", style = MaterialTheme.typography.titleMedium)
        }
        if (i < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalNufoColors.current.hairline))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientsTab(p: Product, lang: String) = InfoCard {
    Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    p.ingredientsText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ?: Text(stringResource(R.string.no_ingredients, p.source), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.allergens), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    if (p.allergenTags.isEmpty()) {
        Text(stringResource(R.string.no_allergens, p.source), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.allergenTags.forEach { Pill(p.tagName(it, lang), GradeE) }
    }
}

@Composable
private fun AdditivesTab(p: Product, lang: String) = InfoCard {
    if (p.additiveTags.isEmpty()) {
        Text(stringResource(R.string.no_additives, p.source), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    } else p.additiveTags.forEachIndexed { i, a ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(GradeC))
            Spacer(Modifier.width(12.dp))
            Text(p.tagName(a, lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        if (i < p.additiveTags.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalNufoColors.current.hairline))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvironmentTab(p: Product, lang: String) = InfoCard {
    EcoScoreScale(p.ecoscoreGrade)
    if (p.ecoscoreGrade == null) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.eco_na_body, p.source), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    }
    if (p.labelTags.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.labels), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.labelTags.take(12).forEach { Pill(p.tagName(it, lang), MaterialTheme.colorScheme.primary) }
        }
    }
    // Only categories with a translated name: raw ids in other languages ("fr:yaourt-egoutte") look broken.
    val cats = p.categoryTags.takeLast(4).filter { it in p.tagNames }
    if (cats.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { Pill(p.tagName(it, lang), LocalNufoColors.current.textSecondary) }
        }
    }
}

/** Crowdsourced shop prices with all four states: loading, reports, none yet, failed. */
@Composable
private fun PricesCard(vm: NufoViewModel, modifier: Modifier) {
    val state by vm.prices.collectAsStateWithLifecycle()
    val t = LocalNufoColors.current
    NufoCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sell, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.prices_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            when (val s = state) {
                PricesState.Loading -> repeat(2) {
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth(if (it == 0) 0.8f else 0.55f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(t.hairline))
                }
                PricesState.Failed -> Text(stringResource(R.string.prices_failed), style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
                is PricesState.Loaded -> if (s.reports.isEmpty()) {
                    Text(stringResource(R.string.prices_none), style = MaterialTheme.typography.bodyMedium, color = t.textSecondary)
                } else s.reports.forEach { r ->
                    val money = java.text.NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance(r.currency) }.format(r.price)
                    val date = runCatching { java.time.LocalDate.parse(r.date).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrDefault(r.date)
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(r.store, r.city).joinToString(", ").ifEmpty { r.country ?: "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(stringResource(R.string.prices_where, r.country ?: "—", date), style = MaterialTheme.typography.labelSmall, color = t.textSecondary)
                        }
                        Text(money, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.prices_source), style = MaterialTheme.typography.labelSmall, color = t.textSecondary)
        }
    }
}