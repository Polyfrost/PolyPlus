package org.polyfrost.polyplus.client.network.p2p

import de.maxhenkel.voicechat.api.Group
import de.maxhenkel.voicechat.api.VoicechatClientApi
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent
import de.maxhenkel.voicechat.api.events.ClientSoundEvent
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.party.PartySvc
import java.util.UUID

class P2PVoicechatPlugin : VoicechatPlugin {
    private val LOGGER = LogManager.getLogger()

    override fun getPluginId(): String = "polyplus"

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(ClientVoicechatInitializationEvent::class.java, ::onClientInit)
        registration.registerEvent(VoicechatServerStartingEvent::class.java, ::onServerStarting)
        registration.registerEvent(ClientVoicechatConnectionEvent::class.java) { event -> PartySvc.onConnection(SvcLink(event.voicechat), event.isConnected) }
        registration.registerEvent(ClientSoundEvent::class.java, ::onMicrophone)
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound::class.java) { event -> onSound(event) }
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound::class.java) { event -> onSound(event) }
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound::class.java) { event -> onSound(event) }
    }

    private fun onMicrophone(event: ClientSoundEvent) {
        if (!PartySvc.blockMicrophone) return
        event.rawAudio = ShortArray(event.rawAudio.size)
        event.cancel()
    }

    private fun onSound(event: ClientReceiveSoundEvent) {
        if (!PartySvc.blockSound) return
        val audio = event.rawAudio ?: return
        if (audio.isNotEmpty()) event.rawAudio = ShortArray(audio.size)
    }

    private fun onClientInit(event: ClientVoicechatInitializationEvent) {
        val host = P2PChannelRegistry.connectedPeers().firstOrNull() ?: return
        val bridge = EosVoicechatBridge.currentBridge()
        if (bridge == null) {
            LOGGER.warn("Joined a world over PolyPlus P2P, but the EOS bridge isn't installed. We can't route voice chat over it, falling back to default UDP (probably won't reach the host).")
            return
        }

        LOGGER.info("Routing Simple Voice Chat through P2P (host: {}).", host)
        event.socketImplementation = EosVoicechatClientSocket(bridge, host)
    }

    private fun onServerStarting(event: VoicechatServerStartingEvent) {
        if (P2PSessionManager.currentSessionId == null) {
            return
        }
        val bridge = EosVoicechatBridge.currentBridge()
        if (bridge == null) {
            LOGGER.warn("Hosting a world over PolyPlus P2P, but the EOS bridge isn't installed. We can't route voice chat over it, falling back to default UDP (probably won't reach the peers).")
            return
        }

        LOGGER.info("Routing Simple Voice Chat through P2P.")
        event.socketImplementation = EosVoicechatServerSocket(bridge)
    }
}

private const val CLIENT_PACKAGE = "de.maxhenkel.voicechat.voice.client."
private const val COMMON_PACKAGE = "de.maxhenkel.voicechat.voice.common."

private class SvcInternals private constructor(private val loader: ClassLoader) {
    private fun type(name: String): Class<*> = Class.forName(name, false, loader)

    private val clientManager = type(CLIENT_PACKAGE + "ClientManager")
    private val stateManagerType = type(CLIENT_PACKAGE + "ClientPlayerStateManager")
    private val stateManager = clientManager.getMethod("getPlayerStateManager")
    private val writeMuted = stateManagerType.getMethod("setMuted", Boolean::class.javaPrimitiveType)
    private val writeDisabled = stateManagerType.getMethod("setDisabled", Boolean::class.javaPrimitiveType)
    private val canEnable = stateManagerType.getMethod("canEnable")
    private val stateOf = stateManagerType.getMethod("getState", UUID::class.java)
    private val ownGroupId = stateManagerType.getMethod("getGroupID")
    private val stateDisconnected = type(COMMON_PACKAGE + "PlayerState").getMethod("isDisconnected")
    private val stateGroup = type(COMMON_PACKAGE + "PlayerState").getMethod("getGroup")
    private val groupManager = clientManager.getMethod("getGroupManager")
    private val groupById = type(CLIENT_PACKAGE + "ClientGroupManager").getMethod("getGroup", UUID::class.java)
    private val groupType = type(COMMON_PACKAGE + "ClientGroup").getMethod("getType")

    val keysShared = type(CLIENT_PACKAGE + "PTTKeyHandler").declaredMethods.any { "polyplus\$" in it.name }

    private fun states(): Any = stateManager.invoke(null)

