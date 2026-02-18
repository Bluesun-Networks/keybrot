# Keybrot — The Mandelbrot Keyboard

**A 3D flight simulator for ideas.** Keybrot is an Android keyboard (IME) that reimagines text input as a high-speed dive through an infinite fractal sphere of letters, words, and concepts.

Instead of tapping flat keys, you **steer** through a transparent 3D sphere of nodes using single-finger gestures. Letters rush toward you. Words form as you dive deeper. Concepts branch into emoji galaxies. It feels like flying.

## How It Works

1. **The Sphere** — When you open the keyboard, you see a transparent sphere with letters (A-Z) distributed equidistantly on its surface.

2. **Steer** — Drag your finger to rotate the camera and aim at a letter. The closer your aim, the more the letter glows and pulls toward you (magnetism).

3. **Dive** — Linger on a letter and it zooms toward you. When it passes the selection threshold, it **explodes** into its children (Mandelbrot-style) — the next predicted letters form a new sphere.

4. **Type** — Keep diving through letters: `H → e → l → l → o`. Words auto-commit when you reach an end-of-word node. Or **swipe up** to accept the top prediction early.

5. **Reset** — **Swipe down** to backspace / clear the current word.

6. **Concepts** — When you complete certain words (like "food"), the sphere can branch into emoji concept suites: 🍔🍕🍎🌮🍣

## Architecture

```
┌─────────────────────────────────────┐
│           ZTypeIMEService           │
│  (InputMethodService + text commit) │
├──────────┬──────────┬───────────────┤
│ Gesture  │ Physics  │    Trie       │
│Processor │ (Dive)   │ (HybridTrie)  │
├──────────┴──────────┴───────────────┤
│          ZTypeRenderer              │
│  (OpenGL ES 3.0 @ 60fps)           │
├─────────────────────────────────────┤
│  SDF Font Atlas │ Node Layout │ Theme│
│  (glow shaders) │ (Fibonacci) │Engine│
├─────────────────────────────────────┤
│  Haptics │ Persistence │ Debug HUD  │
└─────────────────────────────────────┘
```

## Tech Stack

- **Language:** Kotlin
- **Graphics:** OpenGL ES 3.0 with custom GLSL shaders
- **Text:** Signed Distance Field (SDF) font rendering
- **Input:** Velocity-based camera steering + linger-to-select
- **Layout:** Fibonacci sphere equidistant distribution
- **Prediction:** Hybrid Trie (alphabet + concept nodes + adaptive frequency)
- **Haptics:** VibrationEffect contextual feedback
- **Themes:** Cyber-Luminescent, Glassmorphic, Solarized

## Building

```bash
# Clone
git clone https://github.com/Bluesun-Networks/keybrot.git
cd keybrot

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup on Device

1. **Settings → System → Languages & Input → On-screen keyboard**
2. Enable **"Keybrot Keyboard"**
3. Open any text field, switch to Keybrot via keyboard selector

## Development Notes

- **IME Caching:** Android aggressively caches IMEs. After changes, increment `versionCode`/`versionName` in `app/build.gradle.kts` AND force-stop the app.
- **Debug Overlay:** Always enabled in debug builds — shows FPS, velocity, zoom, trie state.
- **Physics Tuning:** All constants in `DivePhysics.kt` companion object are adjustable.

## Project Structure

```
app/src/main/java/com/zooptype/ztype/
├── engine/          # OpenGL renderer, IME service, shaders, SDF atlas
├── trie/            # HybridTrie, TrieNode, TrieNavigator
├── physics/         # DivePhysics (camera, velocity, magnetism, zoom)
├── gesture/         # GestureProcessor (swipe up/down, touch delegation)
├── node/            # SphereNode, NodeLayoutEngine (Fibonacci sphere)
├── theme/           # ThemeEngine (Cyber, Glass, Solarized)
├── haptics/         # HapticEngine (contextual vibrations)
├── debug/           # DebugOverlay (FPS, vectors, trie state)
├── persistence/     # UserDataRepository, SyncInterface
└── settings/        # ZTypeSettingsActivity
```

## License

TBD
