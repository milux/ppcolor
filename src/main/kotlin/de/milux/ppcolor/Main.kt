package de.milux.ppcolor

import de.milux.ppcolor.ml.DBSCANExecutor
import de.milux.ppcolor.ml.HuePoint
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.round
import kotlin.system.exitProcess

const val MIN_ROUND_TIME = 100L
const val STEPS_X = 24
const val STEPS_Y = 13
const val TARGET_SCREEN = 1
const val N_COLORS = 2
val logger = LoggerFactory.getLogger("de.milux.ppcolor")!!

fun main(args : Array<String>) {
    // https://get.videolan.org/vlc/2.2.5.1/win64/vlc-2.2.5.1-win64.7z
    System.setProperty("jna.library.path", "vlc")

    val lockFile = RandomAccessFile("ppcolor.lock", "rw")
    val lock = lockFile.channel.tryLock()
    if (lock == null) {
        logger.error("Already running!")
        exitProcess(1)
    }

    val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (screenDevices.size < 2) {
        logger.error("No Second Screen detected!")
        exitProcess(2)
    }
    screenDevices.forEachIndexed { i, device -> logger.info("Device ${i + 1}: ${device.defaultConfiguration.bounds}") }

    val screenDevice = screenDevices[TARGET_SCREEN - 1]
    val screenBounds = screenDevice.defaultConfiguration.bounds
    val screenTransform = screenDevice.defaultConfiguration.defaultTransform
    val screenWidth = round(screenBounds.width * screenTransform.scaleX).toInt()
    val screenHeight = round(screenBounds.height * screenTransform.scaleY).toInt()

    val captureThread = VLCCapture(screenDevice)
    val calcThread = MidiThread()

    val rawList = ArrayList<Color>((STEPS_X + 1) * (STEPS_Y + 1))
    val stepX = (screenWidth - 1) / (STEPS_X - 1)
    val stepY = (screenHeight - 1) / (STEPS_Y - 1)

    var run = 0L
    val executor = Executors.newFixedThreadPool(1)

    while(true) {
        val time = System.currentTimeMillis()

        val image = captureThread.image
        if (logger.isDebugEnabled && ++run % 10L == 0L) {
            executor.submit {
                ImageIO.write(image, "jpg", File("ss" + System.currentTimeMillis() + ".jpg"))
            }
        }
        val colorModel = image.colorModel

        rawList.clear()
        for (sx in 0 until STEPS_X) {
            for (sy in 0 until STEPS_Y) {
                val rgb = image.getRGB(sx * stepX, sy * stepY)
                val color = Color(colorModel.getRed(rgb), colorModel.getGreen(rgb), colorModel.getBlue(rgb))
                rawList += color
            }
        }

        // Calculate HSB colors with a saturation-brightness-product of at least 0.1
        val huePoints = rawList.map { Color.RGBtoHSB(it.red, it.green, it.blue, null) }
                // Create Pairs with hue value and weight (saturation * brightness), sorted by hue value
                .map { HuePoint(it[0], (it[1] * it[2]).toDouble()) }
                // Filter totally black/gray pixels
                .filter { it.weight != .0 }

        // Don't perform any update if image is predominantly grey
        if (huePoints.any { it.weight > 0.1 }) {
//            val startKMeans = System.currentTimeMillis()
//            val hueMeans = HueKMeans().getKMeans(huePoints)
//            logger.info("HueKMeans results: ${hueMeans?.joinToString()}, " +
//                    "time: ${System.currentTimeMillis() - startKMeans}")
//            if (hueMeans != null) {
//                calcThread.submitHueValues(hueMeans.toList())
//            }
            val startDBSCAN = System.currentTimeMillis()
            val hueClusters = DBSCANExecutor(huePoints).findCenters()
            logger.info("DBSCANExecutor results: ${hueClusters?.joinToString()}, " +
                    "time: ${System.currentTimeMillis() - startDBSCAN}")
            if (hueClusters != null) {
                calcThread.submitHueValues(hueClusters.toList())
            }
        }

        // Sleep after each cycle until MIN_ROUND_TIME ms are over
        val sleepTime = MIN_ROUND_TIME - (System.currentTimeMillis() - time)
        if (sleepTime > 0) {
            logger.trace("Sleep $sleepTime ms")
            sleep(sleepTime)
        } else {
            logger.warn("Round time has been exceeded: $sleepTime")
        }
    }
}