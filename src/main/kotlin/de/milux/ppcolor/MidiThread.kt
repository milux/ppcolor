package de.milux.ppcolor

import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm
import de.milux.ppcolor.debug.DebugFrame
import de.milux.ppcolor.ml.HueKMeans.Companion.cyclicDistance
import java.awt.Color
import java.util.*
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

class MidiThread : Thread() {
    private val bufferList = LinkedList<Array<Color>>()
    private var sumsRed = IntArray(N_COLORS)
    private var sumsGreen = IntArray(N_COLORS)
    private var sumsBlue = IntArray(N_COLORS)
    // Start black
    private var targetColors = Array(N_COLORS) { Color.BLACK }

    init {
        this.isDaemon = true
        this.name = "MIDI-Thread"

        // Automatic start
        start()
    }

    fun submitHueValues(hueValues: List<Float>) {
        val targetHues = hueValues.toFloatArray()
        val averageHues: FloatArray
        // Synchronized fetch of current average colors
        synchronized(this) {
            averageHues = FloatArray(N_COLORS) {
                Color.RGBtoHSB(sumsRed[it] / bufferList.size, sumsGreen[it] / bufferList.size,
                        sumsBlue[it] / bufferList.size, null)[0]
            }
        }
        // Create cost matrix with averages as workers and target values as jobs
        val costMatrix = Array(N_COLORS) { DoubleArray(N_COLORS) }
        for (a in 0 until N_COLORS) {
            for (t in 0 until N_COLORS) {
                costMatrix[a][t] = cyclicDistance(averageHues[a], targetHues[t]).toDouble()
            }
        }
        // Create hue List with ideal order (minimum difference between average and target hue over all indices)
        val orderedTargetHues = HungarianAlgorithm(costMatrix).execute().map { targetHues[it] }
        logger.info("Learned hue centers: ${orderedTargetHues.joinToString { Math.round(it * 360).toString() }}")
        val newTargetColors = Array(N_COLORS) { Color.getHSBColor(orderedTargetHues[it], 1f, 1f) }
        // Synchronized set of new target colors
        synchronized(this) {
            this.targetColors = newTargetColors
        }
    }

    private fun sendNote(note: Int, value: Int) {
        logger.debug("MIDI Note $note: $value")
        try {
            val deviceInfo = MidiSystem.getMidiDeviceInfo().firstOrNull {
                it.name == "Komplete Audio 6 MIDI" && it.description.contains("MIDI")
            } ?: return
            val device = MidiSystem.getMidiDevice(deviceInfo)
            device.open()
            // Send the MIDI message
            val myMsg = ShortMessage()
            myMsg.setMessage(ShortMessage.NOTE_ON, 0, note, value)
            device.receiver.send(myMsg, -1)
        } catch (x: Exception) {
            x.printStackTrace()
        }
    }

    override fun run() {
        val debugFrame = DebugFrame()
        while (true) {
            val time = System.currentTimeMillis()
            // Synchronize color update
            synchronized(this) {
                if (bufferList.size == BUFFER_SIZE) {
                    val remColors = bufferList.first
                    remColors.forEachIndexed { i, remColor ->
                        sumsRed[i] -= remColor.red
                        sumsGreen[i] -= remColor.green
                        sumsBlue[i] -= remColor.blue
                    }
                    bufferList.removeFirst()
                }
                // Color is immutable, so we can go without synchronization, just copying the reference
                val recentColors: Array<Color> = this.targetColors.copyOf()
                bufferList += recentColors
                recentColors.forEachIndexed { i, recentColor ->
                    sumsRed[i] += recentColor.red
                    sumsGreen[i] += recentColor.green
                    sumsBlue[i] += recentColor.blue
                }
            }

            for (i in 0 until N_COLORS) {
                val color = Color(
                        sumsRed[i] / bufferList.size,
                        sumsGreen[i] / bufferList.size,
                        sumsBlue[i] / bufferList.size)
                DebugFrame.colors[i] = color
                sendNote((3 * i) + 1, color.red / 2)
                sendNote((3 * i) + 2, color.green / 2)
                sendNote((3 * i) + 3, color.blue / 2)
            }
            if (debugFrame.isVisible) {
                debugFrame.repaint()
            }

            // Sleep after each cycle until MIN_ROUND_TIME ms are over
            val sleepTime = MIN_ROUND_TIME - (System.currentTimeMillis() - time)
            if (sleepTime > 0) {
                sleep(sleepTime)
            } else {
                logger.warn("Round time has been exceeded: $sleepTime")
            }
        }
    }

    companion object {
        private const val FADE_TIME = 1000L
        const val BUFFER_SIZE = (FADE_TIME / MIN_ROUND_TIME).toInt()
    }
}
