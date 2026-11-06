package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if > 1.8.9 {
import com.mojang.text2speech.Narrator
import net.minecraft.client.NarratorStatus
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen
import net.minecraft.client.gui.screens.options.LanguageSelectScreen
import net.minecraft.client.gui.screens.options.SoundOptionsScreen
//?} else {
/*import net.minecraft.client.gui.screen.menu.options.LanguageOptionsScreen
*///?}

//? if >= 1.21.10 {
import net.minecraft.sounds.SoundSource
//?}

//? if >= 26.1 {
private const val VANILLA_SOUND_BUTTON = true
//?} else {
/*private const val VANILLA_SOUND_BUTTON = false
*///?}

//? if >= 26.3 || = 1.8.9 {
private const val SHIFT_MODIFIER = InputConstants.MOD_SHIFT
//?} else {
/*private const val SHIFT_MODIFIER = 1
*///?}

private const val PROMPT_FRAMES = 40
private const val PARAGRAPH = "\n\n"

internal object StartupAccessibility {
    enum class Control { Narrator, Sounds, Accessibility, Language, Standard, Skip, Continue }

    var focus by mutableStateOf(Control.Continue)
        private set
    var keyboardUsed by mutableStateOf(false)
        private set
    var narratorLabel by mutableStateOf("")
        private set
    private var languageStamp by mutableStateOf("")

    private var leave: (() -> Unit)? = null
    private var attachedOnce = false
    private var done = false
    private var promptFrames = 0
    private var promptSpoken = false
    private var narrating = false

    fun pending(): Boolean =
        //? if > 1.8.9 {
        !done && StartupOverlay.enabled() && Minecraft.getInstance().options.onboardAccessibility
        //?} else {
        /*!done && StartupOverlay.enabled() && !PolyPlusConfig.onboardingCompleted
        *///?}

    @JvmStatic
    fun replacesVanilla(): Boolean = StartupOverlay.enabled() && MainMenuReplacement.enabled() && !StartupOverlay.quickPlaying()

    fun attach(onLeave: () -> Unit) {
        leave = onLeave
        refreshLanguage()
        if (attachedOnce) return
        attachedOnce = true
        focus = if (narratorAvailable()) Control.Narrator else Control.Continue
        narratorLabel = narratorText()
        narrating = narratorOn()
    }

    fun detach() {
        leave = null
    }

    fun controls(): List<Control> = buildList {
        if (narratorSupported()) add(Control.Narrator)
        if (VANILLA_SOUND_BUTTON) add(Control.Sounds)
        if (narratorSupported()) add(Control.Accessibility)
        add(Control.Language)
        add(Control.Standard)
        add(Control.Skip)
        add(Control.Continue)
    }

    fun focused(control: Control): Boolean = keyboardUsed && focus == control

    fun narratorSupported(): Boolean =
        //? if > 1.8.9 {
        true
        //?} else {
        /*false
        *///?}

    fun narratorAvailable(): Boolean =
        //? if > 1.8.9 {
        Minecraft.getInstance().narrator.isActive
        //?} else {
        /*false
        *///?}

    fun promptText(): String {
        if (languageStamp.isEmpty() || !narratorSupported()) return ""
        return Platform.i18n().getUnformattedText(I18n.get("accessibility.onboarding.screen.title").substringAfterLast(PARAGRAPH))
    }

    fun continueLabel(): String = label(Control.Continue)

    private fun continueText(): String =
        //? if > 1.8.9 {
        Platform.i18n().getUnformattedText(I18n.get("gui.continue"))
        //?} else {
        /*Platform.i18n().getUnformattedText(I18n.get("gui.done"))
        *///?}

    fun label(control: Control): String = if (languageStamp.isEmpty()) "" else when (control) {
        Control.Narrator -> narratorLabel
        Control.Sounds -> Platform.i18n().getUnformattedText(I18n.get("options.sounds"))
        Control.Accessibility -> Platform.i18n().getUnformattedText(I18n.get("options.accessibility"))
        Control.Language -> Platform.i18n().getUnformattedText(I18n.get("options.language"))
        Control.Standard -> "Use Minecraft's standard startup"
        Control.Skip -> "Skip"
        Control.Continue -> continueText()
    }

    fun activate(control: Control) {
        focus = control
        when (control) {
            Control.Narrator -> cycleNarrator()
            Control.Sounds, Control.Accessibility, Control.Language -> openSettings(control)
            Control.Standard -> useStandardStartup()
            Control.Skip, Control.Continue -> leave?.invoke()
        }
    }

    fun handleKey(key: Int, modifiers: Int): Boolean {
        val exit = leave ?: return false
        when (key) {
            InputConstants.KEY_ESCAPE -> exit()
            InputConstants.KEY_TAB -> move(if (modifiers and SHIFT_MODIFIER != 0) -1 else 1)
            InputConstants.KEY_UP, InputConstants.KEY_LEFT -> move(-1)
            InputConstants.KEY_DOWN, InputConstants.KEY_RIGHT -> move(1)
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> activate(focus)
            else -> return false
        }
        keyboardUsed = true
        return true
    }

    fun finish() {
        done = true
        //? if > 1.8.9 {
        Minecraft.getInstance().options.onboardingAccessibilityFinished()
        Narrator.getNarrator().clear()
        //?}
    }

    fun frame() {
        if (leave == null) return
        refreshLanguage()
        narratorLabel = narratorText()
        //? if > 1.8.9 {
        speakPrompt()
        val on = narratorOn()
        if (on && !narrating && focus != Control.Narrator) sayQueued(buttonNarration(label(focus)))
        narrating = on
        //?}
    }

    private fun refreshLanguage() {
        val stamp = I18n.get("options.language")
        if (stamp != languageStamp) languageStamp = stamp
    }

    private fun move(by: Int) {
        val shown = controls().filter { it != Control.Narrator || narratorAvailable() }
        focus = shown[(shown.indexOf(focus) + by).mod(shown.size)]
        announce(buttonNarration(label(focus)))
    }

    private fun openSettings(control: Control) {
        val parent = ClientPlatform.currentScreen() ?: return
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        Narrator.getNarrator().clear()
        ClientPlatform.setScreen(
            when (control) {
                Control.Sounds -> SoundOptionsScreen(parent, mc.options)
                Control.Accessibility -> AccessibilityOptionsScreen(parent, mc.options)
                else -> LanguageSelectScreen(parent, mc.options, mc.languageManager)
            },
        )
        //?} else {
        /*ClientPlatform.setScreen(LanguageOptionsScreen(parent, mc.options, mc.languageManager))
        *///?}
    }

    private fun useStandardStartup() {
        PolyPlusConfig.brandedStartup = false
        PolyPlusConfig.save()
        //? if > 1.8.9 {
        Narrator.getNarrator().clear()
        val parent = ClientPlatform.currentScreen()
        ClientPlatform.setScreen(AccessibilityOnboardingScreen(Minecraft.getInstance().options) { ClientPlatform.setScreen(parent) })
        //?} else {
        /*leave?.invoke()
        *///?}
    }

    private fun narratorText(): String {
        //? if > 1.8.9 {
        val mc = Minecraft.getInstance()
        val value = if (mc.narrator.isActive) mc.options.narrator().get().getName().string else I18n.get("options.narrator.notavailable")
        return I18n.get("options.generic_value", I18n.get("options.narrator"), value)
        //?} else {
        /*return ""
        *///?}
    }

    private fun narratorOn(): Boolean =
        //? if > 1.8.9 {
        Minecraft.getInstance().options.narrator().get() != NarratorStatus.OFF
        //?} else {
        /*false
        *///?}

    private fun cycleNarrator() {
        if (!narratorAvailable()) return
        //? if > 1.8.9 {
        val options = Minecraft.getInstance().options
        options.narrator().set(NarratorStatus.byId(options.narrator().get().id + 1))
        options.save()
        narratorLabel = narratorText()
        //?}
    }

    private fun buttonNarration(label: String): String = I18n.get("narration.button", label)

    private fun announce(text: String) {
        //? if > 1.8.9 {
        if (!Minecraft.getInstance().options.narrator().get().shouldNarrateSystem() || !narratorAvailable()) return
        Narrator.getNarrator().clear()
        say(text, true)
        //?}
    }

    //? if > 1.8.9 {
    private fun speakPrompt() {
        if (promptSpoken || !narratorAvailable() || !StartupOverlay.promptAllowed()) return
        if (promptFrames < PROMPT_FRAMES) {
            promptFrames++
        } else if (Minecraft.getInstance().isWindowActive) {
            say(I18n.get("accessibility.onboarding.screen.narrator"), true)
            promptSpoken = true
        }
    }

    private fun sayQueued(text: String) {
        if (Minecraft.getInstance().options.narrator().get().shouldNarrateSystem() && narratorAvailable()) say(text, false)
    }

    private fun say(text: String, interrupt: Boolean) {
        //? if >= 1.21.10 {
        Narrator.getNarrator().say(text, interrupt, Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.VOICE))
        //?} elif >= 1.21.5 {
        /*Narrator.getNarrator().say(text, interrupt, 1f)
        *///?} else {
        /*Narrator.getNarrator().say(text, interrupt)
        *///?}
    }
    //?}
}
