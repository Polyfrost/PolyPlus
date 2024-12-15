package org.polyfrost.polyplus

import org.polyfrost.oneconfig.api.config.v1.Config

object PolyPlusConfig : Config(
    "${PolyPlusConstants.ID}.json",
    PolyPlusConstants.NAME,
    Category.OTHER
) {
}
