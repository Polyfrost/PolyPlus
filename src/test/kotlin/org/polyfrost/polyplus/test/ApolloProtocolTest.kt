package org.polyfrost.polyplus.test

import com.google.protobuf.Any
import com.google.protobuf.Duration
import com.google.protobuf.ListValue
import com.google.protobuf.Message
import com.google.protobuf.NullValue
import com.google.protobuf.Value
import com.google.protobuf.util.JsonFormat
import com.lunarclient.apollo.common.v1.Icon
import com.lunarclient.apollo.common.v1.SimpleResourceLocationIcon
import com.lunarclient.apollo.configurable.v1.ConfigurableSettings
import com.lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage
import com.lunarclient.apollo.notification.v1.DisplayNotificationMessage
import com.lunarclient.apollo.notification.v1.ResetNotificationsMessage
import com.lunarclient.apollo.player.v1.PlayerHandshakeMessage
import com.lunarclient.apollo.waypoint.v1.RemoveWaypointMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.polyfrost.polyplus.server.OptionValue
import org.polyfrost.polyplus.server.ServerCommand
import org.polyfrost.polyplus.server.apollo.ApolloProtocol
import kotlin.random.Random

class ApolloProtocolTest {
    private val knownKeys = listOf("fov.enabled", "zoom.smooth-zoom", "combat.disable-miss-penalty", "combat.allow-dig-and-use")

    private val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
        .add(OverrideConfigurableSettingsMessage.getDescriptor())
        .add(ConfigurableSettings.getDescriptor())
        .add(DisplayNotificationMessage.getDescriptor())
        .add(ResetNotificationsMessage.getDescriptor())
        .add(RemoveWaypointMessage.getDescriptor())
        .build()

    private fun decodeAll(message: Message): List<ApolloProtocol.Decoded> {
        val any = Any.pack(message)
        val printer = JsonFormat.printer().usingTypeRegistry(typeRegistry)
        return listOf(
            ApolloProtocol.decode(any.toByteArray(), knownKeys),
            ApolloProtocol.decodeJson(printer.print(any).toByteArray(), knownKeys),
            ApolloProtocol.decodeJson(printer.preservingProtoFieldNames().print(any).toByteArray(), knownKeys),
        )
    }

    private fun assertCommands(expected: List<ServerCommand>?, message: Message) {
        for (decoded in decodeAll(message)) {
            assertEquals(message.descriptorForType.fullName, decoded.type)
            assertEquals(expected, decoded.commands)
        }
    }

    private fun settings(module: String, enable: Boolean, properties: Map<String, Value>) =
        ConfigurableSettings.newBuilder().setApolloModule(module).setEnable(enable).putAllProperties(properties).build()

    @Test
    fun `override settings map mod_setting and combat keys`() {
        val message = OverrideConfigurableSettingsMessage.newBuilder()
            .addConfigurableSettings(
                settings(
                    "mod_setting",
                    true,
                    mapOf(
                        "fov.enabled" to Value.newBuilder().setBoolValue(false).build(),
                        "fov.speed-f-o-v" to Value.newBuilder().setNumberValue(1.5).build(),
                        "crosshair.color" to Value.newBuilder().setStringValue("ff00ff00").build(),
                        "zoom.smooth-zoom" to Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(),
                        "combat.allow-dig-and-use" to Value.newBuilder().setBoolValue(true).build(),
                        "server_rule.list" to Value.newBuilder().setListValue(ListValue.getDefaultInstance()).build(),
                    ),
                ),
            )
            .addConfigurableSettings(settings("combat", true, mapOf("disable-miss-penalty" to Value.newBuilder().setBoolValue(true).build())))
            .addConfigurableSettings(settings("waypoint", true, mapOf("server-handles-waypoints" to Value.newBuilder().setBoolValue(true).build())))
            .addConfigurableSettings(ConfigurableSettings.newBuilder().setLunarClientMod("fov").setEnable(false).build())
            .build()
        val expected = mapOf(
            "fov.enabled" to OptionValue.Bool(false),
            "fov.speed-f-o-v" to OptionValue.Num(1.5),
            "crosshair.color" to OptionValue.Str("ff00ff00"),
            "zoom.smooth-zoom" to null,
            "combat.disable-miss-penalty" to OptionValue.Bool(true),
        )
        assertCommands(listOf(ServerCommand.SetOptions(expected)), message)
    }

