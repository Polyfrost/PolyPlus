@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.features.OnboardingFeatures.ModCard
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
import org.polyfrost.polyplus.privacy.LauncherEnvironment
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ChipDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeStack
import org.polyfrost.oneconfig.api.ui.v1.components.LinkText
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.components.selectableCard
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import kotlin.math.roundToInt
import org.polyfrost.polyplus.client.gui.onboarding.flow.CardFlowActions
import org.polyfrost.polyplus.client.gui.onboarding.flow.CardFlowFrame
import org.polyfrost.polyplus.client.gui.onboarding.flow.ModReads
import org.polyfrost.polyplus.client.gui.onboarding.flow.OnboardingCardFlow
import org.polyfrost.polyplus.client.gui.onboarding.flow.OnboardingChoices
import org.polyfrost.polyplus.client.gui.onboarding.flow.ScreenPart
import org.polyfrost.polyplus.client.gui.onboarding.flow.cardTopics
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTuning
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTweak
import org.polyfrost.polyplus.client.gui.onboarding.live.LocalLiveTuning

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

class PolyPlusOnboardingScreen :
    OneConfigComposeScreen(previous = null, renderMode = OneConfigComposeScreen.RenderMode.Continuous) {
    private var firstFrameDrawn = false

    // consuming Escape here rather than via shouldCloseOnEsc, since OneConfig hands unhandled keys straight to Screen on 1.8.9
    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean =
        StartupAccessibility.handleKey(key, modifiers) || key == InputConstants.KEY_ESCAPE

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
        if (MenuPanorama.panoramaReady()) {
            Minecraft.getInstance().gameRenderer
                //? if >= 26.2 {
                .panorama()
                .extractRenderState(ctx, width, height)
                //?} else {
                /*.getPanorama()
                .extractRenderState(ctx, width, height, true)
                *///?}
        }
        ctx.blurBeforeThisStratum()
        MenuBackgroundPass.renderInline(ctx)
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        StartupAccessibility.frame()
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    @Composable
    override fun Content() {
        ProvidePopupSounds(false) { OnboardingFlow(OnboardingHost.Screen) { ClientPlatform.setScreen(TitleScreen()) } }
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
    val readsMods = needsSettings || needsModSettings
    val modReads = remember { ModReads.read(readsMods) }
    val offeredCards = remember {
        OnboardingFeatures.newModCards(PolyPlusConfig.onboardingModSettingsVersion, modReads.cards)
    }
    val sprintStep = needsSettings && OnboardingFeatures.polySprintAvailable
    val watermarkStep = remember { LauncherEnvironment.isOneClient && (needsSettings || !PolyPlusConfig.watermarkAsked) }
    val screensStep = remember { needsSettings || !PolyPlusScreensConfig.onboardingAsked }
    val topics = remember {
        cardTopics(offeredCards, sprintStep, if (screensStep) ScreenPart.shown else emptyList(), watermarkStep)
    }
    val choices = remember { OnboardingChoices() }
    val tuning = remember { LiveTuning() }
    val pages = remember {
        buildList {
            if (accessibilityStep) add(OnboardingStep.Accessibility)
            if (needsTerms) add(OnboardingStep.Terms)
            if (needsSettings) add(OnboardingStep.LookAndFeel)
            if (needsSettings || topics.isNotEmpty()) add(OnboardingStep.Cards)
        }.ifEmpty { listOf(OnboardingStep.Cards) }
    }
    var page by remember { mutableIntStateOf(0) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var lightTheme by remember { mutableStateOf(OneConfigTheme.active?.isLightTheme ?: PolyPlusConfig.onboardingLightTheme) }
    var uiStyle by remember {
        mutableIntStateOf(OneConfigTheme.active?.let(OnboardingFeatures::uiStyleOf) ?: PolyPlusConfig.onboardingUiStyle)
    }
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
    var finished by remember { mutableStateOf(false) }
    val finish = finish@{
        if (finished) return@finish
        finished = true
        val sprintAnswered = sprintStep && choices.answered(LiveTweak.SPRINT)
        if (needsSettings) {
            PolyPlusConfig.onboardingLightTheme = lightTheme
            PolyPlusConfig.onboardingUiStyle = uiStyle
            PolyPlusConfig.onboardingGuiScale = chosenGuiScale
            if (sprintAnswered) PolyPlusConfig.onboardingToggleSprint = choices.ours(LiveTweak.SPRINT)
        }
        if (readsMods) saveModSettings(offeredCards, choices.writeTweaks(offeredCards, modReads, tuning))
        val blurAdopted = OnboardingFeatures.adoptRecommendedMotionBlur()
        if (watermarkStep) choices.writeWatermark()
        if (screensStep) choices.writeScreens(ScreenPart.shown)
        PolyPlusConfig.onboardingCompleted = true
        PolyPlusConfig.save()
        if (needsSettings) {
            OnboardingFeatures.applySavedSettings(applySprint = sprintAnswered, applyBlur = blurAdopted)
        } else {
            if (readsMods) OnboardingFeatures.applySavedModSettings()
            if (blurAdopted) OnboardingFeatures.applySavedMotionBlur()
        }
        onFinished()
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
    val onCards = current == OnboardingStep.Cards
    val onNext: () -> Unit = when {
        onTerms -> ({ answerTerms(true) })
        onAccessibility -> leaveAccessibility
        else -> advance
    }
    val onBack: (() -> Unit)? = if (page > 0) ({ page-- }) else null
    if (!onCards) SideEffect { OnboardingKeys.update(page, onNext, onBack, null) }
    DisposableEffect(Unit) { onDispose { OnboardingKeys.clear() } }
    if (!onAccessibility && !onCards) Announce(pageSpeech(current, page))

    val content: @Composable () -> Unit = {
        if (onCards) {
            Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Page)) {
                CompositionLocalProvider(LocalLiveTuning provides tuning) {
                    OnboardingCardFlow(
                        topics,
                        choices,
                        CardFlowFrame(PANEL_WIDTH, PANEL_HEIGHT, lobbyHost),
                        CardFlowActions(onLeave = onBack, onSkip = finish, onFinish = finish),
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Page)) {
                when (current) {
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
                    OnboardingStep.Cards -> Unit
                }
            }
            val lastPage = page == pages.size - 1
            Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Bar)) {
                BottomNavigation(
                    page,
                    pages.size,
                    onSkip = if (onAccessibility) leaveAccessibility else finish,
                    onBack = { page-- },
                    onNext = onNext,
                    allowSkip = onAccessibility || (!onTerms && !lastPage),
                    nextLabel = when {
                        onAccessibility -> StartupAccessibility.continueLabel()
                        onTerms -> "Agree"
                        else -> null
                    },
                    secondaryLabel = if (onTerms) "Decline" else null,
                    onSecondary = { answerTerms(false) },
                    skipFocused = onAccessibility && StartupAccessibility.focused(StartupAccessibility.Control.Skip),
                    nextFocused = onAccessibility && StartupAccessibility.focused(StartupAccessibility.Control.Continue),
                )
            }
        }
    }

    val accessibility = current == OnboardingStep.Accessibility
    val compact = onTerms || accessibility
    if (lobbyHost) {
        val outer = LocalDensity.current
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
                        .themedSurface(SurfaceKind.Panel),
                ) {
                    content()
                }
            }
        }
    }
}

