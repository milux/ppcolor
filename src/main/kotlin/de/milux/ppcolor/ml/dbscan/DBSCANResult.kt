package de.milux.ppcolor.ml.dbscan

import de.milux.ppcolor.HuePoint

data class DBSCANResult(val huePoints: List<HuePoint>) {
    val min: Float
    val max: Float
    val median: Float

    init {
        val hueValues = huePoints.map { it.hue }
        min = hueValues.min() ?: throw IllegalStateException()
        max = hueValues.max() ?: throw IllegalStateException()
        median = if (max - min > 0.5) {
            val translatedHues = hueValues.map { if (it < 0.5) it + 1f else it  }.sorted()
            val median = translatedHues[translatedHues.size / 2]
            if (median < 1f) median else median - 1f
        } else {
            hueValues.sorted()[hueValues.size / 2]
        }
    }
}