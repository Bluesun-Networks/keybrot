package com.zooptype.ztype.theme

import com.zooptype.ztype.node.SphereNode

/**
 * ThemeEngine: Configurable visual themes for Keybrot.
 *
 * Each theme defines:
 * - Node colors (alphabet, EOW, concept, focused)
 * - Glow parameters (intensity, spread, color)
 * - Background ambient color
 * - Motion blur intensity
 * - Particle trail colors
 *
 * Themes are applied to SphereNodes after layout, overriding
 * the default colors from NodeLayoutEngine.
 */
class ThemeEngine {

    var currentTheme: Theme = Theme.CYBER_LUMINESCENT
        set(value) {
            field = value
            activeColors = getThemeColors(value)
        }

    private var activeColors: ThemeColors = getThemeColors(Theme.CYBER_LUMINESCENT)

    fun getColors(): ThemeColors = activeColors

    /**
     * Apply theme colors to a list of sphere nodes.
     */
    fun applyTheme(nodes: List<SphereNode>) {
        for (node in nodes) {
            val color = when {
                node.isFocused -> activeColors.focused
                node.type == com.zooptype.ztype.trie.NodeType.CONCEPT -> activeColors.concept
                node.type == com.zooptype.ztype.trie.NodeType.END_OF_WORD -> activeColors.endOfWord
                else -> activeColors.alphabet
            }

            node.r = color.r
            node.g = color.g
            node.b = color.b
            node.glowIntensity = if (node.isFocused) {
                activeColors.focusedGlowIntensity
            } else {
                activeColors.baseGlowIntensity
            }
        }
    }

    companion object {
        fun getThemeColors(theme: Theme): ThemeColors {
            return when (theme) {
                Theme.CYBER_LUMINESCENT -> ThemeColors(
                    alphabet = RGBA(0f, 1f, 1f, 0.9f),       // Neon cyan
                    endOfWord = RGBA(0f, 1f, 0.4f, 1f),      // Neon green
                    concept = RGBA(1f, 0f, 1f, 1f),           // Neon magenta
                    focused = RGBA(1f, 1f, 1f, 1f),           // Bright white
                    background = RGBA(0.04f, 0.04f, 0.1f, 0f), // Deep void
                    glowColor = RGBA(0f, 1f, 1f, 0.6f),      // Cyan glow
                    baseGlowIntensity = 0.3f,
                    focusedGlowIntensity = 0.8f,
                    motionBlurIntensity = 0.5f,
                    particleColor = RGBA(0f, 1f, 1f, 0.4f)
                )

                Theme.GLASSMORPHIC -> ThemeColors(
                    alphabet = RGBA(0.93f, 0.93f, 1f, 0.8f),   // Soft white
                    endOfWord = RGBA(0.6f, 0.8f, 1f, 0.9f),    // Soft blue
                    concept = RGBA(0.8f, 0.6f, 1f, 0.9f),      // Soft purple
                    focused = RGBA(1f, 1f, 1f, 1f),             // Pure white
                    background = RGBA(0.1f, 0.1f, 0.15f, 0.3f), // Frosted
                    glowColor = RGBA(1f, 1f, 1f, 0.2f),        // Subtle white glow
                    baseGlowIntensity = 0.1f,
                    focusedGlowIntensity = 0.4f,
                    motionBlurIntensity = 0.3f,
                    particleColor = RGBA(1f, 1f, 1f, 0.2f)
                )

                Theme.SOLARIZED -> ThemeColors(
                    alphabet = RGBA(0.51f, 0.58f, 0.59f, 0.9f),  // base0
                    endOfWord = RGBA(0.71f, 0.54f, 0f, 1f),      // yellow
                    concept = RGBA(0.8f, 0.29f, 0.09f, 1f),      // orange
                    focused = RGBA(0.16f, 0.63f, 0.60f, 1f),     // cyan
                    background = RGBA(0f, 0.17f, 0.21f, 0f),     // base03
                    glowColor = RGBA(0.16f, 0.63f, 0.60f, 0.3f), // cyan glow
                    baseGlowIntensity = 0.1f,
                    focusedGlowIntensity = 0.3f,
                    motionBlurIntensity = 0.2f,
                    particleColor = RGBA(0.71f, 0.54f, 0f, 0.3f)
                )
            }
        }
    }
}

enum class Theme {
    CYBER_LUMINESCENT,
    GLASSMORPHIC,
    SOLARIZED
}

data class ThemeColors(
    val alphabet: RGBA,
    val endOfWord: RGBA,
    val concept: RGBA,
    val focused: RGBA,
    val background: RGBA,
    val glowColor: RGBA,
    val baseGlowIntensity: Float,
    val focusedGlowIntensity: Float,
    val motionBlurIntensity: Float,
    val particleColor: RGBA
)

data class RGBA(val r: Float, val g: Float, val b: Float, val a: Float)
