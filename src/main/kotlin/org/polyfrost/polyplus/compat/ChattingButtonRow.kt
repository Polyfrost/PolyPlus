package org.polyfrost.polyplus.compat

import java.lang.reflect.Method
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager

internal object ChattingButtonRow {

    const val SIZE = 9

    private const val MOD_ID = "chatting"
    private const val STEP = SIZE + 2
    private const val RIGHT_MARGIN = 12
    private const val ROW_BOTTOM_MARGIN = 26
    private const val ROW_Y_OFFSET = 2

    private val GLOBAL_BUTTON_TOGGLES = listOf("getChatScreenshot", "getChatDeleteHistory", "getChatSearch")

    private val logger = LogManager.getLogger("PolyPlus/ChattingButtonRow")

    private var api: Api? =
        if (FabricLoader.getInstance().isModLoaded(MOD_ID)) runCatching { Api() }.getOrNull() else null

    data class Slot(val x: Int, val y: Int)

    fun slot(): Slot? = guarded {
        val window = Minecraft.getInstance().window
        Slot(
            x = window.guiScaledWidth - RIGHT_MARGIN - it.buttonCount() * STEP,
            y = window.guiScaledHeight - ROW_BOTTOM_MARGIN + ROW_Y_OFFSET,
        )
    }

    fun background(hovered: Boolean): Int? = guarded { it.background(hovered) }

    private fun <T> guarded(block: (Api) -> T): T? {
        val current = api ?: return null
        return runCatching { block(current) }
            .onFailure {
                api = null
                logger.warn("Chatting is loaded but its button row could not be read, using the standalone emoji button", it)
            }
            .getOrNull()
    }

    private class Api {
        private val config: Any
        private val background: Method
        private val hoveredBackground: Method
        private val argb: Method
        private val buttonCount: Method?
        private val buttonToggles: List<Method>

        init {
            val configClass = Class.forName("org.polyfrost.chatting.config.ChattingConfig")
            config = configClass.getField("INSTANCE").get(null)
            background = configClass.getMethod("getChatButtonBackgroundColor")
            hoveredBackground = configClass.getMethod("getChatButtonHoveredBackgroundColor")
            argb = background.returnType.getMethod("getArgb")
            buttonCount = runCatching {
                Class.forName("org.polyfrost.chatting.chat.ChatButtons").getMethod("globalButtonCount")
            }.getOrNull()
            buttonToggles = if (buttonCount != null) emptyList() else GLOBAL_BUTTON_TOGGLES.map(configClass::getMethod)
        }

        fun buttonCount(): Int =
            buttonCount?.let { it.invoke(null) as Int } ?: buttonToggles.count { it.invoke(config) == true }

        fun background(hovered: Boolean): Int =
            argb.invoke((if (hovered) hoveredBackground else background).invoke(config)) as Int
    }
}
