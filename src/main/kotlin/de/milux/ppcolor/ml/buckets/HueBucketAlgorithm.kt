package de.milux.ppcolor.ml.buckets

import de.milux.ppcolor.*
import de.milux.ppcolor.debug.DebugFrame
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sqrt

object HueBucketAlgorithm {
    /** The number of buckets used for this algorithm */
    const val N_BUCKETS = 512
    /**
     * Since maximum hue distance is 0.5, a value of 2.0 means the whole circle influences the bucket.
     * A value of 4.0 means that at most half the circle (one quarter on either side) influences the bucket.
     */
    private const val DISTANCE_MULTIPLIER = 16.0
    /** The threshold defining the maximum rise in a cluster slope */
    private const val BORDER_THRESHOLD = 0.95
    /** The threshold for the minimum weight that is encapsulated by the cluster borders */
    private const val MIN_INNER_WEIGHT = 0.7
    /** An additional factor to boost weakly represented colors */
    private const val WEAK_COLOR_BOOST = 8.0

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


    fun getHueClusters(extBucketWeights: DoubleArray): ClusteringResult {
        // Calculate the extended bucket weights and pass them to the debug visualization
        DebugFrame.bucketWeights = extBucketWeights
        // Find clusters
        val extBucketWeightSum = extBucketWeights.sum()
        val targetWeight = extBucketWeightSum * TARGET_WEIGHT_THRESHOLD
        var collectedWeight = .0
        val blockedBuckets = BooleanArray(N_BUCKETS) { false }
        val clusters = LinkedList<BucketCluster>()
        while (collectedWeight < targetWeight) {
            val bucketCluster = getCluster(extBucketWeights, blockedBuckets)
            if (bucketCluster == null) {
                break
            } else {
                clusters += bucketCluster
                collectedWeight += bucketCluster.weight
            }
        }
        logger.info("Weight confidence: ${collectedWeight / extBucketWeightSum}")
        DebugFrame.bucketClusters = clusters
        // TODO: Implement support for more than 2 colors here! (Use D'Hondt method?)
        val hueClusters = when {
            clusters.isEmpty() -> {
                emptyList()
            }
            clusters.size < N_COLORS -> {
                listOf(clusters[0].leftBorder, clusters[0].rightBorder)
                        .map { HueCluster(it, clusters[0].weight / extBucketWeightSum) }
            }
            else -> {
                clusters.map { HueCluster(it.center, it.weight / extBucketWeightSum) }
            }
        }
        return ClusteringResult(hueClusters, collectedWeight / extBucketWeightSum)
    }

    private fun getCluster(bucketWeights: DoubleArray, blockedBuckets: BooleanArray): BucketCluster? {
        var clusterWeight = .0
        var limitLeft = 0
        var limitRight = 0
        val maxBucket = bucketWeights
                .withIndex()
                .filter { !blockedBuckets[it.index] }
                .maxByOrNull { it.value } ?: return null
        // Black main bucket
        blockedBuckets[maxBucket.index] = true
        // Find left limit
        var pos = leftIndex(maxBucket.index - 1)
        var posWeight = bucketWeights[pos]
        var prevWeight = maxBucket.value
        while (posWeight < prevWeight || exploreLeft(pos, bucketWeights)) {
            if (blockedBuckets[pos]) {
                break
            }
            limitLeft = pos
            clusterWeight += posWeight
            blockedBuckets[pos] = true
            prevWeight = posWeight
            pos = leftIndex(pos - 1)
            posWeight = bucketWeights[pos]
        }
        limitLeft = leftIndex(limitLeft - 1)
        // Find right limit
        pos = rightIndex(maxBucket.index + 1)
        posWeight = bucketWeights[pos]
        prevWeight = maxBucket.value
        while (posWeight < prevWeight || exploreRight(pos, bucketWeights)) {
            if (blockedBuckets[pos]) {
                break
            }
            limitRight = pos
            clusterWeight += posWeight
            blockedBuckets[pos] = true
            prevWeight = posWeight
            pos = rightIndex(pos + 1)
            posWeight = bucketWeights[pos]
        }
        limitRight = rightIndex(limitRight + 1)
        // Find borders by collecting at least minWeight
        val minWeight = clusterWeight * MIN_INNER_WEIGHT
        var leftBorder = leftIndex(maxBucket.index - 1)
        var rightBorder = rightIndex(maxBucket.index + 1)
        var innerWeight = maxBucket.value
        while (innerWeight < minWeight) {
            if (leftBorder != limitLeft &&
                    (bucketWeights[leftBorder] >= bucketWeights[rightBorder] || rightBorder == limitRight)) {
                innerWeight += bucketWeights[leftBorder]
                leftBorder = leftIndex(leftBorder - 1)
            } else if (rightBorder != limitRight &&
                    (bucketWeights[rightBorder] > bucketWeights[leftBorder] || leftBorder == limitLeft)) {
                innerWeight += bucketWeights[rightBorder]
                rightBorder = rightIndex(rightBorder + 1)
            }
        }
        return BucketCluster(
                clusterWeight,
                leftBorder.toFloat() / N_BUCKETS,
                rightBorder.toFloat() / N_BUCKETS)
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
            if (it.sat >= MIN_SATURATION) {
                val bucket = (it.hue * N_BUCKETS).toInt()
                weights[bucket] += it.y
            }
        }
        val maxWeight = weights.maxOrNull() ?: throw IllegalStateException("weights must not be empty!")
        val normFactor = (2.0 / maxWeight) * WEAK_COLOR_BOOST
        return weights.map { max(0.0, log2(it * normFactor)) }.toDoubleArray()
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
        val maxWeight = extWeights.maxOrNull() ?: throw IllegalStateException("extWeights must not be empty!")
        if (maxWeight.isNaN() || maxWeight == 0.0) {
            logger.error("Found maxWeight $maxWeight, which is an illegal state!", IllegalStateException())
            return smoothedWeights.toDoubleArray()
        }
        // Normalize weights to range [0;1) before output
        return smoothedWeights.map { it / maxWeight }.toDoubleArray()
    }

    private fun leftIndex(i: Int): Int = (i + N_BUCKETS) % N_BUCKETS

    private fun rightIndex(i: Int): Int = i % N_BUCKETS
}