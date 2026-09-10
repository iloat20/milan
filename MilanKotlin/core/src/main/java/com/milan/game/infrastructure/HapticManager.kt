package com.milan.game.infrastructure

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * 统一触觉反馈管理器（Haptic Feedback Manager）。
 *
 * 提供分层触觉体验：
 * - 基础层：View.performHapticFeedback()（系统一致，无需权限）
 * - 增强层：VibrationEffect API（设备优化，需 VIBRATE 权限）
 * - 丰富层：Envelope Effect / Composition API（高端设备）
 *
 * 使用场景：
 * - 抽卡：点击(C) → 蓄力(TICK) → 揭晓(CONFIRM/ERROR) → UR强振(LONG_PRESS)
 * - 战斗：暴击(HEAVY_CLICK) → 击杀(SUCCESS)
 * - UI：按钮(CLICK) → 开关(TOGGLE) → 导航(NAVIGATE)
 */
object HapticManager {

    /** 触觉反馈强度等级 */
    enum class HapticLevel {
        LIGHT,      // 轻触：按钮点击、开关切换
        MEDIUM,     // 中等：抽卡蓄力、普通操作确认
        HEAVY,      // 重型：抽卡揭晓、暴击
        RICH        // 丰富：UR获得、击杀（需设备支持）
    }

    /** 获取 Vibrator 实例 */
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * 基础触觉反馈（View 级）。
     * 系统一致，无需权限，兼容所有设备。
     */
    fun performHapticFeedback(view: View, effect: Int) {
        try {
            view.performHapticFeedback(effect)
        } catch (_: Exception) { }
    }

    /**
     * 增强触觉反馈（VibrationEffect API）。
     * 设备优化，需要 VIBRATE 权限。
     */
    fun performVibration(context: Context, effect: VibrationEffect) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(effect)
            } else {
                // 无振幅控制：降级为简单振动
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: SecurityException) {
            // 权限被拒绝：静默失败
        } catch (_: Exception) { }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // 抽卡场景专用触觉
    // ══════════════════════════════════════════════════════════════════════════════

    /** 抽卡点击（单抽/十连按钮） */
    fun gachaClick(view: View) {
        performHapticFeedback(view, HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 抽卡蓄力阶段（能量聚集） */
    fun gachaCharge(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 短促脉冲序列
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 20, 40, 20, 40, 20),
                intArrayOf(0, 40, 0, 60, 0, 80),
                -1
            )
            performVibration(context, effect)
        }
    }

    /** 抽卡揭晓（SSR/UR） */
    fun gachaReveal(view: View, rarity: Int) {
        when {
            rarity >= 4 -> // UR：强烈长振
                performHapticFeedback(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            rarity >= 3 -> // SSR：中等确认
                performHapticFeedback(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            else -> // R/SR：轻触
                performHapticFeedback(view, HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** UR获得专属丰富振动（高端设备） */
    fun gachaUrSpecial(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 阶梯式增强振动
            val timings = longArrayOf(0, 50, 100, 150, 200, 300, 400, 500)
            val amplitudes = intArrayOf(0, 100, 60, 120, 80, 150, 100, 0)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            performVibration(context, effect)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // 战斗场景专用触觉
    // ══════════════════════════════════════════════════════════════════════════════

    /** 暴击命中 */
    fun battleCriticalHit(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createOneShot(80, 200)
            performVibration(context, effect)
        }
    }

    /** 击杀/胜利 */
    fun battleVictory(view: View) {
        performHapticFeedback(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONFIRM)
    }

    /** 受击/失败 */
    fun battleDefeat(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 双击短振
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 30, 80, 30),
                intArrayOf(0, 150, 0, 100),
                -1
            )
            performVibration(context, effect)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // UI 交互通用触觉
    // ══════════════════════════════════════════════════════════════════════════════

    /** 按钮点击 */
    fun buttonClick(view: View) {
        performHapticFeedback(view, HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** 开关切换 */
    fun toggleSwitch(view: View) {
        performHapticFeedback(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 导航切换 */
    fun navigationTap(view: View) {
        performHapticFeedback(view, HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 长按操作 */
    fun longPress(view: View) {
        performHapticFeedback(view, HapticFeedbackConstants.LONG_PRESS)
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // 养成/升级专用触觉
    // ══════════════════════════════════════════════════════════════════════════════

    /** 升级成功 */
    fun levelUp(view: View) {
        performHapticFeedback(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 突破/进阶成功 */
    fun breakthrough(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 三连递进振动
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 30, 60, 30, 60, 30),
                intArrayOf(0, 80, 0, 120, 0, 160),
                -1
            )
            performVibration(context, effect)
        }
    }

    /** 错误/操作失败 */
    fun errorBuzz(view: View) {
        performHapticFeedback(view, HapticFeedbackConstants.REJECT)
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // 设备能力查询
    // ══════════════════════════════════════════════════════════════════════════════

    /** 设备是否有振动器 */
    fun hasVibrator(context: Context): Boolean {
        return getVibrator(context)?.hasVibrator() == true
    }

    /** 设备是否支持振幅控制（丰富振动） */
    fun hasAmplitudeControl(context: Context): Boolean {
        return getVibrator(context)?.hasAmplitudeControl() == true
    }

    /** 设备是否支持 Envelope Effect（动态振动） */
    fun hasEnvelopeEffectSupport(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getVibrator(context)?.areEnvelopeEffectsSupported() == true
        } else false
    }
}
