package org.polyfrost.polyplus.server.apollo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.server.OneClientProtocol.MAX_KEY_BYTES
import org.polyfrost.polyplus.server.OneClientProtocol.MAX_OPTIONS
import org.polyfrost.polyplus.server.OneClientProtocol.MAX_TEXT_BYTES
import org.polyfrost.polyplus.server.OneClientProtocol.MAX_VALUE_STRING_BYTES
import org.polyfrost.polyplus.server.OptionValue
import org.polyfrost.polyplus.server.ServerCommand
import org.polyfrost.polyplus.server.TextComponents
import java.io.ByteArrayOutputStream

object ApolloProtocol {
    const val CHANNEL = "lunar:apollo"
    const val JSON_CHANNEL = "apollo:json"
    const val GIT_BRANCH = "oneclient"

    const val MAX_PACKET_BYTES = 256 * 1024
    private const val TYPE_PREFIX = "type.googleapis.com/"
    private const val PACKAGE = "lunarclient.apollo."
    private const val OVERRIDE_SETTINGS = PACKAGE + "configurable.v1.OverrideConfigurableSettingsMessage"
    private const val SETTINGS = PACKAGE + "configurable.v1.ConfigurableSettings"
    private const val DISPLAY_NOTIFICATION = PACKAGE + "notification.v1.DisplayNotificationMessage"
    private const val RESET_NOTIFICATIONS = PACKAGE + "notification.v1.ResetNotificationsMessage"
    private const val HANDSHAKE = PACKAGE + "player.v1.PlayerHandshakeMessage"

    private const val MOD_SETTING = "mod_setting"
    private const val COMBAT = "combat"
    private const val COMBAT_PREFIX = "combat."

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_FIXED32 = 5

    private val DURATION = Regex("(-?\\d{1,12})(?:\\.(\\d{1,9}))?s")

    data class Decoded(val type: String, val commands: List<ServerCommand>?)

    private class Settings(val module: String?, val enable: Boolean, val properties: Map<String, JsonElement>)

    fun encodeHandshake(clientVersion: String, minecraftVersion: String): ByteArray {
        val mcVersion = ProtoWriter().string(1, "v" + minecraftVersion.replace('.', '_')).bytes()
        val clientVersionMessage = ProtoWriter().string(1, GIT_BRANCH).string(3, clientVersion).bytes()
        val handshake = ProtoWriter().bytes(1, mcVersion).bytes(2, clientVersionMessage).bytes()
        return ProtoWriter().string(1, TYPE_PREFIX + HANDSHAKE).bytes(2, handshake).bytes()
    }

    fun decode(data: ByteArray, knownKeys: Collection<String>): Decoded {
        require(data.size <= MAX_PACKET_BYTES) { "Packet is ${data.size} bytes" }
        var typeUrl = ""
        var value = ProtoReader(ByteArray(0))
        val any = ProtoReader(data)
        while (any.hasMore()) {
            val tag = any.tag()
            when (tag ushr 3) {
                1 -> typeUrl = any.string(MAX_KEY_BYTES)
                2 -> value = any.message()
                else -> any.skip(tag)
            }
        }
        val type = typeUrl.substringAfterLast('/')
        val commands = when (type) {
            OVERRIDE_SETTINGS -> {
                val settings = ArrayList<Settings>()
                while (value.hasMore()) {
                    val tag = value.tag()
                    if (tag ushr 3 == 1) settings += readSettings(value.message()) else value.skip(tag)
                }
                commands(settings, knownKeys)
            }
            SETTINGS -> commands(listOf(readSettings(value)), knownKeys)
            DISPLAY_NOTIFICATION -> listOf(readNotification(value))
            RESET_NOTIFICATIONS -> listOf(ServerCommand.ResetNotifications)
            else -> null
        }
        return Decoded(type, commands)
    }

    fun decodeJson(data: ByteArray, knownKeys: Collection<String>): Decoded {
        require(data.size <= MAX_PACKET_BYTES) { "Packet is ${data.size} bytes" }
        val message = Json.parseToJsonElement(data.toString(Charsets.UTF_8)) as? JsonObject
            ?: throw IllegalArgumentException("Not a JSON object")
        val typeUrl = message.string("@type") ?: throw IllegalArgumentException("Missing @type")
        require(typeUrl.length <= MAX_KEY_BYTES) { "Type URL is too long" }
        val type = typeUrl.substringAfterLast('/')
        val commands = when (type) {
            OVERRIDE_SETTINGS -> {
                val list = message.field("configurable_settings", "configurableSettings") as? JsonArray ?: JsonArray(emptyList())
                require(list.size <= MAX_OPTIONS) { "Too many modules" }
                commands(list.map { readSettings(it as? JsonObject ?: throw IllegalArgumentException("Bad module entry")) }, knownKeys)
            }
            SETTINGS -> commands(listOf(readSettings(message)), knownKeys)
            DISPLAY_NOTIFICATION -> listOf(
                notification(
                    message.string("title_adventure_json_lines", "titleAdventureJsonLines"),
                    message.string("description_adventure_json_lines", "descriptionAdventureJsonLines"),
                    message.string("title"),
                    message.string("description"),
                    message.string("display_time", "displayTime")?.let(::parseDuration) ?: 0L,
                    message.string("resource_location", "resourceLocation") ?: iconLocation(message.field("icon") as? JsonObject),
                ),
            )
            RESET_NOTIFICATIONS -> listOf(ServerCommand.ResetNotifications)
            else -> null
        }
        return Decoded(type, commands)
    }

