package com.htuy.chord

import io.grpc.ManagedChannelBuilder
import io.grpc.stub.AbstractBlockingStub

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
     * Call succ on the remote node we are connected to, then wrap and return the result.
     */
    fun getSucc(): ChordAddressWrapper {
        val response = stub.succ(Chord.SuccRequest.getDefaultInstance())
        return ChordAddressWrapper.fromChordAddress(response.succ)
    }

    /**
     * Call closestPrecedingFinger on the remote node we are connected to, then wrap and return the result.
     */
    fun getClosestPrecedingFinger(mustPrecede: ChordId): ChordAddressWrapper {
        val request = Chord.ClosestPrecedingFingerRequest.newBuilder().addAllTarget(mustPrecede.toList()).build()
        val response = stub.closestPrecedingFinger(request)
        return ChordAddressWrapper.fromChordAddress(response.fingerAddr)
    }
}
