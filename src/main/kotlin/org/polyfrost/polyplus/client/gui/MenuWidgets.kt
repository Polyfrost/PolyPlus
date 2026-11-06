@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import java.util.concurrent.ConcurrentHashMap

internal object MainMenuRasterAssets {
    private val paths = listOf(
        "avatar.png",
        "hypixel.png",
        "server.png",
    ).map { ASSETS + it }
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    var ready by mutableStateOf(false)
        private set

    fun preload() {
        paths.forEach { load(it) }
        ready = true
    }

    fun cached(path: String): ImageBitmap? = cache[path]

    private fun load(path: String): ImageBitmap? =
        cache[path] ?: runCatching {
            val bytes = MainMenuRasterAssets::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
            PolyBitmap.decode(bytes)!!.asComposeImageBitmap()
        }.getOrNull()?.also { cache[path] = it }
}

internal const val ASSETS = "assets/polyplus/mainmenu/"
private const val ONBOARDING_ASSETS = "assets/polyplus/onboarding/"

internal val PanelBackground: Color
    @Composable get() = OneConfigTheme.current.componentBackground.copy(alpha = 0.5f)
private val ServerIconBackground = Color(0x33FFFFFF)

internal val PanelShape: Shape
    @Composable
    get() = ppShape(9.dp)

internal val Outfit: FontFamily by lazy {
    runCatching {
        val bytes = MainMenuRasterAssets::class.java
            .getResourceAsStream("/${ASSETS}font/Outfit-Bold.ttf")!!.use { it.readBytes() }
        FontFamily(Font("Outfit-Bold", bytes, FontWeight.Bold))
    }.getOrDefault(FontFamily.Default)
}

internal const val REFERENCE_GUI_SCALE = 2f

internal const val GUI_DENSITY_TRIM = 0.88f

internal fun usableScale(value: Float): Float = if (value.isFinite() && value > 0f) value else 1f

internal fun usableSize(width: Float, height: Float): Boolean = width.isFinite() && height.isFinite() && width > 0f && height > 0f

@Composable
internal fun scaledDensity(scale: Float): Density {
    val density = LocalDensity.current
    return remember(density, scale) { Density(usableScale(density.density * scale), usableScale(density.fontScale)) }
}

/** Sizing of the title screen's player preview, which grows to fit a hat cosmetic. */
internal class MenuPreviewSpec(val height: Dp, val modelScale: Float, val fadeStart: Float)

internal fun menuPreviewSpec(): MenuPreviewSpec {
    val hasHeadCosmetic = CosmeticCatalog
        .localEquipped().equipped
        .containsKey(BodySlot.Hat)
    return if (hasHeadCosmetic) MenuPreviewSpec(270.dp, 0.82f, 0.784f) else MenuPreviewSpec(210.dp, 1.05f, 0.72243f)
}

internal const val MENU_PREVIEW_YAW = 180f + 22.9f

@Composable
internal fun MenuPlayerPreview(modifier: Modifier) {
    val spec = menuPreviewSpec()
    Box(modifier = modifier.height(spec.height)) {
        PlayerPreview(
            Modifier.fillMaxWidth().height(spec.height),
            source = PlayerPreviewSource.LocalLive,
            modelScale = spec.modelScale,
            verticalAnchor = 1.0f,
            initialYaw = MENU_PREVIEW_YAW,
            live = true,
            bottomFadeFraction = 1f - spec.fadeStart,
        )
    }
}

@Composable
internal fun SocialPill(screen: Screen, modifier: Modifier) {
    val groups by GroupsRepository.groups.collectAsState()
    PillButton(
        "Social",
        ASSETS + "message-chat-circle.svg",
        modifier,
        onClick = { SocialOverlay.open(screen) },
        badge = groups.any { it.unread },
    )
}

@Composable
internal fun WardrobePill(modifier: Modifier) {
    PillButton(
        "Wardrobe",
        ASSETS + "diamond-01.svg",
        modifier,
        onClick = { PolyPlusOneConfigIntegration.openCosmetics() },
    )
}

@Composable
internal fun HostWorldButton(screen: Screen, modifier: Modifier = Modifier.fillMaxWidth()) {
    PillButton(
        label = "Host World",
        icon = ASSETS + "log-in-04.svg",
        modifier = modifier,
        onClick = { SocialOverlay.openHostCurrentWorld(screen) },
    )
}

internal fun switchToVanillaMenu() = MainMenuReplacement.switchToVanilla()

