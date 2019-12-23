package de.milux.ppcolor.ml.buckets

import de.milux.ppcolor.HuePoint
import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.TARGET_WEIGHT_THRESHOLD
import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.normHue
import java.util.*
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sqrt

object HueBucketAlgorithm {
    /**
     * The number of buckets used for this algorithm
     */
    const val N_BUCKETS = 512
    /**
     * Since maximum hue distance is 0.5, a value of 2 means the whole circle influences the bucket.
     * A value of 4.0 means that at most half the circle (one quarter on either side) influences the bucket.
     */
    private const val DISTANCE_MULTIPLIER = 15.0
    /**
     * The threshold defining the "slope" of a cluster
     */
    private const val BORDER_THRESHOLD = 0.9

    private lateinit var multiplyLookup: DoubleArray

    init {
        val distanceDivisor = N_BUCKETS.toDouble() / DISTANCE_MULTIPLIER
        val multiplyLookup = DoubleArray(N_BUCKETS / 2)
        for (bd in 0 .. N_BUCKETS / 2) {
            val hueDistance = bd.toDouble() / distanceDivisor
            // A value bigger or equal to 1.0 is interpreted as infinite distance, this distances are not used
            if (hueDistance >= 1.0) {
                HueBucketAlgorithm.multiplyLookup = multiplyLookup.copyOfRange(0, bd)
                break
            }
            val inverseDistance = 1.0 - hueDistance
            multiplyLookup[bd] = inverseDistance * inverseDistance
        }
    }


    fun getDominantHueList(hpList: List<HuePoint>): FloatArray {
        // Calculate the total bucket weights and pass them to the debug visualization
        val bucketWeights = getBucketWeights(hpList)
        val totalBucketWeights = getTotalBucketWeights(bucketWeights)
        DebugFrame.bucketWeights = totalBucketWeights
        // Find clusters
        val targetWeight = totalBucketWeights.sum() * TARGET_WEIGHT_THRESHOLD
        var collectedWeight = .0
        val blockedBuckets = BooleanArray(N_BUCKETS) { false }
        val clusters = LinkedList<BucketCluster>()
        while (collectedWeight < targetWeight && clusters.size < N_COLORS) {
            val bucketCluster = getCluster(totalBucketWeights, blockedBuckets)
            if (bucketCluster == null) {
                break
            } else {
                clusters += bucketCluster
                collectedWeight += bucketCluster.weight
            }
        }
        DebugFrame.bucketClusters = clusters
        // TODO: Implement support for more than 2 colors here! (Use D'Hondt method?)
        return if (clusters.size < N_COLORS) {
            listOf(clusters[0].leftBorder, clusters[0].rightBorder).toFloatArray()
        } else {
            clusters.map { it.center }.toFloatArray()
        }
    }

    private fun getCluster(bucketWeights: DoubleArray, blockedBuckets: BooleanArray): BucketCluster? {
        var clusterWeight = .0
        var clusterBorderLeft: Float? = null
        var clusterBorderRight: Float? = null
        val maxBucket = bucketWeights
                .withIndex()
                .filter { !blockedBuckets[it.index] }
                .maxBy { it.value } ?: return null
        // Black main bucket
        blockedBuckets[maxBucket.index] = true
        // Find left border and block whole slope
        val borderWeight = maxBucket.value * BORDER_THRESHOLD
        var posLeft = leftIndex(maxBucket.index - 1)
        var posWeight = bucketWeights[posLeft]
        var prevWeight = bucketWeights[maxBucket.index]
        while (posWeight >= borderWeight || posWeight < prevWeight) {
            if (clusterBorderLeft == null && posWeight < borderWeight || blockedBuckets[posLeft]) {
                clusterBorderLeft = rightIndex(posLeft + 1).toFloat() / N_BUCKETS
            }
            if (blockedBuckets[posLeft]) {
                break
            }
            clusterWeight += posWeight
            blockedBuckets[posLeft] = true
            prevWeight = posWeight
            posLeft = leftIndex(posLeft - 1)
            posWeight = bucketWeights[posLeft]
        }
        // Find right border
        var posRight = rightIndex(maxBucket.index + 1)
        posWeight = bucketWeights[posRight]
        prevWeight = bucketWeights[maxBucket.index]
        while (posWeight >= borderWeight || posWeight < prevWeight) {
            if (clusterBorderRight == null && posWeight < borderWeight || blockedBuckets[posRight]) {
                clusterBorderRight = leftIndex(posRight - 1).toFloat() / N_BUCKETS
            }
            if (blockedBuckets[posRight]) {
                break
            }
            clusterWeight += posWeight
            blockedBuckets[posRight] = true
            prevWeight = posWeight
            posRight = rightIndex(posRight + 1)
            posWeight = bucketWeights[posRight]
        }
        return BucketCluster(clusterWeight, clusterBorderLeft!!, clusterBorderRight!!)
    }

    private fun getBucketWeights(hpList: List<HuePoint>): DoubleArray {
        val weights = DoubleArray(N_BUCKETS)
        hpList.forEach {
            val bucket = (normHue(it.hue) * N_BUCKETS).toInt()
            weights[bucket] += it.weight
        }
        return weights.map { max(.0, log2(it * 50)) }.toDoubleArray()
    }

    private fun getTotalBucketWeights(bucketWeights: DoubleArray): DoubleArray {
        val totalWeights = DoubleArray(N_BUCKETS)
        for (i in totalWeights.indices) {
            totalWeights[i] = bucketWeights[i]
            for (bd in 1 until multiplyLookup.size) {
                val leftNeighbor = leftIndex(i - bd)
                val rightNeighbor = rightIndex(i + bd)
                totalWeights[i] += (bucketWeights[leftNeighbor] + bucketWeights[rightNeighbor]) * multiplyLookup[bd]
            }
        }
        return totalWeights.map { sqrt(it) }.toDoubleArray()
    }

    private fun leftIndex(i: Int): Int = (i + N_BUCKETS) % N_BUCKETS

    private fun rightIndex(i: Int): Int = i % N_BUCKETS
}