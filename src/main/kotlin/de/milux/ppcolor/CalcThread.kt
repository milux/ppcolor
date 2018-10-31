package de.milux.ppcolor

import java.awt.Color
import java.util.*
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

class CalcThread : Thread() {
    private lateinit var receiver: Receiver
    private val bufferList = LinkedList<Color>()
    private var sumRed = 0
    private var sumGreen = 0
    private var sumBlue = 0
    // Start black
    private var targetColor: Color = Color(0, 0, 0)

    init {
        this.isDaemon = true
        this.name = "MIDI-Thread"

        MidiSystem.getMidiDeviceInfo().filter {
            it.name == "Komplete Audio 6 MIDI" && it.description.contains("MIDI")
        }.forEach {
            val device = MidiSystem.getMidiDevice(it)
            device.open()
            try {
                receiver = device.receiver
            } catch (x: Exception) {
                x.printStackTrace()
            }
        }

        // Automatic start
        start()
    }

    fun setTargetColor(color: Color) {
        // Color is immutable, so we can go without synchronization
        this.targetColor = color
    }

    private fun sendNote(receiver: Receiver, note: Int, value: Int) {
        val myMsg = ShortMessage()
        myMsg.setMessage(ShortMessage.NOTE_ON, 0, note, value)
        receiver.send(myMsg, -1)
    }

    override fun run() {
        while (true) {
            if (bufferList.size == BUFFER_SIZE) {
                val remColor = bufferList.first
                sumRed -= remColor.red
                sumGreen -= remColor.green
                sumBlue -= remColor.blue
                bufferList.removeFirst()
            }
            // Color is immutable, so we can go without synchronization, just copying the reference
            val recentColor: Color = this.targetColor
            bufferList += recentColor
            sumRed += recentColor.red
            sumGreen += recentColor.green
            sumBlue += recentColor.blue

            sendNote(receiver, 1, sumRed / bufferList.size / 2)
            sendNote(receiver, 2, sumGreen / bufferList.size / 2)
            sendNote(receiver, 3, sumBlue / bufferList.size / 2)

            sleep(UPDATE_DELAY)
        }
    }

    companion object {
        const val UPDATE_DELAY = 30L
        private const val FADE_TIME = 1000
        const val BUFFER_SIZE = (FADE_TIME / UPDATE_DELAY).toInt()
    }
}