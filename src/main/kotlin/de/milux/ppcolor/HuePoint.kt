package de.milux.ppcolor

import de.milux.ppcolor.ml.dbscan.Weighable
import org.apache.commons.math3.ml.clustering.Clusterable

class HuePoint(val hue: Float, override val weight: Double) : Clusterable, Weighable {
    override fun getPoint(): DoubleArray {
        return DoubleArray(1) { hue.toDouble() }
    }

    companion object {
        fun fromRGB(rgb: RGB) = fromRGB(rgb.red, rgb.green, rgb.blue)

        private fun fromRGB(r: Int, g: Int, b: Int): HuePoint {
            // Y component of YCbCr, see https://en.wikipedia.org/wiki/YCbCr
            val y = (0.299 * r + 0.587 * g + 0.114 * b) / 256
            return HuePoint(hueFromRGB(r, g, b), y * y)
        }

        private fun hueFromRGB(r: Int, g: Int, b: Int): Float {
            var cMax = if (r > g) r else g
            if (b > cMax) {
                cMax = b
            }
            var cMin = if (r < g) r else g
            if (b < cMin) {
                cMin = b
            }
            return if (cMax == 0 || cMax == cMin) {
                0f
            } else {
                val hue = when {
                    r == cMax -> {
                        (g - b).toFloat() / (cMax - cMin).toFloat()
                    }
                    g == cMax -> {
                        2.0f + (b - r).toFloat() / (cMax - cMin).toFloat()
                    }
                    else -> {
                        4.0f + (r - g).toFloat() / (cMax - cMin).toFloat()
                    }
                } / 6.0f
                if (hue < 0f) hue + 1f else hue
            }
        }
    }
}