@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.a11y.a11y
import org.polyfrost.oneconfig.api.ui.v1.a11y.focusRing
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.screens.ModCard as OneConfigModCard
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults
import org.polyfrost.polyplus.client.features.BlockHighlightDraft
import org.polyfrost.polyplus.client.features.BlockHighlightPresets
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.DefaultModOrder
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.features.OnboardingFeatures.ModCard
import org.polyfrost.polyplus.client.gui.preview.BlockHighlightRenderer
import org.polyfrost.polyplus.client.gui.preview.UnityMotionBlur
import org.polyfrost.polyplus.client.gui.preview.VanillaTextures
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.StartupAccessibility
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.legal.LegalDocument
import org.polyfrost.polyplus.client.lobby.LobbyFraming
import org.polyfrost.polyplus.client.legal.LegalDocuments
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Locale
import kotlin.io.path.exists
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

class PolyPlusOnboardingScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private var firstFrameDrawn = false

    // consuming Escape here rather than via shouldCloseOnEsc, since OneConfig hands unhandled keys straight to Screen on 1.8.9
    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean =
        StartupAccessibility.handleKey(key, modifiers) || OnboardingKeyCapture.capture(key) || key == InputConstants.KEY_ESCAPE

    //? if = 1.8.9 {
    /*override fun init() {
        MenuPanorama.legacyPanorama()
        super.init()
    }

    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        super.render(mouseX, mouseY, tickDelta)
        MenuBackgroundPass.renderInline()
        StartupAccessibility.frame()
    }
    *///?} elif <26.1 {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        renderPanorama(ctx, tickDelta)
        if (firstFrameDrawn) {
            val gameRenderer = Minecraft.getInstance().gameRenderer
            //? if <1.21.4 {
            gameRenderer.processBlurEffect(tickDelta)
            //?} else {
            /*gameRenderer.processBlurEffect()
            *///?}
        }
        MenuBackgroundPass.renderInline(ctx)
        super.render(ctx, mouseX, mouseY, tickDelta)
        firstFrameDrawn = true
        StartupAccessibility.frame()
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?} else {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        Minecraft.getInstance().gameRenderer
            //? if >= 26.2 {
            .panorama()
            .extractRenderState(ctx, width, height)
            //?} else {
            /*.getPanorama()
            .extractRenderState(ctx, width, height, true)
            *///?}
        ctx.blurBeforeThisStratum()
        MenuBackgroundPass.renderInline(ctx)
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        StartupAccessibility.frame()
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    @Composable
    override fun compose() {
        ProvideOneConfigTheme {
            OnboardingFlow(OnboardingHost.Screen) { ClientPlatform.setScreen(TitleScreen()) }
        }
    }
}

internal enum class OnboardingHost { Screen, Lobby }

