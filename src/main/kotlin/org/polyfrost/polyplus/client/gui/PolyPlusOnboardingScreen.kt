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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Matrix33
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
import kotlin.math.roundToInt

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
            /*.panorama()
            .extractRenderState(ctx, width, height)
            *///?} else {
            .getPanorama()
            .extractRenderState(ctx, width, height, true)
            //?}
        ctx.blurBeforeThisStratum()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    @Composable
    override fun compose() {
        val pages = remember {
            buildList {
                add(OnboardingPage.LOOK_AND_FEEL)
                if (OnboardingFeatures.modsPageAvailable) add(OnboardingPage.MODS)
                // add(OnboardingPage.COSMETICS)
                add(OnboardingPage.DONE)
            }
        }
        var page by remember { mutableIntStateOf(0) }
        var lightTheme by remember { mutableStateOf(PolyPlusConfig.onboardingLightTheme) }
        var uiStyle by remember { mutableIntStateOf(PolyPlusConfig.onboardingUiStyle) }
        var toggleSprint by remember { mutableStateOf(PolyPlusConfig.onboardingToggleSprint) }
        var motionBlur by remember { mutableIntStateOf(PolyPlusConfig.onboardingMotionBlur.coerceIn(0, MOTION_BLUR_MAX)) }
        var performanceMode by remember { mutableStateOf(PolyPlusConfig.onboardingMotionBlur <= 0) }
        LaunchedEffect(AdaptiveBlurDefaults.sampled) {
            if (AdaptiveBlurDefaults.sampled) performanceMode = AdaptiveBlurDefaults.recommendsPerformance
        }
        val maxGuiScale = remember { OnboardingFeatures.maxGuiScale() }
        var guiScale by remember {
            mutableIntStateOf(
                net.minecraft.client.Minecraft.getInstance().options.guiScale().get().coerceIn(0, maxGuiScale),
            )
        }
        LaunchedEffect(lightTheme, uiStyle) {
            OnboardingFeatures.applyTheme(lightTheme, uiStyle)
        }
        val finish = {
            PolyPlusConfig.onboardingLightTheme = lightTheme
            PolyPlusConfig.onboardingUiStyle = uiStyle
            PolyPlusConfig.onboardingToggleSprint = toggleSprint
            PolyPlusConfig.onboardingMotionBlur = if (performanceMode) 0 else motionBlur
            PolyPlusConfig.onboardingGuiScale = guiScale
            PolyPlusConfig.onboardingCompleted = true
            PolyPlusConfig.save()
            OnboardingFeatures.applySavedSettings()
            val mc = net.minecraft.client.Minecraft.getInstance()
            //? if >= 26.2 {
            /*mc.gui.setScreen(PolyPlusMainMenuScreen())
            *///?} else {
            mc.setScreen(PolyPlusMainMenuScreen())
            //?}
        }

        val waitingForOptimization =
            pages[page] == OnboardingPage.MODS && OnboardingFeatures.polyBlurAvailable && !AdaptiveBlurDefaults.sampled

        Theme {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val guiScaleFactor = guiScaleFactorFor(if (guiScale <= 0) maxGuiScale else guiScale)
                val scale = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT) *
                    guiScaleFactor * UI_SCALE * GUI_DENSITY_TRIM
                CompositionLocalProvider(LocalUiOversample provides (LocalUiOversample.current * scale.coerceAtLeast(1f))) {
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
                                .offset(PANEL_X.dp, PANEL_Y.dp)
                                .size(PANEL_WIDTH.dp, PANEL_HEIGHT.dp)
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
                                OnboardingPage.LOOK_AND_FEEL ->
                                    LookAndFeelPage(
                                        lightTheme, { lightTheme = it },
                                        uiStyle, { uiStyle = it },
                                        guiScale, maxGuiScale, { guiScale = it },
                                    )
                                OnboardingPage.MODS ->
                                    if (waitingForOptimization) {
                                        OptimizingPage()
                                    } else {
                                        ModsPage(
                                            toggleSprint,
                                            { toggleSprint = it },
                                            motionBlur,
                                            { motionBlur = it },
                                            performanceMode,
                                            { enablePerformance ->
                                                performanceMode = enablePerformance
                                                if (!enablePerformance && motionBlur < 1) motionBlur = DEFAULT_QUALITY_BLUR
                                            },
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
                            BottomNavigation(
                                page,
                                pages.size,
                                onSkip = finish,
                                onBack = { page-- },
                                onNext = { if (page == pages.size - 1) finish() else page++ },
                                nextEnabled = !waitingForOptimization,
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
    toggleSprint: Boolean,
    onToggleSprint: (Boolean) -> Unit,
    motionBlur: Int,
    onMotionBlur: (Int) -> Unit,
    performanceMode: Boolean,
    onPerformanceMode: (Boolean) -> Unit,
) {
    Header("Continuing with", "Mods")
    val sprint = OnboardingFeatures.polySprintAvailable
    val blur = OnboardingFeatures.polyBlurAvailable
    val heights = buildList {
        if (sprint) add(SPRINT_SECTION_HEIGHT)
        if (blur) add(BLUR_SECTION_HEIGHT)
    }
    val total = heights.sum() + SECTION_GAP * (heights.size - 1).coerceAtLeast(0)
    var y = CONTENT_TOP + ((CONTENT_BOTTOM - CONTENT_TOP) - total) / 2f
    if (sprint) {
        SprintSection(y, toggleSprint, onToggleSprint)
        y += SPRINT_SECTION_HEIGHT + SECTION_GAP
    }
    if (blur) MotionBlurSection(y, motionBlur, onMotionBlur, performanceMode, onPerformanceMode)
}

@Composable
private fun OptimizingPage() {
    Header("Continuing with", "Mods")
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
private fun MotionBlurSection(
    y: Float,
    motionBlur: Int,
    onMotionBlur: (Int) -> Unit,
    performanceMode: Boolean,
    onPerformanceMode: (Boolean) -> Unit,
) {
    SectionLabel("Motion Blur", y)
    Row(
        Modifier.offset(232.dp, (y + LABEL_HEIGHT).dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ChoiceButton("Performance", ONBOARDING_ASSETS + "flash-off.svg", performanceMode, 198f) { onPerformanceMode(true) }
        ChoiceButton("Quality", "assets/polyplus/ico/stars.svg", !performanceMode, 198f) { onPerformanceMode(false) }
    }

    val displayStrength = if (performanceMode) 0 else motionBlur
    Row(
        Modifier.offset(232.dp, (y + BLUR_SLIDER_OFFSET).dp).alpha(if (performanceMode) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbSize = 13.dp
        var trackWidthPx by remember { mutableStateOf(0f) }
        val progress by animateFloatAsState(
            (displayStrength.toFloat() / MOTION_BLUR_MAX).coerceIn(0f, 1f),
            animationSpec = spring(),
        )
        Box(
            Modifier
                .width(332.dp)
                .height(13.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .then(
                    if (performanceMode) Modifier
                    else Modifier.pointerInput(MOTION_BLUR_MAX) {
                        val thumbPx = thumbSize.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun update(x: Float) {
                                val usableWidth = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                                val progress = ((x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                                onMotionBlur((progress * MOTION_BLUR_MAX).roundToInt())
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
        ) { OnboardingText(displayStrength.toString(), 12, Modifier.padding(start = 8.dp)) }
    }
    MotionBlurPreview(
        displayStrength,
        Modifier.offset(233.5.dp, (y + BLUR_PREVIEW_OFFSET).dp).size(413.dp, BLUR_PREVIEW_HEIGHT.dp)
            .alpha(if (performanceMode) 0.4f else 1f),
    )
}

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
    OnboardingText(kicker, 15, Modifier.offset(0.dp, 35.dp).width(PANEL_WIDTH.dp), TextPrimary, FontWeight.Normal)
    OnboardingText(title, 32, Modifier.offset(0.dp, 66.dp).width(PANEL_WIDTH.dp), TextPrimary, FontWeight.Normal)
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
private fun BottomNavigation(page: Int, pageCount: Int, onSkip: () -> Unit, onBack: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean = true) {
    if (page == 0) {
        ChoiceButton("Skip", MAIN_MENU_ASSETS + "x-close.svg", false, 100f, Modifier.offset(26.dp, 604.dp), onClick = onSkip)
    } else {
        ChoiceButton("Back", "assets/polyplus/ico/left-arrow.svg", false, 100f, Modifier.offset(26.dp, 604.dp), onClick = onBack)
    }
    ChoiceButton(if (page == pageCount - 1) "Finish" else "Next", "assets/polyplus/ico/right-arrow.svg", false, 100f, Modifier.offset(754.dp, 604.dp), primary = true, enabled = nextEnabled, onClick = onNext)
    Row(Modifier.offset(((PANEL_WIDTH - (pageCount * 17f - 5f)) / 2f).dp, 614.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
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
                val localMatrix = Matrix33
                    .makeTranslate((size.width - destinationWidth) / 2f, (size.height - destinationHeight) / 2f)
                    .makeConcat(Matrix33.makeScale(destinationWidth / sourceWidth, destinationHeight / sourceHeight))
                    .makeConcat(Matrix33.makeTranslate(-(image.width - sourceWidth) / 2f, -(image.height - sourceHeight) / 2f))
                drawIntoCanvas {
                    UnityMotionBlur.draw(it.skiaCanvas, image, localMatrix, size.width, size.height, motion)
                }
            }
        }
        val caption = if (strength == 0) "Motion blur off" else "Maximum blur at this strength"
        OnboardingText(caption, 13, Modifier.align(Alignment.Center), Color(0xBFFFFFFF), FontWeight.Light)
    }
}

private fun loadOnboardingImage(path: String): SkiaImage? = runCatching {
    val bytes = PolyPlusOnboardingScreen::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
    SkiaImage.makeFromEncoded(bytes)
}.getOrNull()

private enum class OnboardingPage { LOOK_AND_FEEL, MODS, COSMETICS, DONE }

private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f
private const val UI_SCALE = DESIGN_WIDTH / 1240f
private const val PANEL_X = 520f
private const val PANEL_Y = 210f
private const val PANEL_WIDTH = 880f
private const val PANEL_HEIGHT = 660f
private const val MOTION_BLUR_MAX = 10
private const val DEFAULT_QUALITY_BLUR = 3

private const val CONTENT_TOP = 140f
private const val CONTENT_BOTTOM = 557f
private const val SECTION_GAP = 24f
private const val LABEL_HEIGHT = 32f
private const val SPRINT_SECTION_HEIGHT = LABEL_HEIGHT + 32f
private const val BLUR_SLIDER_OFFSET = LABEL_HEIGHT + 32f + 16f // label, radio row, gap
private const val BLUR_PREVIEW_OFFSET = BLUR_SLIDER_OFFSET + 26f + 17f // + slider row + gap
private const val BLUR_PREVIEW_HEIGHT = 115f
private const val BLUR_SECTION_HEIGHT = BLUR_PREVIEW_OFFSET + BLUR_PREVIEW_HEIGHT
private const val ONBOARDING_ASSETS = "assets/polyplus/onboarding/"
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
