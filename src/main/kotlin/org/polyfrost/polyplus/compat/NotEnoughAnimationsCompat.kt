package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.lang.reflect.Method

object NotEnoughAnimationsCompat {
    private val logger = LogManager.getLogger("PolyPlus/NotEnoughAnimationsCompat")
    private const val MOD_ID = "notenoughanimations"
    private const val STATE_CLASS = "dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState"

    private val present by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }
    private var setEntity: Method? = null
    private var looked = false
    private var broken = false

    @JvmStatic
    fun detach(state: Any?) {
        if (state == null || broken || !present) return
        val method = method(state) ?: return
        if (!method.declaringClass.isInstance(state)) return
        try {
            method.invoke(state, *arrayOf<Any?>(null))
        } catch (e: Throwable) {
            broken = true
            logger.warn("Could not keep Not Enough Animations off lobby and preview players", e)
        }
    }

    private fun method(state: Any): Method? {
        if (looked) return setEntity
        looked = true
        setEntity = try {
            Class.forName(STATE_CLASS, false, state.javaClass.classLoader)
                .methods.first { it.name == "setEntity" && it.parameterCount == 1 }
        } catch (e: Throwable) {
            broken = true
            logger.warn("Not Enough Animations has no render state entity hook; lobby and preview poses may be overridden", e)
            null
        }
        return setEntity
    }
}
