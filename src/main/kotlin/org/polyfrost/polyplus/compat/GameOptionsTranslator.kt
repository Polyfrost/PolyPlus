package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Converts reusable options whose saved format differs between game versions.
 *
 * The game upgrades old formats itself, but only based on the file's `version` line, and that line is never
 * imported. So each upgrade the game would do is repeated here, along with the reverse for older destinations.
 */
internal object GameOptionsTranslator {
    /** 26.2 saves data version 4903; the 26.3 snapshots after it moved key input from GLFW to SDL. */
    private const val SDL_KEYS_SINCE = 4950

    /**
     * Translates one source file's [entries] for this game: [legacy] is 1.8.9, [sdlKeys] is 26.3 and newer.
     * Options that were split or renamed are written in every form, since each version ignores the ones it lacks.
     */
    fun translate(entries: List<Pair<String, String>>, legacy: Boolean, sdlKeys: Boolean = false): List<Pair<String, String>> {
        val source = entries.toMap()
        val sdlSource = (source["version"]?.trim()?.toIntOrNull() ?: 0) >= SDL_KEYS_SINCE
        val translated = entries.mapNotNull { (key, value) -> entry(key, value, legacy, sdlKeys, sdlSource) }
        val written = translated.mapTo(mutableSetOf()) { it.first }
        return translated + derived(source, legacy).filter { written.add(it.first) }
    }

    fun entry(
        key: String,
        value: String,
        legacy: Boolean,
        sdlKeys: Boolean = false,
        sdlSource: Boolean = false,
    ): Pair<String, String>? {
        val converted = when {
            key.startsWith("key_") -> keybind(value, legacy, sdlKeys, sdlSource) ?: return null
            key == "lang" -> if (legacy && '_' in value) {
                value.substringBefore('_').lowercase() + "_" + value.substringAfter('_').uppercase()
            } else value.lowercase()
            key == "ao" -> if (legacy) {
                when (value) { "true" -> "2"; "false" -> "0"; else -> value }
            } else {
                value.toIntOrNull()?.let { (it != 0).toString() } ?: value
            }
            key == "renderClouds" -> {
                val name = value.trim('"')
                if (name !in setOf("true", "false", "fast")) return null
                if (legacy) name else JsonPrimitive(name).toString()
            }
            // 1.8.9 fullbright tweaks save gamma far above 1; modern versions reject that and keep their default.
            key == "gamma" && !legacy -> if ((value.toDoubleOrNull() ?: 0.0) > 1.0) "1.0" else value
            // A 0-1 fraction before 1.21, a whole number from 0 to 10 since.
            key == "menuBackgroundBlurriness" && '.' in value ->
                value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { (it * 10).roundToInt().coerceIn(0, 10).toString() }
                    ?: return null
            else -> value
        }
        val name = if (key == "key_key.swapHands" && !legacy) "key_key.swapOffhand" else key
        return name to converted
    }

    /** Options the source does not have under the name this game reads, worked out from the ones it does have. */
    private fun derived(source: Map<String, String>, legacy: Boolean): List<Pair<String, String>> = buildList {
        fun text(key: String) = source[key]?.trim()?.trim('"')

        // 0 fast, 1 fancy, 2 fabulous: `fancyGraphics` until 1.16, `graphicsMode` until 1.21.11, then a preset.
        val preset = text("graphicsPreset")
        val mode = text("graphicsMode")?.toIntOrNull()?.coerceIn(0, 2)
            ?: text("fancyGraphics")?.toBooleanStrictOrNull()?.let { if (it) 1 else 0 }
            ?: when (preset) {
                "fast" -> 0
                "fancy" -> 1
                "fabulous" -> 2
                "custom" -> when {
                    text("improvedTransparency") == "true" -> 2
                    text("cutoutLeaves") == "false" -> 0
                    else -> 1
                }
                else -> null
            }
        if (legacy) {
            mode?.let { add("fancyGraphics" to (it != 0).toString()) }
            return@buildList
        }
        if (mode != null) {
            add("graphicsMode" to mode.toString())
            // The same split the game makes when it upgrades a `graphicsMode` to 1.21.11.
            if (preset == null) {
                add("cutoutLeaves" to (mode != 0).toString())
                add("weatherRadius" to if (mode == 0) "5" else "10")
                add("vignette" to (mode != 0).toString())
                add("improvedTransparency" to (mode == 2).toString())
                add("graphicsPreset" to "\"custom\"")
            }
        }

        // `showNowPlayingToast` became the `musicToast` choice in 1.21.11.
        val musicToast = text("musicToast")
        val nowPlaying = text("showNowPlayingToast")
        if (musicToast != null) {
            add("showNowPlayingToast" to (musicToast == "pause_and_toast").toString())
        } else if (nowPlaying != null) {
            add("musicToast" to if (nowPlaying == "false") "\"never\"" else "\"pause_and_toast\"")
        }
    }

