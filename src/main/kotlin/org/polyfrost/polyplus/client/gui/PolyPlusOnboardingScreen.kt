package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.LocalUiOversample
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.preview.UnityMotionBlur
import org.polyfrost.polyplus.client.gui.preview.VanillaTextures
import org.polyfrost.polyplus.client.legal.LegalDocument
import org.polyfrost.polyplus.client.legal.LegalDocuments
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import kotlin.math.PI
import kotlin.math.cos
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

class PolyPlusOnboardingScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private var firstFrameDrawn = false

    override fun shouldCloseOnEsc(): Boolean = false

    //? if <26.1 {
    /*override fun render(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        MenuBackgroundPass.enqueue(true)
        renderPanorama(ctx, tickDelta)
        if (firstFrameDrawn) {
            val gameRenderer = net.minecraft.client.Minecraft.getInstance().gameRenderer
            //? if <1.21.4 {
            /*gameRenderer.processBlurEffect(tickDelta)
            *///?} else {
            gameRenderer.processBlurEffect()
            //?}
        }
        super.render(ctx, mouseX, mouseY, tickDelta)
        firstFrameDrawn = true
    }

    override fun renderBackground(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?} else {
    override fun extractRenderState(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        MenuBackgroundPass.enqueue(true)
        net.minecraft.client.Minecraft.getInstance().gameRenderer
            //? if >= 26.2 {
            .panorama()
            .extractRenderState(ctx, width, height)
            //?} else {
            /*.getPanorama()
            .extractRenderState(ctx, width, height, true)
            *///?}
        ctx.blurBeforeThisStratum()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    @Composable
    override fun compose() {
        val needsTerms = remember { PrivacyConsent.needsPrompt() }
        val needsSettings = remember { !PolyPlusConfig.onboardingCompleted }
        val needsModSettings = remember { OnboardingFeatures.needsModSettingsChoice() }
        val needsBlurChoice = remember { OnboardingFeatures.needsMotionBlurChoice() }
        val showsModSettings = (needsSettings || needsModSettings) && OnboardingFeatures.modsPageAvailable
        val modReads = remember { ModReads.read(showsModSettings) }
        val modCards = modReads.cards
        val sprintSection = needsSettings && OnboardingFeatures.polySprintAvailable
        val showsModsPage = showsModSettings && (modCards.isNotEmpty() || sprintSection)
        val pages = remember {
            buildList {
                if (needsTerms) add(OnboardingPage.TERMS)
                if (needsSettings) add(OnboardingPage.LOOK_AND_FEEL)
                if (showsModsPage) {
                    add(OnboardingPage.MODS)
                    repeat((modPageCount(modCards.size) - 1).coerceAtLeast(0)) {
                        add(OnboardingPage.MODS_MORE)
                    }
                }
                if (needsBlurChoice) add(OnboardingPage.MOTION_BLUR)
                if (needsSettings || needsModSettings) add(OnboardingPage.DONE)
            }.ifEmpty { listOf(OnboardingPage.DONE) }
        }
        var page by remember { mutableIntStateOf(0) }
        var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
        var termsAccepted by remember { mutableStateOf(false) }
        var lightTheme by remember { mutableStateOf(PolyPlusConfig.onboardingLightTheme) }
        var uiStyle by remember { mutableIntStateOf(PolyPlusConfig.onboardingUiStyle) }
        var toggleSprint by remember { mutableStateOf(PolyPlusConfig.onboardingToggleSprint) }
        var grassMode by remember { mutableIntStateOf(modReads.grass ?: PolyPlusConfig.onboardingBetterGrassMode) }
        var fireHeight by remember { mutableStateOf(modReads.fireHeight ?: PolyPlusConfig.onboardingFireOverlayHeight) }
        var fireOpacity by remember {
            mutableStateOf(modReads.fireOpacity ?: PolyPlusConfig.onboardingFireOverlayOpacity)
        }
        var shieldHeight by remember { mutableStateOf(modReads.shield ?: PolyPlusConfig.onboardingShieldHeight) }
        var horseOpacity by remember { mutableStateOf(modReads.horse ?: PolyPlusConfig.onboardingHorseOpacity) }
        var waveyCapes by remember { mutableStateOf(modReads.capes ?: PolyPlusConfig.onboardingWaveyCapes) }
        var skinLayers by remember { mutableStateOf(modReads.layers ?: PolyPlusConfig.onboardingSkinLayers) }
        var itemOffsetX by remember { mutableStateOf(modReads.itemX ?: PolyPlusConfig.onboardingItemOffsetX) }
        var itemOffsetY by remember { mutableStateOf(modReads.itemY ?: PolyPlusConfig.onboardingItemOffsetY) }
        var itemOffsetZ by remember { mutableStateOf(modReads.itemZ ?: PolyPlusConfig.onboardingItemOffsetZ) }
        var itemScale by remember { mutableStateOf(modReads.itemScale ?: PolyPlusConfig.onboardingItemScale) }
        val touched = remember { mutableSetOf<ModCard>() }
        fun <T> touch(card: ModCard, old: T, new: T) {
            if (old != new) touched += card
        }
        var motionBlur by remember { mutableIntStateOf(PolyPlusConfig.onboardingMotionBlur.coerceIn(1, MOTION_BLUR_MAX)) }
        var blurMode by remember { mutableIntStateOf(OnboardingFeatures.MOTION_BLUR_UNSET) }
        val maxGuiScale = remember { OnboardingFeatures.maxGuiScale() }
        var guiScale by remember {
            mutableIntStateOf(
                net.minecraft.client.Minecraft.getInstance().options.guiScale().get().coerceIn(0, maxGuiScale),
            )
        }
        LaunchedEffect(lightTheme, uiStyle) {
            if (needsSettings) OnboardingFeatures.applyTheme(lightTheme, uiStyle)
        }
        val finish = {
            if (needsSettings) {
                PolyPlusConfig.onboardingLightTheme = lightTheme
                PolyPlusConfig.onboardingUiStyle = uiStyle
                PolyPlusConfig.onboardingGuiScale = guiScale
                PolyPlusConfig.onboardingToggleSprint = toggleSprint
            }
            if (showsModSettings) {
                PolyPlusConfig.onboardingBetterGrassMode = grassMode
                PolyPlusConfig.onboardingFireOverlayHeight = fireHeight
                PolyPlusConfig.onboardingFireOverlayOpacity = fireOpacity
                PolyPlusConfig.onboardingShieldHeight = shieldHeight
                PolyPlusConfig.onboardingHorseOpacity = horseOpacity
                PolyPlusConfig.onboardingWaveyCapes = waveyCapes
                PolyPlusConfig.onboardingSkinLayers = skinLayers
                PolyPlusConfig.onboardingItemOffsetX = itemOffsetX
                PolyPlusConfig.onboardingItemOffsetY = itemOffsetY
                PolyPlusConfig.onboardingItemOffsetZ = itemOffsetZ
                PolyPlusConfig.onboardingItemScale = itemScale
                fun settled(card: ModCard) = card !in modCards || card !in touched
                PolyPlusConfig.onboardingBetterGrassSettled = settled(ModCard.GRASS)
                PolyPlusConfig.onboardingFireOverlaySettled = settled(ModCard.FIRE_OVERLAY)
                PolyPlusConfig.onboardingShieldHeightSettled = settled(ModCard.SHIELD_HEIGHT)
                PolyPlusConfig.onboardingMountOpacitySettled = settled(ModCard.MOUNT)
                PolyPlusConfig.onboardingWaveyCapesSettled = settled(ModCard.CAPES)
                PolyPlusConfig.onboardingSkinLayersSettled = settled(ModCard.SKIN_LAYERS)
                PolyPlusConfig.onboardingItemPositionsSettled = settled(ModCard.ITEM)
                if (showsModSettings) {
                    PolyPlusConfig.onboardingModSettingsVersion = OnboardingFeatures.completedModSettingsVersion(
                        PolyPlusConfig.onboardingModSettingsVersion,
                        OnboardingFeatures.modCardCount,
                    )
                }
            }
            if (needsBlurChoice && blurMode != OnboardingFeatures.MOTION_BLUR_UNSET) {
                PolyPlusConfig.onboardingMotionBlurMode = blurMode
                PolyPlusConfig.onboardingMotionBlur = motionBlur
                PolyPlusConfig.onboardingPolyBlurApplied = false
                PolyPlusConfig.adaptiveBlurApplied = true
            }
            PolyPlusConfig.onboardingCompleted = true
            PolyPlusConfig.save()
            if (needsSettings) {
                OnboardingFeatures.applySavedSettings()
            } else {
                if (showsModSettings) OnboardingFeatures.applySavedModSettings()
                if (needsBlurChoice) OnboardingFeatures.applySavedMotionBlur()
            }
            val mc = net.minecraft.client.Minecraft.getInstance()
            //? if >= 26.2 {
            mc.gui.setScreen(PolyPlusMainMenuScreen())
            //?} else {
            /*mc.setScreen(PolyPlusMainMenuScreen())
            *///?}
        }

        val waitingForOptimization =
            pages[page] == OnboardingPage.MOTION_BLUR && !AdaptiveBlurDefaults.sampled

        val recordLegalChoice = { accepted: Boolean ->
            val document = legalDocument
            if (accepted) {
                PrivacyConsent.accept(document?.version ?: 0, document?.resolvedPrivacyVersion ?: 0)
            } else {
                PrivacyConsent.decline()
            }
            PrivacyEnforcement.syncConfig()
            PrivacyEnforcement.apply()
        }
        val advance: () -> Unit = {
            if (page == pages.size - 1) finish() else page++
        }
        val answerTerms = { accepted: Boolean ->
            recordLegalChoice(accepted)
            advance()
        }

        Theme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val guiScaleFactor = guiScaleFactorFor(if (guiScale <= 0) maxGuiScale else guiScale)
                val scale = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT) *
                    guiScaleFactor * UI_SCALE * GUI_DENSITY_TRIM
                val compact = pages[page] == OnboardingPage.TERMS
                val panelWidth by animateFloatAsState(
                    if (compact) TERMS_PANEL_WIDTH else PANEL_WIDTH,
                    animationSpec = spring(),
                )
                val panelHeight by animateFloatAsState(
                    if (compact) TERMS_PANEL_HEIGHT else PANEL_HEIGHT,
                    animationSpec = spring(),
                )
                CompositionLocalProvider(
                    LocalUiOversample provides (LocalUiOversample.current * scale.coerceAtLeast(1f)),
                    LocalPanelWidth provides panelWidth,
                    LocalPanelHeight provides panelHeight,
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin.Center
                            },
                    ) {
                        Box(
                            Modifier
                                .offset(((DESIGN_WIDTH - panelWidth) / 2f).dp, ((DESIGN_HEIGHT - panelHeight) / 2f).dp)
                                .size(panelWidth.dp, panelHeight.dp)
                                .shadow(
                                    elevation = 29.dp,
                                    shape = PANEL_SHAPE,
                                    ambientColor = ShadowColor,
                                    spotColor = ShadowColor,
                                )
                                .clip(PANEL_SHAPE)
                                .background(PageBackground.copy(alpha = 0.9f))
                                .border(BorderWidth, LocalTheme.current.borderColor, PANEL_SHAPE),
                        ) {
                            when (pages[page]) {
                                OnboardingPage.TERMS ->
                                    TermsPage(
                                        legalDocument,
                                        termsAccepted,
                                        { termsAccepted = it },
                                        { legalDocument = it },
                                    )
                                OnboardingPage.LOOK_AND_FEEL ->
                                    LookAndFeelPage(
                                        lightTheme, { lightTheme = it },
                                        uiStyle, { uiStyle = it },
                                        guiScale, maxGuiScale, { guiScale = it },
                                    )
                                OnboardingPage.MODS, OnboardingPage.MODS_MORE ->
                                    ModsPage(
                                        modCards,
                                        pages.take(page + 1).count { it == OnboardingPage.MODS_MORE },
                                        needsSettings,
                                        toggleSprint, { toggleSprint = it },
                                        grassMode, { touch(ModCard.GRASS, grassMode, it); grassMode = it },
                                        fireHeight, { touch(ModCard.FIRE_OVERLAY, fireHeight, it); fireHeight = it },
                                        fireOpacity, { touch(ModCard.FIRE_OVERLAY, fireOpacity, it); fireOpacity = it },
                                        shieldHeight, { touch(ModCard.SHIELD_HEIGHT, shieldHeight, it); shieldHeight = it },
                                        horseOpacity, { touch(ModCard.MOUNT, horseOpacity, it); horseOpacity = it },
                                        waveyCapes, { touch(ModCard.CAPES, waveyCapes, it); waveyCapes = it },
                                        skinLayers, { touch(ModCard.SKIN_LAYERS, skinLayers, it); skinLayers = it },
                                        itemOffsetX, itemOffsetY, itemOffsetZ,
                                        { x, y, z ->
                                            if (x != itemOffsetX || y != itemOffsetY || z != itemOffsetZ) {
                                                touched += ModCard.ITEM
                                            }
                                            itemOffsetX = x
                                            itemOffsetY = y
                                            itemOffsetZ = z
                                        },
                                        itemScale, { touch(ModCard.ITEM, itemScale, it); itemScale = it },
                                    )
                                OnboardingPage.MOTION_BLUR ->
                                    if (waitingForOptimization) {
                                        OptimizingPage()
                                    } else {
                                        MotionBlurPage(
                                            blurMode,
                                            { blurMode = it },
                                            motionBlur,
                                            { motionBlur = it },
                                        )
                                    }
                                OnboardingPage.COSMETICS -> CosmeticsPage(
                                    onClaim = { PolyPlusClient.refreshCosmetics() },
                                    onStore = {
                                        finish()
                                        PolyPlusOneConfigIntegration.openCosmetics()
                                    },
                                )
                                OnboardingPage.DONE -> DonePage()
                            }
                            val terms = pages[page] == OnboardingPage.TERMS
                            val blurUnanswered = pages[page] == OnboardingPage.MOTION_BLUR &&
                                blurMode == OnboardingFeatures.MOTION_BLUR_UNSET
                            BottomNavigation(
                                page,
                                pages.size,
                                onSkip = finish,
                                onBack = { page-- },
                                onNext = if (terms) ({ answerTerms(true) }) else advance,
                                nextEnabled = !waitingForOptimization && !blurUnanswered && (!terms || termsAccepted),
                                allowSkip = !terms && !needsBlurChoice,
                                nextLabel = if (terms) "Agree" else null,
                                secondaryLabel = if (terms) "Decline" else null,
                                onSecondary = { answerTerms(false) },
                            )
                        }
                    }
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
        ChoiceButton("Dark", MAIN_MENU_ASSETS + "moon-star.svg", !lightTheme, 198f) { onLightTheme(false) }
        ChoiceButton("Light", ONBOARDING_ASSETS + "sun.svg", lightTheme, 198f) { onLightTheme(true) }
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
        val thumbSize = 13.dp
        var trackWidthPx by remember { mutableStateOf(0f) }
        val progress by animateFloatAsState(
            valueToProgress(guiScale),
            animationSpec = spring(),
        )
        Box(
            Modifier
                .width(332.dp)
                .height(13.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(steps) {
                    val thumbPx = thumbSize.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var pending = guiScale
                        fun update(x: Float) {
                            val usableWidth = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                            val p = ((x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                            pending = indexToValue((p * steps).roundToInt())
                            onGuiScale(pending)
                        }
                        update(down.position.x)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            update(change.position.x)
                            change.consume()
                        } while (change.pressed)
                        OnboardingFeatures.applyGuiScale(pending, persist = false)
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(332.dp)
                    .height(7.dp)
                    .clip(ppShape(4.dp))
                    .background(ChoiceBackground)
                    .border(1.dp, PanelBorderBrush, ppShape(4.dp)),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(7.dp).background(Accent))
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { androidx.compose.ui.unit.IntOffset((progress * (trackWidthPx - thumbSize.toPx())).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(ppShape(7.dp))
                    .background(TextPrimary),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier.width(64.dp).height(26.dp).clip(ppShape(6.dp)).background(ChoiceBackground)
                .border(1.dp, PanelBorderBrush, ppShape(6.dp)),
            contentAlignment = Alignment.CenterStart,
        ) { OnboardingText(if (guiScale <= 0) "Auto" else guiScale.toString(), 12, Modifier.padding(start = 8.dp)) }
    }
}

@Composable
private fun ModsPage(
    allCards: List<ModCard>,
    modPage: Int,
    showSprint: Boolean,
    toggleSprint: Boolean,
    onToggleSprint: (Boolean) -> Unit,
    grassMode: Int,
    onGrassMode: (Int) -> Unit,
    fireHeight: Double,
    onFireHeight: (Double) -> Unit,
    fireOpacity: Float,
    onFireOpacity: (Float) -> Unit,
    shieldHeight: Float,
    onShieldHeight: (Float) -> Unit,
    horseOpacity: Float,
    onHorseOpacity: (Float) -> Unit,
    waveyCapes: Boolean,
    onWaveyCapes: (Boolean) -> Unit,
    skinLayers: Boolean,
    onSkinLayers: (Boolean) -> Unit,
    itemOffsetX: Float,
    itemOffsetY: Float,
    itemOffsetZ: Float,
    onItemOffset: (Float, Float, Float) -> Unit,
    itemScale: Float,
    onItemScale: (Float) -> Unit,
) {
    Header("Continuing with", "Mods")

    val range = modPageRange(allCards.size, modPage)
    val cards = if (range.isEmpty()) emptyList() else allCards.subList(range.first, range.last + 1)
    val sprint = showSprint && OnboardingFeatures.polySprintAvailable && modPage == 0

    val total = (if (sprint) SPRINT_SECTION_HEIGHT + SECTION_GAP else 0f) +
        (if (cards.isEmpty()) -SECTION_GAP else MOD_CARD_HEIGHT)
    var y = CONTENT_TOP + ((CONTENT_BOTTOM - CONTENT_TOP) - total) / 2f
    if (sprint) {
        SprintSection(y, toggleSprint, onToggleSprint)
        y += SPRINT_SECTION_HEIGHT + SECTION_GAP
    }
    if (cards.isEmpty()) return

    val rowWidth = cards.size * MOD_CARD_WIDTH + (cards.size - 1) * MOD_CARD_GAP
    Row(
        Modifier.offset(((LocalPanelWidth.current - rowWidth) / 2f).dp, y.dp),
        horizontalArrangement = Arrangement.spacedBy(MOD_CARD_GAP.dp),
    ) {
        cards.forEach { card ->
            when (card) {
                ModCard.GRASS -> BetterGrassCard(grassMode, onGrassMode)
                ModCard.FIRE_OVERLAY -> FireOverlayCard(fireHeight, onFireHeight, fireOpacity, onFireOpacity)
                ModCard.SHIELD_HEIGHT -> ShieldHeightCard(shieldHeight, onShieldHeight)
                ModCard.ITEM ->
                    ItemPositionCard(itemOffsetX, itemOffsetY, itemOffsetZ, itemScale, onItemOffset, onItemScale)
                ModCard.MOUNT -> MountOpacityCard(horseOpacity, onHorseOpacity)
                ModCard.CAPES -> WaveyCapesCard(waveyCapes, onWaveyCapes)
                ModCard.SKIN_LAYERS -> SkinLayersCard(skinLayers, onSkinLayers)
            }
        }
    }
}

internal fun modPageCount(cards: Int): Int = (cards + MOD_CARDS_PER_PAGE - 1) / MOD_CARDS_PER_PAGE

internal fun modPageRange(cards: Int, modPage: Int): IntRange {
    val pageCount = modPageCount(cards)
    if (pageCount == 0) return IntRange.EMPTY
    return (cards * modPage / pageCount) until (cards * (modPage + 1) / pageCount)
}

@Composable
private fun ModCard(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .size(MOD_CARD_WIDTH.dp, MOD_CARD_HEIGHT.dp)
            .clip(ButtonShape)
            .background(ChoiceBackground)
            .border(BorderWidth, PanelBorderBrush, ButtonShape),
    ) {
        Column(Modifier.fillMaxSize().padding(MOD_CARD_PADDING.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingIcon(icon, TextPrimary, Modifier.size(18.dp))
                OnboardingText(title, 15, color = TextPrimary, weight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BetterGrassCard(mode: Int, onMode: (Int) -> Unit) {
    ModCard("Better Grass", MAIN_MENU_ASSETS + "minecraft-block.svg") {
        BetterGrassPreview(mode)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            "Carry grass down the sides of blocks so slopes and terraces stop showing bare dirt.",
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(MOD_CHIP_GAP.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MOD_CHIP_GAP.dp)) {
                ModeChip("Off", mode == 0) { onMode(0) }
                ModeChip("Fastest", mode == 1) { onMode(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MOD_CHIP_GAP.dp)) {
                ModeChip("Fast", mode == 2) { onMode(2) }
                ModeChip("Fancy", mode == 3) { onMode(3) }
            }
        }
    }
}

@Composable
private fun FireOverlayCard(
    height: Double,
    onHeight: (Double) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
) {
    ModCard("Fire Overlay", ONBOARDING_ASSETS + "zap.svg") {
        FireOverlayPreview(height, opacity)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            if (OnboardingFeatures.fireOverlayOpacityAvailable) {
                "Push the flames down the screen and fade them so burning does not blind you."
            } else {
                "Push the flames down the screen so burning does not block your view."
            },
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        val span = OnboardingFeatures.FIRE_OVERLAY_MAX - OnboardingFeatures.FIRE_OVERLAY_MIN
        FireSlider(
            "Height",
            ((height - OnboardingFeatures.FIRE_OVERLAY_MIN) / span).toFloat(),
            fireHeightLabel(height.toFloat()),
        ) { onHeight(OnboardingFeatures.FIRE_OVERLAY_MIN + it * span) }
        if (OnboardingFeatures.fireOverlayOpacityAvailable) {
            Spacer(Modifier.height(MOD_CHIP_GAP.dp))
            val opacitySpan = OnboardingFeatures.FIRE_OPACITY_MAX - OnboardingFeatures.FIRE_OPACITY_MIN
            FireSlider(
                "Fade",
                (opacity - OnboardingFeatures.FIRE_OPACITY_MIN) / opacitySpan,
                "%.0f%%".fmt(opacity),
            ) { onOpacity(OnboardingFeatures.FIRE_OPACITY_MIN + it * opacitySpan) }
        }
    }
}

@Composable
private fun ShieldHeightCard(height: Float, onHeight: (Float) -> Unit) {
    ModCard("Shield Height", MAIN_MENU_ASSETS + "key-01.svg") {
        ShieldPreview(height)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            "Drop the raised shield down so it stops covering what you are looking at.",
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        val span = OnboardingFeatures.SHIELD_HEIGHT_MAX - OnboardingFeatures.SHIELD_HEIGHT_MIN
        FireSlider(
            "Height",
            (height - OnboardingFeatures.SHIELD_HEIGHT_MIN) / span,
            fireHeightLabel(height),
        ) { onHeight(OnboardingFeatures.SHIELD_HEIGHT_MIN + it * span) }
    }
}

@Composable
private fun MountOpacityCard(opacity: Float, onOpacity: (Float) -> Unit) {
    ModCard("Mount Opacity", ONBOARDING_ASSETS + "compass.svg") {
        HorsePreview(opacity)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            "Fade every mount you ride together so its head stays out of your way.",
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        val span = OnboardingFeatures.HORSE_OPACITY_MAX - OnboardingFeatures.HORSE_OPACITY_MIN
        FireSlider(
            "Mounts",
            (opacity - OnboardingFeatures.HORSE_OPACITY_MIN) / span,
            "%.0f%%".fmt(opacity),
        ) { onOpacity(OnboardingFeatures.HORSE_OPACITY_MIN + it * span) }
    }
}

@Composable
private fun WaveyCapesCard(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    ModCard("Wavey Capes", MAIN_MENU_ASSETS + "user-01.svg") {
        CapePreview(enabled)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            "Simulate your cape so it swings and settles as you move instead of staying a flat board.",
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        OnOffChips(enabled, onEnabled)
    }
}

@Composable
private fun SkinLayersCard(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    ModCard("3D Skin Layers", MAIN_MENU_ASSETS + "image-01.svg") {
        SkinLayersPreview(enabled)
        Spacer(Modifier.height(MOD_DESC_GAP.dp))
        OnboardingText(
            "Lift every outer layer of your skin off the model at once so hats and jackets have depth.",
            12,
            Modifier.width(MOD_PREVIEW_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        Spacer(Modifier.weight(1f))
        OnOffChips(enabled, onEnabled)
    }
}

@Composable
private fun OnOffChips(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MOD_CHIP_GAP.dp)) {
        ModeChip("On", enabled) { onEnabled(true) }
        ModeChip("Off", !enabled) { onEnabled(false) }
    }
}

@Composable
private fun FireSlider(label: String, progress: Float, value: String, onProgress: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnboardingText(
            label,
            11,
            Modifier.width(FIRE_LABEL_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        OnboardingSlider(progress, FIRE_SLIDER_WIDTH, onProgress)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.width(MOD_VALUE_WIDTH.dp).height(24.dp).clip(ppShape(6.dp)).background(ChoiceBackground)
                .border(1.dp, PanelBorderBrush, ppShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { OnboardingText(value, 11, color = TextPrimary, weight = FontWeight.Light) }
    }
}

private fun String.fmt(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)

private fun fireHeightLabel(height: Float): String =
    if (height >= -0.001f) "Vanilla" else "%.2f".fmt(height)

@Composable
private fun ItemPositionCard(
    offsetX: Float,
    offsetY: Float,
    offsetZ: Float,
    scale: Float,
    onOffset: (Float, Float, Float) -> Unit,
    onScale: (Float) -> Unit,
) {
    ModCard("Item Position", MAIN_MENU_ASSETS + "package-01.svg") {
        ItemPositionPreview(offsetX, offsetY, offsetZ, scale)
        Spacer(Modifier.weight(1f))
        OffsetSlider("X", offsetX) { onOffset(it, offsetY, offsetZ) }
        Spacer(Modifier.height(MOD_CHIP_GAP.dp))
        OffsetSlider("Y", offsetY) { onOffset(offsetX, it, offsetZ) }
        Spacer(Modifier.height(MOD_CHIP_GAP.dp))
        OffsetSlider("Z", offsetZ) { onOffset(offsetX, offsetY, it) }
        Spacer(Modifier.height(MOD_CHIP_GAP.dp))
        val scaleSpan = OnboardingFeatures.ITEM_SCALE_MAX - OnboardingFeatures.ITEM_SCALE_MIN
        LabelledSlider(
            "Size",
            (scale - OnboardingFeatures.ITEM_SCALE_MIN) / scaleSpan,
            "%.1f".fmt(scale),
        ) { onScale(snapTo(OnboardingFeatures.ITEM_SCALE_MIN + it * scaleSpan, ITEM_SCALE_STEP)) }
    }
}

private fun snapTo(value: Float, step: Float): Float = (value / step).roundToInt() * step

@Composable
private fun OffsetSlider(label: String, value: Float, onValue: (Float) -> Unit) {
    val span = OnboardingFeatures.ITEM_OFFSET_MAX - OnboardingFeatures.ITEM_OFFSET_MIN
    LabelledSlider(label, (value - OnboardingFeatures.ITEM_OFFSET_MIN) / span, "%.1f".fmt(value)) {
        onValue(snapTo(OnboardingFeatures.ITEM_OFFSET_MIN + it * span, ITEM_OFFSET_STEP))
    }
}

@Composable
private fun LabelledSlider(label: String, progress: Float, value: String, onProgress: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnboardingText(
            label,
            11,
            Modifier.width(MOD_SLIDER_LABEL_WIDTH.dp),
            TextSecondary,
            FontWeight.Light,
            TextAlign.Start,
        )
        OnboardingSlider(progress, MOD_ROW_SLIDER_WIDTH, onProgress)
        Spacer(Modifier.width(6.dp))
        OnboardingText(
            value,
            11,
            Modifier.width(MOD_ROW_VALUE_WIDTH.dp),
            TextPrimary,
            FontWeight.Light,
            TextAlign.End,
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = ppShape(6.dp)
    Box(
        Modifier
            .size(MOD_CHIP_WIDTH.dp, 26.dp)
            .clip(shape)
            .background(if (selected) Accent.asSelectedBackground else ChoiceBackground)
            .border(1.dp, if (selected) SolidColor(Accent) else PanelBorderBrush, shape)
            .clickableWithSound { if (!selected) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        OnboardingText(label, 12, color = TextPrimary, weight = if (selected) FontWeight.Medium else FontWeight.Light)
    }
}

@Composable
private fun OnboardingSlider(progress: Float, width: Float, onProgress: (Float) -> Unit) {
    val thumbSize = 11.dp
    var trackWidthPx by remember { mutableStateOf(0f) }
    val currentOnProgress by rememberUpdatedState(onProgress)
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = spring())
    Box(
        Modifier
            .width(width.dp)
            .height(thumbSize)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
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
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .clip(ppShape(3.dp))
                .background(ChoiceBackground)
                .border(1.dp, PanelBorderBrush, ppShape(3.dp)),
        ) {
            Box(Modifier.fillMaxWidth(animated).height(6.dp).background(Accent))
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (animated * (trackWidthPx - thumbSize.toPx())).roundToInt(),
                        0,
                    )
                }
                .size(thumbSize)
                .clip(ppShape(6.dp))
                .background(TextPrimary),
        )
    }
}

@Composable
private fun OptimizingPage() {
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
        OnboardingIcon(MAIN_MENU_ASSETS + "loading-02.svg", Accent, Modifier.size(40.dp).rotate(angle))
        Spacer(Modifier.height(18.dp))
        OnboardingText("Waiting to optimize game…", 16, Modifier.width(PANEL_WIDTH.dp), TextPrimary, FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        OnboardingText(
            "Measuring your frame rate to pick the best motion blur settings.",
            13,
            Modifier.width(460.dp),
            TextSecondary,
            FontWeight.Light,
        )
    }
}

@Composable
private fun SprintSection(y: Float, toggleSprint: Boolean, onToggleSprint: (Boolean) -> Unit) {
    SectionLabel("Toggle Sprint", y)
    Row(Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ChoiceButton("Enabled", ONBOARDING_ASSETS + "zap.svg", toggleSprint, 198f) { onToggleSprint(true) }
        ChoiceButton("Disabled", ONBOARDING_ASSETS + "flash-off.svg", !toggleSprint, 198f) { onToggleSprint(false) }
    }
}

@Composable
private fun MotionBlurPage(
    blurMode: Int,
    onBlurMode: (Int) -> Unit,
    motionBlur: Int,
    onMotionBlur: (Int) -> Unit,
) {
    Header("One choice left:", "Motion Blur")

    val recommended = if (AdaptiveBlurDefaults.sampled) AdaptiveBlurDefaults.recommendedMode else OnboardingFeatures.MOTION_BLUR_UNSET
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
            "The full blur experience. Only run if you get 200FPS+ in-game.",
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
            .alpha(if (blurOff) 0.4f else 1f),
    )
}

@Composable
private fun BlurStrengthSlider(motionBlur: Int, disabled: Boolean, onMotionBlur: (Int) -> Unit) {
    val steps = MOTION_BLUR_MAX - MOTION_BLUR_MIN
    Row(
        Modifier.offset(232.dp, BLUR_SLIDER_Y.dp).alpha(if (disabled) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbSize = 13.dp
        var trackWidthPx by remember { mutableStateOf(0f) }
        val progress by animateFloatAsState(
            ((motionBlur - MOTION_BLUR_MIN).toFloat() / steps).coerceIn(0f, 1f),
            animationSpec = spring(),
        )
        Box(
            Modifier
                .width(332.dp)
                .height(13.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .then(
                    if (disabled) Modifier
                    else Modifier.pointerInput(steps) {
                        val thumbPx = thumbSize.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun update(x: Float) {
                                val usableWidth = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                                val fraction = ((x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                                onMotionBlur(MOTION_BLUR_MIN + (fraction * steps).roundToInt())
                            }
                            update(down.position.x)
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                update(change.position.x)
                                change.consume()
                            } while (change.pressed)
                        }
                    },
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(332.dp)
                    .height(7.dp)
                    .clip(ppShape(4.dp))
                    .background(ChoiceBackground)
                    .border(1.dp, PanelBorderBrush, ppShape(4.dp)),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(7.dp).background(Accent))
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { androidx.compose.ui.unit.IntOffset((progress * (trackWidthPx - thumbSize.toPx())).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(ppShape(7.dp))
                    .background(TextPrimary),
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier.width(64.dp).height(26.dp).clip(ppShape(6.dp)).background(ChoiceBackground)
                .border(1.dp, PanelBorderBrush, ppShape(6.dp)),
            contentAlignment = Alignment.CenterStart,
        ) { OnboardingText(if (disabled) "Off" else motionBlur.toString(), 12, Modifier.padding(start = 8.dp)) }
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
            .background(if (selected) Accent.asSelectedBackground else ChoiceBackground)
            .border(BorderWidth, if (selected) SolidColor(Accent) else PanelBorderBrush, ButtonShape)
            .clickableWithSound(onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = MODE_CARD_FOOTER.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingIcon(icon, if (selected) Accent else TextPrimary, Modifier.size(18.dp))
                OnboardingText(title, 15, color = TextPrimary, weight = FontWeight.Medium)
            }
            Spacer(Modifier.height(9.dp))
            OnboardingText(
                description,
                12,
                Modifier.width((MODE_CARD_WIDTH - 32f).dp).heightIn(min = MODE_CARD_DESC_HEIGHT.dp),
                TextSecondary,
                FontWeight.Light,
                TextAlign.Start,
            )
            Spacer(Modifier.weight(1f))
            FpsHud(exampleFps, impactColor, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
        }
        if (recommended) {
            OnboardingText(
                "Recommended",
                11,
                Modifier.align(Alignment.BottomCenter).padding(14.dp),
                Accent,
                FontWeight.Medium,
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
        BasicText("FPS: ", style = style.copy(color = TextPrimary))
        BasicText(fps.toString(), style = style.copy(color = color))
    }
}

private val MinecraftFontFamily = FontFamily(Font("assets/oneconfig/fonts/minecraft/Minecraft-Regular.otf"))

@Composable
private fun CosmeticsPage(onClaim: () -> Unit, onStore: () -> Unit) {
    Header("Level up your drip 🔥 with", "Cosmetics")
    OnboardingText(
        "We decided to give you some for free as a warm welcome gift.\nEnjoy them, and check out the store if you want to see more!",
        15,
        Modifier.offset(215.dp, 137.dp).width(450.dp),
        TextPrimary,
        FontWeight.Light,
    )
    Row(Modifier.offset(124.dp, 209.dp), horizontalArrangement = Arrangement.spacedBy(46.dp)) {
        CosmeticCard("Starter Glasses")
        CosmeticCard("Starter Cape")
        CosmeticCard("Starter Bag")
    }
    ChoiceButton("Claim Free Cosmetics", ONBOARDING_ASSETS + "diamond.svg", true, 272f, Modifier.offset(304.dp, 445.dp), onClick = onClaim)
    ChoiceButton("Check Out the Store", ONBOARDING_ASSETS + "shopping-bag.svg", false, 272f, Modifier.offset(304.dp, 493.dp), onClick = onStore)
}

@Composable
private fun DonePage() {
    OnboardingIcon(ONBOARDING_ASSETS + "check-verified.svg", TextPrimary, Modifier.offset(374.75.dp, 157.dp).size(130.5.dp))
    OnboardingText("All Done!", 32, Modifier.offset(0.dp, 311.dp).width(PANEL_WIDTH.dp))
    OnboardingText(
        "That’s all for now, thank you for choosing OneClient! We hope you have a nice experience using it.",
        15,
        Modifier.offset(225.dp, 382.dp).width(430.dp),
        TextPrimary,
        FontWeight.Light,
    )
}

@Composable
private fun Header(kicker: String, title: String) {
    val width = LocalPanelWidth.current
    OnboardingText(kicker, 15, Modifier.offset(0.dp, 35.dp).width(width.dp), TextPrimary, FontWeight.Normal)
    OnboardingText(title, 32, Modifier.offset(0.dp, 66.dp).width(width.dp), TextPrimary, FontWeight.Normal)
}

@Composable
private fun SectionLabel(label: String, y: Float) {
    OnboardingText(label, 15, Modifier.offset(232.dp, y.dp).width(198.dp), TextPrimary, FontWeight.Normal, TextAlign.Start)
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
    onClick: () -> Unit,
) {
    val contentColor = if (primary) Color.White else TextPrimary
    Row(
        modifier
            .width(width.dp)
            .height(32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(
                when {
                    primary -> Accent
                    selected -> Accent.asSelectedBackground
                    else -> ChoiceBackground
                },
            )
            .border(BorderWidth, if (selected || primary) SolidColor(Accent) else PanelBorderBrush, ButtonShape)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingIcon(icon, contentColor, Modifier.size(17.dp))
        OnboardingText(label, 14, color = contentColor, weight = FontWeight.Medium)
    }
}

@Composable
private fun StyleCard(label: String, selected: Boolean, rounded: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(198.dp, 155.dp).clip(ButtonShape)
            .background(if (selected) Accent.asSelectedBackground else ChoiceBackground)
            .border(BorderWidth, if (selected) SolidColor(Accent) else PanelBorderBrush, ButtonShape)
            .clickableWithSound(onClick),
    ) {
        UiPreview(Modifier.offset(13.dp, 12.dp), rounded)
        OnboardingText(label, 14, Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), TextPrimary, FontWeight.Medium)
    }
}

@Composable
private fun UiPreview(modifier: Modifier, rounded: Boolean) {
    val shape = if (rounded) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    Row(modifier.size(172.dp, 108.dp).clip(shape).border(1.dp, Color(0x1AFFFFFF), shape)) {
        Column(Modifier.width(44.dp).height(108.dp).background(Color(0xB3151C22)).padding(8.dp, 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(29.dp, 7.dp).background(Accent))
            repeat(3) { Box(Modifier.width(if (it == 0) 20.dp else 29.dp).height(4.dp).background(if (it == 0) TextSecondary else TextPrimary)) }
        }
        Column(Modifier.width(128.dp).height(108.dp).background(Color(0xF211171C)).padding(8.dp, 7.dp)) {
            Row { Box(Modifier.width(43.dp).height(7.dp).background(TextPrimary)); Spacer(Modifier.width(61.dp)); Box(Modifier.size(7.dp).background(TextPrimary)) }
            Spacer(Modifier.height(8.dp))
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { Box(Modifier.size(34.dp, 23.dp).background(Color(0xFF1A2229)).border(0.dp, Color.Transparent).padding(top = 17.dp).background(Accent)) }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun CosmeticCard(label: String) {
    Box(Modifier.size(180.dp, 202.dp).clip(ppShape(10.dp)).background(ChoiceBackground).border(BorderWidth, PanelBorderBrush, ppShape(10.dp))) {
        Checkerboard(Modifier.offset(17.dp, 18.dp).size(146.dp, 146.dp).clip(ppShape(4.dp)))
        OnboardingText(label, 14, Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp), TextPrimary, FontWeight.Medium)
    }
}

@Composable
private fun Checkerboard(modifier: Modifier) {
    Canvas(modifier) {
        val cell = 12f
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var col = 0
            while (x < size.width) {
                drawRect(if ((row + col) % 2 == 0) Color(0xFF666666) else Color(0xFF4A4A4A), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                x += cell
                col++
            }
            y += cell
            row++
        }
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
) {
    val panelWidth = LocalPanelWidth.current
    val buttonY = LocalPanelHeight.current - NAV_BOTTOM_INSET
    val nextX = panelWidth - 26f - 100f
    if (page == 0) {
        if (allowSkip) {
            ChoiceButton("Skip", MAIN_MENU_ASSETS + "x-close.svg", false, 100f, Modifier.offset(26.dp, buttonY.dp), onClick = onSkip)
        }
    } else {
        ChoiceButton("Back", "assets/polyplus/ico/left-arrow.svg", false, 100f, Modifier.offset(26.dp, buttonY.dp), onClick = onBack)
    }
    if (secondaryLabel != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val hovered by interactionSource.collectIsHoveredAsState()
        Box(
            Modifier
                .offset((nextX - SECONDARY_ACTION_WIDTH - 14f).dp, buttonY.dp)
                .size(SECONDARY_ACTION_WIDTH.dp, 32.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            OnboardingText(
                secondaryLabel,
                13,
                Modifier.hoverable(interactionSource).clickableTextWithSound(onSecondary),
                color = if (hovered) TextPrimary else TextSecondary,
                weight = FontWeight.Light,
                align = TextAlign.End,
            )
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
        onClick = onNext,
    )
    Row(
        Modifier.offset(((panelWidth - (pageCount * 17f - 5f)) / 2f).dp, (buttonY + 10f).dp),
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
private fun OnboardingText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Center,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = LocalTheme.current.typography.family, textAlign = align))
}

@Composable
private fun OnboardingIcon(path: String, color: Color, modifier: Modifier) = Icon(path, color, modifier)

@Composable
private fun MotionBlurPreview(strength: Int, modifier: Modifier = Modifier) {
    val image = remember { loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png") }
    val motion = UnityMotionBlur.maxSmear(strength)
    val shape = ppShape(6.dp)

    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF273137))
            .border(1.dp, PanelBorderBrush, shape),
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
                val src = SkiaRect.makeXYWH(
                    (image.width - sourceWidth) / 2f,
                    (image.height - sourceHeight) / 2f,
                    sourceWidth.toFloat(),
                    sourceHeight.toFloat(),
                )
                val dst = SkiaRect.makeXYWH(
                    (size.width - destinationWidth) / 2f,
                    (size.height - destinationHeight) / 2f,
                    destinationWidth,
                    destinationHeight,
                )
                drawIntoCanvas {
                    UnityMotionBlur.draw(it.skiaCanvas, image, src, dst, SkiaRect.makeWH(size.width, size.height), motion)
                }
            }
        }
        val caption = if (strength == 0) "Motion blur off" else "Maximum blur at this strength"
        OnboardingText(caption, 13, Modifier.align(Alignment.Center), Color(0xBFFFFFFF), FontWeight.Light)
    }
}

@Composable
private fun PreviewFrame(caption: String, draw: DrawScope.() -> Unit) {
    val shape = ppShape(6.dp)
    Box(
        Modifier
            .size(MOD_PREVIEW_WIDTH.dp, MOD_PREVIEW_HEIGHT.dp)
            .clip(shape)
            .background(PreviewBackground)
            .border(1.dp, PanelBorderBrush, shape),
    ) {
        Canvas(Modifier.fillMaxSize(), draw)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(CAPTION_HEIGHT.dp)
                .background(CaptionScrim),
            contentAlignment = Alignment.Center,
        ) { OnboardingText(caption, 11, color = Color(0xF2FFFFFF), weight = FontWeight.Light) }
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
    PreviewFrame(if (enabled) "Simulated and waving" else "Vanilla stiff cape") {
        drawIntoCanvas { canvas -> shot?.let { canvas.skiaCanvas.drawCover(it, size.width, size.height) } }
    }
}

@Composable
private fun SkinLayersPreview(enabled: Boolean) {
    val on = remember { loadOnboardingImage(SKINLAYERS_ASSETS + "on.png") }
    val off = remember { loadOnboardingImage(SKINLAYERS_ASSETS + "off.png") }
    val shot = if (enabled) on else off
    PreviewFrame(if (enabled) "Layers with depth" else "Flat layers") {
        drawIntoCanvas { canvas -> shot?.let { canvas.skiaCanvas.drawCover(it, size.width, size.height) } }
    }
}

private fun betterGrassCaption(mode: Int): String = when (mode) {
    OnboardingFeatures.BETTER_GRASS_OFF -> "Bare dirt sides"
    OnboardingFeatures.BETTER_GRASS_FASTEST -> "Grass on every side"
    OnboardingFeatures.BETTER_GRASS_FANCY -> "Blended grass edges"
    else -> "Grass where terrain steps down"
}

internal fun betterGrassPreviewPath(mode: String): String = GRASS_ASSETS + "grass-${mode.lowercase()}.png"

@Composable
private fun BetterGrassPreview(mode: Int) {
    val shots = remember {
        OnboardingFeatures.BETTER_GRASS_MODES.map { loadOnboardingImage(betterGrassPreviewPath(it)) }
    }
    val shot = shots.getOrNull(mode)
    PreviewFrame(betterGrassCaption(mode)) {
        drawIntoCanvas { canvas -> shot?.let { canvas.skiaCanvas.drawCover(it, size.width, size.height) } }
    }
}

@Composable
private fun FireOverlayPreview(height: Double, opacity: Float) {
    val fire = remember { VanillaTextures.load(VanillaTextures.FIRE) }
    val scene = remember { loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png") }
    val shift by animateFloatAsState((-height).toFloat(), animationSpec = spring())
    val fade by animateFloatAsState(opacity / OnboardingFeatures.FIRE_OPACITY_MAX, animationSpec = spring())
    PreviewFrame(
        if (height >= OnboardingFeatures.FIRE_OVERLAY_MAX - 0.001) "Vanilla height" else "Lowered %.2f".fmt(height),
    ) {
        drawIntoCanvas { canvas -> scene?.let { canvas.skiaCanvas.drawCover(it, size.width, size.height) } }
        drawRect(SceneShade)
        val flames = fire ?: return@PreviewFrame
        val frame = SkiaRect.makeWH(flames.width.toFloat(), flames.width.toFloat())
        val dy = (FIRE_BASELINE + shift) * size.height
        for (direction in intArrayOf(-1, 1)) {
            drawIntoCanvas {
                it.skiaCanvas.drawPixelArt(
                    flames,
                    SkiaRect.makeXYWH(direction * size.width * FIRE_QUAD_OFFSET, dy, size.width, size.height),
                    frame,
                    fireTint(fade),
                )
            }
        }
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
    PreviewFrame(
        if (opacity >= OnboardingFeatures.HORSE_OPACITY_MAX - 0.5f) "Solid" else "%.0f%% opaque".fmt(opacity),
    ) {
        drawIntoCanvas { canvas ->
            val skia = canvas.skiaCanvas
            scene?.let { skia.drawCover(it, size.width, size.height) }
            val mount = horse ?: return@drawIntoCanvas
            skia.drawCover(mount, size.width, size.height, fade)
        }
    }
}

@Composable
private fun ShieldPreview(height: Float) {
    val scene = remember {
        loadOnboardingImage(SHIELD_ASSETS + SHIELD_SCENE) ?: loadOnboardingImage(ONBOARDING_ASSETS + "motion-test.png")
    }
    val shield = remember { loadOnboardingImage(SHIELD_ASSETS + SHIELD_LAYER) }
    val drop by animateFloatAsState(-height, animationSpec = spring())
    PreviewFrame(fireHeightLabel(height).let { if (it == "Vanilla") "Vanilla height" else "Lowered $it" }) {
        drawIntoCanvas { canvas ->
            val skia = canvas.skiaCanvas
            scene?.let { skia.drawCover(it, size.width, size.height) }
            val plate = shield ?: return@drawIntoCanvas
            skia.save()
            skia.translate(0f, drop * SHIELD_DROP_PER_UNIT * size.height)
            skia.drawCover(plate, size.width, size.height)
            skia.restore()
        }
    }
}

@Composable
private fun ItemPositionPreview(offsetX: Float, offsetY: Float, offsetZ: Float, scale: Float) {
    val scene = remember { loadOnboardingImage(ITEM_ASSETS + ITEM_SCENE) }
    val faces = remember { VanillaTextures.load(VanillaTextures.SWORD)?.let(::buildItemFaces).orEmpty() }
    val x by animateFloatAsState(offsetX, animationSpec = spring())
    val y by animateFloatAsState(offsetY, animationSpec = spring())
    val z by animateFloatAsState(offsetZ, animationSpec = spring())
    val itemScale by animateFloatAsState(scale, animationSpec = spring())
    val caption = if (isDefaultItemPosition(offsetX, offsetY, offsetZ, scale)) {
        "Default position"
    } else {
        "X %.1f  Y %.1f  Z %.1f  ×%.1f".fmt(offsetX, offsetY, offsetZ, scale)
    }
    PreviewFrame(caption) {
        drawIntoCanvas { canvas ->
            val skia = canvas.skiaCanvas
            scene?.let { skia.drawCover(it, size.width, size.height) }
            drawItemFaces(skia, heldItemPose(x, y, z, itemScale), faces, size.width, size.height)
        }
    }
}

private fun isDefaultItemPosition(offsetX: Float, offsetY: Float, offsetZ: Float, scale: Float): Boolean =
    offsetX == 0f && offsetY == 0f && offsetZ == 0f && scale == 1f

private fun buildItemFaces(image: SkiaImage): List<ItemFace> {
    val bitmap = runCatching { Bitmap.makeFromImage(image) }.getOrNull() ?: return emptyList()
    return bitmap.use { buildItemFaces(it) }
}

private fun buildItemFaces(bitmap: Bitmap): List<ItemFace> {
    val frameHeight = bitmap.height.coerceAtMost(bitmap.width)
    val w = bitmap.width.coerceAtMost(MAX_ITEM_TEXELS)
    val h = if (bitmap.width <= MAX_ITEM_TEXELS) {
        frameHeight
    } else {
        (frameHeight.toLong() * w / bitmap.width).toInt().coerceAtLeast(1)
    }
    val texel = { u: Int, v: Int -> bitmap.getColor(u * bitmap.width / w, v * frameHeight / h) }
    val opaque = { u: Int, v: Int ->
        u in 0 until w && v in 0 until h && (texel(u, v) ushr 24) > 128
    }
    val faces = ArrayList<ItemFace>()
    val zf = ITEM_FRONT_Z
    val zb = ITEM_BACK_Z
    for (v in 0 until h) {
        for (u in 0 until w) {
            if (!opaque(u, v)) continue
            val rgb = texel(u, v)
            val x0 = u.toFloat() / w
            val x1 = (u + 1).toFloat() / w
            val y1 = 1f - v.toFloat() / h
            val y0 = 1f - (v + 1).toFloat() / h
            faces += ItemFace(floatArrayOf(x0, y1, zf, x1, y1, zf, x1, y0, zf, x0, y0, zf), tint(rgb, SHADE_FLAT))
            if (!opaque(u + 1, v)) {
                faces += ItemFace(floatArrayOf(x1, y1, zf, x1, y1, zb, x1, y0, zb, x1, y0, zf), tint(rgb, SHADE_SIDE))
            }
            if (!opaque(u - 1, v)) {
                faces += ItemFace(floatArrayOf(x0, y1, zb, x0, y1, zf, x0, y0, zf, x0, y0, zb), tint(rgb, SHADE_SIDE))
            }
            if (!opaque(u, v - 1)) {
                faces += ItemFace(floatArrayOf(x0, y1, zb, x1, y1, zb, x1, y1, zf, x0, y1, zf), tint(rgb, SHADE_UP))
            }
            if (!opaque(u, v + 1)) {
                faces += ItemFace(floatArrayOf(x0, y0, zf, x1, y0, zf, x1, y0, zb, x0, y0, zb), tint(rgb, SHADE_DOWN))
            }
        }
    }
    return faces
}

private fun tint(argb: Int, shade: Float): Int {
    val r = ((argb ushr 16 and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    val g = ((argb ushr 8 and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    val b = ((argb and 0xFF) * shade).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private class ItemFace(val pts: FloatArray, val color: Int)

private fun drawItemFaces(
    canvas: SkiaCanvas,
    pose: HeldItemPose,
    faces: List<ItemFace>,
    width: Float,
    height: Float,
) {
    if (faces.isEmpty()) return
    val visible = ArrayList<Pair<Float, FloatArray>>(faces.size)
    val colors = ArrayList<Int>(faces.size)
    for (face in faces) {
        val view = clipNear(pose, face.pts) ?: continue
        val n = view.size / 3
        val screen = FloatArray(n * 2)
        var depth = 0f
        for (i in 0 until n) {
            val v = floatArrayOf(view[i * 3], view[i * 3 + 1], view[i * 3 + 2])
            screen[i * 2] = ndcToFrameX(v) * width
            screen[i * 2 + 1] = ndcToFrameY(v) * height
            depth += v[2]
        }
        if (signedArea(screen) <= 0f) continue
        visible += (depth / n) to screen
        colors += face.color
    }
    if (visible.isEmpty()) return
    val order = visible.indices.sortedBy { visible[it].first }
    Paint().use { paint ->
        paint.isAntiAlias = false
        for (i in order) {
            val pts = visible[i].second
            paint.color = colors[i]
            Path.Polygon(
                Array(pts.size / 2) { Point(pts[it * 2], pts[it * 2 + 1]) },
                isClosed = true,
            ).use { canvas.drawPath(it, paint) }
        }
    }
}

private fun clipNear(pose: HeldItemPose, pts: FloatArray): FloatArray? {
    val n = pts.size / 3
    val view = FloatArray(n * 3)
    var behind = 0
    for (i in 0 until n) {
        val v = pose.apply(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2])
        view[i * 3] = v[0]; view[i * 3 + 1] = v[1]; view[i * 3 + 2] = v[2]
        if (v[2] > -ITEM_NEAR) behind++
    }
    if (behind == 0) return view
    if (behind == n) return null
    val out = ArrayList<Float>((n + 1) * 3)
    for (i in 0 until n) {
        val j = (i + 1) % n
        val az = view[i * 3 + 2]
        val bz = view[j * 3 + 2]
        val aIn = az <= -ITEM_NEAR
        if (aIn) {
            out += view[i * 3]; out += view[i * 3 + 1]; out += az
        }
        if (aIn != (bz <= -ITEM_NEAR)) {
            val t = (-ITEM_NEAR - az) / (bz - az)
            for (k in 0 until 3) out += view[i * 3 + k] + t * (view[j * 3 + k] - view[i * 3 + k])
        }
    }
    return if (out.size < 9) null else out.toFloatArray()
}

private fun signedArea(p: FloatArray): Float {
    val n = p.size / 2
    var a = 0f
    for (i in 0 until n) {
        val j = (i + 1) % n
        a += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
    }
    return a
}

private fun heldItemPose(offsetX: Float, offsetY: Float, offsetZ: Float, scale: Float): HeldItemPose {
    val p = HeldItemPose()
    p.translate(0.56f, -0.52f, -0.72f)
    p.scale(0.6f)
    p.rotateY(275f)
    p.rotateZ(25f)
    val rad = 0.4363323129985824
    p.translate((-0.2 * sin(rad) + 0.4375).toFloat(), (-0.2 * cos(rad) + 0.4375).toFloat(), 0.03125f)
    p.scale(1f / 0.68f)
    p.rotateZ(-25f)
    p.rotateY(90f)
    p.translate(-1.13f * 0.0625f, -3.2f * 0.0625f, -1.13f * 0.0625f)
    p.translate(offsetX * 0.05f, offsetY * 0.05f, offsetZ * 0.05f)
    p.scale(scale)
    p.translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
    p.rotateY(-90f)
    p.rotateZ(25f)
    p.scale(0.68f)
    p.translate(-0.5f, -0.5f, -0.5f)
    return p
}

private fun ndcToFrameX(v: FloatArray): Float = (PROJ_F / PROJ_ASPECT) * v[0] / -v[2] * 0.5f + 0.5f

private fun ndcToFrameY(v: FloatArray): Float =
    (1f - (PROJ_F * v[1] / -v[2] * 0.5f + 0.5f)) * FRAME_Y_SCALE - FRAME_Y_OFFSET

private class HeldItemPose {
    private var m = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    private fun mul(o: FloatArray) {
        val r = FloatArray(16)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var acc = 0f
                for (k in 0 until 4) acc += m[i * 4 + k] * o[k * 4 + j]
                r[i * 4 + j] = acc
            }
        }
        m = r
    }

    fun translate(x: Float, y: Float, z: Float) =
        mul(floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z, 0f, 0f, 0f, 1f))

    fun scale(s: Float) =
        mul(floatArrayOf(s, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 1f))

    fun rotateY(degrees: Float) {
        val a = degrees * PI.toFloat() / 180f
        val c = cos(a)
        val s = sin(a)
        mul(floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f, 0f, 0f, 0f, 1f))
    }

    fun rotateZ(degrees: Float) {
        val a = degrees * PI.toFloat() / 180f
        val c = cos(a)
        val s = sin(a)
        mul(floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
    }

    fun apply(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        m[0] * x + m[1] * y + m[2] * z + m[3],
        m[4] * x + m[5] * y + m[6] * z + m[7],
        m[8] * x + m[9] * y + m[10] * z + m[11],
    )
}

private fun SkiaCanvas.drawPixelArt(
    image: SkiaImage,
    dst: SkiaRect,
    src: SkiaRect = SkiaRect.makeWH(image.width.toFloat(), image.height.toFloat()),
    tint: Int? = null,
) {
    if (tint == null) {
        drawImageRect(image, src, dst, SamplingMode.DEFAULT, null, true)
        return
    }
    Paint().use { paint ->
        paint.colorFilter = ColorFilter.makeBlend(tint, BlendMode.MODULATE)
        drawImageRect(image, src, dst, SamplingMode.DEFAULT, paint, true)
    }
}

private fun SkiaCanvas.drawCover(image: SkiaImage, width: Float, height: Float, alpha: Float = 1f) {
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
    val src = SkiaRect.makeXYWH(
        (image.width - sourceWidth) / 2f,
        (image.height - sourceHeight) / 2f,
        sourceWidth.toFloat(),
        sourceHeight.toFloat(),
    )
    if (alpha >= 1f) {
        drawImageRect(image, src, SkiaRect.makeWH(width, height), SamplingMode.LINEAR, null, true)
        return
    }
    Paint().use { paint ->
        paint.color = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0xFFFFFF
        drawImageRect(image, src, SkiaRect.makeWH(width, height), SamplingMode.LINEAR, paint, true)
    }
}

private fun loadOnboardingImage(path: String): SkiaImage? = runCatching {
    val bytes = PolyPlusOnboardingScreen::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
    SkiaImage.makeFromEncoded(bytes)
}.getOrNull()

@Composable
private fun TermsPage(
    document: LegalDocument?,
    accepted: Boolean,
    onAccepted: (Boolean) -> Unit,
    onDocument: (LegalDocument) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (document == null) LegalDocuments.load().onSuccess(onDocument)
    }

    val panelWidth = LocalPanelWidth.current
    Box(
        Modifier
            .offset(0.dp, TERMS_CONTENT_TOP.dp)
            .size(panelWidth.dp, (LocalPanelHeight.current - NAV_BOTTOM_INSET - 14f - TERMS_CONTENT_TOP).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.clickableTextWithSound { onAccepted(!accepted) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(accepted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnboardingText("I agree to the", 15, weight = FontWeight.Normal)
                    TermsLink("Terms of Service") {
                        ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL)
                    }
                    OnboardingText("and", 15, weight = FontWeight.Normal)
                    TermsLink("Privacy Policy") {
                        ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL)
                    }
                }
            }
            OnboardingText(
                "Declining disables crash reporting and all online features.",
                12,
                Modifier.width(panelWidth.dp),
                TextSecondary,
                FontWeight.Light,
            )
        }
    }
}

// Mirrors OneConfig's CheckboxIndicator so the onboarding checkbox matches the rest of the UI
@Composable
private fun CheckBox(checked: Boolean) {
    val theme = LocalTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .size(24.dp)
            .hoverable(interactionSource)
            .clip(theme.checkBoxShape)
            .background(if (checked) Accent else theme.componentBackground)
            .border(
                1.5.dp,
                when {
                    checked -> Accent
                    hovered -> theme.textColorSecondary
                    else -> theme.borderColor
                },
                theme.checkBoxShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) OnboardingIcon("tick", theme.textColor, Modifier.size(26.dp))
    }
}

@Composable
private fun TermsLink(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.clickableTextWithSound(onClick),
        TextStyle(
            color = Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = LocalTheme.current.typography.family,
            textDecoration = TextDecoration.Underline,
        ),
    )
}

private enum class OnboardingPage { TERMS, LOOK_AND_FEEL, MODS, MODS_MORE, MOTION_BLUR, COSMETICS, DONE }

private enum class ModCard { GRASS, FIRE_OVERLAY, SHIELD_HEIGHT, MOUNT, CAPES, SKIN_LAYERS, ITEM }

private class ModReads(
    val grass: Int?,
    val fireHeight: Double?,
    val fireOpacity: Float?,
    val shield: Float?,
    val horse: Float?,
    val capes: Boolean?,
    val layers: Boolean?,
    val itemX: Float?,
    val itemY: Float?,
    val itemZ: Float?,
    val itemScale: Float?,
) {
    val cards: List<ModCard> = buildList {
        if (grass != null) add(ModCard.GRASS)
        if (fireHeight != null && (!OnboardingFeatures.fireOverlayOpacityAvailable || fireOpacity != null)) {
            add(ModCard.FIRE_OVERLAY)
        }
        if (shield != null) add(ModCard.SHIELD_HEIGHT)
        if (itemX != null && itemY != null && itemZ != null && itemScale != null) add(ModCard.ITEM)
        if (horse != null) add(ModCard.MOUNT)
        if (capes != null) add(ModCard.CAPES)
        if (layers != null) add(ModCard.SKIN_LAYERS)
    }

    companion object {
        fun read(enabled: Boolean): ModReads {
            fun <T> ifAvailable(available: Boolean, read: () -> T?): T? =
                if (enabled && available) read() else null
            val items = enabled && OnboardingFeatures.itemPositionsAvailable
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
                itemX = ifAvailable(items) { OnboardingFeatures.currentItemOffsetX() },
                itemY = ifAvailable(items) { OnboardingFeatures.currentItemOffsetY() },
                itemZ = ifAvailable(items) { OnboardingFeatures.currentItemOffsetZ() },
                itemScale = ifAvailable(items) { OnboardingFeatures.currentItemScale() },
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
private const val MOTION_BLUR_MAX = 10

private const val CONTENT_TOP = 140f
private const val CONTENT_BOTTOM = 557f
private const val SECTION_GAP = 24f
private const val LABEL_HEIGHT = 32f
private const val SPRINT_SECTION_HEIGHT = LABEL_HEIGHT + 32f

private const val MOD_CARD_WIDTH = 240f
private const val MOD_CARD_HEIGHT = 300f
private const val MOD_CARD_GAP = 18f
private const val MOD_CARD_PADDING = 16f
private const val MOD_PREVIEW_WIDTH = MOD_CARD_WIDTH - MOD_CARD_PADDING * 2f
private const val MOD_PREVIEW_HEIGHT = 104f
private const val MOD_CHIP_GAP = 6f
private const val MOD_DESC_GAP = 10f
private const val MOD_SLIDER_LABEL_WIDTH = 28f
private const val MOD_ROW_VALUE_WIDTH = 24f
private const val MOD_ROW_SLIDER_WIDTH = MOD_PREVIEW_WIDTH - MOD_SLIDER_LABEL_WIDTH - MOD_ROW_VALUE_WIDTH - 6f
private const val ITEM_OFFSET_STEP = 0.5f
private const val ITEM_SCALE_STEP = 0.1f

private const val ITEM_SCENE = "item-scene.png"
private const val MAX_ITEM_TEXELS = 32
private const val ITEM_FRONT_Z = 8.5f / 16f
private const val ITEM_BACK_Z = 7.5f / 16f
private const val ITEM_NEAR = 0.05f
private const val ITEM_LIGHT = 0.785f
private const val SHADE_FLAT = 0.8f * ITEM_LIGHT
private const val SHADE_UP = 1.0f * ITEM_LIGHT
private const val SHADE_DOWN = 0.5f * ITEM_LIGHT
private const val SHADE_SIDE = 0.6f * ITEM_LIGHT
private const val PROJ_F = 1.4281480f
private const val PROJ_ASPECT = 3024f / 1898f
private const val FRAME_Y_SCALE = 1898f / 1512f
private const val FRAME_Y_OFFSET = 156f / 1512f
private const val MOD_CHIP_WIDTH = (MOD_PREVIEW_WIDTH - MOD_CHIP_GAP) / 2f
private const val MOD_VALUE_WIDTH = 58f
private const val MOD_CARDS_PER_PAGE = 3
private const val FIRE_LABEL_WIDTH = 38f
private const val FIRE_SLIDER_WIDTH = MOD_PREVIEW_WIDTH - FIRE_LABEL_WIDTH - MOD_VALUE_WIDTH - 8f

private const val CAPE_WAVE_FRAMES = 8
private const val CAPE_WAVE_LOOP_MS = 1080

private const val CAPTION_HEIGHT = 17f
private val CaptionScrim = Color(0xA6000000)
private const val FIRE_QUAD_OFFSET = 0.12f
private const val FIRE_BASELINE = 0.3f
private const val FIRE_ALPHA = 0xCC
private const val SHIELD_DROP_PER_UNIT = 1.0f
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
private const val TERMS_PANEL_HEIGHT = 170f
private const val TERMS_CONTENT_TOP = 30f
private const val NAV_BOTTOM_INSET = 56f
private const val SECONDARY_ACTION_WIDTH = 120f

private val LocalPanelWidth = compositionLocalOf { PANEL_WIDTH }
private val LocalPanelHeight = compositionLocalOf { PANEL_HEIGHT }
private const val ONBOARDING_ASSETS = "assets/polyplus/onboarding/"
private const val GRASS_ASSETS = "assets/polyplus/onboarding/bettergrass/"
private const val ITEM_ASSETS = "assets/polyplus/onboarding/itemposition/"
private const val MOUNT_ASSETS = "assets/polyplus/onboarding/mountopacity/"
private const val MOUNT_SCENE = "mount-scene.png"
private const val MOUNT_FULL = "mount-full.png"
private const val WAVEY_ASSETS = "assets/polyplus/onboarding/waveycapes/"
private const val SKINLAYERS_ASSETS = "assets/polyplus/onboarding/skinlayers/"
private const val SHIELD_ASSETS = "assets/polyplus/onboarding/shield/"
private const val SHIELD_SCENE = "shield-scene.png"
private const val SHIELD_LAYER = "shield-layer.png"
private const val MAIN_MENU_ASSETS = "assets/polyplus/mainmenu/"

private val PANEL_SHAPE: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(9.dp)
private val ButtonShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(9.dp)
private val BorderWidth = 1.5.dp
private const val PanelBorderAngleDeg = 20.0

private val PageBackground: Color
    @Composable get() = LocalTheme.current.pageBackground
private val ShadowColor = Color(0x26000000)

private val ChoiceBackground: Color
    @Composable get() = LocalTheme.current.componentBackground.copy(alpha = 0.5f)
private val TextPrimary: Color
    @Composable get() = LocalTheme.current.textColor
private val TextSecondary: Color
    @Composable get() = LocalTheme.current.textColorSecondary
private val Color.asSelectedBackground: Color get() = copy(alpha = 0.22f)

private const val HUD_TEXT_SIZE = 21f
private const val HUD_PADDING = HUD_TEXT_SIZE * (4f / 9f)
private const val HUD_RADIUS = HUD_TEXT_SIZE * (4f / 9f)
private val HudBackground = Color(0x80000000)
private val ImpactGood = Color(0xFF6FD08C)
private val ImpactWarn = Color(0xFFE7B85C)
private val ImpactHeavy = Color(0xFFE8836B)

private val PanelBorderBrush: Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(PanelBorderAngleDeg)
        val ux = kotlin.math.cos(radians).toFloat()
        val uy = kotlin.math.sin(radians).toFloat()
        val len = size.width * ux + size.height * uy
        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(ux * len, uy * len),
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.5f),
            ),
            colorStops = listOf(0f, 0.5f, 1f),
        )
    }
}
