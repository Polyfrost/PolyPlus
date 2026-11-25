package org.polyfrost.polyplus.client.gui.vanillamenu

//? if > 1.8.9 {
import net.minecraft.SharedConstants
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.utils.ClientPlatform

/**
 * Asked before opening a world saved in another Minecraft version, like the confirm on disconnect,
 * so a misclick in the world list can't upgrade (or break) a world from another version.
 */
object OtherVersionConfirm {
    @JvmStatic
    fun currentVersionName(): String = SharedConstants.getCurrentVersion().name()

    @JvmStatic
    fun show(worldName: String, worldVersion: String, open: Runnable, back: Runnable) {
        val version = worldVersion.ifBlank { "another version" }
        ClientPlatform.setScreen(
            ConfirmScreen(
                { confirmed -> if (confirmed) open.run() else back.run() },
                Component.literal("Open a world from $version?"),
                Component.literal(
                    "\"$worldName\" was last played in $version and you are playing ${currentVersionName()}. " +
                        "Opening it here can change or break the world. Make a backup first if you want to go back.",
                ),
                Component.literal("Open anyway"),
                CommonComponents.GUI_CANCEL,
            ),
        )
    }
}
//?}
