package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt

/**
 * Three-tier performance degradation levels according to the Fluid Motion Design Specification.
 *
 * Degradation is centralized in this motion engine to keep feature code clean and unpolluted.
 * - [FULL]: High-end devices / desktop. Full fidelity: 3D perspective, Gaussian blur, staggered layers.
 * - [LITE]: Mid-range devices. Retains 3D and geometry morphing, but strips expensive GPU backdrop blurs.
 * - [MIN]: Low-end devices / battery-saver. Strips 3D, clears stagger delays to 0ms, and caps animation
 *          durations at <= 240ms.
 */
enum class PerfLevel {
    FULL,
    LITE,
    MIN;

    /**
     * Whether expensive GPU blur filters (such as `Modifier.blur` or backdrop blur) are allowed.
     */
    val allowsBlur: Boolean
        get() = this == FULL

    /**
     * Whether 3D spatial transformations (perspective, rotateX, rotateY) are allowed.
     */
    val allows3D: Boolean
        get() = this != MIN

    /**
     * Centralized duration degradation rule:
     * - FULL / LITE: scales by [motionScale].
     * - MIN: scales by [motionScale] and strictly caps duration at 240ms.
     */
    fun degradeDuration(baseDurationMs: Int, motionScale: Float = 1.0f): Int {
        if (baseDurationMs <= 0 || motionScale <= 0f) return 0
        val scaled = (baseDurationMs * motionScale).roundToInt()
        return when (this) {
            MIN -> scaled.coerceAtMost(240).coerceAtLeast(0)
            else -> scaled.coerceAtLeast(0)
        }
    }

    /**
     * Centralized stagger delay degradation rule:
     * - FULL / LITE: scales by [motionScale].
     * - MIN: clears stagger to 0ms so all elements enter simultaneously with zero delay.
     */
    fun degradeStagger(baseStaggerMs: Int, motionScale: Float = 1.0f): Int {
        if (baseStaggerMs <= 0 || motionScale <= 0f) return 0
        return when (this) {
            MIN -> 0
            else -> (baseStaggerMs * motionScale).roundToInt().coerceAtLeast(0)
        }
    }

    /**
     * Centralized effect (FX) duration degradation:
     * - MIN: caps effect duration at 260ms.
     */
    fun degradeFxDuration(baseFxDurationMs: Int, motionScale: Float = 1.0f): Int {
        if (baseFxDurationMs <= 0 || motionScale <= 0f) return 0
        val scaled = (baseFxDurationMs * motionScale).roundToInt()
        return when (this) {
            MIN -> scaled.coerceAtMost(260).coerceAtLeast(0)
            else -> scaled.coerceAtLeast(0)
        }
    }

    companion object {
        /**
         * Resolves a string value (e.g. from UserSettings or database) to a [PerfLevel].
         */
        fun fromString(value: String?): PerfLevel = when (value?.trim()?.uppercase()) {
            "LITE" -> LITE
            "MIN" -> MIN
            else -> FULL
        }
    }
}

/**
 * CompositionLocal providing current active performance tier. Defaults to [PerfLevel.FULL].
 */
val LocalPerfLevel = compositionLocalOf { PerfLevel.FULL }

/**
 * Convenience Composable helper to resolve animation duration using current [LocalPerfLevel]
 * and [LocalMotionScale].
 */
@Composable
@ReadOnlyComposable
fun resolveDuration(baseDurationMs: Int): Int {
    val perfLevel = LocalPerfLevel.current
    val scale = LocalMotionScale.current
    return perfLevel.degradeDuration(baseDurationMs, scale)
}

/**
 * Convenience Composable helper to resolve stagger offset using current [LocalPerfLevel]
 * and [LocalMotionScale].
 */
@Composable
@ReadOnlyComposable
fun resolveStagger(baseStaggerMs: Int): Int {
    val perfLevel = LocalPerfLevel.current
    val scale = LocalMotionScale.current
    return perfLevel.degradeStagger(baseStaggerMs, scale)
}

/**
 * Builds a [TweenSpec] automatically resolved against [LocalPerfLevel] and [LocalMotionScale].
 */
@Composable
fun <T> resolveTween(
    durationMillis: Int,
    easing: Easing = FluidMotionTokens.EaseQuint,
    delayMillis: Int = 0
): TweenSpec<T> {
    val duration = resolveDuration(durationMillis)
    val delay = resolveStagger(delayMillis)
    return tween(
        durationMillis = duration,
        delayMillis = delay,
        easing = easing
    )
}
