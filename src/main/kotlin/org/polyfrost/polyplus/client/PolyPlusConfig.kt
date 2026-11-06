package org.polyfrost.polyplus.client

import com.mojang.blaze3d.platform.InputConstants
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
import org.polyfrost.polyplus.client.network.eos.EosRtcSupport
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.SceneSharing
import org.polyfrost.polyplus.client.party.SvcHandoff
import org.polyfrost.polyplus.client.party.VoiceInputMode
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.social.SocialOverlay
import java.nio.file.Path

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", "${PolyPlusConstants.NAME} (OneClient)", Category.OTHER) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var defaultSettingsApplied = false

    @JvmStatic @Include
    var appliedDefaults = ""

    @JvmStatic @Include
    var modOrderSeed = ""

    @JvmStatic @Include
    var pylonRawInput: Boolean? = null

    @JvmStatic @Include
    var alphabeticalModOrder = false

    @JvmStatic @Include
    var onboardingCompleted = false

    @JvmStatic @Include
    var onboardingFeaturesApplied = false

    @JvmStatic @Include
    var onboardingSprintApplied = false

    @JvmStatic @Include
    var onboardingPolyBlurApplied = false

    @JvmStatic @Include
    var onboardingModSettingsVersion = 0

    @JvmStatic @Include
    var adaptiveBlurApplied = false

    @JvmStatic @Include
    var animatiumItemPositionReset = false

    @JvmStatic @Include
    var itemPhysicDefaultApplied = false

    @JvmStatic @Include
    var jvmAdviceShownAt = 0L

    @JvmStatic @Include
    var ignoredBadMods = ""

    @JvmStatic
    @Switch(
        title = "RAM Analysis Notifications",
        description = "Check how much memory this pack actually needs and suggest a change when the current allocation is hurting performance. Shown at most once every two days.",
    )
    var ramAdviceNotifications = true

    @JvmStatic
    @Switch(
        title = "Problematic Mod Warnings",
        description = "Warn on startup when an installed mod is on OneClient's list of mods known to cause crashes or conflicts.",
    )
    var badModWarnings = true

    @JvmStatic
    @Switch(
        title = "OneClient Startup Screen",
        description = "Show OneClient's loading screen and first launch card when the game starts. Turn this off to use Minecraft's own loading and accessibility screens.",
    )
    var brandedStartup = true

    @JvmStatic @Include
    var vanillaOfferDismissed = false

    @JvmStatic @Include
    var dynamicFov = true

    @JvmStatic @Include
    var onboardingLightTheme = false

    @JvmStatic @Include
    var onboardingUiStyle = 0

    @JvmStatic @Include
    var onboardingToggleSprint = true

    @JvmStatic @Include
    var onboardingMotionBlurMode = -1

    @JvmStatic @Include
    var onboardingMotionBlur = 3

    @JvmStatic @Include
    var onboardingGuiScale = 0

    @JvmStatic @Include
    var onboardingBetterGrassMode = 0

    @JvmStatic @Include
    var onboardingBetterGrassSettled = false

    @JvmStatic @Include
    var onboardingFireOverlayHeight = 0.0

    @JvmStatic @Include
    var onboardingFireOverlayOpacity = OnboardingFeatures.FIRE_OPACITY_VANILLA

    @JvmStatic @Include
    var onboardingShieldHeight = 0f

    @JvmStatic @Include
    var onboardingFireOverlaySettled = false

    @JvmStatic @Include
    var onboardingShieldHeightSettled = false

    @JvmStatic @Include
    var onboardingHorseOpacity = 100f

    @JvmStatic @Include
    var onboardingMountOpacitySettled = false

    @JvmStatic @Include
    var onboardingWaveyCapes = false

    @JvmStatic @Include
    var onboardingWaveyCapesSettled = false

    @JvmStatic @Include
    var onboardingSkinLayers = false

    @JvmStatic @Include
    var onboardingSkinLayersSettled = false

    @JvmStatic @Include
    var onboardingDynamicLightsMode = 0

    @JvmStatic @Include
    var onboardingDynamicLightsSettled = false

    @JvmStatic @Include
    var onboardingGuidesShown = 0

    /** Gamma Utils' brightness as the mod stores it, where 100 is vanilla maximum. */
    @JvmStatic @Include
    var onboardingGamma = 100f

    @JvmStatic @Include
    var onboardingGammaToggled = 1500f

    @JvmStatic @Include
    var onboardingGammaSmooth = false

    @JvmStatic @Include
    var onboardingGammaSettled = false

    @JvmStatic @Include
    var onboardingBlockHighlightConfig = ""

    @JvmStatic @Include
    var onboardingBlockHighlightSettled = false

    @JvmStatic
    @Dropdown(
        title = "Pause Screen",
        description = "Step Back shows your player in your world. Compact keeps the world in view with a bar above the hotbar. Vanilla is the normal Minecraft pause screen.",
        options = ["Step Back", "Compact", "Vanilla"],
    )
    var pauseScreenStyle: PauseScreenStyle = PauseScreenStyle.STEP_BACK

    @JvmStatic
    @Switch(
        title = "Replace Pause Menu LAN Button",
        description = "Replace the vanilla 'Open to LAN' pause menu button with the PolyPlus Host World flow, for hosting your current world over EOS P2P.",
        category = "Multiplayer",
    )
    var replacePauseLanButton = true

    @JvmStatic
    @Switch(
        title = "PolyPlus User Indicators",
        description = "Show a badge on the nametag and tab list of players who are using PolyPlus.",
    )
    var showPolyPlusIndicator = true

    @JvmStatic
    @Switch(
        title = "Chat Emoji",
        description = "Render :shortcode: and unicode emoji (e.g. :sob:) as Twemoji images in chat, and suggest them as you type.",
    )
    var showChatEmoji = true

    @JvmStatic
    @Switch(
        title = "Disable Narrator While Muted",
        description = "Skip the narrator entirely while Master or Voice/Speech volume is 0% to prevent lagspikes.",
    )
    var disableNarratorWhileMuted = true

    @JvmStatic
    @Switch(
        title = "Automatically Refresh Session",
        description = "Automatically refresh your Microsoft account session when it expires.",
    )
    var autoRefreshSession = true

    @JvmStatic
    @Switch(
        title = "Enable Party Voice",
        description = "Talk with your party. Changing this needs a restart.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceEnabled = true

    @JvmStatic
    @Switch(
        title = "Join Party Voice Automatically",
        description = "Join voice as soon as you join a party.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceAutoJoin = false

    @JvmStatic
    @Dropdown(
        title = "Input Mode",
        description = "Send your voice when you speak, or only while you hold a key.",
        options = ["Voice activity", "Push to talk"],
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceInputMode: VoiceInputMode = VoiceInputMode.VOICE_ACTIVITY

    @Keybind(
        title = "Push to Talk",
        description = "Hold to talk when Input Mode is Push to talk.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voicePushToTalkKeybind = OneConfigKeybind(intArrayOf(InputConstants.KEY_V), null, KeyModifiers.NONE, 0L) { state ->
        PartyVoice.onPushToTalk(state)
        true
    }

    @JvmStatic
    @Slider(
        title = "Voice Activity Threshold",
        description = "How loud you need to be before your voice is sent.",
        min = 0f,
        max = 100f,
        step = 1f,
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceActivityThreshold = 50f

    @JvmStatic
    @Slider(
        title = "Input Volume",
        description = "Microphone volume, in percent.",
        min = 0f,
        max = 200f,
        step = 5f,
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceInputVolume = 100f

    @JvmStatic
    @Slider(
        title = "Output Volume",
        description = "Party voice volume, in percent.",
        min = 0f,
        max = 200f,
        step = 5f,
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceOutputVolume = 100f

    @JvmStatic
    @Slider(
        title = "Proximity Range",
        description = "In proximity mode, how far away in blocks you can still hear a party member.",
        min = 16f,
        max = 128f,
        step = 1f,
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceProximityRange = 48f

    @JvmStatic
    @Switch(
        title = "Use Java Sound for Voice",
        description = "Use Java's own audio for peer-to-peer voice instead of OpenAL. Try this if your microphone or speaker doesn't work.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceJavaSound = false

    @JvmStatic
    @Switch(
        title = "Try EOS Voice on Linux (Experimental)",
        description = "Try Epic's voice rooms on Linux. If they fail, voice falls back to peer-to-peer. Changing this needs a restart.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceLinuxRtc = false

    @JvmStatic
    @Switch(
        title = "Hide My IP in Voice",
        description = "Send peer-to-peer voice through Epic relays so party members never see your IP.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceHideIp = true

    @JvmStatic
    @Dropdown(
        title = "Switch Nearby Members to Simple Voice Chat",
        description = "When a party member is on Simple Voice Chat with you, ask first, always switch to it, or always keep party voice.",
        options = ["Ask", "Always", "Never"],
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var voiceSvcHandoff: SvcHandoff = SvcHandoff.ASK

    @JvmStatic
    @Switch(
        title = "Share My Server With Party",
        description = "Show your party which server you're on, so they can join you.",
        category = "Multiplayer",
        subcategory = "Voice",
    )
    var partyShareServer = true

    @JvmStatic
    @Switch(
        title = "Keep My Own Backdrop in Parties",
        description = "Keep your own lobby scene and time instead of your party leader's. Party members still stand in it.",
        category = "Multiplayer",
        subcategory = "Party",
    )
    var partyKeepOwnBackdrop = false

    @JvmStatic
    @Dropdown(
        title = "Share My Captured Scenes With My Party",
        description = "When you lead a party, let members see lobby scenes captured in your own worlds.",
        options = ["Ask", "Always", "Never"],
        category = "Multiplayer",
        subcategory = "Party",
    )
    var partySceneSharing: SceneSharing = SceneSharing.ASK

    @JvmStatic
    @Switch(
        title = "Join When My Leader Presses Play Together",
        description = "Join your party leader after a 5 second countdown you can cancel. When off, you get a prompt instead.",
        category = "Multiplayer",
        subcategory = "Party",
    )
    var partyPlayTogetherJoin = true

    @JvmStatic @Include
    var partySceneConsents = ""

    @JvmStatic @Include
    var voiceInputDevice = ""

    @JvmStatic @Include
    var voiceOutputDevice = ""

    @JvmStatic @Include
    var voiceNoticeAccepted = false

    @JvmStatic @Include
    var voiceMemberVolumes = ""

    @JvmStatic @Include
    var voiceMemberMutes = ""

    @JvmStatic
    @Switch(
        title = "Accept Terms of Service & Privacy Policy",
        description = "Required for crash reporting and online features (cosmetics, client indicator, etc.). ",
        category = "Privacy",
    )
    var acceptedLegalTerms = true

    @Keybind(
        title = "Socials Menu",
        subcategory = "Keybinds",
        description = "Open the PolyPlus Socials menu",
    )
    var socialsMenuKeybind = OneConfigKeybind(intArrayOf(InputConstants.KEY_P), null, KeyModifiers.SHIFT, 0L) { state ->
        if (state) {
            SocialOverlay.toggle()
        }
        true
    }

    @Keybind(
        title = "Emote Wheel",
        subcategory = "Keybinds",
        description = "Open the PolyPlus Emote Wheel",
    )
    var emoteWheelKeybind = OneConfigKeybind(null, null, KeyModifiers.NONE, 0L) { state ->
        EmoteWheelKeybind.onKeybindState(state)
        true
    }

    @Keybind(
        title = "Open Wardrobe",
        subcategory = "Keybinds",
        description = "Open the PolyPlus wardrobe",
    )
    var wardrobeKeybind = OneConfigKeybind(null, null, KeyModifiers.NONE, 0L) { state ->
        if (state) {
            PolyPlusOneConfigIntegration.openCosmeticsFromKeybind()
        }
        true
    }

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION
        get() = if (PolyPlusConstants.IS_DEV_ENV) field else BackendUrl.PRODUCTION

    init {
        hideIf("apiUrl") { !PolyPlusConstants.IS_DEV_ENV }

        addCallback("acceptedLegalTerms") {
            PrivacyEnforcement.onConfigChanged(acceptedLegalTerms)
        }

        hideIf("voiceLinuxRtc") { !EosRtcSupport.isLinux }

        //? if = 1.8.9 {
        /*hideIf("voiceSvcHandoff") { true }
        *///?}

        addCallback("voiceJavaSound") { PartyVoice.onSettingsChanged(restartAudio = true) }

        for (option in listOf("voiceInputVolume", "voiceOutputVolume", "voiceProximityRange", "voiceHideIp", "voiceInputMode")) {
            addCallback(option) { PartyVoice.onSettingsChanged() }
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect()
            PolyPlusClient.refresh()
        }
    }
}

/** Folds the pre-split polyplus.json into one of the configs made out of it. */
internal fun loadLegacyPolyPlusOptions(label: String, logger: Logger, load: (Path) -> Unit) {
    runCatching {
        Tree.beginFailureCollection()
        try {
            load(ConfigManager.active().folder.resolve("${PolyPlusConstants.ID}.json"))
        } finally {
            val failed = Tree.endFailureCollection()
            if (failed.isNotEmpty()) logger.warn("Left {} $label option(s) at their default", failed)
        }
    }.onFailure { logger.warn("Could not migrate $label options from the legacy PolyPlus config", it) }
}
