package de.milux.ppcolor.ml

import de.milux.ppcolor.N_COLORS
import org.apache.commons.math3.ml.clustering.Cluster
import org.slf4j.LoggerFactory

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
                val translatedHues = hueValues.map { if (it < 0.5) it + 1f else it  }
                val average = translatedHues.average().toFloat()
                if (average < 1f) average else average - 1f
            } else {
                hueValues.average().toFloat()
            }
        }
    }

    private fun findNClusters(): List<Cluster<HuePoint>>? {
        var clusters = emptyList<Cluster<HuePoint>>()
        // Use bisection to refine number of clusters until N_COLORS clusters are found
        var runs = 0
        var eps = MAX_EPS
        while (eps > .001) {
            clusters = getClusters(eps)
            runs++
            if (clusters.size >= N_COLORS) {
                logger.trace("Found $N_COLORS clusters after $runs iterations with eps = $eps")
                return clusters.sortedByDescending { it.points.size }
            }
            eps *= EPS_ANNEALING
        }
        if (clusters.size < N_COLORS) {
            logger.warn("Found only ${clusters.size} clusters, skipping")
            return null
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
        const val MIN_PTS_FACTOR = .1
    }
}