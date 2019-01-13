package de.milux.ppcolor.debug

import de.milux.ppcolor.N_COLORS
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Graphics
import javax.swing.JFrame

class DebugFrame : JFrame("Output colors") {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

        setSize(435, 310)

        if (logger.isDebugEnabled) {
            // If debug on, make JFrame visible
            isVisible = true
        } else {
            // If debug off, dispose JFrame
            dispose()
        }
    }

    override fun paint(g: Graphics) {
        super.paint(g)

        colors.forEachIndexed { i, color ->
            g.color = color
            g.fillRect(10 + (i * 210), 40, 200, 255)
            g.color = Color.RED
            g.fillRect(10 + (i * 210), 40, 10, color.red)
            g.color = Color.GREEN
            g.fillRect(20 + (i * 210), 40, 10, color.green)
            g.color = Color.BLUE
            g.fillRect(30 + (i * 210), 40, 10, color.blue)
        }
    }

    companion object {
        var colors = Array<Color>(N_COLORS) { Color.BLACK }
    }
}
