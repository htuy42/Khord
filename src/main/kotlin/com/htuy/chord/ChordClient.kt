package com.htuy.chord

import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder

// TODO: currently has no understanding that calls might fail. Assumes target will never go down
/**
 * GRPC Client for connecting to other [ChordNode]s.
 */
class ChordClient(toAddr: ChordAddressWrapper) {

    // The channel and stub used to actually contact remote nodes.
    private val channel = ManagedChannelBuilder.forAddress(toAddr.addr, toAddr.port).usePlaintext().build()
    private val stub: ChordNodeGrpc.ChordNodeBlockingStub =
        ChordNodeGrpc.newBlockingStub(channel)

    /**
     * Call pred on the remote node we are connected to, then wrap and return the result.
     */
    fun getPred(): ChordAddressWrapper? {
        val response = stub.pred(Chord.PredRequest.getDefaultInstance())
        if (response.isMissing) {
            return null
        }
        return ChordAddressWrapper.fromChordAddress(response)
    }

    /**
     * Call notify on the remote node we are connected to, then wrap and return the result
     */
    fun notify(ofAddressWrapper: ChordAddressWrapper) {
        stub.notify(ofAddressWrapper.inner)
    }

    /**
     * Call findSuccessor on the remote node we are connected to, then wrap and return the result.
     */
    fun findSuccessor(of: ChordId): ChordAddressWrapper {
        val request = Chord.LookupRequest.newBuilder().addAllTarget(of.toList()).build()
        val response = stub.findSuccessor(request)
        return ChordAddressWrapper.fromChordAddress(response)
    }

    /**
     * Store the given value under the given id.
     */
    fun put(id: ChordId, value: ByteArray){
        val request = Chord.PutRequest.newBuilder().addAllTarget(id.toList()).setContent(ByteString.copyFrom(value)).build()
        stub.put(request)
    }

    /**
     * Get the value stored under the given id.
     */
    fun get(id: ChordId): ByteArray?{
        val response = stub.get(Chord.GetRequest.newBuilder().addAllTarget(id.toList()).build())
        return if(response.success){
            response.content.toByteArray()
        } else {
            null
        }
    }

    /**
     * Transfer the given keys and values to this Node.
     */
    fun transfer(keys : List<ChordId>, values: List<ByteArray>){
        val requestBuilder = Chord.TransferRequest.newBuilder()
        for(ind in keys.indices){
            requestBuilder.addValues(Chord.PutRequest.newBuilder().addAllTarget(keys[ind].toList()).setContent(
                ByteString.copyFrom(values[ind])))
        }
        stub.transfer(requestBuilder.build())
    }
}
