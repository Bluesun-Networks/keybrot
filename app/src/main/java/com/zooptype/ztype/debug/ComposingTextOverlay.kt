package com.zooptype.ztype.debug

import android.opengl.GLES30
import android.opengl.Matrix
import com.zooptype.ztype.engine.NodeMesh
import com.zooptype.ztype.engine.SDFFontAtlas
import com.zooptype.ztype.theme.ThemeColors
import com.zooptype.ztype.trie.TrieNavigator

/**
 * ComposingTextOverlay: Renders the current composing text and top predictions
 * as a 2D overlay at the bottom of the viewport.
 *
 * Layout:
 * ┌──────────────────────────────┐
 * │                              │
 * │        [3D Sphere]           │
 * │                              │
 * ├──────────────────────────────┤
 * │  hel|lo   help   helmet     │  ← composing + predictions
 * └──────────────────────────────┘
 *
 * The current prefix is rendered bright. The top prediction's completion
 * is shown as a dimmed "ghost" extension. Additional predictions are
 * shown to the right, separated by spacing.
 */
class ComposingTextOverlay {

    private lateinit var fontAtlas: SDFFontAtlas
    private var shaderProgram: Int = 0
    private lateinit var textMesh: NodeMesh

    private val orthoMatrix = FloatArray(16)

    // Configurable appearance
    var charWidth = 20f
    var charHeight = 32f
    var bottomMargin = 20f
    var predictionSpacing = 40f
    var maxPredictions = 3

    fun init(fontAtlas: SDFFontAtlas, shaderProgram: Int) {
        this.fontAtlas = fontAtlas
        this.shaderProgram = shaderProgram
        this.textMesh = NodeMesh()
    }

    fun render(
        trieNavigator: TrieNavigator,
        viewportWidth: Int,
        viewportHeight: Int,
        themeColors: ThemeColors?
    ) {
        val prefix = trieNavigator.getCurrentPrefix()
        if (prefix.isEmpty()) return // Nothing to show at root

        // Set up orthographic projection
        Matrix.orthoM(
            orthoMatrix, 0,
            0f, viewportWidth.toFloat(),
            0f, viewportHeight.toFloat(),
            -1f, 1f
        )

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(shaderProgram)

        val predictions = trieNavigator.getTopPredictions(maxPredictions)
        val topPrediction = predictions.firstOrNull()

        // Calculate total width for centering
        val ghostCompletion = if (topPrediction != null && topPrediction.startsWith(prefix)) {
            topPrediction.substring(prefix.length)
        } else {
            ""
        }
        val mainTextWidth = (prefix.length + ghostCompletion.length) * charWidth
        val totalWidth = mainTextWidth + predictions.drop(1).sumOf {
            (it.length * charWidth + predictionSpacing).toInt()
        }

        // Start position: centered horizontally, near bottom
        var cursorX = (viewportWidth - totalWidth) / 2f
        val y = bottomMargin

        // Colors from theme or defaults
        val prefixColor = themeColors?.let {
            floatArrayOf(it.focused.r, it.focused.g, it.focused.b, 1f)
        } ?: floatArrayOf(1f, 1f, 1f, 1f)

        val ghostColor = themeColors?.let {
            floatArrayOf(it.alphabet.r, it.alphabet.g, it.alphabet.b, 0.4f)
        } ?: floatArrayOf(0.7f, 0.7f, 0.7f, 0.4f)

        val predColor = themeColors?.let {
            floatArrayOf(it.alphabet.r, it.alphabet.g, it.alphabet.b, 0.5f)
        } ?: floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)

        // Render current prefix (bright)
        cursorX = drawString(prefix, cursorX, y, charWidth, charHeight, prefixColor, 0.2f)

        // Render ghost completion (dimmed)
        if (ghostCompletion.isNotEmpty()) {
            cursorX = drawString(ghostCompletion, cursorX, y, charWidth, charHeight, ghostColor, 0f)
        }

        // Render additional predictions
        for (prediction in predictions.drop(1)) {
            cursorX += predictionSpacing

            // Dim separator dot
            cursorX = drawString("·", cursorX, y, charWidth * 0.5f, charHeight, predColor, 0f)
            cursorX += charWidth * 0.3f

            cursorX = drawString(prediction, cursorX, y, charWidth * 0.85f, charHeight, predColor, 0f)
        }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    /**
     * Draw a string and return the cursor X position after the last character.
     */
    private fun drawString(
        text: String,
        startX: Float,
        y: Float,
        cw: Float,
        ch: Float,
        color: FloatArray,
        glowIntensity: Float
    ): Float {
        var cursorX = startX
        for (char in text) {
            if (char == ' ') {
                cursorX += cw * 0.6f
                continue
            }

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, cursorX, y, 0f)
            Matrix.scaleM(modelMatrix, 0, cw, ch, 1f)

            val mvpMatrix = FloatArray(16)
            Matrix.multiplyMM(mvpMatrix, 0, orthoMatrix, 0, modelMatrix, 0)

            val mvpLoc = GLES30.glGetUniformLocation(shaderProgram, "uMVPMatrix")
            GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)

            val colorLoc = GLES30.glGetUniformLocation(shaderProgram, "uColor")
            GLES30.glUniform4f(colorLoc, color[0], color[1], color[2], color[3])

            val glowLoc = GLES30.glGetUniformLocation(shaderProgram, "uGlowIntensity")
            GLES30.glUniform1f(glowLoc, glowIntensity)

            fontAtlas.renderChar(shaderProgram, char, textMesh)

            cursorX += cw
        }
        return cursorX
    }
}
