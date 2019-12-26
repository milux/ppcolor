package de.milux.ppcolor.ml.buckets

import de.milux.ppcolor.ml.buckets.HueBucketAlgorithm.N_BUCKETS
import kotlin.math.max

class BucketBuffer(private val bufferSize: Int) {
    private val buffer = Array(bufferSize) { DoubleArray(N_BUCKETS) }
    private val sums = DoubleArray(N_BUCKETS)
    private var bufferFill = 0
    private var bufferIndex = 0

    operator fun plusAssign(buckets: DoubleArray) {
        if (buckets.size != N_BUCKETS) {
            throw IllegalArgumentException("Array colors has invalid length ${buckets.size}!")
        }

        synchronized(this) {
            val lastBuckets = buffer[bufferIndex]
            buffer[bufferIndex] = buckets
            for (i in sums.indices) {
                sums[i] += buckets[i] - lastBuckets[i]
            }

            // Update bufferIndex
            bufferIndex = (bufferIndex + 1) % bufferSize
            // Adjusts bufferFill until it reaches BUFFER_SIZE - 1
            bufferFill = max(bufferFill, bufferIndex + 1)
        }
    }

    fun average(): DoubleArray {
        synchronized(this) {
            return sums.map { it / bufferFill }.toDoubleArray()
        }
    }

}