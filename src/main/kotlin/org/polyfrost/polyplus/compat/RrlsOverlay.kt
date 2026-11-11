package org.polyfrost.polyplus.compat

import java.lang.reflect.Method

object RrlsOverlay {
    private val getState: Method? = method("org.redlance.dima_dencep.mods.rrls.duck.OverlayExtender", "rrls\$getState")
    private val isRendering: Method? = method("org.redlance.dima_dencep.mods.rrls.utils.OverlayHelper\$State", "isRendering")

    @JvmStatic
    fun hides(overlay: Any): Boolean {
        val getState = getState?.takeIf { it.declaringClass.isInstance(overlay) } ?: return false
        return runCatching { isRendering?.invoke(getState.invoke(overlay)) == true }.getOrDefault(false)
    }

    private fun method(owner: String, name: String): Method? =
        runCatching { Class.forName(owner, false, RrlsOverlay::class.java.classLoader).getMethod(name) }.getOrNull()
}
