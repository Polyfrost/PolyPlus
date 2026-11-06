@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialSwitch
import org.polyfrost.polyplus.client.gui.SocialOffStateBackground
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.serverapi.ServerApiProtocol
import java.util.Locale

private const val SUNSET_START = 11500L
private const val NIGHT_START = 13500L
private const val NIGHT_END = 22500L
private const val KILOBYTE = 1024L

@Composable
internal fun BoxScope.SceneControl(lobby: LobbyShell) {
    if (lobby.page != LobbyPage.Home) return
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = LobbyLayout.NavbarHeight + 8.dp, end = LobbyLayout.Margin)
            .width(LobbyLayout.UnitWidth),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier) {
            SocialButton("Change scene", SOCIAL_ASSETS + "image-01.svg", onClick = { open = !open })
        }
        if (open) {
            EscapeCloses { open = false }
            ScenePicker(lobby) { open = false }
        }
    }
}

private val PickerHeight = 320.dp

@Composable
private fun ScenePicker(lobby: LobbyShell, onClose: () -> Unit) {
    val party by PartyRepository.party.collectAsState()
    val member = PartyScene.isMember(party)
    val keepOwn = PartyScene.keepOwnBackdrop
    val following = member && !keepOwn
    Column(Modifier.fillMaxWidth().panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Lobby scene", 16.sp, Modifier.weight(1f), weight = FontWeight.Medium)
            SocialIconButton(SOCIAL_ASSETS + "x-close.svg", onClick = onClose)
        }
        if (member) {
            if (following) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("Following your party leader", 14.sp, weight = FontWeight.Medium)
                    Label(
                        PartyScene.followedLabel()?.let { "$it. Only the leader chooses it." } ?: "Waiting for the leader's scene.",
                        12.sp,
                        color = SocialTextSecondary,
                    )
                }
            }
            SwitchRow("Keep my own backdrop", "Use your own scene and time in parties.", keepOwn, true) {
                PartyScene.setKeepOwnBackdrop(!keepOwn)
            }
        }
        val scroll = rememberScrollState()
        Column(
            Modifier.heightIn(max = PickerHeight).fadingEdges(scroll, OneConfigTheme.current.pageBackground, 32.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SceneRow("Automatic", "Best recent scene", !following && LobbySceneStore.pinned == null, { pick(null) }, enabled = !following)
            Heading("Your captures")
            key(LobbyPacks.revision) {
                val captures = LobbySceneStore.usableScenes
                val locked = LobbySceneStore.lockedScenes
                if (captures.isEmpty() && locked.isEmpty()) Note("No captures yet. Good spots appear here after you play your worlds.")
                captures.forEach { CaptureRow(it, following = following) }
                locked.forEach { (entry, reason) -> CaptureRow(entry, following, reason) }
            }
            Heading("Polyfrost scenes")
            LobbySceneStore.bundledScenes.forEach { entry ->
                SceneRow(entry.label, describe(entry), !following && LobbySceneStore.pinned == entry.id, { pick(entry.id) }, enabled = !following)
            }
            Heading("Capture")
            CaptureSwitchRow()
            ServerRows(lobby)
        }
    }
}

@Composable
private fun CaptureRow(entry: LobbySceneEntry, following: Boolean, locked: String? = null) {
    SceneRow(entry.label, locked ?: describe(entry), !following && LobbySceneStore.pinned == entry.id, { pick(entry.id) }, enabled = locked == null && !following) {
        SocialIconButton(SOCIAL_ASSETS + "trash-01.svg", tooltip = "Delete capture") {
            val showing = LobbyScene.currentId == entry.id
            LobbySceneStore.delete(entry)
            if (showing) LobbyScene.reload()
        }
    }
}

@Composable
private fun SceneRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val (interaction, hovered) = rememberSocialHover()
    val shape = ppShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .card(8.dp, selected)
            .border(1.dp, if (hovered && !selected && enabled) SocialHoverBorder else Color.Transparent, shape)
            .hoverable(interaction, enabled)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).graphicsLayer { alpha = if (enabled) 1f else 0.5f }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, 14.sp, weight = FontWeight.Medium)
            Label(detail, 12.sp, color = SocialTextSecondary)
        }
        trailing()
    }
}

@Composable
private fun CaptureSwitchRow() {
    SwitchRow("Capture scenes", "Saves good spots from your own worlds.", LobbySceneStore.captureEnabled, true) {
        LobbySceneStore.captureEnabled = !LobbySceneStore.captureEnabled
    }
}

@Composable
private fun ServerRows(lobby: LobbyShell) {
    val servers = lobby.servers.saved
    if (servers.isEmpty()) {
        Note("Saved servers appear here. Capture stays on unless you or the server turn it off.")
        return
    }
    servers.forEach { entry ->
        val address = entry.data.ip
        val policy = LobbySceneStore.serverPolicy(address)
        val blocked = policy == ServerApiProtocol.ScenePolicy.DENY
        SwitchRow(entry.data.name, policyText(policy, LobbySceneStore.serverChoice(address)), LobbySceneStore.serverAllows(address, policy), !blocked) {
            LobbySceneStore.setServerChoice(address, !LobbySceneStore.serverAllows(address, policy))
        }
    }
}

private fun policyText(policy: ServerApiProtocol.ScenePolicy, choice: Boolean?): String = when {
    policy == ServerApiProtocol.ScenePolicy.DENY -> "Blocked by this server"
    choice == false -> "Off for this server"
    choice == true -> "On for this server"
    policy == ServerApiProtocol.ScenePolicy.ALLOW -> "Allowed by this server"
    else -> "On unless you turn it off"
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .rowFrame(false, hovered && enabled)
            .hoverable(interaction, enabled)
            .then(if (enabled) Modifier.clickableWithSound(onToggle) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, 14.sp)
            Label(detail, 12.sp, color = SocialTextSecondary)
        }
        Label(if (checked) "On" else "Off", 12.sp, color = SocialTextSecondary)
        SocialSwitch(checked, 42.dp, 21.dp, SocialOffStateBackground)
    }
}

@Composable
private fun Heading(text: String) {
    Column {
        Spacer(Modifier.height(6.dp))
        Label(text, 12.sp, color = SocialTextPrimary, weight = FontWeight.Medium)
    }
}

@Composable
private fun Note(text: String) {
    SocialText(text, 12.sp, color = SocialTextSecondary)
}

private fun pick(id: String?) {
    if (LobbySceneStore.pinned == id) return
    LobbySceneStore.pinned = id
    if (id == null || LobbyScene.currentId != id) LobbyScene.reload()
}

private fun describe(entry: LobbySceneEntry): String {
    val biome = entry.biome.substringAfter(':').replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    val time = when (entry.clock % 24000L) {
        in SUNSET_START until NIGHT_START -> "Sunset"
        in NIGHT_START until NIGHT_END -> "Night"
        else -> "Day"
    }
    return if (entry.bytes > 0L) "$biome, $time, ${entry.bytes / KILOBYTE} KB" else "$biome, $time"
}