private fun saveModSettings(offeredCards: List<ModCard>, touched: Set<ModCard>) {
    val startedAtVersion = PolyPlusConfig.onboardingModSettingsVersion
    val available = OnboardingFeatures.availableCards
    fun settled(card: ModCard, current: Boolean) =
        OnboardingFeatures.settledAfterRun(card, offeredCards, available, touched, current, startedAtVersion)
    PolyPlusConfig.onboardingBetterGrassSettled = settled(ModCard.GRASS, PolyPlusConfig.onboardingBetterGrassSettled)
    PolyPlusConfig.onboardingFireOverlaySettled = settled(ModCard.FIRE_OVERLAY, PolyPlusConfig.onboardingFireOverlaySettled)
    PolyPlusConfig.onboardingShieldHeightSettled = settled(ModCard.SHIELD_HEIGHT, PolyPlusConfig.onboardingShieldHeightSettled)
    PolyPlusConfig.onboardingMountOpacitySettled = settled(ModCard.MOUNT, PolyPlusConfig.onboardingMountOpacitySettled)
    PolyPlusConfig.onboardingWaveyCapesSettled = settled(ModCard.CAPES, PolyPlusConfig.onboardingWaveyCapesSettled)
    PolyPlusConfig.onboardingSkinLayersSettled = settled(ModCard.SKIN_LAYERS, PolyPlusConfig.onboardingSkinLayersSettled)
    PolyPlusConfig.onboardingGammaSettled = settled(ModCard.GAMMA, PolyPlusConfig.onboardingGammaSettled)
    PolyPlusConfig.onboardingBlockHighlightSettled = settled(ModCard.BLOCK_HIGHLIGHT, PolyPlusConfig.onboardingBlockHighlightSettled)
    PolyPlusConfig.onboardingDynamicLightsSettled = settled(ModCard.DYNAMIC_LIGHTS, PolyPlusConfig.onboardingDynamicLightsSettled)
    PolyPlusConfig.onboardingModSettingsVersion = OnboardingFeatures.completedModSettingsVersion(startedAtVersion, available)
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
    SectionHeader("UI Colors", Modifier.offset(232.dp, y.dp).width(198.dp), eyebrow = false)
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ChoiceButton("Dark", MAIN_MENU_ASSETS + "moon-star.svg", !lightTheme, 198f, option = true) { onLightTheme(false) }
        ChoiceButton("Light", ONBOARDING_ASSETS + "sun.svg", lightTheme, 198f, option = true) { onLightTheme(true) }
    }
    y += colorsHeight + SECTION_GAP
    SectionHeader("UI Style", Modifier.offset(232.dp, y.dp).width(198.dp), eyebrow = false)
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), horizontalArrangement = Arrangement.spacedBy(STYLE_CARD_GAP.dp)) {
        UiStyleOptions.forEach { option ->
            StyleCard(option, OnboardingFeatures.uiStyleTheme(option.style, lightTheme), uiStyle == option.style) { onUiStyle(option.style) }
        }
    }
    y += styleHeight + SECTION_GAP
    GuiScaleSection(y, guiScale, maxGuiScale, onGuiScale)
}

