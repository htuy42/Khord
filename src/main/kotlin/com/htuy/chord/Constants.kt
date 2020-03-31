package com.htuy.chord

import kotlin.math.pow

/**
 * The length of the boolean arrays that serve as IDs
 */
const val LENGTH = 32

/**
 * The number of possible ids: 2^[LENGTH]
 */
val M = 2.0.pow(LENGTH.toDouble()).toLong()