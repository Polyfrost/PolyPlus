package org.polyfrost.polyplus.client.features

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.ui.v1.ModCardType
import org.polyfrost.oneconfig.api.ui.v1.ModCardTypes

object AdvancedModCards {
    private val logger = LogManager.getLogger("PolyPlus/AdvancedModCards")

    private const val PIVOT = "skyboxify"

    private val TYPE = ModCardType(
        id = "polyplus:advanced",
        title = "Advanced",
        icon = "settings",
        priority = 50,
        collapsedByDefault = true,
    )

    fun initialize() {
        val order = DefaultModOrder.bundledOrder()
        val pivot = order.indexOf(PIVOT)
        if (pivot < 0) {
            logger.warn("'{}' is missing from the bundled mod order, skipping the advanced group", PIVOT)
            return
        }

        val ids = order.drop(pivot)
        ModCardTypes.register(TYPE)
        ModCardTypes.assignAll(TYPE.id, *ids.toTypedArray())
        logger.info("Marked {} mods as advanced", ids.size)
    }
}
