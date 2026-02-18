package com.zooptype.ztype.trie

/**
 * A single node in the Hybrid Concept-Trie.
 *
 * Can represent:
 * - A letter in a word path (ALPHABET)
 * - An end-of-word marker (END_OF_WORD)
 * - A concept/emoji entry (CONCEPT)
 * - The root of the trie (ROOT)
 */
data class TrieNode(
    /** The character this node represents (null for root/concept nodes) */
    val char: Char? = null,

    /** The type of this node */
    var type: NodeType = NodeType.ALPHABET,

    /** Frequency weight for predictive sorting */
    var frequency: Int = 0,

    /** The complete word if this is an END_OF_WORD node */
    var word: String? = null,

    /** Child nodes: key is character string or concept label */
    val children: HashMap<String, TrieNode> = HashMap(),

    // --- Concept-specific fields ---

    /** Icon reference for CONCEPT nodes (drawable resource name) */
    val conceptIcon: String? = null,

    /** Display label for CONCEPT nodes */
    val conceptLabel: String? = null,

    /** Emoji string for CONCEPT nodes */
    val conceptEmoji: String? = null
) {
    /**
     * Get the display character for this node.
     * For alphabet nodes: the character itself
     * For concept nodes: first char of the emoji
     * For root: space
     */
    fun displayChar(): Char {
        return char ?: conceptEmoji?.firstOrNull() ?: ' '
    }

    /**
     * Get the display string for this node.
     * For alphabet nodes: the character
     * For concept nodes: the emoji
     */
    fun displayString(): String {
        return when (type) {
            NodeType.CONCEPT -> conceptEmoji ?: conceptLabel ?: "?"
            else -> char?.toString() ?: ""
        }
    }

    /**
     * Calculate the total frequency of this subtree.
     * Used for sorting root-level predictions.
     */
    fun subtreeFrequency(): Int {
        var total = frequency
        for (child in children.values) {
            total += child.subtreeFrequency()
        }
        return total
    }

    /**
     * Check if this node has any end-of-word descendants.
     */
    fun hasWords(): Boolean {
        if (type == NodeType.END_OF_WORD) return true
        return children.values.any { it.hasWords() }
    }

    /**
     * Get all words reachable from this node (for prediction).
     * Returns pairs of (word, frequency).
     */
    fun getReachableWords(prefix: String = "", maxResults: Int = 10): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(prefix, results, maxResults)
        return results.sortedByDescending { it.second }.take(maxResults)
    }

    private fun collectWords(prefix: String, results: MutableList<Pair<String, Int>>, maxResults: Int) {
        if (results.size >= maxResults * 2) return // Collect extra, then sort + trim

        if (type == NodeType.END_OF_WORD && word != null) {
            results.add(word!! to frequency)
        }

        for ((key, child) in children) {
            if (child.type == NodeType.CONCEPT) continue // Skip concepts for word collection
            child.collectWords(prefix + (child.char ?: ""), results, maxResults)
        }
    }
}

enum class NodeType {
    ROOT,
    ALPHABET,
    END_OF_WORD,
    CONCEPT
}
