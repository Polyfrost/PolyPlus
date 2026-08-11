package org.polyfrost.polyplus.client.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform

class SessionRefreshPrompt internal constructor() {
    private enum class Stage { IDLE, REFRESHING, RECONNECTING, FAILED }

    private var stage = Stage.IDLE
    private var button: Button? = null
    private var screen: Screen? = null

    fun attach(button: Button, screen: Screen) {
        this.button = button
        this.screen = screen
        update()
    }

    fun label(): Component = Component.translatable(
        when (stage) {
            Stage.IDLE -> "polyplus.session.refresh"
            Stage.REFRESHING -> "polyplus.session.refreshing"
            Stage.RECONNECTING -> "polyplus.session.reconnecting"
            Stage.FAILED -> "polyplus.session.refreshFailed"
        },
    )

    fun onPress() {
        if (stage == Stage.IDLE || stage == Stage.FAILED) start()
    }

    private fun start() {
        stage = Stage.REFRESHING
        update()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val result = SessionRefresh.refreshActiveSession()
            ClientPlatform.runOnMain { finish(result.isSuccess) }
        }
    }

    private fun finish(refreshed: Boolean) {
        if (ClientPlatform.currentScreen() !== screen) return
        stage = if (refreshed) Stage.RECONNECTING else Stage.FAILED
        update()
        if (refreshed && !SessionRefresh.reconnect()) {
            stage = Stage.FAILED
            update()
        }
    }

    private fun update() {
        val button = button ?: return
        button.message = label()
        button.active = stage == Stage.IDLE || stage == Stage.FAILED
    }
}