    @Test
    fun `disabled module resets its keys`() {
        val property = mapOf("disable-miss-penalty" to Value.newBuilder().setBoolValue(true).build())
        assertCommands(
            listOf(ServerCommand.SetOptions(mapOf("combat.disable-miss-penalty" to null, "combat.allow-dig-and-use" to null))),
            settings("combat", false, property),
        )
        assertCommands(
            listOf(ServerCommand.SetOptions(mapOf("fov.enabled" to null, "zoom.smooth-zoom" to null))),
            settings("mod_setting", false, emptyMap()),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `display notification`() {
        val message = DisplayNotificationMessage.newBuilder()
            .setTitleAdventureJsonLines("""{"text":"UHC Announcement","color":"green"}""")
            .setDescriptionAdventureJsonLines("""{"text":"UHC starts in ","extra":[{"text":"5 minutes","color":"red"}]}""")
            .setDisplayTime(Duration.newBuilder().setSeconds(5).setNanos(250_000_000))
            .setResourceLocation("textures/items/apple_golden.png")
            .build()
        assertCommands(listOf(ServerCommand.Notify("UHC Announcement", "UHC starts in 5 minutes", 5250, "textures/items/apple_golden.png")), message)

        val withIcon = DisplayNotificationMessage.newBuilder()
            .setTitle("Plain")
            .setIcon(Icon.newBuilder().setSimpleResourceLocation(SimpleResourceLocationIcon.newBuilder().setResourceLocation("minecraft:apple").setSize(8)))
            .build()
        assertCommands(listOf(ServerCommand.Notify("Plain", "", 0, "minecraft:apple")), withIcon)
    }

    @Test
    fun `reset notifications and unknown types`() {
        assertCommands(listOf(ServerCommand.ResetNotifications), ResetNotificationsMessage.getDefaultInstance())
        assertCommands(null, RemoveWaypointMessage.newBuilder().setName("spawn").build())
    }

    @Test
    fun `apollo example json payloads`() {
        val combat = """{"apollo_module":"combat","enable":true,"properties":{"disable-miss-penalty":true},""" +
            """"@type":"type.googleapis.com/lunarclient.apollo.configurable.v1.ConfigurableSettings"}"""
        assertEquals(
            listOf(ServerCommand.SetOptions(mapOf("combat.disable-miss-penalty" to OptionValue.Bool(true)))),
            ApolloProtocol.decodeJson(combat.toByteArray(), knownKeys).commands,
        )
        val rollback = """{"apollo_module":"mod_setting","enable":true,"properties":{"lighting.enabled":null},""" +
            """"@type":"type.googleapis.com/lunarclient.apollo.configurable.v1.ConfigurableSettings"}"""
        assertEquals(
            listOf(ServerCommand.SetOptions(mapOf("lighting.enabled" to null))),
            ApolloProtocol.decodeJson(rollback.toByteArray(), knownKeys).commands,
        )
    }

    @Test
    fun `handshake parses with the generated classes`() {
        val handshake = Any.parseFrom(ApolloProtocol.encodeHandshake("1.2.3+1.21.1", "1.21.1")).unpack(PlayerHandshakeMessage::class.java)
        assertEquals("v1_21_1", handshake.minecraftVersion.enum)
        assertEquals("oneclient", handshake.lunarClientVersion.gitBranch)
        assertEquals("1.2.3+1.21.1", handshake.lunarClientVersion.semver)
        assertEquals("", handshake.lunarClientVersion.gitCommit)
    }

    @Test
    fun `malformed input is rejected without errors`() {
        val valid = Any.pack(settings("combat", true, mapOf("disable-miss-penalty" to Value.newBuilder().setBoolValue(true).build()))).toByteArray()
        assertThrows<IllegalArgumentException> { ApolloProtocol.decode(valid.copyOf(valid.size - 1), knownKeys) }
        assertThrows<IllegalArgumentException> { ApolloProtocol.decode(ByteArray(ApolloProtocol.MAX_PACKET_BYTES + 1), knownKeys) }
        assertThrows<IllegalArgumentException> { ApolloProtocol.decodeJson("[1]".toByteArray(), knownKeys) }
        assertNull(ApolloProtocol.decode(ByteArray(0), knownKeys).commands)

        val random = Random(42)
        repeat(10_000) {
            val bytes = valid.copyOf()
            repeat(1 + random.nextInt(4)) { bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte() }
            try {
                ApolloProtocol.decode(bytes, knownKeys)
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
