package com.oxherd.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 3-State state machine states for the continuous pill slider.
 */
enum class PillState {
    /** 0ms direct placement: initial mount, orientation change, resize */
    DIRECT_PLACEMENT,
    /** 360ms expo smooth slide: triggered when selected index changes */
    SMOOTH_SLIDE,
    /** In-flight retargeting: smooth redirect from current position when interrupted */
    RETARGETING
}

/**
 * Bounds of a pill target inside its parent container.
 */
data class PillTarget(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

/**
 * State holder for continuous pill sliding with the 3-state state machine.
 */
@Stable
class SlidePillState(initialIndex: Int = 0) {
    var selectedIndex by mutableStateOf(initialIndex)
    val animX = Animatable(0f)
    val animY = Animatable(0f)
    val animWidth = Animatable(0f)
    val animHeight = Animatable(0f)

    var pillState by mutableStateOf(PillState.DIRECT_PLACEMENT)
        private set

    var isInitialized by mutableStateOf(false)
        private set

    private var previousIndex: Int = initialIndex

    /**
     * Updates target layout rect adhering to the 3-state state machine:
     * 1. Direct placement (0ms) on mount or layout resize.
     * 2. Smooth slide (360ms expo) on index change.
     * 3. Continuous retargeting without jump if interrupted in-flight.
     */
    suspend fun updateTarget(
        index: Int,
        target: PillTarget,
        durationMs: Int,
        easing: Easing = FluidMotionTokens.EaseExpo
    ) {
        if (target.width <= 0f || target.height <= 0f) return

        if (!isInitialized) {
            // State 1: Direct placement (0ms)
            pillState = PillState.DIRECT_PLACEMENT
            animX.snapTo(target.x)
            animY.snapTo(target.y)
            animWidth.snapTo(target.width)
            animHeight.snapTo(target.height)
            isInitialized = true
            selectedIndex = index
            previousIndex = index
            return
        }

        if (index == previousIndex) {
            // Layout size update without index change -> snap directly to stay aligned
            animX.snapTo(target.x)
            animY.snapTo(target.y)
            animWidth.snapTo(target.width)
            animHeight.snapTo(target.height)
            return
        }

        // State 2 / 3: Smooth slide or retargeting
        val isRunning = animX.isRunning || animWidth.isRunning
        pillState = if (isRunning) PillState.RETARGETING else PillState.SMOOTH_SLIDE
        selectedIndex = index
        previousIndex = index

        if (durationMs <= 0) {
            animX.snapTo(target.x)
            animY.snapTo(target.y)
            animWidth.snapTo(target.width)
            animHeight.snapTo(target.height)
            return
        }

        val spec = tween<Float>(durationMillis = durationMs, easing = easing)
        coroutineScope {
            launch { animX.animateTo(target.x, spec) }
            launch { animY.animateTo(target.y, spec) }
            launch { animWidth.animateTo(target.width, spec) }
            launch { animHeight.animateTo(target.height, spec) }
        }
    }
}

/**
 * Creates and remembers a [SlidePillState].
 */
@Composable
fun rememberSlidePillState(initialIndex: Int = 0): SlidePillState {
    return remember(initialIndex) { SlidePillState(initialIndex) }
}

/**
 * Production Segmented Control / Station Selector featuring the continuous pill slider
 * with 3-state state machine:
 * - Direct placement (0ms) on mount and resize
 * - Smooth slide (360ms expo) on index change
 * - Continuous in-flight retargeting
 * - Immunity to 3D perspective / scale distortion via parent-local layout tree measurement
 */
@Composable
fun <T> SlidePillSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF1E222B),
    pillColor: Color = Color(0xFF2E384D),
    contentColor: Color = Color(0xFF909BAE),
    activeContentColor: Color = Color.White,
    containerShape: Shape = RoundedCornerShape(10.dp),
    pillShape: Shape = RoundedCornerShape(8.dp),
    height: Dp = 40.dp,
    pillPadding: Dp = 3.dp,
    labelProvider: (T) -> String = { it.toString() }
) {
    val density = LocalDensity.current
    val pillState = rememberSlidePillState(selectedIndex)
    val perfLevel = LocalPerfLevel.current
    val motionScale = LocalMotionScale.current
    val itemTargets = remember { mutableStateMapOf<Int, PillTarget>() }

    val resolvedDurPill = remember(perfLevel, motionScale) {
        perfLevel.degradeDuration(FluidMotionTokens.DUR_PILL, motionScale)
    }

    LaunchedEffect(selectedIndex, itemTargets[selectedIndex], resolvedDurPill) {
        val target = itemTargets[selectedIndex]
        if (target != null) {
            pillState.updateTarget(
                index = selectedIndex,
                target = target,
                durationMs = resolvedDurPill,
                easing = FluidMotionTokens.EaseExpo
            )
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(containerShape)
            .background(containerColor)
            .padding(pillPadding)
    ) {
        // Continuous Pill Layer
        if (pillState.animWidth.value > 0f) {
            val pillOffsetXDp = with(density) { pillState.animX.value.toDp() }
            val pillOffsetYDp = with(density) { pillState.animY.value.toDp() }
            val pillWidthDp = with(density) { pillState.animWidth.value.toDp() }
            val pillHeightDp = with(density) { pillState.animHeight.value.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = pillOffsetXDp, y = pillOffsetYDp)
                    .width(pillWidthDp)
                    .height(pillHeightDp)
                    .clip(pillShape)
                    .background(pillColor)
            )
        }

        // Segmented Items Row
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val pos = coordinates.positionInParent()
                            itemTargets[index] = PillTarget(
                                x = pos.x,
                                y = pos.y,
                                width = coordinates.size.width.toFloat(),
                                height = coordinates.size.height.toFloat()
                            )
                        }
                        .clip(pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onItemSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        color = if (isSelected) activeContentColor else contentColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
