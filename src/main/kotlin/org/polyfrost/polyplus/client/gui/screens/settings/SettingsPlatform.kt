package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.polyplus.client.gui.screens.world.DialogSpec
//? if > 1.8.9 {
import com.mojang.blaze3d.platform.Monitor
import com.mojang.blaze3d.platform.VideoMode
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen
import net.minecraft.client.gui.screens.options.ChatOptionsScreen
import net.minecraft.client.gui.screens.options.LanguageSelectScreen
import net.minecraft.client.gui.screens.options.MouseSettingsScreen
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen
import net.minecraft.client.gui.screens.options.SoundOptionsScreen
import net.minecraft.client.gui.screens.options.VideoSettingsScreen
import net.minecraft.client.gui.screens.options.controls.ControlsScreen
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.Mods
import org.polyfrost.polyplus.mixin.client.access.SettingsOptionsSubScreenAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsVideoAccessor
import java.util.Optional
//?} elif = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.menu.options.ChatOptionsScreen
import net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen
import net.minecraft.client.gui.screen.menu.options.LanguageOptionsScreen
import net.minecraft.client.gui.screen.menu.options.OptionsScreen
import net.minecraft.client.gui.screen.menu.options.SkinCustomizationScreen
import net.minecraft.client.gui.screen.menu.options.SoundsScreen
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen
import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen
import net.minecraft.client.options.GameOptions
import org.polyfrost.polyplus.mixin.client.access.SettingsLegacyOptionsAccessor
import org.polyfrost.polyplus.mixin.client.access.SettingsLegacyVideoAccessor
*///?}
//? if >= 1.21.11 {
import net.minecraft.client.TextureFilteringMethod
//?}
//? if > 1.8.9 && < 26.1 {
/*import org.polyfrost.polyplus.mixin.client.access.SettingsOptionsScreenAccessor
*///?}
//? if > 1.8.9 && < 1.21.11 {
/*import net.minecraft.client.GraphicsStatus
*///?}

//? if > 1.8.9 {
//? if >= 1.21.11 {
internal class VideoSession(val mipmaps: Int, val anisotropy: Int, val filtering: TextureFilteringMethod)
//?} else {
/*internal class VideoSession(val mipmaps: Int)
*///?}

internal object SettingsPlatform {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val options: Options get() = mc.options

    val sodium: Boolean by lazy { Mods.loaded(SODIUM_ID) }

    val videoModName: String? by lazy {
        runCatching { FabricLoader.getInstance().getModContainer(SODIUM_ID).map { it.metadata.name }.orElse(null) }.getOrNull()
    }

    fun supports(category: SettingsCategory): Boolean = true

    fun save() = options.save()

    //? if >= 26.2 {
    fun restartRequired(): Boolean = options.isRestartRequiredToApplyVideoSettings

    fun restartNotice(): String = Component.translatable("options.restartRequired").string
    //?} else {
    /*fun restartRequired(): Boolean = false

    fun restartNotice(): String = "Restart the game to apply."
    *///?}

    //? if >= 1.21.11 {
    fun beginVideo(): VideoSession {
        mc.gpuWarnlistManager.resetWarnings()
        if (warnedFeatureOn()) mc.gpuWarnlistManager.dismissWarning()
        return VideoSession(options.mipmapLevels().get(), options.maxAnisotropyBit().get(), options.textureFiltering().get())
    }

    fun endVideo(session: VideoSession) {
        mc.window.changeFullscreenVideoMode()
        val mipmaps = options.mipmapLevels().get()
        if (mipmaps != session.mipmaps || options.maxAnisotropyBit().get() != session.anisotropy || options.textureFiltering().get() != session.filtering) {
            mc.updateMaxMipLevel(mipmaps)
            mc.delayTextureReload()
        }
        options.save()
    }

