package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen
import net.minecraft.sounds.SoundSource
import org.polyfrost.polyplus.mixin.client.access.SettingsGeneralChatAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsGeneralMouseAccessor
//?} elif = 1.8.9 {
/*import net.minecraft.client.options.GameOptions
*///?}
//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
//?}
//? if >= 26.2 {
import net.minecraft.client.gui.screens.PrivacyConfirmLinkScreen
import net.minecraft.client.gui.screens.friends.FriendsListConfirmScreen
import net.minecraft.util.CommonLinks
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.SettingsGeneralOnlineAccessor
//?}

internal class GeneralAction(
    val key: String,
    val label: String,
    val hint: String?,
    val enabled: Boolean,
    val run: () -> Unit,
)

internal class GeneralSection(val section: SettingSection, val actions: List<GeneralAction> = emptyList())

//? if > 1.8.9 {
internal object GeneralOptions {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val options: Options get() = mc.options

    private val supported = setOf(SettingsCategory.Sound, SettingsCategory.Mouse, SettingsCategory.Chat, SettingsCategory.Online)

    fun supports(category: SettingsCategory): Boolean = category in supported

    fun save() = options.save()

    fun sections(category: SettingsCategory, parent: () -> Screen?): List<GeneralSection> = when (category) {
        SettingsCategory.Sound -> sound()
        SettingsCategory.Mouse -> mouse()
        SettingsCategory.Chat -> chat()
        SettingsCategory.Online -> online(parent)
        else -> emptyList()
    }

    private fun sound(): List<GeneralSection> {
        val o = options
        val volumes = listOfNotNull(row(o.getSoundSourceOptionInstance(SoundSource.MASTER), wide = true)) +
            SoundSource.entries.filter { it != SoundSource.MASTER }.mapNotNull { row(o.getSoundSourceOptionInstance(it)) }
        return listOf(
            GeneralSection(SettingSection("Volume", volumes)),
            GeneralSection(SettingSection("Output", listOfNotNull(row(o.soundDevice()), row(o.directionalAudio()), row(o.showSubtitles())))),
            //? if >= 1.21.11 {
            GeneralSection(SettingSection("Music", listOfNotNull(row(o.musicFrequency()), row(o.musicToast())))),
            //?} elif >= 1.21.8 {
            /*GeneralSection(SettingSection("Music", listOfNotNull(row(o.musicFrequency()), row(o.showNowPlayingToast())))),
            *///?}
        )
    }

    private fun mouse(): List<GeneralSection> {
        val o = options
        return grouped(
            SettingsGeneralMouseAccessor.`polyplus$options`(o),
            //? if >= 1.21.10 {
            listOf(
                "Movement" to setOf(o.sensitivity(), o.invertMouseX(), o.invertMouseY()),
                "Scrolling" to setOf(o.mouseWheelSensitivity(), o.discreteMouseScroll()),
                "Cursor" to setOf(o.allowCursorChanges()),
            ),
            //?} else {
            /*listOf(
                "Movement" to setOf(o.sensitivity(), o.invertYMouse()),
                "Scrolling" to setOf(o.mouseWheelSensitivity(), o.discreteMouseScroll()),
                "Other" to emptySet(),
            ),
            *///?}
        )
    }

    private fun chat(): List<GeneralSection> {
        val o = options
        return grouped(
            SettingsGeneralChatAccessor.`polyplus$options`(o),
            listOf(
                "Messages" to setOf(o.chatVisibility(), o.chatColors(), o.chatLinks(), o.chatLinksPrompt(), o.chatDelay(), o.hideMatchedNames(), o.onlyShowSecureChat()),
                "Appearance" to setOf(o.chatOpacity(), o.textBackgroundOpacity(), o.chatScale(), o.chatLineSpacing(), o.chatWidth(), o.chatHeightFocused(), o.chatHeightUnfocused()),
                //? if >= 1.21.10 {
                "Other" to setOf(o.narrator(), o.autoSuggestions(), o.saveChatDrafts(), o.reducedDebugInfo()),
                //?} else {
                /*"Other" to setOf(o.narrator(), o.autoSuggestions(), o.reducedDebugInfo()),
                *///?}
            ),
        )
    }

