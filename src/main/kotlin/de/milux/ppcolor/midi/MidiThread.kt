package de.milux.ppcolor.midi

import blogspot.software_and_algorithms.stern_library.optimization.HungarianAlgorithm
import de.milux.ppcolor.*
import de.milux.ppcolor.debug.DebugFrame
import org.slf4j.LoggerFactory
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MidiThread : Thread() {
    private val outputColors = Array(N_COLORS) { FloatRGB(0f, 0f, 0f) }
    private val targetColors = Array(N_COLORS) { RGB(0, 0, 0) }
    private var orderedTargetWeights = DoubleArray(N_COLORS)
    private var lastTargetHues = FloatArray(N_COLORS).toList()
    var midiStep = .0

    init {
        this.isDaemon = true
        this.name = "MIDI-Thread"

        // Automatic start
        start()
    }

    fun submitHueClusters(clusters: List<HueCluster>) {
        if (clusters.isEmpty()) {
            // Empty array signals unchanged inputs
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
        val costMatrix = Array(N_COLORS) { DoubleArray(clusters.size) }
        for (m in 0 until N_COLORS) {
            for (t in clusters.indices) {
                costMatrix[m][t] = (
                        hueDistance(lastTargetHues[m], clusters[t].hue) * 0.01
                                + hueDistance(medianHues[m], clusters[t].hue)
                                + 0.1
                        ) / clusters[t].weight.pow(2)
            }
        }
        // Create hue List with ideal order (minimum difference between median and target hue over all indices)
        val hunResult = HungarianAlgorithm(costMatrix).execute()
        val orderedTargetHues = hunResult.map { clusters[it].hue }
        lastTargetHues = orderedTargetHues
        hunResult.map { clusters[it].weight }.forEachIndexed { i, w -> orderedTargetWeights[i] = w }
        logger.info("Learned clusters: ${orderedTargetHues.joinToString { it.toString() }}")
        // Apply new target colors
        orderedTargetHues.forEachIndexed { i, hue -> targetColors[i] = RGB.fromHSB(hue) }
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
            val time = System.currentTimeMillis()
            outputColors.forEachIndexed { i, outputColor ->
                val average = targetColors[i]
                val maxStep = (max(midiStep, MIDI_MIN_STEP) * orderedTargetWeights[i]).toFloat()
                val rDiff = outputColor.red - average.red
                val gDiff = outputColor.green - average.green
                val bDiff = outputColor.blue - average.blue
                val newOutputColor = FloatRGB(
                        outputColor.red - (if (rDiff > 0) min(rDiff, maxStep) else max(rDiff, -maxStep)),
                        outputColor.green - (if (gDiff > 0) min(gDiff, maxStep) else max(gDiff, -maxStep)),
                        outputColor.blue - (if (bDiff > 0) min(bDiff, maxStep) else max(bDiff, -maxStep))
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
