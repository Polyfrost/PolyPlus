package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import java.lang.reflect.Method

object NotEnoughAnimationsCompat {
    private const val STATE_CLASS = "dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState"

    private val hook = ReflectiveHook(LogManager.getLogger("PolyPlus/NotEnoughAnimationsCompat"))
    private var setEntity: Method? = null
    private var looked = false

    @JvmStatic
    fun detach(state: Any?) {
        if (state == null || hook.broken || !Mods.notEnoughAnimations) return
        val method = method(state) ?: return
        if (!method.declaringClass.isInstance(state)) return
        hook.attempt("Could not keep Not Enough Animations off lobby and preview players", Unit) {
            method.invoke(state, *arrayOf<Any?>(null))
        }
    }

    private fun method(state: Any): Method? {
        if (looked) return setEntity
        looked = true
        setEntity = hook.attempt("Not Enough Animations has no render state entity hook; lobby and preview poses may be overridden", null) {
            Reflect.methodByArity(Class.forName(STATE_CLASS, false, state.javaClass.classLoader), "setEntity", 1)
        }
        return setEntity
    }
}
