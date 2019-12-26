package de.milux.ppcolor

import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.midi.MidiThread
import de.milux.ppcolor.ml.buckets.BucketBuffer
import de.milux.ppcolor.ml.buckets.HueBucketAlgorithm
import org.slf4j.LoggerFactory
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.system.exitProcess

// This is the frame grabber (and thus also the main loop) frequency. FPS = 1000 / MIN_ROUND_TIME
const val MIN_ROUND_TIME = 10L
// This is MIDI output (and color adaptation) frequency
const val MIDI_ROUND_TIME = 10L
// This multiplier controls the MIDI output color speed-stability-trade-off, higher is faster
const val MIDI_STEP_MULTIPLIER = 100.0
// This is the minimum adaptation speed of the MIDI output color
const val MIDI_MIN_STEP = 0.1
// The size of the buffer for smoothing of detected colors
const val BUFFER_SIZE = 1
// The size of the buffer for bucket algorithm smoothing
const val BUCKET_AVG_BUFFER_SIZE = 50
// The size of the buffer used to calculate the "pace" of color changes
const val DELTA_BUFFER_SIZE = 3000 / MIN_ROUND_TIME.toInt()
// Horizontal grid resolution to collect samples from frames
const val STEPS_X = 64
// Vertical grid resolution to collect samples from frames
const val STEPS_Y = 36
// The screen to target
const val TARGET_SCREEN = 1
// The name of the MIDI device to use for color output
const val MIDI_DEV_NAME = "Komplete Audio 6"
// Number of output colors
const val N_COLORS = 2
// Defines the minimum saturation of a pixel to regard it as colored
const val MIN_SATURATION = 0.1
// The minimum weight share of all buckets that must be collected into clusters
const val TARGET_WEIGHT_THRESHOLD = 0.8
// The minimum weight share of all buckets that must be contained to use only N_COLOR clusters
const val TARGET_WEIGHT_THRESHOLD_N_CLUSTERS = 0.6

const val GRID_POINTS = STEPS_X * STEPS_Y
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

    val bucketBuffer = BucketBuffer(BUCKET_AVG_BUFFER_SIZE)
    var lastHuePoints = emptyList<HuePoint>()
    // Prewarm delta information
    val deltaBuffer = LinkedList<Double>().also { it += 1e6 }
    var deltaSum = 1e6

    while(true) {
        val time = System.currentTimeMillis()

        val image = captureThread.image
        if (DebugFrame.logger.isTraceEnabled && ++run % (1000L / MIN_ROUND_TIME) == 0L) {
            executor.submit {
                ImageIO.write(image, "jpg", File("ss" + System.currentTimeMillis() + ".jpg"))
            }
        }

        val huePoints = ArrayList<HuePoint>(GRID_POINTS)

        // Use the grid pattern to extract pixel samples from current frame
        for (sx in 0 until STEPS_X) {
            for (sy in 0 until STEPS_Y) {
                val x = sx * stepX
                val y = sy * stepY
                val rgb = getRGBPoint(image, x, y)
                huePoints += HuePoint.fromRGB(rgb)
            }
        }

        // If any valid pixels have been found
        if ((huePoints.maxBy { it.sat }?.sat ?: 0f) >= MIN_SATURATION) {
            var frameDelta = 0.0
            if (lastHuePoints.isNotEmpty()) {
                var validSamples = 0
                huePoints.forEachIndexed { i, hp ->
                    val lastHp = lastHuePoints[i]
                    val combinedWeight = sqrt(hp.y * lastHp.y)
                    if (combinedWeight > MIN_SATURATION) {
                        validSamples += 1
                        frameDelta += hueDistance(hp.hue, lastHp.hue)
                    }
                }
                frameDelta /= max(validSamples, 1)
            }
            deltaBuffer += frameDelta
            deltaSum += frameDelta
            if (deltaBuffer.size > DELTA_BUFFER_SIZE) {
                deltaSum -= deltaBuffer.removeFirst()
            }

            val adaptationFactor = deltaSum / DELTA_BUFFER_SIZE * MIDI_STEP_MULTIPLIER
            midiThread.midiStep = sqrt(adaptationFactor)
            if (logger.isTraceEnabled) {
                logger.trace("Frame delta: $frameDelta; Adaptation pace ${midiThread.midiStep}")
            }
            if (frameDelta != 0.0 || lastHuePoints.isEmpty()) {
                lastHuePoints = huePoints
            }

            val extBucketWeights = HueBucketAlgorithm.getExtendedBucketWeights(huePoints)
            bucketBuffer += extBucketWeights
            val bucketHues = HueBucketAlgorithm.getDominantHueList(bucketBuffer.average())
            if (bucketHues.isNotEmpty()) {
                logger.info("Bucket algorithm results: ${bucketHues.joinToString()}")
            }
            midiThread.submitHueValues(bucketHues)

            if (DebugFrame.logger.isDebugEnabled) {
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
