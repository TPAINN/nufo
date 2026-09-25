package com.nufo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nufo.app.R
import com.nufo.app.ui.theme.GradeA
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NufoSheet(onDismiss: () -> Unit, title: String, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 36.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** What Nufo does with data: privacy, provenance, and what happens when something is missing. */
@Composable
fun AboutSheet(onDismiss: () -> Unit) = NufoSheet(onDismiss, stringResource(R.string.s_about)) {
    Section(Icons.Outlined.Lock, R.string.about_privacy_title, R.string.about_privacy_body)
    Section(Icons.Outlined.Storage, R.string.about_data_title, R.string.about_data_body)
    Section(Icons.Outlined.SearchOff, R.string.about_missing_title, R.string.about_missing_body)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
}

@Composable
private fun Section(icon: ImageVector, title: Int, body: Int) {
    Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
}

/** The complete Nufo Score rule table, so nothing about the number is hidden. */
@Composable
fun MethodologySheet(onDismiss: () -> Unit) = NufoSheet(onDismiss, stringResource(R.string.method_title)) {
    Text(stringResource(R.string.method_intro), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(18.dp))
    val rules = listOf(
        stringResource(R.string.method_rule_nutri) to stringResource(R.string.method_rule_nutri_delta),
        stringResource(R.string.method_rule_nova) to stringResource(R.string.method_rule_nova_delta),
        stringResource(R.string.method_rule_protein) to "+10",
        stringResource(R.string.method_rule_fiber) to "+10",
        stringResource(R.string.method_rule_sugar) to "−15",
        stringResource(R.string.method_rule_salt) to "−15",
        stringResource(R.string.method_rule_satfat) to "−10",
    )
    Column(Modifier.clip(MaterialTheme.shapes.medium).background(LocalNufoColors.current.card)) {
        rules.forEachIndexed { i, (rule, delta) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rule, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(delta, style = MaterialTheme.typography.labelLarge, color = if (delta.startsWith("+")) GradeA else GradeE)
            }
            if (i < rules.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalNufoColors.current.hairline))
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.method_official), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.levels_note), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
}