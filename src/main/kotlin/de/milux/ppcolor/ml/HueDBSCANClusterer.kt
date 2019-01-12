package de.milux.ppcolor.ml

import de.milux.ppcolor.ml.HueKMeans.Companion.cyclicDistance
import org.apache.commons.math3.exception.NotPositiveException
import org.apache.commons.math3.exception.NullArgumentException
import org.apache.commons.math3.ml.clustering.Cluster
import java.util.*

class HueDBSCANClusterer
/**
 * Creates a new instance of a WeightedDBSCANClusterer.
 *
 * @param eps maximum radius of the neighborhood to be considered
 * @param minWeight minimum weight of points needed for a cluster
 * @throws NotPositiveException if `eps < 0.0` or `minPts < 0`
 */
@Throws(NotPositiveException::class)
constructor(
        /** Maximum radius of the neighborhood to be considered.  */
        private val eps: Double,
        /** Minimum number of points needed for a cluster.  */
        private val minWeight: Double) : WeightedDBSCANClusterer<HuePoint>(eps, minWeight) {
    private val pointLookup = HashMap<HuePoint, Int>()

    /**
     * Returns a list of density-reachable neighbors of a `point`.
     *
     * @param point the point to look for
     * @param points possible neighbors
     * @return the List of neighbors
     */
    override fun getNeighbors(point: HuePoint, points: Collection<HuePoint>): List<HuePoint> {
        val pList = points as List<HuePoint>
        val neighbors = ArrayList<HuePoint>()
        val pointIndex = pointLookup[point]!!
        // Seek left
        var leftEndReached = false
        for (i in pointIndex - 1 downTo 0) {
            val dist = cyclicDistance(point.hue, pList[i].hue)
            if (dist <= eps) {
                neighbors += pList[i]
            } else {
                leftEndReached = true
                break
            }
        }
        // Continue on other end
        if (!leftEndReached) {
            for (i in pList.size - 1 downTo 0) {
                val dist = cyclicDistance(point.hue, pList[i].hue)
                if (dist <= eps) {
                    neighbors += pList[i]
                } else {
                    break
                }
            }
        }
        // Seek right
        var rightEndReached = false
        for (i in pointIndex + 1 until pList.size) {
            val dist = cyclicDistance(point.hue, pList[i].hue)
            if (dist <= eps) {
                neighbors += pList[i]
            } else {
                rightEndReached = true
                break
            }
        }
        // Continue on other end
        if (!rightEndReached) {
            for (i in 0 until pList.size) {
                val dist = cyclicDistance(point.hue, pList[i].hue)
                if (dist <= eps) {
                    neighbors += pList[i]
                } else {
                    break
                }
            }
        }
        return neighbors
    }

    /**
     * Performs DBSCAN cluster analysis.
     *
     * @param points the points to cluster
     * @return the list of clusters
     * @throws NullArgumentException if the data points are null
     */
    @Throws(NullArgumentException::class)
    override fun cluster(points: Collection<HuePoint>): List<Cluster<HuePoint>> {
        // Use sorted points
        val sortedPoints = ArrayList(points.sortedBy { it.hue })
        // Create Point lookup
        pointLookup.clear()
        sortedPoints.forEachIndexed { index, huePoint ->
            pointLookup[huePoint] = index
        }
        return super.cluster(sortedPoints)
    }
}