package org.polyfrost.polyplus.client.gui.singleplayer

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.NoticeWithLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.ProgressScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen
import net.minecraft.nbt.NbtException
import net.minecraft.nbt.ReportedNbtException
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.LevelSummary
import net.minecraft.world.level.validation.ContentValidationException
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.IOException

private val LOGGER = LogManager.getLogger("PolyPlus/SingleplayerFlows")

internal class SingleplayerFlows {
    private val mc = Minecraft.getInstance()

    private fun returnToHost(): Runnable {
        val host = ClientPlatform.currentScreen()
        return Runnable { ClientPlatform.setScreen(host) }
    }

    private fun showSymlinkNotice(back: Runnable) {
        ClientPlatform.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(back))
    }

    private fun openAccess(world: SingleplayerWorld, back: Runnable): LevelStorageSource.LevelStorageAccess? {
        //? if >= 1.21.10 {
        mc.setScreenAndShow(GenericMessageScreen(Component.translatable("selectWorld.data_read")))
        //?} else {
        /*mc.forceSetScreen(GenericMessageScreen(Component.translatable("selectWorld.data_read")))
        *///?}
        return try {
            mc.levelSource.validateAndCreateAccess(world.id)
        } catch (error: IOException) {
            SystemToast.onWorldAccessFailure(mc, world.id)
            LOGGER.error("Failed to access level {}", world.id, error)
            back.run()
            null
        } catch (error: ContentValidationException) {
            LOGGER.warn("{}", error.message)
            showSymlinkNotice(back)
            null
        }
    }

    fun create() {
        //? if >= 1.21.10 {
        CreateWorldScreen.openFresh(mc, returnToHost())
        //?} else {
        /*CreateWorldScreen.openFresh(mc, ClientPlatform.currentScreen())
        *///?}
    }

    fun play(world: SingleplayerWorld) {
        if (!world.canPlay) return
        val back = returnToHost()
        if (world.summary is LevelSummary.SymlinkLevelSummary) {
            showSymlinkNotice(back)
            return
        }
        LobbyScene.depart { mc.createWorldOpenFlows().openWorld(world.id, back) }
    }

    fun edit(world: SingleplayerWorld) {
        if (!world.canEdit) return
        val back = returnToHost()
        val access = openAccess(world, back) ?: return
        val screen = try {
            EditWorldScreen.create(mc, access) {
                access.safeClose()
                back.run()
            }
        } catch (error: IOException) {
            failEdit(world, access, back, error)
            return
        } catch (error: NbtException) {
            failEdit(world, access, back, error)
            return
        } catch (error: ReportedNbtException) {
            failEdit(world, access, back, error)
            return
        }
        ClientPlatform.setScreen(screen)
    }

    private fun failEdit(world: SingleplayerWorld, access: LevelStorageSource.LevelStorageAccess, back: Runnable, error: Exception) {
        access.safeClose()
        SystemToast.onWorldAccessFailure(mc, world.id)
        LOGGER.error("Failed to load world data {}", world.id, error)
        back.run()
    }

    fun backup(world: SingleplayerWorld) {
        if (!world.canBackup) return
        val back = returnToHost()
        val access = openAccess(world, back) ?: return
        //? if >= 26.1 {
        EditWorldScreen.makeBackupAndShowToast(access).thenAcceptAsync({
            access.safeClose()
            back.run()
        }, mc)
        //?} else {
        /*EditWorldScreen.makeBackupAndShowToast(access)
        access.safeClose()
        back.run()
        *///?}
    }

    fun recreate(world: SingleplayerWorld) {
        if (!world.canRecreate) return
        val back = returnToHost()
        val host = ClientPlatform.currentScreen()
        val access = openAccess(world, back) ?: return
        @Suppress("TooGenericExceptionCaught")
        try {
            access.use { recreateFrom(it, back, host) }
        } catch (error: Exception) {
            LOGGER.error("Unable to recreate world", error)
            ClientPlatform.setScreen(
                AlertScreen(
                    back,
                    Component.translatable("selectWorld.recreate.error.title"),
                    Component.translatable("selectWorld.recreate.error.text"),
                ),
            )
        }
    }

    private fun recreateFrom(access: LevelStorageSource.LevelStorageAccess, back: Runnable, host: Screen?) {
        val recreated = mc.createWorldOpenFlows().recreateWorldData(access)
        val settings = recreated.first
        val context = recreated.second
        val dataPackDir = CreateWorldScreen.createTempDataPackDirFromExistingWorld(access.getLevelPath(LevelResource.DATAPACK_DIR), mc)
        context.validate()
        //? if >= 1.21.10 {
        val open = { ClientPlatform.setScreen(CreateWorldScreen.createFromExisting(mc, back, settings, context, dataPackDir)) }
        //?} else {
        /*val open = { ClientPlatform.setScreen(CreateWorldScreen.createFromExisting(mc, host, settings, context, dataPackDir)) }
        *///?}
        if (!context.options().isOldCustomizedWorld) {
            open()
            return
        }
        ClientPlatform.setScreen(
            ConfirmScreen(
                { confirmed -> if (confirmed) open() else back.run() },
                Component.translatable("selectWorld.recreate.customized.title"),
                Component.translatable("selectWorld.recreate.customized.text"),
                CommonComponents.GUI_PROCEED,
                CommonComponents.GUI_CANCEL,
            ),
        )
    }

    fun delete(world: SingleplayerWorld) {
        if (!world.canDelete) return
        val back = returnToHost()
        ClientPlatform.setScreen(
            ConfirmScreen(
                { confirmed ->
                    if (confirmed) {
                        ClientPlatform.setScreen(ProgressScreen(true))
                        deleteLevel(world)
                    }
                    back.run()
                },
                Component.translatable("selectWorld.deleteQuestion"),
                Component.translatable("selectWorld.deleteWarning", world.name),
                Component.translatable("selectWorld.deleteButton"),
                CommonComponents.GUI_CANCEL,
            ),
        )
    }

    private fun deleteLevel(world: SingleplayerWorld) {
        try {
            mc.levelSource.createAccess(world.id).use { it.deleteLevel() }
        } catch (error: IOException) {
            SystemToast.onWorldDeleteFailure(mc, world.id)
            LOGGER.error("Failed to delete world {}", world.id, error)
        }
    }
}
//?}
