package org.polyfrost.polyplus.client.privacy

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal object OpSecCompat {

    private val logger = LogManager.getLogger("PolyPlus/OpSecCompat")

    private val translationProtection: Pair<MethodHandle, MethodHandle>? by lazy {
        if (!FabricLoader.getInstance().isModLoaded("opsec")) return@lazy null
        runCatching {
            val config = Class.forName("aurick.opsec.mod.config.OpsecConfig")
            val lookup = MethodHandles.publicLookup()
            lookup.findStatic(config, "getInstance", MethodType.methodType(config)) to
                lookup.findVirtual(config, "isTranslationProtectionEnabled", MethodType.methodType(Boolean::class.javaPrimitiveType))
        }.onFailure { logger.warn("OpSec is loaded but its config could not be read", it) }.getOrNull()
    }

    fun filtersTranslations(): Boolean {
        val (getInstance, isEnabled) = translationProtection ?: return false
        return runCatching { isEnabled.invoke(getInstance.invoke()) as Boolean }.getOrDefault(false)
    }
}
