package com.htuy.chord

/**
 * Wraps a [Chord.ChordAddress], and provides utility functions over it.
 */
data class ChordAddressWrapper(
    val addr: String,
    val port: Int,
    val id: ChordId,
    val original: Chord.ChordAddress? = null
) {
    companion object {
        // Holds onto the created ChordClients so they can be accessed repeatedly even if we get different address
        // objects.
        private val clients = HashMap<ChordAddressWrapper, ChordClient>()

        /**
         * Create a wrapper from a given [Chord.ChordAddress]
         */
        fun fromChordAddress(address: Chord.ChordAddress): ChordAddressWrapper {
            if (address.isMissing) {
                throw IllegalArgumentException("Address was marked as empty / nil, cannot make a wrapper")
            }
            return ChordAddressWrapper(address.addr, address.port, address.idList.toBooleanArray(), address)
        }

        private fun clientFor(target: ChordAddressWrapper): ChordClient {
            return clients.getOrPut(target) {
                ChordClient(target)
            }
        }
    }

    /**
     * Get a [ChordClient] for this address. Multiple calls for the same / equivalent address will return
     * the same client.
     */
    fun clientFor(): ChordClient {
        return clientFor(this)
    }

    /**
     * The wrapped [Chord.ChordAddress]. If one was given at creation, returns that, otherwise lazily constructs
     * one based on the fields of this object.
     */
    val inner: Chord.ChordAddress by lazy {
        if (original != null) {
            original
        } else {
            val builder = Chord.ChordAddress.newBuilder()
            builder.addAllId(id.toList())
            builder.addr = addr
            builder.port = port
            builder.isMissing = false
            builder.build()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChordAddressWrapper

        if (addr != other.addr) return false
        if (port != other.port) return false

        return true
    }

    override fun hashCode(): Int {
        var result = addr.hashCode()
        result = 31 * result + port
        return result
    }

    override fun toString(): String {
        return id.display()
    }
}