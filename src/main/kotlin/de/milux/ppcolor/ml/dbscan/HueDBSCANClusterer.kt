package de.milux.ppcolor.ml.dbscan

import de.milux.ppcolor.HuePoint
import de.milux.ppcolor.hueDistance
import org.apache.commons.math3.exception.NotPositiveException
import org.apache.commons.math3.exception.NullArgumentException
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import java.util.*
import kotlin.collections.HashMap

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
     * Optimized for only directly visiting neighboring candidates.
     *
     * @param point the point to look for
     * @param points possible neighbors
     * @return the List of neighbors
     */
    override fun getNeighbors(point: HuePoint, points: Collection<HuePoint>): List<HuePoint> {
        val pList = points as List
        val neighbors = LinkedList<HuePoint>()
        val pointIndex = pointLookup[point]!!
        // Seek left
        var leftEndReached = false
        for (i in pointIndex - 1 downTo 0) {
            val dist = hueDistance(point.hue, pList[i].hue)
            if (dist <= eps) {
                neighbors.addFirst(pList[i])
            } else {
                leftEndReached = true
                break
            }
        }
        // Continue on other end
        if (!leftEndReached) {
            for (i in pList.size - 1 downTo pointIndex + 1) {
                val dist = hueDistance(point.hue, pList[i].hue)
                if (dist <= eps) {
                    neighbors.addFirst(pList[i])
                } else {
                    break
                }
            }
        }
        // Seek right
        var rightEndReached = false
        for (i in pointIndex + 1 until pList.size) {
            val dist = hueDistance(point.hue, pList[i].hue)
            if (dist <= eps) {
                neighbors.addLast(pList[i])
            } else {
                rightEndReached = true
                break
            }
        }
        // Continue on other end
        if (!rightEndReached) {
            for (i in 0 until pointIndex - 1) {
                val dist = hueDistance(point.hue, pList[i].hue)
                if (dist <= eps) {
                    neighbors.addLast(pList[i])
                } else {
                    break
                }
            }
        }
        return neighbors
    }

    /**
     * Expands the cluster to include density-reachable items.
     *
     * @param cluster Cluster to expand
     * @param point Point to add to cluster
     * @param neighbors List of neighbors
     * @param points the data set
     * @param visited the set of already visited points
     * @return the expanded cluster
     */
    override fun expandCluster(cluster: Cluster<HuePoint>,
                               point: HuePoint,
                               neighbors: List<HuePoint>,
                               points: Collection<HuePoint>,
                               visited: MutableMap<Clusterable, PointStatus>): Cluster<HuePoint> {
        cluster.addPoint(point)
        visited[point] = PointStatus.PART_OF_CLUSTER

        val seeds: MutableList<HuePoint> = ArrayList(neighbors)
        val pSet = HashSet<HuePoint>(neighbors)
        var index = 0
        while (index < seeds.size) {
            val current = seeds[index]
            val pStatus = visited[current]
            // only check non-visited points
            if (pStatus == null) {
                val currentNeighbors = getNeighbors(current, points)
                if (currentNeighbors.sumByDouble { it.weight } >= minWeight) {
                    currentNeighbors.forEach {
                        if (it !in pSet) {
                            pSet += it
                            seeds += it
                        }
                    }
                }
            }

            if (pStatus != PointStatus.PART_OF_CLUSTER) {
                visited[current] = PointStatus.PART_OF_CLUSTER
                cluster.addPoint(current)
            }

            index++
        }

//        val pList = points as List
//        val pSize = pList.nColors
//        val iLeftStart = pointLookup[neighbors.first()]!!
//        val iPointStart = pointLookup[point]!!
//        val iRightStart = pointLookup[neighbors.last()]!!
//        val weightStart = point.weight + neighbors.sumByDouble { it.weight }
//
//        var weight = weightStart
//        var iLeft = iLeftStart
//        var iPoint = iPointStart
//        var iRight = iRightStart
//
//        // Expand right (modulo expressions for wrap-around)
//        while(true) {
//            iPoint++
//            iPoint %= pSize
//            while (cyclicDistance(pList[iPoint].hue, pList[iLeft].hue) > eps) {
//                weight -= pList[iLeft].weight
//                iLeft++
//                iLeft %= pSize
//            }
//            while (cyclicDistance(pList[iPoint].hue, pList[(iRight + 1) % pSize].hue) <= eps) {
//                iRight++
//                iRight %= pSize
//                weight += pList[iRight].weight
//            }
//            if (weight < minWeight || (iRight + 1) % pSize == iLeftStart) {
//                break
//            }
//        }
//        // Add right expansion to cluster
//        val sliceRight = if (iPointStart + 1 < iRight) {
//            pList.slice(iPointStart + 1 .. iRight)
//        } else {
//            pList.slice(iPointStart until pList.nColors) + pList.slice(0 .. iRight)
//        }
//        for (p in sliceRight) {
//            if (visited[p] != PointStatus.PART_OF_CLUSTER) {
//                cluster.addPoint(p)
//                visited[p] = PointStatus.PART_OF_CLUSTER
//            }
//        }
//
//        weight = weightStart
//        iLeft = iLeftStart
//        iPoint = iPointStart
//        iRight = iRightStart
//
//        // Expand left (if expressions for wrap-around)
//        while(true) {
//            iPoint--
//            if (iPoint == -1) iPoint = pSize - 1
//            while (cyclicDistance(pList[iPoint].hue, pList[iRight].hue) > eps) {
//                weight -= pList[iRight].weight
//                iRight--
//                if (iRight == -1) iRight = pSize - 1
//            }
//            while (cyclicDistance(pList[iPoint].hue, pList[if (iLeft == 0) pSize - 1 else iLeft - 1].hue) <= eps) {
//                iLeft--
//                if (iLeft == -1) iLeft = pSize - 1
//                weight += pList[iLeft].weight
//            }
//            if (weight < minWeight || (if (iLeft == 0) pSize - 1 else iLeft - 1) == iRightStart) {
//                break
//            }
//        }
//        // Add left expansion to Cluster
//        val sliceLeft = if (iLeft < iPointStart - 1) {
//            pList.slice(iLeft until iPointStart)
//        } else {
//            pList.slice(iLeft until pList.nColors) + pList.slice(0 until iPointStart)
//        }
//        for (p in sliceLeft) {
//            if (visited[p] != PointStatus.PART_OF_CLUSTER) {
//                cluster.addPoint(p)
//                visited[p] = PointStatus.PART_OF_CLUSTER
//            }
//        }

        return cluster
    }

    /**
     * Performs DBSCAN cluster analysis.
     *
     * @param points the points to cluster
     * @return the list of clusters
     * @throws NullArgumentException if the data points are null
     */
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