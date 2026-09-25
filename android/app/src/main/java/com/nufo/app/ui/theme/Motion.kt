package com.nufo.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.compositionLocalOf

/**
 * The one motion system. Every transition in the app is built from these values; nothing picks its own
 * duration or curve. Positions move on [nufoSpring] (interruptible, carries velocity into predictive back),
 * opacity fades on [EaseOut] tweens. iOS mirrors the same numbers in Theme.swift.
 */
object Motion {
    /** Presses, small toggles, anything leaving. */
    const val FAST = 150
    /** Screens, crossfades, list entrances. */
    const val BASE = 250
    /** First-run moments only (welcome, score ring). */
    const val SLOW = 400
    /** Charts filling to their value (score ring, macro donut). */
    const val REVEAL = 700
    /** Gap between items entering one after another. */
    const val STAGGER = 45

    /** Strong ease-out (also res/interpolator/nufo_ease_out.xml). */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** Strong ease-in-out for on-screen movement (also res/interpolator/nufo_ease_in_out.xml). */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    fun <T> fadeInSpec(delay: Int = 0) = tween<T>(BASE, delay, EaseOut)
    fun <T> fadeOutSpec() = tween<T>(FAST, easing = EaseOut)

    // Forward navigation: the new screen slides in a little from the right; the old one drifts left.
    // Back is the exact mirror, so opening and closing always read as the same gesture reversed.
    fun pushEnter(): EnterTransition = slideInHorizontally(nufoSpring()) { it / 8 } + fadeIn(fadeInSpec())
    fun pushExit(): ExitTransition = slideOutHorizontally(nufoSpring()) { -it / 16 } + fadeOut(fadeOutSpec())
    fun popEnter(): EnterTransition = slideInHorizontally(nufoSpring()) { -it / 16 } + fadeIn(fadeInSpec())
    fun popExit(): ExitTransition = slideOutHorizontally(nufoSpring()) { it / 8 } + fadeOut(fadeOutSpec())

    // Between peers (tabs, welcome → home): fade through, no direction implied.
    fun peerEnter(): EnterTransition = fadeIn(fadeInSpec(delay = FAST / 3)) + scaleIn(tween(BASE, easing = EaseOut), initialScale = 0.96f)
    fun peerExit(): ExitTransition = fadeOut(fadeOutSpec())

    /** Content swapping in place (loading → loaded, tabs inside a screen). */
    fun crossfade(): ContentTransform = fadeIn(fadeInSpec(delay = FAST / 3)) togetherWith fadeOut(fadeOutSpec())
}

/**
 * False until the opening animation has handed over, so first-screen entrances play in view
 * instead of invisibly underneath the splash.
 */
val LocalAppReady = compositionLocalOf { true }
