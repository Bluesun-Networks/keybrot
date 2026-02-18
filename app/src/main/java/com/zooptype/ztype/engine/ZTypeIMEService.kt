package com.zooptype.ztype.engine

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.zooptype.ztype.debug.DebugOverlay
import com.zooptype.ztype.gesture.GestureProcessor
import com.zooptype.ztype.haptics.HapticEngine
import com.zooptype.ztype.node.NodeLayoutEngine
import com.zooptype.ztype.physics.DivePhysics
import com.zooptype.ztype.trie.HybridTrie
import com.zooptype.ztype.trie.TrieNavigator

/**
 * Z-Type Input Method Service.
 *
 * The core entry point. This is an Android IME that renders a transparent
 * OpenGL ES 3.0 surface over the application, presenting an interactive
 * 3D sphere of alphanumeric and conceptual nodes.
 */
class ZTypeIMEService : InputMethodService() {

    private lateinit var glView: ZTypeGLSurfaceView
    private lateinit var renderer: ZTypeRenderer
    private lateinit var hybridTrie: HybridTrie
    private lateinit var trieNavigator: TrieNavigator
    private lateinit var divePhysics: DivePhysics
    private lateinit var gestureProcessor: GestureProcessor
    private lateinit var hapticEngine: HapticEngine
    private lateinit var nodeLayoutEngine: NodeLayoutEngine
    private lateinit var debugOverlay: DebugOverlay

    // Current composing text buffer
    private var composingText = StringBuilder()

    override fun onCreate() {
        super.onCreate()

        // Initialize the Hybrid Trie with dictionary
        hybridTrie = HybridTrie()
        hybridTrie.loadDefaultDictionary(this)

        // Initialize subsystems
        trieNavigator = TrieNavigator(hybridTrie)
        hapticEngine = HapticEngine(this)
        divePhysics = DivePhysics()
        debugOverlay = DebugOverlay()
        nodeLayoutEngine = NodeLayoutEngine()
    }

    override fun onCreateInputView(): View {
        // Force transparency on the IME window
        applyTransparency()

        // Create the renderer with all subsystems
        renderer = ZTypeRenderer(
            context = this,
            trieNavigator = trieNavigator,
            divePhysics = divePhysics,
            nodeLayoutEngine = nodeLayoutEngine,
            debugOverlay = debugOverlay
        )

        // Create the gesture processor that bridges touch → physics → trie
        gestureProcessor = GestureProcessor(
            divePhysics = divePhysics,
            trieNavigator = trieNavigator,
            hapticEngine = hapticEngine,
            onLetterSelected = { char -> onNodeSelected(char) },
            onWordAccepted = { acceptCurrentWord() },
            onReset = { resetInput() }
        )

        // Create the OpenGL surface view
        glView = ZTypeGLSurfaceView(this, renderer, gestureProcessor)

        return glView
    }

    /**
     * CRITICAL: Force true transparency on the IME window.
     * Without this, the OpenGL surface renders on an opaque background.
     */
    private fun applyTransparency() {
        window?.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)

            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) // Keep focusable for IME
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            // Allow transparent rendering
            w.setDimAmount(0f)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false)
            }

            // Force translucent attributes
            val params = w.attributes
            params.format = android.graphics.PixelFormat.TRANSLUCENT
            w.attributes = params
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Reset state for new input session
        composingText.clear()
        trieNavigator.reset()
        divePhysics.reset()

        // Inform renderer that we're active
        renderer.setActive(true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        renderer.setActive(false)
    }

    /**
     * Called when a node (letter/concept) is selected via the dive mechanic.
     * Updates the composing text and sends it to the input connection.
     */
    private fun onNodeSelected(char: Char) {
        composingText.append(char)

        // Update the composing text in the target app
        currentInputConnection?.let { ic ->
            ic.setComposingText(composingText, 1)
        }

        // Advance the trie navigator to show next predictions
        trieNavigator.advance(char)

        // Haptic feedback for selection
        hapticEngine.onNodeSelected()
    }

    /**
     * Swipe-up: Accept the current composing text (or top prediction) and commit.
     */
    private fun acceptCurrentWord() {
        val textToCommit = if (composingText.isNotEmpty()) {
            // Check if there's a better prediction to auto-complete
            val topPrediction = trieNavigator.getTopPrediction()
            if (topPrediction != null && topPrediction.startsWith(composingText.toString())) {
                topPrediction
            } else {
                composingText.toString()
            }
        } else {
            return
        }

        currentInputConnection?.let { ic ->
            ic.commitText("$textToCommit ", 1) // Commit with trailing space
        }

        // Boost frequency for this word in the trie
        hybridTrie.boostFrequency(textToCommit)

        // Reset for next word
        composingText.clear()
        trieNavigator.reset()
        divePhysics.reset()

        hapticEngine.onWordCommitted()
    }

    /**
     * Swipe-down: Reset/backspace the current input state.
     */
    private fun resetInput() {
        if (composingText.isNotEmpty()) {
            composingText.clear()
            currentInputConnection?.setComposingText("", 0)
            trieNavigator.reset()
            divePhysics.reset()
            hapticEngine.onReset()
        } else {
            // If already empty, send a backspace to delete previous character
            currentInputConnection?.deleteSurroundingText(1, 0)
            hapticEngine.onReset()
        }
    }

    /**
     * Auto-commit when an End-of-Word node is reached during diving.
     */
    fun onEndOfWordReached(word: String) {
        currentInputConnection?.let { ic ->
            ic.commitText("$word ", 1)
        }
        hybridTrie.boostFrequency(word)
        composingText.clear()
        trieNavigator.reset()
        divePhysics.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticEngine.release()
    }
}
