package com.zooptype.ztype.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HapticEngine: Contextual haptic feedback for Keybrot.
 *
 * Provides different vibration patterns for different interactions:
 * - Hover tick: subtle click when a node becomes focused
 * - Selection thump: stronger pulse when a node is dived into
 * - Word commit: satisfying double-pulse when a word is committed
 * - Swipe gesture: quick sharp tick for swipe-up/down
 * - Engine hum: continuous low-level vibration during high-speed motion (optional)
 *
 * Uses VibrationEffect API (API 26+) for precise haptic control.
 */
class HapticEngine(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Whether haptics are enabled */
    var isEnabled = true

    /** Intensity multiplier (0.0 = off, 1.0 = full) */
    var intensity = 1.0f

    /** Whether engine hum is enabled */
    var engineHumEnabled = false

    // Pre-built vibration effects
    private val hoverEffect = VibrationEffect.createOneShot(
        10, scaleAmplitude(40)
    )

    private val selectionEffect = VibrationEffect.createOneShot(
        30, scaleAmplitude(150)
    )

    private val wordCommitEffect = VibrationEffect.createWaveform(
        longArrayOf(0, 20, 50, 30),
        intArrayOf(0, 180, 0, 120),
        -1 // No repeat
    )

    private val swipeEffect = VibrationEffect.createOneShot(
        15, scaleAmplitude(100)
    )

    private val resetEffect = VibrationEffect.createOneShot(
        25, scaleAmplitude(80)
    )

    /**
     * Subtle tick when a node becomes the focus of the camera.
     */
    fun onNodeHover() {
        if (!isEnabled) return
        vibrate(hoverEffect)
    }

    /**
     * Strong thump when a node is selected (dived into).
     */
    fun onNodeSelected() {
        if (!isEnabled) return
        vibrate(selectionEffect)
    }

    /**
     * Satisfying pulse when a complete word is committed.
     */
    fun onWordCommitted() {
        if (!isEnabled) return
        vibrate(wordCommitEffect)
    }

    /**
     * Quick tick for swipe gestures (up/down).
     */
    fun onSwipeGesture() {
        if (!isEnabled) return
        vibrate(swipeEffect)
    }

    /**
     * Feedback for reset/backspace action.
     */
    fun onReset() {
        if (!isEnabled) return
        vibrate(resetEffect)
    }

    /**
     * Optional: continuous engine hum based on velocity.
     * Call each frame with current speed (0.0 = stopped, 1.0 = max speed).
     */
    fun updateEngineHum(speed: Float) {
        if (!isEnabled || !engineHumEnabled) return
        // TODO: Implement continuous haptic using VibrationEffect.Composition (API 30+)
        // For now, emit periodic ticks at high speed
        if (speed > 0.5f) {
            val amplitude = (speed * 30 * intensity).toInt().coerceIn(1, 255)
            vibrate(VibrationEffect.createOneShot(5, amplitude))
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        try {
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            // Silently fail if vibration not available
        }
    }

    private fun scaleAmplitude(base: Int): Int {
        return (base * intensity).toInt().coerceIn(1, 255)
    }

    fun release() {
        vibrator.cancel()
    }
}
