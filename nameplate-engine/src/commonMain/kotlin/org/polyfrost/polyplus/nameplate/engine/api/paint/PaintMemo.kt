package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState

class PaintMemo(val bundle: NameplateBundle, val variant: NameplateBundle.Variant, val seed: Int) {
    private val opsInputs = Inputs()
    private val paletteInputs = Inputs()
    private var reactedFrom: ResolvedPalette? = null
    private var reactedFrame = 0
    private var reactedState: TagState = TagState.NONE

    var ops: List<LayerPainter.Painted> = emptyList()
        private set
    var opsDropped: Boolean = false
        private set
    var palette: ResolvedPalette = ResolvedPalette.EMPTY
        private set
    var paletteDropped: Boolean = false
        private set
    var reacted: ResolvedPalette = ResolvedPalette.EMPTY
        private set

    fun hasOps(block: TagBlock, frame: Int, state: TagState, detail: Detail, generation: Int): Boolean =
        opsInputs.same(block, frame, state, detail, generation)

    fun keepOps(
        block: TagBlock,
        frame: Int,
        state: TagState,
        detail: Detail,
        generation: Int,
        ops: List<LayerPainter.Painted>,
        dropped: Boolean,
    ) {
        opsInputs.set(block, frame, state, detail, generation)
        this.ops = ops
        opsDropped = dropped
    }

    fun hasPalette(block: TagBlock, frame: Int, generation: Int): Boolean =
        paletteInputs.same(block, frame, TagState.NONE, Detail.FULL, generation)

    fun keepPalette(block: TagBlock, frame: Int, generation: Int, palette: ResolvedPalette, dropped: Boolean) {
        paletteInputs.set(block, frame, TagState.NONE, Detail.FULL, generation)
        this.palette = palette
        paletteDropped = dropped
    }

    fun hasReacted(looped: ResolvedPalette, frame: Int, state: TagState): Boolean =
        looped === reactedFrom && frame == reactedFrame && (state === reactedState || state == reactedState)

    fun keepReacted(looped: ResolvedPalette, frame: Int, state: TagState, reacted: ResolvedPalette) {
        reactedFrom = looped
        reactedFrame = frame
        reactedState = state
        this.reacted = reacted
    }

    private class Inputs {
        private var frame = 0
        private var state: TagState = TagState.NONE
        private var detail = Detail.FULL
        private var generation = 0
        private var width = 0
        private var height = 0
        private var lineCount = 0
        private var nameX = 0
        private var nameY = 0
        private var nameWidth = 0
        private var prefixWidth = 0
        private var background: PixelRect? = null

        fun same(block: TagBlock, frame: Int, state: TagState, detail: Detail, generation: Int): Boolean =
            frame == this.frame &&
                detail == this.detail &&
                generation == this.generation &&
                block.width == width &&
                block.height == height &&
                block.lineCount == lineCount &&
                block.nameX == nameX &&
                block.nameLineY == nameY &&
                block.nameWidth == nameWidth &&
                block.prefixWidth == prefixWidth &&
                block.background == background &&
                (state === this.state || state == this.state)

        fun set(block: TagBlock, frame: Int, state: TagState, detail: Detail, generation: Int) {
            this.frame = frame
            this.state = state
            this.detail = detail
            this.generation = generation
            width = block.width
            height = block.height
            lineCount = block.lineCount
            nameX = block.nameX
            nameY = block.nameLineY
            nameWidth = block.nameWidth
            prefixWidth = block.prefixWidth
            background = block.background
        }
    }
}
