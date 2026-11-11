package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.polyfrost.polyplus.compat.ModConfigPatcher.Patch
import java.nio.file.Path

/**
 * Turns on the OneConfig mod that matches each feature another client had on: Lunar's toggle sprint becomes
 * PolySprint, Badlion's hit color becomes Damage Tint, and so on.
 *
 * Features are only ever switched on. Clients like Badlion save an "off" state for every mod the user never
 * touched, and mirroring those would switch off things that are on by default here.
 */
internal object ClientFeatureTranslator {

    /** One OneConfig mod setting change. Several targets cover configs whose file name differs by version. */
    private data class Change(val modId: String, val configIds: List<String>, val values: Map<String, JsonElement>)

    private fun change(modId: String, configId: String, vararg values: Pair<String, Any>) =
        Change(modId, listOf(configId), values.associate { (key, value) -> key to value.toJson() })

    private fun Any.toJson(): JsonElement = when (this) {
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

    enum class Feature(val label: String) {
        TOGGLE_SPRINT("Toggle sprint"),
        TOGGLE_SNEAK("Toggle sneak"),
        FREELOOK("Freelook"),
        CROSSHAIR("Custom crosshair"),
        HITBOXES("Hitboxes"),
        MOTION_BLUR("Motion blur"),
        BLOCK_OUTLINE("Block outline"),
        TIME_CHANGER("Time changer"),
        WEATHER_CHANGER("Weather changer"),
        GLINT("Glint colorizer"),
        HIT_COLOR("Hit color"),
        COLOR_SATURATION("Color saturation"),
        FOV_CHANGER("FOV changer"),
        FULLBRIGHT("Fullbright"),
        ITEM_PHYSICS("Item physics"),
        TNT_TIMER("TNT timer"),
        AUTO_TEXT("Auto text"),
        NAMETAGS("Nametags"),
        PARTICLES("Particles"),
        OLD_ANIMATIONS("1.7 animations"),
        SCROLLABLE_TOOLTIPS("Scrollable tooltips"),
        CHAT("Chat tweaks"),
        HEIGHT_OVERLAY("Height limit overlay"),
    }

    private val changes: Map<Feature, Change> = mapOf(
        Feature.TOGGLE_SPRINT to change("polysprint", "polysprint.json", "isEnabled" to true, "toggleSprint" to true),
        Feature.TOGGLE_SNEAK to change("polysprint", "polysprint.json", "isEnabled" to true, "toggleSneak" to true),
        Feature.FREELOOK to change("freelook", "freelook.json", "enabled" to true),
        Feature.CROSSHAIR to change("polycrosshair", "polycrosshair.json", "enabled" to true),
        // Only the mod; "toggled" would show hitboxes all the time instead of on F3+B.
        Feature.HITBOXES to change("polyhitbox", "polyhitbox.json", "enabled" to true),
        Feature.MOTION_BLUR to change("polyblur", "polyblur.json", "isEnabled" to true),
        Feature.BLOCK_OUTLINE to change("custom-block-highlight", "custom-block-highlight.json", "enableModRendering" to true),
        Feature.TIME_CHANGER to change("polytime", "polytime.json", "isEnabled" to true),
        Feature.WEATHER_CHANGER to change("polyweather", "polyweather.json", "isEnabled" to true),
        Feature.GLINT to change("glintcolorizer", "glintcolorizer.json", "enabled" to true),
        Feature.HIT_COLOR to change("damagetint", "damagetint.json", "enabled" to true),
        Feature.COLOR_SATURATION to change("colorsaturation", "colorsaturation.json", "isEnabled" to true),
        Feature.FOV_CHANGER to change("fovchanger", "fovchanger.json", "enabled" to true),
        Feature.FULLBRIGHT to change("fullbright", "fullbright.json", "enable" to true),
        Feature.ITEM_PHYSICS to change("itemphysiclite", "oitemphysic.json", "options.ItemPhysicConfig.Toggle mod" to true),
        Feature.TNT_TIMER to change("countdown", "countdown.json", "enabled" to true),
        Feature.AUTO_TEXT to change("autotext", "autotext.json", "enabled" to true),
        Feature.NAMETAGS to change("polynametag", "nametag.json", "isEnabled" to true),
        Feature.PARTICLES to change("overflowparticles", "overflowparticles.json", "modEnabled" to true),
        Feature.OLD_ANIMATIONS to Change(
            "overflowanimations",
            listOf("overflowanimations-1.8.9.json", "overflowanimations.json"),
            mapOf("modEnabled" to JsonPrimitive(true)),
        ),
        Feature.SCROLLABLE_TOOLTIPS to change("tooltipscroll", "tooltipscroll.json", "canScroll" to true),
        Feature.CHAT to change("chatting", "chatting.json", "modEnabled" to true),
        Feature.HEIGHT_OVERLAY to change("hybedwars", "hybedwars.json", "heightOverlay" to true),
    )

    // Each client's own name for a feature.
    private val lunar = mapOf(
        "TOGGLE_SNEAK" to Feature.TOGGLE_SPRINT,
        "FREELOOK" to Feature.FREELOOK,
        "CROSSHAIR" to Feature.CROSSHAIR,
        "HITBOX" to Feature.HITBOXES,
        "MOTION_BLUR" to Feature.MOTION_BLUR,
        "BLOCK_OUTLINE" to Feature.BLOCK_OUTLINE,
        "TIME_CHANGER" to Feature.TIME_CHANGER,
        "WEATHER_CHANGER" to Feature.WEATHER_CHANGER,
        "GLINT_COLORIZER" to Feature.GLINT,
        "HIT_COLOR" to Feature.HIT_COLOR,
        "DAMAGE_TINT" to Feature.HIT_COLOR,
        "COLOR_SATURATION" to Feature.COLOR_SATURATION,
        "FOV" to Feature.FOV_CHANGER,
        "ITEM_PHYSICS" to Feature.ITEM_PHYSICS,
        "TNT_COUNTDOWN" to Feature.TNT_TIMER,
        "AUTO_TEXT_ACTIONS" to Feature.AUTO_TEXT,
        "NAMETAG" to Feature.NAMETAGS,
        "PARTICLE_CHANGER" to Feature.PARTICLES,
        "ONE_SEVEN_VISUALS" to Feature.OLD_ANIMATIONS,
        "SCROLLABLE_TOOLTIPS" to Feature.SCROLLABLE_TOOLTIPS,
        "CHAT" to Feature.CHAT,
    )

    private val feather = mapOf(
        "toggleSprint" to Feature.TOGGLE_SPRINT,
        "perspective" to Feature.FREELOOK,
        "crosshair" to Feature.CROSSHAIR,
        "hitbox" to Feature.HITBOXES,
        "motionBlur" to Feature.MOTION_BLUR,
        "blockOverlay" to Feature.BLOCK_OUTLINE,
        "timeChanger" to Feature.TIME_CHANGER,
        "weatherchanger" to Feature.WEATHER_CHANGER,
        "glint" to Feature.GLINT,
        "colorSaturation" to Feature.COLOR_SATURATION,
        "fovChanger" to Feature.FOV_CHANGER,
        "brightness" to Feature.FULLBRIGHT,
        "itemPhysic" to Feature.ITEM_PHYSICS,
        "tnttimer" to Feature.TNT_TIMER,
        "autoText" to Feature.AUTO_TEXT,
        "nametags" to Feature.NAMETAGS,
        "particles" to Feature.PARTICLES,
        "animations" to Feature.OLD_ANIMATIONS,
        "customChat" to Feature.CHAT,
    )

    private val badlion = mapOf(
        "toggleSprint" to Feature.TOGGLE_SPRINT,
        "toggleSneak" to Feature.TOGGLE_SNEAK,
        "perspective" to Feature.FREELOOK,
        "crosshair" to Feature.CROSSHAIR,
        "hitboxes" to Feature.HITBOXES,
        "motionBlur" to Feature.MOTION_BLUR,
        "blockOverlay" to Feature.BLOCK_OUTLINE,
        "timeChanger" to Feature.TIME_CHANGER,
        "weatherChanger" to Feature.WEATHER_CHANGER,
        "enchantGlint" to Feature.GLINT,
        "hitColor" to Feature.HIT_COLOR,
        "damageTint" to Feature.HIT_COLOR,
        "colorSaturation" to Feature.COLOR_SATURATION,
        "fovChanger" to Feature.FOV_CHANGER,
        "fullbright" to Feature.FULLBRIGHT,
        "itemPhysic" to Feature.ITEM_PHYSICS,
        "tntTime" to Feature.TNT_TIMER,
        "autoText" to Feature.AUTO_TEXT,
        "nameTags" to Feature.NAMETAGS,
        "particles" to Feature.PARTICLES,
        "animations" to Feature.OLD_ANIMATIONS,
        "chat" to Feature.CHAT,
        "heightLimitBlockOverlay" to Feature.HEIGHT_OVERLAY,
    )

    private val noRisk = mapOf(
        "togglesprint" to Feature.TOGGLE_SPRINT,
        "freelook" to Feature.FREELOOK,
        "custom crosshair" to Feature.CROSSHAIR,
        "hitbox" to Feature.HITBOXES,
        "motionblur" to Feature.MOTION_BLUR,
        "block outlines" to Feature.BLOCK_OUTLINE,
        "time changer" to Feature.TIME_CHANGER,
        "weather changer" to Feature.WEATHER_CHANGER,
        "glint colorizer" to Feature.GLINT,
        "hitcolor" to Feature.HIT_COLOR,
        "color saturation" to Feature.COLOR_SATURATION,
        "fov changer" to Feature.FOV_CHANGER,
        "fullbright" to Feature.FULLBRIGHT,
        "item physics" to Feature.ITEM_PHYSICS,
        "tnt timer" to Feature.TNT_TIMER,
        "autotext" to Feature.AUTO_TEXT,
        "nametags" to Feature.NAMETAGS,
        "particles" to Feature.PARTICLES,
        "old animations" to Feature.OLD_ANIMATIONS,
        "chat" to Feature.CHAT,
    )

    /** Feature changes for every client found in [sources]. */
    fun patches(sources: List<Path>): List<Patch> = buildList {
        LunarHudTranslator.readMods(sources)?.let { addAll(fromLunar(it)) }
        NoRiskHudTranslator.readModules(sources)?.let { addAll(fromNoRisk(it)) }
        FeatherHudTranslator.readProfile(sources)?.let { addAll(fromFeather(it)) }
        BadlionHudTranslator.readProfile(sources)?.let { addAll(fromBadlion(it)) }
    }

    /** Labels of the features that would change here, for the onboarding preview. */
    fun preview(sources: List<Path>): List<String> =
        patches(sources).filter(ModConfigPatcher::wouldApply).map { it.source.substringAfter(" · ") }.distinct()

    /** Lunar saves `enabled` only once the user changes it; fly boost shares PolySprint's scale. */
    internal fun fromLunar(root: JsonObject): List<Patch> = root.mapNotNull { (key, value) ->
        val module = value as? JsonObject ?: return@mapNotNull null
        val feature = lunar[key] ?: return@mapNotNull null
        if (module["enabled"]?.jsonPrimitive?.booleanOrNull != true) return@mapNotNull null
        val extra = buildMap<String, JsonElement> {
            val options = module["options"] as? JsonObject
            if (feature == Feature.TOGGLE_SPRINT) {
                options?.get("flyBoostAmount")?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it in 1.0..10.0 }
                    ?.let { put("flyBoostAmount", JsonPrimitive(it)) }
            }
        }
        feature to extra
    }.flatMap { (feature, extra) -> patchesFor("Lunar", feature, extra) }

