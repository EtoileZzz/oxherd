package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Hold-to-confirm physical micro-interaction button according to the Fluid Motion specification:
 * - 2000ms charge progress layer fills steadily from 0% to 100% on pointer down.
 * - 160ms snap-back with cubic-bezier(.22, 1, .36, 1) if released prematurely.
 * - Haptic click triggered only when 100% threshold is reached.
 *
 * @param onConfirm Invoked immediately upon 100% full charge
 * @param holdDurationMs Total duration in ms to charge to 100% (default 2000ms)
 * @param snapBackDurationMs Duration in ms to snap back to 0% upon release (default 160ms)
 * @param dangerColor Fill color of the charging progress layer
 * @param containerColor Base background color of the button
 * @param contentColor Text/Icon content color
 * @param shape Corner clipping shape
 */
@Composable
fun DangerHoldButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Int = FluidMotionTokens.DUR_HOLD,
    snapBackDurationMs: Int = FluidMotionTokens.DUR_HOLD_SNAP_BACK,
    dangerColor: Color = Color(0xFFD32F2F),
    containerColor: Color = Color(0xFF2C1618),
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    height: Dp = 48.dp,
    icon: @Composable (() -> Unit)? = null
) {
    DangerHoldButton(
        onConfirm = onConfirm,
        modifier = modifier.height(height),
        holdDurationMs = holdDurationMs,
        snapBackDurationMs = snapBackDurationMs,
        dangerColor = dangerColor,
        containerColor = containerColor,
        enabled = enabled,
        shape = shape
    ) { progress, isHolding ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isHolding) "$text ${(progress * 100).toInt()}%" else text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Flexible custom-content overload of [DangerHoldButton].
 */
@Composable
fun DangerHoldButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Int = FluidMotionTokens.DUR_HOLD,
    snapBackDurationMs: Int = FluidMotionTokens.DUR_HOLD_SNAP_BACK,
    dangerColor: Color = Color(0xFFD32F2F),
    containerColor: Color = Color(0xFF2C1618),
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable (progress: Float, isHolding: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current

    val resolvedHoldMs = remember(perfLevel, motionScale, holdDurationMs) {
        perfLevel.degradeDuration(holdDurationMs, motionScale)
    }
    val resolvedSnapBackMs = remember(perfLevel, motionScale, snapBackDurationMs) {
        perfLevel.degradeDuration(snapBackDurationMs, motionScale)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .pointerInput(enabled, resolvedHoldMs, resolvedSnapBackMs) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isHolding = true

                    var chargeCompleted = false
                    val chargeJob = coroutineScope.launch {
                        try {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = resolvedHoldMs,
                                    easing = LinearEasing
                                )
                            )
                            if (progress.value >= 0.999f) {
                                chargeCompleted = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                            }
                        } catch (e: CancellationException) {
                            // Cancelled if released early
                        }
                    }

                    waitForUpOrCancellation()
                    chargeJob.cancel()
                    isHolding = false

                    if (!chargeCompleted) {
                        // 160ms snap-back with cubic-bezier(.22, 1, .36, 1)
                        coroutineScope.launch {
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = resolvedSnapBackMs,
                                    easing = FluidMotionTokens.EaseFadeOut
                                )
                            )
                        }
                    } else {
                        coroutineScope.launch {
                            progress.snapTo(0f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Charging Progress Layer (.hold-fill)
        if (progress.value > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.value.coerceIn(0f, 1f))
                    .align(Alignment.CenterStart)
                    .background(dangerColor)
            )
        }

        // Inner Foreground Content
        content(progress.value, isHolding)
    }
}
