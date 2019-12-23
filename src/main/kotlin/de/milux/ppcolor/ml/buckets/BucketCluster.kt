package de.milux.ppcolor.ml.buckets

import de.milux.ppcolor.hueDistance
import de.milux.ppcolor.normHue

data class BucketCluster(val weight: Double, val leftBorder: Float, val rightBorder: Float) {
    val center = normHue(leftBorder + hueDistance(leftBorder, rightBorder) / 2)
}