internal fun toggleFullscreen() {
    val mc = Minecraft.getInstance()
    //? if >= 26.3 {
    // the window follows the option now
    mc.options.fullscreen().set(!mc.options.fullscreen().get())
    mc.options.save()
    //?} elif > 1.8.9 {
    /*mc.window.toggleFullScreen()
    *///?} else {
    /*mc.toggleFullscreen()
    *///?}
}

internal class ThemeToggle(val icon: String, val label: String, val toggle: () -> Unit)

@Composable
internal fun themeToggle(): ThemeToggle {
    val light = OneConfigTheme.current.isLightTheme
    val minecraftStyle = OneConfigTheme.current.isMinecraftStyle
    return ThemeToggle(
        icon = if (light) ONBOARDING_ASSETS + "sun.svg" else ASSETS + "moon-star.svg",
        label = if (light) "Switch to dark theme" else "Switch to light theme",
        toggle = {
            PolyPlusConfig.onboardingLightTheme = !light
            OnboardingFeatures.applyTheme(!light, if (minecraftStyle) 1 else 0)
            PolyPlusConfig.save()
        },
    )
}

@Composable
internal fun FooterBrandText(platform: String, fontSize: TextUnit = 13.sp, primary: Color = Color.White) {
    val bodyFont = OneConfigTheme.current.fontFamily
    val secondary = SocialTextSecondary

    BasicText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold, fontFamily = Outfit)) {
                append("ONECLIENT")
            }
            withStyle(SpanStyle(color = secondary, fontFamily = bodyFont)) {
                append("   ")
                append(platform)
            }
        },
        style = TextStyle(
            fontSize = fontSize,
            fontFamily = bodyFont,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
internal fun Modifier.pillSurface(onClick: (() -> Unit)?, borderBrush: Brush = SocialPanelBorderBrush, enabled: Boolean = true): Modifier =
    clip(PanelShape)
        .background(PanelBackground)
        .border(SocialPanelBorderWidth, borderBrush, PanelShape)
        .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
        .then(if (enabled && onClick != null) Modifier.clickableWithSound(onClick) else Modifier)

@Composable
internal fun PillButton(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}, borderBrush: Brush = SocialPanelBorderBrush, badge: Boolean = false) {
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .pillSurface(onClick, borderBrush)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemedIcon(icon, SocialTextPrimary, 20.dp)
            SocialText(label, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        if (badge) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(8.dp)
                    .clip(OneConfigTheme.current.circleShape)
                    .background(OneConfigTheme.accent),
            )
        }
    }
}

internal enum class TooltipPlacement { BELOW_END, ABOVE_CENTER }

/** A tooltip bubble placed against the composable it's called in, wrapping at [maxWidth]. */
@Composable
internal fun HoverTooltip(text: String, placement: TooltipPlacement, maxWidth: Dp = Dp.Unspecified) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(gap, placement) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = when (placement) {
                TooltipPlacement.BELOW_END ->
                    IntOffset(anchorBounds.right - popupContentSize.width, anchorBounds.bottom + gap)
                TooltipPlacement.ABOVE_CENTER -> IntOffset(
                    anchorBounds.center.x - popupContentSize.width / 2,
                    anchorBounds.top - gap - popupContentSize.height,
                )
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        TooltipBubble(text, maxWidth)
    }
}

@Composable
private fun TooltipBubble(text: String, maxWidth: Dp) {
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .sceneFrost(9.dp)
            .clip(PanelShape)
            .background(OneConfigTheme.current.popupBackground)
            .border(SocialPanelBorderWidth, OneConfigTheme.current.borderColor, PanelShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(text, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun RasterImage(
    path: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val bitmap = rememberRaster(path)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, alignment = alignment, contentScale = contentScale)
    } else {
        Box(modifier.background(ServerIconBackground))
    }
}

@Composable
internal fun rememberFavicon(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    if (bytes == null || bytes.isEmpty()) null
    else runCatching { PolyBitmap.decode(bytes)!!.asComposeImageBitmap() }.getOrNull()
}

@Composable
private fun rememberRaster(path: String): ImageBitmap? =
    if (MainMenuRasterAssets.ready) MainMenuRasterAssets.cached(path) else null

fun playerName(): String = runCatching {
    Minecraft.getInstance().user.name
}.getOrDefault("Player")

internal fun platformLabel(): String = runCatching {
    //? if fabric || ornithe {
    val loaderName = "Fabric"
    val mcVersion = FabricLoader.getInstance()
        .getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("")
    //?} else {
    /*val loaderName = "NeoForge"
    val mcVersion = net.minecraft.SharedConstants.getCurrentVersion().name
    *///?}
    if (mcVersion.isBlank()) loaderName else "$loaderName $mcVersion"
}.getOrDefault("Fabric")
