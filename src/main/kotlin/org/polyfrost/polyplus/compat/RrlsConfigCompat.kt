package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

// Remove Reloading Screen writes config/rrls.toml in two mutually unreadable layouts and a shared
// config directory hands each build the other's file so rewrite it before rrls reads it
// Settings exclusive to the other layout live in a backup beside the config
object RrlsConfigCompat {

    private val logger = LogManager.getLogger("PolyPlus/RrlsConfigCompat")

    private const val MOD_ID = "rrls"
    private const val CONFIG_FILE = "rrls.toml"

    private const val MODERN_MARKER = "org/redlance/dima_dencep/mods/rrls/RrlsConfig.class"
    private const val LEGACY_MARKER =
        "org/redlance/dima_dencep/mods/rrls/fabric/ConfigExpectPlatformImpl.class"

    internal enum class Format(val backupSuffix: String) {
        LEGACY(".polyplus-legacy.bak"),

        MODERN(".polyplus-modern.bak"),
    }

    private enum class Kind { BOOLEAN, NUMBER, STRING, ENUM }

    // Null keys mean the layout has no equivalent Values outside legacyValues make the legacy
    // parser throw so they are dropped
    private class Option(
        val legacyKey: String?,
        val modernKey: String?,
        val kind: Kind,
        val legacyValues: Set<String>? = null,
    ) {
        fun keyOf(format: Format): String? = if (format == Format.LEGACY) legacyKey else modernKey
    }

    private val OPTIONS = listOf(
        Option("hideOverlays", "global.hideOverlays", Kind.ENUM, setOf("ALL", "LOADING", "RELOADING", "NONE")),
        Option("blockOverlay", "global.blockOverlay", Kind.BOOLEAN),
        Option("miniRender", "global.miniRender", Kind.BOOLEAN),
        Option("enableScissor", "global.enableScissor", Kind.BOOLEAN),
        Option("type", "splash.type", Kind.ENUM, setOf("PROGRESS", "TEXT", "TEXT_WITH_BACKGROUND")),
        Option("rgbProgress", "splash.rgbProgress", Kind.BOOLEAN),
        Option("reloadText", "splash.reloadText", Kind.STRING),
        Option("animationSpeed", "splash.animationSpeed", Kind.NUMBER),
        Option(null, "interpolation.interpolateProgress", Kind.BOOLEAN),
        Option(null, "interpolation.interpolateAtEnd", Kind.BOOLEAN),
        Option(null, "interpolation.ease", Kind.ENUM),
        Option(null, "interpolation.easingArg", Kind.NUMBER),
        Option("resetResources", "other.resetResources", Kind.BOOLEAN),
        Option("reInitScreen", "other.reInitScreen", Kind.BOOLEAN),
        Option("removeOverlayAtEnd", null, Kind.BOOLEAN),
        Option("earlyPackStatusSend", "other.earlyPackStatusSend", Kind.BOOLEAN),
        Option("doubleLoad", "other.doubleLoad", Kind.ENUM, setOf("FORCE_LOAD", "LOAD", "NONE")),
        Option(null, "platform.skipForgeOverlay", Kind.BOOLEAN),
    )

    private val LEGACY_KEYS = OPTIONS.mapNotNull(Option::legacyKey).toSet()
    private val MODERN_SECTIONS = OPTIONS.mapNotNull { it.modernKey?.substringBefore('.') }.toSet()

    private val ENUM_NAME = Regex("[A-Za-z0-9_]+")
    private val FINITE_NUMBER = Regex("""[+-]?\d+(\.\d+)?([eE][+-]?\d+)?""")
    private val NON_FINITE_NUMBERS = setOf("nan", "+nan", "-nan", "inf", "+inf", "-inf")

    fun apply() {
        runCatching(::migrate).onFailure {
            logger.warn("Could not adapt the Remove Reloading Screen config to this game version", it)
        }
    }

    private fun migrate() {
        val installed = installedFormat() ?: return

        val configDir = FabricLoader.getInstance().configDir
        val config = configDir.resolve(CONFIG_FILE)
        if (!config.exists()) return

        val text = config.readText()
        if (isUsableBy(text, installed)) return

        val backup = configDir.resolve(CONFIG_FILE + installed.backupSuffix)
        val preserved = if (backup.exists()) backup.readText() else ""

        // Nothing worth keeping let Remove Reloading Screen write its own defaults
        val rewritten = translate(text, installed, preserved)
        if (rewritten.isEmpty()) return

        val present = detectFormat(text)
        if (present != null && present != installed) {
            config.copyTo(configDir.resolve(CONFIG_FILE + present.backupSuffix), overwrite = true)
        }

        config.writeText(rewritten)
        logger.info(
            "Rewrote {} in the {} layout for the installed build of Remove Reloading Screen",
            CONFIG_FILE,
            installed.name.lowercase(),
        )
    }

