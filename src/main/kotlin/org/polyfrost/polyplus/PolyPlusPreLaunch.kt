package org.polyfrost.polyplus

import org.polyfrost.polyplus.client.PolyPlusSentry

//? if fabric {
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
//?}

class PolyPlusPreLaunch
//? if fabric {
    : PreLaunchEntrypoint
//?}
{

    //? if fabric {
    override
    //?}
    fun onPreLaunch() {
        PolyPlusSentry.initialize()
    }

}
