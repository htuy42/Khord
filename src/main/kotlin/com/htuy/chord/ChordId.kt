package com.htuy.chord

import com.google.common.hash.Hashing
import java.lang.StringBuilder
import java.nio.charset.Charset
import java.util.*

typealias ChordId = BooleanArray

/**
 * Utilities for creating [ChordId] instances.
 */
object ChordIdUtils{
    private val hasher = Hashing.sha256()
    private fun bytesToBools(bytes : ByteArray) : ChordId{
        val bitSet = BitSet.valueOf(bytes)
        return BooleanArray(LENGTH){
            bitSet[it]
        }
    }

    /**
     * Create a [ChordId] for the given string.
     */
    fun chordIdFromString(from : String) : ChordId{
        return bytesToBools(hasher.hashString(from, Charset.defaultCharset()).asBytes())
    }

    /**
     * Create a [ChordId] from the given address and port (by concatenating them and then treating them as a string).
     */
    fun chordIdFromAddrAndPort(addr : String, port : Int) : ChordId{
        return chordIdFromString("$addr:$port")
    }

    /**
     * Create a [ChordId] from a [ByteArray]. For testing purposes.
     */
    fun chordIdFromBytes(b : ByteArray) : ChordId{
        return bytesToBools(b)
    }
}


/**
 * Whether this [ChordId] is greater than the [other], treating the two ids as little endian integers and directly
 * comparing. Tie -> false.
 */
fun ChordId.greaterThan(other : ChordId) : Boolean{
    for(i in LENGTH - 1 downTo 0){
        if(this[i] != other[i]){
            return this[i]
        }
    }
    return false
}


/**
 * Whether this [ChordId] is between the ids of [a] and [b], allowing for wrapping (so ie if [ChordId] is greater than a and
 * a is greater than b, then this returns true.
 * [endInclusive] will be returned when b == this
 */
fun ChordId.isBetween(a : ChordId, b : ChordId, endInclusive: Boolean = true) : Boolean {
    if(this.contentEquals(a)){
        return false
    } else if(this.contentEquals(b)){
        return endInclusive
    }
    return if (b.greaterThan(a)) {
        !this.greaterThan(b) && this.greaterThan(a)
    } else {
        !this.greaterThan(b) || this.greaterThan(a)
    }
}

/**
 * Get the ID that the element in the [tableRow]th row of the [ChordNode] with this ids table must be after.
 */
fun ChordId.getTableItem(tableRow : Int) : ChordId{
    var index = tableRow
    val new = this.clone()
    while(index < LENGTH && this[index]){
        new[index] = false
        index += 1
    }
    if(index < LENGTH){
        new[index] = true
    }
    return new
}

/**
 * A human readable display of a [ChordId]
 */
fun ChordId.display() : String{
    var lastInd = 0
    for(ind in this.indices){
        if(this[ind]){
            lastInd = ind
        }
    }
    val result = StringBuilder()
    for(ind in 0..lastInd){
        result.append(if(this[ind]){"1"}else{"0"})
    }
    return result.toString()
}