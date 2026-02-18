package com.zooptype.ztype.node

import com.zooptype.ztype.trie.NodeType
import com.zooptype.ztype.trie.TrieNode
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * NodeLayoutEngine: Distributes nodes equidistantly on a sphere surface.
 *
 * Uses the Fibonacci sphere algorithm for near-optimal equidistant point
 * distribution on a sphere. This ensures that whether there are 5 nodes
 * or 50, they're always evenly spaced.
 *
 * Also handles:
 * - Mandelbrot spawn animation (children explode from parent center)
 * - Zoom-based scaling (nodes grow as we approach, shrink as we zoom past)
 * - Focus attraction (focused node leans toward camera)
 */
class NodeLayoutEngine {

    /** Animation duration for Mandelbrot spawn (seconds) */
    var spawnDuration = 0.4f

    /** Golden ratio for Fibonacci sphere */
    private val goldenRatio = (1.0 + sqrt(5.0)) / 2.0
    private val goldenAngle = (2.0 * PI / goldenRatio / goldenRatio).toFloat()

    /** Spawn origin for Mandelbrot transition */
    private var spawnOriginX = 0f
    private var spawnOriginY = 0f
    private var spawnOriginZ = 0f
    private var spawnTimer = 0f
    private var isSpawning = false

    /**
     * Distribute TrieNodes on a sphere and return renderable SphereNodes.
     *
     * @param trieNodes The trie children to display
     * @param sphereRadius Current sphere radius (shrinks during zoom)
     * @param zoomProgress Current zoom level (0..1)
     * @return List of positioned SphereNodes ready for rendering
     */
    fun layoutOnSphere(
        trieNodes: List<TrieNode>,
        sphereRadius: Float,
        zoomProgress: Float
    ): List<SphereNode> {
        val n = trieNodes.size
        if (n == 0) return emptyList()

        val sphereNodes = ArrayList<SphereNode>(n)

        for (i in 0 until n) {
            val trieNode = trieNodes[i]

            // Fibonacci sphere point distribution
            val y = 1f - (i.toFloat() / (n - 1).coerceAtLeast(1)) * 2f // Range: 1 to -1
            val radiusAtY = sqrt(1f - y * y)
            val theta = goldenAngle * i

            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY

            // Scale to sphere radius
            val worldX = x * sphereRadius
            val worldY = y * sphereRadius
            val worldZ = z * sphereRadius

            // Create the visual node
            val node = SphereNode(
                displayChar = trieNode.displayChar(),
                displayString = trieNode.displayString(),
                trieKey = trieNode.char?.toString() ?: trieNode.conceptLabel ?: "",
                type = trieNode.type,
                worldX = worldX,
                worldY = worldY,
                worldZ = worldZ,
                theta = theta,
                phi = acos(y),
                scale = calculateNodeScale(trieNode, zoomProgress)
            )

            // Apply color based on node type
            applyNodeColor(node, trieNode)

            // Apply spawn animation if active
            if (isSpawning && spawnTimer < spawnDuration) {
                val t = (spawnTimer / spawnDuration).coerceIn(0f, 1f)
                val easeT = easeOutBack(t)

                node.worldX = lerp(spawnOriginX, worldX, easeT)
                node.worldY = lerp(spawnOriginY, worldY, easeT)
                node.worldZ = lerp(spawnOriginZ, worldZ, easeT)
                node.scale *= easeT
                node.alpha = easeT
                node.spawnProgress = easeT
            }

            sphereNodes.add(node)
        }

        return sphereNodes
    }

    /**
     * Trigger a Mandelbrot spawn transition.
     * Children will explode outward from the selected parent's position.
     */
    fun triggerMandelbrotSpawn(parentX: Float, parentY: Float, parentZ: Float) {
        spawnOriginX = parentX
        spawnOriginY = parentY
        spawnOriginZ = parentZ
        spawnTimer = 0f
        isSpawning = true
    }

    /**
     * Update spawn animation timer.
     */
    fun updateAnimation(deltaTime: Float) {
        if (isSpawning) {
            spawnTimer += deltaTime
            if (spawnTimer >= spawnDuration) {
                isSpawning = false
            }
        }
    }

    /**
     * Calculate node scale based on type and zoom progress.
     */
    private fun calculateNodeScale(node: TrieNode, zoomProgress: Float): Float {
        val baseScale = when (node.type) {
            NodeType.CONCEPT -> 1.2f  // Concepts slightly larger
            NodeType.END_OF_WORD -> 1.1f  // EOW slightly emphasized
            else -> 1.0f
        }

        // Nodes grow slightly as zoom increases (approaching them)
        return baseScale * (1f + zoomProgress * 0.2f)
    }

    /**
     * Apply color to a node based on its type.
     * Default: Cyber-Luminescent theme colors.
     * (ThemeEngine overrides these later)
     */
    private fun applyNodeColor(node: SphereNode, trieNode: TrieNode) {
        when (trieNode.type) {
            NodeType.ALPHABET -> {
                // Neon cyan
                node.r = 0f; node.g = 1f; node.b = 1f; node.alpha = 0.9f
                node.glowIntensity = 0.3f
            }
            NodeType.END_OF_WORD -> {
                // Neon green (word completion available)
                node.r = 0f; node.g = 1f; node.b = 0.4f; node.alpha = 1f
                node.glowIntensity = 0.5f
            }
            NodeType.CONCEPT -> {
                // Neon magenta
                node.r = 1f; node.g = 0f; node.b = 1f; node.alpha = 1f
                node.glowIntensity = 0.6f
            }
            NodeType.ROOT -> {
                node.r = 0.5f; node.g = 0.5f; node.b = 0.5f; node.alpha = 0.5f
                node.glowIntensity = 0.1f
            }
        }
    }

    /**
     * Update focus state: make the focused node "lean" toward the camera.
     */
    fun applyFocusAttraction(
        nodes: List<SphereNode>,
        focusedIndex: Int,
        magnetism: Float,
        cameraX: Float,
        cameraY: Float,
        cameraZ: Float
    ) {
        for ((i, node) in nodes.withIndex()) {
            if (i == focusedIndex) {
                node.isFocused = true
                node.magnetism = magnetism

                // Lean toward camera based on magnetism
                val leanFactor = magnetism * 0.3f
                node.worldX = lerp(node.worldX, cameraX * 0.5f, leanFactor)
                node.worldY = lerp(node.worldY, cameraY * 0.5f, leanFactor)
                node.worldZ = lerp(node.worldZ, cameraZ * 0.5f, leanFactor)

                // Increase glow when focused
                node.glowIntensity = 0.3f + magnetism * 0.7f

                // Scale up slightly
                node.scale *= (1f + magnetism * 0.3f)
            } else {
                node.isFocused = false
                node.magnetism = 0f
            }
        }
    }

    // --- Easing functions ---

    /** Ease-out-back: slight overshoot, then settle (for Mandelbrot spawn) */
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f)
    }

    /** Linear interpolation */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
