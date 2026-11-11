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
        val shown = minOf(floating.size + panels.size, MAX_PANELS)
        target.putFloat(pageFrost).putFloat(chrome).putFloat(shown.toFloat()).putFloat(0f)
        var bounds = writeBounds(target, floating.values, 0)
        bounds = writeBounds(target, panels.values, bounds)
        repeat(MAX_PANELS - bounds) { target.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f) }
        var radii = writeRadii(target, floating.values, 0)
        radii = writeRadii(target, panels.values, radii)
        repeat(RADIUS_ROWS * 4 - radii) { target.putFloat(0f) }
    }

    private fun writeBounds(target: ByteBuffer, source: Collection<Panel>, start: Int): Int {
        var written = start
        for (panel in source) {
            if (written >= MAX_PANELS) break
            target.putFloat(panel.centerX).putFloat(panel.centerY).putFloat(panel.halfWidth).putFloat(panel.halfHeight)
            written++
        }
        return written
    }

    private fun writeRadii(target: ByteBuffer, source: Collection<Panel>, start: Int): Int {
        var written = start
        for (panel in source) {
            if (written >= RADIUS_ROWS * 4) break
            target.putFloat(panel.radius)
            written++
        }
        return written
    }
}
