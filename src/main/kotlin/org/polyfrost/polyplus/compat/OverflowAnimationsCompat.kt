package org.polyfrost.polyplus.compat

//? if = 1.8.9 {
/*import java.lang.reflect.Method

object OverflowAnimationsCompat {
    private val oldSneakModel: Method? by lazy {
        Reflect.find("org.polyfrost.overflowanimations.legacy.model.LegacyEyeHeight")?.let { Reflect.method(it, "isOldSneakModel") }
    }

    fun dropsSneakOffset(): Boolean = runCatching { oldSneakModel?.invoke(null) == true }.getOrDefault(false)
}
*///?}
