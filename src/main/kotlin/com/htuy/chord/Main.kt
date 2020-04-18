package com.htuy.chord

import com.htuy.common.SystemUtilies
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.retry.RetryNTimes
import java.io.File


/**
 * Start a [ChordNode].
 */
fun main(args: Array<String>) {
    val sleepMsBetweenRetries = 1000
    val maxRetries = 4
    val retryPolicy: RetryPolicy = RetryNTimes(
        maxRetries, sleepMsBetweenRetries
    )
    val client = CuratorFrameworkFactory
        .newClient("zoo1:2181", retryPolicy)
    client.start()
    val join = args[0] == "true"
    if(args.size == 2){
        test(client)
        return
    }
    val idstr = File("/etc/hostname").readText().strip()
    val ownId = ChordIdUtils.chordIdFromString(idstr)
    if(join){
        joinNetwork(client,ownId)
    } else {
        createNetwork(client,ownId,idstr)
    }
}

fun test(client:CuratorFramework){
    Thread.sleep(500)
    val hostAddr = client.children.forPath(HOST_ADDR_LOCATION)[0]
    val hostId = client.children.forPath(HOST_ID_LOCATION)[0]
    val hostWrapper = ChordAddressWrapper(hostAddr, PORT,ChordIdUtils.chordIdFromString(hostId))
    val store = DataStore(hostWrapper)
    for(i in 0..10){
        store.store("$i","$i".toByteArray())
    }
    val otherStore = DataStore(hostWrapper)
    Thread.sleep(5000)
    for(i in 0..10){
        val fetched = otherStore.get("$i")
        assert(fetched!!.contentEquals("$i".toByteArray()))
    }
}

fun joinNetwork(client : CuratorFramework, id:ChordId){
    val localIp = System.getenv("POD_IP")
    val localNode = ChordNode(ChordAddressWrapper(localIp,PORT,id))
    val hostAddr = client.children.forPath(HOST_ADDR_LOCATION)[0]
    val hostId = client.children.forPath(HOST_ID_LOCATION)[0]
    val hostWrapper = ChordAddressWrapper(hostAddr, PORT,ChordIdUtils.chordIdFromString(hostId))
    localNode.joinNetwork(hostWrapper)
    Thread.currentThread().join()
}


fun createNetwork(client: CuratorFramework, id:ChordId, idStr: String){

    val resultIp = System.getenv("POD_IP")
    val localNode = ChordNode(ChordAddressWrapper(resultIp,PORT,id))
    client.create().creatingParentsIfNeeded().forPath(HOST_ADDR_LOCATION+"/$resultIp")
    client.create().creatingParentsIfNeeded().forPath(HOST_ID_LOCATION+"/$idStr")
    localNode.joinNetwork(null)
    Thread.currentThread().join()
}
