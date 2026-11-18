package org.polyfrost.polyplus.client.gui.screens.settings

import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS

internal enum class SettingsCategory(val english: String, val icon: String) {
    Video("Video", "hud"),
    Sound("Sound", SOCIAL_ASSETS + "volume-max.svg"),
    Controls("Controls", "keyboard"),
    Mouse("Mouse", "move"),
    Skin("Skin", SOCIAL_ASSETS + "user-01.svg"),
    Accessibility("Accessibility", "eye"),
    Language("Language", SOCIAL_ASSETS + "globe-01.svg"),
    Chat("Chat", SOCIAL_ASSETS + "message-chat-circle.svg"),
    Online("Online", "cloud"),
    Packs("Resource packs", SOCIAL_ASSETS + "package-01.svg"),
    Mods("More from mods", "spanner");

    val label: String get() = SettingsLabels.category(this)
}

internal val SettingsCategory.entryId: String get() = "minecraft:" + name.lowercase()

internal sealed interface SettingKind {
    class Toggle(val on: Boolean, val set: (Boolean) -> Unit) : SettingKind

    class Choice(val options: List<String>, val selected: Int, val select: (Int) -> Unit) : SettingKind

    class Range(
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val step: Float,
        val live: Boolean,
        val text: (Float) -> String,
        val set: (Float) -> Unit,
    ) : SettingKind
}

internal class SettingRow(
    val key: String,
    val label: String,
    val hint: String?,
    val kind: SettingKind,
    val enabled: Boolean = true,
    val wide: Boolean = false,
    val reset: SettingReset? = null,
)

internal class SettingReset(val atDefault: Boolean, val apply: () -> Unit)

internal class SettingSection(val title: String, val rows: List<SettingRow>)