    fun videoSections(): List<SettingSection> {
        val o = options
        return listOf(
            SettingSection(
                text("options.video.display.header"),
                listOf(fullscreenModeRow()) + SettingsVideoAccessor.`polyplus$displayOptions`(o).mapNotNull { row(it) },
            ),
            SettingSection(
                text("options.video.quality.header"),
                listOfNotNull(row(o.graphicsPreset(), wide = true)) + SettingsVideoAccessor.`polyplus$qualityOptions`(o).mapNotNull { row(it) },
            ),
            SettingSection(
                text("options.video.preferences.header"),
                listOfNotNull(row(o.fov())) + SettingsVideoAccessor.`polyplus$preferenceOptions`(o).mapNotNull { row(it) },
            ),
        )
    }
    //?} else {
    /*fun beginVideo(): VideoSession {
        mc.gpuWarnlistManager.resetWarnings()
        if (warnedFeatureOn()) mc.gpuWarnlistManager.dismissWarning()
        return VideoSession(options.mipmapLevels().get())
    }

    fun endVideo(session: VideoSession) {
        mc.window.changeFullscreenVideoMode()
        val mipmaps = options.mipmapLevels().get()
        if (mipmaps != session.mipmaps) {
            mc.updateMaxMipLevel(mipmaps)
            mc.delayTextureReload()
        }
        options.save()
    }

    fun videoSections(): List<SettingSection> {
        val o = options
        val all = SettingsVideoAccessor.`polyplus$options`(o).toList()
        val display: List<OptionInstance<*>> = listOfNotNull(o.framerateLimit(), o.enableVsync(), inactivityLimit(), o.guiScale(), o.fullscreen(), o.gamma())
        val preferences: List<OptionInstance<*>> = listOf(
            o.bobView(), o.attackIndicator(), o.showAutosaveIndicator(), o.screenEffectScale(), o.fovEffectScale(), o.glintSpeed(), o.glintStrength(),
        )
        val quality = listOf(o.biomeBlendRadius()) +
            all.filter { it !in display && it !in preferences && it !== o.graphicsMode() && it !== o.biomeBlendRadius() }
        return listOf(
            SettingSection("Display", listOf(fullscreenModeRow()) + display.mapNotNull { row(it) }),
            SettingSection("Quality", listOfNotNull(row(o.graphicsMode(), wide = true)) + quality.mapNotNull { row(it) }),
            SettingSection("Preferences", listOfNotNull(row(o.fov())) + preferences.mapNotNull { row(it) }),
        )
    }
    *///?}

    fun graphicsWarning(): DialogSpec? {
        val warnlist = mc.gpuWarnlistManager
        if (!warnlist.isShowingWarning) return null
        val feature = Component.translatable(WARNED_FEATURE)
        val lines = listOfNotNull(
            Component.translatable("options.graphics.warning.message", feature, feature).string,
            warnlist.rendererWarnings?.let { Component.translatable("options.graphics.warning.renderer", it).string },
            warnlist.vendorWarnings?.let { Component.translatable("options.graphics.warning.vendor", it).string },
            warnlist.versionWarnings?.let { Component.translatable("options.graphics.warning.version", it).string },
        )
        return DialogSpec(
            text("options.graphics.warning.title"),
            lines.joinToString("\n\n"),
            text("options.graphics.warning.accept"),
            text("options.graphics.warning.cancel"),
            danger = true,
            onResult = { accepted -> if (mc.gpuWarnlistManager.isShowingWarning) resolveGraphicsWarning(accepted) },
        )
    }

    //? if >= 26.2 {
    private fun resolveGraphicsWarning(accepted: Boolean) {
        val warnlist = mc.gpuWarnlistManager
        if (accepted) {
            options.improvedTransparency().set(true)
            mc.levelExtractor.allChanged()
            warnlist.dismissWarning()
        } else {
            warnlist.dismissWarning()
            options.improvedTransparency().set(false)
        }
        options.save()
    }
    //?} elif >= 1.21.11 {
    /*private fun resolveGraphicsWarning(accepted: Boolean) {
        val warnlist = mc.gpuWarnlistManager
        if (accepted) {
            options.improvedTransparency().set(true)
            mc.levelRenderer.allChanged()
            warnlist.dismissWarning()
        } else {
            warnlist.dismissWarning()
            options.improvedTransparency().set(false)
        }
        options.save()
    }
    *///?} else {
    /*private fun resolveGraphicsWarning(accepted: Boolean) {
        val warnlist = mc.gpuWarnlistManager
        if (accepted) {
            options.graphicsMode().set(GraphicsStatus.FABULOUS)
            mc.levelRenderer.allChanged()
            warnlist.dismissWarning()
        } else {
            warnlist.dismissWarningAndSkipFabulous()
        }
        options.save()
    }
    *///?}

    //? if >= 1.21.11 {
    private fun warnedFeatureOn(): Boolean = options.improvedTransparency().get()

    private const val WARNED_FEATURE = "options.improvedTransparency"
    //?} else {
    /*private fun warnedFeatureOn(): Boolean = options.graphicsMode().get() == GraphicsStatus.FABULOUS

    private const val WARNED_FEATURE = "options.graphics.fabulous"
    *///?}

