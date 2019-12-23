package de.milux.ppcolor

import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.midi.MidiThread
import de.milux.ppcolor.ml.buckets.HueBucketAlgorithm
import org.slf4j.LoggerFactory
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.exitProcess

const val MIN_ROUND_TIME = 10L
const val MIDI_ROUND_TIME = 10L
const val MIDI_MIN_STEP = .05f
const val MIDI_STEP_DIVISOR = 30.0f
const val BUFFER_SIZE = 500 / MIN_ROUND_TIME.toInt()
const val DELTA_BUFFER_SIZE = 1500 / MIN_ROUND_TIME.toInt()
const val STEPS_X = 64
const val STEPS_Y = 32
const val STEPS_TOTAL = STEPS_X * STEPS_Y
const val TARGET_SCREEN = 1
const val MIDI_DEV_NAME = "Komplete Audio 6"
const val N_COLORS = 2
const val MIN_WEIGHT = .05
const val MIN_CLUSTER_WEIGHT = .1
const val TARGET_WEIGHT_THRESHOLD = .8
val frameLock = Object()
val logger = LoggerFactory.getLogger("de.milux.ppcolor.MainKt")!!

fun main() {
    System.setProperty("jna.library.path", "/Applications/VLC.app/Contents/MacOS/lib")

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
    val screenWidth = (screenBounds.width * screenTransform.scaleX).roundToInt()
    val screenHeight = (screenBounds.height * screenTransform.scaleY).roundToInt()

    val captureThread = VLCCapture(screenDevice)
    val midiThread = MidiThread()

    val stepX = (screenWidth - 1) / (STEPS_X - 1)
    val stepY = (screenHeight - 1) / (STEPS_Y - 1)

    var run = 0L
    val executor = Executors.newFixedThreadPool(max(Runtime.getRuntime().availableProcessors() - 2, 1))

    val gridRgb = ArrayList<RGB>(STEPS_TOTAL)
    val lastGridRgb = ArrayList<RGB>(STEPS_TOTAL)
    val deltaBuffer = LinkedList<Int>()
    var deltaSum = 0
    // Prewarm delta information
    deltaBuffer += 1e6.toInt()
    deltaSum += 1e6.toInt()

    val taskList = LinkedList<Future<FloatArray>>()

    while(true) {
        val time = System.currentTimeMillis()

        val image = captureThread.image
        if (DebugFrame.logger.isTraceEnabled && ++run % (1000L / MIN_ROUND_TIME) == 0L) {
            executor.submit {
                ImageIO.write(image, "jpg", File("ss" + System.currentTimeMillis() + ".jpg"))
            }
        }

        val huePoints = ArrayList<HuePoint>(STEPS_TOTAL)
        gridRgb.clear()

        // First use a fixed grid to extract pixels
        for (sx in 0 until STEPS_X) {
            for (sy in 0 until STEPS_Y) {
                val x = sx * stepX
                val y = sy * stepY
                val rgb = getRGBPoint(image, x, y)
                gridRgb += rgb
                huePoints += rgb.toHuePoint()
            }
        }

        var frameDelta = 0
        if (lastGridRgb.isNotEmpty()) {
            gridRgb.forEachIndexed { i, color ->
                frameDelta += color diff lastGridRgb[i]
            }
        }
        if (frameDelta != 0) {
            deltaBuffer += frameDelta
            deltaSum += frameDelta
            if (deltaBuffer.size > DELTA_BUFFER_SIZE) {
                deltaSum -= deltaBuffer.removeFirst()
            }
        }
        midiThread.midiStep = deltaSum.toFloat() / DELTA_BUFFER_SIZE / STEPS_TOTAL / MIDI_STEP_DIVISOR
        if (logger.isTraceEnabled) {
            logger.trace("Frame delta: $frameDelta; Adaptation pace ${midiThread.midiStep}")
        }
        if (frameDelta != 0 || lastGridRgb.isEmpty()) {
            lastGridRgb.clear()
            lastGridRgb.addAll(gridRgb)
        }

        // If any valid pixels have been found
        if ((huePoints.maxBy { it.weight } ?: HuePoint(.0f, .0)).weight >= MIN_WEIGHT) {
            taskList += if (frameDelta != 0) {
                executor.submit(Callable { HueBucketAlgorithm.getDominantHueList(huePoints) })
            } else {
                executor.submit(Callable { FloatArray(0) })
            }
            while (taskList.isNotEmpty() && taskList.first.isDone) {
                val bucketHues = taskList.removeFirst().get()
                if (bucketHues.isNotEmpty()) {
                    logger.info("Bucket algorithm results: ${bucketHues.joinToString()}")
                }
                midiThread.submitHueValues(bucketHues)
            }

            if (DebugFrame.logger.isDebugEnabled) {
                DebugFrame.image = captureThread.image
                DebugFrame.huePoints = ArrayList(huePoints)
                DebugFrame.repaint()
            }
        }

        // Sleep after each cycle until MIN_ROUND_TIME ms are over
        val sleepTime = (System.currentTimeMillis() - time) - MIN_ROUND_TIME
        if (sleepTime > 0) {
            logger.warn("Round time has been exceeded by $sleepTime ms")
        }

        // Wait up to two "normal cycles"
        synchronized(frameLock) {
            frameLock.wait(MIN_ROUND_TIME * 2)
        }
    }
}

fun getRGBPoint(image: BufferedImage, x: Int, y: Int): RGB {
    val rgb = image.getRGB(x, y)
    val colorModel = image.colorModel
    return RGB(colorModel.getRed(rgb), colorModel.getGreen(rgb), colorModel.getBlue(rgb))
}

fun normHue(hue: Float): Float {
    return when {
        hue >= 1f -> hue - 1f
        hue < 0f -> hue + 1f
        else -> hue
    }
}

fun hueDistance(a: Float, b: Float): Float {
    val stdDiff = abs(a - b)
    return min(stdDiff, 1f - stdDiff)
}
