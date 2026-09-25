package com.nufo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nufo.app.NufoViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nufo.app.data.offImage
import com.nufo.app.ui.photoKey
import com.nufo.app.ui.sharedPhoto
import com.nufo.app.R
import com.nufo.app.ui.currentDataLang
import com.nufo.app.ui.displayName
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.VerifiedUser
import com.nufo.app.data.Product
import com.nufo.app.ui.components.NufoCard
import com.nufo.app.ui.components.SectionTitle
import com.nufo.app.ui.components.TegakiLogo
import com.nufo.app.ui.components.enterStagger
import com.nufo.app.ui.theme.GreenDeep
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.scoreColor

private var homeLogoPlayed = false

@Composable
fun HomeScreen(
    vm: NufoViewModel,
    onScanBarcode: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSearch: (String?) -> Unit,
    onOpen: (Product) -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Signature writes itself once per launch, not every time Home is revisited.
                TegakiLogo(Modifier.height(46.dp), color = MaterialTheme.colorScheme.primary, animate = !homeLogoPlayed, speed = 1.5f, onFinished = { homeLogoPlayed = true })
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        ScanHero(onScanBarcode, onTakePhoto, onPickGallery, Modifier.padding(horizontal = 20.dp).enterStagger(1))
        Spacer(Modifier.height(16.dp))
        SearchPill({ onSearch(null) }, Modifier.padding(horizontal = 20.dp).enterStagger(2))
        Spacer(Modifier.height(24.dp))
        PopularInGreece(onSearch, Modifier.enterStagger(3))
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.home_recent), Modifier.padding(horizontal = 20.dp).enterStagger(4))
        Spacer(Modifier.height(12.dp))
        val recent = history
        if (recent == null) {
            RecentSkeleton(Modifier.enterStagger(5))
        } else if (recent.isEmpty()) {
            NufoCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().enterStagger(4)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(recent.take(12), key = { _, p -> p.key }) { i, p ->
                    RecentCard(p, { onOpen(p) }, Modifier.enterStagger(5 + i))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TrustCard(Modifier.padding(horizontal = 20.dp).enterStagger(6))
    }
}

/** Common Greek foods, one tap away; the search shows products sold in Greece first. */
@Composable
private fun PopularInGreece(onSearch: (String) -> Unit, modifier: Modifier) {
    val greek = currentDataLang() == "el"
    val items = listOf(
        "Φέτα" to "Feta", "Γιαούρτι" to "Greek yogurt", "Ελαιόλαδο" to "Olive oil", "Σπανακόπιτα" to "Spinach pie",
        "Χαλβάς" to "Halva", "Μέλι" to "Honey", "Φρυγανιές" to "Rusks", "Ταχίνι" to "Tahini",
    )
    Column(modifier) {
        SectionTitle(stringResource(R.string.home_popular), Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.size) { i ->
                val label = if (greek) items[i].first else items[i].second
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(LocalNufoColors.current.card)
                        .border(1.dp, LocalNufoColors.current.hairline, RoundedCornerShape(50))
                        .clickable(role = Role.Button) { onSearch(label) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TrustCard(modifier: Modifier) {
    var about by remember { mutableStateOf(false) }
    NufoCard(modifier.fillMaxWidth(), onClick = { about = true }, onClickLabel = stringResource(R.string.s_about)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_trust_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.home_trust_body), style = MaterialTheme.typography.bodyMedium, color = LocalNufoColors.current.textSecondary)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LocalNufoColors.current.textSecondary)
        }
    }
    if (about) AboutSheet { about = false }
}

@Composable
private fun ScanHero(onScan: () -> Unit, onPhoto: () -> Unit, onGallery: () -> Unit, modifier: Modifier) {
    NufoCard(modifier.fillMaxWidth(), corner = 28.dp, color = Color.Transparent) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF3B8F3F), GreenDeep), start = Offset.Zero, end = Offset(900f, 700f))),
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(12.dp))
                    ScanFrameArt(Modifier.size(72.dp))
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .clickable(onClickLabel = stringResource(R.string.home_scan_label), role = Role.Button, onClick = onScan),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = GreenDeep)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_scan), style = MaterialTheme.typography.titleMedium, color = GreenDeep)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostAction(Icons.Outlined.CameraAlt, stringResource(R.string.home_photo), onPhoto)
                    GhostAction(Icons.Outlined.PhotoLibrary, stringResource(R.string.home_gallery), onGallery)
                }
            }
        }
    }
}

