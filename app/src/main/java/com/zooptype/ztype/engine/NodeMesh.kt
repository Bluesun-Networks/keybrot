package com.zooptype.ztype.engine

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * A reusable quad mesh for rendering nodes (characters/icons) as billboards.
 *
 * Each node is a textured quad that faces the camera. The UV coordinates
 * are updated per-glyph to select the correct region of the SDF font atlas.
 */
class NodeMesh {

    private val vaoId: Int
    private val vboId: Int
    private val eboId: Int
    private val uvVboId: Int

    // Quad vertices: position (x, y, z)
    // Centered at origin, unit size
    private val positions = floatArrayOf(
        -0.5f,  0.5f, 0f,  // top-left
         0.5f,  0.5f, 0f,  // top-right
         0.5f, -0.5f, 0f,  // bottom-right
        -0.5f, -0.5f, 0f   // bottom-left
    )

    // Default UVs (will be updated per-glyph)
    private val defaultUVs = floatArrayOf(
        0f, 0f,   // top-left
        1f, 0f,   // top-right
        1f, 1f,   // bottom-right
        0f, 1f    // bottom-left
    )

    // Triangle indices for the quad
    private val indices = shortArrayOf(
        0, 1, 2,
        0, 2, 3
    )

    private val uvBuffer: FloatBuffer

    init {
        // Create VAO
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        // Create position VBO
        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]

        val posBuffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(positions)
        posBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            positions.size * 4,
            posBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(0)

        // Create UV VBO (dynamic — updated per glyph)
        val uvVbos = IntArray(1)
        GLES30.glGenBuffers(1, uvVbos, 0)
        uvVboId = uvVbos[0]

        uvBuffer = ByteBuffer.allocateDirect(defaultUVs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(defaultUVs)
        uvBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uvVboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            defaultUVs.size * 4,
            uvBuffer,
            GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glEnableVertexAttribArray(1)

        // Create EBO (index buffer)
        val ebos = IntArray(1)
        GLES30.glGenBuffers(1, ebos, 0)
        eboId = ebos[0]

        val idxBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
        idxBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * 2,
            idxBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Unbind VAO
        GLES30.glBindVertexArray(0)
    }

    /**
     * Update the UV coordinates for a specific glyph from the SDF atlas.
     */
    fun updateUVs(u0: Float, v0: Float, u1: Float, v1: Float) {
        val uvs = floatArrayOf(
            u0, v0,   // top-left
            u1, v0,   // top-right
            u1, v1,   // bottom-right
            u0, v1    // bottom-left
        )

        uvBuffer.position(0)
        uvBuffer.put(uvs)
        uvBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uvVboId)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, uvs.size * 4, uvBuffer)
    }

    /**
     * Draw the quad.
     */
    fun draw() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }
}
