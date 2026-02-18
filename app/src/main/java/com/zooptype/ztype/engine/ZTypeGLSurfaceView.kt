package com.zooptype.ztype.engine

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.zooptype.ztype.gesture.GestureProcessor

/**
 * Custom GLSurfaceView for the Z-Type IME.
 *
 * Handles:
 * - Transparent EGL configuration (RGBA8888 with alpha)
 * - 60fps continuous render mode
 * - Touch event delegation to GestureProcessor
 */
class ZTypeGLSurfaceView(
    context: Context,
    private val renderer: ZTypeRenderer,
    private val gestureProcessor: GestureProcessor
) : GLSurfaceView(context) {

    init {
        // Request OpenGL ES 3.0
        setEGLContextClientVersion(3)

        // CRITICAL: Configure for transparency
        // Use RGBA_8888 with 8 alpha bits for true transparency
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)

        // Set the renderer
        setRenderer(renderer)

        // Continuous rendering for 60fps game loop
        renderMode = RENDERMODE_CONTINUOUSLY

        // Make background transparent
        setZOrderOnTop(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Delegate all touch events to the gesture processor
        gestureProcessor.onTouchEvent(event)
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer.onDestroy()
    }
}
