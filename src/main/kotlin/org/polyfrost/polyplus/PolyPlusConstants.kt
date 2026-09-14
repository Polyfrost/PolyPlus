package org.polyfrost.polyplus

import net.fabricmc.loader.api.FabricLoader

object PolyPlusConstants {
    const val ID = "polyplus"
    const val NAME = "PolyPlus"

    val IS_DEV_ENV: Boolean = FabricLoader.getInstance().isDevelopmentEnvironment

    val VERSION: String = FabricLoader.getInstance()
        .getModContainer(ID)
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    const val DISCORD_URL = "https://polyfrost.org/discord"
}
