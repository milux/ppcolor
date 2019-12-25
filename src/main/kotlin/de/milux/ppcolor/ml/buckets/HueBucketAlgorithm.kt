package de.milux.ppcolor.ml.buckets

import de.milux.ppcolor.HuePoint
import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.TARGET_WEIGHT_THRESHOLD
import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.normHue
import org.slf4j.LoggerFactory
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
     * Since maximum hue distance is 0.5, a value of 2.0 means the whole circle influences the bucket.
     * A value of 4.0 means that at most half the circle (one quarter on either side) influences the bucket.
     */
    private const val DISTANCE_MULTIPLIER = 16.0
    /**
     * The threshold defining a "slope" of a cluster
     */
    private const val BORDER_THRESHOLD = 0.9
    /**
     * An additional factor to boost weakly represented colors
     */
    private const val WEAK_COLOR_BOOST = 16.0

    private lateinit var multiplyLookup: DoubleArray
    private val logger = LoggerFactory.getLogger(HueBucketAlgorithm::class.java)!!

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


    fun getDominantHueList(extBucketWeights: DoubleArray): FloatArray {
        // Calculate the extended bucket weights and pass them to the debug visualization
        DebugFrame.bucketWeights = extBucketWeights
        // Find clusters
        val targetWeight = extBucketWeights.sum() * TARGET_WEIGHT_THRESHOLD
        var collectedWeight = .0
        val blockedBuckets = BooleanArray(N_BUCKETS) { false }
        val clusters = LinkedList<BucketCluster>()
        while (collectedWeight < targetWeight && clusters.size < N_COLORS) {
            val bucketCluster = getCluster(extBucketWeights, blockedBuckets)
            if (bucketCluster == null) {
                break
            } else {
                clusters += bucketCluster
                collectedWeight += bucketCluster.weight
            }
        }
        DebugFrame.bucketClusters = clusters
        // TODO: Implement support for more than 2 colors here! (Use D'Hondt method?)
        return when {
            clusters.isEmpty() -> {
                FloatArray(0)
            }
            clusters.size < N_COLORS -> {
                listOf(clusters[0].leftBorder, clusters[0].rightBorder).toFloatArray()
            }
            else -> {
                clusters.map { it.center }.toFloatArray()
            }
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
        while (posWeight >= borderWeight || posWeight < prevWeight || exploreLeft(posLeft, bucketWeights)) {
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
        while (posWeight >= borderWeight || posWeight < prevWeight || exploreRight(posRight, bucketWeights)) {
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

    /**
     * Explores the left slope of a cluster for small rises to include.
     * No need to check for blocked buckets here, because blocked buckets automatically belong
     * to another cluster expanding until at most the position where "true" is returned
     */
    private fun exploreLeft(start: Int, bucketWeights: DoubleArray): Boolean {
        var prevWeight = bucketWeights[rightIndex(start + 1)]
        val peakWeight = prevWeight / BORDER_THRESHOLD
        var posLeft = start
        var posWeight = bucketWeights[posLeft]
        // If peakWeight is reached, this rise is meant to build an independent cluster, so return false
        while (posWeight < peakWeight) {
            // If values start to decline, we can expand until here, so return true
            if (posWeight < prevWeight) {
                return true
            }
            prevWeight = posWeight
            posLeft = leftIndex(posLeft - 1)
            posWeight = bucketWeights[posLeft]
        }
        return false
    }

    /**
     * Explores the right slope of a cluster for small rises to include.
     * No need to check for blocked buckets here, because blocked buckets automatically belong
     * to another cluster expanding until at most the position where "true" is returned
     */
    private fun exploreRight(start: Int, bucketWeights: DoubleArray): Boolean {
        var prevWeight = bucketWeights[leftIndex(start - 1)]
        val peakWeight = prevWeight / BORDER_THRESHOLD
        var posRight = start
        var posWeight = bucketWeights[posRight]
        // If peakWeight is reached, this rise is meant to build an independent cluster, so return false
        while (posWeight < peakWeight) {
            // If values start to decline, we can expand until here, so return true
            if (posWeight < prevWeight) {
                return true
            }
            prevWeight = posWeight
            posRight = leftIndex(posRight + 1)
            posWeight = bucketWeights[posRight]
        }
        return false
    }

    private fun getBucketWeights(hpList: List<HuePoint>): DoubleArray {
        val weights = DoubleArray(N_BUCKETS)
        hpList.forEach {
            val bucket = (normHue(it.hue) * N_BUCKETS).toInt()
            weights[bucket] += it.weight
        }
        val normFactor = 2.0 / weights.max()!!
        return weights.map { max(0.0, log2(it * normFactor * WEAK_COLOR_BOOST)) }.toDoubleArray()
    }

    fun getExtendedBucketWeights(hpList: List<HuePoint>): DoubleArray {
        val bucketWeights = getBucketWeights(hpList)
        val extWeights = DoubleArray(N_BUCKETS)
        for (i in extWeights.indices) {
            extWeights[i] = bucketWeights[i]
            for (bd in 1 until multiplyLookup.size) {
                val leftNeighbor = leftIndex(i - bd)
                val rightNeighbor = rightIndex(i + bd)
                extWeights[i] += (bucketWeights[leftNeighbor] + bucketWeights[rightNeighbor]) * multiplyLookup[bd]
            }
        }
        val smoothedWeights = extWeights.map { sqrt(it) }
        val maxWeight = extWeights.max()!!
        if (maxWeight.isNaN() || maxWeight == 0.0) {
            logger.error("Found maxWeight ${hpList.size}, which is an illegal state!", IllegalStateException())
            return smoothedWeights.toDoubleArray()
        }
        // Normalize weights before output
        return smoothedWeights.map { it / maxWeight }.toDoubleArray()
    }

    private fun leftIndex(i: Int): Int = (i + N_BUCKETS) % N_BUCKETS

    private fun rightIndex(i: Int): Int = i % N_BUCKETS
}