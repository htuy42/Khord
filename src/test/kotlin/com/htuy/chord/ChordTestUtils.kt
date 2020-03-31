package com.htuy.chord

import java.lang.IllegalStateException

/**
 * Utilities for testing on Chord clusters.
 */
object ChordTestUtils {
    /**
     * Wrapper around a list of [ChordNode]s with some utility functions.
     */
    class ChordCluster(val nodes : List<ChordNode>){
        /**
         * Stop all of the [ChordNode]s
         */
        fun stop(){
            nodes.forEach { it.stop() }
        }

        /**
         * Lookup [toLookFor]s succ at each node, and confirm that from all of them we get [expectedHost]
         */
        fun lookupValueAtEach(toLookFor : ChordId, expectedHost : ChordId){
            nodes.forEachIndexed {ind,it ->
                val foundSucc = it.findSuccessor(toLookFor)
                assert(foundSucc.id.contentEquals(expectedHost)){"Expected to find ${toLookFor.display()} at ${expectedHost.display()}, but found it at ${foundSucc.id.display()}"}
            }
        }

    }

    /**
     * Convert an array of [ByteArray]s into a list of [ChordId]s
     */
    fun idListFromByteArrays(vararg byteArrays : ByteArray) : List<ChordId>{
        return byteArrays.map{
            ChordIdUtils.chordIdFromBytes(it)
        }
    }

    /**
     * Sort a list of ids in ascending order.
     */
    fun sortIds(ids : List<ChordId>) : List<ChordId>{
        return ids.sortedWith(Comparator<ChordId> { o1, o2 ->
            if(o1.contentEquals(o2)){
                0
            } else if(o1.greaterThan(o2)){
                1
            } else {
                -1
            }
        })
    }

    /**
     * Create a ring of started [ChordNode]s with the given [ChordId]s, such that each node will have the correct values
     * in its table to start with (and will continue to be correct unless joins or exits occur). Return all of the
     * created nodes.
     *
     * Note that for ease of calculating the tables the list will be sorted, even if it was not sorted to begin with.
     *
     * Also note that the addresses will not be hashable. Since we want to manually assign ids, and the hash function
     * we are using is not reversable, it will not actually be the case that the address + port of each ChordNode can
     * be hashed to its id.
     */
    fun createRingWithCorrectTables(ids: List<ChordId>, firstPort : Int = 12241): ChordCluster {
        val sortedIds = sortIds(ids)
        val addresses = sortedIds.mapIndexed{ind,it ->
            ChordAddressWrapper("localhost",firstPort + ind,it)
        }

        val resultTables = ArrayList<Array<ChordAddressWrapper>>()

        addresses.forEachIndexed{addrIndex, address ->
            val tableEntries = (0 until LENGTH).map{
                val mustPrecede = address.id.getTableItem(it)
                for(elt in 0 until LENGTH - 1){
                    val index = (elt + 1 + addrIndex) % ids.size
                    if(mustPrecede.isBetween(address.id,addresses[index].id)){
                        return@map addresses[index]
                    }
                }
                return@map address
            }.toTypedArray()
            resultTables.add(tableEntries)
        }

        val nodes = addresses.mapIndexed{ind,it ->
            ChordNode(it,resultTables[ind])
        }
        nodes.forEach { it.start() }
        return ChordCluster(nodes)
    }
}