package org.polyfrost.polyplus.server.bindings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.server.Binding
import org.polyfrost.polyplus.server.OneConfigBindings
import org.polyfrost.polyplus.server.OptionRegistry
import org.polyfrost.polyplus.server.OptionValue

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
//?} else {
/*import xyz.tryfle.oitemphysic.config.OSLConfig
*///?}
//? if zoomify {
import dev.isxander.yacl3.config.v3.ConfigEntry
import dev.isxander.zoomify.config.ZoomKeyBehaviour
import dev.isxander.zoomify.config.ZoomifySettings
import dev.isxander.zoomify.utils.TransitionType
import kotlin.math.roundToInt
//?}
//? if jade {
import snownee.jade.api.config.IWailaConfig
//?}
//? if jade && >= 1.21.10 {
import snownee.jade.api.JadeIds
import net.minecraft.resources.Identifier
//?}

private val LOGGER = LogManager.getLogger("PolyPlus/CodeBindings")

class CodeBinding(
    override val key: String,
    override val target: String,
    override val mod: String,
    override val type: String,
    override val bounds: List<Double>?,
    private val read: () -> JsonElement,
    private val write: (OptionValue) -> Boolean,
    private val reset: (JsonElement) -> Unit,
    private val lock: (Boolean) -> Unit = {},
) : Binding {
    override fun snapshot(): JsonElement? = guarded(null, read)

    override fun apply(value: OptionValue): Boolean = guarded(false) { write(value) }

    override fun restore(snapshot: JsonElement) = reset(snapshot)

    override fun setLocked(locked: Boolean) = lock(locked)

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: LinkageError) {
        LOGGER.warn("{} no longer matches the {} version that is installed", target, mod, e)
        fallback
    } catch (e: ReflectiveOperationException) {
        LOGGER.warn("{} no longer matches the {} version that is installed", target, mod, e)
        fallback
    }
}

object CodeBindings {
    fun initialize() {
        CombatBindings.register()
        OneConfigBindings.add(
            "polynametag", "nametag.json",
            """{"nametag.hide-nametags-in-f1": {"paths": ["isHidePlayerNametagsInHiddenHud", "isHideEntityNametagsInHiddenHud", "isHideArmorStandNametagsInHiddenHud"]}}""",
        )
        OneConfigBindings.add(
            "polyzoom", "zoomify.json",
            """{"zoom.smooth-zoom": {"paths": ["zoomInTransition", "zoomOutTransition"], "enum": {"false": "instant", "true": "ease_out_exp"}}}""",
        )
        OneConfigBindings.add(
            "itemphysicliteconfig", "itemphysicliteconfig.mixins.json",
            """{"item-physics.enabled": {"path": "enabled"}, "item-physics.rotation-speed": {"path": "rotateSpeed"}}""",
        )
        //? if > 1.8.9 {
        ClientLifecycleEvents.CLIENT_STARTED.register { resolveMods() }
        //?} else {
        /*resolveMods()
        *///?}
    }

    private fun resolveMods() {
        //? if > 1.8.9 {
        hurtCam()
        //?} else {
        /*itemPhysic()
        *///?}
        //? if zoomify
        zoomify()
        //? if jade
        jade()
        SwitchBindings.scan()
    }

    private fun resolve(mod: String, vararg keys: Pair<String, (String) -> List<Binding>>) {
        if (!FabricLoader.getInstance().isModLoaded(mod)) return
        for ((key, create) in keys) {
            runCatching { create(key).onEach { checkNotNull(it.snapshot()) { "${it.target} can't be read" } } }
                .onSuccess { it.forEach(OptionRegistry::register) }
                .onFailure { OptionRegistry.unresolved(key, mod, it.toString()) }
        }
    }

    private fun bool(key: String, target: String, mod: String, get: () -> Boolean, set: (Boolean) -> Unit, save: () -> Unit = {}) =
        CodeBinding(
            key, target, mod, "bool", null,
            read = { JsonPrimitive(get()) },
            write = { v -> (v as? OptionValue.Bool)?.let { set(it.value) } != null },
            reset = { set(it.jsonPrimitive.boolean); save() },
        )

    private fun number(key: String, target: String, mod: String, bounds: List<Double>?, get: () -> Double, set: (Double) -> Unit, save: () -> Unit = {}) =
        CodeBinding(
            key, target, mod, "number", bounds,
            read = { JsonPrimitive(get()) },
            write = { value -> value.asDouble()?.let { set(if (bounds == null) it else Math.clamp(it, bounds[0], bounds[1])) } != null },
            reset = { set(it.jsonPrimitive.double); save() },
        )

    //? if > 1.8.9 {
    private var tiltBeforeOverride = 1.0

