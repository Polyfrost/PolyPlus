package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.network.http.responses.PartyInvite
import org.polyfrost.polyplus.client.party.PartyRepository
import java.util.UUID

internal data class SocialPerson(
    val id: String,
    val name: String,
    val seed: UUID,
    val realSkin: Boolean,
    val online: Boolean,
    val activity: String? = null,
    val worldInvited: Boolean = false,
    val since: String? = null,
    val presenceKnown: Boolean = true,
    val pendingInvite: Boolean = false,
) {
    val status: String
        get() = activity ?: if (online) "Online" else "Offline"
}

internal data class SocialRequest(val id: Int, val person: SocialPerson, val incoming: Boolean)

internal data class SocialThread(
    val id: Int,
    val title: String,
    val people: List<SocialPerson>,
    val group: Boolean,
    val preview: String?,
    val time: String?,
    val unread: Boolean,
    val special: Boolean,
    val responded: Boolean,
    val clock: String? = null,
    val brief: String? = preview,
    val party: Boolean = false,
)

internal data class SocialWorld(val id: Int, val host: SocialPerson, val name: String?, val accepted: Boolean, val build: String? = null)

internal data class SocialPartyInvite(val invite: PartyInvite, val host: SocialPerson)

internal data class SocialSnapshot(
    val friends: List<SocialPerson>,
    val requests: List<SocialRequest>,
    val blocked: List<SocialPerson>,
    val threads: List<SocialThread>,
    val worlds: List<SocialWorld>,
    val hosting: String?,
    val actAs: String?,
    val delegations: List<SocialPerson>,
    val specialTarget: Boolean,
    val scanningReplies: Boolean,
    val partyInvites: List<SocialPartyInvite> = emptyList(),
) {
    val waiting: Int
        get() = requests.count { it.incoming } + worlds.count { !it.accepted } + partyInvites.size

    fun find(id: String?): SocialPerson? = friends.firstOrNull { it.id == id }

    fun isFriend(id: String): Boolean = friends.any { PartyRepository.samePlayer(it.id, id) }

    fun isBlocked(id: String): Boolean = blocked.any { PartyRepository.samePlayer(it.id, id) }

    fun requestWith(id: String): SocialRequest? = requests.firstOrNull { PartyRepository.samePlayer(it.person.id, id) }
}

internal sealed interface SocialDetail {
    class Conversation(val chat: ChatConversation, val thread: SocialThread) : SocialDetail

    class Friend(val person: SocialPerson) : SocialDetail
}

internal sealed interface SocialDialog {
    object AddFriend : SocialDialog

    object NewGroup : SocialDialog

    class InviteToGroup(val groupId: Int) : SocialDialog
}

internal interface SocialSource {
    @Composable
    fun snapshot(): SocialSnapshot

    @Composable
    fun Dialog(dialog: SocialDialog, onDismiss: () -> Unit)

    fun refresh()

    fun accept(request: SocialRequest)

    fun dismiss(request: SocialRequest)

    fun open(thread: SocialThread)

    fun message(person: SocialPerson)

    fun join(world: SocialWorld)

    fun decline(world: SocialWorld)

    fun inviteToWorld(person: SocialPerson)

    fun remove(person: SocialPerson)

    fun addFriend(person: SocialPerson)

    fun hide(thread: SocialThread)

    fun block(person: SocialPerson)

    fun unblock(person: SocialPerson)

    fun openDirect(player: String)

    fun chatAs(owner: String?)

    fun scanReplies()
}

internal class SocialPageState(val source: SocialSource) {
    var selected by mutableStateOf<String?>(null)
    var dialog: SocialDialog?
        get() = openDialog
        set(value) {
            openDialog = value
        }
    var query by mutableStateOf("")

    fun toggle(person: SocialPerson) {
        selected = person.id.takeUnless { it == selected }
    }

    fun matches(person: SocialPerson): Boolean = query.isBlank() || person.name.contains(query.trim(), ignoreCase = true)

    fun conversation(snapshot: SocialSnapshot): SocialDetail.Conversation? {
        val chat = SocialChatLink.active ?: return null
        val thread = snapshot.threads.firstOrNull { it.id == chat.id } ?: return null
        return SocialDetail.Conversation(chat, thread)
    }

    fun friend(snapshot: SocialSnapshot): SocialDetail.Friend? = snapshot.find(selected)?.let(SocialDetail::Friend)

    fun detail(snapshot: SocialSnapshot): SocialDetail? = friend(snapshot) ?: conversation(snapshot)

    companion object {
        private var openDialog by mutableStateOf<SocialDialog?>(null)

        val dialogOpen: Boolean
            get() = openDialog != null

        fun closeDialog() {
            openDialog = null
        }
    }
}
