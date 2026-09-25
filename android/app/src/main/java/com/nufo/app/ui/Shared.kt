package com.nufo.app.ui


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.nufo.app.ui.theme.LocalReduceMotion

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key that matches a product's photo across list rows, recent cards and the result hero. */
fun photoKey(barcode: String?, name: String) = "photo-${barcode ?: name}"

/**
 * The product photo flies from where it was tapped into the result hero, and back on close.
 * No-op outside a shared-transition host or when the user turned animations off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhoto(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedPhoto.sharedBounds(
            rememberSharedContentState(key), nav,
            boundsTransform = { _, _ -> com.nufo.app.ui.theme.nufoSpring() },
        )
    }
}