package org.polyfrost.polyplus.client.nameplate.render

//? if = 1.8.9 {
/*import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object LegacyCeraShaders {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private const val MOD_ID = "cera"
    private const val SHADER_RENDER = "dev.rdh.cera.modules.shaders.ShaderRender"

    private class Handles(
        val managed: MethodHandle,
        val textPushed: MethodHandle,
        val pushText: MethodHandle,
        val popText: MethodHandle,
        val bindWhite: MethodHandle,
    )

    private var broken = false

    private val installed: Boolean by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }

    private val handles: Handles? by lazy {
        if (!installed) return@lazy null
        try {
            val type = Class.forName(SHADER_RENDER, false, LegacyCeraShaders::class.java.classLoader)
            val lookup = MethodHandles.publicLookup()
            val flag = MethodType.methodType(Boolean::class.javaPrimitiveType)
            val action = MethodType.methodType(Void.TYPE)
            Handles(
                lookup.findStatic(type, "isManaged", flag),
                lookup.findStatic(type, "isTextBatchPushed", flag),
                lookup.findStatic(type, "pushTextBatch", action),
                lookup.findStatic(type, "popTextBatch", action),
                lookup.findStatic(type, "bindWhiteTexture", action),
            )
        } catch (e: ReflectiveOperationException) {
            LOGGER.warn("Cera is installed but its shader text hooks could not be found; nameplates may look wrong under its shader packs", e)
            null
        } catch (e: LinkageError) {
            LOGGER.warn("Cera is installed but its shader text hooks could not be loaded; nameplates may look wrong under its shader packs", e)
            null
        }
    }

    private val shadow: MethodHandle? by lazy {
        if (handles == null) return@lazy null
        try {
            val type = Class.forName(SHADER_RENDER, false, LegacyCeraShaders::class.java.classLoader)
            MethodHandles.publicLookup().findStatic(type, "isShadowPass", MethodType.methodType(Boolean::class.javaPrimitiveType))
        } catch (e: ReflectiveOperationException) {
            LOGGER.warn("Cera is installed but its shadow pass hook could not be found; nameplate effects draw on the CPU under its shader packs", e)
            null
        } catch (e: LinkageError) {
            LOGGER.warn("Cera is installed but its shadow pass hook could not be loaded; nameplate effects draw on the CPU under its shader packs", e)
            null
        }
    }

    @JvmStatic
    fun unreadable(): Boolean = installed && (handles == null || broken)

    @JvmStatic
    fun packHooks(): Boolean = handles != null && !broken && shadow != null

    @JvmStatic
    fun shadowPass(): Boolean {
        val hook = shadow ?: return false
        if (broken) return false
        return guard(false) { hook.invoke() as Boolean }
    }

    @JvmStatic
    fun managed(): Boolean {
        val hooks = handles ?: return false
        if (broken) return false
        return guard(false) { hooks.managed.invoke() as Boolean }
    }

    @JvmStatic
    fun beginText(): Boolean {
        val hooks = handles ?: return false
        if (!managed()) return false
        return guard(false) {
            if (hooks.textPushed.invoke() as Boolean) return@guard false
            hooks.pushText.invoke()
            hooks.textPushed.invoke() as Boolean
        }
    }

    @JvmStatic
    fun endText(pushed: Boolean) {
        if (!pushed) return
        val hooks = handles ?: return
        guard(Unit) { hooks.popText.invoke() }
    }

    @JvmStatic
    fun bindWhite(): Boolean {
        val hooks = handles ?: return false
        if (!managed()) return false
        return guard(false) {
            hooks.bindWhite.invoke()
            true
        }
    }

    private inline fun <T> guard(fallback: T, call: () -> T): T = try {
        call()
    } catch (e: RuntimeException) {
        disable(e)
        fallback
    } catch (e: LinkageError) {
        disable(e)
        fallback
    }

    private fun disable(e: Throwable) {
        if (broken) return
        broken = true
        LOGGER.warn("Cera's shader text hooks failed; nameplates draw without them for the rest of this session", e)
    }
}
*///?}
