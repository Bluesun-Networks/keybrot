# PROMPT 1: THE CHASSIS (The OpenGL IME Foundation)

**Objective:** Build a high-performance, transparent Android Input Method Service (IME) utilizing an OpenGL ES 3.0 render loop.

**Context:**
We are building "Z-Type," a single-finger "Infinite Hallway" keyboard powered by OpenGL ES 3.0 for a "Flashy/Game-Engine" feel. You are building the base container today.

**Key Requirements:**
1. **IME Service:** Inherit from `InputMethodService`. 
2. **Transparency:** 
   - The IME window must be TRULY transparent to show the app underneath.
   - Use `FLAG_LAYOUT_NO_LIMITS` and `FLAG_NOT_FOCUSABLE`. 
   - Set `windowIsTranslucent` in `themes.xml`.
   - **CRITICAL NOTE:** Modern Android caches IMEs aggressively. You MUST increment `versionCode` and `versionName` in `build.gradle` and use "Force Stop" on the app during development to see UI changes.
3. **GLSurfaceView:** 
   - Implement a custom `GLSurfaceView` inside the `onCreateInputView`.
   - Set up a standard "Game Loop" renderer (60fps).
   - Background must be clear (0,0,0,0).
4. **Coordinate System:**
   - Establish a 3D coordinate system where Z-axis is "Depth."
   - Position the camera looking "down the hallway."

**Aesthetic Goal (Base):**
- A deep black/transparent void.
- A "Horizon" line or subtle grid to give a sense of orientation.

**Deliverable:** A functional Android Keyboard app that, when enabled, shows a transparent OpenGL-rendered void over the screen with a basic "FPS Counter" spinning in 3D to prove the loop is active.
