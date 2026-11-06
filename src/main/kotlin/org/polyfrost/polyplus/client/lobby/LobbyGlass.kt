package org.polyfrost.polyplus.client.lobby

import java.nio.ByteBuffer

object LobbyGlass {
    private const val MAX_PANELS = 12
    private const val RADIUS_ROWS = 3
    const val BLOCK_BYTES = (1 + MAX_PANELS + RADIUS_ROWS) * 4 * Float.SIZE_BYTES

    class Panel(val centerX: Float, val centerY: Float, val halfWidth: Float, val halfHeight: Float, val radius: Float)

    private val panels = LinkedHashMap<Any, Panel>()
    private val floating = LinkedHashMap<Any, Panel>()

    var pageFrost = 0f
    var chrome = 0f

    fun put(key: Any, panel: Panel) {
        panels[key] = panel
    }

    fun putFloating(key: Any, panel: Panel) {
        floating[key] = panel
    }

    fun remove(key: Any) {
        panels.remove(key)
        floating.remove(key)
    }

    fun write(target: ByteBuffer) {
        val shown = (floating.values + panels.values).take(MAX_PANELS)
        target.putFloat(pageFrost).putFloat(chrome).putFloat(shown.size.toFloat()).putFloat(0f)
        repeat(MAX_PANELS) { index ->
            val panel = shown.getOrNull(index)
            target.putFloat(panel?.centerX ?: 0f).putFloat(panel?.centerY ?: 0f).putFloat(panel?.halfWidth ?: 0f).putFloat(panel?.halfHeight ?: 0f)
        }
        repeat(RADIUS_ROWS * 4) { index -> target.putFloat(shown.getOrNull(index)?.radius ?: 0f) }
    }
}
