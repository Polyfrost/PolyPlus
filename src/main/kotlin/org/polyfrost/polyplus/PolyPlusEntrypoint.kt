package org.polyfrost.polyplus

import org.polyfrost.polyplus.client.PolyPlusClient

//? if fabric {
import net.fabricmc.api.ClientModInitializer
//?}

class PolyPlusEntrypoint
//? if fabric {
    : ClientModInitializer
//?}
{

    //? if fabric {
    override
    //?}
    fun onInitializeClient(
    ) {
        PolyPlusClient.initialize()
    }

}