@Composable
internal fun OnboardingFlow(
    host: OnboardingHost,
    modifier: Modifier = Modifier,
    withTerms: Boolean = true,
    onFinished: () -> Unit,
) {
    val lobbyHost = host == OnboardingHost.Lobby
    val accessibilityStep = remember { StartupAccessibility.pending() }
    val needsTerms = remember { withTerms && PrivacyConsent.needsPrompt() }
    val needsSettings = remember { !PolyPlusConfig.onboardingCompleted }
    val needsModSettings = remember { OnboardingFeatures.needsModSettingsChoice() }
    val needsBlurChoice = remember { OnboardingFeatures.needsMotionBlurChoice() }
    val owedGuides = remember { OnboardingFeatures.pendingGuides().mapNotNull(ModGuide::of).toSet() }
    val showsModSettings = remember {
        (needsSettings || needsModSettings) && OnboardingFeatures.modsPageAvailable
    }
    val modReads = remember { ModReads.read(showsModSettings) }
    val offeredCards = remember {
        OnboardingFeatures.newModCards(PolyPlusConfig.onboardingModSettingsVersion, modReads.cards)
    }
    val sprintStep = needsSettings && OnboardingFeatures.polySprintAvailable
    var guides by remember { mutableStateOf(owedGuides) }
    val pages = remember(guides) {
        buildList {
            if (accessibilityStep) add(OnboardingStep.Accessibility)
            if (needsTerms) add(OnboardingStep.Terms)
            if (needsSettings) add(OnboardingStep.LookAndFeel)
            if (needsSettings) add(OnboardingStep.ModOrder)
            if (showsModSettings) {
                if (sprintStep) add(OnboardingStep.Sprint)
                offeredCards.filter { it != ModCard.BLOCK_HIGHLIGHT }
                    .chunked(MOD_CARDS_PER_PAGE)
                    .forEach { add(OnboardingStep.Mods(it)) }
                if (ModCard.BLOCK_HIGHLIGHT in offeredCards) add(OnboardingStep.BlockHighlight)
            }
            if (needsBlurChoice) add(OnboardingStep.MotionBlur)
            guides.sortedBy { it.ordinal }.forEach { add(OnboardingStep.Guide(it)) }
            if (!lobbyHost && (needsSettings || needsModSettings)) add(OnboardingStep.Done)
        }.ifEmpty { listOf(OnboardingStep.Done) }
    }
    var page by remember { mutableIntStateOf(0) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var lightTheme by remember { mutableStateOf(PolyPlusConfig.onboardingLightTheme) }
    var uiStyle by remember { mutableIntStateOf(PolyPlusConfig.onboardingUiStyle) }
    var toggleSprint by remember { mutableStateOf(PolyPlusConfig.onboardingToggleSprint) }
    var alphabeticalMods by remember { mutableStateOf(PolyPlusConfig.alphabeticalModOrder) }
    var grassMode by remember { mutableIntStateOf(OnboardingFeatures.BETTER_GRASS_OFF) }
    var fireHeight by remember { mutableStateOf(modReads.fireHeight ?: PolyPlusConfig.onboardingFireOverlayHeight) }
    var fireOpacity by remember {
        mutableStateOf(modReads.fireOpacity ?: PolyPlusConfig.onboardingFireOverlayOpacity)
    }
    var shieldHeight by remember { mutableStateOf(modReads.shield ?: PolyPlusConfig.onboardingShieldHeight) }
    var horseOpacity by remember { mutableStateOf(modReads.horse ?: PolyPlusConfig.onboardingHorseOpacity) }
    var waveyCapes by remember { mutableStateOf(false) }
    var skinLayers by remember { mutableStateOf(false) }
    var dynamicLights by remember { mutableIntStateOf(PolyPlusConfig.onboardingDynamicLightsMode) }
    var gamma by remember {
        mutableStateOf((modReads.gamma ?: PolyPlusConfig.onboardingGamma).clampGamma())
    }
    var gammaToggled by remember {
        mutableStateOf((modReads.gammaToggled ?: PolyPlusConfig.onboardingGammaToggled).clampGamma())
    }
    var gammaSmooth by remember { mutableStateOf(modReads.gammaSmooth ?: PolyPlusConfig.onboardingGammaSmooth) }
    val blockHighlightPresets = remember {
        if (modReads.blockHighlight != null) BlockHighlightPresets.presetJsons() else emptyMap()
    }
    var blockHighlight by remember {
        mutableStateOf(modReads.blockHighlight ?: PolyPlusConfig.onboardingBlockHighlightConfig)
    }
    var blockHighlightBase by remember {
        mutableIntStateOf(
            BlockHighlightPresets.NAMES.indexOfFirst { name ->
                blockHighlightPresets[name]?.let { runCatching { BlockHighlightDraft(blockHighlight).matches(it) }.getOrNull() } == true
            }.coerceAtLeast(0),
        )
    }
    val touched = remember { mutableSetOf<ModCard>() }
    fun markTouched(card: ModCard) {
        touched += card
        ModGuide.of(card)?.let { guides = guides + it }
    }
    fun <T> touch(card: ModCard, old: T, new: T) {
        if (old != new) markTouched(card)
    }
    var motionBlur by remember { mutableIntStateOf(PolyPlusConfig.onboardingMotionBlur.coerceIn(1, MOTION_BLUR_MAX)) }
    var blurMode by remember { mutableIntStateOf(OnboardingFeatures.MOTION_BLUR_UNSET) }
    var blurSampleTimedOut by remember { mutableStateOf(false) }
    var blurPickedManually by remember { mutableStateOf(false) }
    var maxGuiScale by remember { mutableIntStateOf(OnboardingFeatures.maxGuiScale()) }
    var chosenGuiScale by remember {
        mutableIntStateOf(
            //? if > 1.8.9 {
            Minecraft.getInstance().options.guiScale().get().coerceIn(0, maxGuiScale),
            //?} else {
            /*Minecraft.getInstance().options.guiScale.coerceIn(0, maxGuiScale),
            *///?}
        )
    }
    val guiScale = if (chosenGuiScale <= 0) 0 else minOf(chosenGuiScale, maxGuiScale)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val latest = OnboardingFeatures.maxGuiScale()
            if (latest == maxGuiScale) continue
            maxGuiScale = latest
            OnboardingFeatures.applyGuiScale(if (chosenGuiScale <= 0) 0 else minOf(chosenGuiScale, latest), persist = false)
        }
    }
    var themeShown by remember { mutableStateOf(false) }
    LaunchedEffect(lightTheme, uiStyle) {
        if (needsSettings) OnboardingFeatures.applyTheme(lightTheme, uiStyle)
        if (lobbyHost && themeShown) LobbyFraming.react(LobbyFraming.Reaction.GLANCE)
        themeShown = true
    }
    val finish = {
        if (needsSettings) {
            PolyPlusConfig.onboardingLightTheme = lightTheme
            PolyPlusConfig.onboardingUiStyle = uiStyle
            PolyPlusConfig.onboardingGuiScale = chosenGuiScale
            PolyPlusConfig.onboardingToggleSprint = toggleSprint
            if (alphabeticalMods != PolyPlusConfig.alphabeticalModOrder) {
                PolyPlusConfig.alphabeticalModOrder = alphabeticalMods
                DefaultModOrder.apply()
            }
        }
        if (showsModSettings) {
            val startedAtVersion = PolyPlusConfig.onboardingModSettingsVersion
            fun <T> chosen(card: ModCard, value: T, stored: T): T = if (card in offeredCards) value else stored
            PolyPlusConfig.onboardingBetterGrassMode =
                chosen(ModCard.GRASS, grassMode, PolyPlusConfig.onboardingBetterGrassMode)
            PolyPlusConfig.onboardingFireOverlayHeight =
                chosen(ModCard.FIRE_OVERLAY, fireHeight, PolyPlusConfig.onboardingFireOverlayHeight)
            PolyPlusConfig.onboardingFireOverlayOpacity =
                chosen(ModCard.FIRE_OVERLAY, fireOpacity, PolyPlusConfig.onboardingFireOverlayOpacity)
            PolyPlusConfig.onboardingShieldHeight =
                chosen(ModCard.SHIELD_HEIGHT, shieldHeight, PolyPlusConfig.onboardingShieldHeight)
            PolyPlusConfig.onboardingHorseOpacity =
                chosen(ModCard.MOUNT, horseOpacity, PolyPlusConfig.onboardingHorseOpacity)
            PolyPlusConfig.onboardingWaveyCapes =
                chosen(ModCard.CAPES, waveyCapes, PolyPlusConfig.onboardingWaveyCapes)
            PolyPlusConfig.onboardingSkinLayers =
                chosen(ModCard.SKIN_LAYERS, skinLayers, PolyPlusConfig.onboardingSkinLayers)
            if (grassMode != modReads.grass) touched += ModCard.GRASS
            if (waveyCapes != modReads.capes) touched += ModCard.CAPES
            if (skinLayers != modReads.layers) touched += ModCard.SKIN_LAYERS
            PolyPlusConfig.onboardingGamma =
                chosen(ModCard.GAMMA, gamma, PolyPlusConfig.onboardingGamma)
            PolyPlusConfig.onboardingGammaToggled =
                chosen(ModCard.GAMMA, gammaToggled, PolyPlusConfig.onboardingGammaToggled)
            PolyPlusConfig.onboardingGammaSmooth =
                chosen(ModCard.GAMMA, gammaSmooth, PolyPlusConfig.onboardingGammaSmooth)
            PolyPlusConfig.onboardingDynamicLightsMode =
                chosen(ModCard.DYNAMIC_LIGHTS, dynamicLights, PolyPlusConfig.onboardingDynamicLightsMode)
            if (dynamicLights != modReads.dynamicLights) touched += ModCard.DYNAMIC_LIGHTS
            PolyPlusConfig.onboardingBlockHighlightConfig = chosen(
                ModCard.BLOCK_HIGHLIGHT,
                blockHighlight,
                PolyPlusConfig.onboardingBlockHighlightConfig,
            )
            val available = OnboardingFeatures.availableCards
            fun settled(card: ModCard, current: Boolean) =
                OnboardingFeatures.settledAfterRun(
                    card, offeredCards, available, touched, current, startedAtVersion,
                )
            PolyPlusConfig.onboardingBetterGrassSettled =
                settled(ModCard.GRASS, PolyPlusConfig.onboardingBetterGrassSettled)
            PolyPlusConfig.onboardingFireOverlaySettled =
                settled(ModCard.FIRE_OVERLAY, PolyPlusConfig.onboardingFireOverlaySettled)
            PolyPlusConfig.onboardingShieldHeightSettled =
                settled(ModCard.SHIELD_HEIGHT, PolyPlusConfig.onboardingShieldHeightSettled)
            PolyPlusConfig.onboardingMountOpacitySettled =
                settled(ModCard.MOUNT, PolyPlusConfig.onboardingMountOpacitySettled)
            PolyPlusConfig.onboardingWaveyCapesSettled =
                settled(ModCard.CAPES, PolyPlusConfig.onboardingWaveyCapesSettled)
            PolyPlusConfig.onboardingSkinLayersSettled =
                settled(ModCard.SKIN_LAYERS, PolyPlusConfig.onboardingSkinLayersSettled)
            PolyPlusConfig.onboardingGammaSettled =
                settled(ModCard.GAMMA, PolyPlusConfig.onboardingGammaSettled)
            PolyPlusConfig.onboardingBlockHighlightSettled =
                settled(ModCard.BLOCK_HIGHLIGHT, PolyPlusConfig.onboardingBlockHighlightSettled)
            PolyPlusConfig.onboardingDynamicLightsSettled =
                settled(ModCard.DYNAMIC_LIGHTS, PolyPlusConfig.onboardingDynamicLightsSettled)
            PolyPlusConfig.onboardingModSettingsVersion =
                OnboardingFeatures.completedModSettingsVersion(startedAtVersion, available)
        }
        if (needsBlurChoice && blurMode != OnboardingFeatures.MOTION_BLUR_UNSET) {
            PolyPlusConfig.onboardingMotionBlurMode = blurMode
            PolyPlusConfig.onboardingMotionBlur = motionBlur
            PolyPlusConfig.onboardingPolyBlurApplied = false
            PolyPlusConfig.adaptiveBlurApplied = true
        }
        OnboardingFeatures.markGuidesShown(guides.map { it.card })
        PolyPlusConfig.onboardingCompleted = true
        PolyPlusConfig.save()
        if (needsSettings) {
            OnboardingFeatures.applySavedSettings()
        } else {
            if (showsModSettings) OnboardingFeatures.applySavedModSettings()
            if (needsBlurChoice) OnboardingFeatures.applySavedMotionBlur()
        }
        onFinished()
    }

    val skipSetup = {
        if (needsBlurChoice && blurMode == OnboardingFeatures.MOTION_BLUR_UNSET && AdaptiveBlurDefaults.sampled) {
            blurMode = AdaptiveBlurDefaults.recommendedMode
        }
        finish()
    }

    val onBlurPage = pages[page] == OnboardingStep.MotionBlur
    val waitingForOptimization = onBlurPage && !AdaptiveBlurDefaults.sampled && !blurPickedManually
    LaunchedEffect(onBlurPage) {
        if (onBlurPage && !blurSampleTimedOut) {
            delay(BLUR_SAMPLE_TIMEOUT_MILLIS)
            blurSampleTimedOut = true
        }
    }

    val recordLegalChoice = { accepted: Boolean ->
        val document = legalDocument
        if (accepted) {
            PrivacyConsent.accept(document?.version ?: 0, document?.resolvedPrivacyVersion ?: 0)
        } else {
            PrivacyConsent.decline()
        }
        PrivacyEnforcement.syncConfig()
        PrivacyEnforcement.apply()
        if (lobbyHost) LobbyFraming.react(if (accepted) LobbyFraming.Reaction.WAVE else LobbyFraming.Reaction.NOD)
    }
    val advance: () -> Unit = {
        if (lobbyHost && page < pages.size - 1 && pages[page] != OnboardingStep.Terms) LobbyFraming.react(LobbyFraming.Reaction.NOD)
        if (page == pages.size - 1) finish() else page++
    }
    val answerTerms = { accepted: Boolean ->
        recordLegalChoice(accepted)
        advance()
    }
    val leaveAccessibility = {
        StartupAccessibility.finish()
        advance()
    }

    val current = pages[page]
    val onTerms = current == OnboardingStep.Terms
    val onAccessibility = current == OnboardingStep.Accessibility
    val blurUnanswered = onBlurPage && blurMode == OnboardingFeatures.MOTION_BLUR_UNSET
    val onNext: () -> Unit = when {
        onTerms -> ({ answerTerms(true) })
        onAccessibility -> leaveAccessibility
        else -> advance
    }
    val nextEnabled = !waitingForOptimization && !blurUnanswered
    val nextBlocked = when {
        waitingForOptimization -> "Checking your frame rate. Wait a few seconds or pick manually."
        blurUnanswered -> "Choose a mode to continue."
        else -> null
    }
    val onBack: (() -> Unit)? = if (page > 0) ({ page-- }) else null
    SideEffect { OnboardingKeys.update(page, if (nextEnabled) onNext else null, onBack, nextBlocked) }
    DisposableEffect(Unit) { onDispose { OnboardingKeys.clear() } }
    if (!onAccessibility) Announce(pageSpeech(current, page, waitingForOptimization))

    val pageContent: @Composable () -> Unit = {
        when (val step = pages[page]) {
            OnboardingStep.Accessibility -> AccessibilityPage(leaveAccessibility)
            OnboardingStep.Terms ->
                TermsPage(
                    legalDocument,
                    { legalDocument = it },
                    if (lobbyHost) LOBBY_TERMS_TOP else TERMS_CONTENT_TOP,
                )
            OnboardingStep.LookAndFeel ->
                LookAndFeelPage(
                    lightTheme, { lightTheme = it },
                    uiStyle, { uiStyle = it },
                    guiScale, maxGuiScale, { chosenGuiScale = it },
                )
            OnboardingStep.ModOrder -> ModOrderPage(alphabeticalMods) { alphabeticalMods = it }
            OnboardingStep.Sprint -> SprintPage(toggleSprint) { toggleSprint = it }
            is OnboardingStep.Mods -> {
                Header("Continuing with", "Mods")
                ModCardRow(step.cards.size) {
                    step.cards.forEach { card ->
                        when (card) {
                            ModCard.GRASS ->
                                BetterGrassCard(grassMode) {
                                    touch(ModCard.GRASS, grassMode, it)
                                    grassMode = it
                                }
                            ModCard.FIRE_OVERLAY ->
                                FireOverlayCard(
                                    fireHeight,
                                    { touch(ModCard.FIRE_OVERLAY, fireHeight, it); fireHeight = it },
                                    fireOpacity,
                                    {
                                        touch(ModCard.FIRE_OVERLAY, fireOpacity, it)
                                        fireOpacity = it
                                    },
                                )
                            ModCard.SHIELD_HEIGHT ->
                                ShieldHeightCard(shieldHeight) {
                                    touch(ModCard.SHIELD_HEIGHT, shieldHeight, it)
                                    shieldHeight = it
                                }
                            ModCard.MOUNT ->
                                MountOpacityCard(horseOpacity) {
                                    touch(ModCard.MOUNT, horseOpacity, it)
                                    horseOpacity = it
                                }
                            ModCard.CAPES ->
                                WaveyCapesCard(waveyCapes) {
                                    touch(ModCard.CAPES, waveyCapes, it)
                                    waveyCapes = it
                                }
                            ModCard.SKIN_LAYERS ->
                                SkinLayersCard(skinLayers) {
                                    touch(ModCard.SKIN_LAYERS, skinLayers, it)
                                    skinLayers = it
                                }
                            ModCard.GAMMA ->
                                FullbrightCard(
                                    gamma, { touch(ModCard.GAMMA, gamma, it); gamma = it },
                                    gammaToggled,
                                    {
                                        touch(ModCard.GAMMA, gammaToggled, it)
                                        gammaToggled = it
                                    },
                                    gammaSmooth,
                                    {
                                        touch(ModCard.GAMMA, gammaSmooth, it)
                                        gammaSmooth = it
                                    },
                                )
                            ModCard.DYNAMIC_LIGHTS ->
                                DynamicLightsCard(dynamicLights) {
                                    touch(ModCard.DYNAMIC_LIGHTS, dynamicLights, it)
                                    dynamicLights = it
                                }
                            ModCard.BLOCK_HIGHLIGHT -> Unit
                        }
                    }
                }
            }
            OnboardingStep.MotionBlur ->
                if (waitingForOptimization) {
                    OptimizingPage(blurSampleTimedOut) { blurPickedManually = true }
                } else {
                    MotionBlurPage(
                        blurMode,
                        { blurMode = it },
                        motionBlur,
                        { motionBlur = it },
                        showRecommendation = !blurPickedManually,
                    )
                }
            OnboardingStep.BlockHighlight ->
                BlockHighlightPage(
                    blockHighlight,
                    blockHighlightBase,
                    blockHighlightPresets,
                    onEdit = {
                        touch(ModCard.BLOCK_HIGHLIGHT, blockHighlight, it)
                        blockHighlight = it
                    },
                    onBase = { blockHighlightBase = it },
                )
            is OnboardingStep.Guide -> GuidePage(step.guide)
            OnboardingStep.Done -> DonePage()
        }
    }

    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Page)) { pageContent() }
        val lastPage = page == pages.size - 1
        Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Bar)) {
            BottomNavigation(
                page,
                pages.size,
                onSkip = if (onAccessibility) leaveAccessibility else skipSetup,
                onBack = { page-- },
                onNext = onNext,
                nextEnabled = nextEnabled,
                allowSkip = onAccessibility || (!onTerms && !onBlurPage && !lastPage),
                hint = if (blurUnanswered && !waitingForOptimization) "Choose a mode to continue" else null,
                nextBlocked = nextBlocked,
                nextLabel = when {
                    onAccessibility -> StartupAccessibility.continueLabel()
                    onTerms -> "Agree"
                    current is OnboardingStep.Guide -> "Got it"
                    else -> null
                },
                secondaryLabel = if (onTerms) "Decline" else null,
                onSecondary = { answerTerms(false) },
                skipFocused = onAccessibility && StartupAccessibility.focused(StartupAccessibility.Control.Skip),
                nextFocused = onAccessibility && StartupAccessibility.focused(StartupAccessibility.Control.Continue),
            )
        }
    }

    if (lobbyHost) {
        val outer = LocalDensity.current
        val accessibility = pages[page] == OnboardingStep.Accessibility
        val compact = pages[page] == OnboardingStep.Terms || accessibility
        val panelWidth by animateFloatAsState(if (compact) TERMS_PANEL_WIDTH else PANEL_WIDTH, animationSpec = panelSpec())
        val panelHeight by animateFloatAsState(
            when {
                accessibility -> accessibilityPanelHeight()
                compact -> LOBBY_TERMS_HEIGHT
                else -> PANEL_HEIGHT
            },
            animationSpec = panelSpec(),
        )
        CompositionLocalProvider(
            LocalDensity provides Density(usableScale(outer.density * LOBBY_PANEL_SCALE), usableScale(outer.fontScale)),
            LocalPanelWidth provides panelWidth,
            LocalPanelHeight provides panelHeight,
        ) {
            var grow by remember { mutableStateOf(StartupOverlay.cardGrow) }
            LaunchedEffect(Unit) {
                while (grow < 1f) {
                    withFrameNanos { }
                    grow = StartupOverlay.cardGrow
                }
            }
            val shownWidth = CARD_PILL_WIDTH + (panelWidth - CARD_PILL_WIDTH) * grow
            val shownHeight = CARD_PILL_HEIGHT + (panelHeight - CARD_PILL_HEIGHT) * grow
            val rise = (1f - grow) * (CARD_PILL_CENTER - panelHeight / 2f)
            Box(modifier.width(PANEL_WIDTH.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(0.dp, rise.dp).size(shownWidth.dp, shownHeight.dp).panel()) {
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .offset(((shownWidth - panelWidth) / 2f).dp, ((shownHeight - panelHeight) / 2f - rise).dp)
                            .size(panelWidth.dp, panelHeight.dp)
                            .graphicsLayer { alpha = ((grow - CARD_CONTENT_DELAY) / (1f - CARD_CONTENT_DELAY)).coerceIn(0f, 1f) },
                    ) { content() }
                }
            }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().a11yRoot()) {
        val effectiveGuiScale = if (guiScale <= 0) maxGuiScale else guiScale
        val containFit = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
            .takeIf { it.isFinite() && it > 0f } ?: 1f
        val scale = minOf(effectiveGuiScale / REFERENCE_GUI_SCALE, containFit) * UI_SCALE * GUI_DENSITY_TRIM
        val accessibility = pages[page] == OnboardingStep.Accessibility
        val compact = pages[page] == OnboardingStep.Terms || accessibility
        val panelWidth by animateFloatAsState(
            if (compact) TERMS_PANEL_WIDTH else PANEL_WIDTH,
            animationSpec = panelSpec(),
        )
        val panelHeight by animateFloatAsState(
            when {
                accessibility -> accessibilityPanelHeight()
                compact -> TERMS_PANEL_HEIGHT
                else -> PANEL_HEIGHT
            },
            animationSpec = panelSpec(),
        )
        CompositionLocalProvider(
            LocalDensity provides scaledDensity(scale),
            LocalPanelWidth provides panelWidth,
            LocalPanelHeight provides panelHeight,
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp),
            ) {
                Box(
                    Modifier
                        .offset(((DESIGN_WIDTH - panelWidth) / 2f).dp, ((DESIGN_HEIGHT - panelHeight) / 2f).dp)
                        .size(panelWidth.dp, panelHeight.dp)
                        .then(
                            if (OneConfigTheme.current.shadowEnabled) {
                                Modifier.shadow(
                                    elevation = 29.dp,
                                    shape = ButtonShape,
                                    ambientColor = ShadowColor,
                                    spotColor = ShadowColor,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clip(ButtonShape)
                        .background(PageBackground.copy(alpha = 0.9f))
                        .border(SocialPanelBorderWidth, OneConfigTheme.current.borderColor, ButtonShape),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LookAndFeelPage(
    lightTheme: Boolean,
    onLightTheme: (Boolean) -> Unit,
    uiStyle: Int,
    onUiStyle: (Int) -> Unit,
    guiScale: Int,
    maxGuiScale: Int,
    onGuiScale: (Int) -> Unit,
) {
    Header("Let’s configure the", "Look & Feel")
    val colorsHeight = LABEL_HEIGHT + 32f
    val styleHeight = LABEL_HEIGHT + 155f
    val scaleHeight = LABEL_HEIGHT + 32f
    val total = colorsHeight + SECTION_GAP + styleHeight + SECTION_GAP + scaleHeight
    var y = CONTENT_TOP + ((CONTENT_BOTTOM - CONTENT_TOP) - total) / 2f
    SectionLabel("UI Colors", y)
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ChoiceButton("Dark", MAIN_MENU_ASSETS + "moon-star.svg", !lightTheme, 198f, option = true) { onLightTheme(false) }
        ChoiceButton("Light", ONBOARDING_ASSETS + "sun.svg", lightTheme, 198f, option = true) { onLightTheme(true) }
    }
    y += colorsHeight + SECTION_GAP
    SectionLabel("UI Style", y)
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        StyleCard("PolyGlass", uiStyle == 0, rounded = true) { onUiStyle(0) }
        StyleCard("Minecraft", uiStyle == 1, rounded = false) { onUiStyle(1) }
    }
    y += styleHeight + SECTION_GAP
    GuiScaleSection(y, guiScale, maxGuiScale, onGuiScale)
}

@Composable
private fun GuiScaleSection(y: Float, guiScale: Int, maxScale: Int, onGuiScale: (Int) -> Unit) {
    SectionLabel("GUI Scale", y)
    val steps = maxScale.coerceAtLeast(1)
    fun valueToProgress(v: Int): Float = if (v <= 0) 1f else ((v - 1).toFloat() / steps).coerceIn(0f, 1f)
    fun indexToValue(index: Int): Int = if (index >= steps) 0 else index + 1
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), verticalAlignment = Alignment.CenterVertically) {
        var pending by remember { mutableIntStateOf(guiScale) }
        val shown = if (guiScale <= 0) "Auto" else guiScale.toString()
        OnboardingSlider(
            progress = valueToProgress(guiScale),
            width = 332f,
            thumbSize = WIDE_SLIDER_THUMB,
            trackHeight = WIDE_SLIDER_TRACK,
            label = "GUI Scale",
            value = shown,
            keyStep = 1f / steps,
            onRelease = { OnboardingFeatures.applyGuiScale(pending, persist = false) },
        ) { p ->
            pending = indexToValue((p * steps).roundToInt())
            onGuiScale(pending)
        }
        Spacer(Modifier.width(18.dp))
        SliderValueBox(
            shown,
            width = 64.dp,
            height = 26.dp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            alignment = Alignment.CenterStart,
            textPadding = 8.dp,
        )
    }
}

@Composable
private fun ModCardRow(count: Int, content: @Composable RowScope.() -> Unit) {
    val rowWidth = count * CARD_WIDTH + (count - 1) * CARD_GAP
    Row(
        Modifier.offset(((LocalPanelWidth.current - rowWidth) / 2f).dp, CARD_TOP.dp),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
        content = content,
    )
}

@Composable
private fun ModCardFrame(
    title: String,
    icon: String,
    description: String,
    preview: @Composable () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
            .clip(ButtonShape)
            .background(ChoiceBackground)
            .border(SocialPanelBorderWidth, SocialPanelBorderBrush, ButtonShape),
    ) {
        CompositionLocalProvider(LocalCardTitle provides title) { Column(Modifier.fillMaxSize().padding(CARD_PADDING.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, SocialTextPrimary, Modifier.size(22.dp))
                SocialText(title, 17.sp, color = SocialTextPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(CARD_TITLE_GAP.dp))
            preview()
            Spacer(Modifier.height(CARD_DESC_GAP.dp))
            SocialText(
                description,
                12.sp,
                Modifier.width(CARD_CONTENT_WIDTH.dp),
                SocialTextSecondary,
                FontWeight.Light,
                TextAlign.Start,
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.width(CARD_CONTENT_WIDTH.dp),
                verticalArrangement = Arrangement.spacedBy(CARD_ROW_GAP.dp),
                content = controls,
            )
        } }
    }
}