    private fun grouped(all: Array<OptionInstance<*>>, groups: List<Pair<String, Set<OptionInstance<*>>>>): List<GeneralSection> {
        val claimed = groups.flatMap { it.second }.toSet()
        val extra = all.filter { it !in claimed }
        return groups.mapIndexed { index, (title, members) ->
            val instances = all.filter { it in members } + if (index == groups.lastIndex) extra else emptyList()
            GeneralSection(SettingSection(title, instances.mapNotNull { row(it) }))
        }
    }

    //? if >= 26.2 {
    private fun online(parent: () -> Screen?): List<GeneralSection> {
        val o = options
        val social = mc.playerSocialManager
        val friendsOn = social.isFriendListEnabled
        val friendsAvailable = !mc.isDemo && !mc.isOfflineDeveloperMode
        val friends = listOfNotNull(
            SettingRow(FRIENDS_KEY, text("options.friendsList"), null, SettingKind.Toggle(friendsOn) { toggleFriends(it, parent) }, enabled = friendsAvailable),
            SettingRow(
                FRIEND_REQUESTS_KEY,
                text("options.allowFriendRequests"),
                text("options.allowFriendRequests.tooltip"),
                SettingKind.Toggle(social.isAllowFriendRequests) { applyFriends(social.isFriendListEnabled, it) {} },
                enabled = friendsOn,
            ),
            row(o.inGameNotification(), enabled = friendsOn),
            row(o.sharePresence(), enabled = friendsOn),
        )
        val xbox = GeneralAction(XBOX_KEY, text("options.online.xboxSettings"), null, true) { openPrivacyLink(parent) }
        val telemetryAllowed = mc.allowsTelemetry()
        val telemetry = GeneralAction(
            TELEMETRY_KEY,
            text("options.telemetry"),
            if (telemetryAllowed) null else text("options.telemetry.disabled"),
            telemetryAllowed,
        ) { parent()?.let { SettingsRoutes.pass(TelemetryInfoScreen(it, options)) } }
        return listOf(
            GeneralSection(SettingSection(text("options.online.friends.header"), friends), listOf(xbox)),
            GeneralSection(SettingSection(text("options.online.servers.header"), listOfNotNull(row(o.allowServerListing())))),
            GeneralSection(SettingSection(text("options.online.realms.header"), listOfNotNull(row(o.realmsNotifications())))),
            GeneralSection(SettingSection("Privacy", emptyList()), listOf(telemetry)),
        )
    }

    private fun toggleFriends(on: Boolean, parent: () -> Screen?) {
        val social = mc.playerSocialManager
        if (on) {
            val host = parent() ?: return
            SettingsRoutes.pass(
                FriendsListConfirmScreen(
                    { accepted ->
                        ClientPlatform.setScreen(host)
                        if (accepted && !mc.isOfflineDeveloperMode) {
                            social.setFriendListEnabled(true)
                            social.setAllowFriendRequests(true)
                            applyFriends(true, true) {}
                        }
                    },
                    SettingsGeneralOnlineAccessor.`polyplus$confirmTitle`(),
                    SettingsGeneralOnlineAccessor.`polyplus$confirmMessage`(),
                    SettingsGeneralOnlineAccessor.`polyplus$confirmTurnOn`(),
                    SettingsGeneralOnlineAccessor.`polyplus$confirmTurnOff`(),
                ),
            )
            return
        }
        val wasEnabled = social.isFriendListEnabled
        val wasAllowing = social.isAllowFriendRequests
        social.setFriendListEnabled(false)
        social.setAllowFriendRequests(false)
        options.inGameNotification().set(false)
        options.save()
        applyFriends(false, false) { success ->
            if (!success) {
                social.setFriendListEnabled(wasEnabled)
                social.setAllowFriendRequests(wasAllowing)
            }
        }
    }

    private fun applyFriends(enabled: Boolean, allowRequests: Boolean, result: (Boolean) -> Unit) {
        SettingsGeneralOnlineAccessor.`polyplus$applyFriendSettings`(mc, enabled, allowRequests) { result(it) }
    }

