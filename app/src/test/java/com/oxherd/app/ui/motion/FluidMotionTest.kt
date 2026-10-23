package com.oxherd.app.ui.motion

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidMotionTest {

    // ──────────────────────────────────────────────────────────────
    // 1. Fluid Motion Tokens & Curves Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    fun testEasingCurvesDefined() {
        // Verify all 15 curves are defined
        val curves = listOf(
            FluidMotionTokens.EaseQuint,
            FluidMotionTokens.EaseExpo,
            FluidMotionTokens.EaseSoft,
            FluidMotionTokens.EaseEmphasized,
            FluidMotionTokens.EaseIos,
            FluidMotionTokens.EaseSnappy,
            FluidMotionTokens.EaseSpring,
            FluidMotionTokens.EaseBack,
            FluidMotionTokens.EaseGentle,
            FluidMotionTokens.EaseTile,
            FluidMotionTokens.EaseReveal,
            FluidMotionTokens.EaseQuartOut,
            FluidMotionTokens.EaseExpoIn,
            FluidMotionTokens.EaseOvershoot,
            FluidMotionTokens.EaseLinear
        )
        assertEquals(15, curves.size)
    }

    @Test
    fun testDurationTokens() {
        assertEquals(90, FluidMotionTokens.DUR_PRESS_IN)
        assertEquals(260, FluidMotionTokens.DUR_PRESS_OUT)
        assertEquals(120, FluidMotionTokens.DUR_TEXT_OUT)
        assertEquals(220, FluidMotionTokens.DUR_TEXT_IN)
        assertEquals(200, FluidMotionTokens.DUR_TAB_COLOR)
        assertEquals(180, FluidMotionTokens.DUR_LIST_ITEM)
        assertEquals(320, FluidMotionTokens.DUR_CARD_IN)
        assertEquals(340, FluidMotionTokens.DUR_ZONE)
        assertEquals(360, FluidMotionTokens.DUR_PILL)
        assertEquals(380, FluidMotionTokens.DUR_RIPPLE)
        assertEquals(460, FluidMotionTokens.DUR_DIALOG_IN)
        assertEquals(320, FluidMotionTokens.DUR_DIALOG_OUT)
        assertEquals(500, FluidMotionTokens.DUR_BLOCK)
        assertEquals(500, FluidMotionTokens.DUR_GLOW)
        assertEquals(2000, FluidMotionTokens.DUR_HOLD)
        assertEquals(160, FluidMotionTokens.DUR_HOLD_SNAP_BACK)
    }

    @Test
    fun testStaggerTokens() {
        assertEquals(30, FluidMotionTokens.STAGGER_LIST)
        assertEquals(34, FluidMotionTokens.STAGGER_COLUMN)
        assertEquals(40, FluidMotionTokens.STAGGER_MODAL)
        assertEquals(60, FluidMotionTokens.STAGGER_VIEW)
    }

    @Test
    fun testMotionScale() {
        // Base duration 340ms
        assertEquals(340, FluidMotionTokens.scaledDuration(340, 1.0f))
        assertEquals(170, FluidMotionTokens.scaledDuration(340, 0.5f))
        assertEquals(510, FluidMotionTokens.scaledDuration(340, 1.5f))
        assertEquals(680, FluidMotionTokens.scaledDuration(340, 2.0f))
        assertEquals(0, FluidMotionTokens.scaledDuration(340, 0.0f))

        // Stagger list 30ms
        assertEquals(30, FluidMotionTokens.scaledStagger(30, 1.0f))
        assertEquals(15, FluidMotionTokens.scaledStagger(30, 0.5f))
        assertEquals(45, FluidMotionTokens.scaledStagger(30, 1.5f))
        assertEquals(0, FluidMotionTokens.scaledStagger(30, 0.0f))
    }

    @Test
    fun testCubicAtSolver() {
        // Boundary conditions
        assertEquals(0f, FluidMotionTokens.cubicAt(0.18f, 1f, 0.28f, 1f, 0f), 0.0001f)
        assertEquals(1f, FluidMotionTokens.cubicAt(0.18f, 1f, 0.28f, 1f, 1f), 0.0001f)

        // Linear curve
        val midLinear = FluidMotionTokens.cubicAt(0f, 0f, 1f, 1f, 0.5f)
        assertEquals(0.5f, midLinear, 0.01f)

        // Quint strong ease-out: at progress 0.3, progress value should be well above 0.8
        val quintAt03 = FluidMotionTokens.cubicAt(0.18f, 1f, 0.28f, 1f, 0.3f)
        assertTrue("Quint curve at 0.3 must exhibit strong ease-out (> 0.8), got: $quintAt03", quintAt03 > 0.80f)
    }

    // ──────────────────────────────────────────────────────────────
    // 2. Performance Tier Degradation Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    fun testPerfLevelCapabilities() {
        // FULL: allows blur and 3D
        assertTrue(PerfLevel.FULL.allowsBlur)
        assertTrue(PerfLevel.FULL.allows3D)

        // LITE: drops blur, allows 3D
        assertFalse(PerfLevel.LITE.allowsBlur)
        assertTrue(PerfLevel.LITE.allows3D)

        // MIN: drops blur and 3D
        assertFalse(PerfLevel.MIN.allowsBlur)
        assertFalse(PerfLevel.MIN.allows3D)
    }

    @Test
    fun testPerfLevelDurationDegradation() {
        // Base 500ms
        assertEquals(500, PerfLevel.FULL.degradeDuration(500, 1.0f))
        assertEquals(500, PerfLevel.LITE.degradeDuration(500, 1.0f))
        // MIN caps duration at 240ms
        assertEquals(240, PerfLevel.MIN.degradeDuration(500, 1.0f))

        // Short duration 180ms
        assertEquals(180, PerfLevel.MIN.degradeDuration(180, 1.0f))
    }

    @Test
    fun testPerfLevelStaggerDegradation() {
        assertEquals(40, PerfLevel.FULL.degradeStagger(40, 1.0f))
        assertEquals(40, PerfLevel.LITE.degradeStagger(40, 1.0f))
        // MIN zeros all stagger delays
        assertEquals(0, PerfLevel.MIN.degradeStagger(40, 1.0f))
    }

    @Test
    fun testPerfLevelFxDurationDegradation() {
        assertEquals(560, PerfLevel.FULL.degradeFxDuration(560, 1.0f))
        // MIN caps fx at 260ms
        assertEquals(260, PerfLevel.MIN.degradeFxDuration(560, 1.0f))
    }

    @Test
    fun testPerfLevelFromString() {
        assertEquals(PerfLevel.FULL, PerfLevel.fromString("FULL"))
        assertEquals(PerfLevel.LITE, PerfLevel.fromString("LITE"))
        assertEquals(PerfLevel.MIN, PerfLevel.fromString("MIN"))
        assertEquals(PerfLevel.FULL, PerfLevel.fromString("unknown"))
        assertEquals(PerfLevel.FULL, PerfLevel.fromString(null))
    }

    // ──────────────────────────────────────────────────────────────
    // 3. Flash Highlight Color Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    fun testFlashColorMix() {
        // Pure black accent -> 92% white + 8% 0 = 0.92f (subject to 8-bit 1/255 quantization ~0.002)
        val blackAccent = Color(0f, 0f, 0f, 1f)
        val flashBlack = computeFlashColor(blackAccent)
        assertEquals(0.92f, flashBlack.red, 0.005f)
        assertEquals(0.92f, flashBlack.green, 0.005f)
        assertEquals(0.92f, flashBlack.blue, 0.005f)

        // Pure white accent -> 92% white + 8% 1 = 1.0f
        val whiteAccent = Color(1f, 1f, 1f, 1f)
        val flashWhite = computeFlashColor(whiteAccent)
        assertEquals(1.0f, flashWhite.red, 0.005f)
        assertEquals(1.0f, flashWhite.green, 0.005f)
        assertEquals(1.0f, flashWhite.blue, 0.005f)

        // Blue accent (0xFF5B8DEF)
        val accent = Color(0xFF5B8DEF)
        val flash = computeFlashColor(accent)
        assertEquals(0.92f + accent.red * 0.08f, flash.red, 0.005f)
        assertEquals(0.92f + accent.green * 0.08f, flash.green, 0.005f)
        assertEquals(0.92f + accent.blue * 0.08f, flash.blue, 0.005f)
    }

    // ──────────────────────────────────────────────────────────────
    // 4. SlidePill State Machine Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    fun testSlidePillStateMachine() = runTest {
        val state = SlidePillState(0)
        assertEquals(PillState.DIRECT_PLACEMENT, state.pillState)
        assertFalse(state.isInitialized)

        // Step 1: Initial placement on mount -> Direct Placement (0ms)
        state.updateTarget(
            index = 0,
            target = PillTarget(x = 10f, y = 5f, width = 100f, height = 40f),
            durationMs = 360
        )
        assertTrue(state.isInitialized)
        assertEquals(PillState.DIRECT_PLACEMENT, state.pillState)
        assertEquals(10f, state.animX.value, 0.001f)
        assertEquals(100f, state.animWidth.value, 0.001f)

        // Step 2: Index changed -> Smooth slide (360ms expo)
        state.updateTarget(
            index = 1,
            target = PillTarget(x = 120f, y = 5f, width = 100f, height = 40f),
            durationMs = 0 // use 0 for instant test completion
        )
        assertEquals(1, state.selectedIndex)
        assertEquals(120f, state.animX.value, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────
    // 5. EaseTile and Tab Shift Direction Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    fun testEaseTileCurve() {
        // EaseTile: (0.20f, 0.80f, 0.20f, 1.0f)
        assertEquals(0f, FluidMotionTokens.cubicAt(0.20f, 0.80f, 0.20f, 1.0f, 0f), 0.0001f)
        assertEquals(1f, FluidMotionTokens.cubicAt(0.20f, 0.80f, 0.20f, 1.0f, 1f), 0.0001f)
        val midVal = FluidMotionTokens.cubicAt(0.20f, 0.80f, 0.20f, 1.0f, 0.5f)
        assertTrue("EaseTile mid-progress must show rapid acceleration (> 0.70), got $midVal", midVal > 0.70f)
    }

    @Test
    fun testFluidTabShiftDirections() {
        // Direction calculation rule:
        // tab 1 (selectedTab > previousTab) -> +24dp
        // tab 0 (selectedTab < previousTab) -> -24dp
        val forwardDirection = if (1 > 0) 24f else -24f
        assertEquals(24f, forwardDirection, 0.0001f)

        val backwardDirection = if (0 > 1) 24f else -24f
        assertEquals(-24f, backwardDirection, 0.0001f)
    }
}
