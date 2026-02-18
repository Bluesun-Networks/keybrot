package com.zooptype.ztype.engine

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.zooptype.ztype.debug.DebugOverlay
import com.zooptype.ztype.node.NodeLayoutEngine
import com.zooptype.ztype.node.SphereNode
import com.zooptype.ztype.physics.DivePhysics
import com.zooptype.ztype.trie.TrieNavigator
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * The main OpenGL ES 3.0 Renderer for Z-Type.
 *
 * Orchestrates the render loop:
 * 1. Clear to transparent
 * 2. Update physics (camera position, node positions)
 * 3. Render nodes on the sphere
 * 4. Render debug overlay (FPS, touch vectors, trie state)
 */
class ZTypeRenderer(
    private val context: Context,
    private val trieNavigator: TrieNavigator,
    private val divePhysics: DivePhysics,
    private val nodeLayoutEngine: NodeLayoutEngine,
    private val debugOverlay: DebugOverlay
) : GLSurfaceView.Renderer {

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    // Shader programs
    private var nodeShaderProgram: Int = 0
    private var sdfTextShaderProgram: Int = 0
    private var glowShaderProgram: Int = 0

    // SDF Font Atlas
    private lateinit var sdfFontAtlas: SDFFontAtlas

    // Node mesh (shared geometry for all nodes)
    private lateinit var nodeMesh: NodeMesh

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

        // Compile shaders
        nodeShaderProgram = ShaderCompiler.compileProgram(
            NODE_VERTEX_SHADER, NODE_FRAGMENT_SHADER
        )
        sdfTextShaderProgram = ShaderCompiler.compileProgram(
            SDF_VERTEX_SHADER, SDF_FRAGMENT_SHADER
        )

        // Initialize SDF font atlas
        sdfFontAtlas = SDFFontAtlas(context)

        // Initialize node mesh (a quad billboard for each node)
        nodeMesh = NodeMesh()

        // Initialize debug overlay
        debugOverlay.init(sdfFontAtlas, sdfTextShaderProgram)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height

        // Perspective projection — looking into the sphere
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 60f, ratio, 0.1f, 100f)

        // Initial camera: looking at origin from slightly back
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 3f,    // eye: slightly back from center
            0f, 0f, 0f,    // center: looking at origin
            0f, 1f, 0f     // up: Y-axis
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        // Calculate delta time
        val now = System.nanoTime()
        deltaTime = (now - lastFrameTime) / 1_000_000_000f
        lastFrameTime = now

        // FPS calculation
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

        // --- UPDATE PHASE ---

        // Update physics (camera position based on touch input)
        divePhysics.update(deltaTime)

        // Get current camera matrix from physics
        divePhysics.applyCameraTransform(viewMatrix)

        // Compute view-projection matrix
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Get the current nodes to display from the trie navigator + layout engine
        val currentNodes = trieNavigator.getCurrentNodes()
        val layoutNodes = nodeLayoutEngine.layoutOnSphere(
            currentNodes,
            divePhysics.getCurrentSphereRadius(),
            divePhysics.getZoomProgress()
        )

        // --- RENDER PHASE ---

        // Render each node
        renderNodes(layoutNodes)

        // Render debug overlay
        debugOverlay.render(
            vpMatrix = vpMatrix,
            fps = currentFps,
            deltaTime = deltaTime,
            divePhysics = divePhysics,
            trieNavigator = trieNavigator,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
    }

    private fun renderNodes(nodes: List<SphereNode>) {
        GLES30.glUseProgram(sdfTextShaderProgram)

        for (node in nodes) {
            // Calculate model matrix for this node on the sphere
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
     */
    private fun applyBillboard(modelMatrix: FloatArray, viewMatrix: FloatArray) {
        // Extract rotation from view matrix and apply inverse to model
        modelMatrix[0] = viewMatrix[0]; modelMatrix[1] = viewMatrix[4]; modelMatrix[2] = viewMatrix[8]
        modelMatrix[4] = viewMatrix[1]; modelMatrix[5] = viewMatrix[5]; modelMatrix[6] = viewMatrix[9]
        modelMatrix[8] = viewMatrix[2]; modelMatrix[9] = viewMatrix[6]; modelMatrix[10] = viewMatrix[10]
    }

    fun onDestroy() {
        if (nodeShaderProgram != 0) GLES30.glDeleteProgram(nodeShaderProgram)
        if (sdfTextShaderProgram != 0) GLES30.glDeleteProgram(sdfTextShaderProgram)
        if (glowShaderProgram != 0) GLES30.glDeleteProgram(glowShaderProgram)
    }

    companion object {
        // --- VERTEX SHADER: Node rendering ---
        const val NODE_VERTEX_SHADER = """
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

        // --- FRAGMENT SHADER: Node rendering ---
        const val NODE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;

            in vec2 vTexCoord;

            uniform vec4 uColor;
            uniform sampler2D uTexture;

            out vec4 fragColor;

            void main() {
                vec4 texColor = texture(uTexture, vTexCoord);
                fragColor = texColor * uColor;
            }
        """

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

                // SDF edge threshold
                float edgeWidth = 0.1;
                float edge = 0.5;

                // Smooth text edge
                float alpha = smoothstep(edge - edgeWidth, edge + edgeWidth, dist);

                // Glow effect: softer, wider threshold
                float glowEdge = 0.3;
                float glowWidth = 0.25;
                float glowAlpha = smoothstep(glowEdge - glowWidth, glowEdge, dist) * uGlowIntensity;

                // Combine: solid text + glow halo
                vec3 textColor = uColor.rgb;
                vec3 glowColor = uColor.rgb * 1.5; // Brighter glow

                vec3 finalColor = mix(glowColor, textColor, alpha);
                float finalAlpha = max(alpha, glowAlpha) * uColor.a;

                fragColor = vec4(finalColor, finalAlpha);
            }
        """
    }
}
