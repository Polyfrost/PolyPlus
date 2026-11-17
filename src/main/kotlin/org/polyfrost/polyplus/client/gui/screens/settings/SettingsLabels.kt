package org.polyfrost.polyplus.client.gui.screens.settings

//? if >= 26.3 {
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
//?} elif = 1.8.9 {
/*import net.minecraft.client.resource.language.I18n
*///?}

internal object SettingsLabels {
    private val english = mapOf(
        "options.notifications.display_time" to "Minecraft toast duration",
        "options.narrator" to "Minecraft narrator",
        "options.accessibility.narrator_hotkey" to "Narrator toggle shortcut",
        "soundCategory.voice" to "Minecraft narrator and voices",
        "options.telemetry" to "Minecraft telemetry data",
        "options.realmsNotifications" to "Realms news and invites",
        "options.allowServerListing" to "Show my name in server player lists",
        "options.online.friends.header" to "Minecraft friends",
        "options.friendsList" to "Minecraft friends list",
        "options.allowFriendRequests" to "Allow Minecraft friend requests",
        "options.inGameNotification" to "Minecraft friend alerts in game",
        "options.sharePresence" to "Activity shown to Minecraft friends",
        "options.online.xboxSettings" to "Xbox privacy settings",
        "options.chat.visibility" to "Chat visibility",
        "options.chat.color" to "Chat colors",
        "options.chat.links" to "Clickable web links",
        "options.chat.links.prompt" to "Ask before opening links",
        "options.chat.delay_instant" to "Chat message delay",
        "options.chat.drafts" to "Keep unsent chat messages",
        "options.chat.width" to "Chat width",
        "options.chat.height.focused" to "Chat height when open",
        "options.chat.height.unfocused" to "Chat height when closed",
        "options.chat.line_spacing" to "Chat line spacing",
        "options.musicToast" to "Now playing toast",
        "options.showNowPlayingToast" to "Show now playing toast",
        "options.showSubtitles" to "Sound subtitles",
        "options.accessibility.menu_background_blurriness" to "Minecraft menu background blur",
        "options.accessibility.panorama_speed" to "Title screen panorama speed",
        "options.hideSplashTexts" to "Hide title screen splash text",
        "options.darkMojangStudiosBackgroundColor" to "Black Mojang loading screen",
        "options.glintSpeed" to "Enchantment glint speed",
        "options.glintStrength" to "Enchantment glint strength",
    )

    fun label(key: String, fallback: String): String = if (isEnglish()) english[key] ?: fallback else fallback

    //? if >= 26.3 {
    fun text(key: String): String = label(key, Component.translatable(key).string)

    private fun isEnglish(): Boolean = runCatching { Minecraft.getInstance().languageManager.selected }.getOrNull() in ENGLISH
    //?} elif = 1.8.9 {
    /*fun text(key: String): String = label(key, I18n.translate(key))

    private fun isEnglish(): Boolean = SettingsLegacyOptions.isEnglish()
    *///?} else {
    /*fun text(key: String): String = key

    private fun isEnglish(): Boolean = false
    *///?}

    private val ENGLISH = setOf("en_us", "en_gb")
}
