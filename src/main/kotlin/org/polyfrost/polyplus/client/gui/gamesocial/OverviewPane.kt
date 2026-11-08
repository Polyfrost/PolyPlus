@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.polyplus.client.gui.lobbysocial.friendRowItems
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyActions
import org.polyfrost.polyplus.client.gui.lobbyshell.PartySeats
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxItem
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SectionHeading
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialog
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPerson
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.socialRow
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.party.PartyRepository

private const val SEAT_COLUMNS = 4
private const val FRIEND_COLUMNS = 2
private const val MAX_FRIENDS = 8

private val SectionGap = 12.dp

@Composable
internal fun OverviewPane(host: SocialHost, state: SocialPageState, snapshot: SocialSnapshot, invites: List<InboxItem>, onVoice: () -> Unit, modifier: Modifier) {
    val current by PartyRepository.party.collectAsState()
    val party = current
    val online = snapshot.friends.filter { friend -> friend.online && party?.member(friend.id) == null }
    if (invites.isEmpty() && party == null && online.isEmpty() && snapshot.hosting == null) {
        QuietOverview(state, snapshot, modifier)
        return
    }
    ScrollFade(modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), 28.dp) {
        if (invites.isNotEmpty()) InvitesSection(invites)
        if (party != null) PartySection(host, party, onVoice)
        if (online.isNotEmpty()) FriendsSection(state, online)
        snapshot.hosting?.let { HostingSection(it) }
        Spacer(Modifier.height(SectionGap))
    }
}

@Composable
private fun QuietOverview(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelTitle(if (snapshot.friends.isEmpty()) "Add a friend to see them here" else "None of your friends are online")
            AddFriendButton(state)
        }
    }
}

@Composable
private fun AddFriendButton(state: SocialPageState) {
    TextButton("Add friend", themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"), Modifier.height(38.dp), ButtonStyle.Primary, true) { state.dialog = SocialDialog.AddFriend }
}

@Composable
private fun InvitesSection(invites: List<InboxItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        SectionHeading("Invites ${invites.size}")
        Column(Modifier.fillMaxWidth().card()) {
            invites.forEachIndexed { index, item -> InviteRow(item, index > 0, roomy = true) }
        }
    }
}

@Composable
private fun PartySection(host: SocialHost, party: Party, onVoice: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("Party ${party.members.size} of ${PartyRepository.MAX_MEMBERS}", Modifier.weight(1f))
            PartyActions(party, onVoice)
        }
        PartySeats(host, party, SEAT_COLUMNS)
    }
}

@Composable
private fun FriendsSection(state: SocialPageState, online: List<SocialPerson>) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        SectionHeading("Online ${online.size}")
        online.take(MAX_FRIENDS).chunked(FRIEND_COLUMNS).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                pair.forEach { FriendCell(state, it, Modifier.weight(1f)) }
                if (pair.size < FRIEND_COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FriendCell(state: SocialPageState, person: SocialPerson, modifier: Modifier) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        modifier
            .height(52.dp)
            .socialRow(false, hovered)
            .contextMenu { friendRowItems(state, person) }
            .hoverable(interaction)
            .clickableWithSound { state.toggle(person) }
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonHead(person, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(person.name, 14.sp, weight = FontWeight.Medium)
            Label(person.status, 12.sp, color = MutedText)
        }
        IconButton(themedIcon(SOCIAL_ASSETS + "message-chat-circle.svg"), "Message", Modifier) { state.source.message(person) }
    }
}

@Composable
private fun HostingSection(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        SectionHeading("Hosting")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemedIcon(SOCIAL_ASSETS + "globe-01.svg", SocialTextPrimary, 18.dp)
            Label(name, 14.sp, Modifier.weight(1f), weight = FontWeight.Medium)
            TextButton("Stop hosting", Modifier.height(38.dp), SocialHosting::stop)
        }
    }
}
