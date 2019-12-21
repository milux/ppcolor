package de.milux.ppcolor

import java.awt.Color

data class FloatRGB(val red: Float, val green: Float, val blue: Float) {
    val hue get() = Color.RGBtoHSB(red.toInt(), green.toInt(), blue.toInt(), null)[0]

    val color get() = Color(red.toInt(), green.toInt(), blue.toInt())
}