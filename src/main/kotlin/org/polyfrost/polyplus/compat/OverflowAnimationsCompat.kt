package org.polyfrost.polyplus.compat

//? if = 1.8.9 {
/*import org.apache.logging.log4j.LogManager
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object OverflowAnimationsCompat {
    private val logger = LogManager.getLogger("PolyPlus/OverflowAnimationsCompat")

    private val oldSneakModel: MethodHandle? by lazy {
        if (!Mods.overflowAnimations) return@lazy null
        runCatching {
            MethodHandles.publicLookup().findStatic(
                Class.forName("org.polyfrost.overflowanimations.legacy.model.LegacyEyeHeight"),
                "isOldSneakModel",
                MethodType.methodType(Boolean::class.javaPrimitiveType),
            )
        }.onFailure { logger.warn("OverflowAnimations is loaded but its sneak model setting could not be read", it) }.getOrNull()
    }

    fun dropsSneakOffset(): Boolean = runCatching { oldSneakModel?.invoke() as? Boolean ?: false }.getOrDefault(false)
}
*///?}
