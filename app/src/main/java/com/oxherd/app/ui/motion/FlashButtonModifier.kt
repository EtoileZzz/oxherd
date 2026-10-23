package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Computes the solid flash highlight color defined by the Fluid Motion specification:
 * 92% pure white + 8% accent highlight color.
 *
 * This produces a crystal-clear, bright flash in both light and dark themes without
 * murky muddying or perceptual banding.
 */
fun computeFlashColor(accentColor: Color): Color {
    val r = 0.92f + accentColor.red * 0.08f
    val g = 0.92f + accentColor.green * 0.08f
    val b = 0.92f + accentColor.blue * 0.08f
    return Color(
        red = r.coerceIn(0f, 1f),
        green = g.coerceIn(0f, 1f),
        blue = b.coerceIn(0f, 1f),
        alpha = 1.0f
    )
}

/**
 * Replaces default Material ripples with the Fluid Motion solid flash highlight:
 * - Solid 92% white + 8% accent highlight
 * - Peak opacity: 0.30 at 12% of duration (~60ms)
 * - Decay: 500ms decay using cubic-bezier(0.22, 1, 0.36, 1)
 * - Strictly NO ripples (indication = null)
 * - Strict restart protocol: cancels running animation, resets alpha to 0, starts fresh tween.
 *
 * @param accentColor Theme accent color blended into the highlight at 8%
 * @param shape Shape bounding the flash highlight layer (prevents glow overflow)
 * @param enabled Whether click interactions are enabled
 * @param onClick Callback invoked upon click
 */
fun Modifier.flashButton(
    accentColor: Color = Color(0xFF5B8DEF),
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val motionScale = LocalMotionScale.current
    val perfLevel = LocalPerfLevel.current
    val coroutineScope = rememberCoroutineScope()
    val flashAlpha = remember { Animatable(0f) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    val flashColor = remember(accentColor) { computeFlashColor(accentColor) }

    val durationMs = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_GLOW, motionScale)
    }
    val riseTime = remember(durationMs) {
        (durationMs * FluidMotionTokens.GLOW_PEAK_OFFSET).roundToInt().coerceAtLeast(1)
    }
    val decayTime = remember(durationMs, riseTime) {
        (durationMs - riseTime).coerceAtLeast(1)
    }

    this
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Strictly NO ripples
            enabled = enabled
        ) {
            if (durationMs > 0) {
                // Four-step restart protocol: Cancel -> Reset -> Start fresh
                currentJob?.cancel()
                currentJob = coroutineScope.launch {
                    flashAlpha.snapTo(0f)
                    // Phase 1: Rapid attack to peak opacity 0.30 at 12% offset
                    flashAlpha.animateTo(
                        targetValue = FluidMotionTokens.GLOW_PEAK_OPACITY,
                        animationSpec = tween(
                            durationMillis = riseTime,
                            easing = LinearEasing
                        )
                    )
                    // Phase 2: Smooth 500ms decay with cubic-bezier(.22, 1, .36, 1)
                    flashAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = decayTime,
                            easing = FluidMotionTokens.EaseFadeOut
                        )
                    )
                }
            }
            onClick()
        }
        .drawWithContent {
            drawContent()
            val alpha = flashAlpha.value
            if (alpha > 0.001f) {
                drawRect(
                    color = flashColor.copy(alpha = alpha),
                    size = size
                )
            }
        }
}

/**
 * Overloaded `.flashButton` accepting a [RoundedCornerShape] with specific radius corner dp.
 */
fun Modifier.flashButton(
    accentColor: Color = Color(0xFF5B8DEF),
    cornerRadiusDp: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = flashButton(
    accentColor = accentColor,
    shape = RoundedCornerShape(cornerRadiusDp),
    enabled = enabled,
    onClick = onClick
)
