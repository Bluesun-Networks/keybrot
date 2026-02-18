package com.zooptype.ztype.debug

import android.opengl.GLES30
import android.opengl.Matrix
import com.zooptype.ztype.BuildConfig
import com.zooptype.ztype.engine.SDFFontAtlas
import com.zooptype.ztype.engine.NodeMesh
import com.zooptype.ztype.physics.DivePhysics
import com.zooptype.ztype.trie.TrieNavigator

/**
 * DebugOverlay: Always-on debug HUD for development.
 *
 * Displays:
 * - FPS counter (with color coding: green > 55, yellow > 30, red < 30)
 * - Touch velocity vector (direction + magnitude)
 * - Camera angles (theta, phi)
 * - Zoom progress bar
 * - Magnetism indicator
 * - Trie state (current prefix, depth, prediction count)
 * - Focused node info
 *
 * Rendered as 2D overlay on top of the 3D scene using orthographic projection.
 */
class DebugOverlay {

    private var isEnabled = BuildConfig.SHOW_DEBUG_OVERLAY
    private lateinit var fontAtlas: SDFFontAtlas
    private var shaderProgram: Int = 0
    private lateinit var textMesh: NodeMesh

    // Orthographic projection for 2D overlay
    private val orthoMatrix = FloatArray(16)

    fun init(fontAtlas: SDFFontAtlas, shaderProgram: Int) {
        this.fontAtlas = fontAtlas
        this.shaderProgram = shaderProgram
        this.textMesh = NodeMesh()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun render(
        vpMatrix: FloatArray,
        fps: Float,
        deltaTime: Float,
        divePhysics: DivePhysics,
        trieNavigator: TrieNavigator,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        if (!isEnabled) return

        // Set up orthographic projection for 2D text overlay
        Matrix.orthoM(
            orthoMatrix, 0,
            0f, viewportWidth.toFloat(),
            0f, viewportHeight.toFloat(),
            -1f, 1f
        )

        // Disable depth testing for overlay
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(shaderProgram)

        val lineHeight = 24f
        val leftMargin = 10f
        val charWidth = 14f
        var y = viewportHeight.toFloat() - lineHeight

        // FPS
        val fpsColor = when {
            fps >= 55f -> floatArrayOf(0f, 1f, 0.4f, 0.8f) // Green
            fps >= 30f -> floatArrayOf(1f, 1f, 0f, 0.8f)    // Yellow
            else -> floatArrayOf(1f, 0.2f, 0.2f, 0.8f)      // Red
        }
        drawString("FPS: %.0f".format(fps), leftMargin, y, charWidth, lineHeight, fpsColor)
        y -= lineHeight

        // Delta time
        drawString("dt: %.1fms".format(deltaTime * 1000f), leftMargin, y, charWidth, lineHeight)
        y -= lineHeight

        // Camera angles
        drawString(
            "cam: θ=%.2f φ=%.2f".format(
                Math.toDegrees(divePhysics.cameraTheta.toDouble()),
                Math.toDegrees(divePhysics.cameraPhi.toDouble())
            ),
            leftMargin, y, charWidth, lineHeight
        )
        y -= lineHeight

        // Velocity
        drawString(
            "vel: %.3f, %.3f (spd: %.3f)".format(
                divePhysics.velocityX,
                divePhysics.velocityY,
                divePhysics.getSpeed()
            ),
            leftMargin, y, charWidth, lineHeight
        )
        y -= lineHeight

        // Zoom progress
        val zoomBar = buildZoomBar(divePhysics.getZoomProgress())
        drawString("zoom: $zoomBar %.0f%%".format(divePhysics.getZoomProgress() * 100),
            leftMargin, y, charWidth, lineHeight,
            floatArrayOf(0f, 1f, 1f, 0.8f))
        y -= lineHeight

        // Magnetism
        drawString(
            "mag: %.2f  linger: %.0fms".format(
                divePhysics.magnetism,
                divePhysics.lingerTime * 1000
            ),
            leftMargin, y, charWidth, lineHeight,
            floatArrayOf(1f, 0f, 1f, 0.8f)
        )
        y -= lineHeight

        // Trie state
        val prefix = trieNavigator.getCurrentPrefix()
        val depth = trieNavigator.getDepth()
        val nodeCount = trieNavigator.getCurrentNodes().size
        drawString(
            "trie: \"$prefix\" d=$depth nodes=$nodeCount",
            leftMargin, y, charWidth, lineHeight,
            floatArrayOf(0f, 1f, 0f, 0.8f)
        )
        y -= lineHeight

        // Top prediction
        val topPred = trieNavigator.getTopPrediction() ?: "(none)"
        drawString("pred: $topPred", leftMargin, y, charWidth, lineHeight)
        y -= lineHeight

        // Focus info
        drawString(
            "focus: node=${divePhysics.focusedNodeIndex} touch=${divePhysics.isTouching}",
            leftMargin, y, charWidth, lineHeight
        )

        // Re-enable depth testing
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawString(
        text: String,
        x: Float,
        y: Float,
        charWidth: Float,
        charHeight: Float,
        color: FloatArray = floatArrayOf(0.7f, 0.7f, 0.7f, 0.7f)
    ) {
        var cursorX = x
        for (char in text) {
            if (char == ' ') {
                cursorX += charWidth * 0.6f
                continue
            }

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, cursorX, y, 0f)
            Matrix.scaleM(modelMatrix, 0, charWidth, charHeight, 1f)

            val mvpMatrix = FloatArray(16)
            Matrix.multiplyMM(mvpMatrix, 0, orthoMatrix, 0, modelMatrix, 0)

            val mvpLoc = GLES30.glGetUniformLocation(shaderProgram, "uMVPMatrix")
            GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)

            val colorLoc = GLES30.glGetUniformLocation(shaderProgram, "uColor")
            GLES30.glUniform4f(colorLoc, color[0], color[1], color[2], color[3])

            val glowLoc = GLES30.glGetUniformLocation(shaderProgram, "uGlowIntensity")
            GLES30.glUniform1f(glowLoc, 0f) // No glow on debug text

            fontAtlas.renderChar(shaderProgram, char, textMesh)

            cursorX += charWidth
        }
    }

    private fun buildZoomBar(progress: Float): String {
        val filled = (progress * 10).toInt()
        val empty = 10 - filled
        return "[" + "#".repeat(filled) + "-".repeat(empty) + "]"
    }
}
