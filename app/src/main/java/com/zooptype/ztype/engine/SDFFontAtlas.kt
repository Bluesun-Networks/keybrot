package com.zooptype.ztype.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max

/**
 * Signed Distance Field Font Atlas.
 *
 * Generates an SDF texture atlas at startup using the Dead Reckoning
 * distance transform algorithm (O(n) per row/column, vs O(n*r^2) brute force).
 *
 * SDF enables:
 * - Crisp text at any zoom level (perfect for 3D depth)
 * - Native glow/outline effects in the fragment shader
 * - Single texture for all glyphs (minimal draw calls)
 *
 * Performance: Atlas generation takes ~10-30ms on modern devices
 * (vs 500ms+ with brute-force), well within the surface creation budget.
 */
class SDFFontAtlas(context: Context) {

    var textureId: Int = 0
        private set

    // Atlas dimensions — 1024x512 for sharper glyphs
    private val atlasWidth = 1024
    private val atlasHeight = 512
    private val cellSize = 64
    private val padding = 8  // SDF spread radius
    private val cols = atlasWidth / cellSize
    private val rows = atlasHeight / cellSize

    // Glyph UV mapping: char -> (u0, v0, u1, v1)
    private val glyphUVs = HashMap<Char, FloatArray>()

    private val charset: String

