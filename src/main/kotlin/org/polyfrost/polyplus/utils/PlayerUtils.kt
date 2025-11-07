package org.polyfrost.polyplus.utils

import dev.deftu.omnicore.api.client.client
import java.util.UUID

object PlayerUtils {
    //#if MC >= 1.20.4
    //$$ val uuid: UUID = client.uuid
    //#else
    val uuid: UUID = client.session.profile.id
    //#endif


}