    fun setMuted(muted: Boolean): Boolean = runCatching { writeMuted.invoke(states(), muted) }.isSuccess

    fun setDisabled(disabled: Boolean): Boolean = runCatching { writeDisabled.invoke(states(), disabled) }.isSuccess

    fun ready(): Boolean = runCatching { canEnable.invoke(states()) as Boolean }.getOrDefault(true)

    fun connected(player: UUID): Boolean = runCatching {
        val state = stateOf.invoke(states(), player) ?: return false
        !(stateDisconnected.invoke(state) as Boolean)
    }.getOrDefault(false)

    fun ownGroup(): UUID? = runCatching { ownGroupId.invoke(states()) as UUID? }.getOrNull()

    fun groupOf(player: UUID): UUID? = runCatching {
        val state = stateOf.invoke(states(), player) ?: return null
        stateGroup.invoke(state) as UUID?
    }.getOrNull()

    fun openToOthers(group: UUID?): Boolean {
        if (group == null) return true
        return runCatching {
            val found = groupById.invoke(groupManager.invoke(null), group) ?: return true
            groupType.invoke(found) === Group.Type.OPEN
        }.getOrDefault(true)
    }

    companion object {
        private val LOGGER = LogManager.getLogger()

        val instance: SvcInternals? by lazy {
            runCatching {
                SvcInternals(SvcInternals::class.java.classLoader).also {
                    if (!it.keysShared) LOGGER.warn("Simple Voice Chat changed how it reads its push-to-talk key, so party push-to-talk won't open it")
                }
            }.onFailure {
                LOGGER.warn("Simple Voice Chat's internals changed, so party voice falls back to its public API", it)
            }.getOrNull()
        }
    }
}

private class SvcLink(private val api: VoicechatClientApi) : PartySvc.Link {
    private val internals = SvcInternals.instance

    private fun voiceActivated(): Boolean =
        runCatching { api.clientConfig.getString(ACTIVATION_KEY, "") }.getOrDefault("").equals(VOICE_ACTIVATION, ignoreCase = true)

    override fun ready(): Boolean = internals?.ready() ?: true

    override fun disconnected(): Boolean = runCatching { api.isDisconnected() }.getOrDefault(true)

    override fun muted(): Boolean = voiceActivated() && runCatching { api.isMuted() }.getOrDefault(false)

    override fun disabled(): Boolean = runCatching { api.isDisabled() }.getOrDefault(false)

    override fun setMuted(muted: Boolean): Boolean = voiceActivated() && internals?.setMuted(muted) == true

    override fun setDisabled(disabled: Boolean): Boolean = internals?.setDisabled(disabled) == true

    override fun drivesState(): Boolean = internals != null

    override fun keysShared(): Boolean = internals?.keysShared == true

    override fun pushToTalkHeld(): Boolean = runCatching { api.isPushToTalkKeyPressed() }.getOrDefault(false)

    override fun connected(player: UUID): Boolean =
        internals?.connected(player) ?: runCatching { !api.isDisconnected(player) }.getOrDefault(false)

    override fun listening(player: UUID): Boolean = runCatching { !api.isDisabled(player) }.getOrDefault(false)

    override fun range(player: UUID): Double {
        val own = internals?.ownGroup()
        val theirs = internals?.groupOf(player)
        if (own != null && own == theirs) return Double.POSITIVE_INFINITY
        if (internals?.openToOthers(own) == false || internals?.openToOthers(theirs) == false) return 0.0
        return runCatching { api.voiceChatDistance }.getOrDefault(0.0)
    }

    override fun talking(player: UUID?): Boolean = runCatching { api.isTalking(player) }.getOrDefault(false)

    override fun talkHint(): String {
        if (keysShared()) return ""
        if (voiceActivated()) return "It uses voice activation, so your party push-to-talk key does nothing there."
        val key = runCatching {
            Minecraft.getInstance().options.keyMappings.firstOrNull { it.name == PUSH_TO_TALK_KEY }?.takeIf { !it.isUnbound }?.translatedKeyMessage?.string
        }.getOrNull()
        return if (key != null) {
            "Hold $key to talk there. Your party push-to-talk key does nothing there."
        } else {
            "It has its own push-to-talk key. Your party one does nothing there."
        }
    }

    private companion object {
        const val ACTIVATION_KEY = "microphone_activation_type"
        const val VOICE_ACTIVATION = "VOICE"
        const val PUSH_TO_TALK_KEY = "key.push_to_talk"
    }
}
