package de.milux.ppcolor

import de.milux.ppcolor.ml.DBSCANExecutor
import de.milux.ppcolor.ml.HuePoint
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import java.lang.Math.round
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.system.exitProcess

const val MIN_ROUND_TIME = 33L
const val FADE_BUFFER_SIZE = 30
const val STEPS_X = 16
const val STEPS_Y = 9
const val TARGET_SCREEN = 2
const val MIDI_DEV_NAME = "Komplete Audio 6 MIDI"
const val MIDI_DEV_DESC_SUBSTR = "MIDI"
const val N_COLORS = 2
const val MAX_RANDOM_LOOKUPS = 10000
const val MIN_WEIGHT = .1
val logger = LoggerFactory.getLogger("de.milux.ppcolor.MainKt")!!

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

    val huePoints = ArrayList<HuePoint>((STEPS_X + 1) * (STEPS_Y + 1))
    val stepX = (screenWidth - 1) / (STEPS_X - 1)
    val stepY = (screenHeight - 1) / (STEPS_Y - 1)

    var run = 0L
    val executor = Executors.newFixedThreadPool(1)

    while(true) {
        val time = System.currentTimeMillis()

        val image = captureThread.image
        if (logger.isTraceEnabled && ++run % 10L == 0L) {
            executor.submit {
                ImageIO.write(image, "jpg", File("ss" + System.currentTimeMillis() + ".jpg"))
            }
        }

        huePoints.clear()
        // First use a fixed grid to extract pixels
        val usedCoordinates = HashSet<Pair<Int, Int>>()
        var nRandomLookups = 0
        for (sx in 0 until STEPS_X) {
            for (sy in 0 until STEPS_Y) {
                val x = sx * stepX
                val y = sy * stepY
                usedCoordinates += Pair(x, y)
                val hp = getHuePoint(image, x, y)
                if (hp != null) {
                    huePoints += hp
                }
            }
        }

        // This is not a mistake! We want the same "random" sequence for each execution!
        val rand = Random(42)
        // If any valid pixels have been found
        if (huePoints.isNotEmpty()) {
            // Use random, unvisited coordinates to get additional pixels
            val expectedPoints = STEPS_X * STEPS_Y
            while (huePoints.size < expectedPoints && nRandomLookups < MAX_RANDOM_LOOKUPS) {
                nRandomLookups++
                val x = rand.nextInt(screenWidth)
                val y = rand.nextInt(screenHeight)
                val coordinates = Pair(x, y)
                if (coordinates !in usedCoordinates) {
                    usedCoordinates += coordinates
                    val hp = getHuePoint(image, x, y)
                    huePoints += hp ?: continue
                }
            }
            // Use clustering algorithm to find clusters
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
            sleep(sleepTime)
        } else {
            logger.debug("Round time has been exceeded: $sleepTime")
        }
    }
}

fun getHuePoint(image: BufferedImage, x: Int, y: Int): HuePoint? {
    val rgb = image.getRGB(x, y)
    val colorModel = image.colorModel
    val hsb = Color.RGBtoHSB(colorModel.getRed(rgb), colorModel.getGreen(rgb), colorModel.getBlue(rgb), null)
    val hp = HuePoint(hsb[0], hsb[1].toDouble() * hsb[2])
    return if (hp.weight >= MIN_WEIGHT) hp else null
}