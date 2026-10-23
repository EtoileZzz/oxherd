package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated strikethrough text composable according to the Fluid Motion specification:
 * - Interpolated strike line from 0% to 100% width
 * - Smooth text fading from 1.0 to [completedAlpha]
 * - Precise per-line strikethrough coordinates using [TextLayoutResult]
 *
 * @param text The string to render
 * @param progress Strikethrough progress from 0.0 (no line, full alpha) to 1.0 (full line, faded)
 * @param strikeColor Color of the strikethrough line (defaults to [LocalContentColor])
 * @param strikeThickness Stroke width of the strike line
 * @param completedAlpha Final opacity of the text when strikethrough reaches 100%
 */
@Composable
fun StrikethroughText(
    text: String,
    progress: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    strikeColor: Color = Color.Unspecified,
    strikeThickness: Dp = 1.5.dp,
    completedAlpha: Float = 0.40f,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val textAlpha = 1.0f - (1.0f - completedAlpha) * clampedProgress

    val resolvedTextColor = if (color.isSpecified) {
        color.copy(alpha = color.alpha * textAlpha)
    } else {
        LocalContentColor.current.copy(alpha = LocalContentColor.current.alpha * textAlpha)
    }

    val resolvedStrikeColor = if (strikeColor.isSpecified) {
        strikeColor
    } else {
        LocalContentColor.current.copy(alpha = 0.85f)
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()

            if (clampedProgress > 0.001f) {
                val strokePx = strikeThickness.toPx()
                val result = layoutResult
                if (result != null && result.lineCount > 0) {
                    for (lineIndex in 0 until result.lineCount) {
                        val lineLeft = result.getLineLeft(lineIndex)
                        val lineRight = result.getLineRight(lineIndex)
                        val fullLineWidth = (lineRight - lineLeft).coerceAtLeast(0f)
                        val lineY = (result.getLineTop(lineIndex) + result.getLineBottom(lineIndex)) / 2f

                        val currentWidth = fullLineWidth * clampedProgress
                        if (currentWidth > 0f) {
                            drawLine(
                                color = resolvedStrikeColor,
                                start = Offset(lineLeft, lineY),
                                end = Offset(lineLeft + currentWidth, lineY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                } else {
                    // Fallback to simple center horizontal strike if layoutResult not yet measured
                    val centerY = size.height / 2f
                    val currentWidth = size.width * clampedProgress
                    drawLine(
                        color = resolvedStrikeColor,
                        start = Offset(0f, centerY),
                        end = Offset(currentWidth, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    ) {
        Text(
            text = text,
            color = resolvedTextColor,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layoutResult = it }
        )
    }
}

/**
 * State-driven overload of [StrikethroughText] that animates when [isCompleted] changes.
 *
 * Employs Fluid Motion 340ms [FluidMotionTokens.EaseQuint] curve, degraded appropriately
 * through [LocalPerfLevel] and scaled through [LocalMotionScale].
 */
@Composable
fun StrikethroughText(
    text: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    strikeColor: Color = Color.Unspecified,
    strikeThickness: Dp = 1.5.dp,
    completedAlpha: Float = 0.40f,
    durationMs: Int = FluidMotionTokens.DUR_ZONE,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val resolvedDuration = remember(perfLevel, motionScale, durationMs) {
        perfLevel.degradeDuration(durationMs, motionScale)
    }

    val animatedProgress = remember { Animatable(if (isCompleted) 1f else 0f) }

    LaunchedEffect(isCompleted, resolvedDuration) {
        val target = if (isCompleted) 1f else 0f
        if (resolvedDuration > 0) {
            animatedProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = resolvedDuration,
                    easing = FluidMotionTokens.EaseQuint
                )
            )
        } else {
            animatedProgress.snapTo(target)
        }
    }

    StrikethroughText(
        text = text,
        progress = animatedProgress.value,
        modifier = modifier,
        style = style,
        color = color,
        strikeColor = strikeColor,
        strikeThickness = strikeThickness,
        completedAlpha = completedAlpha,
        maxLines = maxLines,
        overflow = overflow
    )
}