    private fun commands(settings: List<Settings>, knownKeys: Collection<String>): List<ServerCommand> {
        val values = LinkedHashMap<String, OptionValue?>()
        for (module in settings) {
            val inModule: (String) -> Boolean = when (module.module) {
                MOD_SETTING -> { key -> !key.startsWith(COMBAT_PREFIX) }
                COMBAT -> { key -> key.startsWith(COMBAT_PREFIX) }
                else -> continue
            }
            if (!module.enable) {
                knownKeys.filter(inModule).forEach { values[it] = null }
                continue
            }
            val prefix = if (module.module == COMBAT) COMBAT_PREFIX else ""
            for ((key, element) in module.properties) {
                if (!inModule(prefix + key)) continue
                if (element is JsonNull) {
                    values[prefix + key] = null
                    continue
                }
                val primitive = element as? JsonPrimitive ?: continue
                values[prefix + key] = when {
                    primitive.isString -> {
                        require(primitive.content.length <= MAX_VALUE_STRING_BYTES) { "Value of $key is too long" }
                        OptionValue.Str(primitive.content)
                    }
                    primitive.booleanOrNull != null -> OptionValue.Bool(primitive.content.toBoolean())
                    else -> OptionValue.Num(primitive.doubleOrNull ?: continue)
                }
            }
        }
        require(values.size <= MAX_OPTIONS) { "Too many options" }
        return if (values.isEmpty()) emptyList() else listOf(ServerCommand.SetOptions(values))
    }

    private fun notification(titleJson: String?, descriptionJson: String?, title: String?, description: String?, durationMs: Long, icon: String?) =
        ServerCommand.Notify(
            titleJson?.let(TextComponents::toPlainText) ?: title.orEmpty(),
            descriptionJson?.let(TextComponents::toPlainText) ?: description.orEmpty(),
            durationMs,
            icon?.ifEmpty { null },
        )

    private fun readSettings(reader: ProtoReader): Settings {
        var module: String? = null
        var enable = false
        val properties = LinkedHashMap<String, JsonElement>()
        while (reader.hasMore()) {
            val tag = reader.tag()
            when (tag ushr 3) {
                2 -> module = reader.string(MAX_KEY_BYTES)
                3 -> enable = reader.varint() != 0L
                4 -> {
                    val entry = reader.message()
                    var key: String? = null
                    var value: JsonElement? = null
                    while (entry.hasMore()) {
                        val entryTag = entry.tag()
                        when (entryTag ushr 3) {
                            1 -> key = entry.string(MAX_KEY_BYTES)
                            2 -> value = readValue(entry.message())
                            else -> entry.skip(entryTag)
                        }
                    }
                    if (key != null && value != null) properties[key] = value
                    require(properties.size <= MAX_OPTIONS) { "Too many properties" }
                }
                else -> reader.skip(tag)
            }
        }
        return Settings(module, enable, properties)
    }

    private fun readValue(reader: ProtoReader): JsonElement? {
        var value: JsonElement? = null
        while (reader.hasMore()) {
            val tag = reader.tag()
            value = when (tag ushr 3) {
                1 -> JsonNull.also { reader.varint() }
                2 -> JsonPrimitive(Double.fromBits(reader.fixed64()))
                3 -> JsonPrimitive(reader.string(MAX_VALUE_STRING_BYTES))
                4 -> JsonPrimitive(reader.varint() != 0L)
                else -> null.also { reader.skip(tag) }
            }
        }
        return value
    }

    private fun readNotification(reader: ProtoReader): ServerCommand.Notify {
        var title: String? = null
        var description: String? = null
        var resourceLocation: String? = null
        var durationMs = 0L
        var titleJson: String? = null
        var descriptionJson: String? = null
        var icon: String? = null
        while (reader.hasMore()) {
            val tag = reader.tag()
            when (tag ushr 3) {
                1 -> title = reader.string(MAX_TEXT_BYTES)
                2 -> description = reader.string(MAX_TEXT_BYTES)
                3 -> resourceLocation = reader.string(MAX_KEY_BYTES)
                4 -> durationMs = readDuration(reader.message())
                5 -> titleJson = reader.string(MAX_TEXT_BYTES)
                6 -> descriptionJson = reader.string(MAX_TEXT_BYTES)
                7 -> icon = readIcon(reader.message())
                else -> reader.skip(tag)
            }
        }
        return notification(titleJson, descriptionJson, title, description, durationMs, resourceLocation ?: icon)
    }

