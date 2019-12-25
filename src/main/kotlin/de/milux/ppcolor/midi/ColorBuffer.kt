package de.milux.ppcolor.midi

import de.milux.ppcolor.RGB
import kotlin.math.max

class ColorBuffer(private val nColors: Int, private val bufferSize: Int) {
    private val redBuffers = Array(nColors) { IntArray(bufferSize) }
    private val greenBuffers = Array(nColors) { IntArray(bufferSize) }
    private val blueBuffers = Array(nColors) { IntArray(bufferSize) }
    private var bufferFill = 0
    private var bufferIndex = 0

    fun getAveraged(colorIndex: Int): FloatRGB {
        return FloatRGB(
                redBuffers[colorIndex].average().toFloat(),
                greenBuffers[colorIndex].average().toFloat(),
                blueBuffers[colorIndex].average().toFloat())
    }

    operator fun plusAssign(colors: Collection<RGB>) {
        if (colors.size != nColors) {
            throw IllegalArgumentException("Array colors has invalid length ${colors.size}!")
        }

        colors.forEachIndexed { i, color ->
            redBuffers[i][bufferIndex] = color.red
            greenBuffers[i][bufferIndex] = color.green
            blueBuffers[i][bufferIndex] = color.blue
        }

        // Update bufferIndex
        bufferIndex = (bufferIndex + 1) % bufferSize
        // Adjusts bufferFill until it reaches BUFFER_SIZE - 1
        bufferFill = max(bufferFill, bufferIndex + 1)
    }
}