package com.htuy.zmq

fun main(args : Array<String>){
    val req = AwfulServer.PutRequest.newBuilder().setKey("dog").setValue("cat").build()
}