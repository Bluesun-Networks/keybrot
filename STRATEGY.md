# STRATEGY: Z-TYPE NEON

## The Vision
A "Super Cool," "Flashy," and "Insanely Powerful" single-finger input mechanism. It is an **Infinite Hallway** of thoughts where alphanumeric characters and conceptual pictograms are navigated via a Mandelbrot zoom.

## Key Design Decisions
1. **OpenGL ES 3.0 over 2D Canvas:** Standard Android Canvas is too slow for high-velocity 3D zoom effects. OpenGL provides the "game-engine" feel required.
2. **Hallway over Sphere:** A rotational sphere layout feels like a menu. The "Mandelbrot" vision requires forward-motion (depth) — an infinite hallway.
3. **Transparency as Primary Requirement:** OS-level transparency on Android is brittle and must be treated as a primary technical requirement from day one.

## The Roadmap

### Phase 1: The Engine ([01_foundation_engine.md](./01_foundation_engine.md))
Build a fresh Android project with a focus on an **OpenGL ES 3.0** powered `InputMethodService`. Solve the transparency and render-loop first.

### Phase 2: The Mind ([02_hybrid_brain.md](./02_hybrid_brain.md))
Implement the Hybrid Trie. This is the "Insanely Powerful" part. It doesn't just predict words; it bridges the gap between text and concept.

### Phase 3: The Physics ([03_infinite_hallway_physics.md](./03_infinite_hallway_physics.md))
Code the "Dive." This is the hardest part. The steering and speed must feel like "Flying" through a 3D tunnel of data.

### Phase 4: The Vibe ([04_neon_polish_theming.md](./04_neon_polish_theming.md))
The "Flashy" part. Add the shaders, neon glows, and haptics. Make it skinnable.

## Strategic Commands for Future-Me
- **Performance is Everything:** If it drops below 60fps, it loses the "Super Cool" factor.
- **Natural Pull:** Dragging "Down" should feel like pushing "Forward" into the depth.
- **Concept Overlap:** The transition from Letters -> Icons should be seamless. If I type "Beach," the hallway shouldn't just offer 'e', it should offer a 🏖️ icon.

---
