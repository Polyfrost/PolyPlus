package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
//? if > 1.8.9 {
import net.minecraft.client.multiplayer.resolver.ServerAddress
//?} else {
/*import net.minecraft.client.gui.screen.ProgressScreen
import org.polyfrost.polyplus.client.lobby.currentServer
import org.polyfrost.polyplus.client.lobby.level
import org.polyfrost.polyplus.client.social.singleplayerServer
import org.polyfrost.polyplus.mixin.client.access.MinecraftServerAccessor
*///?}
//? if >= 1.21.8 {
import net.minecraft.client.multiplayer.ClientLevel
//?} else if > 1.8.9 {
/*import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.network.chat.Component
*///?}
import net.minecraft.client.multiplayer.ServerData
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.ClientPlatform

object PartyJoin {
    private val LOGGER = LogManager.getLogger()

    private const val CHECK_TIMEOUT_MS = 10_000L
    private const val SAVE_TIMEOUT_MS = 30_000L
    private const val SAVING_LEVEL = "menu.savingLevel"

    enum class Here { SERVER, SINGLEPLAYER, HOSTING }

    class Pending internal constructor(
        val target: String,
        val here: Here,
        val address: String?,
        internal val server: ServerData?,
        internal val before: () -> Unit,
        internal val after: () -> Unit,
    ) {
        var ready by mutableStateOf(server == null)
            internal set
        var problem by mutableStateOf<String?>(null)
            internal set
        internal var check: ServerCheck? = server?.let { ServerCheck(it.ip) }
        internal val openedAt = System.currentTimeMillis()
    }

    var pending by mutableStateOf<Pending?>(null)
        private set

    private var leaving: (() -> Unit)? = null
    private var left = false
    //? if = 1.8.9 {
    /*private var saving: Thread? = null
    private var savingSince = 0L
    *///?}

    fun inWorld(): Boolean = ClientPlatform.inWorld() && !LobbyScene.terrainActive()

    fun serverAddress(): String? = Minecraft.getInstance().currentServer?.ip?.trim()

    fun server(data: ServerData, before: () -> Unit = {}) {
        if (!inWorld()) {
            before()
            connect(data)
            return
        }
        ask(Pending(data.ip, here(), serverAddress(), data, before) { connect(data) })
    }

    fun world(name: String, join: () -> Unit) {
        if (!inWorld()) {
            join()
            return
        }
        ask(
            Pending(name, here(), serverAddress(), null, {}) {
                ClientPlatform.setScreen(TitleScreen())
                join()
            },
        )
    }

    fun confirm() {
        val current = pending ?: return
        if (!current.ready || leaving != null) return
        pending = null
        current.before()
        LOGGER.info("Leaving this world to join {}", current.target)
        left = false
        leaving = current.after
    }

    fun cancel() {
        pending?.check?.close()
        pending = null
    }

    fun tick() {
        pending?.let(::poll)
        val after = leaving ?: return
        if (!left) {
            left = true
            runCatching(::leave).onFailure {
                leaving = null
                throw it
            }
        }
        if (!saved()) return
        leaving = null
        after()
    }

    private fun ask(request: Pending) {
        if (leaving != null) return
        cancel()
        pending = request
    }

    private fun here(): Here {
        val mc = Minecraft.getInstance()
        return when {
            mc.singleplayerServer == null -> Here.SERVER
            P2PSessionManager.currentSessionId != null -> Here.HOSTING
            else -> Here.SINGLEPLAYER
        }
    }

    private fun poll(current: Pending) {
        val check = current.check ?: return
        val result = check.poll()
        val expired = System.currentTimeMillis() - current.openedAt > CHECK_TIMEOUT_MS
        when {
            result == ServerCheck.Result.OK -> current.ready = true
            result == ServerCheck.Result.INCOMPATIBLE -> {
                val needs = check.serverVersion.ifBlank { null }
                current.problem = needs?.let { "${current.target} needs Minecraft $it" } ?: "${current.target} needs another Minecraft version"
            }
            result == ServerCheck.Result.UNREACHABLE || expired -> {
                if (result == ServerCheck.Result.PENDING) check.close()
                current.problem = "Can't reach ${current.target}"
            }
            else -> return
        }
        current.check = null
    }

    private fun leave() {
        val mc = Minecraft.getInstance()
        //? if >= 1.21.8 {
        val local = mc.isLocalServer
        mc.level?.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE)
        if (local) mc.disconnectWithSavingScreen() else mc.disconnectWithProgressScreen()
        //?} else if > 1.8.9 {
        /*val local = mc.isLocalServer
        mc.level?.disconnect()
        if (local) mc.disconnect(GenericMessageScreen(Component.translatable(SAVING_LEVEL))) else mc.disconnect()
        *///?} else {
        /*saving = (mc.singleplayerServer as? MinecraftServerAccessor)?.`polyplus$getThread`()
        savingSince = System.currentTimeMillis()
        mc.level?.disconnect()
        mc.setWorld(null)
        ClientPlatform.setScreen(ProgressScreen())
        *///?}
    }

    private fun saved(): Boolean {
        //? if = 1.8.9 {
        /*val thread = saving ?: return true
        if (thread.isAlive && System.currentTimeMillis() - savingSince < SAVE_TIMEOUT_MS) return false
        saving = null
        *///?}
        return true
    }

    private fun connect(data: ServerData) {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        ConnectScreen.startConnecting(TitleScreen(), mc, ServerAddress.parseString(data.ip), data, false, null)
        //?} else {
        /*ClientPlatform.setScreen(ConnectScreen(TitleScreen(), mc, data.entry))
        *///?}
    }
}
