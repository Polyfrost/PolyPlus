package org.polyfrost.polyplus.nameplate.engine.internal.layout

import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling

internal class BlockAnchor(private val anchor: Anchor, private val dx: Int, private val dy: Int) : BadgeStyling.Anchor {
    override fun x(block: TagBlock): Int = anchor.x(block.rect()) - BadgeStyling.CELLS_WIDE / 2 + dx

    override fun y(block: TagBlock): Int = anchor.y(block.rect()) - BadgeStyling.CELLS_HIGH / 2 + dy
}
