package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader

object Mods {
    val chatting: Boolean by lazy { loaded("chatting") }
    val distantHorizons: Boolean by lazy { loaded("distanthorizons") }
    val entityModelFeatures: Boolean by lazy { loaded("entity_model_features") }
    val evergreenHud: Boolean by lazy { loaded("evergreenhud") }
    val exploitPreventer: Boolean by lazy { loaded("exploitpreventer") }
    val flashback: Boolean by lazy { loaded("flashback") }
    val iris: Boolean by lazy { loaded("iris") }
    val notEnoughAnimations: Boolean by lazy { loaded("notenoughanimations") }
    val replayMod: Boolean by lazy { loaded("replaymod") }
    val wwaypoints: Boolean by lazy { loaded("wwaypoints") }

    fun loaded(id: String): Boolean = runCatching { FabricLoader.getInstance().isModLoaded(id) }.getOrDefault(false)
}
