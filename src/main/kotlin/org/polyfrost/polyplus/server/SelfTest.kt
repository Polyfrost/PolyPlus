package org.polyfrost.polyplus.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.nio.file.Files
import java.nio.file.Path

//? if fabric {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
//?} else {
/*import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
*///?}

object SelfTest {
    private val LOGGER = LogManager.getLogger("PolyPlus/ServerSelfTest")
    private const val SETTLE_TICKS = 100
    private var ticks = 0
    private var done = false

    fun initialize() {
        val output = System.getProperty("polyplus.server.selftest")?.takeIf { it.isNotBlank() } ?: return
        //? if fabric {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick(Path.of(output)) })
        //?} else {
        /*eventHandler { _: TickEvent.End -> tick(Path.of(output)) }.register()
        *///?}
    }

    private fun tick(output: Path) {
        if (done || ClientPlatform.currentScreen() == null || ++ticks < SETTLE_TICKS) return
        done = true
        runCatching { write(output) }.onFailure { LOGGER.error("Could not write the server self-test report to {}", output, it) }
        Minecraft.getInstance().stop()
    }

    private fun write(output: Path) {
        OneConfigBindings.reportMissingTrees()
        val report = JsonObject(
            OptionRegistry.reports().mapValues { (_, r) ->
                JsonObject(
                    mapOf(
                        "mod" to JsonPrimitive(r.mod),
                        "modVersion" to JsonPrimitive(r.modVersion),
                        "status" to JsonPrimitive(r.status),
                        "type" to (r.type?.let(::JsonPrimitive) ?: JsonNull),
                        "bounds" to (r.bounds?.let { b -> JsonArray(b.map(::JsonPrimitive)) } ?: JsonNull),
                    ),
                )
            },
        )
        output.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(output, report.toString())
        LOGGER.info("Wrote server self-test report for {} key(s) to {}", report.size, output)
    }
}
