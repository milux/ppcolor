package de.milux.ppcolor

import java.awt.*
import java.lang.Thread.sleep
import java.util.*
import kotlin.math.max
import kotlin.system.exitProcess

const val MIN_ROUND_TIME = 100L
val SCALE_FACTOR = Toolkit.getDefaultToolkit().screenResolution / 96.0
const val STEPS_X = 20
const val STEPS_Y = 20

fun main(args : Array<String>) {
    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (screenDevices.size < 2) {
        println("No Second Screen detected!")
        exitProcess(1)
    }
    val screenBounds = screenDevices[1].defaultConfiguration.bounds
    val robot = Robot()
    val calcThread = CalcThread()
    val ssRectangle = Rectangle(
            (screenBounds.x / SCALE_FACTOR).toInt(),
            (screenBounds.y / SCALE_FACTOR).toInt(),
            (screenBounds.width / SCALE_FACTOR).toInt(),
            (screenBounds.height / SCALE_FACTOR).toInt()
    )

    val stepX = (screenBounds.width / SCALE_FACTOR - 20) / STEPS_X
    val stepY = (screenBounds.height / SCALE_FACTOR - 20) / STEPS_Y
    val baseX = 10
    val baseY = 10

    while(true) {
        val time = System.currentTimeMillis()

        val rawList = ArrayList<Color>()

        val image = robot.createScreenCapture(ssRectangle)
//        ImageIO.write(image, "png", File("ss" + System.currentTimeMillis() + ".png"))
        val colorModel = image.colorModel

        for (sx in 0..STEPS_X) {
            for (sy in 0..STEPS_Y) {
                val rgb = image.getRGB((baseX + sx * stepX).toInt(), (baseY + sy * stepY).toInt())
                val color = Color(colorModel.getRed(rgb), colorModel.getGreen(rgb), colorModel.getBlue(rgb))
                rawList += color
            }
        }

        // Take the upper quarter with the brightest values
        val sortedColors = rawList.sortedBy { it.red + it.green + it.blue }
        val colorList = sortedColors.slice(STEPS_X * STEPS_Y / 4 * 3 .. STEPS_X * STEPS_Y)

        val clSize = colorList.size
        val mainColor = Color(
                colorList.sumBy { it.red } / clSize,
                colorList.sumBy { it.green } / clSize,
                colorList.sumBy { it.blue } / clSize
        )
        print(mainColor)

        val hsb = Color.RGBtoHSB(mainColor.red, mainColor.green, mainColor.blue, null)
        // Maximize saturation
        hsb[1] = 1.0f
        val satColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2])

        val ccMax = max(max(satColor.red, satColor.green), satColor.blue)
        val scaleFactor = 255.0 / ccMax
        val normColor = Color(
                (satColor.red * scaleFactor).toInt(),
                (satColor.green * scaleFactor).toInt(),
                (satColor.blue * scaleFactor).toInt()
        )

        print(" -> ")
        print(normColor)
        println()

        calcThread.setTargetColor(normColor)

        // Sleep after each cycle until MIN_ROUND_TIME ms are over
        val sleepTime = MIN_ROUND_TIME - (System.currentTimeMillis() - time)
        if (sleepTime > 0) {
            sleep(System.currentTimeMillis() - time)
        }
    }
}