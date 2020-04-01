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
                assert(foundSucc.id.contentEquals(expectedHost)){
                    println("At $ind")
                    for(node in nodes){
                        println("Table of ${node.ownAddr.id.display()}")
                        for(i in 0 until LENGTH){
                            println(node.table[i].id.display())
                        }
                        println("\n")
                    }
                    "Expected to find ${toLookFor.display()} at ${expectedHost.display()}, but found it at ${foundSucc.id.display()}"}
            }
        }

        fun verifyTables(){
            val sortedIds = sortIds(nodes.map { it.ownAddr.id })
            val sortedNodes = nodes.sortedWith(Comparator<ChordNode> { o1, o2 ->
                if(o1.ownAddr.id.contentEquals(o2.ownAddr.id)){
                    0
                } else if(o1.ownAddr.id.greaterThan(o2.ownAddr.id)){
                    1
                } else {
                    -1
                }
            })
            val correctTableNodes = createRingWithCorrectTables(sortedIds,start=false)
            for(ind in sortedNodes.indices){
                val node = sortedNodes[ind]
                val correctNode = correctTableNodes.nodes[ind]
                for(row in 0 until LENGTH){
                    assert(node.table[row].id.contentEquals(correctNode.table[row].id)){
                        println("Node:")
                        println(node.ownAddr.id.display())
                        println("Had entry:")
                        println(node.table[row].id.display())
                        println("Expected entry")
                        println(correctNode.table[row].id.display())
                        println("Successor looking for")
                        println(node.ownAddr.id.getTableItem(row).display())
                        println("all entries")
                        for(otherRow in 0 until LENGTH){
                            println(node.table[otherRow].id.display())
                        }
                        println("row num")
                        println(row)
                        println("ids")
                        for(node in nodes){
                            println(node.ownAddr.id.display())
                        }
                        "Node had incorrect table entry."
                    }

                }
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
    fun createRingWithCorrectTables(ids: List<ChordId>, firstPort : Int = 12241, start: Boolean = true): ChordCluster {
        val sortedIds = sortIds(ids)
        val addresses = sortedIds.mapIndexed{ind,it ->
            ChordAddressWrapper("localhost",firstPort + ind,it)
        }

        val resultTables = ArrayList<Array<ChordAddressWrapper>>()

        addresses.forEachIndexed{addrIndex, address ->
            val tableEntries = (0 until LENGTH).map{
                val mustPrecede = address.id.getTableItem(it)
                for(elt in 0 until ids.size - 1){
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
        if(start) {
            nodes.forEach { it.start() }
        }
        return ChordCluster(nodes)
    }

    /**
     * Create a ring of started [ChordNode]s with the given ids, by creating one and then repeatedly having new ones
     * call join on it. Return all of the created nodes.
     */
    fun createRingWithIdsByJoin(ids: List<ChordId>, firstPort: Int = 12241) : ChordCluster{
        val nodes = ArrayList<ChordNode>()
        val firstNode = ChordNode(ChordAddressWrapper("localhost",firstPort,ids[0]))
        firstNode.joinNetwork(null)
        nodes.add(firstNode)
        for(i in 1 until ids.size){
            val newNode = ChordNode(ChordAddressWrapper("localhost",firstPort + i, ids[i]))
            newNode.joinNetwork(nodes[i-1].ownAddr)
            nodes.add(newNode)
        }
        return ChordCluster(nodes)
    }
}