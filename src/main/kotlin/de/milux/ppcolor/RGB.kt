package de.milux.ppcolor

import java.awt.Color
import kotlin.math.abs

data class RGB(val red: Int, val green: Int, val blue: Int) {
    constructor(c: Color) : this(c.red, c.green, c.blue)

    infix fun diff(rgb: RGB): Int {
        return abs(red - rgb.red) + abs(green - rgb.green) + abs(blue - rgb.blue)
    }

    val hue get() = Color.RGBtoHSB(red, green, blue, null)[0]

    fun toHuePoint(minWeight: Double): HuePoint? {
        val hp = toHuePoint()
        return if (hp.weight >= minWeight) hp else null
    }

    fun toHuePoint(): HuePoint {
        val hsb = Color.RGBtoHSB(red, green, blue, null)
        return HuePoint(hsb[0], hsb[1].toDouble() * hsb[2] * hsb[2])
    }

    val color get() = Color(red, green, blue)

    companion object {
        fun fromHSB(hue: Float, saturation: Float = 1f, brightness: Float = 1f): RGB {
            return RGB(Color.getHSBColor(hue, saturation, brightness))
        }
    }
}