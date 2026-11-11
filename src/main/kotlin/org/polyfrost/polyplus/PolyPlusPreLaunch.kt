package org.polyfrost.polyplus

import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.compat.ModConfigDefaults

//? if fabric || ornithe {
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
//?}

class PolyPlusPreLaunch
//? if fabric || ornithe {
    : PreLaunchEntrypoint
//?}
{

    //? if fabric || ornithe {
    override
    //?}
    fun onPreLaunch() {
        PolyPlusSentry.markGameThread()
        PolyPlusSentry.initialize()
        ModConfigDefaults.apply()
    }

}
