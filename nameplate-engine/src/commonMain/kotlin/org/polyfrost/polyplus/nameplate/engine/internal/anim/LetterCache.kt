package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.internal.text.LetterProgram

internal class LetterCache {
    private val slots = arrayOfNulls<Slot>(CAPACITY)
    private val key = IntArray(KEY_SIZE)
    private var keyLength = 0
    private var clock = 0

    fun lookup(
        program: LetterProgram,
        variant: NameplateBundle.Variant,
        block: TagBlock,
        frame: Int,
        length: Int,
        fps: Int,
        count: Int,
        glyphX: IntArray,
        glyphWidth: IntArray,
    ): Slot {
        val owner = if (program.readsParams) variant else null
        val hash = buildKey(program, block, frame, length, fps, count, glyphX, glyphWidth)
        val now = ++clock
        val home = hash and (CAPACITY - 1)
        var victim = home
        var victimAge = -1
        for (probe in 0 until WAYS) {
            val index = (home + probe) and (CAPACITY - 1)
            val slot = slots[index]
            if (slot == null) {
                if (victimAge != Int.MAX_VALUE) {
                    victim = index
                    victimAge = Int.MAX_VALUE
                }
                continue
            }
            if (slot.matches(hash, program, owner, key, keyLength)) {
                slot.stamp = now
                return slot
            }
            val age = now - slot.stamp
            if (age > victimAge) {
                victim = index
                victimAge = age
            }
        }
        val slot = slots[victim] ?: Slot().also { slots[victim] = it }
        slot.claim(hash, program, owner, key, keyLength, now)
        return slot
    }

    private fun buildKey(
        program: LetterProgram,
        block: TagBlock,
        frame: Int,
        length: Int,
        fps: Int,
        count: Int,
        glyphX: IntArray,
        glyphWidth: IntArray,
    ): Int {
        keyLength = 0
        push(frame)
        push(length)
        push(fps)
        push(count)
        if (program.readsBlock) {
            val background = block.background
            push(block.width)
            push(block.height)
            push(block.lineCount)
            push(block.nameX)
            push(block.nameLineY)
            push(block.nameWidth)
            push(block.prefixWidth)
            push(background.x0)
            push(background.y0)
            push(background.x1)
            push(background.y1)
        }
        if (program.readsGlyphX) {
            for (i in 0 until count) {
                push(glyphX[i])
            }
        }
        if (program.readsGlyphWidth) {
            for (i in 0 until count) {
                push(glyphWidth[i])
            }
        }
        var hash = 0
        for (i in 0 until keyLength) {
            hash = hash * HASH_STEP + key[i]
        }
        return hash xor (hash ushr HASH_SHIFT)
    }

    private fun push(value: Int) {
        key[keyLength++] = value
    }

    class Slot {
        val frame: LetterFrame = LetterFrame()
        var filled: Boolean = false
            private set
        var failed: Boolean = false
            private set
        var result: Boolean = false
            private set
        var stamp: Int = 0

        private val key = IntArray(KEY_SIZE)
        private var keyLength = 0
        private var hash = 0
        private var program: LetterProgram? = null
        private var owner: NameplateBundle.Variant? = null

        fun matches(hash: Int, program: LetterProgram, owner: NameplateBundle.Variant?, key: IntArray, keyLength: Int): Boolean {
            if (!filled || this.hash != hash || this.program !== program || this.owner !== owner || this.keyLength != keyLength) {
                return false
            }
            for (i in 0 until keyLength) {
                if (this.key[i] != key[i]) return false
            }
            return true
        }

        fun claim(hash: Int, program: LetterProgram, owner: NameplateBundle.Variant?, key: IntArray, keyLength: Int, stamp: Int) {
            filled = false
            this.hash = hash
            this.program = program
            this.owner = owner
            key.copyInto(this.key, 0, 0, keyLength)
            this.keyLength = keyLength
            this.stamp = stamp
        }

        fun fill(source: LetterFrame, failed: Boolean, result: Boolean) {
            frame.copyFrom(source)
            this.failed = failed
            this.result = result
            filled = true
        }
    }

    companion object {
        const val CAPACITY = 128
        private const val WAYS = 4
        private const val KEY_SIZE = 15 + 2 * LetterFrame.MAX_GLYPHS
        private const val HASH_STEP = 31
        private const val HASH_SHIFT = 16
    }
}
