package com.nufo.app.ui.screens


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.nufo.app.BuildConfig
import com.nufo.app.R
import androidx.compose.ui.res.stringResource
import com.nufo.app.scan.PRODUCT_BARCODES
import com.nufo.app.ui.theme.LocalReduceMotion
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Barcodes that exist in Open Food Facts; offered in debug builds because emulator cameras can't see real packaging. */
private val SAMPLE_BARCODES = listOf(
    "5201054017388" to "FAGE", "5201037508506" to "ΔΕΛΤΑ", "5201004059192" to "Παπαδοπούλου", "5012501081100" to "ΙΟΝ",
    "5202234610887" to "Κρι Κρι", "5201010144592" to "Misko", "3017620422003" to "Nutella", "0049000050103" to "Coca-Cola US",
)

private fun validBarcode(s: String) = s.length in 8..14 && s.all(Char::isDigit)

@OptIn(ExperimentalGetImage::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(onBarcode: (String) -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val delivered = remember { AtomicBoolean(false) }

    fun deliver(code: String) {
        if (delivered.compareAndSet(false, true)) {
            // CONFIRM exists from Android 11; older phones get the standard tap.
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
            view.post { onBarcode(code) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && cameraFailed) Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_error_title), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.camera_error_body), color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { manual = true }) { Text(stringResource(R.string.scan_type)) }
        }
        else if (granted) CameraPreview(onCamera = { camera = it }, onDetected = ::deliver, onError = { cameraFailed = true })
        else Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.scan_permission_title), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.scan_permission_body), color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_allow)) }
        }

        // Brackets and laser only frame a live camera; over a prompt they collide with its text.
        if (granted && !cameraFailed) ScanOverlay()

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassButton(Icons.Outlined.Close, stringResource(R.string.scan_close), onClose)
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                GlassButton(if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, stringResource(if (torch) R.string.scan_flash_off else R.string.scan_flash_on)) {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.scan_align), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                LabeledGlassButton(Icons.Outlined.PhotoLibrary, stringResource(R.string.scan_gallery), onGallery)
                LabeledGlassButton(Icons.Outlined.Keyboard, stringResource(R.string.scan_type)) { manual = true }
            }
        }
    }

    if (manual) {
        var code by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { manual = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.scan_enter), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit).take(14) },
                    label = { Text(stringResource(R.string.scan_digits)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = code.isNotEmpty() && !validBarcode(code),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (validBarcode(code)) { manual = false; deliver(code) } }),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { manual = false; deliver(code) }, enabled = validBarcode(code),
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.scan_lookup)) }
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.scan_samples), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SAMPLE_BARCODES.forEach { (bc, name) -> AssistChip(onClick = { manual = false; deliver(bc) }, label = { Text(name) }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onDetected: (String) -> Unit, onError: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(PRODUCT_BARCODES) }
    var last by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val code = codes.firstNotNullOfOrNull { it.rawValue }
                            // Require two identical reads in a row to reject partial misreads.
                            if (code != null && code == last) onDetected(code)
                            last = code
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    onCamera(provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
                }.onFailure { android.util.Log.w("Nufo", "Camera bind failed", it); onError() }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Dimmed surround with a rounded window, corner brackets and a sweeping laser line. */
@Composable
private fun ScanOverlay() {
    val reduce = LocalReduceMotion.current
    val sweep = rememberInfiniteTransition(label = "scan")
    val t by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.78f
        val h = w * 0.62f
        val left = (size.width - w) / 2
        val top = size.height * 0.42f - h / 2
        val r = 28.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, Offset(left, top), Size(w, h), CornerRadius(r), blendMode = BlendMode.Clear)
        val sw = 5.dp.toPx(); val len = 34.dp.toPx(); val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y + dy * (r + len)), Offset(x, y + dy * r), sw, StrokeCap.Round)
            drawArc(c, when { dx > 0 && dy > 0 -> 180f; dx < 0 && dy > 0 -> 270f; dx > 0 -> 90f; else -> 0f }, 90f, false,
                Offset(if (dx > 0) x else x - 2 * r, if (dy > 0) y else y - 2 * r), Size(2 * r, 2 * r),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round))
            drawLine(c, Offset(x + dx * r, y), Offset(x + dx * (r + len), y), sw, StrokeCap.Round)
        }
        corner(left, top, 1f, 1f); corner(left + w, top, -1f, 1f); corner(left, top + h, 1f, -1f); corner(left + w, top + h, -1f, -1f)
        val y = top + 18.dp.toPx() + (h - 36.dp.toPx()) * (if (reduce) 0.5f else t)
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF69F0AE), Color.Transparent), left, left + w),
            Offset(left + 16.dp.toPx(), y), Offset(left + w - 16.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round,
        )
    }
}

@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick, Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
    ) { Icon(icon, label) }
}

@Composable
private fun LabeledGlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    // Icon and label form one touch target and one accessibility node.
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}