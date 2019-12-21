package de.milux.ppcolor.debug

import de.milux.ppcolor.ml.HuePoint
import de.milux.ppcolor.normHue

data class DBSCANResult(val huePoints: List<HuePoint>) {
    val min: Float
    val max: Float
    val median: Float

    init {
        val hueValues = huePoints.map { it.hue }
        min = hueValues.min() ?: 0f
        max = hueValues.max() ?: 0f
        median = if (max - min > 0.5) {
            val translatedHues = hueValues.map { if (it < 0.5) it + 1f else it  }.sorted()
            val median = translatedHues[translatedHues.size / 2]
            if (median < 1f) median else median - 1f
        } else {
            hueValues.sorted()[hueValues.size / 2]
        }
    }

    constructor(center: Float) : this(listOf(
            HuePoint(normHue(center - 0.005f), .0),
            HuePoint(center, .0),
            HuePoint(normHue(center + 0.005f), .0)))
}