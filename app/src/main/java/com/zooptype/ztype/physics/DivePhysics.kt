package com.zooptype.ztype.physics

import android.opengl.Matrix
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DivePhysics: The "flight simulator" physics engine for Keybrot.
 *
 * Manages:
 * - Camera position and orientation (where the user is "looking" in the sphere)
 * - Velocity-based steering (finger movement → camera direction)
 * - Zoom/dive progress (how far the user has dived toward a node)
 * - Exponential smoothing for fluid, jitter-free motion
 * - Magnetism/linger detection for node selection
 *
 * The physics model:
 * - The camera sits at the center of a sphere, looking outward
 * - Finger velocity rotates the camera's look direction
 * - Sustained focus on a node increases "zoom" toward it
 * - When zoom reaches threshold, the node is "selected" (Mandelbrot explosion)
 */
class DivePhysics {

    // --- Camera State ---

    /** Camera rotation angles (spherical coordinates) */
    var cameraTheta = 0f  // Horizontal rotation (radians)
        private set
    var cameraPhi = Math.PI.toFloat() / 2f  // Vertical rotation (radians, π/2 = looking straight)
        private set

    /** Zoom progress toward the focused node (0.0 = no zoom, 1.0 = selected) */
    var zoomProgress = 0f
        private set

    /** Current sphere radius (shrinks as we zoom in) */
    private var sphereRadius = 2.0f
    private val baseSphereRadius = 2.0f

    // --- Velocity State ---

    /** Smoothed velocity vector from touch input */
    var velocityX = 0f
        private set
    var velocityY = 0f
        private set

    /** Raw touch velocity (before smoothing) */
    private var rawVelocityX = 0f
    private var rawVelocityY = 0f

    /** Touch state */
    var isTouching = false
        private set
    private var touchX = 0f
    private var touchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // --- Focus/Magnetism State ---

    /** Index of the currently focused node (-1 = none) */
    var focusedNodeIndex = -1
        private set

    /** How long the focus has been on the current node (seconds) */
    var lingerTime = 0f
        private set

    /** Magnetism strength (0.0 = none, 1.0 = fully locked) */
    var magnetism = 0f
        private set

    // --- Tuning Constants ---

    companion object {
        /** Sensitivity: how fast the camera rotates per pixel of touch movement */
        const val STEERING_SENSITIVITY = 0.003f

        /** Exponential smoothing factor for velocity (0 = no smoothing, 1 = frozen) */
        const val VELOCITY_SMOOTHING = 0.85f

        /** Zoom speed when focused on a node */
        const val ZOOM_SPEED = 1.5f

        /** Zoom decay when not focused */
        const val ZOOM_DECAY = 3.0f

        /** Linger threshold to begin magnetism (seconds) */
        const val LINGER_THRESHOLD = 0.035f

        /** Magnetism buildup rate */
        const val MAGNETISM_RATE = 5.0f

        /** Magnetism decay rate when not lingering */
        const val MAGNETISM_DECAY = 8.0f

        /** Zoom threshold for node selection (triggers Mandelbrot explosion) */
        const val SELECTION_THRESHOLD = 0.95f

        /** Maximum angular velocity (radians/second) */
        const val MAX_ANGULAR_VELOCITY = 3.0f

        /** Friction when touch is released */
        const val FRICTION = 4.0f
    }

    /**
     * Update physics state for this frame.
     */
    fun update(deltaTime: Float) {
        if (deltaTime <= 0f || deltaTime > 0.1f) return // Guard against bad delta

        // Smooth velocity
        velocityX = velocityX * VELOCITY_SMOOTHING + rawVelocityX * (1f - VELOCITY_SMOOTHING)
        velocityY = velocityY * VELOCITY_SMOOTHING + rawVelocityY * (1f - VELOCITY_SMOOTHING)

        // Apply friction when not touching
        if (!isTouching) {
            val friction = FRICTION * deltaTime
            velocityX *= maxOf(0f, 1f - friction)
            velocityY *= maxOf(0f, 1f - friction)
        }

        // Clamp velocity
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (speed > MAX_ANGULAR_VELOCITY) {
            val scale = MAX_ANGULAR_VELOCITY / speed
            velocityX *= scale
            velocityY *= scale
        }

        // Update camera rotation from velocity
        cameraTheta += velocityX * deltaTime
        cameraPhi += velocityY * deltaTime

        // Clamp phi to avoid flipping (keep in 0.1..π-0.1 range)
        cameraPhi = cameraPhi.coerceIn(0.1f, Math.PI.toFloat() - 0.1f)

        // Update focus/magnetism
        if (focusedNodeIndex >= 0 && isTouching) {
            lingerTime += deltaTime

            if (lingerTime >= LINGER_THRESHOLD) {
                // Build magnetism
                magnetism = (magnetism + MAGNETISM_RATE * deltaTime).coerceAtMost(1f)

                // Zoom in toward the focused node
                zoomProgress = (zoomProgress + ZOOM_SPEED * magnetism * deltaTime).coerceAtMost(1f)
            }
        } else {
            // Decay magnetism and zoom
            magnetism = (magnetism - MAGNETISM_DECAY * deltaTime).coerceAtLeast(0f)
            zoomProgress = (zoomProgress - ZOOM_DECAY * deltaTime).coerceAtLeast(0f)
            lingerTime = 0f
        }

        // Update sphere radius based on zoom
        sphereRadius = baseSphereRadius * (1f - zoomProgress * 0.7f)

        // Clear raw velocity each frame (it's set from touch events)
        rawVelocityX = 0f
        rawVelocityY = 0f
    }

