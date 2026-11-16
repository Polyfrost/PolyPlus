package org.polyfrost.polyplus.compat

import com.bawnorton.mixinsquared.api.MixinCanceller

class RrlsMixinCanceller : MixinCanceller {
    override fun shouldCancel(targetClassNames: List<String>, mixinClassName: String): Boolean =
        mixinClassName.startsWith("org.redlance.dima_dencep.mods.rrls.")
}
