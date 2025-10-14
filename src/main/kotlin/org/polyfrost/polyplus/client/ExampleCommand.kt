package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.api.commands.v1.factories.annotated.Command
import org.polyfrost.oneconfig.api.commands.v1.factories.annotated.Handler
import org.polyfrost.oneconfig.utils.v1.dsl.openUI
import org.polyfrost.polyplus.PolyPlus

@Command(PolyPlus.ID)
object ExampleCommand {

    @Handler
    private fun main() {
        Config.openUI()
    }

}
