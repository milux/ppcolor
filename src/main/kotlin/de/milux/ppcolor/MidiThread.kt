package de.milux.ppcolor

import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm
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

    private fun sendNotes(notes: Array<MidiNote>) {
        try {
            val deviceInfo = MidiSystem.getMidiDeviceInfo().firstOrNull {
                it.name == MIDI_DEV_NAME && it.description.contains(MIDI_DEV_DESC_SUBSTR)
            } ?: return
            val device = MidiSystem.getMidiDevice(deviceInfo)
            device.open()
            // Send the MIDI message
            val myMsg = ShortMessage()
            for (note in notes) {
                logger.trace("MIDI Note ${note.note}: ${note.value}")
                myMsg.setMessage(ShortMessage.NOTE_ON, 0, note.note, note.value)
                device.receiver.send(myMsg, -1)
            }
//            device.close()
        } catch (x: Exception) {
            logger.error("MIDI Error", x)
        }
    }

    override fun run() {
        val notes = Array(N_COLORS * 3) { MidiNote(0, 0) }
        while (true) {
            val time = System.currentTimeMillis()
            // Synchronize color update
            synchronized(this) {
                if (bufferList.size == FADE_BUFFER_SIZE) {
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
                notes[(3 * i)] = MidiNote((3 * i) + 1, sumsRed[i] / bufferList.size / 2)
                notes[(3 * i) + 1] = MidiNote((3 * i) + 2, sumsGreen[i] / bufferList.size / 2)
                notes[(3 * i) + 2] = MidiNote((3 * i) + 3, sumsBlue[i] / bufferList.size / 2)
            }
            sendNotes(notes)

            // Sleep after each cycle until MIN_ROUND_TIME ms are over
            val sleepTime = MIN_ROUND_TIME - (System.currentTimeMillis() - time)
            if (sleepTime > 0) {
                sleep(sleepTime)
            } else {
                logger.warn("Round time has been exceeded: $sleepTime")
            }
        }
    }
}