    /**
     * Apply the current camera transform to a view matrix.
     */
    fun applyCameraTransform(viewMatrix: FloatArray) {
        // Camera at origin, looking outward based on theta/phi
        val lookX = sin(cameraPhi) * cos(cameraTheta)
        val lookY = cos(cameraPhi)
        val lookZ = sin(cameraPhi) * sin(cameraTheta)

        // Zoom: move camera forward toward the look direction
        val zoomOffset = zoomProgress * baseSphereRadius * 0.8f
        val eyeX = lookX * zoomOffset
        val eyeY = lookY * zoomOffset
        val eyeZ = lookZ * zoomOffset

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,                              // eye
            lookX * baseSphereRadius, lookY * baseSphereRadius, lookZ * baseSphereRadius, // center (look at sphere surface)
            0f, 1f, 0f                                       // up
        )
    }

    /**
     * Get the current camera look direction (normalized).
     */
    fun getLookDirection(): FloatArray {
        val x = sin(cameraPhi) * cos(cameraTheta)
        val y = cos(cameraPhi)
        val z = sin(cameraPhi) * sin(cameraTheta)
        return floatArrayOf(x, y, z)
    }

    // --- Touch Input ---

    fun onTouchDown(x: Float, y: Float) {
        isTouching = true
        touchX = x
        touchY = y
        lastTouchX = x
        lastTouchY = y
    }

    fun onTouchMove(x: Float, y: Float) {
        if (!isTouching) return

        val dx = x - lastTouchX
        val dy = y - lastTouchY

        // Convert pixel delta to angular velocity
        rawVelocityX = dx * STEERING_SENSITIVITY
        rawVelocityY = -dy * STEERING_SENSITIVITY // Invert Y: drag down = look up

        lastTouchX = x
        lastTouchY = y
        touchX = x
        touchY = y
    }

    fun onTouchUp(x: Float, y: Float) {
        isTouching = false
    }

    /**
     * Set which node is currently focused (closest to camera look direction).
     */
    fun setFocusedNode(index: Int) {
        if (index != focusedNodeIndex) {
            focusedNodeIndex = index
            lingerTime = 0f
            magnetism = 0f
        }
    }

    /**
     * Check if the current focused node should be selected
     * (zoom has reached the selection threshold).
     */
    fun shouldSelect(): Boolean {
        return zoomProgress >= SELECTION_THRESHOLD && focusedNodeIndex >= 0
    }

    /**
     * Get the current effective sphere radius (shrinks during zoom).
     */
    fun getCurrentSphereRadius(): Float = sphereRadius

    /**
     * Get the current zoom progress (0..1).
     */
    fun getZoomProgress(): Float = zoomProgress

    /**
     * Get the current speed (magnitude of velocity).
     */
    fun getSpeed(): Float = sqrt(velocityX * velocityX + velocityY * velocityY)

    /**
     * Reset all physics state (new word / swipe-down).
     */
    fun reset() {
        zoomProgress = 0f
        magnetism = 0f
        lingerTime = 0f
        focusedNodeIndex = -1
        sphereRadius = baseSphereRadius
        // Keep camera rotation (don't disorient the user)
    }

    /**
     * Full reset (new input session).
     */
    fun fullReset() {
        reset()
        cameraTheta = 0f
        cameraPhi = Math.PI.toFloat() / 2f
        velocityX = 0f
        velocityY = 0f
        rawVelocityX = 0f
        rawVelocityY = 0f
    }
}
