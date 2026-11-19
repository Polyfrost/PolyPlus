package org.polyfrost.polyplus.client.gui.screens.worldoptions

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.server.IntegratedServer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
//?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.world.Difficulty
import net.minecraft.world.WorldSettings.GameMode as GameType
*///?}
//? if >= 1.21.11 {
import net.minecraft.server.permissions.Permissions
//?}
//? if < 26.2 && > 1.8.9 {
/*import net.minecraft.world.level.LevelSettings
import org.polyfrost.polyplus.mixin.client.access.PrimaryLevelDataAccessor
*///?}

internal class WorldOptionsSnapshot(
    val difficulty: Int,
    val difficultyLocked: Boolean,
    val hardcore: Boolean,
    val demo: Boolean,
    val owner: Boolean,
    val canChange: Boolean,
    val commandsOwner: Boolean,
    val defaultMode: Int?,
    val personalMode: Int?,
    val allowCommands: Boolean?,
    val rules: List<RuleEntry> = emptyList(),
    val multiplayer: MultiplayerState? = null,
    val restrictions: List<String>? = null,
)

internal class WorldOptionsChanges(
    val difficulty: Int?,
    val lockDifficulty: Boolean,
    val defaultMode: Int?,
    val personalMode: Int?,
    val allowCommands: Boolean?,
    val rules: Map<String, Any> = emptyMap(),
    val lan: LanChange? = null,
    val guestCommands: Boolean? = null,
    val forceMode: Boolean? = null,
)

internal object WorldOptionsPlatform {
    val available: Boolean
        get() {
            val mc = Minecraft.getInstance()
            //? if > 1.8.9 {
            val server = mc.singleplayerServer
            val player = mc.player ?: return false
            if (mc.level == null) return false
            if (server != null && isOwner(server, player)) return true
            return hasPermission(player, GAMEMASTER)
            //?} else {
            /*return mc.server != null && mc.world != null
            *///?}
        }

