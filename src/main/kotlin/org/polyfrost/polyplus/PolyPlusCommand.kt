package org.polyfrost.polyplus

import org.polyfrost.oneconfig.api.commands.v1.factories.annotated.Command
import org.polyfrost.oneconfig.utils.v1.dsl.openUI

@Command(PolyPlusConstants.ID)
class PolyPlusCommand {

    @Command
    fun main() {
        PolyPlusConfig.openUI()
    }

}
