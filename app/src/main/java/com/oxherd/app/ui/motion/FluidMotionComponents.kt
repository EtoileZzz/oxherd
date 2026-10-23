package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fluid Motion Specification composable animations & modifiers.
 * Implements:
 * - Scene 4: viewIn (Header drop -24dp -> 0dp, content float +16dp -> 0dp)
 * - Scene 5: listIn (Staggered cascading items entrance index * 30ms with anti-flicker protection)
 * - Scene 6: temporalShift (Station switching horizontal displacement with direction sensing)
 */

/**
 * Scene 4 viewIn: Header entrance.
 * Drops from y = -24.dp with gentle ease-out (500ms).
 * Uses one-shot entrance tracking with [graphicsLayer] to smoothly float in without jumping.
 */
fun Modifier.fluidViewInHeader(): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_BLOCK, motionScale)
    }

    var hasAnimated by rememberSaveable(Unit) { mutableStateOf(false) }

    if (duration <= 0 || hasAnimated) {
        return@composed this
    }

    val offsetY = remember { Animatable(-24f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                launch {
                    alpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = (duration * 0.6f).roundToInt(),
                            easing = FluidMotionTokens.EaseQuint
                        )
                    )
                }
                launch {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = duration,
                            easing = FluidMotionTokens.EaseGentle
                        )
                    )
                }
            }
        } finally {
            hasAnimated = true
        }
    }

    this.graphicsLayer {
        translationY = offsetY.value.dp.toPx()
        this.alpha = alpha.value
    }
}

/**
 * Scene 4 viewIn: Main content entrance.
 * Floats from y = 16.dp and scale = 0.97f with gentle ease-out, staggered by 60ms.
 * Uses [graphicsLayer] and one-shot entrance tracking so the list container floats in smoothly
 * without layout reflows or recomposition jumps.
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

    var hasAnimated by rememberSaveable(Unit) { mutableStateOf(false) }

    if (duration <= 0 || hasAnimated) {
        return@composed this
    }

    val offsetY = remember { Animatable(16f) }
    val scale = remember { Animatable(0.97f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                launch {
                    alpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = duration,
                            delayMillis = delay,
                            easing = FluidMotionTokens.EaseQuint
                        )
                    )
                }
                launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = duration,
                            delayMillis = delay,
                            easing = FluidMotionTokens.EaseGentle
                        )
                    )
                }
                launch {
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
        } finally {
            hasAnimated = true
        }
    }

    this.graphicsLayer {
        translationY = offsetY.value.dp.toPx()
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

/**
 * Scene 5 listIn: Staggered list item cascading entrance.
 * Staggers by 30ms per item index, animating y from 14dp -> 0dp and alpha 0f -> 1f.
 * Uses one-shot entrance protection to prevent flickering during LazyColumn scrolling and item recycling.
 *
 * @param index Index of item in the list for stagger delay calculation
 * @param isInitialLoad Set to true only during initial list entrance. When false, items enter immediately without delay/fade.
 */
fun Modifier.fluidListInItem(
    index: Int,
    isInitialLoad: Boolean = true
): Modifier = fluidStaggeredEntrance(index = index, isInitialLoad = isInitialLoad)

/**
 * Scene 5 listIn: Staggered list item cascading entrance with explicit [isInitialLoad] control
 * and one-shot protection to guarantee 100% stable 60fps scrolling without flicker.
 */
fun Modifier.fluidStaggeredEntrance(
    index: Int,
    isInitialLoad: Boolean = true
): Modifier = composed {
    if (!isInitialLoad) return@composed this

    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_CARD_IN, motionScale)
    }

    // One-shot entrance tracking: once animated, never animate again during scroll/recomposition
    var hasAnimated by rememberSaveable(Unit) { mutableStateOf(false) }

    if (duration <= 0 || hasAnimated) {
        return@composed this
    }

    val staggerDelay = remember(perfLevel, motionScale, index) {
        val baseDelay = (index * FluidMotionTokens.STAGGER_LIST).coerceAtMost(300)
        perfLevel.degradeStagger(baseDelay, motionScale)
    }

    val offsetY = remember { Animatable(14f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                launch {
                    alpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = duration,
                            delayMillis = staggerDelay,
                            easing = FluidMotionTokens.EaseQuint
                        )
                    )
                }
                launch {
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
        } finally {
            hasAnimated = true
        }
    }

    this.graphicsLayer {
        translationY = offsetY.value.dp.toPx()
        this.alpha = alpha.value
    }
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

            coroutineScope {
                launch {
                    animAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = duration,
                            easing = FluidMotionTokens.EaseQuint
                        )
                    )
                }
                launch {
                    animOffsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = duration,
                            easing = FluidMotionTokens.EaseExpo
                        )
                    )
                }
            }
        }
    }

    this.graphicsLayer {
        translationX = animOffsetX.value.dp.toPx()
        this.alpha = animAlpha.value
    }
}

/**
 * Tab switching direction-aware shift:
 * When switching to tab 1, contents slide in from +24dp with EaseQuint 340ms;
 * When switching back to tab 0, slide in from -24dp.
 */
fun Modifier.fluidTabShift(selectedTab: Int): Modifier = composed {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val duration = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(340, motionScale)
    }

    var previousTab by remember { mutableIntStateOf(selectedTab) }
    val animOffsetX = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(1f) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != previousTab) {
            val direction = if (selectedTab > previousTab) 1f else -1f
            previousTab = selectedTab

            if (duration > 0) {
                // Direction-aware start offset: tab 1 from +24dp, tab 0 from -24dp
                animOffsetX.snapTo(24f * direction)
                animAlpha.snapTo(0.35f)

                coroutineScope {
                    launch {
                        animAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = duration,
                                easing = FluidMotionTokens.EaseQuint
                            )
                        )
                    }
                    launch {
                        animOffsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = duration,
                                easing = FluidMotionTokens.EaseQuint
                            )
                        )
                    }
                }
            } else {
                animOffsetX.snapTo(0f)
                animAlpha.snapTo(1f)
            }
        }
    }

    this.graphicsLayer {
        translationX = animOffsetX.value.dp.toPx()
        this.alpha = animAlpha.value
    }
}
