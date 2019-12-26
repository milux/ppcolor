package de.milux.ppcolor

class HuePoint(val hue: Float, val sat: Float, val y: Double) {
    companion object {
        data class HueSat(val hue: Float, val sat: Float)

        fun fromRGB(rgb: RGB) = fromRGB(rgb.red, rgb.green, rgb.blue)

        private fun fromRGB(r: Int, g: Int, b: Int): HuePoint {
            val hs = hueSatFromRGB(r, g, b)
            // Y component of YCbCr, see https://en.wikipedia.org/wiki/YCbCr
            val y = (0.299 * r + 0.587 * g + 0.114 * b) / 256
            return HuePoint(hs.hue, hs.sat, y)
        }

        private fun hueSatFromRGB(r: Int, g: Int, b: Int): HueSat {
            var cMax = if (r > g) r else g
            if (b > cMax) {
                cMax = b
            }
            var cMin = if (r < g) r else g
            if (b < cMin) {
                cMin = b
            }
            return if (cMax == 0 || cMax == cMin) {
                return HueSat(0f, 0f)
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
                HueSat(if (hue < 0f) hue + 1f else hue, (cMax - cMin).toFloat() / 256f)
            }
        }
    }
}