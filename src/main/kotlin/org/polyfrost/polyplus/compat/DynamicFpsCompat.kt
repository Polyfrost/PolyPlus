package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.utils.AnimationActivity
import java.lang.reflect.Method

object DynamicFpsCompat {
    private val hook = ReflectiveHook(LogManager.getLogger("PolyPlus/DynamicFpsCompat"))
    //? if > 1.8.9 {
    private const val MOD_CLASS = "dynamic_fps.impl.DynamicFPSMod"
    //?} else {
    /*private const val MOD_CLASS = "net.lostluma.dynamic_fps.impl.fabric.DynamicFPSMod"
    *///?}

    @Volatile
    private var holding = false
    private var refresh: Method? = null
    private var looked = false

    @JvmStatic
    fun holding(): Boolean = holding

    @JvmStatic
    fun frame() {
        if (hook.broken) return
        val now = hook.attempt("Could not read animation state; Dynamic FPS will no longer be paused during animations", false) {
            AnimationActivity.update()
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
            holding = false
            hook.fail("Could not refresh the Dynamic FPS state", e)
        }
    }

    private fun method(): Method? {
        if (looked) return refresh
        looked = true
        refresh = try {
            Class.forName(MOD_CLASS).getMethod("onStatusChanged", Boolean::class.javaPrimitiveType)
        } catch (e: Throwable) {
            holding = false
            hook.fail("Dynamic FPS has no onStatusChanged hook; animations may run at a reduced frame rate", e)
            null
        }
        return refresh
    }
}
