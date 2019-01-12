/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.milux.ppcolor.ml

import org.apache.commons.math3.exception.NotPositiveException
import org.apache.commons.math3.exception.NullArgumentException
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import org.apache.commons.math3.ml.clustering.Clusterer
import org.apache.commons.math3.ml.distance.DistanceMeasure
import org.apache.commons.math3.ml.distance.EuclideanDistance
import org.apache.commons.math3.util.MathUtils
import java.util.*

/**
 * DBSCAN (density-based spatial clustering of applications with noise) algorithm.
 *
 *
 * The DBSCAN algorithm forms clusters based on the idea of density connectivity, i.e.
 * a point p is density connected to another point q, if there exists a chain of
 * points p<sub>i</sub>, with i = 1 .. n and p<sub>1</sub> = p and p<sub>n</sub> = q,
 * such that each pair &lt;p<sub>i</sub>, p<sub>i+1</sub>&gt; is directly density-reachable.
 * A point q is directly density-reachable from point p if it is in the -neighborhood
 * of this point.
 *
 *
 * Any point that is not density-reachable from a formed cluster is treated as noise, and
 * will thus not be present in the result.
 *
 *
 * The algorithm requires two parameters:
 *
 *  * eps: the distance that defines the -neighborhood of a point
 *  * minPoints: the minimum number of density-connected points required to form a cluster
 *
 *
 * @param <T> type of the points to cluster
 * @see [DBSCAN
 * @see [
 * A Density-Based Algorithm for Discovering Clusters in Large Spatial Databases with Noise](http://www.dbs.ifi.lmu.de/Publikationen/Papers/KDD-96.final.frame.pdf)
 *
 * @since 3.2
](http://en.wikipedia.org/wiki/DBSCAN)</T> */
open class WeightedDBSCANClusterer<T>
/**
 * Creates a new instance of a WeightedDBSCANClusterer.
 *
 * @param eps maximum radius of the neighborhood to be considered
 * @param minWeight minimum weight of points needed for a cluster
 * @param measure the distance measure to use
 * @throws NotPositiveException if `eps < 0.0` or `minPts < 0`
 */
@Throws(NotPositiveException::class)
constructor(
        /** Maximum radius of the neighborhood to be considered.  */
        private val eps: Double,
        /** Minimum number of points needed for a cluster.  */
        private val minWeight: Double,
        measure: DistanceMeasure = EuclideanDistance()) : Clusterer<T>(measure)
        where T : Clusterable, T : Weighable {

    /** Status of a point during the clustering process.  */
    enum class PointStatus {
        /** The point has is considered to be noise.  */
        NOISE,
        /** The point is already part of a cluster.  */
        PART_OF_CLUSTER
    }

    init {
        if (eps < 0.0) {
            throw NotPositiveException(eps)
        }
        if (minWeight < 0) {
            throw NotPositiveException(minWeight)
        }
        if (!minWeight.isFinite()) {
            throw IllegalArgumentException("minWeight is not a finite value")
        }
    }

    /**
     * Performs DBSCAN cluster analysis.
     *
     * @param points the points to cluster
     * @return the list of clusters
     * @throws NullArgumentException if the data points are null
     */
    @Throws(NullArgumentException::class)
    override fun cluster(points: Collection<T>): List<Cluster<T>> {
        // sanity checks
        MathUtils.checkNotNull(points)

        val clusters = ArrayList<Cluster<T>>()
        val visited = HashMap<Clusterable, PointStatus>()

        for (point in points) {
            if (visited[point] != null) {
                continue
            }
            val neighbors = getNeighbors(point, points)
            if (neighbors.sumByDouble { it.weight } >= minWeight) {
                // DBSCAN does not care about center points
                val cluster = Cluster<T>()
                clusters.add(expandCluster(cluster, point, neighbors, points, visited))
            } else {
                visited[point] = PointStatus.NOISE
            }
        }

        return clusters
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
    protected fun expandCluster(cluster: Cluster<T>,
                              point: T,
                              neighbors: List<T>,
                              points: Collection<T>,
                              visited: MutableMap<Clusterable, PointStatus>): Cluster<T> {
        cluster.addPoint(point)
        visited[point] = PointStatus.PART_OF_CLUSTER

        var seeds: MutableList<T> = ArrayList(neighbors)
        var index = 0
        while (index < seeds.size) {
            val current = seeds[index]
            val pStatus = visited[current]
            // only check non-visited points
            if (pStatus == null) {
                val currentNeighbors = getNeighbors(current, points)
                if (currentNeighbors.sumByDouble { it.weight } >= minWeight) {
                    seeds = merge(seeds, currentNeighbors)
                }
            }

            if (pStatus != PointStatus.PART_OF_CLUSTER) {
                visited[current] = PointStatus.PART_OF_CLUSTER
                cluster.addPoint(current)
            }

            index++
        }
        return cluster
    }

    /**
     * Returns a list of density-reachable neighbors of a `point`.
     *
     * @param point the point to look for
     * @param points possible neighbors
     * @return the List of neighbors
     */
    protected open fun getNeighbors(point: T, points: Collection<T>): List<T> {
        val neighbors = ArrayList<T>()
        for (neighbor in points) {
            if (point !== neighbor && distance(neighbor, point) <= eps) {
                neighbors.add(neighbor)
            }
        }
        return neighbors
    }

    /**
     * Merges two lists together.
     *
     * @param one first list
     * @param two second list
     * @return merged lists
     */
    private fun merge(one: MutableList<T>, two: List<T>): MutableList<T> {
        val oneSet = HashSet(one)
        for (item in two) {
            if (!oneSet.contains(item)) {
                one.add(item)
            }
        }
        return one
    }
}