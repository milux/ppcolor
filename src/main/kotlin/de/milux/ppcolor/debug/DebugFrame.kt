package de.milux.ppcolor.debug

import de.milux.ppcolor.HuePoint
import de.milux.ppcolor.MIN_SATURATION
import de.milux.ppcolor.N_COLORS
import de.milux.ppcolor.ml.buckets.BucketCluster
import de.milux.ppcolor.ml.buckets.HueBucketAlgorithm.N_BUCKETS
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Graphics
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

class DebugFrame : JPanel() {
    init {
        this.background = Color.BLACK
        val frame = JFrame("Output nColors")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1445, 950)
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

        huePoints.filter { it.sat >= MIN_SATURATION }.forEach { p ->
            val angle = p.hue * 2 * PI
            val x = (CIRCLE_CENTER_X + cos(angle) * CIRCLE_RADIUS).toInt()
            val y = (CIRCLE_CENTER_Y + sin(angle) * CIRCLE_RADIUS).toInt()
            val pd = (5 + p.y * 10).toInt()
            g.color = Color.getHSBColor(p.hue, 1f, 1f)
            g.fillOval(x, y, pd, pd)
        }

        bucketClusters.forEach {
            val diff = if (it.leftBorder > it.rightBorder) {
                1 - it.leftBorder + it.rightBorder
            } else {
                it.leftBorder - it.rightBorder
            }
            val startAngle = if (it.leftBorder < it.rightBorder) it.leftBorder else it.rightBorder
//            println("left: ${it.leftBorder}, right: ${it.rightBorder}, diff: $diff")
            g.color = Color.getHSBColor(it.center, 1f, 1f)
            g.fillArc(
                    CIRCLE_CENTER_X - CIRCLE_RADIUS + 25,
                    CIRCLE_CENTER_Y - CIRCLE_RADIUS + 25,
                    CIRCLE_RADIUS * 2 - 50,
                    CIRCLE_RADIUS * 2 - 50,
                    (-startAngle * 360).toInt(),
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

        if (bucketWeights.isNotEmpty()) {
            val maxW = bucketWeights.max() ?: throw IllegalStateException()
            bucketWeights.forEachIndexed { i, w ->
                val hue = i.toFloat() / N_BUCKETS
                val radius = (300 * w / maxW).toInt()
                g.color = Color.getHSBColor(hue, 1f, 1f)
                g.fillArc(
                        CIRCLE_CENTER_X - radius,
                        CIRCLE_CENTER_Y - radius,
                        radius * 2,
                        radius * 2,
                        -(hue * 360).toInt(),
                        ceil(360.toFloat() / N_BUCKETS).toInt())
            }
        }
    }

    companion object {
        const val CIRCLE_RADIUS = 400
        const val CIRCLE_CENTER_X = 925
        const val CIRCLE_CENTER_Y = 450

        private lateinit var debugFrame: DebugFrame
        val logger = LoggerFactory.getLogger(DebugFrame::class.java)!!

        var colors = Array<Color>(N_COLORS) { Color.BLACK }
        var outColors = Array<Color>(N_COLORS) { Color.BLACK }
        var huePoints = emptyList<HuePoint>()
        var bucketWeights = DoubleArray(0)
        var bucketClusters = emptyList<BucketCluster>()
        var isRepainting = false

        init {
            if (logger.isDebugEnabled) {
                debugFrame = DebugFrame()
            }
        }

        fun repaint() {
            synchronized(DebugFrame::class.java) {
                if (isRepainting) {
                    return
                }
                SwingUtilities.invokeLater {
                    isRepainting = true
                    debugFrame.repaint()
                    isRepainting = false
                }
            }
        }
    }
}
