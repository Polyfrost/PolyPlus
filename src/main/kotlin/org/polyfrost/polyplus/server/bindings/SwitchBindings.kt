package org.polyfrost.polyplus.server.bindings

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.ModToggles
import org.polyfrost.oneconfig.internal.ui.api.PropertyModToggle
import org.polyfrost.oneconfig.internal.ui.api.findMasterSwitch
import org.polyfrost.polyplus.server.OneConfigBinding
import org.polyfrost.polyplus.server.OptionRegistry
import org.polyfrost.polyplus.server.OptionValue
import java.util.concurrent.ConcurrentHashMap

object SwitchBindings {
    private val LOGGER = LogManager.getLogger("PolyPlus/SwitchBindings")
    private val lockedToggles: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var listening = false

    @Synchronized
    fun scan() {
        try {
            mods()
            huds()
            vanillaHuds()
        } catch (e: LinkageError) {
            LOGGER.warn("This OneConfig version can't switch mods or HUDs off for servers", e)
        } catch (e: ReflectiveOperationException) {
            LOGGER.warn("This VanillaHUD version doesn't match its API", e)
        }
    }

    private fun mods() {
        val trees = ConfigManager.active().trees().toList()
        for (mod in FabricLoader.getInstance().allMods) {
            val id = mod.metadata.id
            val key = "mod.$id.enabled"
            if (OptionRegistry.bindings(key).isNotEmpty()) continue
            val toggle = ModToggles.toggleFor(id)
            if (toggle != null && toggle !is PropertyModToggle) {
                listen()
                OptionRegistry.register(
                    CodeBinding(
                        key, "oneconfig-toggle:$id", id, "bool", null,
                        read = { JsonPrimitive(toggle.isEnabled) },
                        write = { v -> (v as? OptionValue.Bool)?.let { toggle.isEnabled = it.value } != null },
                        reset = { toggle.isEnabled = it.jsonPrimitive.boolean },
                        lock = { locked -> if (locked) lockedToggles += id else lockedToggles -= id },
                    ),
                )
                continue
            }
            val switch = (toggle as? PropertyModToggle)?.property
            val tree = trees.firstOrNull { tree -> switch?.let { tree.pathTo(it) } != null }
                ?: trees.firstOrNull { it.id?.removeSuffix(".json") == id || it.title == mod.metadata.name }
                ?: continue
            val property = switch ?: tree.findMasterSwitch() ?: continue
            val path = tree.pathTo(property) ?: continue
            OneConfigBinding.create(key, id, tree, path)
                .onSuccess(OptionRegistry::register)
                .onFailure { OptionRegistry.unresolved(key, id, it.message ?: it.toString()) }
        }
    }

    private fun huds() {
        for (hud in HudManager.instances()) {
            val id = hud.id?.removeSuffix(".json") ?: continue
            val key = "hud.$id.enabled"
            if (OptionRegistry.bindings(key).isNotEmpty()) continue
            OptionRegistry.register(
                CodeBinding(
                    key, "oneconfig-hud:$id", "oneconfig", "bool", null,
                    read = { JsonPrimitive(!hud.hidden) },
                    write = { v -> (v as? OptionValue.Bool)?.let { hud.hidden = !it.value } != null },
                    reset = { hud.hidden = !it.jsonPrimitive.boolean },
                ),
            )
        }
    }

    private fun vanillaHuds() {
        val api = try {
            Class.forName("org.polyfrost.vanillahud.api.VanillaHudApi")
        } catch (_: ClassNotFoundException) {
            return
        }
        val elements = api.getMethod("elements")
        val isVanilla = api.getMethod("isDrawnAsVanilla", String::class.java)
        val setVanilla = api.getMethod("setDrawnAsVanilla", String::class.java, Boolean::class.javaPrimitiveType)
        for (id in elements.invoke(null) as List<*>) {
            val key = "hud.$id.vanilla"
            if (OptionRegistry.bindings(key).isNotEmpty()) continue
            OptionRegistry.register(
                CodeBinding(
                    key, "vanillahud:vanilla.$id", "vanillahud", "bool", null,
                    read = { JsonPrimitive(isVanilla.invoke(null, id) as Boolean) },
                    write = { v -> (v as? OptionValue.Bool)?.let { setVanilla.invoke(null, id, it.value) } != null },
                    reset = { setVanilla.invoke(null, id, it.jsonPrimitive.boolean) },
                ),
            )
        }
    }

    private fun listen() {
        if (listening) return
        listening = true
        ModToggles.addListener { id, enabled ->
            if (enabled && id in lockedToggles) ModToggles.toggleFor(id)?.isEnabled = false
        }
    }

    private fun Tree.pathTo(target: Property<*>, prefix: String = ""): String? {
        for ((key, node) in map) {
            if (node === target) return prefix + key
            if (node is Tree) node.pathTo(target, "$prefix$key.")?.let { return it }
        }
        return null
    }
}
