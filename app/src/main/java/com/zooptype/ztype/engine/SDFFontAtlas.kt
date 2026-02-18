package com.zooptype.ztype.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * Signed Distance Field Font Atlas.
 *
 * Generates an SDF texture atlas at runtime from a system font,
 * then provides UV coordinates for each glyph. SDF allows:
 * - Crisp text at any zoom level (perfect for 3D depth)
 * - Native glow/outline effects in the fragment shader
 * - Single texture for all glyphs (minimal draw calls)
 */
class SDFFontAtlas(context: Context) {

    // Atlas texture ID
    var textureId: Int = 0
        private set

    // Atlas dimensions
    private val atlasWidth = 512
    private val atlasHeight = 512
    private val cellSize = 48 // Each glyph cell size in pixels
    private val padding = 6  // SDF spread padding
    private val cols = atlasWidth / cellSize
    private val rows = atlasHeight / cellSize

    // Glyph UV mapping: char -> (u0, v0, u1, v1)
    private val glyphUVs = HashMap<Char, FloatArray>()

    // Characters to include in the atlas
    private val charset: String

    init {
        // Full charset: A-Z, a-z, 0-9, punctuation, special
        charset = buildString {
            // Uppercase
            for (c in 'A'..'Z') append(c)
            // Lowercase
            for (c in 'a'..'z') append(c)
            // Digits
            for (c in '0'..'9') append(c)
            // Punctuation & special
            append(".,;:!?@#\$%^&*()-_+=[]{}|\\/<>\"'`~ ")
        }

        generateAtlas(context)
    }

    /**
     * Generate the SDF atlas from a high-res rendered bitmap.
     *
     * Strategy:
     * 1. Render each glyph at high resolution to a bitmap
     * 2. Compute the SDF (distance to nearest edge) for each pixel
     * 3. Pack all SDF glyphs into a single atlas texture
     */
    private fun generateAtlas(context: Context) {
        val bitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textSize = (cellSize - padding * 2).toFloat()
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Render each character into the atlas grid
        charset.forEachIndexed { index, char ->
            val col = index % cols
            val row = index / cols

            if (row < rows) {
                val x = col * cellSize + cellSize / 2f
                val y = row * cellSize + cellSize * 0.7f // Baseline offset

                canvas.drawText(char.toString(), x, y, paint)

                // Store UV coordinates (normalized 0..1)
                val u0 = col.toFloat() * cellSize / atlasWidth
                val v0 = row.toFloat() * cellSize / atlasHeight
                val u1 = (col + 1f) * cellSize / atlasWidth
                val v1 = (row + 1f) * cellSize / atlasHeight

                glyphUVs[char] = floatArrayOf(u0, v0, u1, v1)
            }
        }

        // Now compute SDF from the rendered bitmap
        val sdfBitmap = computeSDF(bitmap)

        // Upload to OpenGL texture
        textureId = uploadTexture(sdfBitmap)

        bitmap.recycle()
        sdfBitmap.recycle()
    }

    /**
     * Compute a Signed Distance Field from a binary (anti-aliased) glyph bitmap.
     * Uses a brute-force approach suitable for the atlas size.
     * For production, consider the EDT (Euclidean Distance Transform) algorithm.
     */
    private fun computeSDF(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val spread = padding.toFloat()

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val sdfBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sdfPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val isInside = (pixels[y * w + x] and 0xFF) > 127

                // Find minimum distance to edge
                var minDist = spread

                val searchRadius = spread.toInt()
                for (sy in maxOf(0, y - searchRadius)..minOf(h - 1, y + searchRadius)) {
                    for (sx in maxOf(0, x - searchRadius)..minOf(w - 1, x + searchRadius)) {
                        val otherInside = (pixels[sy * w + sx] and 0xFF) > 127
                        if (isInside != otherInside) {
                            val dx = (x - sx).toFloat()
                            val dy = (y - sy).toFloat()
                            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (dist < minDist) minDist = dist
                        }
                    }
                }

                // Normalize: inside = 0.5..1.0, outside = 0.0..0.5
                val normalizedDist = if (isInside) {
                    0.5f + (minDist / spread) * 0.5f
                } else {
                    0.5f - (minDist / spread) * 0.5f
                }

                val byteVal = (normalizedDist.coerceIn(0f, 1f) * 255).toInt()
                sdfPixels[y * w + x] = Color.argb(255, byteVal, byteVal, byteVal)
            }
        }

        sdfBitmap.setPixels(sdfPixels, 0, w, 0, 0, w, h)
        return sdfBitmap
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)

        return texIds[0]
    }

    /**
     * Get UV coordinates for a character.
     * Returns (u0, v0, u1, v1) or null if not in atlas.
     */
    fun getGlyphUVs(char: Char): FloatArray? {
        return glyphUVs[char] ?: glyphUVs[char.uppercaseChar()]
    }

    /**
     * Render a single character using the SDF atlas.
     * Binds the atlas texture and sets UV uniforms.
     */
    fun renderChar(shaderProgram: Int, char: Char, mesh: NodeMesh) {
        val uvs = getGlyphUVs(char) ?: return

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

        val texLoc = GLES30.glGetUniformLocation(shaderProgram, "uSDFTexture")
        GLES30.glUniform1i(texLoc, 0)

        // Update mesh UVs for this specific glyph
        mesh.updateUVs(uvs[0], uvs[1], uvs[2], uvs[3])
        mesh.draw()
    }
}
