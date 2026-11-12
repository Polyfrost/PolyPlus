package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.social.SpecialChatResponses

internal enum class FriendFilter { All, Online, Requests, Blocked }

internal enum class ThreadFilter { All, Direct, Groups, Special }

private fun nextReplyFilter(filter: SpecialChatResponses.Filter) = when (filter) {
    SpecialChatResponses.Filter.All -> SpecialChatResponses.Filter.Awaiting
    SpecialChatResponses.Filter.Awaiting -> SpecialChatResponses.Filter.Responded
    SpecialChatResponses.Filter.Responded -> SpecialChatResponses.Filter.All
}

internal class SocialListState(private val hideParty: Boolean = false) {
    var chosen by mutableStateOf(ThreadFilter.All)
    var searching by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var unreadOnly by mutableStateOf(false)
        private set
    var replies by mutableStateOf(SpecialChatResponses.Filter.All)
        private set

    private val narrowed: Boolean
        get() = query.isNotBlank() || unreadOnly || replies != SpecialChatResponses.Filter.All

    fun toggleSearch() {
        searching = !searching
        if (!searching) query = ""
    }

    fun toggleUnread() {
        unreadOnly = !unreadOnly
    }

    fun cycleReplies() {
        replies = nextReplyFilter(replies)
    }

    fun filter(snapshot: SocialSnapshot): ThreadFilter =
        chosen.takeUnless { it == ThreadFilter.Special && !snapshot.specialTarget } ?: ThreadFilter.All

    fun tabs(snapshot: SocialSnapshot): List<ThreadFilter> =
        ThreadFilter.entries.filter { it != ThreadFilter.Special || snapshot.specialTarget }

    fun badged(tab: ThreadFilter, snapshot: SocialSnapshot): Boolean =
        tab == ThreadFilter.Special && snapshot.threads.any { it.special && it.unread }

    fun unread(snapshot: SocialSnapshot): Int = snapshot.threads.count { it.unread && !(hideParty && it.party) }

    fun matching(snapshot: SocialSnapshot, activeId: Int? = null, filtered: Boolean = true): List<SocialThread> =
        (if (query.isBlank()) snapshot.threads else snapshot.allThreads).filter {
            val inReplies = !filtered || !it.special || replies == SpecialChatResponses.Filter.All || it.responded == (replies == SpecialChatResponses.Filter.Responded)
            val inUnread = !filtered || !unreadOnly || it.unread || it.id == activeId
            !(hideParty && it.party) && inReplies && inUnread && it.title.contains(query.trim(), ignoreCase = true)
        }

    fun threads(snapshot: SocialSnapshot, activeId: Int?): List<SocialThread> {
        val filter = filter(snapshot)
        return (if (query.isBlank()) snapshot.threads else snapshot.allThreads).filter {
            if (hideParty && it.party) return@filter false
            val apart = snapshot.specialTarget && it.special
            val inTab = when (filter) {
                ThreadFilter.All -> !apart
                ThreadFilter.Direct -> !it.group && !apart
                ThreadFilter.Groups -> it.group && !apart
                ThreadFilter.Special -> it.special
            }
            val inReplies = !it.special || replies == SpecialChatResponses.Filter.All || it.responded == (replies == SpecialChatResponses.Filter.Responded)
            inTab && inReplies && (!unreadOnly || it.unread || it.id == activeId) && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true))
        }
    }

    fun emptyTitle(snapshot: SocialSnapshot): String {
        val filter = filter(snapshot)
        return when {
            narrowed -> "No conversations match your filters"
            filter == ThreadFilter.All -> "No conversations yet"
            filter == ThreadFilter.Direct -> "No direct messages yet"
            filter == ThreadFilter.Groups -> "No group chats yet"
            else -> "No Special Chat messages yet"
        }
    }
}

@Composable
internal fun rememberSocialListState(source: SocialSource, snapshot: SocialSnapshot, hideParty: Boolean = false): SocialListState {
    val list = remember { SocialListState(hideParty) }
    LaunchedEffect(list.replies, snapshot.threads) {
        if (list.replies != SpecialChatResponses.Filter.All) source.scanReplies()
    }
    return list
}

internal fun visibleFriends(state: SocialPageState, snapshot: SocialSnapshot, filter: FriendFilter): List<SocialPerson> =
    snapshot.friends.filter { state.matches(it) && (filter != FriendFilter.Online || it.online) }

internal fun rosterSections(people: List<SocialPerson>): List<Pair<String, List<SocialPerson>>> = listOf(
    "Playing" to people.filter { it.online && it.activity != null },
    "Online" to people.filter { it.online && it.activity == null },
    "Offline" to people.filter { !it.online },
).filter { (_, members) -> members.isNotEmpty() }

@Composable
internal fun FollowSocialChanges(state: SocialPageState, snapshot: SocialSnapshot) {
    val friendIds = remember(snapshot.friends) { snapshot.friends.mapTo(HashSet()) { it.id } }
    var knownFriends by remember { mutableStateOf<Set<String>?>(null) }
    var requestPeople by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(snapshot.requests) {
        requestPeople = requestPeople + snapshot.requests.map { it.person.id }
    }
    LaunchedEffect(friendIds) {
        knownFriends?.let { known -> (friendIds - known).firstOrNull { it in requestPeople }?.let(state.source::openDirect) }
        knownFriends = friendIds
    }
    var identity by remember { mutableStateOf(snapshot.actAs) }
    LaunchedEffect(snapshot.actAs) {
        if (identity != snapshot.actAs) {
            identity = snapshot.actAs
            state.selected = null
            SocialChatLink.close()
        }
    }
}
