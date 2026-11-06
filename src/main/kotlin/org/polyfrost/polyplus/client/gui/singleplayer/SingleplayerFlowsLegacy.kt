package org.polyfrost.polyplus.client.gui.singleplayer

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import net.minecraft.client.gui.screen.ConfirmScreen
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.gui.screen.menu.world.EditWorldScreen
import net.minecraft.client.resource.language.I18n
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbyWorldBackup
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform

private val LOGGER = LogManager.getLogger("PolyPlus/SingleplayerFlows")

internal class SingleplayerFlows {
    private val mc = Minecraft.getInstance()

    fun create() = ClientPlatform.setScreen(CreateWorldScreen(ClientPlatform.currentScreen()))

    fun play(world: SingleplayerWorld) {
        if (world.canPlay) LobbyScene.depart { HostWorldManager.openWorld(world.id) }
    }

    fun edit(world: SingleplayerWorld) {
        if (world.canEdit) ClientPlatform.setScreen(EditWorldScreen(ClientPlatform.currentScreen(), world.id))
    }

    fun backup(world: SingleplayerWorld) {
        if (!world.canBackup) return
        runCatching { LobbyWorldBackup.write(world.id) }
            .onSuccess { SocialNotices.emit("World backed up: $it") }
            .onFailure {
                LOGGER.error("Failed to back up world {}", world.id, it)
                SocialErrors.emit("Couldn't back up the world")
            }
    }

    fun recreate(world: SingleplayerWorld) {
        if (!world.canRecreate) return
        runCatching {
            val screen = CreateWorldScreen(ClientPlatform.currentScreen())
            val storage = mc.worldStorageSource.get(world.id, false)
            val data = storage.loadData()
            storage.forceSave()
            screen.copyWorld(data)
            ClientPlatform.setScreen(screen)
        }.onFailure { LOGGER.error("Unable to recreate world", it) }
    }

    fun delete(world: SingleplayerWorld) {
        if (!world.canDelete) return
        val host = ClientPlatform.currentScreen()
        ClientPlatform.setScreen(
            ConfirmScreen(
                { confirmed, _ ->
                    if (confirmed) {
                        mc.worldStorageSource.flush()
                        mc.worldStorageSource.delete(world.id)
                    }
                    ClientPlatform.setScreen(host)
                },
                I18n.translate("selectWorld.deleteQuestion"),
                I18n.translate("selectWorld.deleteWarning", world.name),
                I18n.translate("selectWorld.deleteButton"),
                I18n.translate("gui.cancel"),
                0,
            ),
        )
    }
}
*///?}
