package de.milux.ppcolor.midi

import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm
import de.milux.ppcolor.*
import de.milux.ppcolor.debug.DebugFrame
import org.slf4j.LoggerFactory
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MidiThread : Thread() {
    private val buffer = ColorBuffer(N_COLORS, BUFFER_SIZE)
    private val outputColors = Array(N_COLORS) { FloatRGB(0f, 0f, 0f) }
    private var lastTargetColors: List<RGB> = emptyList()
    var midiStep = 0f

    init {
        this.isDaemon = true
        this.name = "MIDI-Thread"

        // Automatic start
        start()
    }

    fun submitHueValues(hueValues: FloatArray) {
        if (hueValues.isEmpty()) {
            // Empty array signals unchanged inputs, replay the last target colors and return
            buffer += lastTargetColors
            return
        }
        val medianHues: FloatArray
        // Synchronized fetch of current average nColors
        synchronized(this) {
            medianHues = FloatArray(N_COLORS) {
                outputColors[it].hue
            }
        }
        // Create cost matrix with medians as workers and target values as jobs
        val costMatrix = Array(N_COLORS) { DoubleArray(hueValues.size) }
        for (m in 0 until N_COLORS) {
            for (t in hueValues.indices) {
                costMatrix[m][t] = hueDistance(medianHues[m], hueValues[t]).toDouble()
            }
        }
        // Create hue List with ideal order (minimum difference between median and target hue over all indices)
        val hunResult = HungarianAlgorithm(costMatrix).execute()
        val orderedTargetHues = hunResult.map { hueValues[it] }
        logger.info("Learned hue centers: ${orderedTargetHues.joinToString { (it * 360).roundToInt().toString() }}")
        // Push new target colors to buffer
        lastTargetColors = orderedTargetHues.map { RGB.fromHSB(it) }
        buffer += lastTargetColors
    }

    private fun sendNotes(notes: Array<MidiNote>) {
        try {
            val deviceInfo = MidiSystem.getMidiDeviceInfo().firstOrNull {
                it.name == MIDI_DEV_NAME
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
            device.close()
        } catch (x: Exception) {
            logger.error("MIDI Error", x)
        }
    }

    override fun run() {
        val notes = Array(N_COLORS * 3) { MidiNote(0, 0) }
        while (true) {
            val midiMaxStep = max(midiStep, MIDI_MIN_STEP)
            val time = System.currentTimeMillis()
            outputColors.forEachIndexed { i, outputColor ->
                val average = buffer.getAveraged(i)
                val rDiff = outputColor.red - average.red
                val gDiff = outputColor.green - average.green
                val bDiff = outputColor.blue - average.blue
                val newOutputColor = FloatRGB(
                        outputColor.red - (if (rDiff > 0) min(rDiff, midiMaxStep) else max(rDiff, -midiMaxStep)),
                        outputColor.green - (if (gDiff > 0) min(gDiff, midiMaxStep) else max(gDiff, -midiMaxStep)),
                        outputColor.blue - (if (bDiff > 0) min(bDiff, midiMaxStep) else max(bDiff, -midiMaxStep))
                )
                outputColors[i] = newOutputColor
                if (DebugFrame.logger.isDebugEnabled) {
                    DebugFrame.colors[i] = average.color
                    DebugFrame.outColors[i] = newOutputColor.color
                }
                notes[(3 * i)] = MidiNote((3 * i) + 1, (newOutputColor.red / 2).toInt())
                notes[(3 * i) + 1] = MidiNote((3 * i) + 2, (newOutputColor.green / 2).toInt())
                notes[(3 * i) + 2] = MidiNote((3 * i) + 3, (newOutputColor.blue / 2).toInt())
            }
            sendNotes(notes)

            // Sleep after each cycle until MIN_ROUND_TIME ms are over
            val sleepTime = MIDI_ROUND_TIME - (System.currentTimeMillis() - time)
            if (sleepTime > 0) {
                sleep(sleepTime)
            } else {
                logger.warn("Round time has been exceeded by ${-sleepTime} ms")
            }
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(MidiThread::class.java)!!
    }
}
