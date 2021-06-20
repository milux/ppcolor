package de.milux.ppcolor

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.GraphicsDevice
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.roundToInt


class VLCCapture(screenDevice: GraphicsDevice) : Thread() {
    val image: BufferedImage
    private val mediaPlayerComponent: CallbackMediaPlayerComponent
    private val options: Array<String>

    init {
        this.isDaemon = true
        this.name = "VLC-ScreenCapture-Thread"

        val screenBounds = screenDevice.defaultConfiguration.bounds
        val screenTransform = screenDevice.defaultConfiguration.defaultTransform
        val width = (screenBounds.width * screenTransform.scaleX).roundToInt()
        val height = (screenBounds.height * screenTransform.scaleY).roundToInt()

        image = screenDevice.defaultConfiguration.createCompatibleImage(width, height)
        image.accelerationPriority = 1.0f

        options = if (System.getProperty("os.name").contains("Windows")) {
            arrayOf(":screen-fps=${1000L / MIN_ROUND_TIME}", ":live-caching=0",
                    ":screen-width=$width", ":screen-height=$height",
                    ":screen-left=0", ":screen-top=${screenBounds.y}")
        } else {
            val screenId = screenDevice.iDstring.split(" ")[1].toLong()
            arrayOf(":screen-fps=${1000L / MIN_ROUND_TIME}", ":live-caching=0",
                    ":screen-display-id=$screenId")
        }
        logger.info("Player options: ${options.joinToString()}")
        mediaPlayerComponent = CallbackMediaPlayerComponent(null,
                null, null, true, null,
                RenderCallback(), BufferFormatCallback(), null)

        // Automatic start
        start()
    }

    override fun run() {
        val player = mediaPlayerComponent.mediaPlayer()
        player.media().play("screen://", *options)
    }

    private inner class RenderCallback :
            RenderCallbackAdapter((image.raster.dataBuffer as DataBufferInt).data) {

        public override fun onDisplay(mediaPlayer: MediaPlayer, data: IntArray) {
            // Wake up the screen processing loop
            synchronized(frameLock) {
                frameLock.notifyAll()
            }
        }
    }

    private inner class BufferFormatCallback : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat =
                RV32BufferFormat(sourceWidth, sourceHeight)
    }
}
