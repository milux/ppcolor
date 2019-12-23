package de.milux.ppcolor.ml.kmeans

import de.milux.ppcolor.HuePoint
import de.milux.ppcolor.MIN_CLUSTER_WEIGHT
import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.hueDistance
import de.milux.ppcolor.normHue
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

class HueKMeans(private val nColors: Int) {
    private val centers = FloatArray(nColors)
    private val adjustmentVectors = DoubleArray(nColors)
    private val adjustmentWeightSums = DoubleArray(nColors)
    private val clusterPoints = Array(nColors) { ArrayList<HuePoint>() }

    fun getKMeans(hpList: List<HuePoint>): FloatArray? {
        // Select samples as initial center values
        val totalWeight = hpList.sumByDouble { it.weight }
        for (i in 0 until nColors) {
            centers[i] = hpList[Random.nextInt(hpList.size)].hue
        }

        for (r in 1 .. 20) {
            adjustmentVectors.fill(.0)
            adjustmentWeightSums.fill(.0)
            for (i in clusterPoints.indices) {
                clusterPoints[i] = ArrayList()
            }
            for (hw in hpList) {
                val nearestCluster = findNearestCluster(hw.hue)
                clusterPoints[nearestCluster].add(hw)
                updateCluster(nearestCluster, hw)
            }
            if (adjustmentVectors.any { abs(it) > 1e-5 } ) {
                // Update center position
                adjustmentVectors.forEachIndexed { i, adjustmentVector ->
                    // Norm new center to hue range [0;1)
                    centers[i] = normHue(centers[i] + (adjustmentVector / adjustmentWeightSums[i]).toFloat())
                }
                val maxWeightSum = adjustmentWeightSums.withIndex().maxBy { it.value } ?: throw IllegalStateException()
                adjustmentWeightSums.forEachIndexed { i, w ->
                    if (w < MIN_CLUSTER_WEIGHT * totalWeight) {
                        val dominantClusterPoints = clusterPoints[maxWeightSum.index]
                        centers[i] = dominantClusterPoints[Random.nextInt(dominantClusterPoints.size)].hue
                    }
                }
            } else {
                break
            }
        }

        if (DebugFrame.logger.isDebugEnabled) {
            DebugFrame.kMeansResults = centers.filterIndexed { i, _ -> adjustmentWeightSums[i] > 0f }
        }

        // Replace empty cluster(s) with hue of strongest cluster
        val maxWeightSum = adjustmentWeightSums.withIndex().maxBy { it.value } ?: throw IllegalStateException()
        adjustmentWeightSums.forEachIndexed { i, w ->
            if (w < MIN_CLUSTER_WEIGHT * totalWeight) {
                centers[i] = centers[maxWeightSum.index]
            }
        }

        return centers
    }

    private fun updateCluster(clusterIndex: Int, hwPair: HuePoint) {
        val center = centers[clusterIndex]
        val diff = hwPair.hue - center
        val absDiff = abs(diff)
        if (absDiff <= 0.5f) {
            // No correction for cyclic structure required, just add weighted adjustments
            adjustmentVectors[clusterIndex] += diff * absDiff * hwPair.weight
        } else {
            // For correct update with cyclic "wrap-around",
            // subtract weighted "inverse" difference (-1 * (1 - abs(diff)))
            val invDiff = -sign(diff) * (1f - absDiff)
            adjustmentVectors[clusterIndex] -= invDiff * (1f - absDiff) * hwPair.weight
        }
        // Always update the adjustment weight sum
        adjustmentWeightSums[clusterIndex] += hwPair.weight
    }

    private fun findNearestCluster(hue: Float): Int {
        var min = Float.MAX_VALUE
        var nearest = -1
        for (i in 0 until nColors) {
            val dist = hueDistance(hue, centers[i])
            if (dist < min) {
                min = dist
                nearest = i
            }
        }
        return nearest
    }
}
