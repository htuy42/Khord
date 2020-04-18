package com.htuy.chord

import org.junit.Assert.*
import org.junit.Test
import java.lang.AssertionError
import kotlin.random.Random

/**
 * Tests for the functions of [ChordNode].
 * In practice a lot of this is integration testing.
 */
class ChordNodeTest {

    data class ChordRingBasicLookupTest(
        val ids: List<ByteArray>,
        val toLookup: ByteArray,
        val expectedLocation: ByteArray
    )

    private val chordRingBasicLookupTests = listOf(
        ChordRingBasicLookupTest(
            listOf(byteArrayOf(1), byteArrayOf(4), byteArrayOf(2), byteArrayOf(5)),
            byteArrayOf(3),
            byteArrayOf(4)
        ),
        ChordRingBasicLookupTest(
            listOf(byteArrayOf(1), byteArrayOf(4), byteArrayOf(2), byteArrayOf(5)),
            byteArrayOf(4),
            byteArrayOf(4)
        ),
        ChordRingBasicLookupTest(
            listOf(byteArrayOf(1), byteArrayOf(4), byteArrayOf(2), byteArrayOf(5)),
            byteArrayOf(6),
            byteArrayOf(1)
        ),
        ChordRingBasicLookupTest(
            listOf(byteArrayOf(1), byteArrayOf(4), byteArrayOf(2), byteArrayOf(5)),
            byteArrayOf(3),
            byteArrayOf(4)
        ),
        ChordRingBasicLookupTest(
            listOf(byteArrayOf(1), byteArrayOf(4), byteArrayOf(2), byteArrayOf(5)),
            byteArrayOf(3, 4, 5),
            byteArrayOf(1)
        )
    )

    data class ChordRingBasicLookupTestIds(
        val ids: List<ChordId>,
        val toLookup: ChordId,
        val expectedLocation: ChordId
    ) {
        companion object {
            fun fromNonIds(nonIds: ChordRingBasicLookupTest): ChordRingBasicLookupTestIds {
                return ChordRingBasicLookupTestIds(
                    ChordTestUtils.idListFromByteArrays(*nonIds.ids.toTypedArray()),
                    ChordIdUtils.chordIdFromBytes(nonIds.toLookup),
                    ChordIdUtils.chordIdFromBytes(nonIds.expectedLocation)
                )
            }
        }
    }

    /**
     * Generates a random [ChordRingBasicLookupTest].
     */
    fun createRandomLookupTest(): ChordRingBasicLookupTestIds {
        val ids = (0..Random.nextInt(10,20)).map {
            ChordIdUtils.chordIdFromBytes(Random.nextBytes(LENGTH / 8))
        }.distinctBy { it.display() }

        val target = ChordIdUtils.chordIdFromBytes(Random.nextBytes(LENGTH / 8))
        for (id in ids) {
            if (id.contentEquals(target)) {
                return createRandomLookupTest()
            }
        }
        val sorted = ChordTestUtils.sortIds(ids)
        var correctSucc: ChordId = sorted[0]
        for (chordId in sorted) {
            if (chordId.greaterThan(target) || chordId.contentEquals(target)) {
                correctSucc = chordId
                break
            }
        }
        return ChordRingBasicLookupTestIds(ids, target, correctSucc)
    }

    private fun testIdsFromStrings(strings : List<String>): ChordRingBasicLookupTestIds{
        val ids = strings.map{str ->
            BooleanArray(LENGTH){
                if(it >= str.length){
                    false
                } else {
                    str[it] == '1'
                }
            }
        }
        return ChordRingBasicLookupTestIds(ids,ids[0],ids[0])
    }

    @Test
    fun testChordRingCreation(){
        fun doSingleTest(test: ChordRingBasicLookupTestIds){
            println("Making ring")
            val cluster = ChordTestUtils.createRingWithIdsByJoin(test.ids)
            println("Made ring")
            // Sleep to allow the ring to stabilize
            Thread.sleep((250 * LENGTH * 2.5).toLong())
            try{
                cluster.verifyTables()
            } catch(e: AssertionError){
                throw e
            }
            try {
                cluster.lookupValueAtEach(
                    test.toLookup,
                    test.expectedLocation
                )
            } catch (e: AssertionError) {
                e.printStackTrace()
                println("Failed test.")
                println("IDS:")
                for(id in test.ids){
                    println(id.display())
                }
                println("To lookup")
                println(test.toLookup.display())
                println("Expected loc")
                println(test.expectedLocation.display())
                cluster.stop()
                throw e
            }
            cluster.stop()

        }
        for(i in 0..1){
            println("\n new test")
//            doSingleTest(createRandomLookupTest())
        }
    }

    @Test
    fun testChordRingBasicLookup() {
        fun doSingleTest(test: ChordRingBasicLookupTestIds) {
            val cluster =
                ChordTestUtils.createRingWithCorrectTables(test.ids)
            try {
                cluster.lookupValueAtEach(
                    test.toLookup,
                    test.expectedLocation
                )
            } catch (e: AssertionError) {
                e.printStackTrace()
                println("Failed test.")
                println("IDS:")
                for(id in test.ids){
                    println(id.display())
                }
                println("To lookup")
                println(test.toLookup.display())
                println("Expected loc")
                println(test.expectedLocation.display())
                cluster.stop()
                throw e
            }
            cluster.stop()
        }
        chordRingBasicLookupTests.forEach { doSingleTest(ChordRingBasicLookupTestIds.fromNonIds(it)) }

        for (i in 0..200) {
            doSingleTest(createRandomLookupTest())
        }
    }

    @Test
    fun testKvStore(){
        for(i in 0..1){
            val test = createRandomLookupTest()
            val cluster = ChordTestUtils.createRingWithIdsByJoin(test.ids)
            val store = DataStore(cluster.nodes[0].ownAddr)

            val datas = (0..100).map{
                val key = "$it"
                val contents = key.toByteArray()
                Pair(key,contents)
            }
            for((key,contents) in datas){
                store.store(key,contents)
            }
            Thread.sleep(LENGTH * 25 * UPDATE_RATE)
            cluster.verifyTables()

            val otherStore = DataStore(cluster.nodes[1].ownAddr)
            for((key,contents) in datas){
                val fetched = otherStore.get(key)
                assert(fetched?.contentEquals(contents)?:false){
                    "Fetched ${fetched.toString()} for $key"
                }
            }
            cluster.stop()
        }
    }
}