package org.polyfrost.polyplus

import org.polyfrost.polyplus.client.PolyPlusClient

//? if fabric || ornithe {
import net.fabricmc.api.ClientModInitializer
//?}

class PolyPlusEntrypoint
//? if fabric || ornithe {
    : ClientModInitializer
//?}
{

    //? if fabric || ornithe {
    override
    //?}
    fun onInitializeClient(
    ) {
        PolyPlusClient.initialize()
    }

}
