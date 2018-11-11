package de.milux.ppcolor

import java.awt.*
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess

const val MIN_ROUND_TIME = 100L
val SCALE_FACTOR = Toolkit.getDefaultToolkit().screenResolution / 96.0
const val STEPS_X = 20
const val STEPS_Y = 20
const val RGB_MIN_DIFF = 25

fun main(args : Array<String>) {
    val lockFile = RandomAccessFile("ppcolor.lock", "rw")
    val lock = lockFile.channel.tryLock()
    if (lock == null) {
        println("Already running!")
        exitProcess(1)
    }

    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (screenDevices.size < 2) {
        println("No Second Screen detected!")
        exitProcess(2)
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

    val stepX = ((screenBounds.width - 1) / SCALE_FACTOR) / STEPS_X
    val stepY = ((screenBounds.height - 1) / SCALE_FACTOR) / STEPS_Y
    val numValues = STEPS_X * STEPS_Y / 4

    while(true) {
        val time = System.currentTimeMillis()

        val rawList = ArrayList<Color>()

        val image = robot.createScreenCapture(ssRectangle)
//        ImageIO.write(image, "png", File("ss" + System.currentTimeMillis() + ".png"))
        val colorModel = image.colorModel

        for (sx in 0 .. STEPS_X) {
            for (sy in 0 .. STEPS_Y) {
                val rgb = image.getRGB((sx * stepX).toInt(), (sy * stepY).toInt())
                val color = Color(colorModel.getRed(rgb), colorModel.getGreen(rgb), colorModel.getBlue(rgb))
                rawList += color
            }
        }

        // Take the upper quarter with the brightest values
        val sortedColors = rawList
                .filter { abs(it.red - it.green) > RGB_MIN_DIFF
                        || abs(it.green - it.blue) > RGB_MIN_DIFF
                        || abs(it.red - it.blue) > RGB_MIN_DIFF }
                .sortedBy {
                    val hsb = Color.RGBtoHSB(it.red, it.green, it.blue, null)
                    hsb[1] * hsb[2]
                }
        // Only execute if we have color values
        if (sortedColors.isNotEmpty()) {
            val colorList = sortedColors
                    .slice(max(0, sortedColors.size - numValues) until sortedColors.size)

            val clSize = colorList.size
            val mainColor = Color(
                    colorList.sumBy { it.red } / clSize,
                    colorList.sumBy { it.green } / clSize,
                    colorList.sumBy { it.blue } / clSize
            )
            print(mainColor)

            val hsb = Color.RGBtoHSB(mainColor.red, mainColor.green, mainColor.blue, null)
            // Maximize saturation and brightness
            val satColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f)

            print(" -> ")
            print(satColor)
            println()

            calcThread.setTargetColor(satColor)
        }

        // Sleep after each cycle until MIN_ROUND_TIME ms are over
        val sleepTime = MIN_ROUND_TIME - (System.currentTimeMillis() - time)
        if (sleepTime > 0) {
            sleep(System.currentTimeMillis() - time)
        }
    }
}