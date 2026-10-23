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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bidirectional Swipe-to-complete physical interaction container adhering to Fluid Motion Design Specification:
 * - Supports BOTH left swipe and right swipe to complete / dismiss!
 * - Non-linear spring damping resistance beyond 64dp threshold
 * - 64dp threshold for tactile haptic click feedback
 * - Strikethrough growth proportional to swipe progress (0% to 100%)
 * - Freeze-inner container morphing (340ms quint with clipToBounds) to prevent reflow storms
 * - Complete physical dismissal lifecycle: item flies off and morphs down to 0 height BEFORE onComplete removal
 *
 * @param onComplete Callback invoked when swipe is released past the threshold
 * @param isCompleted External completion state
 * @param threshold Drag distance required to arm completion (defaults to 64.dp)
 * @param dismissOnComplete Whether the item morphs down to 0 height upon completion
 * @param canSwipeWhenCompleted Whether completed items can be swiped to toggle/restore
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
    canSwipeWhenCompleted: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    onDismissed: (() -> Unit)? = null,
    background: @Composable (progress: Float, thresholdReached: Boolean, isSwipingLeft: Boolean) -> Unit = { progress, reached, isLeft ->
        DefaultSwipeCompleteBackground(
            progress = progress,
            thresholdReached = reached,
            isSwipingLeft = isLeft,
            shape = shape
        )
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
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var thresholdReached by remember { mutableStateOf(false) }

    // Freeze-inner morphing measurements
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }
    val morphCollapse = remember { Animatable(0f) } // 0f = full height, 1f = collapsed to 0
    var isMorphing by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }

    // External completion observer (for programmatic completion without drag gesture)
    LaunchedEffect(isCompleted) {
        if (isCompleted && dismissOnComplete && !isMorphing && !isDismissing) {
            isMorphing = true
            isDismissing = true
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

    val currentOffset = if (isDragging) dragOffsetX else animOffsetX.value
    val absOffset = abs(currentOffset)
    val swipeProgress = if (isCompleted && absOffset == 0f && !canSwipeWhenCompleted) {
        1.0f
    } else {
        (absOffset / thresholdPx).coerceIn(0f, 1f)
    }
    val isSwipingLeft = currentOffset < 0f

    // Calculate outer container height for freeze-inner morphing
    val measuredHeightDp = with(density) { measuredHeightPx.toDp() }
    val isCollapsing = (isMorphing || isDismissing) && measuredHeightPx > 0f
    val containerHeightModifier = if (isCollapsing) {
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
                if (!isMorphing && !isDismissing && coords.size.height > 0) {
                    measuredHeightPx = coords.size.height.toFloat()
                }
            }
    ) {
        // Inner content wrapper frozen at measured height during morph
        Box(
            modifier = if (isCollapsing && measuredHeightDp > 0.dp) {
                Modifier.fillMaxWidth().requiredHeight(measuredHeightDp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            // Background revealed under swipe (supports both left and right directions)
            // Strictly guard against corner leakage: when abs(currentOffset) < 0.5f (static state),
            // the background Reveal layer is not rendered. Inset padding of 0.5.dp and shape clipping
            // ensure the background is strictly inside the foreground card bounds.
            if (absOffset >= 0.5f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(0.5.dp)
                        .clip(shape)
                ) {
                    background(swipeProgress, thresholdReached, isSwipingLeft)
                }
            }

            // Swiping Foreground Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(currentOffset.roundToInt(), 0) }
                    .clip(shape)
                    .pointerInput(enabled, isCompleted, canSwipeWhenCompleted, isDismissing, isMorphing) {
                        if (!enabled || isMorphing || isDismissing) return@pointerInput
                        if (isCompleted && !canSwipeWhenCompleted) return@pointerInput

                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                rawDragX = animOffsetX.value
                                dragOffsetX = animOffsetX.value
                            },
                            onDragEnd = {
                                isDragging = false
                                if (thresholdReached) {
                                    val sign = if (rawDragX >= 0f) 1f else -1f
                                    if (dismissOnComplete) {
                                        isDismissing = true
                                        isMorphing = true
                                        coroutineScope.launch {
                                            animOffsetX.snapTo(dragOffsetX)
                                            coroutineScope {
                                                launch {
                                                    animOffsetX.animateTo(
                                                        targetValue = sign * 1500f,
                                                        animationSpec = tween(
                                                            durationMillis = 260,
                                                            easing = FluidMotionTokens.EaseExpo
                                                        )
                                                    )
                                                }
                                                launch {
                                                    morphCollapse.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = tween(
                                                            durationMillis = resolvedDurZone,
                                                            easing = FluidMotionTokens.EaseQuint
                                                        )
                                                    )
                                                }
                                            }
                                            // ONLY after both animations finish, trigger completion and removal
                                            onComplete()
                                            onDismissed?.invoke()
                                        }
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onComplete()
                                        coroutineScope.launch {
                                            animOffsetX.snapTo(dragOffsetX)
                                            animOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                            thresholdReached = false
                                            rawDragX = 0f
                                            dragOffsetX = 0f
                                        }
                                    }
                                } else {
                                    // Release before threshold: spring back to 0
                                    coroutineScope.launch {
                                        animOffsetX.snapTo(dragOffsetX)
                                        animOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                        thresholdReached = false
                                        rawDragX = 0f
                                        dragOffsetX = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                coroutineScope.launch {
                                    animOffsetX.snapTo(dragOffsetX)
                                    animOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    thresholdReached = false
                                    rawDragX = 0f
                                    dragOffsetX = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                rawDragX += dragAmount

                                val absDrag = abs(rawDragX)
                                val sign = if (rawDragX >= 0f) 1f else -1f

                                // Non-linear spring damping beyond threshold in both directions
                                val damped = if (absDrag <= thresholdPx) {
                                    absDrag
                                } else {
                                    thresholdPx + (absDrag - thresholdPx) * 0.32f
                                }
                                dragOffsetX = damped * sign

                                // Tactile haptic feedback when crossing threshold
                                if (damped >= thresholdPx && !thresholdReached) {
                                    thresholdReached = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (damped < thresholdPx && thresholdReached) {
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
 * Default background for [SwipeToCompleteBox] showing fluid reveal of checkmark indicator
 * on the correct side (left side when swiping right, right side when swiping left).
 */
@Composable
fun DefaultSwipeCompleteBackground(
    progress: Float,
    thresholdReached: Boolean,
    isSwipingLeft: Boolean = false,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    activeColor: Color = Color(0xFF2E7D32),
    inactiveColor: Color = Color(0xFF1B221E),
    iconSize: Dp = 32.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    horizontalPadding: Dp = 20.dp
) {
    if (progress <= 0f) return
    val bgColor = if (thresholdReached) activeColor else inactiveColor
    val checkAlpha = progress.coerceIn(0f, 1f)
    val alignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = horizontalPadding),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (thresholdReached) 0.95f else 0.20f * checkAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                color = if (thresholdReached) activeColor else Color.White.copy(alpha = checkAlpha),
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    }
}
