package de.milux.ppcolor

import de.milux.ppcolor.ml.dbscan.Weighable
import org.apache.commons.math3.ml.clustering.Clusterable

class HuePoint(val hue: Float, override val weight: Double) : Clusterable, Weighable {
    override fun getPoint(): DoubleArray {
        return DoubleArray(1) { hue.toDouble() }
    }
}