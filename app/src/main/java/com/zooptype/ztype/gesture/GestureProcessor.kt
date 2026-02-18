package com.zooptype.ztype.gesture

import android.view.MotionEvent
import com.zooptype.ztype.haptics.HapticEngine
import com.zooptype.ztype.physics.DivePhysics
import com.zooptype.ztype.trie.TrieNavigator
import kotlin.math.abs

/**
 * GestureProcessor: Bridges raw touch events to physics + IME actions.
 *
 * Recognizes:
 * - Steering: finger movement → camera rotation via DivePhysics
 * - Swipe Up: fast upward swipe → accept/commit current predicted word
 * - Swipe Down: fast downward swipe → reset/backspace
 * - Linger: prolonged hover near a node → magnetism → selection
 *
 * Does NOT handle node hit-testing (that's done in the render loop
 * based on camera direction + node positions). This class only deals
 * with the raw touch → gesture classification.
 */
class GestureProcessor(
    private val divePhysics: DivePhysics,
    private val trieNavigator: TrieNavigator,
    private val hapticEngine: HapticEngine,
    private val onLetterSelected: (Char) -> Unit,
    private val onWordAccepted: () -> Unit,
    private val onReset: () -> Unit
) {

    // Swipe detection state
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartTime = 0L
    private var isSwipeCandidate = false

    // Selection state (set by the render loop when a node passes zoom threshold)
    @Volatile
    var pendingSelection: Char? = null

    companion object {
        /** Minimum swipe distance (pixels) to trigger a gesture */
        const val MIN_SWIPE_DISTANCE = 100f

        /** Maximum swipe time (ms) — must be fast to be a swipe */
        const val MAX_SWIPE_TIME = 300L

        /** Minimum vertical-to-horizontal ratio for a directional swipe */
        const val SWIPE_DIRECTION_RATIO = 1.5f

        /** Maximum horizontal movement allowed during a vertical swipe */
        const val MAX_SWIPE_CROSS_AXIS = 80f
    }

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> onCancel()
        }
    }

    private fun onDown(event: MotionEvent) {
        val x = event.x
        val y = event.y

        swipeStartX = x
        swipeStartY = y
        swipeStartTime = System.currentTimeMillis()
        isSwipeCandidate = true

        divePhysics.onTouchDown(x, y)
    }

    private fun onMove(event: MotionEvent) {
        val x = event.x
        val y = event.y

        // Check if this is still a potential swipe
        val dx = x - swipeStartX
        val dy = y - swipeStartY
        val elapsed = System.currentTimeMillis() - swipeStartTime

        // If too much time has passed or too much lateral movement, it's steering not a swipe
        if (elapsed > MAX_SWIPE_TIME * 2) {
            isSwipeCandidate = false
        }

        // Forward to physics for steering
        divePhysics.onTouchMove(x, y)

        // Check for pending selection from render loop
        processPendingSelection()
    }

    private fun onUp(event: MotionEvent) {
        val x = event.x
        val y = event.y
        val elapsed = System.currentTimeMillis() - swipeStartTime

        divePhysics.onTouchUp(x, y)

        // Check for swipe gestures
        if (isSwipeCandidate && elapsed <= MAX_SWIPE_TIME) {
            val dx = x - swipeStartX
            val dy = y - swipeStartY
            val absDx = abs(dx)
            val absDy = abs(dy)

            // Check for vertical swipe
            if (absDy >= MIN_SWIPE_DISTANCE && absDy > absDx * SWIPE_DIRECTION_RATIO) {
                if (dy < 0) {
                    // SWIPE UP → Accept/commit word
                    onSwipeUp()
                } else {
                    // SWIPE DOWN → Reset/backspace
                    onSwipeDown()
                }
                return
            }
        }

        // Not a swipe — check for pending selection
        processPendingSelection()

        isSwipeCandidate = false
    }

    private fun onCancel() {
        divePhysics.onTouchUp(0f, 0f)
        isSwipeCandidate = false
    }

    private fun onSwipeUp() {
        hapticEngine.onSwipeGesture()
        onWordAccepted()
    }

    private fun onSwipeDown() {
        hapticEngine.onSwipeGesture()
        onReset()
    }

    /**
     * Process any pending node selection (set by the render loop
     * when zoom progress crosses the selection threshold).
     */
    private fun processPendingSelection() {
        val char = pendingSelection
        if (char != null) {
            pendingSelection = null
            onLetterSelected(char)
        }
    }

    /**
     * Called by the render loop when a node selection occurs
     * (zoom threshold crossed on a focused node).
     */
    fun notifyNodeSelected(char: Char) {
        pendingSelection = char
    }

    /**
     * Called by the render loop each frame to ensure pending selections
     * are processed even if no touch events are firing.
     * This handles the edge case where selection triggers between
     * touch events or right after touch-up.
     */
    fun pollPendingSelection() {
        processPendingSelection()
    }
}
