package org.polyfrost.polyplus.client.utils


import dev.deftu.omnicore.api.client.OmniDesktop
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO


/*****************************************************************************
 * A convenience class for loading icons from images.
 *
 * Icons loaded from this class are formatted to fit within the required
 * dimension (16x16, 32x32, or 128x128). If the source image is larger than the
 * target dimension, it is shrunk down to the minimum size that will fit. If it
 * is smaller, then it is only scaled up if the new scale can be a per-pixel
 * linear scale (i.e., x2, x3, x4, etc). In both cases, the image's width/height
 * ratio is kept the same as the source image.
 *
 * @author Chris Molini
 */
object IconLoader {
    /*************************************************************************
     * Loads an icon in ByteBuffer form.
     *
     * @param filepath
     * The location of the Image to use as an icon.
     *
     * @return An array of ByteBuffers containing the pixel data for the icon in
     * varying sizes.
     */
    @JvmStatic
    fun load(filepath: String): Array<ByteBuffer?> {
        return load(File(filepath))
    }

    /*************************************************************************
     * Loads an icon in ByteBuffer form.
     *
     * @param fil
     * A File pointing to the image.
     *
     * @return An array of ByteBuffers containing the pixel data for the icon in
     * various sizes (as recommended by the OS).
     */
    @JvmStatic
    fun load(fil: File): Array<ByteBuffer?> {
        val image: BufferedImage
        try {
            image = ImageIO.read(fil)
        } catch (e: IOException) {
            e.printStackTrace()
            return arrayOfNulls(2)
        }

        return load(image)
    }

    @JvmStatic
    fun load(image: BufferedImage): Array<ByteBuffer?> {
        val buffers: Array<ByteBuffer?>
        if (OmniDesktop.isWindows) {
            buffers = arrayOfNulls(2)
            buffers[0] = loadInstance(image, 16)
            buffers[1] = loadInstance(image, 32)
        } else if (OmniDesktop.isMac) {
            buffers = arrayOfNulls(1)
            buffers[0] = loadInstance(image, 128)
        } else {
            buffers = arrayOfNulls(1)
            buffers[0] = loadInstance(image, 32)
        }
        return buffers
    }

    /*************************************************************************
     * Copies the supplied image into a square icon at the indicated size.
     *
     * @param image
     * The image to place onto the icon.
     * @param dimension
     * The desired size of the icon.
     *
     * @return A ByteBuffer of pixel data at the indicated size.
     */
    private fun loadInstance(image: BufferedImage, dimension: Int): ByteBuffer {
        val scaledIcon = BufferedImage(dimension, dimension, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = scaledIcon.createGraphics()
        val ratio = getIconRatio(image, scaledIcon)
        val width = image.width * ratio
        val height = image.height * ratio
        g.drawImage(
            image,
            ((scaledIcon.width - width) / 2).toInt(),
            ((scaledIcon.height - height) / 2).toInt(),
            (width).toInt(),
            (height).toInt(),
            null
        )
        g.dispose()

        return convertToByteBuffer(scaledIcon)
    }

    /*************************************************************************
     * Gets the width/height ratio of the icon. This is meant to simplify
     * scaling the icon to a new dimension.
     *
     * @param src
     * The base image that will be placed onto the icon.
     * @param icon
     * The icon that will have the image placed on it.
     *
     * @return The amount to scale the source image to fit it onto the icon
     * appropriately.
     */
    private fun getIconRatio(src: BufferedImage, icon: BufferedImage): Double {
        var ratio: Double
        if (src.width > icon.width) ratio = (icon.width).toDouble() / src.width
        else ratio = (icon.width.toFloat() / src.width).toDouble()
        if (src.height > icon.height) {
            val r2 = (icon.height).toDouble() / src.height
            if (r2 < ratio) ratio = r2
        } else {
            val r2 = (icon.height.toFloat() / src.height).toDouble()
            if (r2 < ratio) ratio = r2
        }
        return ratio
    }

    /*************************************************************************
     * Converts a BufferedImage into a ByteBuffer of pixel data.
     *
     * @param image
     * The image to convert.
     *
     * @return A ByteBuffer that contains the pixel data of the supplied image.
     */
    fun convertToByteBuffer(image: BufferedImage): ByteBuffer {
        val buffer = ByteArray(image.width * image.height * 4)
        var counter = 0
        for (i in 0..<image.height) for (j in 0..<image.width) {
            val colorSpace = image.getRGB(j, i)
            buffer[counter] = ((colorSpace shl 8) shr 24).toByte()
            buffer[counter + 1] = ((colorSpace shl 16) shr 24).toByte()
            buffer[counter + 2] = ((colorSpace shl 24) shr 24).toByte()
            buffer[counter + 3] = (colorSpace shr 24).toByte()
            counter += 4
        }
        return ByteBuffer.wrap(buffer)
    }
}