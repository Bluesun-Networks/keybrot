package com.zooptype.ztype.node

import com.zooptype.ztype.trie.NodeType

/**
 * SphereNode: A visual node positioned on the 3D sphere.
 *
 * This is the renderable representation of a TrieNode.
 * It knows its 3D world position on the sphere surface,
 * its visual state (color, glow, scale, magnetism), and
 * its identity (what character/concept it represents).
 */
data class SphereNode(
    /** The character to display */
    val displayChar: Char,

    /** Full display string (for emoji/concepts) */
    val displayString: String,

    /** The trie key for this node (character string or concept label) */
    val trieKey: String,

    /** Node type */
    val type: NodeType,

    /** 3D world position on the sphere */
    var worldX: Float = 0f,
    var worldY: Float = 0f,
    var worldZ: Float = 0f,

    /** Angular position on sphere (for layout calculations) */
    var theta: Float = 0f,  // Horizontal angle
    var phi: Float = 0f,    // Vertical angle

    /** Visual scale (1.0 = normal) */
    var scale: Float = 1.0f,

    /** Color (RGBA) */
    var r: Float = 0f,
    var g: Float = 1f,
    var b: Float = 1f,
    var alpha: Float = 1f,

    /** Glow intensity (0.0 = none, 1.0 = full neon glow) */
    var glowIntensity: Float = 0.3f,

    /** Magnetism level (0.0 = none, 1.0 = fully attracted to camera) */
    var magnetism: Float = 0f,

    /** Whether this node is currently focused */
    var isFocused: Boolean = false,

    /** Animation progress for the Mandelbrot spawn transition */
    var spawnProgress: Float = 1.0f  // 0 = just spawned, 1 = fully materialized
)
