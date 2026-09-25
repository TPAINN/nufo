package com.nufo.app.ui.screens

import com.nufo.app.ui.theme.Motion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nufo.app.R
import com.nufo.app.scan.DishConfidence
import com.nufo.app.scan.PhotoAnalysis
import com.nufo.app.ui.currentDataLang
import androidx.compose.ui.res.stringResource
import com.nufo.app.scan.PhotoAnalyzer
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.nufoSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface PhotoState {
    data object Empty : PhotoState
    data object Analyzing : PhotoState
    data class Done(val analysis: PhotoAnalysis) : PhotoState
    data class Failed(val message: String) : PhotoState
}

/** Decodes a picked photo at most ~1600 px on its long side: analysis needs no more, and huge photos cost memory. */
private fun decode(context: android.content.Context, uri: Uri): Bitmap =
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val scale = 1600f / maxOf(info.size.width, info.size.height)
            if (scale < 1f) d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        // Android 8.x has no ImageDecoder: read the size first, then decode with a power-of-two sample.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Unreadable image")
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoScreen(
    source: String,
    onBarcode: (String) -> Unit,
    onDish: (key: String, name: String, photo: Bitmap?) -> Unit,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var state by remember { mutableStateOf<PhotoState>(PhotoState.Empty) }
    var launched by rememberSaveable { mutableStateOf(false) }
    val openFailed = stringResource(R.string.photo_open_failed)
    val analysisFailed = stringResource(R.string.photo_failed)
    val lang = currentDataLang()

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) bitmap = bmp }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) bitmap = runCatching { decode(context, uri) }.getOrElse { state = PhotoState.Failed(openFailed); null }
    }
    fun pick(fromCamera: Boolean) {
        if (fromCamera) camera.launch(null) else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) { if (!launched) { launched = true; pick(source == "camera") } }
    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        state = PhotoState.Analyzing
        state = runCatching { withContext(Dispatchers.Default) { PhotoAnalyzer.analyze(context, bmp, lang) } }
            .fold({ PhotoState.Done(it) }, { PhotoState.Failed(analysisFailed) })
        (state as? PhotoState.Done)?.analysis?.barcode?.let(onBarcode)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.photo_title), style = MaterialTheme.typography.titleLarge)
        }
        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(bmp.asImageBitmap(), stringResource(R.string.photo_selected), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(14.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.photo_change_label)) { pick(source == "camera") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.photo_change), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ pick(true) }) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.photo_camera)) }
                OutlinedButton({ pick(false) }) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.photo_gallery)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            state,
            transitionSpec = { (fadeIn(Motion.fadeInSpec(delay = Motion.FAST / 3)) + slideInVertically(nufoSpring()) { it / 8 }).togetherWith(fadeOut(Motion.fadeOutSpec())) },
            label = "photoState",
            modifier = Modifier.padding(horizontal = 20.dp),
        ) { s ->
            when (s) {
                PhotoState.Empty -> Text(stringResource(R.string.photo_prompt), color = LocalNufoColors.current.textSecondary)
                PhotoState.Analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.photo_analyzing), style = MaterialTheme.typography.bodyLarge)
                }
                is PhotoState.Failed -> ConfirmFood(emptyList(), s.message, null, onConfirm)
                is PhotoState.Done -> {
                    val a = s.analysis
                    val sure = a.dishes.firstOrNull()?.takeIf { it.confidence >= DishConfidence.CONFIDENT }
                    val dishes = a.dishes.filter { it.confidence >= DishConfidence.PLAUSIBLE }.take(3).map { it.label }
                    // Dish guesses first, then words read from packaging, then broad categories.
                    val candidates = (dishes + a.textLines + a.labels.map { it.label }).distinct().take(6)
                    val hint = when {
                        a.barcode != null -> stringResource(R.string.photo_found_barcode, a.barcode)
                        sure != null -> stringResource(R.string.photo_looks_like, sure.label, (sure.confidence * 100).toInt())
                        dishes.isNotEmpty() -> stringResource(R.string.photo_pick_one)
                        candidates.isNotEmpty() -> stringResource(R.string.photo_not_sure)
                        else -> stringResource(R.string.photo_unknown)
                    }
                    // Only a confident guess is filled in for the user; otherwise they choose.
                    // A recognised dish opens its nutrition directly; anything typed by hand is searched.
                    ConfirmFood(candidates, hint, sure?.label) { text ->
                        val dish = a.dishes.firstOrNull { it.label.equals(text, ignoreCase = true) && it.key != null }
                        if (dish?.key != null) onDish(dish.key, dish.label, bitmap) else onConfirm(text)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmFood(candidates: List<String>, hint: String, preselected: String?, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(preselected.orEmpty()) }
    NufoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(hint, style = MaterialTheme.typography.titleMedium)
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { c -> FilterChip(selected = text == c, onClick = { text = c }, label = { Text(c) }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { text = it }, label = { Text(stringResource(R.string.photo_food_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onConfirm(text.trim()) }),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text(stringResource(R.string.photo_find)) }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.photo_portion_note), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
        }
    }
}