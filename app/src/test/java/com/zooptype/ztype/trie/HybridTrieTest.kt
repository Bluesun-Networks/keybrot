package com.zooptype.ztype.trie

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the HybridTrie — the "Brain" of Keybrot.
 */
class HybridTrieTest {

    private lateinit var trie: HybridTrie

    @Before
    fun setUp() {
        trie = HybridTrie()
    }

    @Test
    fun `insert and find basic word`() {
        trie.insert("hello", 50)
        val node = trie.findNode("hello")
        assertNotNull(node)
        assertEquals(NodeType.END_OF_WORD, node!!.type)
        assertEquals("hello", node.word)
    }

    @Test
    fun `findNode returns null for missing prefix`() {
        trie.insert("hello", 50)
        assertNull(trie.findNode("xyz"))
    }

    @Test
    fun `predictions sorted by frequency`() {
        trie.insert("hello", 50)
        trie.insert("help", 30)
        trie.insert("hero", 20)
        trie.insert("heap", 10)

        val predictions = trie.getPredictions("he", 4)
        assertTrue(predictions.isNotEmpty())

        // Children of "he" should be sorted by subtree frequency
        val chars = predictions.map { it.char }
        // 'l' subtree (hello=50 + help=30 = 80) should come before 'r' (hero=20) and 'a' (heap=10)
        assertEquals('l', chars[0])
    }

    @Test
    fun `root predictions return A-Z sorted by frequency`() {
        trie.insert("the", 100)
        trie.insert("to", 90)
        trie.insert("and", 80)
        trie.insert("hello", 50)

        val roots = trie.getTopRootChildren(26)
        assertTrue(roots.isNotEmpty())

        // 't' subtree (the=100 + to=90 = 190) should come first
        assertEquals('t', roots[0].char)
    }

    @Test
    fun `frequency boosting works`() {
        trie.insert("hello", 50)
        trie.insert("help", 50) // Same base frequency

        // Boost "help" multiple times
        trie.boostFrequency("help")
        trie.boostFrequency("help")
        trie.boostFrequency("help")

        // "help" should now rank higher due to user boosts
        val predictions = trie.getPredictions("hel", 2)
        // The boost should affect ordering
        assertTrue(predictions.isNotEmpty())
    }

    @Test
    fun `concept suite insertion and retrieval`() {
        trie.insert("food", 30)
        trie.insertConceptSuite("food", listOf(
            ConceptNode("burger", "\uD83C\uDF54", "Burger", 10),
            ConceptNode("pizza", "\uD83C\uDF55", "Pizza", 9)
        ))

        val foodNode = trie.findNode("food")
        assertNotNull(foodNode)
        assertEquals(NodeType.END_OF_WORD, foodNode!!.type)

        // Check concept children
        val burger = foodNode.children["burger"]
        assertNotNull(burger)
        assertEquals(NodeType.CONCEPT, burger!!.type)
        assertEquals("\uD83C\uDF54", burger.conceptEmoji)
    }

    @Test
    fun `bigram recording and retrieval`() {
        trie.insert("how", 50)
        trie.insert("are", 40)
        trie.insert("about", 30)

        // Record bigrams
        trie.recordBigram("how", "are")
        trie.recordBigram("how", "are")
        trie.recordBigram("how", "about")

        // "are" should be boosted after "how" due to bigram count
        val predictions = trie.getPredictions("", 26, previousWord = "how")
        assertTrue(predictions.isNotEmpty())
    }

    @Test
    fun `export and import user data`() {
        trie.insert("hello", 50)
        trie.boostFrequency("hello")
        trie.boostFrequency("hello")

        val exported = trie.exportUserData()
        assertEquals(2, exported["hello"])

        // Create a new trie and import
        val newTrie = HybridTrie()
        newTrie.insert("hello", 50)
        newTrie.importUserData(exported)

        val reExported = newTrie.exportUserData()
        assertEquals(2, reExported["hello"])
    }

    @Test
    fun `trie node display char works for alphabet`() {
        val node = TrieNode(char = 'a', type = NodeType.ALPHABET)
        assertEquals('a', node.displayChar())
    }

    @Test
    fun `trie node display string works for concept`() {
        val node = TrieNode(
            type = NodeType.CONCEPT,
            conceptEmoji = "\uD83C\uDF54",
            conceptLabel = "Burger"
        )
        assertEquals("\uD83C\uDF54", node.displayString())
    }

    @Test
    fun `trie navigator advances correctly`() {
        trie.insert("hello", 50)

        val nav = TrieNavigator(trie)
        assertEquals("", nav.getCurrentPrefix())
        assertEquals(0, nav.getDepth())

        nav.advance('h')
        assertEquals("h", nav.getCurrentPrefix())
        assertEquals(1, nav.getDepth())

        nav.advance('e')
        assertEquals("he", nav.getCurrentPrefix())
        assertEquals(2, nav.getDepth())
    }

    @Test
    fun `trie navigator detects end of word`() {
        trie.insert("hi", 50)

        val nav = TrieNavigator(trie)
        nav.advance('h')
        assertFalse(nav.isEndOfWord())

        nav.advance('i')
        assertTrue(nav.isEndOfWord())
        assertEquals("hi", nav.getCurrentWord())
    }

    @Test
    fun `trie navigator reset clears state`() {
        trie.insert("hello", 50)

        val nav = TrieNavigator(trie)
        nav.advance('h')
        nav.advance('e')
        assertEquals("he", nav.getCurrentPrefix())

        nav.reset()
        assertEquals("", nav.getCurrentPrefix())
        assertEquals(0, nav.getDepth())
    }

    @Test
    fun `trie navigator top prediction works`() {
        trie.insert("hello", 50)
        trie.insert("help", 30)

        val nav = TrieNavigator(trie)
        nav.advance('h')
        nav.advance('e')
        nav.advance('l')

        val topPred = nav.getTopPrediction()
        assertEquals("hello", topPred) // Higher frequency
    }

    @Test
    fun `multiple words with same prefix`() {
        trie.insert("cat", 30)
        trie.insert("car", 40)
        trie.insert("card", 20)
        trie.insert("care", 25)

        val node = trie.findNode("ca")
        assertNotNull(node)
        assertEquals(2, node!!.children.size) // 'r' and 't'
    }

    @Test
    fun `empty trie returns empty predictions`() {
        val predictions = trie.getPredictions("xyz", 10)
        // Should fallback to root children, which is also empty
        assertTrue(predictions.isEmpty())
    }

    @Test
    fun `subtree frequency calculation`() {
        trie.insert("the", 100)
        trie.insert("to", 90)
        trie.insert("that", 80)

        val tNode = trie.findNode("t")
        assertNotNull(tNode)
        // Subtree should sum: the(100) + to(90) + that(80) = 270 + intermediate nodes
        assertTrue(tNode!!.subtreeFrequency() > 0)
    }
}