@Composable
private fun GuiScaleSection(y: Float, guiScale: Int, maxScale: Int, onGuiScale: (Int) -> Unit) {
    SectionHeader("GUI Scale", Modifier.offset(232.dp, y.dp).width(198.dp), eyebrow = false)
    val steps = maxScale.coerceAtLeast(1)
    fun valueToProgress(v: Int): Float = if (v <= 0) 1f else ((v - 1).toFloat() / steps).coerceIn(0f, 1f)
    fun indexToValue(index: Int): Int = if (index >= steps) 0 else index + 1
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), verticalAlignment = Alignment.CenterVertically) {
        var pending by remember { mutableIntStateOf(guiScale) }
        val shown = if (guiScale <= 0) "Auto" else guiScale.toString()
        OnboardingSlider(
            progress = valueToProgress(guiScale),
            width = 332f,
            label = "GUI Scale",
            valueText = { p -> indexToValue((p * steps).roundToInt()).let { if (it <= 0) "Auto" else it.toString() } },
            keyStep = 1f / steps,
            onRelease = { OnboardingFeatures.applyGuiScale(pending, persist = false) },
        ) { p ->
            pending = indexToValue((p * steps).roundToInt())
            onGuiScale(pending)
        }
        Spacer(Modifier.width(18.dp))
        ValueBadge(shown, 64.dp, 26.dp, Alignment.CenterStart)
    }
}