    private fun openPrivacyLink(parent: () -> Screen?) {
        val host = parent() ?: return
        val uri = CommonLinks.PRIVACY_AND_ONLINE_SETTINGS
        SettingsRoutes.pass(
            PrivacyConfirmLinkScreen(
                { open ->
                    //? if >= 26.3 {
                    if (open) Blaze3D.openUri(uri)
                    //?} else {
                    /*if (open) ClientPlatform.openUri(uri.toString())
                    *///?}
                    ClientPlatform.setScreen(host)
                },
                //? if >= 26.3 {
                uri,
                //?} else {
                /*uri.toString(),
                *///?}
            ),
        )
    }
    //?} else {
    /*private fun online(parent: () -> Screen?): List<GeneralSection> {
        val o = options
        val telemetryAllowed = mc.allowsTelemetry()
        val telemetry = GeneralAction(
            TELEMETRY_KEY,
            text("options.telemetry"),
            if (telemetryAllowed) null else text("options.telemetry.disabled"),
            telemetryAllowed,
        ) { parent()?.let { SettingsRoutes.pass(TelemetryInfoScreen(it, options)) } }
        return listOf(
            GeneralSection(SettingSection("Servers", listOfNotNull(row(o.allowServerListing())))),
            GeneralSection(SettingSection("Realms", listOfNotNull(row(o.realmsNotifications())))),
            GeneralSection(SettingSection("Privacy", emptyList()), listOf(telemetry)),
        )
    }
    *///?}

    private fun text(key: String): String = SettingsLabels.text(key)

    private fun row(instance: OptionInstance<*>, wide: Boolean = false, enabled: Boolean = true): SettingRow? {
        val active = enabled && (instance !== options.narrator() || mc.narrator.isActive)
        return SettingsOptionRows.row(instance, wide, active)
    }

    //? if >= 26.2 {
    private const val FRIENDS_KEY = "options.friendsList"
    private const val FRIEND_REQUESTS_KEY = "options.allowFriendRequests"
    private const val XBOX_KEY = "options.online.xboxSettings"
    //?}
    private const val TELEMETRY_KEY = "options.telemetry"
}
//?} elif = 1.8.9 {
/*internal object GeneralOptions {
    private val supported = setOf(SettingsCategory.Sound, SettingsCategory.Mouse, SettingsCategory.Chat, SettingsCategory.Online)

    fun supports(category: SettingsCategory): Boolean = category in supported

    fun save() = SettingsLegacyOptions.save()

    fun sections(category: SettingsCategory, parent: () -> Screen?): List<GeneralSection> = when (category) {
        SettingsCategory.Sound -> listOf(section("Volume", SettingsLegacyOptions.soundRows()))
        SettingsCategory.Mouse -> listOf(section("Movement", SettingsLegacyOptions.rows(GameOptions.Option.SENSITIVITY, GameOptions.Option.INVERT_MOUSE)))
        SettingsCategory.Chat -> listOf(
            section(
                "Messages",
                SettingsLegacyOptions.rows(
                    GameOptions.Option.CHAT_VISIBILITY,
                    GameOptions.Option.CHAT_COLOR,
                    GameOptions.Option.CHAT_LINKS,
                    GameOptions.Option.CHAT_LINKS_PROMPT,
                ),
            ),
            section(
                "Appearance",
                SettingsLegacyOptions.rows(
                    GameOptions.Option.CHAT_OPACITY,
                    GameOptions.Option.CHAT_SCALE,
                    GameOptions.Option.CHAT_WIDTH,
                    GameOptions.Option.CHAT_HEIGHT_FOCUSED,
                    GameOptions.Option.CHAT_HEIGHT_UNFOCUSED,
                ),
            ),
            section("Other", SettingsLegacyOptions.rows(GameOptions.Option.REDUCED_DEBUG_INFO)),
        )
        SettingsCategory.Online -> listOf(section("Realms", SettingsLegacyOptions.rows(GameOptions.Option.REALMS_NOTIFICATIONS)))
        else -> emptyList()
    }

    private fun section(title: String, rows: List<SettingRow>): GeneralSection = GeneralSection(SettingSection(title, rows))
}
*///?}