    init {
        charset = buildString {
            for (c in 'A'..'Z') append(c)
            for (c in 'a'..'z') append(c)
            for (c in '0'..'9') append(c)
            append(".,;:!?@#\$%^&*()-_+=[]{}|\\/<>\"'`~ ")
            append("·") // separator for composing overlay
        }

        val startTime = System.currentTimeMillis()
        generateAtlas(context)
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "SDF atlas generated in ${elapsed}ms (${charset.length} glyphs, ${atlasWidth}x${atlasHeight})")
    }

    private fun generateAtlas(context: Context) {
        // Step 1: Render glyphs at high resolution with anti-aliasing
        val hiresBitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(hiresBitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = (cellSize - padding * 2).toFloat()
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }

        charset.forEachIndexed { index, char ->
            val col = index % cols
            val row = index / cols
            if (row >= rows) return@forEachIndexed

            val x = col * cellSize + cellSize / 2f
            val y = row * cellSize + cellSize * 0.72f

            canvas.drawText(char.toString(), x, y, paint)

            val u0 = col.toFloat() * cellSize / atlasWidth
            val v0 = row.toFloat() * cellSize / atlasHeight
            val u1 = (col + 1f) * cellSize / atlasWidth
            val v1 = (row + 1f) * cellSize / atlasHeight

            glyphUVs[char] = floatArrayOf(u0, v0, u1, v1)
        }

        // Step 2: Compute SDF using fast Dead Reckoning algorithm
        val sdfBitmap = computeSDFFast(hiresBitmap)

        // Step 3: Upload to GPU
        textureId = uploadTexture(sdfBitmap)

        hiresBitmap.recycle()
        sdfBitmap.recycle()
    }

    /**
     * Fast SDF computation using the Dead Reckoning (8-connected) distance transform.
     *
     * Two-pass algorithm:
     * Pass 1: Forward scan (top-left to bottom-right)
     * Pass 2: Backward scan (bottom-right to top-left)
     *
     * Each pass propagates distance from the nearest edge pixel.
     * O(2 * w * h) — linear in image size, ~100x faster than brute-force.
     */
    private fun computeSDFFast(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val spread = padding.toFloat()

        // Extract alpha channel as binary inside/outside
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val inside = BooleanArray(w * h) { (pixels[it] ushr 24) > 127 }

        // Distance arrays for inside and outside
        val distOutside = FloatArray(w * h) { Float.MAX_VALUE }
        val distInside = FloatArray(w * h) { Float.MAX_VALUE }

        // Initialize: pixels on the boundary get distance 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val isIn = inside[idx]

                // Check if this pixel is on the edge (neighbor has different state)
                var isEdge = false
                if (x > 0 && inside[idx - 1] != isIn) isEdge = true
                if (x < w - 1 && inside[idx + 1] != isIn) isEdge = true
                if (y > 0 && inside[idx - w] != isIn) isEdge = true
                if (y < h - 1 && inside[idx + w] != isIn) isEdge = true

                if (isEdge) {
                    if (isIn) distInside[idx] = 0f
                    else distOutside[idx] = 0f
                }
            }
        }

        // Forward pass (top-left to bottom-right)
        deadReckoningForward(distOutside, w, h)
        deadReckoningForward(distInside, w, h)

        // Backward pass (bottom-right to top-left)
        deadReckoningBackward(distOutside, w, h)
        deadReckoningBackward(distInside, w, h)

        // Combine into SDF: inside = positive, outside = negative
        val sdfBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sdfPixels = IntArray(w * h)

        for (i in 0 until w * h) {
            val dist = if (inside[i]) {
                sqrt(distInside[i])
            } else {
                -sqrt(distOutside[i])
            }

            // Normalize to 0..1 range (0.5 = edge)
            val normalized = (dist / spread * 0.5f + 0.5f).coerceIn(0f, 1f)
            val byteVal = (normalized * 255).toInt()
            sdfPixels[i] = Color.argb(255, byteVal, byteVal, byteVal)
        }

        sdfBitmap.setPixels(sdfPixels, 0, w, 0, 0, w, h)
        return sdfBitmap
    }

    /**
     * Forward pass of Dead Reckoning: propagate distances from top-left to bottom-right.
     */
    private fun deadReckoningForward(dist: FloatArray, w: Int, h: Int) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val current = dist[idx]

                // Check 4 forward neighbors and propagate minimum
                if (x > 0) {
                    val d = dist[idx - 1] + 1f
                    if (d < current) dist[idx] = d
                }
                if (y > 0) {
                    val d = dist[idx - w] + 1f
                    if (d < dist[idx]) dist[idx] = d
                }
                if (x > 0 && y > 0) {
                    val d = dist[idx - w - 1] + 1.414f
                    if (d < dist[idx]) dist[idx] = d
                }
                if (x < w - 1 && y > 0) {
                    val d = dist[idx - w + 1] + 1.414f
                    if (d < dist[idx]) dist[idx] = d
                }
            }
        }
    }

    /**
     * Backward pass: propagate distances from bottom-right to top-left.
     */
    private fun deadReckoningBackward(dist: FloatArray, w: Int, h: Int) {
        for (y in h - 1 downTo 0) {
            for (x in w - 1 downTo 0) {
                val idx = y * w + x

                if (x < w - 1) {
                    val d = dist[idx + 1] + 1f
                    if (d < dist[idx]) dist[idx] = d
                }
                if (y < h - 1) {
                    val d = dist[idx + w] + 1f
                    if (d < dist[idx]) dist[idx] = d
                }
                if (x < w - 1 && y < h - 1) {
                    val d = dist[idx + w + 1] + 1.414f
                    if (d < dist[idx]) dist[idx] = d
                }
                if (x > 0 && y < h - 1) {
                    val d = dist[idx + w - 1] + 1.414f
                    if (d < dist[idx]) dist[idx] = d
                }
            }
        }
    }

    private fun uploadTexture(bitmap: Bitmap): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])

        // Linear filtering for smooth SDF interpolation
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)

        return texIds[0]
    }

    fun getGlyphUVs(char: Char): FloatArray? {
        return glyphUVs[char] ?: glyphUVs[char.uppercaseChar()]
    }

    fun renderChar(shaderProgram: Int, char: Char, mesh: NodeMesh) {
        val uvs = getGlyphUVs(char) ?: return

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

        val texLoc = GLES30.glGetUniformLocation(shaderProgram, "uSDFTexture")
        GLES30.glUniform1i(texLoc, 0)

        mesh.updateUVs(uvs[0], uvs[1], uvs[2], uvs[3])
        mesh.draw()
    }

    companion object {
        private const val TAG = "SDFFontAtlas"
    }
}