    private fun keybind(value: String, legacy: Boolean, sdlKeys: Boolean, sdlSource: Boolean): String? {
        val numeric = value.toIntOrNull()
        if (numeric != null) {
            if (legacy) return value
            if (numeric == 0) return "key.keyboard.unknown"
            if (numeric in -100..-1) {
                val button = when (numeric) { -100 -> "left"; -99 -> "right"; -98 -> "middle"; else -> (numeric + 101).toString() }
                return "key.mouse.$button"
            }
            return legacyKeys[numeric]?.let { if (sdlKeys) glfwToSdl[it] ?: it else it }
        }

        // The name this key has before 26.3, or null when it only exists since then.
        val glfwName = sdlToGlfw[value] ?: value.takeUnless { sdlSource && it in glfwToSdl }
        if (legacy) {
            if (value == "key.keyboard.unknown") return "0"
            if (value.startsWith("key.mouse.")) {
                val button = when (val name = value.removePrefix("key.mouse.")) {
                    "left" -> 1
                    "right" -> 2
                    "middle" -> 3
                    else -> name.toIntOrNull()?.takeIf { it in 4..100 } ?: return null
                }
                return (button - 101).toString()
            }
            return modernKeys[glfwName]?.toString()
        }
        if (sdlKeys) {
            if (sdlSource) return value.takeIf { sdlNamePattern.matches(it) }
            // Raw GLFW key and scan codes mean something else to SDL, and it has no F25.
            return value.takeIf { it in glfwNames && it != "key.keyboard.f25" }?.let { glfwToSdl[it] ?: it }
        }
        // Before 26.3 a name the game does not know stops it loading the rest of the options file.
        return glfwName?.takeIf { it in glfwNames || !sdlSource && glfwCodePattern.matches(it) }
    }

    // LWJGL 2 scan codes are neither GLFW key codes nor ASCII. Names match InputConstants' saved keys.
    private val legacyKeys = buildMap<Int, String> {
        fun keys(start: Int, vararg names: String) = names.forEachIndexed { index, name -> put(start + index, "key.keyboard.$name") }
        keys(1, "escape", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal", "backspace", "tab")
        keys(16, "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left.bracket", "right.bracket", "enter", "left.control")
        keys(30, "a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "apostrophe", "grave.accent", "left.shift", "backslash")
        keys(44, "z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash", "right.shift", "keypad.multiply", "left.alt", "space", "caps.lock")
        keys(59, *(1..10).map { "f$it" }.toTypedArray())
        keys(69, "num.lock", "scroll.lock", "keypad.7", "keypad.8", "keypad.9", "keypad.subtract", "keypad.4", "keypad.5", "keypad.6", "keypad.add", "keypad.1", "keypad.2", "keypad.3", "keypad.0", "keypad.decimal")
        keys(87, "f11", "f12")
        keys(100, "f13", "f14", "f15")
        keys(141, "keypad.equal")
        keys(156, "keypad.enter", "right.control")
        keys(181, "keypad.divide")
        keys(183, "print.screen", "right.alt")
        keys(197, "pause")
        keys(199, "home", "up", "page.up")
        keys(203, "left")
        keys(205, "right")
        keys(207, "end", "down", "page.down", "insert", "delete")
        keys(219, "left.win", "right.win", "menu")
    }
    private val modernKeys = legacyKeys.entries.associate { (code, name) -> name to code }

    // 26.3 moved from GLFW to SDL: the physical keypad decimal and application keys were renamed, and the old
    // names now belong to other keys.
    private val glfwToSdl = mapOf(
        "key.keyboard.keypad.decimal" to "key.keyboard.keypad.period",
        "key.keyboard.menu" to "key.keyboard.application",
    )
    private val sdlToGlfw = glfwToSdl.entries.associate { (glfw, sdl) -> sdl to glfw }

    /** Every key name the game saves from 1.13 to 26.2. */
    private val glfwNames = modernKeys.keys +
        (16..25).map { "key.keyboard.f$it" } +
        listOf("key.keyboard.world.1", "key.keyboard.world.2", "key.keyboard.unknown") +
        listOf("left", "right", "middle", "4", "5", "6", "7", "8").map { "key.mouse.$it" }

    /** Keys without a name are saved by their raw code, e.g. `scancode.86` or `key.mouse.9`. */
    private val glfwCodePattern = Regex("(key\\.keyboard|key\\.mouse|scancode)\\.\\d+")
    private val sdlNamePattern = Regex("key\\.(keyboard|mouse)\\.[a-z0-9.]+")
}