@Composable
private fun ValueBadge(value: String, width: Dp, height: Dp, alignment: Alignment = Alignment.Center, dimmed: Boolean = false) {
    val disabledAlpha = OneConfigTheme.current.disabledAlpha
    Box(
        Modifier.width(width).height(height).graphicsLayer { alpha = if (dimmed) disabledAlpha else 1f },
        contentAlignment = alignment,
    ) { Badge(value) }
}

@Composable
internal fun OnboardingSlider(
    progress: Float,
    width: Float,
    enabled: Boolean = true,
    label: String? = null,
    valueText: ((Float) -> String)? = null,
    keyStep: Float = SLIDER_KEY_STEP,
    onRelease: () -> Unit = {},
    onProgress: (Float) -> Unit,
) {
    Slider(
        value = progress,
        onValueChange = onProgress,
        modifier = Modifier.width(width.dp),
        enabled = enabled,
        onValueChangeFinished = onRelease,
        size = ControlSize.Compact,
        keyStep = keyStep,
        contentDescription = label,
        valueDescription = valueText,
        sound = null,
    )
}

@Composable
private fun Header(kicker: String, title: String) {
    val width = LocalPanelWidth.current
    Text(kicker, modifier = Modifier.offset(0.dp, 35.dp).width(width.dp), style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
    Text(title, modifier = Modifier.offset(0.dp, 66.dp).width(width.dp), style = OneConfigTheme.current.typography.body.copy(fontSize = 32.sp), textAlign = TextAlign.Center)
}

@Composable
private fun LockupHeader() {
    val width = LocalPanelWidth.current
    val ink = SocialTextPrimary.toArgb()
    SideEffect { StartupOverlay.cardInk = ink }
    Text("Welcome to", modifier = Modifier.offset(0.dp, 28.dp).width(width.dp), style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
    Row(
        Modifier.offset(0.dp, 56.dp).width(width.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.onGloballyPositioned { StartupOverlay.cardMark = StartupOverlay.place(it) }) { LobbyIcon("logo", size = 36.dp) }
        Box(Modifier.onGloballyPositioned { StartupOverlay.cardWord = StartupOverlay.place(it) }) {
            Text("ONECLIENT", style = OneConfigTheme.current.typography.body.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.2.sp, fontFamily = Outfit), maxLines = 1, softWrap = false)
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
        Text(
            prompt,
            modifier = Modifier.offset(ACCESSIBILITY_MARGIN.dp, ACCESSIBILITY_TEXT_Y.dp).width(rowWidth.dp),
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.body,
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
                        chip = true,
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
    val focused = StartupAccessibility.focused(StartupAccessibility.Control.Standard)
    Box(modifier.width(LocalPanelWidth.current.dp), contentAlignment = Alignment.Center) {
        LinkText(
            StartupAccessibility.label(StartupAccessibility.Control.Standard),
            { StartupAccessibility.activate(StartupAccessibility.Control.Standard) },
            style = OneConfigTheme.current.typography.body.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                textDecoration = if (focused) TextDecoration.Underline else TextDecoration.None,
            ),
        )
    }
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
    chip: Boolean = false,
    blocked: String? = null,
    onClick: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val chipped = option || chip
    val frame = modifier
        .width(width.dp)
        .height(32.dp)
        .then(if (focused) Modifier.border(FOCUS_BORDER_WIDTH.dp, theme.textColor, if (chipped) ChipDefaults.shape else ButtonDefaults.shape) else Modifier)
    if (chipped) {
        Chip(
            label,
            selected,
            onClick,
            frame,
            icon = icon,
            enabled = enabled,
            role = if (option) A11yRole.Option else A11yRole.Button,
        )
        return
    }
    val style = if (primary) ControlStyle.Primary else ControlStyle.Secondary
    val message = blocked.takeIf { !enabled }
    TextButton(
        label,
        if (message != null) ({ A11y.say(message, true) }) else onClick,
        if (message != null) frame.graphicsLayer { alpha = theme.disabledAlpha } else frame,
        style = style,
        enabled = enabled || message != null,
        leading = { Icon(name = icon, contentDescription = null, tint = ButtonDefaults.contentColor(style), modifier = Modifier.size(17.dp)) },
        contentDescription = message?.let { "$label, unavailable. $it" },
        sound = if (message != null) null else UiSound.Click,
    )
}

private class UiStyleOption(val style: Int, val label: String, val caption: String, val glass: Boolean)

private val UiStyleOptions = listOf(
    UiStyleOption(OnboardingFeatures.UI_STYLE_POLYFROSTED, "Polyfrosted", "Frosted panels, the OneConfig look", glass = false),
    UiStyleOption(OnboardingFeatures.UI_STYLE_POLYGLASS, "PolyGlass", "See-through glass over your game", glass = true),
    UiStyleOption(OnboardingFeatures.UI_STYLE_MINECRAFT, "Minecraft", "Blocky, like the vanilla menus", glass = false),
)

private val StylePreviewBackdrop = Brush.verticalGradient(
    0f to Color(0xFF4F86D1),
    0.58f to Color(0xFFA3CBEF),
    0.59f to Color(0xFF5E9E3A),
    1f to Color(0xFF3B6A25),
)

private val StylePreviewSheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.04f)))

