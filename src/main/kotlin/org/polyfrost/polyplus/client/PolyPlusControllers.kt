package org.polyfrost.polyplus.client

import org.polyfrost.compose.host.controller.ControllerAction
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.Controllers
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.ping.PingInput
import org.polyfrost.polyplus.client.social.SocialOverlay

object PolyPlusControllers {
    private const val CATEGORY = "polyplus.controller.category"

    private class Held(val action: ControllerAction, val onChange: (Boolean) -> Unit) {
        var down = false

        fun poll() {
            val now = action.isPressed
            if (now == down) return
            down = now
            onChange(now)
        }
    }

    private val held = ArrayList<Held>()
    private var emoteWheel: ControllerAction? = null

    fun initialize() {
        tap("open_socials", "openSocials") { SocialOverlay.toggle() }
        tap("open_wardrobe", "openWardrobe") { PolyPlusOneConfigIntegration.openCosmeticsFromKeybind() }
        tap("toggle_mute", "toggleMute") { PartyVoice.onToggleMuteKey() }
        tap("toggle_deafen", "toggleDeafen") { PartyVoice.onToggleDeafenKey() }
        tap("join_leave_voice", "joinLeaveVoice") { PartyVoice.onJoinLeaveKey() }
        emoteWheel = hold("emote_wheel", "emoteWheel") { EmoteWheelKeybind.onControllerState(it) }
        register("emote_wheel_next", "emoteWheelNext", radial = false, ControllerButton.B).onPress { WheelOverlay.page(1) }
        hold("ping", "ping") { PingInput.onControllerState(it) }
        hold("push_to_talk", "pushToTalk") { PartyVoice.onControllerPushToTalk(it) }
        eventHandler<TickEvent.End> { for (entry in held) entry.poll() }
    }

    fun canBindEmoteWheel(): Boolean = emoteWheel?.isRebindable == true

    private fun register(id: String, name: String, radial: Boolean, button: ControllerButton = ControllerButton.NONE): ControllerAction =
        Controllers.register("polyplus:$id", "polyplus.controller.$name", button, category = CATEGORY, radial = radial)

    private fun tap(id: String, name: String, run: () -> Unit) {
        register(id, name, radial = true).onPress { run() }
    }

    private fun hold(id: String, name: String, onChange: (Boolean) -> Unit): ControllerAction =
        register(id, name, radial = false).also { held += Held(it, onChange) }
}
