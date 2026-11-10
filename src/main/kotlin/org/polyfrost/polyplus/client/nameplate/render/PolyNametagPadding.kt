package org.polyfrost.polyplus.client.nameplate.render

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import java.lang.reflect.Method
import kotlin.math.roundToInt

object PolyNametagPadding {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private var config: Config? = if (FabricLoader.getInstance().isModLoaded("polynametag")) Config.find() else null

    fun backgroundRect(width: Int, height: Int): PixelRect {
        val vanilla = PixelRect.vanillaBackground(width, height)
        val found = config ?: return vanilla
        return try {
            if (!found.padded()) {
                return vanilla
            }
            val padX = (found.paddingX.invoke(found.instance) as Float).roundToInt()
            val padY = (found.paddingY.invoke(found.instance) as Float).roundToInt()
            vanilla.expandBy(padX, padY)
        } catch (e: ReflectiveOperationException) {
            config = null
            LOGGER.warn("PolyNametag padding could not be read; nameplates use vanilla's background rect", e)
            vanilla
        }
    }

    private fun PixelRect.expandBy(dx: Int, dy: Int): PixelRect = PixelRect(x0 - dx, y0 - dy, x1 + dx, y1 + dy)

    private class Config(
        val instance: Any,
        private val enabled: Method,
        private val background: Method,
        val paddingX: Method,
        val paddingY: Method,
    ) {
        fun padded(): Boolean = enabled.invoke(instance) as Boolean && background.invoke(instance) as Boolean

        companion object {
            fun find(): Config? = try {
                val type = Class.forName("org.polyfrost.polynametag.client.PolyNametagConfig")
                Config(
                    type.getField("INSTANCE").get(null),
                    type.getMethod("isEnabled"),
                    type.getMethod("getBackground"),
                    type.getMethod("getPaddingX"),
                    type.getMethod("getPaddingY"),
                )
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("PolyNametag config not found; nameplates use vanilla's background rect", e)
                null
            } catch (e: LinkageError) {
                LOGGER.warn("PolyNametag config not found; nameplates use vanilla's background rect", e)
                null
            }
        }
    }
}
