package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.FaviconTexture
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.server.network.EventLoopGroupHolder
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import java.util.concurrent.Executors

/**
 * Quickplay as a panel like OneClient's: a "Quickplay" header that folds open into your last servers,
 * each with its icon and how many players are online, one click to join.
 */
object QuickplayPanel {
    private val LOGGER = LogManager.getLogger("PolyPlus/Quickplay")
    const val ROWS = 3
    const val ROW_HEIGHT = 26
    private const val REPING_MILLIS = 30_000L

    var expanded = true

    private val pinger = ServerStatusPinger()
    private val pool = Executors.newCachedThreadPool { Thread(it, "PolyPlus Quickplay ping").apply { isDaemon = true } }
    private val servers = LinkedHashMap<String, Pinged>()

    class Pinged(val data: ServerData) {
        var pingedAt = 0L
        var failed = false
        var iconBytes: ByteArray? = null
        var icon: FaviconTexture? = null
    }

    fun entries(): List<Pinged> {
        val recent = PolyPlusRecentServers.recent().take(ROWS)
        val now = System.currentTimeMillis()
        return recent.map { server ->
            val key = server.ip.lowercase()
            val name = if (PolyPlusRecentServers.isDefaultName(server.name)) server.ip else server.name
            val pinged = servers.getOrPut(key) { Pinged(ServerData(name, server.ip, ServerData.Type.OTHER)) }
            if (now - pinged.pingedAt > REPING_MILLIS) ping(pinged, now)
            pinged
        }
    }

    private fun ping(server: Pinged, now: Long) {
        server.pingedAt = now
        server.failed = false
        pool.execute {
            try {
                pinger.pingServer(server.data, {}, { server.failed = true }, EventLoopGroupHolder.remote(false))
            } catch (e: Exception) {
                server.failed = true
                LOGGER.debug("Couldn't ping {}", server.data.ip, e)
            }
        }
    }

    fun tick() {
        pinger.tick()
        for (server in servers.values) {
            val bytes = server.data.iconBytes ?: continue
            if (bytes.contentEquals(server.iconBytes)) continue
            server.iconBytes = bytes
            runCatching {
                val texture = server.icon ?: FaviconTexture.forServer(Minecraft.getInstance().textureManager, server.data.ip).also { server.icon = it }
                texture.upload(NativeImage.read(bytes))
            }.onFailure { LOGGER.debug("Bad server icon for {}", server.data.ip, it) }
        }
    }

    fun header(width: Int, onToggle: () -> Unit): Button =
        Button.builder(headerLabel()) { button ->
            expanded = !expanded
            button.message = headerLabel()
            onToggle()
        }.size(width, 20).build()

    private fun headerLabel(): Component = Component.literal(if (expanded) "Quickplay  ▾" else "Quickplay  ▸")

    class Row(private val parent: Screen, private val server: Pinged, width: Int) :
        AbstractWidget(0, 0, width, ROW_HEIGHT, Component.literal(server.data.name)) {

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val font = Minecraft.getInstance().font
            val hovered = isHoveredOrFocused
            graphics.fill(x, y, x + width, y + height, if (hovered) 0xC0303036.toInt() else 0xA0101014.toInt())
            if (hovered) graphics.outline(x, y, width, height, 0x80FFFFFF.toInt())
            val iconSize = 18
            val iconX = x + 4
            val iconY = y + (height - iconSize) / 2
            val icon = server.icon
            if (icon != null && server.iconBytes != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon.textureLocation(), iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
            } else {
                graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF2E3036.toInt())
                val letter = server.data.name.take(1).uppercase()
                graphics.text(font, letter, iconX + (iconSize - font.width(letter)) / 2, iconY + 5, 0xFFDDDDDD.toInt())
            }
            val textX = iconX + iconSize + 5
            val maxText = width - (textX - x) - 10
            graphics.text(font, font.plainSubstrByWidth(server.data.name, maxText), textX, y + 4, 0xFFFFFFFF.toInt())
            val players = server.data.players
            val status = when {
                players != null -> "${"%,d".format(players.online())} players online"
                server.failed || System.currentTimeMillis() - server.pingedAt > 8_000 -> "Can't connect"
                else -> "Pinging..."
            }
            graphics.text(font, font.plainSubstrByWidth(status, maxText), textX, y + 14, 0xFF9A9CA3.toInt())
            graphics.text(font, ">", x + width - 8, y + (height - 8) / 2, if (hovered) 0xFFFFFFFF.toInt() else 0xFF777777.toInt())
        }

        override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
            MultiplayerTabs.connect(parent, server.data.name, server.data.ip)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
//?}