    private fun hurtCam() {
        val tilt = { Minecraft.getInstance().options.damageTiltStrength() }
        fun binding(key: String, type: String, bounds: List<Double>?, value: (OptionValue) -> Double?) = CodeBinding(
            key, "minecraft:options.damageTiltStrength", "minecraft", type, bounds,
            read = { JsonPrimitive(tilt().get().also { tiltBeforeOverride = it }) },
            write = { v -> value(v)?.let { tilt().set(it) } != null },
            reset = { tilt().set(it.jsonPrimitive.double); Minecraft.getInstance().options.save() },
        )
        resolve(
            "minecraft",
            "hurt-cam.disable-hurt-cam" to { listOf(binding(it, "bool", null) { v -> (v as? OptionValue.Bool)?.let { b -> if (b.value) 0.0 else tiltBeforeOverride } }) },
            "hurt-cam.hurt-shaking-intensity" to { listOf(binding(it, "number", listOf(0.0, 1.0)) { v -> v.asDouble()?.let { d -> Math.clamp(d, 0.0, 1.0) } }) },
        )
    }
    //?} else {
    /*private fun itemPhysic() = resolve(
        "itemphysiclite",
        "item-physics.enabled" to {
            listOf(bool(it, "itemphysiclite:toggled", "itemphysiclite", { OSLConfig.toggled.get() }, { v -> OSLConfig.toggled.set(v) }))
        },
        "item-physics.rotation-speed" to {
            listOf(number(it, "itemphysiclite:fallingRotationSpeed", "itemphysiclite", null, { OSLConfig.fallingRotationSpeed.get().toDouble() }, { v -> OSLConfig.fallingRotationSpeed.set(v.toFloat()) }))
        },
    )
    *///?}

    //? if zoomify {
    private fun zoomify() {
        val save = { ZoomifySettings.saveToFile() }
        fun <T : Any> switch(key: String, entry: ConfigEntry<T>, off: T, on: T, decode: (String) -> T) = CodeBinding(
            key, "zoomify:${entry.fieldName()}", "zoomify", "bool", null,
            read = { JsonPrimitive(entry.get().let { if (it is Enum<*>) it.name else it.toString() }) },
            write = { v -> (v as? OptionValue.Bool)?.let { entry.set(if (it.value) entry.get().takeIf { current -> current != off } ?: on else off) } != null },
            reset = { entry.set(decode(it.jsonPrimitive.content)); save() },
        )
        resolve(
            "zoomify",
            "zoom.toggle-key-zoom" to { listOf(switch(it, ZoomifySettings.zoomKeyBehaviour, ZoomKeyBehaviour.HOLD, ZoomKeyBehaviour.TOGGLE, ZoomKeyBehaviour::valueOf)) },
            "zoom.smooth-zoom" to { key ->
                listOf(ZoomifySettings.zoomInTransition, ZoomifySettings.zoomOutTransition)
                    .map { switch(key, it, TransitionType.INSTANT, TransitionType.EASE_OUT_EXP, TransitionType::valueOf) }
            },
            "zoom.smooth-camera" to { listOf(switch(it, ZoomifySettings.cinematicCamera, 0, 100, String::toInt)) },
            "zoom.variable-zoom" to {
                listOf(bool(it, "zoomify:scrollZoom", "zoomify", { ZoomifySettings.scrollZoom.get() }, { v -> ZoomifySettings.scrollZoom.set(v) }, save))
            },
            "zoom.zoom-divisor" to {
                listOf(
                    number(
                        it, "zoomify:initialZoom", "zoomify", listOf(1.0, 10.0),
                        { ZoomifySettings.initialZoom.get().toDouble() }, { v -> ZoomifySettings.initialZoom.set(v.roundToInt()) }, save,
                    ),
                )
            },
        )
    }
    //?}

    //? if jade {
    private fun jade() {
        //? if >= 1.21.10 {
        val general = { IWailaConfig.get().general() }
        val overlay = { IWailaConfig.get().overlay() }
        val save = { IWailaConfig.get().save() }
        fun plugin(key: String, id: Identifier) = IWailaConfig.get().plugin().let { config ->
            require(id in config.values()) { "Jade has no plugin option $id" }
            bool(key, "jade:plugin.$id", "jade", { IWailaConfig.get().plugin().get(id) }, { v -> check(IWailaConfig.get().plugin().set(id, v)) }, save)
        }
        val plugins = arrayOf<Pair<String, (String) -> List<Binding>>>(
            "waila.show-block-coords" to { listOf(plugin(it, JadeIds.CORE_COORDINATES)) },
            "waila.show-correct-tool" to { listOf(plugin(it, JadeIds.MC_HARVEST_TOOL)) },
        )
        //?} else {
        /*val general = { IWailaConfig.get().general }
        val overlay = { IWailaConfig.get().overlay }
        val save = {}
        val plugins = emptyArray<Pair<String, (String) -> List<Binding>>>()
        for (key in listOf("waila.show-block-coords", "waila.show-correct-tool")) {
            if (FabricLoader.getInstance().isModLoaded("jade")) OptionRegistry.unresolved(key, "jade", "this Jade version can't change plugin options through its API")
        }
        *///?}
        resolve(
            "jade",
            "waila.enabled" to {
                listOf(bool(it, "jade:general.displayTooltip", "jade", { general().shouldDisplayTooltip() }, { v -> general().setDisplayTooltip(v) }, save))
            },
            "waila.show-entities" to {
                listOf(bool(it, "jade:general.displayEntities", "jade", { general().displayEntities }, { v -> general().displayEntities = v }, save))
            },
            "waila.scale" to {
                listOf(
                    number(
                        it, "jade:overlay.overlayScale", "jade", listOf(0.2, 2.0),
                        { overlay().overlayScale.toDouble() }, { v -> overlay().overlayScale = v.toFloat() }, save,
                    ),
                )
            },
            *plugins,
        )
    }
    //?}
}
