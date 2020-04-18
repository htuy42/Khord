package com.htuy.chord

import kotlin.math.pow

/**
 * The length of the boolean arrays that serve as IDs
 */
const val LENGTH = 32

const val UPDATE_RATE = 250L

/**
 * The number of possible ids: 2^[LENGTH]
 */
val M = 2.0.pow(LENGTH.toDouble()).toLong()

/**
 * How long values remain in the [DataStore] before being dropped if they aren't reput
 */
const val MS_BEFORE_VAL_REMOVED_FROM_STORE = 15000L

const val PORT = 22415
const val HOST_ADDR_LOCATION = "/hostaddr"

const val HOST_ID_LOCATION = "/hostid"