@Composable
private fun StyleCard(option: UiStyleOption, theme: OneConfigUITheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(STYLE_CARD_WIDTH.dp, 155.dp)
            .themedSurface(SurfaceKind.Card)
            .selectableCard(selected, onClick, "${option.label} style. ${option.caption}")
            .padding(9.dp),
    ) {
        UiPreview(option, theme)
        Spacer(Modifier.height(7.dp))
        Text(option.label, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium, fontFamily = theme.fontFamily), maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        Text(option.caption, color = SocialTextSecondary, style = OneConfigTheme.current.typography.caption, maxLines = 2)
    }
}

@Composable
private fun UiPreview(option: UiStyleOption, theme: OneConfigUITheme) {
    val accent = OneConfigTheme.accent
    val window = theme.shapes.medium
    val small = theme.shapes.small
    Box(Modifier.size(STYLE_PREVIEW_WIDTH.dp, STYLE_PREVIEW_HEIGHT.dp).clip(OneConfigTheme.shape(4.dp)).background(StylePreviewBackdrop)) {
        val windowModifier = Modifier.fillMaxSize().padding(8.dp, 7.dp).clip(window)
        Row(windowModifier.background(theme.pageBackground).border(1.dp, theme.borderColor, window)) {
            Column(
                Modifier.width(26.dp).fillMaxHeight().background(theme.sidebarBackground).padding(5.dp, 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(16.dp, 4.dp).clip(small).background(accent))
                repeat(3) {
                    Box(Modifier.width(if (it == 0) 11.dp else 16.dp).height(3.dp).clip(small).background(if (it == 0) theme.textColorSecondary else theme.textColor))
                }
            }
            Column(Modifier.fillMaxSize().padding(5.dp, 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(24.dp, 4.dp).clip(small).background(theme.textColor))
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { column ->
                            Box(Modifier.size(17.dp, 15.dp).clip(small).background(theme.modCardBackground).border(0.5.dp, theme.borderColor, small)) {
                                val bar = if (row == 0 && column == 0) accent else theme.textColorSecondary
                                Box(Modifier.align(Alignment.BottomStart).padding(3.dp).size(9.dp, 2.dp).clip(small).background(bar))
                            }
                        }
                    }
                }
            }
        }
        if (option.glass) Box(windowModifier.background(StylePreviewSheen))
    }
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
                TextButton(
                    "Skip setup",
                    onSkip,
                    Modifier.offset(x = -ButtonDefaults.horizontalPadding(ControlSize.Compact)),
                    style = ControlStyle.Ghost,
                    size = ControlSize.Compact,
                )
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
            Text(hint, color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Light), textAlign = TextAlign.End)
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
                    .clip(OneConfigTheme.shape(8.dp))
                    .background(if (index == page) SocialTextPrimary.copy(alpha = 0.5f) else OneConfigTheme.current.componentBackground)
                    .border(1.dp, SocialTextPrimary.copy(alpha = if (index == page) 0.8f else 0.4f), OneConfigTheme.shape(8.dp)),
            )
        }
    }
}

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
                Text("Agree to the", style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
                TermsLink("Terms of Service") {
                    ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL)
                }
                Text("and", style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
                TermsLink("Privacy Policy") {
                    ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL)
                }
                Text("to use online features.", style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
            }
            Text(
                TERMS_NOTE,
                modifier = Modifier.width((panelWidth - 80f).dp),
                color = SocialTextSecondary,
                style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Light),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TermsLink(label: String, onClick: () -> Unit) {
    LinkText(
        label,
        onClick,
        style = OneConfigTheme.current.typography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
    )
}

