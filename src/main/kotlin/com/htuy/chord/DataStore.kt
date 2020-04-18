package com.htuy.chord

import com.htuy.concurrent.TimeoutMap

/**
 * The "application": values can be stored and fetched.
 * A node's address is necessary to perform lookups.
 * [redundancy] determines how many times each key is stored in the network (with different salt each time).
 * Redundancy has significant cost (doubling it won't quite double the system cost, but it will be reasonably close).
 * With a large enough network, ids will in expectation be stored at around [redundancy] nodes, but in smaller ones
 * this won't always be true due to the nature of Chord.
 */
class DataStore(val addr: ChordAddressWrapper, val redundancy : Int = 1){
    /**
     * The values stored here. When an entry times out, it is re-put
     */
    private val internal = TimeoutMap(MS_BEFORE_VAL_REMOVED_FROM_STORE / 3, name="ds",handler=this::chordStoreValue)

    /**
     * Store the given [value] under the given [id].
     */
    fun store(id : String, value: ByteArray){
        for(i in 0 until redundancy){
            val chordId = ChordIdUtils.chordIdFromStringSalted(id,i)
            chordStoreValue(chordId,value)
        }
    }

    /**
     * Attempt to find the given [id] in the network. On failed lookups, will retry up to [retries] times.
     */
    fun get(id: String, retries: Int = 3, retryDelay: Long = 500): ByteArray?{
        for(i in 0 until retries) {
            val fromInternal = internal[ChordIdUtils.chordIdFromStringSalted(id,0)]
            if (fromInternal != null) {
                return fromInternal
            } else {
                // Retries at each node that might contain the value counts as a single "retry"
                for(j in 0 until redundancy){
                    val saltedId = ChordIdUtils.chordIdFromStringSalted(id,j)
                    val location = addr.clientFor().findSuccessor(saltedId)
                    val remoteObject = location.clientFor().get(saltedId)
                    if(remoteObject != null){
                        return remoteObject
                    }
                }
                Thread.sleep(retryDelay)
            }
        }
        return null
    }

    /**
     * Store a value into chord.
     */
    private fun chordStoreValue(id : ChordId, value: ByteArray){
        val location = addr.clientFor().findSuccessor(id)
        location.clientFor().put(id,value)
        internal.put(id,value)
    }

    /**
     * The internal map needs to be stopped, since it has a running thread in it and no way to know it needs to end
     * otherwise.
     */
    protected fun finalize(){
        internal.stop()
    }
}