    //? if >= 1.21.4 && < 1.21.11 {
    /*private fun inactivityLimit(): OptionInstance<*>? = options.inactivityFpsLimit()
    *///?} elif < 1.21.4 {
    /*private fun inactivityLimit(): OptionInstance<*>? = null
    *///?}

    fun open(category: SettingsCategory, parent: Screen) {
        val o = options
        val screen: Screen = when (category) {
            SettingsCategory.Video -> videoScreen(parent)
            SettingsCategory.Sound -> SoundOptionsScreen(parent, o)
            SettingsCategory.Controls -> ControlsScreen(parent, o)
            SettingsCategory.Mouse -> MouseSettingsScreen(parent, o)
            SettingsCategory.Skin -> SkinCustomizationScreen(parent, o)
            SettingsCategory.Accessibility -> AccessibilityOptionsScreen(parent, o)
            SettingsCategory.Language -> LanguageSelectScreen(parent, o, mc.languageManager)
            SettingsCategory.Chat -> ChatOptionsScreen(parent, o)
            SettingsCategory.Online -> OnlineOptionsScreen(parent, o)
            SettingsCategory.Packs -> PackSelectionScreen(
                mc.resourcePackRepository,
                { repository ->
                    o.updateResourcePacks(repository)
                    ClientPlatform.setScreen(parent)
                },
                mc.resourcePackDirectory,
                Component.translatable("resourcePack.title"),
            )
            //? if >= 26.3 || < 26.1 {
            SettingsCategory.Mods -> OptionsScreen(parent, o)
            //?} else {
            /*SettingsCategory.Mods -> OptionsScreen(parent, o, mc.level != null)
            *///?}
        }
        SettingsRoutes.pass(screen)
    }

    fun replaces(screen: Screen): Boolean =
        screen.javaClass == OptionsScreen::class.java || (!sodium && screen.javaClass == VideoSettingsScreen::class.java)

    fun parentOf(screen: Screen): Screen? = when (screen) {
        //? if >= 26.1 {
        is OptionsScreen -> screen.lastScreen
        //?} else {
        /*is OptionsScreen -> (screen as Any as SettingsOptionsScreenAccessor).`polyplus$optionsLastScreen`()
        *///?}
        is SettingsOptionsSubScreenAccessor -> screen.`polyplus$lastScreen`()
        else -> null
    }

    private fun videoScreen(parent: Screen): Screen {
        if (sodium) sodiumScreen(parent)?.let { return it }
        return VideoSettingsScreen(parent, mc, options)
    }

    private fun sodiumScreen(parent: Screen): Screen? = SODIUM_SCREENS.firstNotNullOfOrNull { name ->
        runCatching { Class.forName(name).getMethod("createScreen", Screen::class.java).invoke(null, parent) as? Screen }.getOrNull()
    }

    private fun text(key: String): String = Component.translatable(key).string

    private fun fullscreenModeRow(): SettingRow {
        val window = mc.window
        val monitor = window.findBestMonitor()
        //? if >= 26.3 {
        val caption = text("options.fullscreen.exclusive.mode")
        val hint: String? = text("options.fullscreen.exclusive.mode.tooltip")
        //?} else {
        /*val caption = text("options.fullscreen.resolution")
        val hint: String? = null
        *///?}
        if (monitor == null) {
            return SettingRow(FULLSCREEN_KEY, caption, hint, SettingKind.Choice(listOf(text("options.fullscreen.unavailable")), 0) {}, enabled = false)
        }
        val labels = listOf(text("options.fullscreen.current")) + (0 until modeCount(monitor)).map { modeLabel(monitor, it) }
        val selected = window.preferredFullscreenVideoMode.map { modeIndex(monitor, it) }.orElse(-1) + 1
        val reset = SettingReset(selected <= 0) { window.setPreferredFullscreenVideoMode(Optional.empty()) }
        return SettingRow(FULLSCREEN_KEY, caption, hint, SettingKind.Choice(labels, selected) { index ->
            window.setPreferredFullscreenVideoMode(if (index <= 0) Optional.empty() else Optional.of(modeAt(monitor, index - 1)))
        }, reset = reset)
    }

    //? if >= 26.2 {
    private fun modeCount(monitor: Monitor): Int = monitor.modeCount()

    private fun modeAt(monitor: Monitor, index: Int): VideoMode = monitor.mode(index)

    private fun modeIndex(monitor: Monitor, mode: VideoMode): Int = monitor.indexOfMode(mode)
    //?} else {
    /*private fun modeCount(monitor: Monitor): Int = monitor.getModeCount()

    private fun modeAt(monitor: Monitor, index: Int): VideoMode = monitor.getMode(index)

    private fun modeIndex(monitor: Monitor, mode: VideoMode): Int = monitor.getVideoModeIndex(mode)
    *///?}

