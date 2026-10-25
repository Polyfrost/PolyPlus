package org.polyfrost.polyplus.client.features

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.SemanticVersion
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object PylonRawInput {
    private val LOGGER = LogManager.getLogger()
    private val MIN_VERSION = SemanticVersion.parse("0.2.6")

    private val handles: Pair<MethodHandle, MethodHandle>? by lazy {
        val version = FabricLoader.getInstance().getModContainer("pylon").orElse(null)?.metadata?.version
        if (version !is SemanticVersion || version < MIN_VERSION) return@lazy null
        runCatching {
            val display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl")
            val instance = display.getMethod("instance").invoke(null)
            val lookup = MethodHandles.publicLookup()
            lookup.findVirtual(display, "isRawInput", MethodType.methodType(Boolean::class.javaPrimitiveType)).bindTo(instance) to
                lookup.findVirtual(display, "setRawInput", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType)).bindTo(instance)
        }.onFailure { LOGGER.warn("Pylon {} is installed but its raw input toggle could not be found", version, it) }.getOrNull()
    }

    @JvmStatic
    val isAvailable: Boolean get() = handles != null

    @JvmStatic
    var enabled: Boolean
        get() = handles?.first?.invoke() as? Boolean ?: false
        set(value) {
            val setter = handles?.second ?: return
            setter.invoke(value)
            PolyPlusConfig.pylonRawInput = value
            PolyPlusConfig.save()
        }

    fun initialize() {
        val setter = handles?.second ?: return
        PolyPlusConfig.pylonRawInput?.let { setter.invoke(it) }
    }
}
