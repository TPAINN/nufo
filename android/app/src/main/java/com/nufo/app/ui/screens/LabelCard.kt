package com.nufo.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nufo.app.LabelState
import com.nufo.app.NufoViewModel
import com.nufo.app.R
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.theme.GradeA
import com.nufo.app.ui.theme.GradeE
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.Motion
import com.nufo.app.ui.theme.nufoSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fills in what the databases lack from a photo of the package's nutrition table: the camera takes a full-resolution
 * photo (the preview thumbnail is too small for label print), or up to two are picked from the gallery (table and
 * ingredients). The values are read exactly as printed; Nutri-Score and NOVA are computed from them.
 */
@Composable
fun LabelCard(vm: NufoViewModel, missing: Boolean, modifier: Modifier = Modifier, unnamed: Boolean = false, needsIngredients: Boolean = false) {
    val state by vm.label.collectAsStateWithLifecycle()
    if (!missing && state == LabelState.Idle) {
        // Complete, but built from a label that printed no name: the only thing left to ask for.
        if (unnamed) NufoCard(modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.label_name_title), style = MaterialTheme.typography.titleMedium)
                NameField(vm::renameProduct)
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraDenied by remember { mutableStateOf(false) }
    fun read(uris: List<Uri>) = scope.launch {
        val bitmaps = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { decode(context, it) }.getOrNull() } }
        if (bitmaps.isNotEmpty()) vm.readLabel(bitmaps)
        withContext(Dispatchers.IO) { File(context.cacheDir, "labels").deleteRecursively() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> photoUri?.takeIf { ok }?.let { read(listOf(it)) } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris -> if (uris.isNotEmpty()) read(uris) }
    fun takePhoto() {
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "label-${System.currentTimeMillis()}.jpg"))
        photoUri = uri
        runCatching { camera.launch(uri) }
    }
    // The app declares CAMERA (for the scanner), so Android refuses the camera app unless that permission is granted.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) takePhoto() else cameraDenied = true }
    fun camera() {
        cameraDenied = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
        else permission.launch(Manifest.permission.CAMERA)
    }
    fun pick() { cameraDenied = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    NufoCard(modifier.fillMaxWidth()) {
        AnimatedContent(state, contentKey = { it::class }, transitionSpec = { Motion.crossfade() }, label = "label-card") { st ->
            Column(Modifier.padding(18.dp).animateContentSize(nufoSpring()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (st) {
                    LabelState.Reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.label_reading), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.label_reading_body), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
                        }
                    }
                    is LabelState.Done -> {
                        Header(Icons.Outlined.CheckCircle, GradeA, stringResource(R.string.label_done),
                            stringResource(if (st.contributed) R.string.label_done_shared else R.string.label_done_body))
                        // A nutrition table rarely carries the product name: let people name it right here.
                        if (unnamed) NameField(vm::renameProduct)
                        // NOVA needs the ingredient list; offer one more photo for it.
                        if (needsIngredients) {
                            Text(stringResource(R.string.label_add_ingredients), style = MaterialTheme.typography.bodyMedium)
                            Buttons(::camera, ::pick)
                        }
                    }
                    else -> {
                        val failed = st as? LabelState.Failed
                        when {
                            failed?.unreadable == true -> Header(Icons.Outlined.ErrorOutline, GradeE, stringResource(R.string.label_unreadable), stringResource(R.string.label_unreadable_body))
                            failed != null -> Header(Icons.Outlined.ErrorOutline, GradeE, stringResource(R.string.label_failed), stringResource(R.string.label_failed_body))
                            else -> Header(Icons.Outlined.DocumentScanner, MaterialTheme.colorScheme.primary, stringResource(R.string.label_title), stringResource(R.string.label_body))
                        }
                        if (cameraDenied) Text(stringResource(R.string.photo_camera_denied), style = MaterialTheme.typography.labelMedium, color = GradeE)
                        Buttons(::camera, ::pick)
                    }
                }
            }
        }
    }
}

@Composable
private fun Buttons(onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onCamera, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.label_camera), maxLines = 1)
        }
        OutlinedButton(onGallery, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.label_gallery), maxLines = 1)
        }
    }
}

@Composable
private fun NameField(onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun save() { if (name.isNotBlank()) { onSave(name.trim()); focus.clearFocus() } }
    androidx.compose.material3.OutlinedTextField(
        name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.label_name)) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
        trailingIcon = { if (name.isNotBlank()) androidx.compose.material3.TextButton(::save) { Text(stringResource(R.string.label_name_save)) } },
    )
}

@Composable
private fun Header(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
}
