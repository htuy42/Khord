package com.htuy.chord

import org.junit.Test

/**
 * Tests for the functionality of [ChordId]
 */
class ChordIdTest {

    @Test
    fun testCreatingIds() {
        val testString1 = "dog"
        val testString2 = "cat"
        val id1 = ChordIdUtils.chordIdFromString(testString1)
        val id2 = ChordIdUtils.chordIdFromString(testString2)
        val id3 = ChordIdUtils.chordIdFromAddrAndPort(testString1, 12)
        val id4 = ChordIdUtils.chordIdFromAddrAndPort(testString1, 9)

        // These are not strictly required to be true, especially given that LENGTH is currently a very small number.
        // So if these fail, check some additional strings, run additional testing etc. If these pass, it indicates
        // basic functionality of the hashing at least sort of works.
        assert(!id1.contentEquals(id2))
        assert(!id3.contentEquals(id1))
        assert(!id4.contentEquals(id3))

        // These should never fail. If they do something went wrong.
        assert(id1.size == LENGTH)
        assert(id2.size == LENGTH)
        assert(id3.size == LENGTH)
        assert(id4.size == LENGTH)
    }

    private data class GreaterThanTest(val first: ByteArray, val second: ByteArray, val greater: Boolean)

    private val greaterThanTests = listOf(
        GreaterThanTest(byteArrayOf(1), byteArrayOf(2), false),
        GreaterThanTest(byteArrayOf(1), byteArrayOf(6), false),
        GreaterThanTest(byteArrayOf(10), byteArrayOf(25), false),
        GreaterThanTest(byteArrayOf(2), byteArrayOf(1), true),
        GreaterThanTest(byteArrayOf(20), byteArrayOf(1), true),
        GreaterThanTest(byteArrayOf(25), byteArrayOf(12), true),
        GreaterThanTest(byteArrayOf(2), byteArrayOf(2), false),
        GreaterThanTest(byteArrayOf(2, 5), byteArrayOf(1, 100), false),
        GreaterThanTest(byteArrayOf(1, 100), byteArrayOf(2, 5), true),
        GreaterThanTest(byteArrayOf(2, 100), byteArrayOf(1, 100), true)
    )

    @Test
    fun testGreaterThan() {
        for (test in greaterThanTests) {
            val id1 = ChordIdUtils.chordIdFromBytes(test.first)
            val id2 = ChordIdUtils.chordIdFromBytes(test.second)
            assert(id1.greaterThan(id2) == test.greater) { "Failed on $test." }
        }
    }

    private data class BetweenTest(
        val first: ByteArray,
        val second: ByteArray,
        val target: ByteArray,
        val isBetween: Boolean,
        val endInclusive : Boolean = true
    )

    private val betweenTests = listOf(
        // All positive, true
        BetweenTest(byteArrayOf(1), byteArrayOf(3), byteArrayOf(2), true),
        BetweenTest(byteArrayOf(1), byteArrayOf(10), byteArrayOf(5), true),
        BetweenTest(byteArrayOf(10), byteArrayOf(30), byteArrayOf(20), true),
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(40), true),
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(5), true),
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(10), true),

        // All positive, false
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(12), false),
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(30), false),
        BetweenTest(byteArrayOf(10), byteArrayOf(30), byteArrayOf(5), false),
        BetweenTest(byteArrayOf(10), byteArrayOf(30), byteArrayOf(35), false),

        // Negatives trues
        BetweenTest(byteArrayOf(-10), byteArrayOf(30), byteArrayOf(5), true),
        BetweenTest(byteArrayOf(-10), byteArrayOf(30), byteArrayOf(-5), true),
        BetweenTest(byteArrayOf(-10), byteArrayOf(-5), byteArrayOf(-6), true),
        BetweenTest(byteArrayOf(20), byteArrayOf(-20), byteArrayOf(25), true),
        BetweenTest(byteArrayOf(20), byteArrayOf(-20), byteArrayOf(-25), true),

        // Negative falses
        BetweenTest(byteArrayOf(20), byteArrayOf(-20), byteArrayOf(10), false),
        BetweenTest(byteArrayOf(20), byteArrayOf(-20), byteArrayOf(-10), false),
        BetweenTest(byteArrayOf(-20), byteArrayOf(20), byteArrayOf(25), false),
        BetweenTest(byteArrayOf(-20), byteArrayOf(20), byteArrayOf(-25), false),
        BetweenTest(byteArrayOf(-20), byteArrayOf(-10), byteArrayOf(10), false),
        BetweenTest(byteArrayOf(-20), byteArrayOf(-10), byteArrayOf(-5), false),
        BetweenTest(byteArrayOf(-20), byteArrayOf(-10), byteArrayOf(-25), false),

        // Multiple bytes
        BetweenTest(byteArrayOf(2,10), byteArrayOf(5,10), byteArrayOf(3,10), true),
        BetweenTest(byteArrayOf(2,10), byteArrayOf(5,12), byteArrayOf(6,11), true),
        BetweenTest(byteArrayOf(2,10), byteArrayOf(5,11), byteArrayOf(6,11), false),

        // Not end inclusive
        BetweenTest(byteArrayOf(30), byteArrayOf(10), byteArrayOf(10), false, false)
    )

    @Test
    fun testBetween() {
        for (test in betweenTests) {
            val id1 = ChordIdUtils.chordIdFromBytes(test.first)
            val id2 = ChordIdUtils.chordIdFromBytes(test.second)
            val targetId = ChordIdUtils.chordIdFromBytes(test.target)

            assert(targetId.isBetween(id1, id2,endInclusive = test.endInclusive) == test.isBetween) { "Failed on $test." }
        }
    }

    private data class GetTableItemTest(val original: ByteArray, val expected: ByteArray, val index: Int)

    private val getTableItemTests = listOf(
        GetTableItemTest(byteArrayOf(1), byteArrayOf(2), 0),
        GetTableItemTest(byteArrayOf(1), byteArrayOf(3), 1),
        GetTableItemTest(byteArrayOf(1), byteArrayOf(5), 2),
        GetTableItemTest(byteArrayOf(22), byteArrayOf(24), 1),
        GetTableItemTest(byteArrayOf(127), byteArrayOf((128).toByte()), 0),
        GetTableItemTest(byteArrayOf(127), byteArrayOf(-128), 0),
        GetTableItemTest(byteArrayOf(127), byteArrayOf((129).toByte()), 1),
        GetTableItemTest(byteArrayOf(127), byteArrayOf((135).toByte()), 3),
        GetTableItemTest(byteArrayOf((200).toByte()), byteArrayOf((208).toByte()), 3),
        GetTableItemTest(byteArrayOf((200).toByte()), byteArrayOf(8, 1), 6),
        GetTableItemTest(byteArrayOf(-1), byteArrayOf(0, 1), 0)

    )

    @Test
    fun testGetTableItem() {
        for (test in getTableItemTests) {
            val idOrig = ChordIdUtils.chordIdFromBytes(test.original)
            val idExpected = ChordIdUtils.chordIdFromBytes(test.expected)
            val actual = idOrig.getTableItem(test.index)
            assert(idExpected.contentEquals(actual)) { "Failed on $test. Got ${actual.asList()}, expected ${idExpected.asList()}." }
        }
    }
}