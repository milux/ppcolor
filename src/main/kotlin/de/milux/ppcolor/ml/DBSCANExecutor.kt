package de.milux.ppcolor.ml

import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.SHOW_DEBUG_FRAME
import de.milux.ppcolor.debug.DBSCANResult
import de.milux.ppcolor.debug.DebugFrame
import org.apache.commons.math3.ml.clustering.Cluster
import org.slf4j.LoggerFactory
import kotlin.math.min

class DBSCANExecutor(hueList: List<HuePoint>) {
    private val points: List<HuePoint> = hueList.sortedBy { it.hue }
    private val minWeight: Double
    private val logger = LoggerFactory.getLogger(javaClass)!!

    init {
        val totalWeight = points.sumByDouble { it.weight }
        minWeight = MIN_PTS_FACTOR * totalWeight
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
        // Use bisection to refine number of clusters until N_COLORS clusters are found
        var runs = 0
        var eps = optimisticEps
        while (eps > .001) {
            runs++
            clusters = getClusters(eps)
            if (clusters.size >= N_COLORS) {
                optimisticEps = min(eps * OPTIMISTIC_EPS_FACTOR, MAX_EPS)
                logger.trace("Found ${clusters.size} clusters after $runs iterations with eps = $eps")
                if (SHOW_DEBUG_FRAME) {
                    DebugFrame.dbscanResults = clusters.map { DBSCANResult(it.points) }
                }
                return clusters
            }
            eps *= EPS_ANNEALING
        }
        if (clusters.size < N_COLORS) {
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
        const val MAX_EPS = .1
        const val MIN_PTS_FACTOR = .05
        const val OPTIMISTIC_EPS_FACTOR = 2.5

        private var optimisticEps = MAX_EPS
    }
}