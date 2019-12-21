package de.milux.ppcolor.debug

import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.SHOW_DEBUG_FRAME
import de.milux.ppcolor.ml.HuePoint
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DebugFrame : JPanel() {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        this.background = Color.BLACK
        val frame = JFrame("Output nColors")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1445, 1060)
        frame.contentPane = this
        frame.isVisible = true
    }

    override fun paint(g: Graphics) {
        super.paint(g)

        outColors.forEachIndexed { i, color ->
            g.color = Color.RED
            g.fillRect(10 + (i * 210), 10, 10, color.red)
            g.color = Color.GREEN
            g.fillRect(20 + (i * 210), 10, 10, color.green)
            g.color = Color.BLUE
            g.fillRect(30 + (i * 210), 10, 10, color.blue)
            g.color = colors[i]
            g.fillRect(40 + (i * 210), 10, 170, 127)
            g.color = color
            g.fillRect(40 + (i * 210), 137, 170, 128)
        }

        huePoints.forEach { p ->
            val angle = p.hue * 2 * PI
            val x = (CIRCLE_CENTER_X + cos(angle) * CIRCLE_RADIUS).toInt()
            val y = (CIRCLE_CENTER_Y + sin(angle) * CIRCLE_RADIUS).toInt()
            val pd = (5 + p.weight * 10).toInt()
            g.color = Color.getHSBColor(p.hue, 1f, 1f)
            g.fillOval(x, y, pd, pd)
        }

        dbscanResults.forEach {
            val diff = if (it.max - it.min > 0.5) 1f - (it.max - it.min) else it.max - it.min
            g.color = Color.getHSBColor(it.median, 1f, 1f)
            g.fillArc(
                    CIRCLE_CENTER_X - CIRCLE_RADIUS + 25,
                    CIRCLE_CENTER_Y - CIRCLE_RADIUS + 25,
                    CIRCLE_RADIUS * 2 - 50,
                    CIRCLE_RADIUS * 2 - 50,
                    -(it.max * 360).toInt(),
                    (diff * 360).toInt())
        }
        g.color = Color.BLACK
        g.fillArc(
                CIRCLE_CENTER_X - CIRCLE_RADIUS + 75,
                CIRCLE_CENTER_Y - CIRCLE_RADIUS + 75,
                CIRCLE_RADIUS * 2 - 150,
                CIRCLE_RADIUS * 2 - 150,
                0,
                360)

        kMeansResults.forEach {
            g.color = Color.getHSBColor(it, 1f, 1f)
            g.fillArc(
                    CIRCLE_CENTER_X - CIRCLE_RADIUS + 175,
                    CIRCLE_CENTER_Y - CIRCLE_RADIUS + 175,
                    CIRCLE_RADIUS * 2 - 350,
                    CIRCLE_RADIUS * 2 - 350,
                    -((it + 0.005) * 360).toInt(),
                    3)
        }

//        val imageWidth = 400
//        val imageHeight = (image.getHeight(null).toFloat() * 400f / image.getWidth(null).toFloat()).toInt()
//        g.drawImage(image, 10, 310, imageWidth, imageHeight, null)
    }

    companion object {
        const val CIRCLE_RADIUS = 400
        const val CIRCLE_CENTER_X = 925
        const val CIRCLE_CENTER_Y = 450

        private lateinit var debugFrame: DebugFrame

        var colors = Array<Color>(N_COLORS) { Color.BLACK }
        var outColors = Array<Color>(N_COLORS) { Color.BLACK }
        var huePoints = emptyList<HuePoint>()
        var dbscanResults = emptyList<DBSCANResult>()
        var kMeansResults = emptyList<Float>()
        var image: Image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

        init {
            if (SHOW_DEBUG_FRAME) {
                debugFrame = DebugFrame()
            }
        }

        fun repaint() {
            SwingUtilities.invokeAndWait { debugFrame.repaint() }
        }
    }
}
