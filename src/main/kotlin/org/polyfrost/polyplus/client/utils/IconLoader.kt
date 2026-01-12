package org.polyfrost.polyplus.client.utils


import dev.deftu.omnicore.api.client.OmniDesktop
import org.lwjgl.BufferUtils
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
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
    @JvmField
    val IMAGE_SIZES = when {
        OmniDesktop.isWindows -> intArrayOf(16, 32)
        OmniDesktop.isMac -> intArrayOf(128)
        else -> intArrayOf(32)
    }
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
        val buffers: Array<ByteBuffer?> = arrayOfNulls(IMAGE_SIZES.size)
        for (i in IMAGE_SIZES.indices) {
            buffers[i] = loadInstance(image, IMAGE_SIZES[i])
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
    @JvmStatic
    fun loadInstance(image: BufferedImage, dimension: Int): ByteBuffer {
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
        val (width, height) = image.width to image.height
        val buffer = BufferUtils.createByteBuffer(width * height * 4)

        for (y in 0..<height) for (x in 0..<width) {
            val colorSpace = image.getRGB(x, y)
            val index = (y * width + x) * 4
            buffer.put(index, ((colorSpace shr 16) and 0xFF).toByte())
            buffer.put(index + 1, ((colorSpace shr 8) and 0xFF).toByte())
            buffer.put(index + 2, (colorSpace and 0xFF).toByte())
            buffer.put(index + 3, ((colorSpace shr 24) and 0xFF).toByte())
        }

        return buffer
    }
}