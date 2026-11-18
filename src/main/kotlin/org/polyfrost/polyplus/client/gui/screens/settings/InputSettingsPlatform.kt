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
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonLinks
import net.minecraft.world.entity.player.PlayerModelPart
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.SettingsInputAccessibilityAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsInputControlsAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsInputFontAccessor
import java.util.Locale
//?} elif = 1.8.9 {
/*import net.minecraft.client.options.GameOptions
*///?}
//? if >= 1.21.4 {
import net.minecraft.world.flag.FeatureFlags
//?}

internal class LanguageEntry(val code: String, val label: String, val search: String)

//? if > 1.8.9 {
internal object InputSettingsPlatform {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val options: Options get() = mc.options

    const val available: Boolean = true

    fun text(key: String): String = Component.translatable(key).string

    fun save() = options.save()

    fun controlsSections(): List<SettingSection> =
        listOf(SettingSection(text("controls.title"), SettingsInputControlsAccessor.`polyplus$options`(options).mapNotNull { row(it) }))

    fun skinSections(): List<SettingSection> {
        val o = options
        val parts = PlayerModelPart.entries.map { part ->
            val enabled = o.isModelPartEnabled(part)
            val set = { on: Boolean ->
                //? if >= 1.21.4 {
                o.setModelPart(part, on)
                //?} else {
                /*o.toggleModelPart(part, on)
                *///?}
                o.save()
            }
            SettingRow("part-" + part.id, part.getName().string, null, SettingKind.Toggle(enabled, set), reset = SettingReset(enabled) { set(true) })
        }
        return listOf(
            SettingSection(text("options.skinCustomisation.title"), parts),
            SettingSection("Hand", listOfNotNull(row(o.mainHand()))),
        )
    }

    fun accessibilitySections(): List<SettingSection> {
        val o = options
        val all = SettingsInputAccessibilityAccessor.`polyplus$options`(o).toList()
        val groups = listOf(
            //? if >= 1.21.4 {
            "General" to listOf(o.narrator(), o.narratorHotkey(), o.showSubtitles(), o.highContrast(), o.highContrastBlockOutline()),
            //?} else {
            /*"General" to listOf(o.narrator(), o.narratorHotkey(), o.showSubtitles(), o.highContrast()),
            *///?}
            "Text and chat" to listOf(o.textBackgroundOpacity(), o.backgroundForChatOnly(), o.chatOpacity(), o.chatLineSpacing(), o.chatDelay(), o.notificationDisplayTime()),
            //? if >= 1.21.4 {
            "Effects" to listOf(
                o.bobView(), o.screenEffectScale(), o.fovEffectScale(), o.darknessEffectScale(), o.damageTiltStrength(),
                o.glintSpeed(), o.glintStrength(), o.hideLightningFlash(), o.rotateWithMinecart(),
            ),
            //?} else {
            /*"Effects" to listOf(
                o.bobView(), o.screenEffectScale(), o.fovEffectScale(), o.darknessEffectScale(), o.damageTiltStrength(),
                o.glintSpeed(), o.glintStrength(), o.hideLightningFlash(),
            ),
            *///?}
            "Menus" to listOf(o.menuBackgroundBlurriness(), o.darkMojangStudiosBackground(), o.panoramaSpeed(), o.hideSplashTexts()),
        )
        val grouped = groups.flatMap { it.second }.toSet()
        val rest = all.filter { it !in grouped }
        val sections = groups.map { (title, list) -> SettingSection(title, list.filter { it in all }.mapNotNull { row(it) }) }
        return sections + SettingSection("More", rest.mapNotNull { row(it) })
    }

    fun fontSection(): SettingSection =
        SettingSection(text("options.font.title"), SettingsInputFontAccessor.`polyplus$options`(options).mapNotNull { row(it) })

    fun languageWarning(): String = text("options.languageAccuracyWarning")

