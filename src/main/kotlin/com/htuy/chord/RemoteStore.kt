package com.htuy.chord

import com.htuy.concurrent.TimeoutMap

/**
 * Holds onto objects that have been sent to a [ChordNode]. Objects have to be refreshed periodically or they are
 * dropped.
 */
class RemoteStore(val node : ChordNode){

    internal data class HashableId(val id : ChordId){
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HashableId

            if (!id.contentEquals(other.id)) return false

            return true
        }
        override fun hashCode(): Int {
            return id.contentHashCode()
        }

    }

    /**
     * Where we keep the values. When they time out they are just dropped.
     */
    internal val store = TimeoutMap<HashableId,ByteArray>(MS_BEFORE_VAL_REMOVED_FROM_STORE)

    protected fun finalize(){
        store.stop()
    }

    /**
     * Store the given [value] here under [id]. It will be removed if it is not put again eventually.
     */
    fun put(id: ChordId, value: ByteArray){
        store.put(HashableId(id),value)
    }

    /**
     * If the given id is stored here, returns it.
     */
    fun get(id: ChordId): ByteArray?{
        return store[HashableId(id)]
    }

    /**
     * When our node gets a new predecessor, some of the values may be moved.
     */
    fun newPredecessor(other: ChordAddressWrapper){
        val allEntries = store.entries.toList()
        val toSendKeys = ArrayList<ChordId>()
        val toSendValues = ArrayList<ByteArray>()
        for(entry in allEntries){
            if(other.id.isBetween(entry.key.id,node.ownAddr.id,false,true)){
                toSendKeys.add(entry.key.id)
                toSendValues.add(entry.value)
            }
        }
        other.clientFor().transfer(toSendKeys,toSendValues)
    }


}