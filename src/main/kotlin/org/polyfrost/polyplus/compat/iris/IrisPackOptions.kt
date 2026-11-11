package org.polyfrost.polyplus.compat.iris

import net.irisshaders.iris.Iris
import net.irisshaders.iris.shaderpack.option.values.OptionValues
import org.polyfrost.polyplus.client.lobby.LOBBY_LOGGER

object IrisPackOptions {
    private val SWITCHES = listOf("DOF", "DEPTH_OF_FIELD")
    private val CHOICES = mapOf("WORLD_BLUR" to "0")

    @Volatile
    private var broken = false
    private var checked: OptionValues? = null
    private var cached = false

    @JvmStatic
    fun depthOfField(): Boolean {
        if (broken) return false
        return runCatching {
            val values = Iris.getCurrentPack().orElse(null)?.shaderPackOptions?.optionValues ?: return false
            if (values !== checked) {
                cached = read(values)
                checked = values
            }
            cached
        }.getOrElse { error ->
            broken = true
            LOBBY_LOGGER.warn("Shader pack depth of field check disabled", error)
            false
        }
    }

    private fun read(values: OptionValues): Boolean {
        val options = values.optionSet
        val switched = SWITCHES.any { it in options.booleanOptions && values.getBooleanValueOrDefault(it) }
        return switched || CHOICES.any { (name, off) -> name in options.stringOptions && values.getStringValueOrDefault(name) != off }
    }
}
