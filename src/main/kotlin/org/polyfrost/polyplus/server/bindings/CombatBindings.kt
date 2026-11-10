package org.polyfrost.polyplus.server.bindings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.server.Binding
import org.polyfrost.polyplus.server.OptionRegistry
import org.polyfrost.polyplus.server.OptionValue

object CombatBindings {
    @Volatile
    @JvmField
    var disableMissPenalty = false

    @Volatile
    @JvmField
    var disableBlockMissPenalty = false

    @Volatile
    @JvmField
    var allowDigAndUse = false

    @JvmStatic
    fun skipMissPenalty(targetingBlock: Boolean): Boolean = if (targetingBlock) disableBlockMissPenalty else disableMissPenalty

    fun register() {
        OptionRegistry.register(Flag("disable-miss-penalty") { disableMissPenalty = it })
        OptionRegistry.register(Flag("disable-block-miss-penalty") { disableBlockMissPenalty = it })
        OptionRegistry.register(Flag("allow-dig-and-use") { allowDigAndUse = it })
    }

    private class Flag(name: String, private val set: (Boolean) -> Unit) : Binding {
        override val key = "combat.$name"
        override val target = "combat:$name"
        override val mod = PolyPlusConstants.ID
        override val type = "bool"

        override fun snapshot(): JsonElement = JsonPrimitive(false)

        override fun apply(value: OptionValue): Boolean {
            if (value !is OptionValue.Bool) return false
            set(value.value)
            return true
        }

        override fun restore(snapshot: JsonElement) = set(false)
    }
}