    /** Feather saves `enabled` as a string, and only once it differs from the default. */
    internal fun fromFeather(root: JsonObject): List<Patch> = root.mapNotNull { (key, value) ->
        val feature = feather[key] ?: return@mapNotNull null
        val enabled = (value as? JsonObject)?.get("enabled")?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        feature.takeIf { enabled == true }
    }.flatMap { patchesFor("Feather", it) }

    internal fun fromBadlion(root: JsonObject): List<Patch> = root.mapNotNull { (key, value) ->
        val feature = badlion[key] ?: return@mapNotNull null
        val enabled = (value as? JsonObject)?.get("enabled")?.jsonPrimitive?.booleanOrNull
        feature.takeIf { enabled == true }
    }.flatMap { patchesFor("Badlion", it) }

    internal fun fromNoRisk(modules: JsonArray): List<Patch> = modules.mapNotNull { element ->
        val module = element as? JsonObject ?: return@mapNotNull null
        val name = module["name"]?.jsonPrimitive?.content?.trim()?.lowercase()?.replace('_', ' ') ?: return@mapNotNull null
        val feature = noRisk[name] ?: return@mapNotNull null
        val enabled = (module["values"] as? JsonArray)?.firstNotNullOfOrNull { item ->
            (item as? JsonObject)?.takeIf { it["name"]?.jsonPrimitive?.content == "isEnabled" }?.get("value")
        }?.jsonPrimitive?.booleanOrNull
        feature.takeIf { enabled == true }
    }.flatMap { patchesFor("NoRisk", it) }

    private fun patchesFor(client: String, feature: Feature, extra: Map<String, JsonElement> = emptyMap()): List<Patch> {
        val change = changes[feature] ?: return emptyList()
        return change.configIds.map { configId ->
            Patch(
                source = "$client ${feature.name} · ${feature.label}",
                modId = change.modId,
                configId = configId,
                values = change.values + extra,
                onlyIfExists = true,
            )
        }
    }
}
