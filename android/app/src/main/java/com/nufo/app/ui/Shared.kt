package com.nufo.app.ui


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.nufo.app.ui.theme.LocalReduceMotion
import com.nufo.app.ui.theme.nufoSpring

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key that matches a product's photo across list rows, recent cards and the result hero. */
fun photoKey(barcode: String?, name: String) = "photo-${barcode ?: name}"

/** Corner radii of a photo where it sits: a recent card rounds only its top, a list thumbnail all four. */
data class PhotoCorners(val top: Dp, val bottom: Dp) {
    constructor(all: Dp) : this(all, all)
}

private class Placed(val corners: PhotoCorners)

/** Every photo currently on screen under each key, so one end of a flight can start from the other's corners. */
private val placedPhotos = mutableStateMapOf<String, List<Placed>>()

/**
 * The product photo flies from where it was tapped into the result hero, and back on close, and its corners
 * morph with it (a 14 dp thumbnail grows into the 28 dp hero) instead of snapping when it lands.
 * Outside a shared-transition host, or with animations off, it is simply clipped to [corners].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(key: String, corners: PhotoCorners): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (shared == null || nav == null || LocalReduceMotion.current) {
        return clip(RoundedCornerShape(corners.top, corners.top, corners.bottom, corners.bottom))
    }
    val me = remember(corners) { Placed(corners) }
    DisposableEffect(key, me) {
        placedPhotos[key] = placedPhotos[key].orEmpty() + me
        onDispose { placedPhotos[key] = placedPhotos[key].orEmpty() - me }
    }
    // The other end of the flight: while entering it is where the photo comes from, while leaving where it goes.
    val other = placedPhotos[key]?.firstOrNull { it !== me }?.corners ?: corners
    val top by nav.transition.animateDp({ nufoSpring() }, label = "photo-top") { if (it == EnterExitState.Visible) corners.top else other.top }
    val bottom by nav.transition.animateDp({ nufoSpring() }, label = "photo-bottom") { if (it == EnterExitState.Visible) corners.bottom else other.bottom }
    val shape = RoundedCornerShape(top, top, bottom, bottom)
    return with(shared) {
        this@sharedPhoto.sharedBounds(
            rememberSharedContentState(key), nav,
            boundsTransform = { _, _ -> nufoSpring() },
            clipInOverlayDuringTransition = OverlayClip(shape),
        ).clip(shape)
    }
}