internal object OnboardingKeys {
    private const val PAGE_SETTLE_MS = 350L

    private var page = -1
    private var pageAt = 0L
    private var next: (() -> Unit)? = null
    private var back: (() -> Unit)? = null
    private var blocked: String? = null
    private var left: (() -> Unit)? = null
    private var right: (() -> Unit)? = null

    fun update(
        page: Int,
        next: (() -> Unit)?,
        back: (() -> Unit)?,
        blocked: String?,
        left: (() -> Unit)? = null,
        right: (() -> Unit)? = null,
    ) {
        if (page != this.page) {
            this.page = page
            pageAt = System.currentTimeMillis()
        }
        this.next = next
        this.back = back
        this.blocked = blocked
        this.left = left
        this.right = right
    }

    fun clear() {
        page = -1
        next = null
        back = null
        blocked = null
        left = null
        right = null
    }

    fun handle(key: Int): Boolean {
        when (key) {
            InputConstants.KEY_ESCAPE -> if (!EscapeStack.dispatch()) (back ?: return false)()
            InputConstants.KEY_LEFT -> if (LobbyA11y.keyboardFocused()) return false else (left ?: return false)()
            InputConstants.KEY_RIGHT -> if (LobbyA11y.keyboardFocused()) return false else (right ?: return false)()
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

private fun pageSpeech(step: OnboardingStep, page: Int): String {
    val body = when (step) {
        OnboardingStep.Accessibility, OnboardingStep.Cards -> ""
        OnboardingStep.Terms ->
            "Welcome to OneClient. Agree to the Terms of Service and Privacy Policy to use online features. $TERMS_NOTE"
        OnboardingStep.LookAndFeel -> "Look and Feel. Choose the UI colors, UI style and GUI scale."
    }
    return "Step ${page + 1}. $body"
}

private enum class OnboardingStep { Accessibility, Terms, LookAndFeel, Cards }

private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f
private const val UI_SCALE = DESIGN_WIDTH / 1240f
private const val PANEL_WIDTH = 880f
private const val PANEL_HEIGHT = 660f

private const val CONTENT_TOP = 140f
private const val CONTENT_BOTTOM = 557f
private const val SECTION_GAP = 24f
private const val STYLE_CARD_WIDTH = 130f
private const val STYLE_CARD_GAP = 12f
private const val STYLE_PREVIEW_WIDTH = 112f
private const val STYLE_PREVIEW_HEIGHT = 72f
private const val LABEL_HEIGHT = 32f
private const val SLIDER_KEY_STEP = 0.05f
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
private const val TERMS_NOTE =
    "Declining turns off crash reports, friends, parties, world hosting, featured servers, cosmetics and the Marketplace. You can change this later in Settings."

private val LocalPanelWidth = compositionLocalOf { PANEL_WIDTH }
private val LocalPanelHeight = compositionLocalOf { PANEL_HEIGHT }
private const val ONBOARDING_ASSETS = "assets/polyplus/onboarding/"
private const val MAIN_MENU_ASSETS = "assets/polyplus/mainmenu/"
