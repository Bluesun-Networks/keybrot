package com.zooptype.ztype.trie

/**
 * TrieNavigator: Manages the current position within the HybridTrie
 * as the user dives through nodes.
 *
 * Think of it as a cursor that tracks:
 * - Where we are in the trie (current node)
 * - What prefix has been built so far
 * - What the next available children/predictions are
 * - The previously committed word (for bigram context)
 */
class TrieNavigator(private val trie: HybridTrie) {

    /** Current position in the trie */
    private var currentNode: TrieNode = trie.root

    /** The prefix built so far by diving through nodes */
    private var currentPrefix = StringBuilder()

    /** The previously committed word (for bigram prediction) */
    private var previousWord: String? = null

    /** Maximum number of nodes to show on the sphere */
    var maxVisibleNodes: Int = 26

    /**
     * Get the current set of nodes to display on the sphere.
     * This is the core "what should the user see?" method.
     */
    fun getCurrentNodes(): List<TrieNode> {
        return if (currentPrefix.isEmpty()) {
            // At root: show top-level letters (A-Z) sorted by frequency
            trie.getTopRootChildren(maxVisibleNodes)
        } else {
            // Deeper in trie: show children of current node
            trie.getPredictions(currentPrefix.toString(), maxVisibleNodes, previousWord)
        }
    }

    /**
     * Advance into a node (user has selected/dived into a character).
     */
    fun advance(char: Char) {
        currentPrefix.append(char.lowercaseChar())
        val nextNode = currentNode.children[char.lowercaseChar().toString()]
        if (nextNode != null) {
            currentNode = nextNode
        }
    }

    /**
     * Advance into a concept node (user selected an emoji/icon).
     */
    fun advanceConcept(conceptLabel: String) {
        val conceptNode = currentNode.children[conceptLabel]
        if (conceptNode != null) {
            currentNode = conceptNode
        }
    }

    /**
     * Check if the current position is an end-of-word.
     */
    fun isEndOfWord(): Boolean {
        return currentNode.type == NodeType.END_OF_WORD
    }

    /**
     * Get the current completed word (if at an end-of-word node).
     */
    fun getCurrentWord(): String? {
        return if (isEndOfWord()) currentNode.word else null
    }

    /**
     * Get the top prediction (most likely word from current position).
     */
    fun getTopPrediction(): String? {
        val words = currentNode.getReachableWords(currentPrefix.toString(), 1)
        return words.firstOrNull()?.first
    }

    /**
     * Get the top N word predictions from the current position.
     */
    fun getTopPredictions(n: Int = 5): List<String> {
        return currentNode.getReachableWords(currentPrefix.toString(), n)
            .map { it.first }
    }

    /**
     * Get the current prefix.
     */
    fun getCurrentPrefix(): String = currentPrefix.toString()

    /**
     * Get the current node.
     */
    fun getCurrentTrieNode(): TrieNode = currentNode

    /**
     * Get the depth (how many letters deep we are).
     */
    fun getDepth(): Int = currentPrefix.length

    /**
     * Check if concept nodes are available at current position.
     */
    fun hasConceptChildren(): Boolean {
        return currentNode.children.values.any { it.type == NodeType.CONCEPT }
    }

    /**
     * Get concept children at current position.
     */
    fun getConceptChildren(): List<TrieNode> {
        return currentNode.children.values.filter { it.type == NodeType.CONCEPT }
    }

    /**
     * Reset to root (for new word input).
     * Optionally records the completed word as context for bigram prediction.
     */
    fun reset(completedWord: String? = null) {
        if (completedWord != null) {
            // Record bigram
            previousWord?.let { prev ->
                trie.recordBigram(prev, completedWord)
            }
            previousWord = completedWord
        }
        currentNode = trie.root
        currentPrefix.clear()
    }

    /**
     * Full reset (new input session).
     */
    fun fullReset() {
        currentNode = trie.root
        currentPrefix.clear()
        previousWord = null
    }
}
