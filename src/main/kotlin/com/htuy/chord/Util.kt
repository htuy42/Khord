package com.htuy.chord

import io.grpc.stub.StreamObserver

/**
 * [StreamObserver] utility for responding to a request and completing the [StreamObserver].
 */
fun <T> respondTo(response: T, observer: StreamObserver<T>) {
    observer.onNext(response)
    observer.onCompleted()
}