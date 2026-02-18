package com.zooptype.ztype.trie

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Hybrid Concept-Trie: The "Brain" of Keybrot.
 *
 * A multi-layered prefix tree that supports:
 * - Standard alphanumeric input (A-Z, 0-9)
 * - Conceptual branching (words → emoji/icon concept suites)
 * - Frequency-weighted predictions that adapt to user behavior
 * - Bigram/trigram context for future prediction enhancement
 *
 * The trie is composable: the same structure works whether showing
 * 5 top predictions or the full 36+ alphanumeric set.
 */
class HybridTrie {

    val root = TrieNode(type = NodeType.ROOT)

    // User frequency boosts (persisted separately)
    private val userFrequencyBoosts = HashMap<String, Int>()

    // Bigram context: previousWord → Map<nextWord, count>
    private val bigramCounts = HashMap<String, HashMap<String, Int>>()

    /**
     * Insert a word with its base frequency.
     */
    fun insert(word: String, frequency: Int = 1) {
        var current = root
        for ((index, char) in word.lowercase().withIndex()) {
            current = current.children.getOrPut(char.toString()) {
                TrieNode(
                    char = char,
                    type = NodeType.ALPHABET
                )
            }
        }
        current.type = NodeType.END_OF_WORD
        current.frequency = frequency
        current.word = word.lowercase()
    }

    /**
     * Insert a concept suite: a word that branches into icons/emojis.
     * e.g., "food" → [🍔, 🍕, 🍎]
     */
    fun insertConceptSuite(triggerWord: String, concepts: List<ConceptNode>) {
        // First, make sure the trigger word is in the trie
        var current = root
        for (char in triggerWord.lowercase()) {
            current = current.children.getOrPut(char.toString()) {
                TrieNode(char = char, type = NodeType.ALPHABET)
            }
        }
        current.type = NodeType.END_OF_WORD
        current.word = triggerWord.lowercase()

        // Add concept children
        for (concept in concepts) {
            current.children[concept.label] = TrieNode(
                char = null,
                type = NodeType.CONCEPT,
                conceptIcon = concept.icon,
                conceptLabel = concept.label,
                conceptEmoji = concept.emoji,
                frequency = concept.frequency
            )
        }
    }

    /**
     * Get predictions for a given prefix.
     * Returns the top N children sorted by frequency (including user boosts).
     */
    fun getPredictions(prefix: String, maxResults: Int = 26, previousWord: String? = null): List<TrieNode> {
        val node = findNode(prefix) ?: return getTopRootChildren(maxResults)

        return node.children.values
            .sortedByDescending { child ->
                val baseFreq = child.frequency
                val boost = if (child.word != null) {
                    userFrequencyBoosts[child.word] ?: 0
                } else {
                    0
                }
                // Bigram boost
                val bigramBoost = if (previousWord != null && child.word != null) {
                    bigramCounts[previousWord]?.get(child.word) ?: 0
                } else {
                    0
                }
                baseFreq + boost * 10 + bigramBoost * 5
            }
            .take(maxResults)
    }

    /**
     * Get predictions at root level (initial sphere).
     * Returns A-Z sorted by frequency of words starting with each letter.
     */
    fun getTopRootChildren(maxResults: Int = 26): List<TrieNode> {
        return root.children.values
            .sortedByDescending { it.subtreeFrequency() }
            .take(maxResults)
    }

    /**
     * Find the node at the end of a prefix path.
     */
    fun findNode(prefix: String): TrieNode? {
        var current = root
        for (char in prefix.lowercase()) {
            current = current.children[char.toString()] ?: return null
        }
        return current
    }

    /**
     * Boost the frequency of a word after the user selects it.
     * This makes the keyboard "learn" user preferences.
     */
    fun boostFrequency(word: String) {
        val key = word.lowercase()
        userFrequencyBoosts[key] = (userFrequencyBoosts[key] ?: 0) + 1
    }

    /**
     * Record a bigram (word pair) for contextual prediction.
     */
    fun recordBigram(previousWord: String, currentWord: String) {
        val prev = previousWord.lowercase()
        val curr = currentWord.lowercase()
        val map = bigramCounts.getOrPut(prev) { HashMap() }
        map[curr] = (map[curr] ?: 0) + 1
    }

