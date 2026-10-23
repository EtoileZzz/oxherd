package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe-to-complete physical interaction container adhering to the Fluid Motion Design Specification:
 * - Damped swipe gesture with non-linear spring resistance beyond threshold
 * - 64dp threshold for haptic click feedback
 * - Strikethrough growth proportional to swipe progress (0% to 100%)
 * - Freeze-inner container morphing (340ms quint with clipToBounds) to prevent reflow storms
 *
 * @param onComplete Callback invoked when swipe is released past the 64dp threshold
 * @param isCompleted External completion state
 * @param threshold Drag distance required to arm completion (defaults to 64.dp)
 * @param dismissOnComplete Whether the item morphs down to 0 height upon completion
 * @param onDismissed Optional callback invoked after container morph animation completes
 * @param background Background revealed under the swiping card
 * @param content Foreground card content, receives (swipeProgress, isCompleted)
 */
@Composable
fun SwipeToCompleteBox(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    threshold: Dp = 64.dp,
    dismissOnComplete: Boolean = true,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    onDismissed: (() -> Unit)? = null,
    background: @Composable (progress: Float, thresholdReached: Boolean) -> Unit = { progress, reached ->
        DefaultSwipeCompleteBackground(progress = progress, thresholdReached = reached, shape = shape)
    },
    content: @Composable (progress: Float, isCompleted: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current

    val thresholdPx = with(density) { threshold.toPx() }
    val resolvedDurZone = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_ZONE, motionScale)
    }

    val animOffsetX = remember { Animatable(0f) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var thresholdReached by remember { mutableStateOf(false) }

    // Freeze-inner morphing measurements
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }
    val morphCollapse = remember { Animatable(0f) } // 0f = full height, 1f = collapsed to 0
    var isMorphing by remember { mutableStateOf(false) }

    // External completion observer
    LaunchedEffect(isCompleted) {
        if (isCompleted && dismissOnComplete && !isMorphing) {
            isMorphing = true
            morphCollapse.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = resolvedDurZone,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
            onDismissed?.invoke()
        }
    }

    val currentOffset = animOffsetX.value
    val swipeProgress = if (isCompleted) 1.0f else (currentOffset / thresholdPx).coerceIn(0f, 1f)

    // Calculate outer container height for freeze-inner morphing
    val measuredHeightDp = with(density) { measuredHeightPx.toDp() }
    val containerHeightModifier = if (isMorphing && measuredHeightPx > 0f) {
        val remainingFraction = (1f - morphCollapse.value).coerceIn(0f, 1f)
        Modifier.height(measuredHeightDp * remainingFraction)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(containerHeightModifier)
            .clipToBounds() // Strict bound clipping: freeze inner without reflow
            .onGloballyPositioned { coords ->
                if (!isMorphing && coords.size.height > 0) {
                    measuredHeightPx = coords.size.height.toFloat()
                }
            }
    ) {
        // Inner content wrapper frozen at measured height during morph
        Box(
            modifier = if (isMorphing && measuredHeightDp > 0.dp) {
                Modifier.fillMaxWidth().requiredHeight(measuredHeightDp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            // Background revealed under swipe
            background(swipeProgress, thresholdReached)

            // Swiping Foreground Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(currentOffset.roundToInt(), 0) }
                    .clip(shape)
                    .pointerInput(enabled, isCompleted, isMorphing) {
                        if (!enabled || isCompleted || isMorphing) return@pointerInput

                        detectHorizontalDragGestures(
                            onDragStart = {
                                rawDragX = animOffsetX.value
                            },
                            onDragEnd = {
                                if (thresholdReached) {
                                    onComplete()
                                    if (dismissOnComplete) {
                                        isMorphing = true
                                        coroutineScope.launch {
                                            morphCollapse.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(
                                                    durationMillis = resolvedDurZone,
                                                    easing = FluidMotionTokens.EaseQuint
                                                )
                                            )
                                            onDismissed?.invoke()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            animOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                            thresholdReached = false
                                            rawDragX = 0f
                                        }
                                    }
                                } else {
                                    // Release before threshold: spring back to 0
                                    coroutineScope.launch {
                                        animOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                        thresholdReached = false
                                        rawDragX = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    animOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    thresholdReached = false
                                    rawDragX = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                rawDragX += dragAmount

                                // Non-linear spring damping beyond threshold
                                val visual = if (rawDragX <= 0f) {
                                    0f
                                } else if (rawDragX <= thresholdPx) {
                                    rawDragX
                                } else {
                                    thresholdPx + (rawDragX - thresholdPx) * 0.32f
                                }

                                coroutineScope.launch {
                                    animOffsetX.snapTo(visual)
                                }

                                // 64dp haptic click trigger
                                if (visual >= thresholdPx && !thresholdReached) {
                                    thresholdReached = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (visual < thresholdPx && thresholdReached) {
                                    thresholdReached = false
                                }
                            }
                        )
                    }
            ) {
                content(swipeProgress, isCompleted)
            }
        }
    }
}

/**
 * Default background for [SwipeToCompleteBox] showing fluid reveal of checkmark indicator.
 */
@Composable
fun DefaultSwipeCompleteBackground(
    progress: Float,
    thresholdReached: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    activeColor: Color = Color(0xFF2E7D32),
    inactiveColor: Color = Color(0xFF1B221E)
) {
    val bgColor = if (thresholdReached) activeColor else inactiveColor
    val checkAlpha = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (thresholdReached) 0.95f else 0.20f * checkAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = if (thresholdReached) activeColor else Color.White.copy(alpha = checkAlpha),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
