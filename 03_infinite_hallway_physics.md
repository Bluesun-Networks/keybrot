# PROMPT 3: THE DYNAMICS (The Infinite Hallway Physics)

**Objective:** Implement the "Mandelbrot Zoom" interaction model where users "dive" through 3D nodes using single-finger velocity and lingering.

**Context:** 
The interface is not a flat keyboard. It is a 3D tunnel. Moving the finger doesn't just "move" a cursor; it "steers" the camera down a path.

**Key Requirements:**
1. **The Navigation Vector:**
   - Touch Down: Capture start coordinates.
   - Touch Move: Calculate the **Velocity Vector**. 
   - **Steering:** The angle of the finger's motion determines which node in the "Hallway" the camera focus shifts toward.
2. **The "Dive" Mechanic (Z-Axis):**
   - As a node (Letter/Icon) gets closer to the center of the screen based on the steering, its **Z-Depth** decreases (it rushes toward the user).
   - "Deep Dive": When a node passes a "Selection Threshold" (Z < 0), the Trie's children for that node are instantiated in the distance and begin rushing forward.
3. **The Linger-to-Select Logic:**
   - If the user keeps their finger over a node for >35ms (adjustable), "Magnetize" the node to the finger.
   - **Visual Feedback:** The node should glow/pulse when magnetized.
4. **The Mandelbrot Transition:**
   - When a node is selected, it should "explode" or "expand" into its children nodes. This transition must be seamless. The children nodes should emerge from the center of the parent node.

**Aesthetic Goal:**
High-velocity, fluid motion. It should feel like "Flight" through data. Use **Exponential Smoothing** on the camera to prevent jitter.

**Deliverable:** An OpenGL renderer integration where alphanumeric nodes move toward the camera based on touch steering, and "diving" into a letter updates the world with the next set of children node.
