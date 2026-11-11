package org.polyfrost.polyplus.client.lobby

import java.nio.ByteBuffer

interface GlassRegistry {
    fun put(key: Any, panel: LobbyGlass.Panel)

    fun putFloating(key: Any, panel: LobbyGlass.Panel)

    fun remove(key: Any)
}

object LobbyGlass : GlassRegistry {
    private const val MAX_PANELS = 12
    private const val RADIUS_ROWS = 3
    const val BLOCK_BYTES = (1 + MAX_PANELS + RADIUS_ROWS) * 4 * Float.SIZE_BYTES

    data class Panel(val centerX: Float, val centerY: Float, val halfWidth: Float, val halfHeight: Float, val radius: Float)

    private val panels = LinkedHashMap<Any, Panel>()
    private val floating = LinkedHashMap<Any, Panel>()

    var pageFrost = 0f
    var chrome = 0f

    override fun put(key: Any, panel: Panel) {
        panels[key] = panel
    }

    override fun putFloating(key: Any, panel: Panel) {
        floating[key] = panel
    }

    override fun remove(key: Any) {
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
