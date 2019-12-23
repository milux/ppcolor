package de.milux.ppcolor.ml

import de.milux.ppcolor.MIN_CLUSTER_WEIGHT
import de.milux.ppcolor.debug.DBSCANResult
import de.milux.ppcolor.debug.DebugFrame
import org.apache.commons.math3.ml.clustering.Cluster
import org.slf4j.LoggerFactory
import kotlin.math.min

class DBSCANExecutor(private val nColors: Int, hueList: List<HuePoint>) {
    private val points: List<HuePoint> = hueList.sortedBy { it.hue }
    private val totalWeight: Double
    private val minWeight: Double
    private val logger = LoggerFactory.getLogger(javaClass)!!

    init {
        totalWeight = points.sumByDouble { it.weight }
        minWeight = MIN_CLUSTER_WEIGHT * totalWeight
    }

    fun findCenters(): List<Float>? {
        val hueClusters = findNClusters()?.map { cluster -> cluster.points.map { it.hue } }
        return hueClusters?.map { hueValues ->
            if (hueValues.max()!! - hueValues.min()!! > 0.5) {
                val translatedHues = hueValues.map { if (it < 0.5) it + 1f else it  }.sorted()
                val median = translatedHues[translatedHues.size / 2]
                if (median < 1f) median else median - 1f
            } else {
                hueValues.sorted()[hueValues.size / 2]
            }
        }
    }

    private fun findNClusters(optimistic: Boolean = true): List<Cluster<HuePoint>>? {
        var clusters = emptyList<Cluster<HuePoint>>()
        // Use bisection to refine number of clusters until nColors clusters are found
        var runs = 0
        var eps = optimisticEps
        while (eps > .001) {
            runs++
            clusters = getClusters(eps)
            if (clusters.size >= nColors) {
                optimisticEps = min(eps * OPTIMISTIC_EPS_FACTOR, MAX_EPS)
                logger.trace("Found ${clusters.size} clusters after $runs iterations with eps = $eps")
                if (DebugFrame.logger.isDebugEnabled) {
                    DebugFrame.dbscanResults = clusters.map { DBSCANResult(it.points) }
                }
                return clusters
            } else if (!optimistic && clusters.sumByDouble { c -> c.points.sumByDouble { it.weight } }
                    >= totalWeight * FEW_CLUSTERS_THRESHOLD) {
                optimisticEps = min(eps * OPTIMISTIC_EPS_FACTOR, MAX_EPS)
                logger.trace("Found ${clusters.size} clusters after $runs iterations with eps = $eps")
                if (DebugFrame.logger.isDebugEnabled) {
                    DebugFrame.dbscanResults = clusters.map { DBSCANResult(it.points) }
                }
                // Order clusters by weight
                val orderedClusters = clusters.sortedByDescending { c -> c.points.sumByDouble { it.weight } }
                // Copy clusters list until new list holds at least N_COLOR clusters
                val duplicatedClusters = ArrayList(orderedClusters)
                while (duplicatedClusters.size < nColors) {
                    duplicatedClusters += orderedClusters
                }
                // Return exactly N_COLOR clusters
                return duplicatedClusters.subList(0, nColors)
            }
            eps *= EPS_ANNEALING
        }
        if (clusters.size < nColors) {
            // If this may have happened due to an optimistic execution, retry with conservative eps
            return if (optimistic) {
                logger.warn("Found only ${clusters.size} clusters (optimistic), retrying with MAX_EPS")
                optimisticEps = MAX_EPS
                findNClusters(false)
            } else {
                logger.warn("Found only ${clusters.size} clusters, skipping")
                null
            }
        }
        return clusters
    }

    private fun getClusters(distance: Double): List<Cluster<HuePoint>> {
        val clusterer = HueDBSCANClusterer(distance, minWeight)
        return clusterer.cluster(points)
    }

    companion object {
        const val EPS_ANNEALING = .8
        const val MAX_EPS = .05
        const val OPTIMISTIC_EPS_FACTOR = 2.5
        const val FEW_CLUSTERS_THRESHOLD = 0.8

        private var optimisticEps = MAX_EPS
    }
}