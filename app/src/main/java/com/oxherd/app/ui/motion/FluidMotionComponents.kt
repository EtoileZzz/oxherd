package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Fluid Motion Specification composable animations & modifiers.
 * Implements:
 * - Scene 4: viewIn (Header drop -24dp -> 0dp, content float +16dp -> 0dp)
 * - Scene 5: listIn (Staggered cascading items entrance index * 30ms)
 * - Scene 6: temporalShift (Station switching horizontal displacement with direction sensing)
 */

/**
 * Scene 4 viewIn: Header entrance.
 * Drops from y = -24.dp with gentle ease-out (500ms).
 */
fun Modifier.fluidViewInHeader(): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_BLOCK, motionScale)
    }

    val offsetY = remember { Animatable(-24f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (duration <= 0) {
            offsetY.snapTo(0f)
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (duration * 0.6f).roundToInt(),
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        if (duration > 0) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FluidMotionTokens.EaseGentle
                )
            )
        }
    }

    this
        .offset { IntOffset(0, offsetY.value.dp.roundToPx()) }
        .alpha(alpha.value)
}

/**
 * Scene 4 viewIn: Main content entrance.
 * Floats from y = 16.dp and scale = 0.97f with gentle ease-out, staggered by 60ms.
 */
fun Modifier.fluidViewInContent(): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_BLOCK, motionScale)
    }
    val delay = remember(perfLevel, motionScale) {
        perfLevel.degradeStagger(FluidMotionTokens.STAGGER_VIEW, motionScale)
    }

    val offsetY = remember { Animatable(16f) }
    val scale = remember { Animatable(0.97f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (duration <= 0) {
            offsetY.snapTo(0f)
            scale.snapTo(1f)
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    delayMillis = delay,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        if (duration > 0) {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    delayMillis = delay,
                    easing = FluidMotionTokens.EaseGentle
                )
            )
        }
    }
    LaunchedEffect(Unit) {
        if (duration > 0) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = duration,
                    delayMillis = delay,
                    easing = FluidMotionTokens.EaseGentle
                )
            )
        }
    }

    this
        .offset { IntOffset(0, offsetY.value.dp.roundToPx()) }
        .scale(scale.value)
        .alpha(alpha.value)
}

/**
 * Scene 5 listIn: Staggered list item cascading entrance.
 * Staggers by 30ms per item index, animating y from 14dp -> 0dp and alpha 0f -> 1f.
 */
fun Modifier.fluidListInItem(index: Int): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_CARD_IN, motionScale)
    }
    val staggerDelay = remember(perfLevel, motionScale, index) {
        val baseDelay = (index * FluidMotionTokens.STAGGER_LIST).coerceAtMost(300)
        perfLevel.degradeStagger(baseDelay, motionScale)
    }

    val offsetY = remember { Animatable(14f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(index) {
        if (duration <= 0) {
            offsetY.snapTo(0f)
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    delayMillis = staggerDelay,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        }
    }
    LaunchedEffect(index) {
        if (duration > 0) {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = duration,
                    delayMillis = staggerDelay,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        }
    }

    this
        .offset { IntOffset(0, offsetY.value.dp.roundToPx()) }
        .alpha(alpha.value)
}

/**
 * Scene 6 temporalShift: Station / tab switching direction-aware shift.
 * Shifts along X axis based on target ID change, providing physical momentum.
 */
fun Modifier.fluidTemporalShift(stationId: Long): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(340, motionScale)
    }

    var previousStationId by remember { mutableStateOf(stationId) }
    val animOffsetX = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(1f) }

    LaunchedEffect(stationId) {
        if (stationId != previousStationId && duration > 0) {
            val direction = if (stationId > previousStationId) 1f else -1f
            previousStationId = stationId

            // Snap to initial shifted position and animate to zero
            animOffsetX.snapTo(20f * direction)
            animAlpha.snapTo(0.4f)

            animAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        }
    }
    LaunchedEffect(stationId) {
        if (animOffsetX.value != 0f && duration > 0) {
            animOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FluidMotionTokens.EaseExpo
                )
            )
        }
    }

    this
        .offset { IntOffset(animOffsetX.value.dp.roundToPx(), 0) }
        .alpha(animAlpha.value)
}
