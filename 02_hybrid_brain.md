# PROMPT 2: THE BRAIN (The Hybrid Concept-Trie)

**Objective:** Implement a multi-layered Prefix Tree (Trie) that supports standard alphanumeric input AND conceptual icon branching.

**Context:** 
The user navigates Z-Type by "diving" into nodes. A node can be a Letter ('A') or a Concept ('Travel').

**Key Requirements:**
1. **The TrieNode Class:** 
   - `char`: The character it represents (optional).
   - `icon`: Reference to a vector/SVG asset (optional).
   - `type`: Either `ALPHABET`, `CONCEPT`, or `EOW` (End of Word).
   - `frequency`: Weight for predictive sorting.
   - `children`: Map of children nodes.
2. **Hybrid Structure:**
   - **Layer 1 (Alphabet):** Standard A-Z dictionary.
   - **Layer 2 (Concepts):** When a word is completed (e.g., "Food"), the "Hallway" should offer a branch to a "Concept Suite" (Icons like Burger, Pizza, Apple).
   - **Layer 3 (Hierarchical Concepts):** Diving into "Travel" yields "Plane," "Hotel," "Map."
3. **The Search Engine:** 
   - Must return the "Top N" predictions based on the current `potentialWord`.
   - Must be fast enough to update every frame (or offloaded to a background thread if large).
4. **Serialization:** 
   - Should be able to load from a JSON/Protobuf dictionary file.

**Goal:**
Enable a user to type "He" and see 'l', 'y', 'a' as letters, while also seeing a "Greeting" icon branch that leads to emojis like 👋, 🤝.

**Deliverable:** A Kotlin-based `HybridTrie` class with unit tests demonstrating successful navigation between alphanumeric and conceptual nodes.
