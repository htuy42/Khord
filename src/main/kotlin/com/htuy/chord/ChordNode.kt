package com.htuy.chord

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver

/**
 * A single node in the chord ring. Acts as a Chord server and also supplies the application with access methods to
 * the ring.
 */
class ChordNode(val ownAddr: ChordAddressWrapper, val givenAddrs: Array<ChordAddressWrapper>) :
    ChordNodeGrpc.ChordNodeImplBase() {

    // The server this node uses to service GRPC requests from other [ChordClient]s
    private val server: Server

    // Construct the server, but do not start it
    init {
        val serverBuilder = ServerBuilder.forPort(ownAddr.port)
        server = serverBuilder.addService(this).build()
    }

    // Table values are initialized to this if they aren't otherwise given in givenAddrs
    private val table = Array(LENGTH) {
        if (givenAddrs.size <= it) {
            ownAddr
        } else {
            givenAddrs[it]
        }
    }

    /**
     * Find the largest entry in our finger table that precedes [request#target].
     */
    override fun closestPrecedingFinger(
        request: Chord.ClosestPrecedingFingerRequest,
        responseObserver: StreamObserver<Chord.ClosestPrecedingFingerResponse>
    ) {
        println("I am ${ownAddr.id.display()}")
        println("Got request ${request.targetList.toBooleanArray().display()}")
        var best = ownAddr
        val targetId = request.targetList.toBooleanArray()
        for (i in LENGTH - 1 downTo 0) {
            if (table[i].id.isBetween(ownAddr.id, targetId,false)) {
                best = table[i]
                break
            }
        }
        println("I respond ${best.id.display()}")
        respondTo(Chord.ClosestPrecedingFingerResponse.newBuilder().setFingerAddr(best.inner).build(),responseObserver)
    }

    /**
     * Return our successor, or equivalently `table[0]`
     */
    override fun succ(request: Chord.SuccRequest, responseObserver: StreamObserver<Chord.SuccResponse>) {
        respondTo(Chord.SuccResponse.newBuilder().setSucc(table[0].inner).build(),responseObserver)
    }

    /**
     * Find the successor in the ring of the given [ChordId], by calling [findPredecessor] and then getting the
     * successor of the result.
     */
    fun findSuccessor(target: ChordId): ChordAddressWrapper {
        return findPredecessor(target).clientFor().getSucc()
    }

    /**
     * Find the predecessor in the ring of the given [ChordId].
     */
    fun findPredecessor(target: ChordId): ChordAddressWrapper {
        var nPrime = ownAddr
        while (!target.isBetween(nPrime.id, nPrime.clientFor().getSucc().id)) {
            nPrime = nPrime.clientFor().getClosestPrecedingFinger(target)
        }
        return nPrime
    }

    /**
     * Start the internal server.
     */
    fun start() {
        server.start()
    }

    /**
     * Stop the internal server. Does not return until it has terminated.
     */
    fun stop(){
        server.shutdownNow()
        server.awaitTermination()
    }

}