@Composable
private fun cardSpeech(label: String): String = LocalCardTitle.current.let { if (it.isEmpty()) label else "$it, $label" }

@Composable
private fun CardLabel(label: String) {
    SocialText(
        label,
        12.sp,
        Modifier.width(CARD_LABEL_WIDTH.dp),
        SocialTextSecondary,
        FontWeight.Light,
        TextAlign.Start,
    )
}

@Composable
private fun CardSlider(label: String, progress: Float, value: String, interactive: Boolean = true, onProgress: (Float) -> Unit) {
    Row(Modifier.height(CARD_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        CardLabel(label)
        Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
        OnboardingSlider(progress, CARD_SLIDER_WIDTH, enabled = interactive, label = cardSpeech(label), value = value, onProgress = onProgress)
        Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
        SliderValueBox(value, CARD_VALUE_WIDTH.dp, CARD_ROW_HEIGHT.dp, 12.sp)
    }
}

@Composable
private fun CardChip(label: String, spoken: String, selected: Boolean, interactive: Boolean = true, onClick: () -> Unit) {
    val shape = ppShape(6.dp)
    Box(
        Modifier
            .size(CARD_CHIP_WIDTH.dp, CARD_ROW_HEIGHT.dp)
            .clip(shape)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
            .border(SocialPanelBorderWidth, if (selected) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, shape)
            .then(if (interactive) Modifier.clickableWithSound(spoken, A11yRole.Option, selected, shape = shape) { if (!selected) onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(label, 12.sp, color = SocialTextPrimary, fontWeight = if (selected) FontWeight.Medium else FontWeight.Light, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CardToggle(label: String, enabled: Boolean, interactive: Boolean = true, onEnabled: (Boolean) -> Unit) {
    Row(Modifier.height(CARD_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        CardLabel(label)
        Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
        val spoken = cardSpeech(label)
        CardChip("On", "$spoken: On", enabled, interactive) { onEnabled(true) }
        Spacer(Modifier.width(CARD_CHIP_GAP.dp))
        CardChip("Off", "$spoken: Off", !enabled, interactive) { onEnabled(false) }
    }
}

/**
 * A dropdown for modes that are a list rather than a yes/no. Controls sit at the foot of the card,
 * so the menu opens upward. It reserves the open menu's height up front and pins the closed row to
 * the bottom of that box: the row never moves, and the menu stays inside its parent's bounds, which
 * a negative offset would not — Compose does not hit-test children drawn outside their parent.
 */
@Composable
private fun CardDropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shape = ppShape(6.dp)
    val menuHeight = options.size * CARD_MENU_ITEM_HEIGHT + CARD_MENU_PADDING * 2f
    val trigger = remember { FocusRequester() }
    val chosen = remember { FocusRequester() }
    val spoken = cardSpeech(label)
    DisposableEffect(expanded) {
        val close: () -> Unit = {
            expanded = false
            trigger.requestFocus()
        }
        if (expanded) OnboardingKeys.dismiss = close
        onDispose { if (OnboardingKeys.dismiss === close) OnboardingKeys.dismiss = null }
    }
    LaunchedEffect(expanded) {
        if (expanded && A11y.keyboardMode) chosen.requestFocus()
    }
    Box(Modifier.width(CARD_CONTENT_WIDTH.dp).height((menuHeight + CARD_MENU_GAP + CARD_ROW_HEIGHT).dp)) {
        if (expanded) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(CARD_DROPDOWN_WIDTH.dp, menuHeight.dp)
                    .shadow(10.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
                    .clip(shape)
                    .background(MenuBackground)
                    .border(SocialPanelBorderWidth, SocialPanelBorderBrush, shape)
                    .padding(vertical = CARD_MENU_PADDING.dp),
            ) {
                options.forEachIndexed { index, option ->
                    CardMenuItem(option, index == selected, if (index == selected) chosen else null) {
                        if (index != selected) onSelect(index)
                        expanded = false
                        if (A11y.keyboardMode) trigger.requestFocus()
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).height(CARD_ROW_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel(label)
            Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
            Row(
                Modifier
                    .size(CARD_DROPDOWN_WIDTH.dp, CARD_ROW_HEIGHT.dp)
                    .clip(shape)
                    .background(if (expanded) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
                    .border(SocialPanelBorderWidth, if (expanded) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, shape)
                    .focusRequester(trigger)
                    .clickableWithSound(
                        "$spoken: ${options.getOrElse(selected) { options.first() }}, " + if (expanded) "expanded" else "collapsed",
                        shape = shape,
                    ) { expanded = !expanded }
                    .padding(horizontal = CARD_MENU_INSET.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SocialText(
                    options.getOrElse(selected) { options.first() },
                    12.sp,
                    Modifier.weight(1f),
                    SocialTextPrimary,
                    FontWeight.Medium,
                    TextAlign.Start,
                )
                Icon(ONBOARDING_ASSETS + "chevron-selector.svg", SocialTextSecondary, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun CardMenuItem(label: String, selected: Boolean, requester: FocusRequester?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(CARD_MENU_ITEM_HEIGHT.dp)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else Color.Transparent)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .clickableWithSound(label, A11yRole.Option, selected, shape = RoundedCornerShape(0.dp), onClick = onClick)
            .padding(horizontal = CARD_MENU_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialText(
            label,
            12.sp,
            Modifier.weight(1f),
            SocialTextPrimary,
            if (selected) FontWeight.Medium else FontWeight.Light,
            TextAlign.Start,
        )
        if (selected) Icon(MAIN_MENU_ASSETS + "check.svg", OneConfigTheme.accent, Modifier.size(12.dp))
    }
}

@Composable
private fun SprintPage(toggleSprint: Boolean, onToggleSprint: (Boolean) -> Unit) {
    Header("Continuing with", "Toggle Sprint")
    Column(
        Modifier.fillMaxSize().padding(top = CONTENT_TOP.dp, bottom = (PANEL_HEIGHT - CONTENT_BOTTOM).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SocialText(
            SPRINT_TEXT,
            13.sp,
            Modifier.width(460.dp),
            SocialTextSecondary,
            FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChoiceButton("Enabled", ONBOARDING_ASSETS + "zap.svg", toggleSprint, 198f, option = true) { onToggleSprint(true) }
            ChoiceButton("Disabled", ONBOARDING_ASSETS + "flash-off.svg", !toggleSprint, 198f, option = true) { onToggleSprint(false) }
        }
    }
}

@Composable
private fun ModOrderPage(alphabetical: Boolean, onAlphabetical: (Boolean) -> Unit) {
    Header("Continuing with", "Mod Sorting")
    Column(
        Modifier.fillMaxSize().padding(top = CONTENT_TOP.dp, bottom = (PANEL_HEIGHT - CONTENT_BOTTOM).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SocialText(
            MOD_ORDER_TEXT,
            13.sp,
            Modifier.width(460.dp),
            SocialTextSecondary,
            FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ModOrderCard("Recommended", alphabetical = false, selected = !alphabetical) { onAlphabetical(false) }
            ModOrderCard("Alphabetical", alphabetical = true, selected = alphabetical) { onAlphabetical(true) }
        }
    }
}

@Composable
private fun ModOrderCard(label: String, alphabetical: Boolean, selected: Boolean, onClick: () -> Unit) {
    val mods = remember(alphabetical) {
        DefaultModOrder.preview(alphabetical).take(MOD_ORDER_PREVIEW_COLUMNS * MOD_ORDER_PREVIEW_ROWS)
    }
    Box(
        Modifier.width(MOD_ORDER_CARD_WIDTH.dp).clip(ButtonShape)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
            .border(SocialPanelBorderWidth, if (selected) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, ButtonShape),
    ) {
        Column(
            Modifier.padding(MOD_ORDER_CARD_PADDING.dp, MOD_ORDER_CARD_PADDING.dp, MOD_ORDER_CARD_PADDING.dp, 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .layout { measurable, _ ->
                        val grid = measurable.measure(Constraints())
                        layout(
                            (grid.width * MOD_ORDER_PREVIEW_SCALE).roundToInt(),
                            (grid.height * MOD_ORDER_PREVIEW_SCALE).roundToInt(),
                        ) {
                            grid.placeWithLayer(0, 0) {
                                scaleX = MOD_ORDER_PREVIEW_SCALE
                                scaleY = MOD_ORDER_PREVIEW_SCALE
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }
                    .size(
                        (MOD_GRID_CARD_WIDTH * MOD_ORDER_PREVIEW_COLUMNS + MOD_GRID_GAP * (MOD_ORDER_PREVIEW_COLUMNS - 1)).dp,
                        (MOD_GRID_CARD_HEIGHT * MOD_ORDER_PREVIEW_ROWS + MOD_GRID_GAP * (MOD_ORDER_PREVIEW_ROWS - 1)).dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(MOD_GRID_GAP.dp),
            ) {
                mods.chunked(MOD_ORDER_PREVIEW_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MOD_GRID_GAP.dp)) {
                        row.forEach { OneConfigModCard(it, Modifier.width(MOD_GRID_CARD_WIDTH.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SocialText(label, 14.sp, color = SocialTextPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Box(Modifier.matchParentSize().clickableWithSound("$label order", A11yRole.Option, selected, shape = ButtonShape, onClick = onClick))
    }
}

@Composable
private fun BetterGrassCard(mode: Int, onMode: (Int) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.GRASS),
        MAIN_MENU_ASSETS + "minecraft-block.svg",
        "Carry grass down the sides of blocks so slopes and terraces stop showing bare dirt.",
        { BetterGrassPreview(mode) },
    ) {
        CardDropdown("Mode", listOf("Off", "Fastest", "Fast", "Fancy"), mode, onMode)
    }
}

@Composable
private fun FireOverlayCard(
    height: Double,
    onHeight: (Double) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
) {
    ModCardFrame(
        modCardTitle(ModCard.FIRE_OVERLAY),
        ONBOARDING_ASSETS + "zap.svg",
        if (OnboardingFeatures.fireOverlayOpacityAvailable) {
            "Push the flames down the screen and fade them so burning does not blind you."
        } else {
            "Push the flames down the screen so burning does not block your view."
        },
        { FireOverlayPreview(height, opacity) },
    ) {
        val span = OnboardingFeatures.FIRE_OVERLAY_MAX - OnboardingFeatures.FIRE_OVERLAY_MIN
        CardSlider(
            "Height",
            ((height - OnboardingFeatures.FIRE_OVERLAY_MIN) / span).toFloat(),
            fireHeightLabel(height.toFloat()),
        ) { onHeight(OnboardingFeatures.FIRE_OVERLAY_MIN + it * span) }
        if (OnboardingFeatures.fireOverlayOpacityAvailable) {
            val opacitySpan = OnboardingFeatures.FIRE_OPACITY_MAX - OnboardingFeatures.FIRE_OPACITY_MIN
            CardSlider(
                "Fade",
                (opacity - OnboardingFeatures.FIRE_OPACITY_MIN) / opacitySpan,
                if (opacity.roundToInt() == OnboardingFeatures.FIRE_OPACITY_VANILLA.toInt()) "Vanilla" else "%.0f%%".fmt(opacity),
            ) { onOpacity(OnboardingFeatures.FIRE_OPACITY_MIN + it * opacitySpan) }
        }
    }
}

@Composable
private fun ShieldHeightCard(height: Float, onHeight: (Float) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.SHIELD_HEIGHT),
        MAIN_MENU_ASSETS + "key-01.svg",
        "Drop the raised shield down so it stops covering what you are looking at.",
        { ShieldPreview(height) },
    ) {
        val span = OnboardingFeatures.SHIELD_HEIGHT_MAX - OnboardingFeatures.SHIELD_HEIGHT_MIN
        CardSlider(
            "Height",
            (height - OnboardingFeatures.SHIELD_HEIGHT_MIN) / span,
            fireHeightLabel(height),
        ) { onHeight(OnboardingFeatures.SHIELD_HEIGHT_MIN + it * span) }
    }
}

@Composable
private fun MountOpacityCard(opacity: Float, onOpacity: (Float) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.MOUNT),
        ONBOARDING_ASSETS + "compass.svg",
        "Fade every mount you ride together so its head stays out of your way.",
        { HorsePreview(opacity) },
    ) {
        val span = OnboardingFeatures.HORSE_OPACITY_MAX - OnboardingFeatures.HORSE_OPACITY_MIN
        CardSlider(
            "Mounts",
            (opacity - OnboardingFeatures.HORSE_OPACITY_MIN) / span,
            "%.0f%%".fmt(opacity),
        ) { onOpacity(OnboardingFeatures.HORSE_OPACITY_MIN + it * span) }
    }
}

@Composable
private fun WaveyCapesCard(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.CAPES),
        MAIN_MENU_ASSETS + "user-01.svg",
        "Simulate your cape so it swings and settles as you move instead of staying a flat board.",
        { CapePreview(enabled) },
    ) {
        CardToggle("Waving", enabled, onEnabled = onEnabled)
    }
}

@Composable
private fun SkinLayersCard(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.SKIN_LAYERS),
        MAIN_MENU_ASSETS + "image-01.svg",
        "Lift every outer layer of your skin off the model at once so hats and jackets have depth.",
        { SkinLayersPreview(enabled) },
    ) {
        CardToggle("Depth", enabled, onEnabled = onEnabled)
    }
}

@Composable
private fun DynamicLightsCard(mode: Int, onMode: (Int) -> Unit) {
    ModCardFrame(
        modCardTitle(ModCard.DYNAMIC_LIGHTS),
        MAIN_MENU_ASSETS + "moon-star.svg",
        "Light up the area around held torches and glowing entities. Costs frame rate in busy scenes.",
        { DynamicLightsPreview(mode) },
    ) {
        CardDropdown("Mode", listOf("Off", "Fastest", "Fast", "Fancy"), mode, onMode)
    }
}

@Composable
private fun FullbrightCard(
    gamma: Float,
    onGamma: (Float) -> Unit,
    toggled: Float,
    onToggled: (Float) -> Unit,
    smooth: Boolean,
    onSmooth: (Boolean) -> Unit,
) {
    ModCardFrame(
        modCardTitle(ModCard.GAMMA),
        "assets/polyplus/ico/stars.svg",
        "Raise the brightness past vanilla's limit so caves and night stop hiding what is in front of you.",
        { GammaPreview(gamma) },
    ) {
        val span = OnboardingFeatures.GAMMA_MAX - OnboardingFeatures.GAMMA_MIN
        CardSlider("Brightness", (gamma - OnboardingFeatures.GAMMA_MIN) / span, "%.0f%%".fmt(gamma)) {
            onGamma(OnboardingFeatures.GAMMA_MIN + it * span)
        }
        CardSlider("Toggles to", (toggled - OnboardingFeatures.GAMMA_MIN) / span, "%.0f%%".fmt(toggled)) {
            onToggled(OnboardingFeatures.GAMMA_MIN + it * span)
        }
        CardToggle("Smooth", smooth, onEnabled = onSmooth)
        Row(Modifier.height(CARD_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Toggle key")
            Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
            ToggleKeyButton()
        }
    }
}

@Composable
private fun BlockHighlightPage(
    json: String,
    base: Int,
    presets: Map<String, String>,
    onEdit: (String) -> Unit,
    onBase: (Int) -> Unit,
) {
    Header("Continuing with", "Block Highlight")
    val draft = remember(json) { BlockHighlightDraft(json) }
    val names = BlockHighlightPresets.NAMES
    val edited = presets[names[base]]?.let { !draft.matches(it) } ?: true
    fun change(edit: BlockHighlightDraft.() -> Unit) = onEdit(BlockHighlightDraft(json).apply(edit).toJson())
    fun pick(index: Int) {
        onBase(index)
        presets[names[index]]?.let(onEdit)
    }
    Row(
        Modifier.offset(CARD_MARGIN.dp, EDITOR_TOP.dp),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
    ) {
        BlockHighlightPreview(json, names[base], EDITOR_PREVIEW_WIDTH, EDITOR_HEIGHT)
        Column(Modifier.width(CARD_CONTENT_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(CARD_ROW_GAP.dp)) {
            CardStepper(
                "Start from",
                if (edited) "Custom" else BlockHighlightPresets.LABELS[base],
                onPrevious = { pick((base - 1).mod(names.size)) },
                onNext = { pick((base + 1).mod(names.size)) },
            )
            SwatchRow(
                "Outline", draft.outlineStart, draft.outlineEnd, !draft.outlineRainbow,
                { change { outlineStart = it } }, { change { outlineEnd = it } },
            )
            CardSlider("Opacity", draft.outlineOpacity, "%.0f%%".fmt(draft.outlineOpacity * 100f)) {
                change { outlineOpacity = it }
            }
            val widthSpan = CBH_WIDTH_MAX - CBH_WIDTH_MIN
            CardSlider("Width", (draft.outlineWidth - CBH_WIDTH_MIN) / widthSpan, "%.1f".fmt(draft.outlineWidth)) {
                change { outlineWidth = ((CBH_WIDTH_MIN + it * widthSpan) * 2f).roundToInt() / 2f }
            }
            CardToggle("Rainbow", draft.outlineRainbow) { change { outlineRainbow = it } }
            CardToggle("See-through", draft.seeThrough) { change { seeThrough = it } }
            CardToggle("Fill", draft.fill) { change { fill = it } }
            Column(
                Modifier.graphicsLayer { alpha = if (draft.fill) 1f else DISABLED_ALPHA },
                verticalArrangement = Arrangement.spacedBy(CARD_ROW_GAP.dp),
            ) {
                SwatchRow(
                    "Fill colour", draft.fillStart, draft.fillEnd, !draft.fillRainbow,
                    { change { fillStart = it } }, { change { fillEnd = it } }, draft.fill,
                )
                CardSlider("Fill opacity", draft.fillOpacity, "%.0f%%".fmt(draft.fillOpacity * 100f), draft.fill) {
                    change { fillOpacity = it }
                }
                CardToggle("Fill rainbow", draft.fillRainbow, draft.fill) { change { fillRainbow = it } }
            }
            CardToggle("Animations", draft.animations) { change { animations = it } }
        }
    }
}

@Composable
private fun CardStepper(label: String, text: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    val shape = ppShape(6.dp)
    var spoken by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != spoken) A11y.say("$label: $text", true)
        spoken = text
    }
    Row(Modifier.height(CARD_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        CardLabel(label)
        Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
        Row(
            Modifier
                .size(CARD_DROPDOWN_WIDTH.dp, CARD_ROW_HEIGHT.dp)
                .clip(shape)
                .background(ChoiceBackground)
                .border(SocialPanelBorderWidth, SocialPanelBorderBrush, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperArrow("$label: $text, previous", back = true, onPrevious)
            SocialText(text, 12.sp, Modifier.weight(1f), SocialTextPrimary, FontWeight.Medium, TextAlign.Center)
            StepperArrow("$label: $text, next", back = false, onNext)
        }
    }
}

@Composable
private fun StepperArrow(label: String, back: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(CARD_ROW_HEIGHT.dp).clickableWithSound(label, shape = ppShape(6.dp), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MAIN_MENU_ASSETS + "chevron-right.svg",
            SocialTextSecondary,
            Modifier.size(13.dp).then(if (back) Modifier.rotate(180f) else Modifier),
        )
    }
}

@Composable
private fun SwatchRow(
    label: String,
    start: Int,
    end: Int,
    enabled: Boolean,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    interactive: Boolean = true,
) {
    val clickable = enabled && interactive
    var editingEnd by remember { mutableStateOf(false) }
    Row(
        Modifier.height(CARD_ROW_HEIGHT.dp).graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardLabel(label)
        Spacer(Modifier.width(CARD_ROW_GUTTER.dp))
        Swatch(start, "$label start colour: ${colourName(start)}", selected = !editingEnd, clickable) { editingEnd = false }
        Spacer(Modifier.width(SWATCH_GAP.dp))
        Swatch(end, "$label end colour: ${colourName(end)}", selected = editingEnd, clickable) { editingEnd = true }
        Spacer(Modifier.width(SWATCH_GROUP_GAP.dp))
        SWATCH_PALETTE.forEachIndexed { index, rgb ->
            if (index > 0) Spacer(Modifier.width(SWATCH_GAP.dp))
            Swatch(rgb, "Set ${if (editingEnd) "end" else "start"} colour to ${colourName(rgb)}", selected = null, clickable) {
                if (editingEnd) onEnd(rgb) else onStart(rgb)
            }
        }
    }
}

@Composable
private fun Swatch(rgb: Int, label: String, selected: Boolean?, enabled: Boolean, onClick: () -> Unit) {
    val shape = ppShape(4.dp)
    val marked = selected == true
    Box(
        Modifier
            .size(SWATCH_SIZE.dp)
            .clip(shape)
            .background(Color(0xFF000000.toInt() or rgb))
            .border(if (marked) 2.dp else SocialPanelBorderWidth, if (marked) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, shape)
            .then(
                if (enabled) {
                    Modifier.clickableWithSound(label, if (selected == null) A11yRole.Button else A11yRole.Option, selected, shape = shape, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    )
}

private fun colourName(rgb: Int): String = when (rgb and 0xFFFFFF) {
    0x000000 -> "black"
    0xFFFFFF -> "white"
    0xE5484D -> "red"
    0xF5A524 -> "orange"
    0xF5E142 -> "yellow"
    0x46C35A -> "green"
    0x5BCEFA -> "light blue"
    0x9B6BF2 -> "purple"
    else -> "#%06X".fmt(rgb and 0xFFFFFF)
}

@Composable
private fun ToggleKeyButton() {
    var capturing by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(OnboardingFeatures.gammaToggleKeyLabel() ?: "Unbound") }
    DisposableEffect(Unit) {
        onDispose { OnboardingKeyCapture.pending = null }
    }
    val shape = ppShape(6.dp)
    Box(
        Modifier.size(CARD_CHIP_WIDTH.dp, CARD_ROW_HEIGHT.dp).clip(shape)
            .background(if (capturing) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
            .border(SocialPanelBorderWidth, if (capturing) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, shape)
            .clickableWithSound(
                cardSpeech("Toggle key") + ": " + if (capturing) "press a key, or Escape to cancel" else label,
                shape = shape,
            ) {
                if (capturing) return@clickableWithSound
                capturing = true
                A11y.say("Press a key to bind, or Escape to cancel", true)
                OnboardingKeyCapture.pending = { key ->
                    capturing = false
                    OnboardingKeyCapture.pending = null
                    if (key != null && OnboardingFeatures.bindGammaToggleKey(key)) {
                        label = OnboardingFeatures.gammaToggleKeyLabel() ?: "Unbound"
                    }
                    A11y.say("Toggle key: $label", true)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SocialText(
            if (capturing) "Press a key" else label,
            12.sp,
            color = SocialTextPrimary,
            fontWeight = if (capturing) FontWeight.Medium else FontWeight.Light,
            textAlign = TextAlign.Center,
        )
    }
}

private fun String.fmt(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)

private fun Float.clampGamma(): Float =
    coerceIn(OnboardingFeatures.GAMMA_MIN, OnboardingFeatures.GAMMA_MAX)

private fun fireHeightLabel(height: Float): String =
    if (height >= -0.001f) "Vanilla" else "%.2f".fmt(height)

@Composable
internal fun OnboardingSlider(
    progress: Float,
    width: Float,
    thumbSize: Dp = 11.dp,
    trackHeight: Dp = 6.dp,
    enabled: Boolean = true,
    label: String? = null,
    value: String? = null,
    keyStep: Float = SLIDER_KEY_STEP,
    onRelease: () -> Unit = {},
    onProgress: (Float) -> Unit,
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentProgress by rememberUpdatedState(progress)
    val spokenValue by rememberUpdatedState(value)
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = spring())
    val trackShape = ppShape(trackHeight / 2)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (focused && A11y.keyboardMode) value?.let { A11y.say(it, true) }
    }
    Box(
        Modifier
            .width(width.dp)
            .height(thumbSize)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .then(
                if (!enabled || label == null) {
                    Modifier
                } else {
                    Modifier
                        .onFocusChanged {
                            if (it.isFocused == focused) return@onFocusChanged
                            focused = it.isFocused
                            if (focused && A11y.keyboardMode) {
                                A11y.say(listOfNotNull(label, spokenValue, "slider").joinToString(", "), true)
                            }
                        }
                        .focusRing(ppShape(thumbSize / 2))
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val target = when (event.key) {
                                Key.DirectionLeft -> currentProgress - keyStep
                                Key.DirectionRight -> currentProgress + keyStep
                                Key.MoveHome -> 0f
                                Key.MoveEnd -> 1f
                                else -> return@onKeyEvent false
                            }
                            currentOnProgress(target.coerceIn(0f, 1f))
                            currentOnRelease()
                            true
                        }
                        .focusable()
                },
            )
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        val thumbPx = thumbSize.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun update(x: Float) {
                                val usable = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                                currentOnProgress(((x - thumbPx / 2f) / usable).coerceIn(0f, 1f))
                            }
                            update(down.position.x)
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                update(change.position.x)
                                change.consume()
                            } while (change.pressed)
                            currentOnRelease()
                        }
                    }
                },
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(trackShape)
                .background(ChoiceBackground)
                .border(1.dp, SocialPanelBorderBrush, trackShape),
        ) {
            Box(Modifier.fillMaxWidth(animated).height(trackHeight).background(OneConfigTheme.accent))
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        (animated * (trackWidthPx - thumbSize.toPx())).roundToInt(),
                        0,
                    )
                }
                .size(thumbSize)
                .clip(ppShape(thumbSize / 2))
                .background(SocialTextPrimary),
        )
    }
}

@Composable
private fun SliderValueBox(
    value: String,
    width: Dp,
    height: Dp = 24.dp,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Light,
    alignment: Alignment = Alignment.Center,
    textPadding: Dp = 0.dp,
) {
    Box(
        Modifier.width(width).height(height).clip(ppShape(6.dp)).background(ChoiceBackground)
            .border(1.dp, SocialPanelBorderBrush, ppShape(6.dp)),
        contentAlignment = alignment,
    ) {
        SocialText(
            value,
            fontSize,
            Modifier.padding(start = textPadding),
            color = SocialTextPrimary,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OptimizingPage(showPickManually: Boolean, onPickManually: () -> Unit) {
    Header("One choice left:", "Motion Blur")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val transition = rememberInfiniteTransition(label = "optimizing")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "spin",
        )
        Icon(MAIN_MENU_ASSETS + "loading-02.svg", OneConfigTheme.accent, Modifier.size(40.dp).rotate(angle))
        Spacer(Modifier.height(18.dp))
        SocialText("Checking your frame rate… (about 6 seconds)", 16.sp, Modifier.width(PANEL_WIDTH.dp), SocialTextPrimary, FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        SocialText(
            "Measuring your frame rate to pick the best motion blur settings.",
            13.sp,
            Modifier.width(460.dp),
            SocialTextSecondary,
            FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        if (showPickManually) {
            Spacer(Modifier.height(18.dp))
            ChoiceButton(
                "Pick manually",
                ONBOARDING_ASSETS + "chevron-selector.svg",
                false,
                150f,
                onClick = onPickManually,
            )
        }
    }
}

@Composable
private fun MotionBlurPage(
    blurMode: Int,
    onBlurMode: (Int) -> Unit,
    motionBlur: Int,
    onMotionBlur: (Int) -> Unit,
    showRecommendation: Boolean,
) {
    Header("One choice left:", "Motion Blur")

    val recommended = if (showRecommendation && AdaptiveBlurDefaults.sampled) {
        AdaptiveBlurDefaults.recommendedMode
    } else {
        OnboardingFeatures.MOTION_BLUR_UNSET
    }
    Row(
        Modifier.offset(BLUR_CARDS_X.dp, BLUR_CARDS_Y.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        MotionBlurModeCard(
            "Disabled",
            MAIN_MENU_ASSETS + "x-close.svg",
            "No motion blur at all. You get the most performance with this option.",
            ImpactGood,
            300,
            blurMode == OnboardingFeatures.MOTION_BLUR_DISABLED,
            recommended == OnboardingFeatures.MOTION_BLUR_DISABLED,
        ) { onBlurMode(OnboardingFeatures.MOTION_BLUR_DISABLED) }
        MotionBlurModeCard(
            "Performance",
            ONBOARDING_ASSETS + "zap.svg",
            "Motion blur with no hand blur option and less samples per frame for the motion blur.",
            ImpactWarn,
            225,
            blurMode == OnboardingFeatures.MOTION_BLUR_PERFORMANCE,
            recommended == OnboardingFeatures.MOTION_BLUR_PERFORMANCE,
        ) { onBlurMode(OnboardingFeatures.MOTION_BLUR_PERFORMANCE) }
        MotionBlurModeCard(
            "Quality",
            "assets/polyplus/ico/stars.svg",
            "Best for 200+ FPS. Heaviest on performance.",
            ImpactHeavy,
            200,
            blurMode == OnboardingFeatures.MOTION_BLUR_QUALITY,
            recommended == OnboardingFeatures.MOTION_BLUR_QUALITY,
        ) { onBlurMode(OnboardingFeatures.MOTION_BLUR_QUALITY) }
    }

    val blurOff = blurMode != OnboardingFeatures.MOTION_BLUR_PERFORMANCE &&
        blurMode != OnboardingFeatures.MOTION_BLUR_QUALITY
    SectionLabel("Blur Strength", BLUR_STRENGTH_LABEL_Y)
    BlurStrengthSlider(motionBlur, blurOff, onMotionBlur)
    MotionBlurPreview(
        if (blurOff) 0 else motionBlur,
        Modifier.offset(233.5.dp, BLUR_PREVIEW_Y.dp).size(413.dp, BLUR_PREVIEW_HEIGHT.dp)
            .graphicsLayer { alpha = if (blurOff) 0.4f else 1f },
    )
}

@Composable
private fun BlurStrengthSlider(motionBlur: Int, disabled: Boolean, onMotionBlur: (Int) -> Unit) {
    val steps = MOTION_BLUR_MAX - MOTION_BLUR_MIN
    Row(
        Modifier.offset(232.dp, BLUR_SLIDER_Y.dp).graphicsLayer { alpha = if (disabled) 0.4f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingSlider(
            progress = ((motionBlur - MOTION_BLUR_MIN).toFloat() / steps).coerceIn(0f, 1f),
            width = 332f,
            thumbSize = WIDE_SLIDER_THUMB,
            trackHeight = WIDE_SLIDER_TRACK,
            enabled = !disabled,
            label = "Blur Strength",
            value = motionBlur.toString(),
            keyStep = 1f / steps,
        ) { fraction -> onMotionBlur(MOTION_BLUR_MIN + (fraction * steps).roundToInt()) }
        Spacer(Modifier.width(18.dp))
        SliderValueBox(
            if (disabled) "Off" else motionBlur.toString(),
            width = 64.dp,
            height = 26.dp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            alignment = Alignment.CenterStart,
            textPadding = 8.dp,
        )
    }
}

@Composable
private fun MotionBlurModeCard(
    title: String,
    icon: String,
    description: String,
    impactColor: Color,
    exampleFps: Int,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(MODE_CARD_WIDTH.dp, MODE_CARD_HEIGHT.dp)
            .clip(ButtonShape)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
            .border(SocialPanelBorderWidth, if (selected) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, ButtonShape)
            .clickableWithSound(
                "$title${if (recommended) ", recommended" else ""}. $description",
                A11yRole.Option,
                selected,
                shape = ButtonShape,
                onClick = onClick,
            ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = MODE_CARD_FOOTER.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, if (selected) OneConfigTheme.accent else SocialTextPrimary, Modifier.size(18.dp))
                SocialText(title, 15.sp, color = SocialTextPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(9.dp))
            SocialText(
                description,
                12.sp,
                Modifier.width((MODE_CARD_WIDTH - 32f).dp).heightIn(min = MODE_CARD_DESC_HEIGHT.dp),
                SocialTextSecondary,
                FontWeight.Light,
                TextAlign.Start,
            )
            Spacer(Modifier.weight(1f))
            FpsHud(exampleFps, impactColor, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
        }
        if (recommended) {
            SocialText(
                "Recommended",
                11.sp,
                Modifier.align(Alignment.BottomCenter).padding(14.dp),
                OneConfigTheme.accent,
                FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FpsHud(fps: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(ppShape(HUD_RADIUS.dp))
            .background(HudBackground)
            .padding(HUD_PADDING.dp),
    ) {
        val style = TextStyle(fontSize = HUD_TEXT_SIZE.sp, fontFamily = MinecraftFontFamily)
        BasicText("FPS: ", style = style.copy(color = SocialTextPrimary))
        BasicText(fps.toString(), style = style.copy(color = color))
    }
}

private val MinecraftFontFamily = FontFamily(Font("assets/oneconfig/fonts/minecraft/Minecraft-Regular.otf"))

@Composable
private fun DonePage() {
    Icon(ONBOARDING_ASSETS + "check-verified.svg", SocialTextPrimary, Modifier.offset(374.75.dp, 157.dp).size(130.5.dp))
    SocialText("All Done!", 32.sp, Modifier.offset(0.dp, 311.dp).width(PANEL_WIDTH.dp), textAlign = TextAlign.Center)
    SocialText(
        DONE_TEXT,
        15.sp,
        Modifier.offset(225.dp, 382.dp).width(430.dp),
        SocialTextPrimary,
        FontWeight.Light,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GuidePage(guide: ModGuide) {
    Header("Where to change this later", guide.feature)
    SocialText(guide.route, 15.sp, Modifier.offset(GUIDE_MARGIN.dp, GUIDE_TEXT_Y.dp).width(GUIDE_WIDTH.dp), SocialTextPrimary, FontWeight.Light)
    SocialText(guide.options, 13.sp, Modifier.offset(GUIDE_MARGIN.dp, (GUIDE_TEXT_Y + 24f).dp).width(GUIDE_WIDTH.dp), SocialTextSecondary, FontWeight.Light)
    GuideShot(guide.path)
}

@Composable
private fun GuideShot(path: String) {
    val shot = remember(path) { loadOnboardingImage(path) } ?: return
    val fit = minOf(GUIDE_WIDTH / shot.width, GUIDE_HEIGHT / shot.height)
    val shape = ppShape(6.dp)
    Box(
        Modifier.offset(0.dp, GUIDE_SHOT_Y.dp).width(PANEL_WIDTH.dp).height(GUIDE_HEIGHT.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .size((shot.width * fit).dp, (shot.height * fit).dp)
                .clip(shape)
                .border(1.dp, SocialPanelBorderBrush, shape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCover(shot, size.width, size.height)
            }
        }
    }
}

@Composable
private fun Header(kicker: String, title: String) {
    val width = LocalPanelWidth.current
    SocialText(kicker, 15.sp, Modifier.offset(0.dp, 35.dp).width(width.dp), SocialTextPrimary, FontWeight.Normal, textAlign = TextAlign.Center)
    SocialText(title, 32.sp, Modifier.offset(0.dp, 66.dp).width(width.dp), SocialTextPrimary, FontWeight.Normal, textAlign = TextAlign.Center)
}

@Composable
private fun LockupHeader() {
    val width = LocalPanelWidth.current
    val ink = SocialTextPrimary.toArgb()
    SideEffect { StartupOverlay.cardInk = ink }
    SocialText("Welcome to", 15.sp, Modifier.offset(0.dp, 28.dp).width(width.dp), SocialTextPrimary, FontWeight.Normal, textAlign = TextAlign.Center)
    Row(
        Modifier.offset(0.dp, 56.dp).width(width.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.onGloballyPositioned { StartupOverlay.cardMark = StartupOverlay.place(it) }) { LobbyIcon("logo", size = 36.dp) }
        Box(Modifier.onGloballyPositioned { StartupOverlay.cardWord = StartupOverlay.place(it) }) {
            SocialText("ONECLIENT", 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.2.sp, fontFamily = Outfit, maxLines = 1)
        }
    }
}

@Composable
private fun AccessibilityPage(onLeave: () -> Unit) {
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) {
        StartupAccessibility.attach { leave() }
        onDispose { StartupAccessibility.detach() }
    }
    val panelWidth = LocalPanelWidth.current
    val prompt = StartupAccessibility.promptText()
    val settings = remember { accessibilitySettings() }
    val rows = remember(settings) {
        if (settings.size % 2 == 1) listOf(settings.take(1)) + settings.drop(1).chunked(2) else settings.chunked(2)
    }
    val rowWidth = panelWidth - ACCESSIBILITY_MARGIN * 2f
    LockupHeader()
    if (prompt.isNotEmpty()) {
        SocialText(
            prompt,
            14.sp,
            Modifier.offset(ACCESSIBILITY_MARGIN.dp, ACCESSIBILITY_TEXT_Y.dp).width(rowWidth.dp),
            SocialTextSecondary,
            FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
    Column(Modifier.offset(ACCESSIBILITY_MARGIN.dp, ACCESSIBILITY_ROWS_Y.dp), verticalArrangement = Arrangement.spacedBy(ACCESSIBILITY_GAP.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ACCESSIBILITY_GAP.dp)) {
                row.forEach { control ->
                    ChoiceButton(
                        StartupAccessibility.label(control),
                        accessibilityIcon(control),
                        StartupAccessibility.selected(control),
                        (rowWidth - ACCESSIBILITY_GAP * (row.size - 1)) / row.size,
                        enabled = control != StartupAccessibility.Control.Narrator || StartupAccessibility.narratorAvailable(),
                        focused = StartupAccessibility.focused(control),
                    ) { StartupAccessibility.activate(control) }
                }
            }
        }
    }
    StandardStartupLink(Modifier.offset(0.dp, (LocalPanelHeight.current - NAV_BOTTOM_INSET - ACCESSIBILITY_LINK_INSET).dp))
}

private fun accessibilitySettings(): List<StartupAccessibility.Control> =
    StartupAccessibility.controls().filter {
        it != StartupAccessibility.Control.Standard && it != StartupAccessibility.Control.Skip && it != StartupAccessibility.Control.Continue
    }

private fun accessibilityPanelHeight(): Float {
    val rows = (accessibilitySettings().size + 1) / 2
    return ACCESSIBILITY_PANEL_HEIGHT + (rows - ACCESSIBILITY_BASE_ROWS).coerceAtLeast(0) * (ACCESSIBILITY_ROW_HEIGHT + ACCESSIBILITY_GAP)
}

private fun panelSpec(): AnimationSpec<Float> = Motion.spec(spring())

private fun accessibilityIcon(control: StartupAccessibility.Control): String = MAIN_MENU_ASSETS + when (control) {
    StartupAccessibility.Control.Narrator -> "message-chat-circle.svg"
    StartupAccessibility.Control.Sounds -> "headphones-02.svg"
    StartupAccessibility.Control.Accessibility -> "settings-02.svg"
    StartupAccessibility.Control.ReducedMotion -> "pause-circle.svg"
    else -> "globe-01.svg"
}

@Composable
private fun StandardStartupLink(modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused = StartupAccessibility.focused(StartupAccessibility.Control.Standard)
    Box(modifier.width(LocalPanelWidth.current.dp), contentAlignment = Alignment.Center) {
        BasicText(
            StartupAccessibility.label(StartupAccessibility.Control.Standard),
            Modifier.hoverable(interactionSource).clickableTextWithSound { StartupAccessibility.activate(StartupAccessibility.Control.Standard) },
            TextStyle(
                color = if (hovered || focused) SocialTextPrimary else SocialTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                fontFamily = OneConfigTheme.current.fontFamily,
                textDecoration = if (focused) TextDecoration.Underline else TextDecoration.None,
            ),
        )
    }
}

@Composable
private fun SectionLabel(label: String, y: Float) {
    SocialText(label, 15.sp, Modifier.offset(232.dp, y.dp).width(198.dp), SocialTextPrimary, FontWeight.Normal, TextAlign.Start)
}

@Composable
private fun ChoiceButton(
    label: String,
    icon: String,
    selected: Boolean,
    width: Float,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focused: Boolean = false,
    option: Boolean = false,
    blocked: String? = null,
    onClick: () -> Unit,
) {
    val contentColor = if (primary) Color.White else SocialTextPrimary
    val shape = ButtonShape
    val role = if (option) A11yRole.Option else A11yRole.Button
    Row(
        modifier
            .width(width.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(ButtonShape)
            .background(
                when {
                    primary -> OneConfigTheme.accent
                    selected -> OneConfigTheme.accent.asSocialSelected
                    else -> ChoiceBackground
                },
            )
            .border(
                if (focused) FOCUS_BORDER_WIDTH.dp else SocialPanelBorderWidth,
                when {
                    focused -> SolidColor(SocialTextPrimary)
                    selected || primary -> SolidColor(OneConfigTheme.accent)
                    else -> SocialPanelBorderBrush
                },
                ButtonShape,
            )
            .then(
                when {
                    enabled -> Modifier.clickableWithSound(label, role, selected.takeIf { option }, shape = shape, onClick = onClick)
                    blocked != null -> Modifier.a11y("$label, unavailable. $blocked", A11yRole.Button, null, null, shape, true).clickable { A11y.say(blocked, true) }
                    else -> Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentColor, Modifier.size(17.dp))
        SocialText(label, 14.sp, color = contentColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StyleCard(label: String, selected: Boolean, rounded: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(198.dp, 155.dp).clip(ButtonShape)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else ChoiceBackground)
            .border(SocialPanelBorderWidth, if (selected) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, ButtonShape)
            .clickableWithSound("$label style", A11yRole.Option, selected, shape = ButtonShape, onClick = onClick),
    ) {
        UiPreview(Modifier.offset(13.dp, 12.dp), rounded)
        SocialText(label, 14.sp, Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), SocialTextPrimary, FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UiPreview(modifier: Modifier, rounded: Boolean) {
    val shape = if (rounded) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    Row(modifier.size(172.dp, 108.dp).clip(shape).border(1.dp, Color(0x1AFFFFFF), shape)) {
        Column(Modifier.width(44.dp).height(108.dp).background(Color(0xB3151C22)).padding(8.dp, 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(29.dp, 7.dp).background(OneConfigTheme.accent))
            repeat(3) { Box(Modifier.width(if (it == 0) 20.dp else 29.dp).height(4.dp).background(if (it == 0) SocialTextSecondary else SocialTextPrimary)) }
        }
        Column(Modifier.width(128.dp).height(108.dp).background(Color(0xF211171C)).padding(8.dp, 7.dp)) {
            Row { Box(Modifier.width(43.dp).height(7.dp).background(SocialTextPrimary)); Spacer(Modifier.width(61.dp)); Box(Modifier.size(7.dp).background(SocialTextPrimary)) }
            Spacer(Modifier.height(8.dp))
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { Box(Modifier.size(34.dp, 23.dp).background(Color(0xFF1A2229)).border(0.dp, Color.Transparent).padding(top = 17.dp).background(OneConfigTheme.accent)) }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun TextAction(label: String, textAlign: TextAlign, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    SocialText(
        label,
        13.sp,
        Modifier.hoverable(interactionSource).a11y(label, A11yRole.Button).clickableTextWithSound(onClick),
        color = if (hovered) SocialTextPrimary else SocialTextSecondary,
        fontWeight = FontWeight.Light,
        textAlign = textAlign,
    )
}

@Composable
private fun BottomNavigation(
    page: Int,
    pageCount: Int,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    allowSkip: Boolean = true,
    nextLabel: String? = null,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    hint: String? = null,
    nextBlocked: String? = null,
    skipFocused: Boolean = false,
    nextFocused: Boolean = false,
) {
    val panelWidth = LocalPanelWidth.current
    val buttonY = LocalPanelHeight.current - NAV_BOTTOM_INSET
    val nextX = panelWidth - 26f - 100f
    if (page == 0) {
        if (allowSkip) {
            ChoiceButton("Skip", MAIN_MENU_ASSETS + "x-close.svg", false, 100f, Modifier.offset(26.dp, buttonY.dp), focused = skipFocused, onClick = onSkip)
        }
    } else {
        ChoiceButton("Back", "assets/polyplus/ico/left-arrow.svg", false, 100f, Modifier.offset(26.dp, buttonY.dp), onClick = onBack)
        if (allowSkip) {
            Box(
                Modifier
                    .offset((26f + 100f + 14f).dp, buttonY.dp)
                    .size(SECONDARY_ACTION_WIDTH.dp, 32.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                TextAction("Skip setup", TextAlign.Start, onSkip)
            }
        }
    }
    if (secondaryLabel != null) {
        ChoiceButton(
            secondaryLabel,
            MAIN_MENU_ASSETS + "x-close.svg",
            false,
            100f,
            Modifier.offset((nextX - 100f - 14f).dp, buttonY.dp),
            onClick = onSecondary,
        )
    } else if (hint != null) {
        Box(
            Modifier
                .offset((nextX - HINT_WIDTH - 14f).dp, buttonY.dp)
                .size(HINT_WIDTH.dp, 32.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            SocialText(hint, 13.sp, color = SocialTextSecondary, fontWeight = FontWeight.Light, textAlign = TextAlign.End)
        }
    }
    ChoiceButton(
        nextLabel ?: if (page == pageCount - 1) "Finish" else "Next",
        "assets/polyplus/ico/right-arrow.svg",
        false,
        100f,
        Modifier.offset(nextX.dp, buttonY.dp),
        primary = true,
        enabled = nextEnabled,
        focused = nextFocused,
        blocked = nextBlocked,
        onClick = onNext,
    )
    val dotsWidth = pageCount * 17f - 5f
    val dotsX = (panelWidth - dotsWidth) / 2f
    if (secondaryLabel != null && dotsX + dotsWidth > nextX - 100f - 14f - 8f) return
    Row(
        Modifier.offset(dotsX.dp, (buttonY + 10f).dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                Modifier.size(if (index == page) 12.dp else 10.dp)
                    .clip(ppShape(8.dp))
                    .background(if (index == page) Color(0x80EBF2FF) else Color(0x73232D32))
                    .border(1.dp, if (index == page) Color(0xCCFFFFFF) else Color(0x66FFFFFF), ppShape(8.dp)),
            )
        }
    }
}

@Composable
private fun MotionBlurPreview(strength: Int, modifier: Modifier = Modifier) {
    val image = remember { loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png") }
    val motion = UnityMotionBlur.maxSmear(strength)
    val shape = ppShape(6.dp)

    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF273137))
            .border(1.dp, SocialPanelBorderBrush, shape),
    ) {
        if (image != null) {
            Canvas(Modifier.fillMaxSize()) {
                val overscan = 1.12f
                val destinationWidth = size.width * overscan
                val destinationHeight = size.height * overscan
                val destinationRatio = destinationWidth / destinationHeight
                val sourceRatio = image.width.toFloat() / image.height.toFloat()
                val sourceWidth: Int
                val sourceHeight: Int
                if (sourceRatio > destinationRatio) {
                    sourceHeight = image.height
                    sourceWidth = (sourceHeight * destinationRatio).roundToInt()
                } else {
                    sourceWidth = image.width
                    sourceHeight = (sourceWidth / destinationRatio).roundToInt()
                }
                val src = Rect(
                    Offset((image.width - sourceWidth) / 2f, (image.height - sourceHeight) / 2f),
                    Size(sourceWidth.toFloat(), sourceHeight.toFloat()),
                )
                val dst = Rect(
                    Offset((size.width - destinationWidth) / 2f, (size.height - destinationHeight) / 2f),
                    Size(destinationWidth, destinationHeight),
                )
                UnityMotionBlur.draw(this, image, src, dst, Rect(0f, 0f, size.width, size.height), motion)
            }
        }
    }
}

@Composable
private fun PreviewFrame(
    width: Float = CARD_CONTENT_WIDTH,
    height: Float = CARD_PREVIEW_HEIGHT,
    draw: DrawScope.() -> Unit,
) {
    val shape = ppShape(6.dp)
    Box(
        Modifier
            .size(width.dp, height.dp)
            .clip(shape)
            .background(PreviewBackground)
            .border(1.dp, SocialPanelBorderBrush, shape),
    ) {
        Canvas(Modifier.fillMaxSize(), draw)
    }
}

@Composable
private fun CapePreview(enabled: Boolean) {
    val frames = remember {
        (0 until CAPE_WAVE_FRAMES).map { loadOnboardingImage(WAVEY_ASSETS + "wave-$it.png") }
    }
    val still = remember { loadOnboardingImage(WAVEY_ASSETS + "off.png") }
    val shot = if (enabled) {
        val transition = rememberInfiniteTransition(label = "cape")
        val position by transition.animateFloat(
            initialValue = 0f,
            targetValue = CAPE_WAVE_FRAMES.toFloat(),
            animationSpec = infiniteRepeatable(tween(CAPE_WAVE_LOOP_MS, easing = LinearEasing)),
            label = "wave",
        )
        frames.getOrNull(position.toInt().coerceIn(0, CAPE_WAVE_FRAMES - 1))
    } else {
        still
    }
    Box {
        PreviewFrame {
            shot?.let { drawCover(it, size.width, size.height) }
        }
        FpsBadge(if (enabled) TOGGLE_ON_FPS else PREVIEW_FPS[0])
    }
}

@Composable
private fun SkinLayersPreview(enabled: Boolean) {
    val on = remember { loadOnboardingImage(SKINLAYERS_ASSETS + "on.png") }
    val off = remember { loadOnboardingImage(SKINLAYERS_ASSETS + "off.png") }
    val shot = if (enabled) on else off
    Box {
        PreviewFrame {
            shot?.let { drawCover(it, size.width, size.height) }
        }
        FpsBadge(if (enabled) TOGGLE_ON_FPS else PREVIEW_FPS[0])
    }
}

@Composable
private fun DynamicLightsPreview(mode: Int) {
    val on = remember { loadOnboardingImage(DYNAMIC_LIGHTS_ASSETS + "on.png") }
    val off = remember { loadOnboardingImage(DYNAMIC_LIGHTS_ASSETS + "off.png") }
    val shot = if (mode == OnboardingFeatures.DYNAMIC_LIGHTS_OFF) off else on
    Box {
        PreviewFrame {
            shot?.let { drawCover(it, size.width, size.height) }
        }
        FpsBadge(PREVIEW_FPS.getOrElse(mode) { PREVIEW_FPS.last() })
    }
}

@Composable
private fun BoxScope.FpsBadge(tier: Pair<Int, Color>) {
    val (fps, color) = tier
    SocialText(
        "$fps FPS",
        12.sp,
        Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .clip(ppShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color,
        FontWeight.Medium,
        TextAlign.Center,
    )
}

private val PREVIEW_FPS = listOf(
    400 to Color(0xFF4ADE80),
    300 to Color(0xFFA3E635),
    200 to Color(0xFFFBBF24),
    100 to Color(0xFFF87171),
)

private val TOGGLE_ON_FPS = 200 to Color(0xFFF87171)

@Composable
private fun GammaPreview(gamma: Float) {
    val (darkPath, brightPath) = gammaPreviewPaths
    val dark = remember { loadOnboardingImage(darkPath) }
    val bright = remember { loadOnboardingImage(brightPath) }
    val span = OnboardingFeatures.GAMMA_MAX - OnboardingFeatures.GAMMA_MIN
    val fade by animateFloatAsState((gamma - OnboardingFeatures.GAMMA_MIN) / span, animationSpec = spring())
    PreviewFrame {
        dark?.let { drawCover(it, size.width, size.height) }
        bright?.let { drawCover(it, size.width, size.height, fade) }
    }
}

internal fun betterGrassPreviewPath(mode: String): String = GRASS_ASSETS + "grass-${mode.lowercase()}.png"

internal val gammaPreviewPaths: List<String> = listOf(GAMMA_ASSETS + GAMMA_DARK, GAMMA_ASSETS + GAMMA_BRIGHT)

internal val modGuidePaths: List<String> = ModGuide.entries.map { it.path }

@Composable
private fun BetterGrassPreview(mode: Int) {
    val shots = remember {
        OnboardingFeatures.BETTER_GRASS_MODES.map { loadOnboardingImage(betterGrassPreviewPath(it)) }
    }
    val shot = shots.getOrNull(mode)
    Box {
        PreviewFrame {
            shot?.let { drawCover(it, size.width, size.height) }
        }
        FpsBadge(PREVIEW_FPS.getOrElse(mode) { PREVIEW_FPS.last() })
    }
}

@Composable
private fun FireOverlayPreview(height: Double, opacity: Float) {
    val fire = remember { VanillaTextures.load(VanillaTextures.FIRE) }
    val scene = remember { loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png") }
    val shift by animateFloatAsState((-height).toFloat(), animationSpec = spring())
    val fade by animateFloatAsState(opacity / OnboardingFeatures.FIRE_OPACITY_MAX, animationSpec = spring())
    PreviewFrame {
        scene?.let { drawCover(it, size.width, size.height) }
        drawRect(SceneShade)
        val flames = fire ?: return@PreviewFrame
        val frame = Rect(0f, 0f, flames.width.toFloat(), flames.width.toFloat())
        val dy = (FIRE_BASELINE + shift) * size.height
        for (direction in intArrayOf(-1, 1)) {
            drawPixelArt(
                flames,
                Rect(Offset(direction * size.width * FIRE_QUAD_OFFSET, dy), Size(size.width, size.height)),
                frame,
                fireTint(fade),
            )
        }
    }
}

@Composable
private fun BlockHighlightPreview(json: String, block: String, width: Float, height: Float) {
    val style = remember(json) { runCatching { BlockHighlightStyle.parse(json) }.getOrNull() }
    val textures = remember(block) { BlockHighlightRenderer.faces(block) }
    var millis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        if (Motion.reduced) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) withFrameMillis { millis = System.currentTimeMillis() - start }
    }
    PreviewFrame(width, height) {
        val skia = style ?: return@PreviewFrame
        BlockHighlightRenderer.draw(
            this, size.width, size.height, skia, textures,
            System.currentTimeMillis(), millis * BLOCK_SPIN_PER_MS + BLOCK_SPIN_START,
        )
    }
}

@Composable
private fun HorsePreview(opacity: Float) {
    val scene = remember {
        loadOnboardingImage(MOUNT_ASSETS + MOUNT_SCENE) ?: loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png")
    }
    val horse = remember { loadOnboardingImage(MOUNT_ASSETS + MOUNT_FULL) }
    val fade by animateFloatAsState(
        opacity / OnboardingFeatures.HORSE_OPACITY_MAX,
        animationSpec = spring(),
    )
    PreviewFrame {
        scene?.let { drawCover(it, size.width, size.height) }
        horse?.let { drawCover(it, size.width, size.height, fade) }
    }
}

@Composable
private fun ShieldPreview(height: Float) {
    val scene = remember {
        loadOnboardingImage(SHIELD_ASSETS + SHIELD_SCENE) ?: loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png")
    }
    val shield = remember { loadOnboardingImage(SHIELD_ASSETS + SHIELD_LAYER) }
    val drop by animateFloatAsState(-height, animationSpec = spring())
    PreviewFrame {
        scene?.let { drawCover(it, size.width, size.height) }
        val plate = shield ?: return@PreviewFrame
        translate(0f, drop * SHIELD_DROP_PER_UNIT * size.height) {
            drawCover(plate, size.width, size.height)
        }
    }
}

private fun DrawScope.drawPixelArt(
    image: PolyBitmap,
    dst: Rect,
    src: Rect = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
    tint: Int? = null,
) {
    val w = image.width.toFloat()
    val h = image.height.toFloat()
    val color = PolyColor(tint ?: -1)
    drawPoly {
        it.image(image, dst.left, dst.top, dst.width, dst.height, src.left / w, src.top / h, src.right / w, src.bottom / h, color, FilterQuality.None)
    }
}

private fun DrawScope.drawCover(image: PolyBitmap, width: Float, height: Float, alpha: Float = 1f) {
    val target = width / height
    val source = image.width.toFloat() / image.height.toFloat()
    val sourceWidth: Int
    val sourceHeight: Int
    if (source > target) {
        sourceHeight = image.height
        sourceWidth = (sourceHeight * target).roundToInt()
    } else {
        sourceWidth = image.width
        sourceHeight = (sourceWidth / target).roundToInt()
    }
    val u0 = (image.width - sourceWidth) / 2f / image.width
    val v0 = (image.height - sourceHeight) / 2f / image.height
    val u1 = u0 + sourceWidth.toFloat() / image.width
    val v1 = v0 + sourceHeight.toFloat() / image.height
    val color = PolyColor(((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0xFFFFFF)
    drawPoly { it.image(image, 0f, 0f, width, height, u0, v0, u1, v1, color, FilterQuality.Low) }
}

private fun loadOnboardingImage(path: String): PolyBitmap? = runCatching {
    val bytes = PolyPlusOnboardingScreen::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
    PolyBitmap.decode(bytes)
}.getOrNull()

@Composable
private fun TermsPage(
    document: LegalDocument?,
    onDocument: (LegalDocument) -> Unit,
    top: Float,
) {
    LaunchedEffect(Unit) {
        if (document == null) LegalDocuments.load().onSuccess(onDocument)
    }

    val panelWidth = LocalPanelWidth.current
    if (top > TERMS_CONTENT_TOP) {
        if (StartupOverlay.enabled()) LockupHeader() else Header("Welcome to", "OneClient")
    }
    Box(
        Modifier
            .offset(0.dp, top.dp)
            .size(panelWidth.dp, (LocalPanelHeight.current - NAV_BOTTOM_INSET - 14f - top).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SocialText("Agree to the", 15.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
                TermsLink("Terms of Service") {
                    ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL)
                }
                SocialText("and", 15.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
                TermsLink("Privacy Policy") {
                    ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL)
                }
                SocialText("to use online features.", 15.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            }
            SocialText(
                TERMS_NOTE,
                12.sp,
                Modifier.width((panelWidth - 80f).dp),
                SocialTextSecondary,
                FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Mirrors OneConfig's CheckboxIndicator so the onboarding checkbox matches the rest of the UI
@Composable
private fun CheckBox(checked: Boolean) {
    val theme = OneConfigTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .size(24.dp)
            .hoverable(interactionSource)
            .clip(theme.checkBoxShape)
            .background(if (checked) OneConfigTheme.accent else SocialOffStateBackground)
            .border(
                1.5.dp,
                when {
                    checked -> OneConfigTheme.accent
                    hovered -> theme.textColorSecondary
                    else -> theme.borderColor
                },
                theme.checkBoxShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon("tick", theme.textColor, Modifier.size(26.dp))
    }
}

@Composable
private fun TermsLink(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.a11y(label, A11yRole.Link).clickableTextWithSound(onClick),
        TextStyle(
            color = OneConfigTheme.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OneConfigTheme.current.fontFamily,
            textDecoration = TextDecoration.Underline,
        ),
    )
}

internal object OnboardingKeyCapture {
    @Volatile
    var pending: ((InputConstants.Key?) -> Unit)? = null

    fun capture(key: Int): Boolean {
        val capture = pending ?: return false
        capture(
            if (key == InputConstants.KEY_ESCAPE) {
                null
            } else {
                //? if >= 26.3 || = 1.8.9 {
                InputConstants.Type.KEYBOARD.getOrCreate(key)
                //?} else {
                /*InputConstants.Type.KEYSYM.getOrCreate(key)
                *///?}
            },
        )
        return true
    }
}

internal object OnboardingKeys {
    private const val PAGE_SETTLE_MS = 350L

    private var page = -1
    private var pageAt = 0L
    private var next: (() -> Unit)? = null
    private var back: (() -> Unit)? = null
    private var blocked: String? = null
    var dismiss: (() -> Unit)? = null

    fun update(page: Int, next: (() -> Unit)?, back: (() -> Unit)?, blocked: String?) {
        if (page != this.page) {
            this.page = page
            pageAt = System.currentTimeMillis()
        }
        this.next = next
        this.back = back
        this.blocked = blocked
    }

    fun clear() {
        page = -1
        next = null
        back = null
        blocked = null
        dismiss = null
    }

    fun handle(key: Int): Boolean {
        if (OnboardingKeyCapture.pending != null) return false
        when (key) {
            InputConstants.KEY_ESCAPE -> (dismiss ?: back ?: return false)()
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (LobbyA11y.keyboardFocused()) return false
                if (System.currentTimeMillis() - pageAt < PAGE_SETTLE_MS) return true
                val action = next
                when {
                    action != null -> action()
                    blocked != null -> A11y.say(blocked.orEmpty(), true)
                    else -> return false
                }
            }
            else -> return false
        }
        return true
    }
}

private fun modCardTitle(card: ModCard): String = when (card) {
    ModCard.GRASS -> "Better Grass"
    ModCard.FIRE_OVERLAY -> "Fire Overlay"
    ModCard.SHIELD_HEIGHT -> "Shield Height"
    ModCard.MOUNT -> "Mount Opacity"
    ModCard.CAPES -> "Wavey Capes"
    ModCard.SKIN_LAYERS -> "3D Skin Layers"
    ModCard.DYNAMIC_LIGHTS -> "Dynamic Lights"
    ModCard.GAMMA -> "Fullbright"
    ModCard.BLOCK_HIGHLIGHT -> "Block Highlight"
}

private fun pageSpeech(step: OnboardingStep, page: Int, waiting: Boolean): String {
    val body = when (step) {
        OnboardingStep.Accessibility -> ""
        OnboardingStep.Terms ->
            "Welcome to OneClient. Agree to the Terms of Service and Privacy Policy to use online features. $TERMS_NOTE"
        OnboardingStep.LookAndFeel -> "Look and Feel. Choose the UI colors, UI style and GUI scale."
        OnboardingStep.ModOrder -> "Mod Sorting. $MOD_ORDER_TEXT"
        OnboardingStep.Sprint -> "Toggle Sprint. $SPRINT_TEXT"
        is OnboardingStep.Mods -> "Mods: " + step.cards.joinToString(" and ") { modCardTitle(it) } + "."
        OnboardingStep.BlockHighlight -> "Block Highlight. Pick a preset to start from, then adjust the outline and fill."
        OnboardingStep.MotionBlur ->
            if (waiting) {
                "Motion Blur. Checking your frame rate, about 6 seconds."
            } else {
                "Motion Blur. Choose Disabled, Performance or Quality, then set the blur strength."
            }
        is OnboardingStep.Guide ->
            "Where to change this later: ${step.guide.feature}. ${step.guide.route.replace(" → ", ", then ")}. ${step.guide.options}."
        OnboardingStep.Done -> "All Done! $DONE_TEXT"
    }
    return "Step ${page + 1}. $body"
}

private enum class ModGuide(
    val card: ModCard,
    val feature: String,
    val route: String,
    val options: String,
    val shot: String,
) {
    GRASS(
        ModCard.GRASS,
        "Better Grass",
        "Mods \u2192 LambdaBetterGrass",
        "Better Grass",
        "better-grass.png",
    ),
    FIRE(
        ModCard.FIRE_OVERLAY,
        "Fire Overlay",
        "Mods \u2192 Overlay Tweaks \u2192 HUD \u2192 Fire",
        "Fire Overlay Height and Fire Overlay Opacity",
        "overlay-fire.png",
    ),
    SHIELD(
        ModCard.SHIELD_HEIGHT,
        "Shield Height",
        "Mods \u2192 Overlay Tweaks \u2192 Items \u2192 Shields",
        "Shield Height",
        "overlay-shield.png",
    ),
    MOUNT(
        ModCard.MOUNT,
        "Mount Opacity",
        "Mods \u2192 Mount Opacity \u2192 General",
        "One slider per mount you can ride",
        "mount-opacity.png",
    ),
    BLOCK_OUTLINE(
        ModCard.BLOCK_HIGHLIGHT,
        "Custom Block Highlight",
        "Mods \u2192 Custom Block Highlight \u2192 Extras \u2192 Presets",
        "Configure the outline or choose from presets",
        "block-outline.png",
    )
    ;

    val path: String get() = GUIDE_ASSETS + shot

    companion object {
        fun of(card: ModCard): ModGuide? = entries.firstOrNull { it.card == card }
    }
}

private sealed interface OnboardingStep {
    data object Accessibility : OnboardingStep
    data object Terms : OnboardingStep
    data object LookAndFeel : OnboardingStep
    data object ModOrder : OnboardingStep
    data object Sprint : OnboardingStep

    data class Mods(val cards: List<ModCard>) : OnboardingStep

    data object BlockHighlight : OnboardingStep

    data object MotionBlur : OnboardingStep

    data class Guide(val guide: ModGuide) : OnboardingStep
    data object Done : OnboardingStep
}

private class ModReads(
    val grass: Int?,
    val fireHeight: Double?,
    val fireOpacity: Float?,
    val shield: Float?,
    val horse: Float?,
    val capes: Boolean?,
    val layers: Boolean?,
    val gamma: Float?,
    val gammaToggled: Float?,
    val gammaSmooth: Boolean?,
    val blockHighlight: String?,
    val dynamicLights: Int?,
) {
    val cards: List<ModCard> = buildList {
        if (grass != null) add(ModCard.GRASS)
        if (fireHeight != null && (!OnboardingFeatures.fireOverlayOpacityAvailable || fireOpacity != null)) {
            add(ModCard.FIRE_OVERLAY)
        }
        if (shield != null) add(ModCard.SHIELD_HEIGHT)
        if (horse != null) add(ModCard.MOUNT)
        if (capes != null) add(ModCard.CAPES)
        if (layers != null) add(ModCard.SKIN_LAYERS)
        if (gamma != null) add(ModCard.GAMMA)
        if (blockHighlight != null) add(ModCard.BLOCK_HIGHLIGHT)
        if (dynamicLights != null) add(ModCard.DYNAMIC_LIGHTS)
    }

    companion object {
        fun read(enabled: Boolean): ModReads {
            fun <T> ifAvailable(available: Boolean, read: () -> T?): T? =
                if (enabled && available) read() else null
            return ModReads(
                grass = ifAvailable(OnboardingFeatures.betterGrassAvailable) {
                    OnboardingFeatures.currentBetterGrassMode()
                },
                fireHeight = ifAvailable(OnboardingFeatures.fireOverlayAvailable) {
                    OnboardingFeatures.currentFireOverlayHeight()
                },
                fireOpacity = ifAvailable(
                    OnboardingFeatures.fireOverlayAvailable && OnboardingFeatures.fireOverlayOpacityAvailable,
                ) { OnboardingFeatures.currentFireOverlayOpacity() },
                shield = ifAvailable(OnboardingFeatures.shieldHeightAvailable) {
                    OnboardingFeatures.currentShieldHeight()
                },
                horse = ifAvailable(OnboardingFeatures.mountOpacityAvailable) {
                    OnboardingFeatures.currentHorseOpacity()
                },
                capes = ifAvailable(OnboardingFeatures.waveyCapesAvailable) {
                    OnboardingFeatures.currentWaveyCapes()
                },
                layers = ifAvailable(OnboardingFeatures.skinLayersAvailable) {
                    OnboardingFeatures.currentSkinLayers()
                },
                gamma = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) {
                    OnboardingFeatures.currentGamma()
                },
                gammaToggled = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) {
                    OnboardingFeatures.currentGammaToggled()
                },
                gammaSmooth = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) {
                    OnboardingFeatures.currentGammaSmooth()
                },
                blockHighlight = ifAvailable(BlockHighlightPresets.available) {
                    // The title screen can compose before DefaultSettings' first tick (1.8.9 reloads
                    // resources synchronously), so show the vanilla preset those tasks are about to apply.
                    runCatching {
                        if (BlockHighlightPresets.markerPath().exists() && !BlockHighlightPresets.isModDefault()) {
                            BlockHighlightPresets.currentJson()
                        } else {
                            BlockHighlightPresets.presetJson(BlockHighlightPresets.DEFAULT)
                        }
                    }.getOrNull()
                },
                dynamicLights = ifAvailable(OnboardingFeatures.dynamicLightsAvailable) {
                    OnboardingFeatures.currentDynamicLightsMode()
                },
            )
        }
    }
}

private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f
private const val UI_SCALE = DESIGN_WIDTH / 1240f
private const val PANEL_WIDTH = 880f
private const val PANEL_HEIGHT = 660f
private const val MOTION_BLUR_MIN = 1
private val WIDE_SLIDER_THUMB = 13.dp
private val WIDE_SLIDER_TRACK = 7.dp
private const val MOTION_BLUR_MAX = 10

private const val CONTENT_TOP = 140f
private const val CONTENT_BOTTOM = 557f
private const val SECTION_GAP = 24f
private const val LABEL_HEIGHT = 32f
private const val MOD_CARDS_PER_PAGE = 2
private const val MOD_ORDER_PREVIEW_ROWS = 2
private const val MOD_ORDER_PREVIEW_COLUMNS = 4
private const val MOD_ORDER_CARD_WIDTH = 380f
private const val MOD_ORDER_CARD_PADDING = 14f
private const val MOD_GRID_CARD_WIDTH = 190f
private const val MOD_GRID_CARD_HEIGHT = 140f
private const val MOD_GRID_GAP = 19f
private const val MOD_ORDER_PREVIEW_SCALE =
    (MOD_ORDER_CARD_WIDTH - MOD_ORDER_CARD_PADDING * 2f) /
        (MOD_GRID_CARD_WIDTH * MOD_ORDER_PREVIEW_COLUMNS + MOD_GRID_GAP * (MOD_ORDER_PREVIEW_COLUMNS - 1))
private const val CARD_MARGIN = 48f
private const val CARD_GAP = 24f
private const val CARD_WIDTH = (PANEL_WIDTH - CARD_MARGIN * 2f - CARD_GAP) / MOD_CARDS_PER_PAGE
private const val CARD_TOP = 134f
private const val CARD_HEIGHT = 430f
private const val CARD_PADDING = 20f
private const val CARD_CONTENT_WIDTH = CARD_WIDTH - CARD_PADDING * 2f
private const val CARD_PREVIEW_HEIGHT = 150f
private const val CARD_TITLE_GAP = 12f
private const val CARD_DESC_GAP = 12f
private const val CARD_ROW_GAP = 6f
private const val CARD_ROW_HEIGHT = 28f
private const val CARD_ROW_GUTTER = 8f
private const val CARD_LABEL_WIDTH = 94f
private const val CARD_VALUE_WIDTH = 62f
private const val CARD_SLIDER_WIDTH =
    CARD_CONTENT_WIDTH - CARD_LABEL_WIDTH - CARD_VALUE_WIDTH - CARD_ROW_GUTTER * 2f
private const val CARD_DROPDOWN_WIDTH = CARD_CONTENT_WIDTH - CARD_LABEL_WIDTH - CARD_ROW_GUTTER
private const val CARD_MENU_ITEM_HEIGHT = 26f
private const val CARD_MENU_PADDING = 5f
private const val CARD_MENU_INSET = 10f
private const val CARD_MENU_GAP = 6f
private const val CARD_CHIP_GAP = 8f
private const val CARD_CHIP_WIDTH =
    (CARD_CONTENT_WIDTH - CARD_LABEL_WIDTH - CARD_ROW_GUTTER - CARD_CHIP_GAP) / 2f

private const val CAPE_WAVE_FRAMES = 8
private const val CAPE_WAVE_LOOP_MS = 1080

private const val FIRE_QUAD_OFFSET = 0.12f
private const val FIRE_BASELINE = 0.3f
private const val FIRE_ALPHA = 0xCC
private const val SHIELD_DROP_PER_UNIT = 1.0f
private const val BLOCK_SPIN_PER_MS = 0.0004f
private const val CBH_WIDTH_MIN = 1f
private const val CBH_WIDTH_MAX = 10f
private const val SWATCH_SIZE = 20f
private const val SWATCH_GAP = 3f
private const val SWATCH_GROUP_GAP = 12f
private val SWATCH_PALETTE = listOf(
    0x000000, 0xFFFFFF, 0xE5484D, 0xF5A524, 0xF5E142, 0x46C35A, 0x5BCEFA, 0x9B6BF2,
)
private const val EDITOR_HEIGHT = 11 * CARD_ROW_HEIGHT + 10 * CARD_ROW_GAP
private const val EDITOR_TOP = CARD_TOP + (CARD_HEIGHT - EDITOR_HEIGHT) / 2f
private const val EDITOR_PREVIEW_WIDTH = PANEL_WIDTH - CARD_MARGIN * 2f - CARD_GAP - CARD_CONTENT_WIDTH
private const val BLOCK_SPIN_START = 0.6f
private fun fireTint(fade: Float): Int =
    ((FIRE_ALPHA * fade.coerceIn(0f, 1f)).toInt() shl 24) or 0xFFFFFF
private val PreviewBackground = Color(0xFF273137)
private val SceneShade = Color(0x59000000)

private const val MODE_CARD_WIDTH = 240f
private const val MODE_CARD_HEIGHT = 212f
private const val MODE_CARD_FOOTER = 38f
// Three lines of the 12sp description font whose line height is 1.5em
private const val MODE_CARD_DESC_HEIGHT = 3f * 12f * 1.5f
private const val BLUR_CARDS_X = (PANEL_WIDTH - (MODE_CARD_WIDTH * 3f + 36f)) / 2f
private const val BLUR_CARDS_Y = 130f
private const val BLUR_STRENGTH_LABEL_Y = BLUR_CARDS_Y + MODE_CARD_HEIGHT + 26f
private const val BLUR_SLIDER_Y = BLUR_STRENGTH_LABEL_Y + LABEL_HEIGHT
private const val BLUR_PREVIEW_Y = BLUR_SLIDER_Y + 26f + 16f
private const val BLUR_PREVIEW_HEIGHT = CONTENT_BOTTOM - BLUR_PREVIEW_Y
private const val TERMS_PANEL_WIDTH = 620f
private const val ACCESSIBILITY_PANEL_HEIGHT = 348f
private const val ACCESSIBILITY_MARGIN = 26f
private const val ACCESSIBILITY_GAP = 8f
private const val ACCESSIBILITY_TEXT_Y = 112f
private const val ACCESSIBILITY_ROWS_Y = 156f
private const val ACCESSIBILITY_LINK_INSET = 26f
private const val ACCESSIBILITY_ROW_HEIGHT = 32f
private const val ACCESSIBILITY_BASE_ROWS = 2
private const val FOCUS_BORDER_WIDTH = 2f
private const val CARD_PILL_WIDTH = 300f
private const val CARD_PILL_HEIGHT = 84f
private const val CARD_PILL_CENTER = 74f
private const val CARD_CONTENT_DELAY = 0.35f
private const val LOBBY_PANEL_SCALE = UI_SCALE * GUI_DENSITY_TRIM * LobbyLayout.DESIGN_WIDTH / DESIGN_WIDTH
private const val LOBBY_TERMS_HEIGHT = 300f
private const val LOBBY_TERMS_TOP = 118f
private const val TERMS_PANEL_HEIGHT = 170f
private const val TERMS_CONTENT_TOP = 30f
private const val NAV_BOTTOM_INSET = 56f
private const val SECONDARY_ACTION_WIDTH = 120f
private const val HINT_WIDTH = 200f
private const val BLUR_SAMPLE_TIMEOUT_MILLIS = 10_000L
private const val SLIDER_KEY_STEP = 0.05f
private const val TERMS_NOTE =
    "Declining turns off crash reports, friends, parties, world hosting, featured servers, cosmetics and the Marketplace. You can change this later in Settings."
private const val MOD_ORDER_TEXT = "Choose how the mods list is sorted. Recommended puts the mods you are most likely to tweak first."
private const val SPRINT_TEXT = "Turn sprinting into a toggle so you keep running after you let go of the key."
private const val DONE_TEXT = "That’s all for now, thank you for choosing OneClient! We hope you have a nice experience using it."

private val LocalCardTitle = compositionLocalOf { "" }

private val LocalPanelWidth = compositionLocalOf { PANEL_WIDTH }
private val LocalPanelHeight = compositionLocalOf { PANEL_HEIGHT }
private const val ONBOARDING_ASSETS = "assets/polyplus/onboarding/"
private const val GRASS_ASSETS = "assets/polyplus/onboarding/bettergrass/"
private const val MOUNT_ASSETS = "assets/polyplus/onboarding/mountopacity/"
private const val MOUNT_SCENE = "mount-scene.png"
private const val MOUNT_FULL = "mount-full.png"
private const val WAVEY_ASSETS = "assets/polyplus/onboarding/waveycapes/"
private const val DYNAMIC_LIGHTS_ASSETS = "assets/polyplus/onboarding/dynamiclights/"
private const val SKINLAYERS_ASSETS = "assets/polyplus/onboarding/skinlayers/"
private const val GAMMA_ASSETS = "assets/polyplus/onboarding/gamma/"
private const val GAMMA_DARK = "gamma-dark.png"
private const val GAMMA_BRIGHT = "gamma-bright.png"
private const val SHIELD_ASSETS = "assets/polyplus/onboarding/shield/"
private const val SHIELD_SCENE = "shield-scene.png"
private const val SHIELD_LAYER = "shield-layer.png"
private const val MAIN_MENU_ASSETS = "assets/polyplus/mainmenu/"
private const val GUIDE_ASSETS = "assets/polyplus/onboarding/guides/"
private const val GUIDE_TEXT_Y = 128f
private const val GUIDE_SHOT_Y = 188f
private const val GUIDE_HEIGHT = CONTENT_BOTTOM - GUIDE_SHOT_Y
private const val GUIDE_WIDTH = PANEL_WIDTH - CARD_MARGIN * 2f
private const val GUIDE_MARGIN = CARD_MARGIN

private val ButtonShape: Shape
    @Composable
    get() = ppShape(9.dp)

private val PageBackground: Color
    @Composable get() = OneConfigTheme.current.pageBackground
private val ShadowColor = Color(0x26000000)

private val ChoiceBackground: Color
    @Composable get() = OneConfigTheme.current.componentBackground.copy(alpha = 0.5f)
private val MenuBackground: Color
    @Composable get() = SocialDropdownBackground

private const val HUD_TEXT_SIZE = 21f
private const val HUD_PADDING = HUD_TEXT_SIZE * (4f / 9f)
private const val HUD_RADIUS = HUD_TEXT_SIZE * (4f / 9f)
private val HudBackground = Color(0x80000000)
private val ImpactGood = Color(0xFF6FD08C)
private val ImpactWarn = Color(0xFFE7B85C)
private val ImpactHeavy = Color(0xFFE8836B)

