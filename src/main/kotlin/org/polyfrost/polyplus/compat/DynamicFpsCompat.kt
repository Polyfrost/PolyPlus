package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.utils.AnimationActivity
import java.lang.reflect.Method

object DynamicFpsCompat {
    private val logger = LogManager.getLogger("PolyPlus/DynamicFpsCompat")
    //? if > 1.8.9 {
    private const val MOD_CLASS = "dynamic_fps.impl.DynamicFPSMod"
    //?} else {
    /*private const val MOD_CLASS = "net.lostluma.dynamic_fps.impl.fabric.DynamicFPSMod"
    *///?}

    @Volatile
    private var holding = false
    private var broken = false
    private var refresh: Method? = null
    private var looked = false

    @JvmStatic
    fun holding(): Boolean = holding

    @JvmStatic
    fun frame() {
        if (broken) return
        val now = try {
            AnimationActivity.update()
        } catch (e: Throwable) {
            broken = true
            logger.warn("Could not read animation state; Dynamic FPS will no longer be paused during animations", e)
            false
        }
        if (now == holding) return
        holding = now
        reevaluate()
    }

    private fun reevaluate() {
        val method = method() ?: return
        try {
            method.invoke(null, false)
        } catch (e: Throwable) {
            broken = true
            holding = false
            logger.warn("Could not refresh the Dynamic FPS state", e)
        }
    }

    private fun method(): Method? {
        if (looked) return refresh
        looked = true
        refresh = try {
            Class.forName(MOD_CLASS).getMethod("onStatusChanged", Boolean::class.javaPrimitiveType)
        } catch (e: Throwable) {
            broken = true
            holding = false
            logger.warn("Dynamic FPS has no onStatusChanged hook; animations may run at a reduced frame rate", e)
            null
        }
        return refresh
    }
}
