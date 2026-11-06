package org.polyfrost.polyplus.client.party

import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.PartyMember
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PartySvc {
    interface Link {
        fun ready(): Boolean

        fun disconnected(): Boolean

        fun muted(): Boolean

        fun disabled(): Boolean

        fun setMuted(muted: Boolean): Boolean

        fun setDisabled(disabled: Boolean): Boolean

        fun drivesState(): Boolean

        fun keysShared(): Boolean

        fun pushToTalkHeld(): Boolean

        fun connected(player: UUID): Boolean

        fun listening(player: UUID): Boolean

        fun range(player: UUID): Double

        fun talking(player: UUID?): Boolean

        fun talkHint(): String
    }

    private const val TITLE = "Simple Voice Chat"
    private const val MAX_NAMES = 2
    private const val ENTER_RANGE = 0.7
    private const val LEAVE_RANGE = 0.8

    private val answers = ConcurrentHashMap<String, Boolean>()

    @Volatile private var connected = false
    @Volatile private var svcKey = false
    @Volatile private var prompt: Notification? = null
    private var promptKey: String? = null
    private var announced = false
    private var paired: Set<String> = emptySet()

    @Volatile var link: Link? = null
        private set

    @Volatile var linked = false
        private set

    @Volatile var blockMicrophone = false

    @Volatile var blockSound = false

    @Volatile var talkOpen = false

    fun onConnection(link: Link, connected: Boolean) {
        this.link = link
        this.connected = connected
    }

    fun sharePushToTalk(down: Boolean): Boolean {
        svcKey = down
        return !blockMicrophone && (down || talkOpen)
    }

    fun pushToTalkHeld(): Boolean {
        val link = link
        if (!linked || link == null) return false
        return if (link.keysShared()) svcKey else link.pushToTalkHeld()
    }

    fun resolve(others: List<PartyMember>, key: String?): Set<String> {
        val link = link
        if (link == null || !connected || key == null || link.disconnected() || !link.ready() || !PolyConnection.isConnected) {
            release()
            return emptySet()
        }
        val choice = choice(key)
        if (choice != true) unlink()
        linked = choice == true && (linked || !link.disabled())
        val onSvc = others.filter { isOnSvc(it, key, link) }
        if (choice == null && !link.disabled() && onSvc.isNotEmpty()) ask(key, onSvc, link) else closePrompt()
        paired = if (linked) onSvc.filter { inReach(it, link) }.mapTo(HashSet()) { it.player } else emptySet()
        if (paired.isNotEmpty() && !announced) {
            announced = true
            Notifications.info(TITLE, message("Nearby party members are on it now. Party voice covers anyone out of range.", link.talkHint()))
        }
        return paired
    }

    fun release() {
        unlink()
        closePrompt()
    }

    fun speaking(paired: Set<String>): Set<String> {
        val link = link ?: return emptySet()
        return paired.filterTo(HashSet()) { player -> parse(player)?.let(link::talking) == true }
    }

    fun noteUnmuted() {
        val link = link ?: return
        if (linked && !link.drivesState() && link.muted()) {
            Notifications.info(TITLE, "You're still muted there. Use its mute key to talk to nearby members.")
        }
    }

    fun noteUndeafened() {
        val link = link ?: return
        if (linked && !link.drivesState() && link.disabled()) {
            Notifications.info(TITLE, "It's still turned off. Use its disable key to hear nearby members.")
        }
    }

    fun noteMutedThere() {
        Notifications.info(TITLE, "You were muted there, so party voice is muted too.")
    }

    private fun unlink() {
        linked = false
        paired = emptySet()
        blockMicrophone = false
        blockSound = false
        talkOpen = false
        if (announced) {
            announced = false
            Notifications.info("PolyPlus", "You're back on party voice.")
        }
    }

    private fun choice(key: String): Boolean? = when (PolyPlusConfig.voiceSvcHandoff) {
        SvcHandoff.ALWAYS -> true
        SvcHandoff.NEVER -> false
        SvcHandoff.ASK -> answers[key]
    }

    private fun isOnSvc(member: PartyMember, key: String, link: Link): Boolean {
        val state = member.state ?: return false
        if (!member.online || !state.voice.inVoice || state.location.key != key) return false
        return parse(member.player)?.let(link::connected) == true
    }

    private fun inReach(member: PartyMember, link: Link): Boolean {
        if (member.state?.svcConnected != true) return false
        val id = parse(member.player) ?: return false
        val held = member.player in paired
        if (!held && !link.listening(id)) return false
        val range = link.range(id)
        if (range == Double.POSITIVE_INFINITY) return true
        val distance = PartyProximity.distanceTo(member) ?: return false
        return distance <= range * (if (held) LEAVE_RANGE else ENTER_RANGE)
    }

    private fun parse(player: String): UUID? = runCatching { UUID.fromString(player) }.getOrNull()

    private fun message(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" ")

    private fun ask(key: String, members: List<PartyMember>, link: Link) {
        if (prompt != null && promptKey == key) return
        closePrompt()
        val names = members.take(MAX_NAMES).joinToString(" and ") { PlayerNamesRepository.nameOr(it.player) }
        val who = if (members.size > MAX_NAMES) "$names and others are" else if (members.size > 1) "$names are" else "$names is"
        promptKey = key
        prompt = Notifications.builder(
            TITLE,
            message(
                "$who on it here too. Switch to it while you're close together, or keep party voice? Party voice takes over when you move apart.",
                link.talkHint(),
                "Press Esc to choose.",
            ),
        )
            .persistent()
            .action(NotificationAction("Switch", primary = true, onClick = { answers[key] = true }))
            .action(NotificationAction("Keep party voice", onClick = { answers[key] = false }))
            .send()
    }

    private fun closePrompt() {
        prompt?.let(Notifications::dismiss)
        prompt = null
        promptKey = null
    }
}