    private fun readDuration(reader: ProtoReader): Long {
        var seconds = 0L
        var nanos = 0L
        while (reader.hasMore()) {
            val tag = reader.tag()
            when (tag ushr 3) {
                1 -> seconds = reader.varint()
                2 -> nanos = reader.varint().toInt().toLong()
                else -> reader.skip(tag)
            }
        }
        return toMillis(seconds, nanos)
    }

    private fun toMillis(seconds: Long, nanos: Long): Long = Math.clamp(seconds, 0L, Int.MAX_VALUE.toLong()) * 1000 + Math.clamp(nanos, 0L, 999_999_999L) / 1_000_000

    private fun readIcon(reader: ProtoReader): String? {
        var location: String? = null
        while (reader.hasMore()) {
            val tag = reader.tag()
            if (tag ushr 3 in 2..4) {
                val icon = reader.message()
                while (icon.hasMore()) {
                    val iconTag = icon.tag()
                    if (iconTag ushr 3 == 1) location = icon.string(MAX_KEY_BYTES) else icon.skip(iconTag)
                }
            } else {
                reader.skip(tag)
            }
        }
        return location
    }

    private fun readSettings(json: JsonObject): Settings {
        val properties = json.field("properties") as? JsonObject ?: JsonObject(emptyMap())
        require(properties.size <= MAX_OPTIONS) { "Too many properties" }
        require(properties.keys.all { it.length <= MAX_KEY_BYTES }) { "Property key is too long" }
        val enable = (json.field("enable") as? JsonPrimitive)?.booleanOrNull ?: false
        return Settings(json.string("apollo_module", "apolloModule"), enable, properties)
    }

    private fun iconLocation(icon: JsonObject?): String? {
        if (icon == null) return null
        return listOf("simple_resource_location", "simpleResourceLocation", "advanced_resource_location", "advancedResourceLocation", "resource_location", "resourceLocation")
            .firstNotNullOfOrNull { (icon[it] as? JsonObject)?.string("resource_location", "resourceLocation") }
    }

    private fun parseDuration(text: String): Long {
        val match = DURATION.matchEntire(text) ?: throw IllegalArgumentException("Bad duration $text")
        val nanos = match.groupValues[2].padEnd(9, '0').toLong()
        return toMillis(match.groupValues[1].toLong(), nanos)
    }

    private fun JsonObject.field(vararg names: String): JsonElement? = names.firstNotNullOfOrNull { this[it] }

    private fun JsonObject.string(vararg names: String): String? {
        val element = field(*names) as? JsonPrimitive ?: return null
        if (!element.isString) return null
        require(element.content.length <= MAX_TEXT_BYTES) { "${names[0]} is too long" }
        return element.content
    }

    private class ProtoReader(private val data: ByteArray, private var position: Int = 0, private val end: Int = data.size) {
        fun hasMore(): Boolean = position < end

        fun tag(): Int {
            val tag = varint()
            require(tag in 8..Int.MAX_VALUE) { "Bad tag $tag" }
            return tag.toInt()
        }

        fun varint(): Long {
            var value = 0L
            for (shift in 0 until 64 step 7) {
                val b = byte()
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return value
            }
            throw IllegalArgumentException("Varint is too long")
        }

        fun fixed64(): Long {
            var value = 0L
            for (shift in 0 until 64 step 8) value = value or (byte().toLong() shl shift)
            return value
        }

        fun string(maxBytes: Int): String {
            val length = length()
            require(length <= maxBytes) { "String of $length bytes" }
            return String(data, position, length, Charsets.UTF_8).also { position += length }
        }

        fun message(): ProtoReader {
            val length = length()
            return ProtoReader(data, position, position + length).also { position += length }
        }

        fun skip(tag: Int) {
            val bytes = when (tag and 7) {
                WIRE_VARINT -> {
                    varint()
                    return
                }
                WIRE_FIXED64 -> 8
                WIRE_LENGTH -> length()
                WIRE_FIXED32 -> 4
                else -> throw IllegalArgumentException("Unsupported wire type ${tag and 7}")
            }
            require(bytes <= end - position) { "Truncated field" }
            position += bytes
        }

        private fun length(): Int {
            val length = varint()
            require(length in 0..(end - position).toLong()) { "Bad length $length" }
            return length.toInt()
        }

        private fun byte(): Int {
            require(position < end) { "Unexpected end of message" }
            return data[position++].toInt() and 0xFF
        }
    }

    private class ProtoWriter {
        private val out = ByteArrayOutputStream()

        fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

        fun bytes(field: Int, value: ByteArray): ProtoWriter {
            varint(field shl 3 or WIRE_LENGTH)
            varint(value.size)
            out.write(value, 0, value.size)
            return this
        }

        private fun varint(value: Int) {
            var remaining = value
            while (remaining and 0x7F.inv() != 0) {
                out.write((remaining and 0x7F) or 0x80)
                remaining = remaining ushr 7
            }
            out.write(remaining)
        }

        fun bytes(): ByteArray = out.toByteArray()
    }
}
