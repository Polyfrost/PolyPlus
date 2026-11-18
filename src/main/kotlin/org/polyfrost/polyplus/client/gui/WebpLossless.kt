package org.polyfrost.polyplus.client.gui

import kotlin.math.abs

internal class WebpLosslessImage(val width: Int, val height: Int, val argb: IntArray)

internal object WebpLossless {
    private const val SIGNATURE = 0x2f
    private const val NUM_LITERAL_CODES = 256
    private const val NUM_LENGTH_CODES = 24
    private const val NUM_DISTANCE_CODES = 40
    private const val CODE_LENGTH_CODES = 19
    private const val PREDICTOR = 0
    private const val CROSS_COLOR = 1
    private const val SUBTRACT_GREEN = 2
    private const val COLOR_INDEXING = 3
    private const val OPAQUE_BLACK = 0xff000000.toInt()

    private val CODE_LENGTH_ORDER = intArrayOf(17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    private val CODE_TO_PLANE = intArrayOf(
        0x18, 0x07, 0x17, 0x19, 0x28, 0x06, 0x27, 0x29, 0x16, 0x1a,
        0x26, 0x2a, 0x38, 0x05, 0x37, 0x39, 0x15, 0x1b, 0x36, 0x3a,
        0x25, 0x2b, 0x48, 0x04, 0x47, 0x49, 0x14, 0x1c, 0x35, 0x3b,
        0x46, 0x4a, 0x24, 0x2c, 0x58, 0x45, 0x4b, 0x34, 0x3c, 0x03,
        0x57, 0x59, 0x13, 0x1d, 0x56, 0x5a, 0x23, 0x2d, 0x44, 0x4c,
        0x55, 0x5b, 0x33, 0x3d, 0x68, 0x02, 0x67, 0x69, 0x12, 0x1e,
        0x66, 0x6a, 0x22, 0x2e, 0x54, 0x5c, 0x43, 0x4d, 0x65, 0x6b,
        0x32, 0x3e, 0x78, 0x01, 0x77, 0x79, 0x53, 0x5d, 0x11, 0x1f,
        0x64, 0x6c, 0x42, 0x4e, 0x76, 0x7a, 0x21, 0x2f, 0x75, 0x7b,
        0x31, 0x3f, 0x63, 0x6d, 0x52, 0x5e, 0x00, 0x74, 0x7c, 0x41,
        0x4f, 0x10, 0x20, 0x62, 0x6e, 0x30, 0x73, 0x7d, 0x51, 0x5f,
        0x40, 0x72, 0x7e, 0x61, 0x6f, 0x50, 0x71, 0x7f, 0x60, 0x70,
    )

    fun decode(bytes: ByteArray, offset: Int, length: Int): WebpLosslessImage {
        val reader = BitReader(bytes, offset, offset + length)
        require(reader.read(8) == SIGNATURE) { "not a VP8L stream" }
        val width = reader.read(14) + 1
        val height = reader.read(14) + 1
        reader.read(1)
        require(reader.read(3) == 0) { "unknown VP8L version" }
        return WebpLosslessImage(width, height, decodeImage(reader, width, height, topLevel = true))
    }

    private class Transform(val type: Int, val width: Int, val bits: Int = 0, val data: IntArray? = null)

    private fun decodeImage(reader: BitReader, width: Int, height: Int, topLevel: Boolean): IntArray {
        var xSize = width
        val transforms = ArrayList<Transform>()
        if (topLevel) {
            while (reader.read(1) == 1) {
                when (val type = reader.read(2)) {
                    PREDICTOR, CROSS_COLOR -> {
                        val bits = reader.read(3) + 2
                        val data = decodeImage(reader, subSize(xSize, bits), subSize(height, bits), false)
                        transforms += Transform(type, xSize, bits, data)
                    }
                    SUBTRACT_GREEN -> transforms += Transform(type, xSize)
                    else -> {
                        val size = reader.read(8) + 1
                        val table = decodeImage(reader, size, 1, false)
                        for (i in 1 until size) table[i] = addPixels(table[i], table[i - 1])
                        val bits = when {
                            size <= 2 -> 3
                            size <= 4 -> 2
                            size <= 16 -> 1
                            else -> 0
                        }
                        transforms += Transform(type, xSize, bits, table)
                        xSize = subSize(xSize, bits)
                    }
                }
            }
        }

        val cacheBits = if (reader.read(1) == 1) reader.read(4).also { require(it in 1..11) { "bad color cache size" } } else 0
        var prefixBits = 0
        var entropy: IntArray? = null
        var groupCount = 1
        if (topLevel && reader.read(1) == 1) {
            prefixBits = reader.read(3) + 2
            val image = decodeImage(reader, subSize(xSize, prefixBits), subSize(height, prefixBits), false)
            for (i in image.indices) {
                image[i] = (image[i] shr 8) and 0xffff
                if (image[i] + 1 > groupCount) groupCount = image[i] + 1
            }
            entropy = image
        }
        val cacheSize = if (cacheBits > 0) 1 shl cacheBits else 0
        val groups = Array(groupCount) {
            arrayOf(
                readCode(reader, NUM_LITERAL_CODES + NUM_LENGTH_CODES + cacheSize),
                readCode(reader, NUM_LITERAL_CODES),
                readCode(reader, NUM_LITERAL_CODES),
                readCode(reader, NUM_LITERAL_CODES),
                readCode(reader, NUM_DISTANCE_CODES),
            )
        }

        val pixels = decodePixels(reader, xSize, height, groups, entropy, prefixBits, cacheBits)
        var image = pixels
        for (transform in transforms.asReversed()) image = invert(transform, image, height)
        return image
    }

    private fun decodePixels(
        reader: BitReader,
        width: Int,
        height: Int,
        groups: Array<Array<HuffmanCode>>,
        entropy: IntArray?,
        prefixBits: Int,
        cacheBits: Int,
    ): IntArray {
        val total = width * height
        val pixels = IntArray(total)
        val cache = if (cacheBits > 0) IntArray(1 shl cacheBits) else null
        val cacheShift = 32 - cacheBits
        val entropyWidth = subSize(width, prefixBits)
        var i = 0
        var cached = 0
        while (i < total) {
            val group = if (entropy == null) groups[0] else groups[entropy[((i / width) shr prefixBits) * entropyWidth + ((i % width) shr prefixBits)]]
            val green = group[0].read(reader)
            when {
                green < NUM_LITERAL_CODES -> {
                    val red = group[1].read(reader)
                    val blue = group[2].read(reader)
                    val alpha = group[3].read(reader)
                    pixels[i++] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
                green < NUM_LITERAL_CODES + NUM_LENGTH_CODES -> {
                    val length = prefixValue(reader, green - NUM_LITERAL_CODES)
                    val distance = planeDistance(width, prefixValue(reader, group[4].read(reader)))
                    require(distance <= i && i + length <= total) { "backward reference out of bounds" }
                    for (k in 0 until length) {
                        pixels[i] = pixels[i - distance]
                        i++
                    }
                }
                else -> {
                    requireNotNull(cache) { "color cache used without one" }
                    while (cached < i) insert(cache, cacheShift, pixels[cached++])
                    pixels[i++] = cache[green - NUM_LITERAL_CODES - NUM_LENGTH_CODES]
                }
            }
            if (cache != null) while (cached < i) insert(cache, cacheShift, pixels[cached++])
        }
        return pixels
    }

    private fun insert(cache: IntArray, shift: Int, argb: Int) {
        cache[(0x1e35a7bd * argb) ushr shift] = argb
    }

    private fun prefixValue(reader: BitReader, code: Int): Int {
        if (code < 4) return code + 1
        val extra = (code - 2) shr 1
        val offset = (2 + (code and 1)) shl extra
        return offset + reader.read(extra) + 1
    }

    private fun planeDistance(width: Int, code: Int): Int {
        if (code > 120) return code - 120
        val plane = CODE_TO_PLANE[code - 1]
        val distance = (plane shr 4) * width + 8 - (plane and 0xf)
        return if (distance >= 1) distance else 1
    }

    private fun invert(transform: Transform, image: IntArray, height: Int): IntArray {
        val width = transform.width
        when (transform.type) {
            SUBTRACT_GREEN -> for (i in image.indices) {
                val p = image[i]
                val green = (p shr 8) and 0xff
                val red = (((p shr 16) and 0xff) + green) and 0xff
                val blue = ((p and 0xff) + green) and 0xff
                image[i] = (p and 0xff00ff00.toInt()) or (red shl 16) or blue
            }
            PREDICTOR -> invertPredictor(image, width, height, transform.bits, transform.data!!)
            CROSS_COLOR -> invertCrossColor(image, width, height, transform.bits, transform.data!!)
            else -> return invertColorIndexing(image, width, height, transform.bits, transform.data!!)
        }
        return image
    }

    private fun invertPredictor(image: IntArray, width: Int, height: Int, bits: Int, modes: IntArray) {
        val blocksWide = subSize(width, bits)
        image[0] = addPixels(image[0], OPAQUE_BLACK)
        for (x in 1 until width) image[x] = addPixels(image[x], image[x - 1])
        for (y in 1 until height) {
            val row = y * width
            image[row] = addPixels(image[row], image[row - width])
            for (x in 1 until width) {
                val i = row + x
                val mode = (modes[(y shr bits) * blocksWide + (x shr bits)] shr 8) and 0xf
                val left = image[i - 1]
                val top = image[i - width]
                val topRight = image[i - width + 1]
                val topLeft = image[i - width - 1]
                val prediction = when (mode) {
                    0 -> OPAQUE_BLACK
                    1 -> left
                    2 -> top
                    3 -> topRight
                    4 -> topLeft
                    5 -> average(average(left, topRight), top)
                    6 -> average(left, topLeft)
                    7 -> average(left, top)
                    8 -> average(topLeft, top)
                    9 -> average(top, topRight)
                    10 -> average(average(left, topLeft), average(top, topRight))
                    11 -> select(left, top, topLeft)
                    12 -> clampAddSubtractFull(left, top, topLeft)
                    13 -> clampAddSubtractHalf(average(left, top), topLeft)
                    else -> OPAQUE_BLACK
                }
                image[i] = addPixels(image[i], prediction)
            }
        }
    }

    private fun invertCrossColor(image: IntArray, width: Int, height: Int, bits: Int, elements: IntArray) {
        val blocksWide = subSize(width, bits)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val e = elements[(y shr bits) * blocksWide + (x shr bits)]
                val greenToRed = (e and 0xff).toByte().toInt()
                val greenToBlue = ((e shr 8) and 0xff).toByte().toInt()
                val redToBlue = ((e shr 16) and 0xff).toByte().toInt()
                val i = y * width + x
                val p = image[i]
                val green = ((p shr 8) and 0xff).toByte().toInt()
                val red = (((p shr 16) and 0xff) + ((greenToRed * green) shr 5)) and 0xff
                val blue = ((p and 0xff) + ((greenToBlue * green) shr 5) + ((redToBlue * red.toByte().toInt()) shr 5)) and 0xff
                image[i] = (p and 0xff00ff00.toInt()) or (red shl 16) or blue
            }
        }
    }

