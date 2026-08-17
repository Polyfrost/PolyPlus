package org.polyfrost.polyplus.compat

import dev.isxander.debugify.Debugify
import dev.isxander.debugify.api.DebugifyApi

class DebugifyBannableFixes : DebugifyApi {

    override fun getDisabledFixes(): Array<String> {
        if (Debugify.CONFIG.gameplayFixesInMultiplayer) return emptyArray()
        DebugifyCompat.bannableFixesDisabled = true
        return arrayOf("MC-231097", "MC-136249")
    }
}

object DebugifyCompat {
    @JvmStatic
    var bannableFixesDisabled = false
        internal set
}
