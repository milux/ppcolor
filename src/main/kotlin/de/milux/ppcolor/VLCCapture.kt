package de.milux.ppcolor

import uk.co.caprica.vlcj.player.MediaPlayerFactory
import uk.co.caprica.vlcj.player.direct.BufferFormatCallback
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer
import uk.co.caprica.vlcj.player.direct.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat
import java.awt.GraphicsDevice
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt


class VLCCapture(screenDevice: GraphicsDevice) : Thread() {
    private val image: BufferedImage
    private val factory: MediaPlayerFactory
    private val mediaPlayer: DirectMediaPlayer
    private val options: Array<String>

    init {
        this.isDaemon = true
        this.name = "VLC-ScreenCapture-Thread"

        val screenBounds = screenDevice.defaultConfiguration.bounds
        val screenTransform = screenDevice.defaultConfiguration.defaultTransform
        val width = Math.round(screenBounds.width * screenTransform.scaleX).toInt()
        val height = Math.round(screenBounds.height * screenTransform.scaleY).toInt()

        image = screenDevice.defaultConfiguration.createCompatibleImage(width, height)
        image.accelerationPriority = 1.0f

        options = arrayOf(":screen-fps=${1000L / MIN_ROUND_TIME}", ":live-caching=0",
                ":screen-width=$width", ":screen-height=$height",
                ":screen-left=${screenBounds.x}", ":screen-top=${screenBounds.y}")
        logger.info("Player options: ${options.joinToString()}")
        factory = MediaPlayerFactory()
        mediaPlayer = factory.newDirectMediaPlayer(
                BufferFormatCallback { w, h -> RV32BufferFormat(w, h) },
                RenderCallback())

        // Automatic start
        start()
    }

    override fun run() {
        mediaPlayer.playMedia("screen://", *options)
    }

    fun getImage(): BufferedImage {
        val cm = image.colorModel
        val isAlphaPreMultiplied = cm.isAlphaPremultiplied
        val raster = image.copyData(null)
        return BufferedImage(cm, raster, isAlphaPreMultiplied, null)
    }

    private inner class RenderCallback internal constructor() :
            RenderCallbackAdapter((image.raster.dataBuffer as DataBufferInt).data) {

        public override fun onDisplay(mediaPlayer: DirectMediaPlayer, data: IntArray) {
            // not required, data is copied from image directly
        }
    }
}
