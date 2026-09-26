package com.nufo.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import com.nufo.app.UpdateState
import com.nufo.app.ui.theme.Motion
import com.nufo.app.ui.theme.nufoSpring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nufo.app.BuildConfig
import com.nufo.app.NufoViewModel
import com.nufo.app.R
import com.nufo.app.data.ALLERGENS
import com.nufo.app.data.Diet
import com.nufo.app.data.TagNames
import com.nufo.app.data.ThemeMode
import com.nufo.app.data.Units
import com.nufo.app.ui.AppLanguage
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.components.enterStagger
import com.nufo.app.ui.currentDataLang
import com.nufo.app.ui.label
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: NufoViewModel) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val lang = currentDataLang()
    var confirmClear by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Group(stringResource(R.string.s_language), 0) {
            if (AppLanguage.supported) {
                val current = AppLanguage.get(context)
                Segmented(
                    listOf(null, "el", "en"), current,
                    { when (it) { null -> stringResource(R.string.s_system); "el" -> "Ελληνικά"; else -> "English" } },
                ) { AppLanguage.set(context, it) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.s_language_note), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        context.startActivity(Intent(AndroidSettings.ACTION_LOCALE_SETTINGS))
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.s_language_system), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                }
            }
        }
        Group(stringResource(R.string.s_units), 1) {
            Segmented(Units.entries, s.units, { stringResource(if (it == Units.Metric) R.string.s_metric else R.string.s_imperial) }, vm::setUnits)
        }
        Group(stringResource(R.string.s_theme), 2) {
            Segmented(ThemeMode.entries, s.theme, {
                stringResource(when (it) { ThemeMode.System -> R.string.s_system; ThemeMode.Light -> R.string.s_light; ThemeMode.Dark -> R.string.s_dark })
            }, vm::setTheme)
        }
        Group(stringResource(R.string.s_diet), 3) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Diet.entries.forEach { d -> FilterChip(s.diet == d, { vm.setDiet(d) }, { Text(stringResource(d.label())) }) }
            }
        }
        Group(stringResource(R.string.s_allergens), 4) {
            Text(stringResource(R.string.s_allergens_note), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALLERGENS.forEach { tag ->
                    FilterChip(tag in s.allergenAlerts, { vm.toggleAllergen(tag) }, { Text(TagNames.fallback(tag, lang) ?: tag) })
                }
            }
        }
        Group(stringResource(R.string.s_sources), 5) {
            Source("Open Food Facts", stringResource(R.string.s_off_body)) { open("https://world.openfoodfacts.org") }
            Source("USDA FoodData Central", stringResource(R.string.s_usda_body)) { open("https://fdc.nal.usda.gov") }
            Source("Google ML Kit", stringResource(R.string.s_mlkit_body)) { open("https://developers.google.com/ml-kit") }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.s_privacy), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
        NufoCard(Modifier.fillMaxWidth().enterStagger(6), onClick = { about = true }, onClickLabel = stringResource(R.string.s_about)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.s_about), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.s_about_body), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LocalNufoColors.current.textSecondary)
            }
        }
        Group(stringResource(R.string.s_photos), 7) {
            ToggleRow(stringResource(R.string.s_smart_photos), stringResource(R.string.s_smart_photos_body), s.smartPhotos, vm::setSmartPhotos)
        }
        Group(stringResource(R.string.s_updates), 8) { UpdatesSection(vm, s.autoUpdates) { open(it) } }
        Group(stringResource(R.string.s_data), 9) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmClear = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = GradeE)
                Text(stringResource(R.string.s_clear), style = MaterialTheme.typography.bodyLarge, color = GradeE)
            }
        }
        Text(
            stringResource(R.string.disclaimer) + " " + stringResource(R.string.s_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    if (about) AboutSheet { about = false }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.s_clear_title)) },
        text = { Text(stringResource(R.string.s_clear_body)) },
        confirmButton = {
            TextButton({
                vm.clearHistory()
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                confirmClear = false
            }) { Text(stringResource(R.string.clear), color = GradeE) }
        },
        dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun UpdatesSection(vm: NufoViewModel, auto: Boolean, onDownload: (String) -> Unit) {
    val state by vm.update.collectAsStateWithLifecycle()
    ToggleRow(stringResource(R.string.s_auto_updates), stringResource(R.string.s_auto_updates_body), auto, vm::setAutoUpdates)
    // Manual checking is revealed only when the automatic one is off; an available update always shows.
    AnimatedVisibility(
        visible = !auto || state is UpdateState.Available,
        enter = expandVertically(nufoSpring()) + fadeIn(Motion.fadeInSpec(delay = Motion.FAST / 3)),
        exit = shrinkVertically(nufoSpring()) + fadeOut(Motion.fadeOutSpec()),
    ) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val available = state as? UpdateState.Available
            if (available != null) {
                Button({ onDownload(available.update.url) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.s_update_download) + " " + available.update.version)
                }
            } else {
                OutlinedButton(vm::checkForUpdatesNow, Modifier.fillMaxWidth(), enabled = state != UpdateState.Checking) {
                    Text(stringResource(R.string.s_check_updates))
                }
            }
            AnimatedContent(state, transitionSpec = { Motion.crossfade() using SizeTransform(clip = false) }, label = "update-status") { st ->
                val text = when (st) {
                    UpdateState.Idle -> null
                    UpdateState.Checking -> stringResource(R.string.s_checking)
                    UpdateState.UpToDate -> stringResource(R.string.s_up_to_date)
                    UpdateState.Failed -> stringResource(R.string.s_update_failed)
                    is UpdateState.Available -> stringResource(R.string.s_update_available, st.update.version)
                }
                if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Group(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.enterStagger(index)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        NufoCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected == o, { onSelect(o) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label(o), maxLines = 1) }
        }
    }
}

@Composable
private fun Source(name: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = stringResource(R.string.s_open_site, name), onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = LocalNufoColors.current.textSecondary)
    }
}