package com.oxherd.app.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt

/**
 * Fluid Motion Design Tokens according to the Fluid Motion Design Specification.
 *
 * Defines the 15 non-linear Cubic-Bezier curves, standard step duration tokens,
 * stagger offsets, and global motion scale multiplier.
 */
object FluidMotionTokens {

    // ──────────────────────────────────────────────────────────────
    // 1. 15 Non-linear Cubic-Bezier Easing Curves
    // ──────────────────────────────────────────────────────────────

    /** 超强缓出 (Super strong ease-out) - 起步极快、收尾平顺长拖尾。默认主曲线。 */
    val EaseQuint: Easing = CubicBezierEasing(0.18f, 1.0f, 0.28f, 1.0f)

    /** 急迫缓出 (Urgent ease-out) - 比 quint 起步更快，适合小元素快速归位、浮入落定、滑块连续滑动。 */
    val EaseExpo: Easing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)

    /** 柔和进出 (Gentle in-out) - 对称 S 型，加减速平稳，用于退场、淡出、交叉淡入。 */
    val EaseSoft: Easing = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)

    /** 强调曲线 (Material Emphasized) - 戏剧感强，用于共享轴推进、整屏位移。 */
    val EaseEmphasized: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)

    /** iOS 风格缓动 (iOS Smooth) - 减速段持续有力，用于侧滑抽屉、顺滑展开。 */
    val EaseIos: Easing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    /** 快速吸附 (Snappy snap) - 极短减速行程，用于轻微提示与快速吸附。 */
    val EaseSnappy: Easing = CubicBezierEasing(0.20f, 0.90f, 0.10f, 1.0f)

    /** 轻微回弹 (Spring bounce) - Over-shoot 约为 42%，用于焦点卡片强化触感回弹。 */
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.42f, 0.52f, 1.0f)

    /** 显著回弹 (Back overshoot) - Over-shoot 约为 30%，用于小卡片缩放浮入、旋入落定。 */
    val EaseBack: Easing = CubicBezierEasing(0.28f, 1.30f, 0.46f, 1.0f)

    /** 沉稳优雅 (Gentle float) - 平缓减速，用于页面整体上浮 (500ms)、条目沉稳切换。 */
    val EaseGentle: Easing = CubicBezierEasing(0.25f, 0.80f, 0.35f, 1.0f)

    /** 磁贴专用 (Tile flip) - 先快加速后快速稳固入位。 */
    val EaseTile: Easing = CubicBezierEasing(0.20f, 0.80f, 0.20f, 1.0f)

    /** 揭示式扩散 (Reveal sweep) - S 型平稳扫过，用于波前扩散。 */
    val EaseReveal: Easing = CubicBezierEasing(0.45f, 0.0f, 0.55f, 1.0f)

    /** 四次衰减 (Quartic out) - 用于拼图拼合等错峰落位。 */
    val EaseQuartOut: Easing = CubicBezierEasing(0.17f, 0.84f, 0.44f, 1.0f)

    /** 缓入退场 (Expo in) - 用于“先走一步”的退场动画，为新内容进场让位。 */
    val EaseExpoIn: Easing = CubicBezierEasing(0.70f, 0.0f, 0.84f, 0.0f)

    /** 重力落体反弹 (Gravity drop overshoot) - 用于落体式弹窗从顶部掉入并震动反弹。 */
    val EaseOvershoot: Easing = CubicBezierEasing(0.22f, 1.35f, 0.36f, 1.0f)

    /** 线性插值 (Linear) - 仅用于瞬切 (0ms) 或搭配透明度变化的墨滴缩放。 */
    val EaseLinear: Easing = LinearEasing

    // ── 语义化别名 ──
    val EaseCubic: Easing = EaseQuint
    val EaseSmooth: Easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f)
    val EaseFadeOut: Easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val EaseEnter: Easing = EaseQuint
    val EaseExit: Easing = EaseFadeOut

    // ──────────────────────────────────────────────────────────────
    // 2. 阶梯时长令牌规范 (Base Duration Tokens, 1.0x Scale)
    // ──────────────────────────────────────────────────────────────

    /** 按钮按压下陷时长 (90ms) */
    const val DUR_PRESS_IN: Int = 90

    /** 按钮抬起释放时长 (260ms) */
    const val DUR_PRESS_OUT: Int = 260

    /** 文案速隐时长 (120ms) */
    const val DUR_TEXT_OUT: Int = 120

    /** 文案速显时长 (220ms) */
    const val DUR_TEXT_IN: Int = 220

    /** 选项卡文字变色时长 (200ms) */
    const val DUR_TAB_COLOR: Int = 200

    /** 列表逐条淡入时长 (180ms) */
    const val DUR_LIST_ITEM: Int = 180

    /** 卡片内容浮入时长 (320ms) */
    const val DUR_CARD_IN: Int = 320

    /** 容器展开收起/几何补间时长 (340ms) */
    const val DUR_ZONE: Int = 340

    /** 胶囊滑块连续滑入目标时长 (360ms) */
    const val DUR_PILL: Int = 360

    /** 涟漪扩散基础时长 (380ms) */
    const val DUR_RIPPLE: Int = 380

    /** 弹窗进场时长 (460ms) */
    const val DUR_DIALOG_IN: Int = 460

    /** 弹窗退场时长 (320ms) */
    const val DUR_DIALOG_OUT: Int = 320

    /** 页面主块错峰上浮时长 (500ms) */
    const val DUR_BLOCK: Int = 500

    /** 按钮点击高光衰减总时长 (500ms) */
    const val DUR_GLOW: Int = 500

    /** 危险长按蓄力充满时长 (2000ms) */
    const val DUR_HOLD: Int = 2000

    /** 危险长按中途松开弹性回缩时长 (160ms) */
    const val DUR_HOLD_SNAP_BACK: Int = 160

    // ──────────────────────────────────────────────────────────────
    // 3. 错峰延迟步长令牌 (Stagger Tokens)
    // ──────────────────────────────────────────────────────────────

    /** 列表子项错峰步长 (30ms) */
    const val STAGGER_LIST: Int = 30

    /** 数据网格按列错峰步长 (34ms) */
    const val STAGGER_COLUMN: Int = 34

    /** 弹窗子组件逐条拼合错峰步长 (40ms) */
    const val STAGGER_MODAL: Int = 40

    /** 页面主模块上浮错峰步长 (60ms) */
    const val STAGGER_VIEW: Int = 60

    // ──────────────────────────────────────────────────────────────
    // 4. 交互高光参数令牌
    // ──────────────────────────────────────────────────────────────

    /** 点击高光峰值不透明度 (0.30) */
    const val GLOW_PEAK_OPACITY: Float = 0.30f

    /** 点击高光峰值时间点在总时长的百分比 (0.12 = 12%) */
    const val GLOW_PEAK_OFFSET: Float = 0.12f

    // ──────────────────────────────────────────────────────────────
    // 5. 辅助缩放与数值求解计算器
    // ──────────────────────────────────────────────────────────────

    /**
     * 根据全局 motionScale 计算实际补间时长
     */
    fun scaledDuration(baseDurationMs: Int, motionScale: Float = 1.0f): Int {
        if (baseDurationMs <= 0 || motionScale <= 0f) return 0
        return (baseDurationMs * motionScale).roundToInt().coerceAtLeast(0)
    }

    /**
     * 根据全局 motionScale 计算实际错峰延迟
     */
    fun scaledStagger(baseStaggerMs: Int, motionScale: Float = 1.0f): Int {
        if (baseStaggerMs <= 0 || motionScale <= 0f) return 0
        return (baseStaggerMs * motionScale).roundToInt().coerceAtLeast(0)
    }

    /**
     * 生成适应全局 motionScale 的 Compose TweenSpec
     */
    fun <T> fluidTween(
        durationMillis: Int,
        easing: Easing = EaseQuint,
        delayMillis: Int = 0,
        motionScale: Float = 1.0f
    ): TweenSpec<T> {
        return tween(
            durationMillis = scaledDuration(durationMillis, motionScale),
            delayMillis = scaledStagger(delayMillis, motionScale),
            easing = easing
        )
    }

    /**
     * 24 次二分法高精度三阶贝塞尔数值求解器 (满足逐帧 Canvas 绘制与物理插值)
     *
     * @param p1x 控制点 1 的 X 坐标 (0.0 ~ 1.0)
     * @param p1y 控制点 1 的 Y 坐标
     * @param p2x 控制点 2 的 X 坐标 (0.0 ~ 1.0)
     * @param p2y 控制点 2 的 Y 坐标
     * @param progress 线性进度 t (0.0 ~ 1.0)
     * @return 映射后的物理进度 (0.0 ~ 1.0)
     */
    fun cubicAt(p1x: Float, p1y: Float, p2x: Float, p2y: Float, progress: Float): Float {
        if (progress <= 0f) return 0f
        if (progress >= 1f) return 1f

        fun bez(t: Float, a: Float, b: Float): Float {
            val u = 1f - t
            return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
        }

        var lo = 0f
        var hi = 1f
        var mid = progress
        for (i in 0 until 24) {
            mid = (lo + hi) / 2f
            if (bez(mid, p1x, p2x) < progress) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return bez(mid, p1y, p2y)
    }
}

/**
 * CompositionLocal providing the global motion duration scale multiplier.
 * Default is 1.0f (1.0x).
 */
val LocalMotionScale = compositionLocalOf { 1.0f }