    private fun invertColorIndexing(packed: IntArray, width: Int, height: Int, bits: Int, table: IntArray): IntArray {
        val packedWidth = subSize(width, bits)
        val out = IntArray(width * height)
        val bitsPerPixel = 8 shr bits
        val mask = (1 shl bitsPerPixel) - 1
        val perByte = (1 shl bits) - 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = (packed[y * packedWidth + (x shr bits)] shr 8) and 0xff
                val index = (p shr ((x and perByte) * bitsPerPixel)) and mask
                out[y * width + x] = if (index < table.size) table[index] else 0
            }
        }
        return out
    }

    private fun readCode(reader: BitReader, alphabetSize: Int): HuffmanCode {
        val lengths = IntArray(alphabetSize)
        if (reader.read(1) == 1) {
            val symbols = reader.read(1) + 1
            val first = reader.read(if (reader.read(1) == 1) 8 else 1)
            require(first < alphabetSize) { "bad simple code" }
            lengths[first] = 1
            if (symbols == 2) {
                val second = reader.read(8)
                require(second < alphabetSize) { "bad simple code" }
                lengths[second] = 1
            }
            return HuffmanCode(lengths)
        }

        val codeLengthLengths = IntArray(CODE_LENGTH_CODES)
        val count = reader.read(4) + 4
        for (i in 0 until count) codeLengthLengths[CODE_LENGTH_ORDER[i]] = reader.read(3)
        val codeLengthCode = HuffmanCode(codeLengthLengths)

        var maxSymbol = alphabetSize
        if (reader.read(1) == 1) {
            val lengthBits = 2 + 2 * reader.read(3)
            maxSymbol = 2 + reader.read(lengthBits)
            require(maxSymbol <= alphabetSize) { "bad code length count" }
        }
        var symbol = 0
        var previous = 8
        while (symbol < alphabetSize) {
            if (maxSymbol-- == 0) break
            val code = codeLengthCode.read(reader)
            if (code < 16) {
                lengths[symbol++] = code
                if (code != 0) previous = code
                continue
            }
            val (extraBits, base, value) = when (code) {
                16 -> Triple(2, 3, previous)
                17 -> Triple(3, 3, 0)
                else -> Triple(7, 11, 0)
            }
            val repeat = base + reader.read(extraBits)
            require(symbol + repeat <= alphabetSize) { "code lengths overflow" }
            repeat(repeat) { lengths[symbol++] = value }
        }
        return HuffmanCode(lengths)
    }

    private class HuffmanCode(lengths: IntArray) {
        private val single: Int
        private val counts = IntArray(16)
        private val firstCode = IntArray(16)
        private val firstIndex = IntArray(16)
        private val sorted: IntArray
        private val fast = IntArray(1 shl FAST_BITS) { -1 }

        init {
            var used = 0
            var last = 0
            for (s in lengths.indices) {
                val length = lengths[s]
                if (length > 0) {
                    require(length <= 15) { "code too long" }
                    counts[length]++
                    used++
                    last = s
                }
            }
            single = if (used <= 1) last else -1
            sorted = IntArray(used)
            var code = 0
            var index = 0
            for (length in 1..15) {
                firstCode[length] = code
                firstIndex[length] = index
                code = (code + counts[length]) shl 1
                index += counts[length]
            }
            val next = firstIndex.copyOf()
            val nextCode = firstCode.copyOf()
            for (s in lengths.indices) {
                val length = lengths[s]
                if (length == 0) continue
                sorted[next[length]++] = s
                val c = nextCode[length]++
                if (length <= FAST_BITS) {
                    val reversed = Integer.reverse(c) ushr (32 - length)
                    var fill = reversed
                    while (fill < (1 shl FAST_BITS)) {
                        fast[fill] = (s shl 4) or length
                        fill += 1 shl length
                    }
                }
            }
        }

        fun read(reader: BitReader): Int {
            if (single >= 0) return single
            val entry = fast[reader.peek(FAST_BITS)]
            if (entry >= 0) {
                reader.skip(entry and 0xf)
                return entry shr 4
            }
            var code = 0
            for (length in 1..15) {
                code = (code shl 1) or reader.read(1)
                val offset = code - firstCode[length]
                if (offset in 0 until counts[length]) return sorted[firstIndex[length] + offset]
            }
            error("invalid prefix code")
        }

        companion object {
            const val FAST_BITS = 8
        }
    }

    private class BitReader(private val bytes: ByteArray, private var pos: Int, private val end: Int) {
        private var buffer = 0L
        private var count = 0

        private fun fill() {
            while (count <= 56) {
                val b = if (pos < end) bytes[pos].toLong() and 0xff else 0L
                pos++
                buffer = buffer or (b shl count)
                count += 8
            }
        }

        fun peek(bits: Int): Int {
            if (count < bits) fill()
            return (buffer and ((1L shl bits) - 1)).toInt()
        }

        fun skip(bits: Int) {
            if (count < bits) fill()
            buffer = buffer ushr bits
            count -= bits
            require(pos - (count shr 3) <= end + 8) { "truncated VP8L stream" }
        }

        fun read(bits: Int): Int {
            if (bits == 0) return 0
            val value = peek(bits)
            skip(bits)
            return value
        }
    }

    private fun subSize(size: Int, bits: Int): Int = (size + (1 shl bits) - 1) shr bits

    private fun addPixels(a: Int, b: Int): Int {
        val alphaGreen = (a and 0xff00ff00.toInt()) + (b and 0xff00ff00.toInt())
        val redBlue = (a and 0x00ff00ff) + (b and 0x00ff00ff)
        return (alphaGreen and 0xff00ff00.toInt()) or (redBlue and 0x00ff00ff)
    }

    private fun average(a: Int, b: Int): Int =
        (((a xor b) and 0xfefefefe.toInt()) ushr 1) + (a and b)

    private fun channel(p: Int, shift: Int): Int = (p ushr shift) and 0xff

    private fun select(left: Int, top: Int, topLeft: Int): Int {
        var toLeft = 0
        var toTop = 0
        for (shift in 0..24 step 8) {
            toLeft += abs(channel(top, shift) - channel(topLeft, shift))
            toTop += abs(channel(left, shift) - channel(topLeft, shift))
        }
        return if (toLeft < toTop) left else top
    }

    private fun clampAddSubtractFull(a: Int, b: Int, c: Int): Int {
        var out = 0
        for (shift in 0..24 step 8) {
            out = out or ((channel(a, shift) + channel(b, shift) - channel(c, shift)).coerceIn(0, 255) shl shift)
        }
        return out
    }

    private fun clampAddSubtractHalf(a: Int, b: Int): Int {
        var out = 0
        for (shift in 0..24 step 8) {
            val ac = channel(a, shift)
            out = out or ((ac + (ac - channel(b, shift)) / 2).coerceIn(0, 255) shl shift)
        }
        return out
    }
}
