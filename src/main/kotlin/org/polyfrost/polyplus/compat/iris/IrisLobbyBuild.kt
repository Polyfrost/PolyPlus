package org.polyfrost.polyplus.compat.iris

import java.util.function.BooleanSupplier

object IrisLobbyBuild {
    @Volatile
    private var building: BooleanSupplier = BooleanSupplier { false }

    @JvmStatic
    fun wire(source: BooleanSupplier) {
        building = source
    }

    @JvmStatic
    fun isBuilding(): Boolean = runCatching { building.asBoolean }.getOrDefault(false)
}