    private fun modeLabel(monitor: Monitor, index: Int): String {
        val mode = modeAt(monitor, index)
        return Component.translatable(
            "options.fullscreen.entry",
            mode.width,
            mode.height,
            //? if >= 26.3 {
            mode.refreshRateLabel(),
            //?} else {
            /*mode.refreshRate,
            *///?}
            mode.redBits + mode.greenBits + mode.blueBits,
        ).string
    }

    //? if >= 1.21.11 {
    private fun row(instance: OptionInstance<*>, wide: Boolean = false): SettingRow? {
        val enabled = instance !== options.maxAnisotropyBit() || options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC
        return SettingsOptionRows.row(instance, wide, enabled)
    }
    //?} else {
    /*private fun row(instance: OptionInstance<*>, wide: Boolean = false): SettingRow? = SettingsOptionRows.row(instance, wide)
    *///?}

    private const val SODIUM_ID = "sodium"
    private const val FULLSCREEN_KEY = "fullscreen-mode"
    private val SODIUM_SCREENS = listOf(
        "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen",
        "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI",
        "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI",
    )
}
//?} elif = 1.8.9 {
/*internal class VideoSession

internal object SettingsPlatform {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val options: GameOptions get() = mc.options

    val sodium: Boolean get() = false

    val videoModName: String? get() = null

    fun supports(category: SettingsCategory): Boolean = category != SettingsCategory.Accessibility

    fun save() = options.save()

    fun restartRequired(): Boolean = false

    fun restartNotice(): String = ""

    fun beginVideo(): VideoSession = VideoSession()

    fun endVideo(session: VideoSession) = options.save()

    fun graphicsWarning(): DialogSpec? = null

    fun videoSections(): List<SettingSection> = listOf(
        SettingSection(
            "Display",
            SettingsLegacyOptions.rows(
                GameOptions.Option.USE_FULLSCREEN,
                GameOptions.Option.ENABLE_VSYNC,
                GameOptions.Option.FRAMERATE_LIMIT,
                GameOptions.Option.GUI_SCALE,
                GameOptions.Option.GAMMA,
            ),
        ),
        SettingSection(
            "Quality",
            listOfNotNull(SettingsLegacyOptions.row(GameOptions.Option.GRAPHICS, wide = true)) + SettingsLegacyOptions.rows(
                GameOptions.Option.RENDER_DISTANCE,
                GameOptions.Option.AMBIENT_OCCLUSION,
                GameOptions.Option.RENDER_CLOUDS,
                GameOptions.Option.PARTICLES,
                GameOptions.Option.MIPMAP_LEVELS,
                GameOptions.Option.ENTITY_SHADOWS,
                GameOptions.Option.BLOCK_ALTERNATIVES,
                GameOptions.Option.USE_VBO,
            ),
        ),
        SettingSection(
            "Preferences",
            SettingsLegacyOptions.rows(GameOptions.Option.FOV, GameOptions.Option.VIEW_BOBBING, GameOptions.Option.ANAGLYPH),
        ),
    )

    fun open(category: SettingsCategory, parent: Screen) {
        val o = options
        val screen: Screen = when (category) {
            SettingsCategory.Video -> VideoOptionsScreen(parent, o)
            SettingsCategory.Sound -> SoundsScreen(parent, o)
            SettingsCategory.Controls, SettingsCategory.Mouse -> ControlsOptionsScreen(parent, o)
            SettingsCategory.Skin -> SkinCustomizationScreen(parent)
            SettingsCategory.Language -> LanguageOptionsScreen(parent, o, mc.getLanguageManager())
            SettingsCategory.Chat -> ChatOptionsScreen(parent, o)
            SettingsCategory.Packs -> ResourcePacksScreen(parent)
            SettingsCategory.Accessibility, SettingsCategory.Online, SettingsCategory.Mods -> OptionsScreen(parent, o)
        }
        SettingsRoutes.pass(screen)
    }

    fun replaces(screen: Screen): Boolean =
        screen.javaClass == OptionsScreen::class.java || screen.javaClass == VideoOptionsScreen::class.java

    fun parentOf(screen: Screen): Screen? = when (screen) {
        is OptionsScreen -> (screen as Any as? SettingsLegacyOptionsAccessor)?.`polyplus$optionsParent`()
        is VideoOptionsScreen -> (screen as Any as? SettingsLegacyVideoAccessor)?.`polyplus$videoParent`()
        else -> null
    }
}
*///?}
