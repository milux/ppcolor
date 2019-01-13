package de.milux.ppcolor.debug

import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Graphics
import javax.swing.JFrame

class DebugFrame : JFrame("Output colors") {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

        setSize(430, 220)

        if (logger.isDebugEnabled) {
            // If debug on, make JFrame visible
            isVisible = true
        } else {
            // If debug off, dispose JFrame
            dispose()
        }
    }

    override fun paint(g: Graphics?) {
        super.paint(g!!)

        g.color = color1
        g.fillRect(10, 10, 200, 200)

        g.color = color2
        g.fillRect(220, 10, 200, 200)
    }

    companion object {
        var color1 = Color.BLACK!!
        var color2 = Color.BLACK!!
    }
}