    /**
     * Load the default English dictionary from assets.
     */
    fun loadDefaultDictionary(context: Context) {
        try {
            val inputStream = context.assets.open("dictionary.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<DictionaryEntry>>() {}.type
            val entries: List<DictionaryEntry> = Gson().fromJson(reader, type)

            for (entry in entries) {
                insert(entry.word, entry.frequency)
            }
            reader.close()

            // Load concept suites
            loadConceptSuites(context)
        } catch (e: Exception) {
            // Fallback: insert basic A-Z if dictionary not found
            loadFallbackDictionary()
        }
    }

    /**
     * Load concept suites (word → emoji mappings) from assets.
     */
    private fun loadConceptSuites(context: Context) {
        try {
            val inputStream = context.assets.open("concepts.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<ConceptSuite>>() {}.type
            val suites: List<ConceptSuite> = Gson().fromJson(reader, type)

            for (suite in suites) {
                insertConceptSuite(suite.triggerWord, suite.concepts)
            }
            reader.close()
        } catch (e: Exception) {
            // Concepts are optional, fail silently
            loadFallbackConcepts()
        }
    }

    /**
     * Fallback dictionary with basic common words.
     */
    private fun loadFallbackDictionary() {
        val commonWords = listOf(
            "the" to 100, "be" to 95, "to" to 94, "of" to 93, "and" to 92,
            "a" to 91, "in" to 90, "that" to 89, "have" to 88, "i" to 87,
            "it" to 86, "for" to 85, "not" to 84, "on" to 83, "with" to 82,
            "he" to 81, "as" to 80, "you" to 79, "do" to 78, "at" to 77,
            "this" to 76, "but" to 75, "his" to 74, "by" to 73, "from" to 72,
            "they" to 71, "we" to 70, "say" to 69, "her" to 68, "she" to 67,
            "or" to 66, "an" to 65, "will" to 64, "my" to 63, "one" to 62,
            "all" to 61, "would" to 60, "there" to 59, "their" to 58, "what" to 57,
            "so" to 56, "up" to 55, "out" to 54, "if" to 53, "about" to 52,
            "who" to 51, "get" to 50, "which" to 49, "go" to 48, "me" to 47,
            "when" to 46, "make" to 45, "can" to 44, "like" to 43, "time" to 42,
            "no" to 41, "just" to 40, "him" to 39, "know" to 38, "take" to 37,
            "people" to 36, "into" to 35, "year" to 34, "your" to 33, "good" to 32,
            "some" to 31, "could" to 30, "them" to 29, "see" to 28, "other" to 27,
            "than" to 26, "then" to 25, "now" to 24, "look" to 23, "only" to 22,
            "come" to 21, "its" to 20, "over" to 19, "think" to 18, "also" to 17,
            "back" to 16, "after" to 15, "use" to 14, "two" to 13, "how" to 12,
            "our" to 11, "work" to 10, "first" to 9, "well" to 8, "way" to 7,
            "even" to 6, "new" to 5, "want" to 4, "because" to 3, "any" to 2,
            "these" to 1, "give" to 1, "day" to 1, "most" to 1, "us" to 1,
            // More common words
            "hello" to 50, "hi" to 48, "hey" to 45, "thanks" to 40, "thank" to 40,
            "please" to 38, "sorry" to 35, "yes" to 60, "no" to 58, "okay" to 55,
            "ok" to 54, "sure" to 45, "great" to 40, "love" to 50, "happy" to 35,
            "food" to 30, "eat" to 28, "travel" to 25, "car" to 22, "home" to 40,
            "beach" to 20, "weather" to 18, "music" to 25, "movie" to 22, "game" to 20
        )

        for ((word, freq) in commonWords) {
            insert(word, freq)
        }
    }

    /**
     * Fallback concept suites.
     */
    private fun loadFallbackConcepts() {
        insertConceptSuite("food", listOf(
            ConceptNode("burger", "\uD83C\uDF54", "Burger", 10),
            ConceptNode("pizza", "\uD83C\uDF55", "Pizza", 9),
            ConceptNode("apple", "\uD83C\uDF4E", "Apple", 8),
            ConceptNode("taco", "\uD83C\uDF2E", "Taco", 7),
            ConceptNode("sushi", "\uD83C\uDF63", "Sushi", 6)
        ))
        insertConceptSuite("travel", listOf(
            ConceptNode("plane", "✈️", "Plane", 10),
            ConceptNode("hotel", "\uD83C\uDFE8", "Hotel", 9),
            ConceptNode("map", "\uD83D\uDDFA️", "Map", 8),
            ConceptNode("beach_concept", "\uD83C\uDFD6️", "Beach", 7),
            ConceptNode("car_concept", "\uD83D\uDE97", "Car", 6)
        ))
        insertConceptSuite("hello", listOf(
            ConceptNode("wave", "\uD83D\uDC4B", "Wave", 10),
            ConceptNode("handshake", "\uD83E\uDD1D", "Handshake", 9),
            ConceptNode("smile", "\uD83D\uDE0A", "Smile", 8)
        ))
        insertConceptSuite("love", listOf(
            ConceptNode("heart", "❤️", "Heart", 10),
            ConceptNode("kiss", "\uD83D\uDE18", "Kiss", 9),
            ConceptNode("hug", "\uD83E\uDD17", "Hug", 8),
            ConceptNode("rose", "\uD83C\uDF39", "Rose", 7)
        ))
        insertConceptSuite("weather", listOf(
            ConceptNode("sun", "☀️", "Sun", 10),
            ConceptNode("rain", "\uD83C\uDF27️", "Rain", 9),
            ConceptNode("snow", "❄️", "Snow", 8),
            ConceptNode("storm", "⛈️", "Storm", 7)
        ))
        insertConceptSuite("music", listOf(
            ConceptNode("notes", "\uD83C\uDFB5", "Notes", 10),
            ConceptNode("guitar", "\uD83C\uDFB8", "Guitar", 9),
            ConceptNode("mic", "\uD83C\uDFA4", "Mic", 8),
            ConceptNode("headphones", "\uD83C\uDFA7", "Headphones", 7)
        ))
    }

    /**
     * Export user frequency data for persistence.
     */
    fun exportUserData(): Map<String, Int> = HashMap(userFrequencyBoosts)

    /**
     * Import user frequency data from persistence.
     */
    fun importUserData(data: Map<String, Int>) {
        userFrequencyBoosts.clear()
        userFrequencyBoosts.putAll(data)
    }

    /**
     * Export bigram data for persistence.
     */
    fun exportBigramData(): Map<String, Map<String, Int>> = HashMap(bigramCounts)

    /**
     * Import bigram data from persistence.
     */
    fun importBigramData(data: Map<String, Map<String, Int>>) {
        bigramCounts.clear()
        for ((key, value) in data) {
            bigramCounts[key] = HashMap(value)
        }
    }
}

// --- Data classes ---

data class DictionaryEntry(
    val word: String,
    val frequency: Int
)

data class ConceptSuite(
    val triggerWord: String,
    val concepts: List<ConceptNode>
)

data class ConceptNode(
    val label: String,
    val emoji: String,
    val icon: String,  // Could be a drawable resource name
    val frequency: Int
)