@Composable
private fun RowScope.GhostAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Rounded scanner-frame corners with a barcode, echoing the scanner overlay. */
@Composable
private fun ScanFrameArt(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width; val c = w * 0.28f; val sw = 5.dp.toPx()
        val col = Color.White.copy(alpha = 0.9f)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(col, Offset(x, y + dy * c), Offset(x, y), sw, StrokeCap.Round)
            drawLine(col, Offset(x, y), Offset(x + dx * c, y), sw, StrokeCap.Round)
        }
        corner(sw, sw, 1f, 1f); corner(w - sw, sw, -1f, 1f); corner(sw, w - sw, 1f, -1f); corner(w - sw, w - sw, -1f, -1f)
        val bars = listOf(2f, 1f, 3f, 1f, 2f, 1f, 1f, 3f, 1f, 2f)
        var x = w * 0.26f
        bars.forEachIndexed { i, b ->
            if (i % 2 == 0) drawRect(Color.White.copy(alpha = 0.55f), Offset(x, w * 0.32f), androidx.compose.ui.geometry.Size(b * 3f, w * 0.36f))
            x += b * 3f + 2.5f
        }
        drawLine(Color(0xFFB9F6CA), Offset(w * 0.18f, w / 2), Offset(w * 0.82f, w / 2), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier) {
    NufoCard(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.nav_search), corner = 18.dp) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = LocalNufoColors.current.textSecondary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_search), style = MaterialTheme.typography.bodyLarge, color = LocalNufoColors.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentCard(p: Product, onClick: () -> Unit, modifier: Modifier) {
    NufoCard(modifier.width(150.dp), onClick = onClick, onClickLabel = stringResource(R.string.open_product, p.displayName())) {
        Box {
            ProductThumb(p, Modifier.fillMaxWidth().height(104.dp))
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(scoreColor(p.nufoScore)),
                contentAlignment = Alignment.Center,
            ) { Text(p.nufoScore.toString(), style = MaterialTheme.typography.labelMedium, color = Color.White) }
        }
        Column(Modifier.padding(12.dp)) {
            Text(p.displayName(), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                p.nutritionPer100g.calories?.let { stringResource(R.string.kcal_per_100g, it.toInt().toString()) } ?: stringResource(R.string.kcal_na),
                style = MaterialTheme.typography.labelMedium, color = LocalNufoColors.current.textSecondary,
            )
        }
    }
}

/**
 * Product photo, or a soft tinted placeholder with the first letter until a photo has actually arrived.
 * Lists load Open Food Facts' 200 px rendition; the [hero] shows 400 px at once and swaps in the
 * full-resolution photo when it lands, so it is sharp without making anyone wait.
 */
@Composable
fun ProductThumb(p: Product, modifier: Modifier, hero: Boolean = false) =
    ProductThumb(p.imageUrl, p.displayName(), modifier, hero, photoKey(p.barcode, p.name))

@Composable
fun ProductThumb(imageUrl: String?, name: String, modifier: Modifier, hero: Boolean = false, sharedKey: String? = null) {
    val context = LocalContext.current
    Box(
        modifier.then(if (sharedKey != null && imageUrl != null) Modifier.sharedPhoto(sharedKey) else Modifier)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        if (imageUrl != null) {
            var loaded by remember(imageUrl) { mutableStateOf(false) }
            var fullLoaded by remember(imageUrl) { mutableStateOf(false) }
            // Packshots sit on white, with a little air around them.
            val frame = Modifier.fillMaxSize().then(if (loaded) Modifier.background(Color.White).padding(if (hero) 16.dp else 8.dp) else Modifier)
            AsyncImage(
                ImageRequest.Builder(context).data(offImage(imageUrl, "400")).crossfade(220).build(),
                contentDescription = null, contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { android.util.Log.w("Nufo", "Image failed: $imageUrl", it.result.throwable) },
                modifier = frame.graphicsLayer { alpha = if (fullLoaded) 0f else 1f },
            )
            if (hero) AsyncImage(
                ImageRequest.Builder(context).data(offImage(imageUrl, "full")).crossfade(320).build(),
                contentDescription = null, contentScale = ContentScale.Fit,
                onSuccess = { loaded = true; fullLoaded = true },
                modifier = frame,
            )
        }
    }
}

/** Loading placeholder for the recent-scans row, shown until the database answers. */
@Composable
private fun RecentSkeleton(modifier: Modifier) {
    val c = LocalNufoColors.current.hairline
    Row(modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) { Box(Modifier.width(150.dp).height(176.dp).clip(RoundedCornerShape(22.dp)).background(c)) }
    }
}