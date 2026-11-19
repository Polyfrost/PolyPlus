package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.features.BlockHighlightPresets
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.OnboardingFeatures

@Composable
internal fun LiveWorldPreview(tweak: LiveTweak, ours: Boolean, modifier: Modifier) = when (tweak) {
    LiveTweak.FIRE -> {
        val tuning = LocalLiveTuning.current
        RenderFirePreview(
            if (ours) tuning.fireHeight.toFloat() else OnboardingFeatures.FIRE_OVERLAY_MAX.toFloat(),
            if (ours) tuning.fireOpacity else OnboardingFeatures.FIRE_OPACITY_VANILLA,
            modifier,
        )
    }
    LiveTweak.SHIELD -> LiveWorldShieldPreview(
        if (ours) LocalLiveTuning.current.shieldHeight else OnboardingFeatures.SHIELD_HEIGHT_MAX,
        modifier,
    )
    LiveTweak.FULLBRIGHT -> LiveWorldCavePreview(if (ours) LiveOurs.GAMMA / PERCENT else vanillaBrightness(), modifier)
    LiveTweak.DYNAMIC_LIGHTS -> LiveWorldTunnelPreview(
        vanillaBrightness(),
        if (ours) LiveOurs.DYNAMIC_LIGHTS_MODE else OnboardingFeatures.DYNAMIC_LIGHTS_OFF,
        modifier,
    )
    LiveTweak.BETTER_GRASS -> LiveWorldGrassPreview(if (ours) LiveOurs.BETTER_GRASS_MODE else OnboardingFeatures.BETTER_GRASS_OFF, modifier)
    LiveTweak.BLOCK_OUTLINE -> {
        val tuning = LocalLiveTuning.current
        LiveWorldOutlinePreview(if (ours) tuning.outlinePreset else null, if (ours) tuning.outlineColor else 0, modifier)
    }
    else -> Box(modifier)
}

@Composable
internal fun <T : RenderBlocksDiorama> LiveWorldCanvas(
    label: String,
    modifier: Modifier,
    background: Brush,
    create: () -> T,
    configure: (T) -> Unit,
    overlay: (DrawScope.(T, Float) -> Unit)? = null,
) {
    val diorama = remember { create() }
    val slot = remember(diorama) { RenderBlocksSlot(label, diorama) }
    SideEffect { configure(diorama) }
    RenderBlocksCanvas(slot, modifier.background(background), overlay?.let { draw -> { scale -> draw(diorama, scale) } })
}

@Composable
internal fun LiveWorldFirstPerson(label: String, modifier: Modifier, hud: LiveWorldHudContent) {
    LiveWorldCanvas(
        label,
        modifier,
        HillBackground,
        create = { LiveWorldHill(label, LiveWorldShot.FIRST_PERSON) },
        configure = { it.hud = hud },
    )
}

@Composable
private fun LiveWorldCavePreview(brightness: Float, modifier: Modifier) {
    LiveWorldCanvas(
        "polyplus_live_cave",
        modifier,
        CaveBackground,
        create = { LiveWorldCave("polyplus_live_cave", brightness) },
        configure = { it.brightness = brightness },
    )
}

@Composable
private fun LiveWorldTunnelPreview(brightness: Float, heldLightMode: Int, modifier: Modifier) {
    LiveWorldCanvas(
        "polyplus_live_tunnel",
        modifier,
        CaveBackground,
        create = { LiveWorldTunnel("polyplus_live_tunnel", brightness, heldLightMode) },
        configure = {
            it.brightness = brightness
            it.heldLightMode = heldLightMode
        },
    )
}

@Composable
private fun LiveWorldGrassPreview(mode: Int, modifier: Modifier) {
    LiveWorldCanvas(
        "polyplus_live_grass",
        modifier,
        HillBackground,
        create = { LiveWorldHill("polyplus_live_grass", LiveWorldShot.HILLSIDE) },
        configure = { it.betterGrassMode = mode },
    )
}

@Composable
private fun LiveWorldOutlinePreview(preset: String?, color: Int, modifier: Modifier) {
    val base = remember(preset) { outlineStyle(preset) }
    val style = remember(base, color) { if (color == 0) base else recolor(base, color) }
    val startMillis = remember { System.currentTimeMillis() }
    LiveWorldCanvas(
        "polyplus_live_outline",
        modifier,
        HillBackground,
        create = { LiveWorldHill("polyplus_live_outline", LiveWorldShot.LOOK_AT) },
        configure = {},
        overlay = { hill, pixelScale ->
            LiveWorldOutline.draw(this, style, hill.viewProjection, hill.eye, LiveWorldHill.TARGET, System.currentTimeMillis() - startMillis, pixelScale, hill::isEmpty)
        },
    )
}

private fun outlineStyle(preset: String?): BlockHighlightStyle {
    val json = preset?.let { runCatching { BlockHighlightPresets.presetJson(it) }.getOrNull() } ?: VANILLA_OUTLINE
    return runCatching { BlockHighlightStyle.parse(json) }.getOrElse { BlockHighlightStyle.parse(VANILLA_OUTLINE) }
}

private fun recolor(style: BlockHighlightStyle, argb: Int): BlockHighlightStyle {
    val rgb = argb and RGB
    val alpha = (argb ushr ALPHA_SHIFT) and CHANNEL
    fun BlockHighlightStyle.Colors.tinted() = BlockHighlightStyle.Colors(rgb, rgb, this.alpha * alpha / CHANNEL, null)
    return BlockHighlightStyle(
        style.layers.map {
            BlockHighlightStyle.Layer(
                it.colors.tinted(), it.width, it.depth, it.faces, it.scale,
                it.cutFromCenter, it.cutFromCorner, it.innerMult, it.outerMult,
            )
        },
        style.fill?.let { BlockHighlightStyle.Fill(it.colors.tinted(), it.depth, it.faces, it.scale) },
    )
}

private fun vanillaBrightness(): Float =
    runCatching { Minecraft.getInstance().options.gamma().get().toFloat() }.getOrDefault(DEFAULT_BRIGHTNESS).coerceIn(0f, 1f)

private val HillBackground = SolidColor(Color(0xFFC0D8FF))
private val CaveBackground = Brush.verticalGradient(listOf(Color(0xFF11161B), Color(0xFF07090B)))
private const val RGB = 0xFFFFFF
private const val CHANNEL = 0xFF
private const val ALPHA_SHIFT = 24
private const val VANILLA_OUTLINE = """{"drawVanillaOutline":true,"enableModRendering":false}"""
private const val PERCENT = 100f
private const val DEFAULT_BRIGHTNESS = 0.5f
//?} else {
/*import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun LiveWorldPreview(@Suppress("UNUSED_PARAMETER") tweak: LiveTweak, @Suppress("UNUSED_PARAMETER") ours: Boolean, modifier: Modifier) = Box(modifier)
*///?}
