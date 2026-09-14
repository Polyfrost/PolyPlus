package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.LanguageAdapter
import net.fabricmc.loader.api.LanguageAdapterException
import net.fabricmc.loader.api.ModContainer
import org.apache.logging.log4j.LogManager

@Suppress("unused")
class EarlyConfigAdapter : LanguageAdapter {

    init {
        try {
            ModConfigDefaults.applyEarly()
        } catch (e: Throwable) {
            runCatching {
                LogManager.getLogger(ID).warn("Could not apply the early mod defaults; they stay as they are", e)
            }
        }
    }

    override fun <T> create(mod: ModContainer, value: String, type: Class<T>): T =
        throw LanguageAdapterException("$ID only exists to run before mixin bootstrapping; it cannot create $value")

    private companion object {
        const val ID = "polyplus-early"
    }
}
