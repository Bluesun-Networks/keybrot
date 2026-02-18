package com.zooptype.ztype.engine

import android.opengl.GLES30
import android.util.Log

/**
 * Utility for compiling and linking OpenGL ES 3.0 shader programs.
 */
object ShaderCompiler {

    private const val TAG = "ShaderCompiler"

    /**
     * Compile a vertex + fragment shader into a linked program.
     */
    fun compileProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Failed to compile shaders")
            return 0
        }

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        // Check link status
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetProgramInfoLog(program)
            Log.e(TAG, "Program link failed: $log")
            GLES30.glDeleteProgram(program)
            return 0
        }

        // Clean up individual shaders (they're linked into the program now)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            val typeName = if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
            Log.e(TAG, "$typeName shader compile failed: $log")
            GLES30.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * Check for OpenGL errors (useful for debugging).
     */
    fun checkGLError(operation: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "$operation: GL Error 0x${Integer.toHexString(error)}")
        }
    }
}
