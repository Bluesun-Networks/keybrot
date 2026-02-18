package com.zooptype.ztype.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.zooptype.ztype.debug.ComposingTextOverlay
import com.zooptype.ztype.debug.DebugOverlay
import com.zooptype.ztype.gesture.GestureProcessor
import com.zooptype.ztype.haptics.HapticEngine
import com.zooptype.ztype.node.NodeLayoutEngine
import com.zooptype.ztype.node.SphereNode
import com.zooptype.ztype.physics.DivePhysics
import com.zooptype.ztype.theme.ThemeEngine
import com.zooptype.ztype.trie.TrieNavigator
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * The main OpenGL ES 3.0 Renderer for Keybrot.
 *
 * Orchestrates the complete render loop each frame:
 * 1. Calculate delta time
 * 2. Update physics (camera position, velocity smoothing)
 * 3. Layout nodes on sphere (Fibonacci distribution)
 * 4. Hit-test: determine which node the camera is facing
 * 5. Update focus/magnetism on the closest node
 * 6. Check for selection threshold (Mandelbrot trigger)
 * 7. Apply theme colors
 * 8. Render all nodes with SDF text + glow
 * 9. Render debug overlay
 */
class ZTypeRenderer(
    private val context: Context,
    private val trieNavigator: TrieNavigator,
    private val divePhysics: DivePhysics,
    private val nodeLayoutEngine: NodeLayoutEngine,
    private val debugOverlay: DebugOverlay
) : GLSurfaceView.Renderer {

    // Wired by IMEService after construction
    var gestureProcessor: GestureProcessor? = null
    var themeEngine: ThemeEngine? = null
    var hapticEngine: HapticEngine? = null

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    // Shader programs
    private var sdfTextShaderProgram: Int = 0

    // SDF Font Atlas
    private lateinit var sdfFontAtlas: SDFFontAtlas

    // Node mesh (shared geometry for all nodes)
    private lateinit var nodeMesh: NodeMesh

    // Composing text overlay (shows typed prefix + predictions)
    private val composingOverlay = ComposingTextOverlay()

    // Timing
    private var lastFrameTime = System.nanoTime()
    private var deltaTime = 0f
    private var frameCount = 0
    private var fpsAccumulator = 0f
    private var currentFps = 0f

    // State
    @Volatile
    private var isActive = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Current frame's rendered nodes (for hit-testing)
    private var currentFrameNodes: List<SphereNode> = emptyList()
    private var previousFocusedIndex = -1

    fun setActive(active: Boolean) {
        isActive = active
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Transparent clear color (CRITICAL for IME transparency)
        GLES30.glClearColor(0f, 0f, 0f, 0f)

        // Enable blending for transparency
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Enable depth testing for 3D
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        // Compile SDF text shader
        sdfTextShaderProgram = ShaderCompiler.compileProgram(
            SDF_VERTEX_SHADER, SDF_FRAGMENT_SHADER
        )

        // Initialize SDF font atlas
        sdfFontAtlas = SDFFontAtlas(context)

        // Initialize node mesh (a quad billboard for each node)
        nodeMesh = NodeMesh()

        // Initialize debug overlay
        debugOverlay.init(sdfFontAtlas, sdfTextShaderProgram)
        composingOverlay.init(sdfFontAtlas, sdfTextShaderProgram)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height

        // Perspective projection — looking into the sphere
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 60f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // --- TIMING ---
        val now = System.nanoTime()
        deltaTime = (now - lastFrameTime) / 1_000_000_000f
        lastFrameTime = now

        // FPS tracking
        frameCount++
        fpsAccumulator += deltaTime
        if (fpsAccumulator >= 1f) {
            currentFps = frameCount / fpsAccumulator
            frameCount = 0
            fpsAccumulator = 0f
        }

        // Clear with full transparency
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (!isActive) return

        // --- PHYSICS UPDATE ---
        divePhysics.update(deltaTime)
        divePhysics.applyCameraTransform(viewMatrix)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // --- NODE LAYOUT ---
        val trieNodes = trieNavigator.getCurrentNodes()
        val layoutNodes = nodeLayoutEngine.layoutOnSphere(
            trieNodes,
            divePhysics.getCurrentSphereRadius(),
            divePhysics.zoomProgress
        )
        nodeLayoutEngine.updateAnimation(deltaTime)

        // --- HIT TESTING ---
        val focusedIndex = performHitTest(layoutNodes)
        divePhysics.setFocusedNode(focusedIndex)

        // Haptic hover feedback when focus changes
        if (focusedIndex != previousFocusedIndex && focusedIndex >= 0) {
            hapticEngine?.onNodeHover()
        }
        previousFocusedIndex = focusedIndex

        // Apply focus attraction
        if (focusedIndex >= 0) {
            val lookDir = divePhysics.getLookDirection()
            nodeLayoutEngine.applyFocusAttraction(
                layoutNodes, focusedIndex, divePhysics.magnetism,
                lookDir[0], lookDir[1], lookDir[2]
            )
        }

        // --- SELECTION CHECK ---
        if (divePhysics.shouldSelect() && focusedIndex >= 0 && focusedIndex < layoutNodes.size) {
            val selectedNode = layoutNodes[focusedIndex]
            // Trigger Mandelbrot spawn animation from selected node's position
            nodeLayoutEngine.triggerMandelbrotSpawn(
                selectedNode.worldX, selectedNode.worldY, selectedNode.worldZ
            )
            // Notify gesture processor of selection
            val char = selectedNode.displayChar
            if (char != ' ') {
                gestureProcessor?.notifyNodeSelected(char)
            }
            // Reset zoom for next selection
            divePhysics.reset()
        }

        // --- THEME ---
        themeEngine?.applyTheme(layoutNodes)

        currentFrameNodes = layoutNodes

        // --- RENDER ---
        renderNodes(layoutNodes)

        // --- COMPOSING TEXT OVERLAY ---
        composingOverlay.render(
            trieNavigator = trieNavigator,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            themeColors = themeEngine?.getColors()
        )

        // --- DEBUG OVERLAY ---
        debugOverlay.render(
            vpMatrix = vpMatrix,
            fps = currentFps,
            deltaTime = deltaTime,
            divePhysics = divePhysics,
            trieNavigator = trieNavigator,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )

        // --- POLL PENDING SELECTIONS ---
        gestureProcessor?.pollPendingSelection()

        // --- ENGINE HUM ---
        hapticEngine?.updateEngineHum(divePhysics.getSpeed())
    }

    /**
     * Hit-test: Find which node is closest to the camera's look direction.
     *
     * Uses dot product between camera look vector and the direction
     * from camera to each node. The node with the highest dot product
     * (smallest angle) is the "focused" node.
     */
    private fun performHitTest(nodes: List<SphereNode>): Int {
        if (nodes.isEmpty()) return -1

        val lookDir = divePhysics.getLookDirection()
        var bestDot = -1f
        var bestIndex = -1

        for ((i, node) in nodes.withIndex()) {
            // Direction from origin to node (normalized)
            val dx = node.worldX
            val dy = node.worldY
            val dz = node.worldZ
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 0.001f) continue

            val nx = dx / len
            val ny = dy / len
            val nz = dz / len

            // Dot product with look direction
            val dot = lookDir[0] * nx + lookDir[1] * ny + lookDir[2] * nz

            if (dot > bestDot) {
                bestDot = dot
                bestIndex = i
            }
        }

        // Adaptive threshold: fewer nodes = more generous targeting
        // 5 nodes → 0.7 (wider cone), 26 nodes → 0.85 (standard), 50+ nodes → 0.92 (precise)
        val threshold = when {
            nodes.size <= 5 -> 0.7f
            nodes.size <= 10 -> 0.78f
            nodes.size <= 26 -> 0.85f
            else -> 0.92f
        }
        return if (bestDot > threshold) bestIndex else -1
    }

    private fun renderNodes(nodes: List<SphereNode>) {
        GLES30.glUseProgram(sdfTextShaderProgram)

        for (node in nodes) {
            // Calculate model matrix for this node
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, node.worldX, node.worldY, node.worldZ)

            // Scale based on focus/magnetism
            val scale = node.scale * (1f + node.magnetism * 0.3f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

            // Billboard: make the node always face the camera
            applyBillboard(modelMatrix, viewMatrix)

            // MVP matrix
            val mvpMatrix = FloatArray(16)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            // Set uniforms
            val mvpLocation = GLES30.glGetUniformLocation(sdfTextShaderProgram, "uMVPMatrix")
            GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)

            val colorLocation = GLES30.glGetUniformLocation(sdfTextShaderProgram, "uColor")
            GLES30.glUniform4f(colorLocation, node.r, node.g, node.b, node.alpha)

            val glowLocation = GLES30.glGetUniformLocation(sdfTextShaderProgram, "uGlowIntensity")
            GLES30.glUniform1f(glowLocation, node.glowIntensity)

            // Render the character using SDF font atlas
            sdfFontAtlas.renderChar(sdfTextShaderProgram, node.displayChar, nodeMesh)
        }
    }

    /**
     * Make a model matrix billboard — always face the camera.
     * Extracts the rotation from the view matrix and applies the inverse.
     */
    private fun applyBillboard(modelMatrix: FloatArray, viewMatrix: FloatArray) {
        // Preserve translation (indices 12, 13, 14)
        val tx = modelMatrix[12]
        val ty = modelMatrix[13]
        val tz = modelMatrix[14]
        val sx = modelMatrix[0] // Scale X (approximate)
        val sy = modelMatrix[5] // Scale Y (approximate)
        val sz = modelMatrix[10] // Scale Z (approximate)

        // Set rotation to inverse of view rotation (transpose)
        modelMatrix[0] = viewMatrix[0] * sx
        modelMatrix[1] = viewMatrix[4] * sx
        modelMatrix[2] = viewMatrix[8] * sx
        modelMatrix[4] = viewMatrix[1] * sy
        modelMatrix[5] = viewMatrix[5] * sy
        modelMatrix[6] = viewMatrix[9] * sy
        modelMatrix[8] = viewMatrix[2] * sz
        modelMatrix[9] = viewMatrix[6] * sz
        modelMatrix[10] = viewMatrix[10] * sz

        // Restore translation
        modelMatrix[12] = tx
        modelMatrix[13] = ty
        modelMatrix[14] = tz
    }

    fun onDestroy() {
        if (sdfTextShaderProgram != 0) GLES30.glDeleteProgram(sdfTextShaderProgram)
    }

    companion object {
        // --- VERTEX SHADER: SDF Text ---
        const val SDF_VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTexCoord;

            uniform mat4 uMVPMatrix;

            out vec2 vTexCoord;

            void main() {
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        // --- FRAGMENT SHADER: SDF Text with glow ---
        const val SDF_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;

            in vec2 vTexCoord;

            uniform sampler2D uSDFTexture;
            uniform vec4 uColor;
            uniform float uGlowIntensity;

            out vec4 fragColor;

            void main() {
                float dist = texture(uSDFTexture, vTexCoord).r;

                // SDF edge thresholds
                float edgeWidth = 0.1;
                float edge = 0.5;

                // Crisp text edge
                float alpha = smoothstep(edge - edgeWidth, edge + edgeWidth, dist);

                // Glow halo: softer, wider threshold
                float glowEdge = 0.3;
                float glowWidth = 0.25;
                float glowAlpha = smoothstep(glowEdge - glowWidth, glowEdge, dist) * uGlowIntensity;

                // Combine: solid text + glow halo
                vec3 textColor = uColor.rgb;
                vec3 glowColor = uColor.rgb * 1.5; // Brighter glow, will be clamped

                vec3 finalColor = mix(glowColor, textColor, alpha);
                float finalAlpha = max(alpha, glowAlpha) * uColor.a;

                // Discard fully transparent fragments for performance
                if (finalAlpha < 0.01) discard;

                fragColor = vec4(finalColor, finalAlpha);
            }
        """
    }
}
