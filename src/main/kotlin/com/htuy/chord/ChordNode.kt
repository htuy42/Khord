package com.htuy.chord

import com.google.protobuf.ByteString
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * A single node in the chord ring. Acts as a Chord server and also supplies the application with access methods to
 * the ring.
 */
class ChordNode(
    val ownAddr: ChordAddressWrapper,
    givenAddrs: Array<ChordAddressWrapper> = arrayOf(),
    val updateFrequency: Long = UPDATE_RATE
) : ChordNodeGrpc.ChordNodeImplBase() {

    // Stores objects that other nodes have placed here.
    private val remoteObjects = RemoteStore(this)

    // The server this node uses to service GRPC requests from other [ChordClient]s
    private val server: Server

    // Our current predecessor in the ring
    private var predecessor: ChordAddressWrapper? = null

     // Lock for the [predecessor] field.
    private val predLock = ReentrantLock()

    // The next finger to attempt to correct in fixFingers
    private var nextFingerToCheck = 1


    // Construct the server, but do not start it
    init {
        val serverBuilder = ServerBuilder.forPort(ownAddr.port)
        server = serverBuilder.addService(this).build()
    }


    // Table values are initialized to this if they aren't otherwise given in givenAddrs
    internal val table = Array(LENGTH) {
        if (givenAddrs.size <= it) {
            ownAddr
        } else {
            givenAddrs[it]
        }
    }

    /**
     * Join the network the given node is a member of. Calls [start].
     */
    fun joinNetwork(otherNode: ChordAddressWrapper?) {
        if (otherNode == null) {
            start()
            startStabilizeAndFix()
            return
        } else {
            table[0] = otherNode.clientFor().findSuccessor(ownAddr.id)
            initFingerTable()
            start()
            startStabilizeAndFix()
        }
    }


    /**
     * Initialize our finger table by looking up its entries at a node that is already in the network.
     */
    private fun initFingerTable() {
        for (ind in 1 until LENGTH) {
            val tableItem = ownAddr.id.getTableItem(ind)
            table[ind] = findSuccessor(tableItem)
        }
    }

    /**
     * Begin a background process to run [stabilize] and [fixFingers] every [updateFrequency]
     */
    private fun startStabilizeAndFix() {
        thread {
            while (!server.isShutdown) {
                stabilize()
                fixFingers()
                checkPred()
                Thread.sleep(updateFrequency)
            }
        }
    }

    /**
     * Confirm that our successor knows about us and that we have the correct successor.
     */
    fun stabilize() {
        val x = table[0].clientFor().getPred()
        if (x != null) {
            synchronized(table) {
                if (x.id.isBetween(ownAddr.id, table[0].id, false, false) || table[0].id.contentEquals(ownAddr.id)) {
                    table[0] = x
                }
            }
        }
        table[0].clientFor().notify(ownAddr)
    }

    /**
     * We are being notified of a node that thinks it might be our predeccesor. Add it if appropriate.
     */
    override fun notify(request: Chord.ChordAddress, responseObserver: StreamObserver<Chord.NotifyResponse>) {
        val wrappedAddress = ChordAddressWrapper.fromChordAddress(request)
        var changedPred = false
        synchronized(predLock) {
            if (predecessor == null || wrappedAddress.id.isBetween(predecessor!!.id, ownAddr.id)) {
                if (!wrappedAddress.id.contentEquals(ownAddr.id)) {
                    predecessor = wrappedAddress
                    changedPred = true
                }
            }
        }
        respondTo(Chord.NotifyResponse.getDefaultInstance(), responseObserver)
        if(changedPred){
            val newPred = predecessor
            if(newPred != null){
                remoteObjects.newPredecessor(newPred)
            }
        }
    }

    override fun put(request: Chord.PutRequest, responseObserver: StreamObserver<Chord.PutResponse>) {
        remoteObjects.put(request.targetList.toBooleanArray(), request.content.toByteArray())
        respondTo(Chord.PutResponse.newBuilder().setSuccess(true).build(), responseObserver)
    }

    override fun get(request: Chord.GetRequest, responseObserver: StreamObserver<Chord.GetResponse>) {
        val storedValue = remoteObjects.get(request.targetList.toBooleanArray())
        if (storedValue == null) {
            respondTo(Chord.GetResponse.newBuilder().setSuccess(false).build(), responseObserver)
        } else {
            respondTo(
                Chord.GetResponse.newBuilder().setSuccess(true).setContent(ByteString.copyFrom(storedValue)).build(),
                responseObserver
            )
        }
    }

    override fun transfer(request: Chord.TransferRequest, responseObserver: StreamObserver<Chord.TransferResponse>) {
        for (subRequest in request.valuesList) {
            remoteObjects.put(subRequest.targetList.toBooleanArray(), subRequest.content.toByteArray())
        }
        respondTo(Chord.TransferResponse.getDefaultInstance(), responseObserver)
    }

    /**
     * Called occasionally, fixes one of our fingers to improve routing.
     */
    fun fixFingers() {
        nextFingerToCheck += 1
        nextFingerToCheck %= LENGTH
        val toCheck = ownAddr.id.getTableItem(nextFingerToCheck)

        val correctAdr = findSuccessor(toCheck)

        synchronized(table) {
            table[nextFingerToCheck] = correctAdr
        }

    }

    /**
     * Called occasionally, checks whether our pred has failed.
     *
     * NOTE: not currently doing anything, to be implemented.
     */
    fun checkPred() {
        val toCheck = predecessor
        if (toCheck != null) {
            // TODO check if pred has failed and null it if it has.
        }
    }


    /**
     * Get the finger in our table that is closest to [ofId] while also preceding it.
     */
    private fun closestPrecedingFinger(ofId: ChordId): ChordAddressWrapper {
        for (i in LENGTH - 1 downTo 0) {
            synchronized(table) {
                if (!table[i].id.contentEquals(ownAddr.id)) {
                    if (table[i].id.isBetween(ownAddr.id, ofId, false, false)) {
                        return table[i]
                    }
                }
            }
        }
        return ownAddr
    }

    /**
     * Perform a call to our own findSuccessor routine for another node, generally in the case that the other node is
     * joining and cannot do this call on its own.
     */
    override fun findSuccessor(request: Chord.LookupRequest, responseObserver: StreamObserver<Chord.ChordAddress>) {
        val response = findSuccessor(request.targetList.toBooleanArray())
        respondTo(response.inner, responseObserver)
    }

    /**
     * Return our successor, or equivalently `table[0]`
     */
    override fun succ(request: Chord.SuccRequest, responseObserver: StreamObserver<Chord.ChordAddress>) {
        respondTo(table[0].inner, responseObserver)
    }

    /**
     * Returns our current [predecessor].
     */
    override fun pred(request: Chord.PredRequest, responseObserver: StreamObserver<Chord.ChordAddress>) {
        respondTo(predecessor?.inner ?: Chord.ChordAddress.newBuilder().setIsMissing(true).build(), responseObserver)
    }

    /**
     * Find the successor in the ring of the given [ChordId]. Checks if we are the predecessor, in which case returns
     * our successor. Otherwise finds the node we know of most likely to be the predecessor and has it attempt to find
     * the successor.
     */
    fun findSuccessor(target: ChordId): ChordAddressWrapper {
        synchronized(table) {
            if (target.isBetween(ownAddr.id, table[0].id, true, false)) {
                return table[0]
            }
        }
        val nPrime = closestPrecedingFinger(target)
        if (nPrime.id.contentEquals(ownAddr.id)) {
            return ownAddr
        }
        return nPrime.clientFor().findSuccessor(target)
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
    fun stop() {
        server.shutdownNow()
        server.awaitTermination()
    }

}