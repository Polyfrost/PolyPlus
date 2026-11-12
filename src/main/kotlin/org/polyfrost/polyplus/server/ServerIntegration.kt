package org.polyfrost.polyplus.server

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.server.apollo.ApolloBridge
import org.polyfrost.polyplus.server.bindings.CodeBindings
import org.polyfrost.polyplus.server.bindings.SwitchBindings
import java.util.concurrent.CopyOnWriteArrayList

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
//?}

object ServerIntegration {
    private val LOGGER = LogManager.getLogger("PolyPlus/ServerIntegration")
    const val NOTIFICATION = "notification"
    private const val MAX_NOTIFICATIONS = 16
    private const val MAX_TITLE = 256
    private const val MAX_DESCRIPTION = 2048
    private const val MAX_DEBUG_LINES = 8

    @JvmField
    val overrides = OverrideManager(
        FabricLoader.getInstance().configDir.resolve("polyplus").resolve("server-overrides.json"),
        OptionRegistry::bindings,
    ).also { it.loadJournal() }

    private val sessionStartListeners = CopyOnWriteArrayList<Runnable>()
    private val notifications = ArrayDeque<Notification>()

    fun initialize() {
        CodeBindings.initialize()
        OneConfigBindings.initialize()
        OneClientApi.initialize()
        ApolloBridge.initialize()
        //? if > 1.8.9 {
        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            if (client.isSameThread) onSessionEnd() else client.execute { onSessionEnd() }
        }
        //?}
        SelfTest.initialize()
    }

    fun capabilities(): Set<String> {
        SwitchBindings.scan()
        return OptionRegistry.keys() + NOTIFICATION
    }

    fun dispatch(command: ServerCommand) {
        runCatching {
            when (command) {
                is ServerCommand.SetOptions -> {
                    for ((key, value) in command.values) {
                        if (value == null) overrides.reset(key) else overrides.set(key, value)
                    }
                    debugNotify(command.values.map { (key, value) -> if (value == null) "$key reset" else "$key = ${value.asLookupKey()}" })
                }
                ServerCommand.ResetOptions -> {
                    overrides.resetAll()
                    debugNotify(listOf("All settings reset"))
                }
                is ServerCommand.Notify -> notify(command)
                ServerCommand.ResetNotifications -> clearNotifications()
            }
        }.onFailure { LOGGER.error("Server command {} failed", command::class.simpleName, it) }
    }

    private fun debugNotify(lines: List<String>) {
        if (!PolyPlusConfig.notifyServerSettingChanges || lines.isEmpty()) return
        val shown = lines.take(MAX_DEBUG_LINES).joinToString("\n")
        val more = if (lines.size > MAX_DEBUG_LINES) "\n+${lines.size - MAX_DEBUG_LINES} more" else ""
        Notifications.send("Server changed settings", (shown + more).take(MAX_DESCRIPTION))
    }

    fun addSessionStartListener(listener: Runnable) {
        sessionStartListeners += listener
    }

    @JvmStatic
    fun onJoinGame() {
        onSessionEnd()
        //? if = 1.8.9
        /*ServerChannels.onLegacyJoin()*/
        OneClientApi.onSessionStart()
        for (listener in sessionStartListeners) {
            runCatching { listener.run() }.onFailure { LOGGER.error("Session start listener failed", it) }
        }
    }

    @JvmStatic
    fun onDisconnect() {
        //? if = 1.8.9
        /*ServerChannels.onLegacyDisconnect()*/
        onSessionEnd()
    }

    fun onSessionEnd() {
        runCatching { overrides.resetAll() }.onFailure { LOGGER.error("Could not reset server overrides", it) }
        clearNotifications()
        OneClientApi.onSessionEnd()
    }

    private fun notify(command: ServerCommand.Notify) {
        val duration = if (command.durationMs <= 0) Notification.DEFAULT_DURATION else Math.clamp(command.durationMs, 1_000L, 60_000L).toFloat()
        val sent = Notifications.send(command.title.take(MAX_TITLE), command.description.take(MAX_DESCRIPTION), duration = duration)
        synchronized(notifications) {
            notifications.addLast(sent)
            if (notifications.size > MAX_NOTIFICATIONS) Notifications.dismiss(notifications.removeFirst())
        }
    }

    private fun clearNotifications() {
        val sent = synchronized(notifications) { notifications.toList().also { notifications.clear() } }
        sent.forEach { runCatching { Notifications.dismiss(it) } }
    }
}
