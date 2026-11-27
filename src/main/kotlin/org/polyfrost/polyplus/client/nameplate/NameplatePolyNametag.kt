package org.polyfrost.polyplus.client.nameplate

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polynametag.api.PolyNametagApi
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawTag
import kotlin.math.roundToInt
//? if = 1.8.9 {
/*import net.minecraft.entity.Entity
*///?}

object NameplatePolyNametag {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val MOD_ID = "polynametag"
    private const val API_VERSION = 1
    private const val BACKGROUND_API_VERSION = 2
    private const val RGB = 0xFFFFFF
    private const val ALPHA = -0x1000000
    private const val VANILLA_RGB = 0xFFFFFF
    private const val PROBE_BACKGROUND = 0x40000000

    //? if >= 26.3 {
    private const val CAPTURED_STYLED = true
    //?} else {
    /*private const val CAPTURED_STYLED = false
    *///?}

    val installed: Boolean = runCatching { FabricLoader.getInstance().isModLoaded(MOD_ID) }.getOrDefault(false)

    @Volatile
    private var usable: Boolean = detect()

    @Volatile
    private var yieldable: Boolean = usable && version() >= BACKGROUND_API_VERSION
    private var yielded = false

    var textRgb: Int = VANILLA_RGB
        private set
    var textArgb: Int = ALPHA or VANILLA_RGB
        private set
    var shadow: Boolean = false
        private set
    var dy: Float = 0f
        private set

    private var ownBackground = false
    private var backgroundX0 = 0
    private var backgroundY0 = 0
    private var backgroundX1 = 0
    private var backgroundY1 = 0

    fun applyTo(tag: NameplateDrawTag) {
        if (textRgb != VANILLA_RGB) tag.externalColor = textRgb
        if (!ownBackground) return
        tag.backgroundX0 = backgroundX0
        tag.backgroundY0 = backgroundY0
        tag.backgroundX1 = backgroundX1
        tag.backgroundY1 = backgroundY1
    }

    fun style(color: Int, width: Int): Boolean {
        textArgb = color
        textRgb = color and RGB
        shadow = false
        dy = 0f
        ownBackground = false
        if (!usable) return false
        val half = width / 2f
        val style = try {
            PolyNametagApi.style(color, false, PROBE_BACKGROUND, false, -half, 0f, width.toFloat())
        } catch (e: LinkageError) {
            disable(e)
            return false
        } catch (e: RuntimeException) {
            LOGGER.warn("PolyNametag could not style a name tag", e)
            return false
        }
        val textY = style.textY
        textArgb = style.textColor
        textRgb = style.textColor and RGB
        shadow = style.textShadow
        dy = if (CAPTURED_STYLED) 0f else textY
        ownBackground = style.ownBackground
        if (ownBackground) {
            backgroundX0 = (style.backgroundLeft + half).roundToInt()
            backgroundY0 = (style.backgroundTop - textY).roundToInt()
            backgroundX1 = (style.backgroundRight + half).roundToInt()
            backgroundY1 = (style.backgroundBottom - textY).roundToInt()
        }
        return true
    }

    fun yieldBackground(yield: Boolean) {
        if (!yieldable || yielded == yield) return
        try {
            PolyNametagApi.yieldBackground(yield)
            yielded = yield
        } catch (e: LinkageError) {
            yieldable = false
            LOGGER.warn("PolyNametag could not hand its name tag background to nameplates", e)
        }
    }

    //? if = 1.8.9 {
    /*fun lowersSneaking(entity: Entity): Boolean {
        if (!usable) return false
        return try {
            PolyNametagApi.lowersSneakingNametag(entity)
        } catch (e: LinkageError) {
            disable(e)
            false
        }
    }
    *///?}

    private fun version(): Int = try {
        PolyNametagApi.VERSION
    } catch (e: LinkageError) {
        0
    }

    private fun detect(): Boolean {
        if (!installed) return false
        return try {
            PolyNametagApi.VERSION >= API_VERSION
        } catch (e: LinkageError) {
            LOGGER.info("This PolyNametag has no styling API; nameplates use vanilla's name tag style")
            false
        }
    }

    private fun disable(e: LinkageError) {
        if (!usable) return
        usable = false
        LOGGER.warn("PolyNametag's styling API could not be reached; nameplates use vanilla's name tag style", e)
    }
}
