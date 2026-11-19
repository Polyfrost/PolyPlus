package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.LogManager
import java.lang.reflect.Field

object PictureModeCompat {
    private const val CLIENT = "mod.icanttellyou.picturemode.client.PictureModeClient"
    private const val STATE = "mod.icanttellyou.picturemode.client.PictureModeState"

    private val hook = ReflectiveHook(LogManager.getLogger("PolyPlus/PictureModeCompat"))
    private val installed: Boolean by lazy { Mods.loaded("picturemode") }
    private var field: Field? = null
    private var lobbyState: Any? = null
    private var looked = false

    @JvmStatic
    fun swapIn() {
        if (!installed || hook.broken) return
        hook.attempt("Could not give Picture Mode a state for the lobby", Unit) {
            val field = field() ?: return
            if (field.get(null) == null) field.set(null, lobbyState)
        }
    }

    @JvmStatic
    fun swapOut() {
        if (!installed || hook.broken) return
        hook.attempt("Could not clear the lobby's Picture Mode state", Unit) {
            val field = field() ?: return
            val state = lobbyState ?: return
            if (field.get(null) === state) field.set(null, null)
        }
    }

    private fun field(): Field? {
        if (looked) return field
        looked = true
        val client = Reflect.find(CLIENT) ?: return null
        val type = Reflect.find(STATE) ?: return null
        lobbyState = type.getConstructor().newInstance()
        field = Reflect.declared(client, "state")
        return field
    }
}