    private fun installedFormat(): Format? {
        val container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null) ?: return null
        return when {
            container.findPath(MODERN_MARKER).isPresent -> Format.MODERN
            container.findPath(LEGACY_MARKER).isPresent -> Format.LEGACY
            else -> null
        }
    }

    internal fun detectFormat(text: String): Format? {
        val values = parse(text)
        return when {
            values.keys.any { it.substringBefore('.', "") in MODERN_SECTIONS } -> Format.MODERN
            values.keys.any { it in LEGACY_KEYS } -> Format.LEGACY
            else -> null
        }
    }

    internal fun isUsableBy(text: String, target: Format): Boolean {
        if (detectFormat(text) != target) return false

        val values = parse(text)
        // Every literal has to survive the legacy parser including ones under keys it ignores
        if (target == Format.LEGACY && values.values.any { !isLexableByToml4j(it) }) return false

        return OPTIONS.all { option ->
            val raw = option.keyOf(target)?.let(values::get) ?: return@all true
            sanitize(raw, option, target) != null
        }
    }

    // Settings only the target layout has come from preserved the last file written in it
    internal fun translate(text: String, target: Format, preserved: String): String {
        val source = parse(text)
        val sourceFormat = detectFormat(text) ?: target
        val fallback = parse(preserved)

        val values = LinkedHashMap<String, String>()
        for (option in OPTIONS) {
            val targetKey = option.keyOf(target) ?: continue
            val raw = option.keyOf(sourceFormat)?.let(source::get) ?: fallback[targetKey] ?: continue
            val value = sanitize(raw, option, target) ?: continue
            values[targetKey] = value
        }
        return render(values, target)
    }

    private fun render(values: Map<String, String>, format: Format): String = buildString {
        if (format == Format.LEGACY) {
            values.forEach { (key, value) -> append(key).append(" = ").append(value).append('\n') }
            return@buildString
        }

        var section: String? = null
        values.forEach { (path, value) ->
            val current = path.substringBefore('.')
            if (current != section) {
                if (section != null) append('\n')
                append('[').append(current).append("]\n")
                section = current
            }
            append('\t').append(path.substringAfter('.')).append(" = ").append(value).append('\n')
        }
    }

    private fun sanitize(raw: String, option: Option, target: Format): String? = when (option.kind) {
        Kind.BOOLEAN -> raw.takeIf { it == "true" || it == "false" }
        Kind.NUMBER -> sanitizeNumber(raw, target)
        Kind.STRING -> decodeString(raw)?.let(::encodeString)
        Kind.ENUM -> sanitizeEnum(raw, option, target)
    }

    private fun sanitizeNumber(raw: String, target: Format): String? {
        val value = raw.toDoubleOrNull()
        if (value != null && value.isFinite()) return value.toString()
        // Only the sectioned layout's parser understands nan and inf
        return raw.takeIf { target == Format.MODERN && it in NON_FINITE_NUMBERS }
    }

    private fun sanitizeEnum(raw: String, option: Option, target: Format): String? {
        val name = raw.removeSurrounding("\"").removeSurrounding("'")
        if (!ENUM_NAME.matches(name)) return null
        if (target == Format.LEGACY && option.legacyValues?.contains(name) == false) return null
        return "\"$name\""
    }

    private fun isLexableByToml4j(raw: String): Boolean = when {
        raw == "true" || raw == "false" -> true
        raw.startsWith('"') || raw.startsWith('\'') -> decodeString(raw) != null
        else -> FINITE_NUMBER.matches(raw)
    }

    // Keyed by key at the top level and section.key inside a table values stay as raw literals
    private fun parse(text: String): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        var section = ""
        text.lineSequence().forEach { line ->
            val trimmed = stripComment(line).trim()
            if (trimmed.isEmpty()) return@forEach

            if (trimmed.startsWith('[')) {
                section = trimmed.removePrefix("[").substringBefore(']').trim().removeSurrounding("\"")
                return@forEach
            }

            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@forEach
            val key = trimmed.take(separator).trim().removeSurrounding("\"")
            val value = trimmed.substring(separator + 1).trim()
            if (key.isEmpty() || value.isEmpty()) return@forEach

            values[if (section.isEmpty()) key else "$section.$key"] = value
        }
        return values
    }

    private fun stripComment(line: String): String {
        var quote = ' '
        var escaped = false
        line.forEachIndexed { index, character ->
            when {
                escaped -> escaped = false
                quote == '"' && character == '\\' -> escaped = true
                quote != ' ' -> if (character == quote) quote = ' '
                character == '"' || character == '\'' -> quote = character
                character == '#' -> return line.take(index)
            }
        }
        return line
    }

    private fun decodeString(raw: String): String? {
        if (raw.length < 2) return null
        val quote = raw.first()
        if (raw.last() != quote) return null

        val body = raw.substring(1, raw.length - 1)
        if (quote == '\'') return body.takeIf { '\'' !in it }
        if (quote != '"') return null

        val decoded = StringBuilder()
        var index = 0
        while (index < body.length) {
            val character = body[index++]
            if (character == '"') return null
            if (character != '\\') {
                decoded.append(character)
                continue
            }
            if (index >= body.length) return null
            when (val escape = body[index++]) {
                'b' -> decoded.append('\b')
                't' -> decoded.append('\t')
                'n' -> decoded.append('\n')
                'f' -> decoded.append('\u000C')
                'r' -> decoded.append('\r')
                '"' -> decoded.append('"')
                '\\' -> decoded.append('\\')
                'u', 'U' -> {
                    val digits = if (escape == 'u') 4 else 8
                    if (index + digits > body.length) return null
                    val code = body.substring(index, index + digits).toIntOrNull(16) ?: return null
                    if (code !in 0..Character.MAX_CODE_POINT) return null
                    decoded.appendCodePoint(code)
                    index += digits
                }
                else -> return null
            }
        }
        return decoded.toString()
    }

    private fun encodeString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\b' -> append("\\b")
                character == '\t' -> append("\\t")
                character == '\n' -> append("\\n")
                character == '\u000C' -> append("\\f")
                character == '\r' -> append("\\r")
                character < ' ' || character == '\u007F' -> append("\\u%04X".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }

}
