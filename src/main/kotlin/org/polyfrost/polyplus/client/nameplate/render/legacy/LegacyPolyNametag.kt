package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import java.lang.reflect.Method

object LegacyPolyNametag {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val RGB = 0xFFFFFF
    private const val WHITE = -1

    private var hooks: Hooks? = if (FabricLoader.getInstance().isModLoaded("polynametag")) Hooks.find() else null

    var enabled: Boolean = false
        private set
    var offsetY: Float = 0f
        private set
    var textRgb: Int = ResolvedText.UNSET
        private set
    var textShadow: Boolean = false
        private set

    fun refresh() {
        val found = hooks
        if (found == null) {
            clear()
            return
        }
        try {
            enabled = found.enabled.invoke(found.config) as Boolean
            offsetY = found.translateY.invoke(null, 0f) as Float
            val rgb = (found.textColor.invoke(null, WHITE) as Int) and RGB
            textRgb = if (rgb == RGB) ResolvedText.UNSET else rgb
            textShadow = found.textShadow.invoke(null, false) as Boolean
        } catch (e: ReflectiveOperationException) {
            disable("PolyNametag settings could not be read; nameplates ignore its height, color and shadow", e)
        }
    }

    fun textColor(color: Int): Int {
        val found = hooks ?: return color
        return try {
            found.textColor.invoke(null, color) as Int
        } catch (e: ReflectiveOperationException) {
            disable("PolyNametag text color could not be read; nameplates use vanilla's", e)
            color
        }
    }

    private fun disable(message: String, error: ReflectiveOperationException) {
        hooks = null
        clear()
        LOGGER.warn(message, error)
    }

    private fun clear() {
        enabled = false
        offsetY = 0f
        textRgb = ResolvedText.UNSET
        textShadow = false
    }

    private class Hooks(
        val config: Any,
        val enabled: Method,
        val translateY: Method,
        val textColor: Method,
        val textShadow: Method,
    ) {
        companion object {
            fun find(): Hooks? = try {
                val config = Class.forName("org.polyfrost.polynametag.client.PolyNametagConfig")
                val renderer = Class.forName("org.polyfrost.polynametag.client.NametagRenderer")
                Hooks(
                    config.getField("INSTANCE").get(null),
                    config.getMethod("isEnabled"),
                    renderer.getMethod("translateY", Float::class.javaPrimitiveType),
                    renderer.getMethod("textColor", Int::class.javaPrimitiveType),
                    renderer.getMethod("textShadow", Boolean::class.javaPrimitiveType),
                )
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("PolyNametag hooks not found; nameplates ignore its height, color and shadow", e)
                null
            } catch (e: LinkageError) {
                LOGGER.warn("PolyNametag hooks not found; nameplates ignore its height, color and shadow", e)
                null
            }
        }
    }
}
*///?}
