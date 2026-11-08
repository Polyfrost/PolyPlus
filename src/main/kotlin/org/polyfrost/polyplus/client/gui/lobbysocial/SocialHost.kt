@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeStack
import org.polyfrost.polyplus.client.party.LeaderFollow

internal interface SocialHost {
    fun join(data: ServerData)
}

internal fun SocialHost.joinAddress(address: String, name: String = address) = join(ServerData(name, address, ServerData.Type.OTHER))

@Composable
internal fun LeaderJoinEffect(host: SocialHost) {
    val card = LeaderFollow.card ?: return
    LaunchedEffect(card, card.cleared) {
        if (card.cleared) {
            LeaderFollow.dismiss()
            host.joinAddress(card.address)
        }
    }
}

internal object SocialEscape {
    val popupOpen: Boolean
        get() = EscapeStack.hasOpen

    fun closePopup(): Boolean = EscapeStack.closeTop()

    fun closeDialog(): Boolean {
        if (!SocialPageState.dialogOpen) return false
        SocialPageState.closeDialog()
        return true
    }

    fun back(frame: () -> Boolean = { false }): Boolean = closePopup() || frame() || closeDialog()

    fun window(frame: () -> Boolean): Boolean = closePopup() || closeDialog() || frame()
}
