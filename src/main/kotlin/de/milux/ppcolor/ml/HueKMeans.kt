package de.milux.ppcolor.ml

import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.SHOW_DEBUG_FRAME
import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.normHue
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class HueKMeans {
    private val centers = FloatArray(N_COLORS)
    private val adjustmentSums = FloatArray(N_COLORS)
    private val adjustmentWeightSums = FloatArray(N_COLORS)

    fun getKMeans(hwList: List<HuePoint>): FloatArray? {
        // Select samples as initial center values
        val initList = hwList.filter { it.weight > 0.1 }
        if (initList.isEmpty()) {
            return null
        }
        for (i in 0 until N_COLORS) {
            centers[i] = initList[Random.nextInt(initList.size)].hue
        }

        for (r in 1 .. 20) {
            adjustmentSums.fill(0f)
            adjustmentWeightSums.fill(0f)
            for (hw in hwList) {
                val nearestCluster = findNearestCluster(hw.hue)
                updateCluster(nearestCluster, hw)
            }
            val newCenters = getNewCenters()
            if (adjustmentSums.any { abs(it) > 1e-5 }) {
                newCenters.copyInto(centers)
            } else {
                break
            }
        }

        if (SHOW_DEBUG_FRAME) {
            DebugFrame.kMeansResults = centers.filterIndexed { i, _ -> adjustmentWeightSums[i] > 0f }
        }

        // Replace empty cluster(s) with hue of strongest cluster
        adjustmentWeightSums.forEachIndexed { i, w ->
            if (w == 0f) {
                val maxWeightSum = adjustmentWeightSums.withIndex().maxBy { it.value } ?: return null
                centers[i] = centers[maxWeightSum.index]
            }
        }

        return centers
    }

    private fun getNewCenters(): FloatArray {
        val newCenters = centers.copyOf()
        for (i in 0 until N_COLORS) {
            // Update center position
            val adjustmentVector = adjustmentSums[i] / adjustmentWeightSums[i]
            if (!adjustmentVector.isNaN()) {
                newCenters[i] += adjustmentVector
                // Norm center to hue range [0;1)
                newCenters[i] = normHue(newCenters[i])
            }
        }
        return newCenters
    }

    private fun updateCluster(clusterIndex: Int, hwPair: HuePoint) {
        val center = centers[clusterIndex]
        val diff = hwPair.hue - center
        val absDiff = abs(diff)
        if (absDiff <= 0.5f) {
            // No correction for cyclic structure required, just add weighted adjustments
            adjustmentSums[clusterIndex] += diff * hwPair.weight.toFloat()
        } else {
            // For correct update with cyclic "wrap-around",
            // subtract weighted "inverse" difference (-1 * (1 - abs(diff)))
            val invDiff = - Math.signum(diff) * (1f - absDiff)
            adjustmentSums[clusterIndex] -= invDiff * hwPair.weight.toFloat()
        }
        // Always update the adjustment weight sum
        adjustmentWeightSums[clusterIndex] += hwPair.weight.toFloat()
    }

    private fun findNearestCluster(hue: Float): Int {
        var min = Float.MAX_VALUE
        var nearest = -1
        for (i in 0 until N_COLORS) {
            val dist = cyclicDistance(hue, centers[i])
            if (dist < min) {
                min = dist
                nearest = i
            }
        }
        return nearest
    }

    companion object {
        fun cyclicDistance(a: Float, b: Float): Float {
            val stdDiff = abs(a - b)
            return min(stdDiff, 1f - stdDiff)
        }
    }
}