    //? if > 1.8.9 {
    fun snapshot(): WorldOptionsSnapshot? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val server = mc.singleplayerServer
        val owner = server != null && isOwner(server, player)
        val data = level.levelData
        return WorldOptionsSnapshot(
            level.difficulty.ordinal,
            data.isDifficultyLocked || data.isHardcore,
            data.isHardcore,
            server?.isDemo == true,
            owner,
            owner || hasPermission(player, GAMEMASTER),
            hasPermission(player, OWNER),
            if (owner) server?.worldData?.gameType?.ordinal else null,
            //? if >= 26.3 {
            if (owner) server?.personalGameMode?.ordinal else null,
            //?} else {
            /*if (owner) mc.gameMode?.playerMode?.ordinal else null,
            *///?}
            if (owner) server?.worldData?.isAllowCommands else null,
            if (owner) GameRulesPlatform.read() else emptyList(),
            if (owner && server != null) MultiplayerPlatform.state(server) else null,
            restrictionLines(),
        )
    }

    fun apply(changes: WorldOptionsChanges) {
        val mc = Minecraft.getInstance()
        val connection = mc.connection
        changes.difficulty?.let { connection?.send(ServerboundChangeDifficultyPacket(Difficulty.values()[it])) }
        if (changes.lockDifficulty) connection?.send(ServerboundLockDifficultyPacket(true))
        val server = mc.singleplayerServer ?: return
        changes.allowCommands?.let { setCommands(server, it) }
        //? if >= 26.3 {
        changes.defaultMode?.let { server.setWorldGameType(GameType.values()[it]) }
        changes.personalMode?.let { server.setPersonalGameType(GameType.values()[it]) }
        //?} else {
        /*val uuid = mc.player?.uuid
        changes.defaultMode?.let { mode -> server.execute { server.setDefaultGameType(GameType.values()[mode]) } }
        changes.personalMode?.let { mode ->
            server.execute { uuid?.let { server.playerList.getPlayer(it) }?.setGameMode(GameType.values()[mode]) }
        }
        *///?}
        if (changes.rules.isNotEmpty()) GameRulesPlatform.write(changes.rules)
        MultiplayerPlatform.apply(server, changes)
    }

    //? if >= 26.2 {
    private fun setCommands(server: IntegratedServer, on: Boolean) = server.setWorldAllowCommands(on)
    //?} else {
    /*private fun setCommands(server: IntegratedServer, on: Boolean) {
        server.execute {
            val access = server.worldData as PrimaryLevelDataAccessor
            access.`polyplus$setSettings`(withCommands(access.`polyplus$getSettings`(), on))
            MultiplayerPlatform.refreshPermissions(server)
        }
    }
    *///?}

    //? if = 26.1 {
    /*private fun withCommands(settings: LevelSettings, on: Boolean) =
        LevelSettings(settings.levelName(), settings.gameType(), settings.difficultySettings(), on, settings.dataConfiguration())
    *///?}
    //? if < 26.1 && > 1.8.9 {
    /*private fun withCommands(settings: LevelSettings, on: Boolean) =
        LevelSettings(settings.levelName(), settings.gameType(), settings.hardcore(), settings.difficulty(), on, settings.gameRules(), settings.dataConfiguration)
    *///?}

    private fun restrictionLines(): List<String>? {
        //? if >= 26.3 {
        val abilities = Minecraft.getInstance().player?.chatAbilities() ?: return null
        val restrictions = abilities.restrictions().map { it.display().string }.toList()
        val statuses = listOf(
            "send_messages" to abilities.canSendMessages(),
            "send_commands" to abilities.canSendCommands(),
            "receive_system_messages" to abilities.canReceiveSystemMessages(),
            "receive_player_messages" to abilities.canReceivePlayerMessages(),
        ).map { Component.translatable("restrictions_screen.permission.${it.first}." + if (it.second) "allowed" else "denied").string }
        return restrictions + statuses
        //?} else {
        /*return null
        *///?}
    }

    //? if >= 1.21.11 {
    private const val GAMEMASTER = 0
    private const val OWNER = 1

    private fun isOwner(server: IntegratedServer, player: Player): Boolean = server.isSingleplayerOwner(player.nameAndId())

    private fun hasPermission(player: Player, level: Int): Boolean =
        player.permissions().hasPermission(if (level == OWNER) Permissions.COMMANDS_OWNER else Permissions.COMMANDS_GAMEMASTER)
    //?} else {
    /*private const val GAMEMASTER = 2
    private const val OWNER = 4

    private fun isOwner(server: IntegratedServer, player: Player): Boolean = server.isSingleplayerOwner(player.gameProfile)

    private fun hasPermission(player: Player, level: Int): Boolean = player.hasPermissions(level)
    *///?}
    //?} else {
    /*fun snapshot(): WorldOptionsSnapshot? {
        val mc = Minecraft.getInstance()
        val server = mc.server ?: return null
        val world = server.getWorld(0)
        val data = world.data
        return WorldOptionsSnapshot(
            data.difficulty.id,
            data.isDifficultyLocked || data.isHardcore,
            data.isHardcore,
            server.isDemo,
            true,
            true,
            true,
            server.getWorld(0).data.defaultGamemode.id,
            mc.interactionManager?.gameMode?.id,
            data.allowCommands(),
            GameRulesPlatform.read(),
            MultiplayerPlatform.state(server),
            null,
        )
    }

    fun apply(changes: WorldOptionsChanges) {
        val mc = Minecraft.getInstance()
        val server = mc.server ?: return
        val uuid = mc.player?.uuid
        changes.difficulty?.let { id ->
            val difficulty = Difficulty.byId(id)
            mc.options.difficulty = difficulty
            server.executeTask(Runnable { server.setDifficulty(difficulty) })
        }
        if (changes.lockDifficulty) {
            server.executeTask(Runnable { server.getWorld(0).data.setDifficultyLocked(true) })
            mc.world?.data?.setDifficultyLocked(true)
        }
        changes.allowCommands?.let { on ->
            server.executeTask(Runnable {
                server.getWorld(0).data.setAllowCommands(on)
                if (!on) server.playerManager.setAllowCommands(false)
            })
        }
        changes.defaultMode?.let { id ->
            server.executeTask(Runnable {
                server.setDefaultGameMode(GameType.byId(id))
                server.getWorld(0).data.setDefaultGameMode(GameType.byId(id))
            })
        }
        changes.personalMode?.let { id ->
            server.executeTask(Runnable { uuid?.let { server.playerManager.get(it) }?.setGameMode(GameType.byId(id)) })
        }
        if (changes.rules.isNotEmpty()) GameRulesPlatform.write(changes.rules)
        MultiplayerPlatform.apply(server, changes)
    }
    *///?}
}
