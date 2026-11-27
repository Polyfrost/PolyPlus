package org.polyfrost.polyplus.client.nameplate.material

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
//? if iris {
import net.irisshaders.iris.api.v0.IrisApi
import org.polyfrost.polyplus.compat.iris.IrisPackTarget
//?} else {
/*import java.lang.reflect.Field
*///?}
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.nameplate.render.LegacyCeraShaders
*///?}

object NameplateShaderPacks {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    const val NONE: Int = 0
    const val MAIN: Int = 1
    const val SHADOW: Int = 2

    private var probedFrame = Int.MIN_VALUE
    private val gate = NameplatePackGate({ NameplateMaterials.packTarget }, { surface }, ::reportFailure)

    //? if iris {
    private val surface: NameplatePackSurface? by lazy { if (iris) IrisPackTarget else null }

    private val iris: Boolean by lazy {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return@lazy false
        try {
            IrisApi.getInstance().minorApiRevision >= 0
        } catch (e: LinkageError) {
            LOGGER.warn("Iris is installed but its API could not be read; nameplate effects draw on the CPU under shader packs", e)
            false
        }
    }
    //?} else {
    /*private val shaderPackLoaded: Field? by lazy {
        for (name in arrayOf("net.optifine.shaders.Shaders", "shadersmod.client.Shaders")) {
            val field = try {
                Class.forName(name, false, NameplateShaderPacks::class.java.classLoader).getDeclaredField("shaderPackLoaded")
                    .also { it.isAccessible = true }
            } catch (e: ReflectiveOperationException) {
                null
            } catch (e: LinkageError) {
                null
            } catch (e: RuntimeException) {
                null
            }
            if (field != null) return@lazy field
        }
        null
    }

    private val surface: NameplatePackSurface? = null
    *///?}

    @JvmStatic
    fun probe(frame: Int) {
        if (probedFrame == frame) return
        probedFrame = frame
        gate.update(readPackInUse())
    }

    @JvmStatic
    fun state(): Int {
        if (!gate.packInUse) return NONE
        return if (shadowPass()) SHADOW else MAIN
    }

    @JvmStatic
    fun gpuAllowed(): Boolean = gate.gpuAllowed()

    @JvmStatic
    fun redirecting(): Boolean = gate.redirecting()

    @JvmStatic
    fun fogOff(): Boolean = gate.fogOff()

    @JvmStatic
    fun unlockBlend(pipeline: Any?) = gate.unlockBlend(pipeline)

    @JvmStatic
    fun redirect(pipeline: Any?) = gate.redirect(pipeline)

    @JvmStatic
    fun restore() = gate.restore()

    @JvmStatic
    fun targetFailed(e: Throwable) = gate.fail(e)

    private fun reportFailure(e: Throwable) {
        LOGGER.warn("Nameplate effects could not draw into the shader pack's text target; they draw on the CPU while a pack is on", e)
    }

    private fun readPackInUse(): Boolean {
        //? if = 1.8.9 {
        /*if (LegacyCeraShaders.managed() || LegacyCeraShaders.unreadable()) return true
        *///?}
        //? if iris {
        if (!iris) return false
        return try {
            IrisApi.getInstance().isShaderPackInUse
        } catch (e: RuntimeException) {
            false
        }
        //?} else {
        /*val field = shaderPackLoaded ?: return false
        return runCatching { field.getBoolean(null) }.getOrDefault(false)
        *///?}
    }

    private fun shadowPass(): Boolean {
        //? if = 1.8.9 {
        /*if (LegacyCeraShaders.shadowPass()) return true
        *///?}
        //? if iris {
        if (!iris) return false
        return try {
            IrisApi.getInstance().isRenderingShadowPass
        } catch (e: RuntimeException) {
            false
        }
        //?} else {
        /*return false
        *///?}
    }
}

interface NameplatePackTarget {
    fun resolve(): Boolean
    fun owns(pipeline: Any): Boolean
}
