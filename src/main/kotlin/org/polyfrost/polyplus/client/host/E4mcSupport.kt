package org.polyfrost.polyplus.client.host

//? if fabric {
import net.fabricmc.loader.api.FabricLoader
//?}

object E4mcSupport {
    private val MOD_IDS = listOf("e4mc_minecraft", "e4mc")

    val isPresent: Boolean by lazy {
        //? if fabric {
        val loader = FabricLoader.getInstance()
        MOD_IDS.any { loader.isModLoaded(it) }
        //?} else {
        /*false
        *///?}
    }
}
