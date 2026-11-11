package org.polyfrost.polyplus

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.util.Properties

object PolyPlusConstants {
    const val ID = "polyplus"
    const val NAME = "PolyPlus"

    val IS_DEV_ENV: Boolean = FabricLoader.getInstance().isDevelopmentEnvironment

    val IS_TESTER: Boolean = PolyPlusConstants::class.java.getResourceAsStream("/polyplus-tester.properties")
        ?.use { stream -> Properties().apply { load(stream) }.getProperty("tester")?.trim().toBoolean() }
        ?: false

    init {
        if (IS_TESTER) {
            LoggerFactory.getLogger(ID).info("Tester build: using the staging backend and the testing EOS app")
        }
    }

    val VERSION: String = FabricLoader.getInstance()
        .getModContainer(ID)
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    val MINECRAFT_VERSION: String = FabricLoader.getInstance()
        .getModContainer("minecraft")
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    //? if ornithe {
    /*const val LOADER = "ornithe"
    *///?} else {
    const val LOADER = "fabric"
    //?}

    const val DISCORD_URL = "https://polyfrost.org/discord"
}