    fun currentLanguage(): String = mc.languageManager.selected

    fun languages(): List<LanguageEntry> = mc.languageManager.languages.map { (code, info) ->
        LanguageEntry(code, info.toComponent().string, (info.name() + " " + info.region()).lowercase(Locale.ROOT))
    }

    fun applyLanguage(code: String) {
        val manager = mc.languageManager
        if (code == manager.selected) return
        manager.selected = code
        options.languageCode = code
        options.save()
        mc.reloadResourcePacks()
    }

    fun openAccessibilityGuide(parent: Screen) {
        val uri = CommonLinks.ACCESSIBILITY_HELP
        SettingsRoutes.pass(ConfirmLinkScreen({ open ->
            if (open) ClientPlatform.openUri(uri.toString())
            SettingsRoutes.pass(parent)
        //? if >= 26.3 {
        }, uri, true))
        //?} else {
        /*}, uri.toString(), true))
        *///?}
    }

    fun accessibilityGuideLabel(): String = text("options.accessibility.link")

    private fun enabled(instance: OptionInstance<*>): Boolean = when {
        instance === options.narrator() -> mc.narrator.isActive
        instance === options.highContrast() -> mc.resourcePackRepository.availableIds.contains("high_contrast")
        //? if >= 1.21.4 {
        instance === options.rotateWithMinecart() -> mc.level?.enabledFeatures()?.contains(FeatureFlags.MINECART_IMPROVEMENTS) == true
        //?}
        else -> true
    }

    private fun disabledHint(instance: OptionInstance<*>): String? = when {
        instance === options.highContrast() && !enabled(instance) -> text("options.accessibility.high_contrast.error.tooltip")
        else -> null
    }

    private val holdOrToggle: List<OptionInstance<Boolean>>
        //? if >= 1.21.10 {
        get() = listOf(options.toggleCrouch(), options.toggleSprint(), options.toggleAttack(), options.toggleUse())
        //?} else {
        /*get() = listOf(options.toggleCrouch(), options.toggleSprint())
        *///?}

    private fun row(instance: OptionInstance<*>): SettingRow? {
        val base = SettingsOptionRows.row(instance, enabled = enabled(instance), hint = disabledHint(instance)) ?: return null
        val toggle = holdOrToggle.firstOrNull { it === instance } ?: return base
        val kind = SettingKind.Choice(listOf(text("options.key.hold"), text("options.key.toggle")), if (toggle.get()) 1 else 0) {
            toggle.set(it == 1)
            save()
        }
        return SettingRow(base.key, base.label, base.hint, kind, base.enabled, reset = base.reset)
    }
}
//?} elif = 1.8.9 {
/*internal object InputSettingsPlatform {
    const val available: Boolean = true

    fun text(key: String): String = SettingsLegacyOptions.text(key).removeSuffix("...")

    fun save() = SettingsLegacyOptions.save()

    fun controlsSections(): List<SettingSection> =
        listOf(SettingSection(text("controls.title"), SettingsLegacyOptions.rows(GameOptions.Option.TOUCHSCREEN)))

    fun skinSections(): List<SettingSection> =
        listOf(SettingSection(text("options.skinCustomisation.title"), SettingsLegacyOptions.skinRows()))

    fun accessibilitySections(): List<SettingSection> = emptyList()

    fun fontSection(): SettingSection = SettingSection("Font", SettingsLegacyOptions.rows(GameOptions.Option.FORCE_UNICODE_FONT))

    fun languageWarning(): String = text("options.languageWarning")

    fun currentLanguage(): String = SettingsLegacyOptions.currentLanguage()

    fun languages(): List<LanguageEntry> = SettingsLegacyOptions.languages()

    fun applyLanguage(code: String) = SettingsLegacyOptions.applyLanguage(code)

    fun openAccessibilityGuide(parent: Screen) {}

    fun accessibilityGuideLabel(): String = ""
}
*///?}
