@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.compose.minecraft.screen.ComposeScreen
import org.polyfrost.polyplus.client.cosmetics.CosmeticSwatches
import org.polyfrost.polyplus.client.cosmetics.SwatchArt
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateChoice
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.NameplateTryOn
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if > 1.8.9 {
import net.minecraft.client.CameraType
import org.polyfrost.polyplus.client.nameplate.NameplateSelfVisibility
//?}
//? if = 1.8.9 {
/*import org.lwjgl.input.Keyboard
*///?}
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}
//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

private val PANEL_MIN_WIDTH = 200.dp
private val PANEL_MAX_WIDTH = 300.dp
private const val PANEL_SCREEN_SHARE = 0.42f
private val PANEL_RADIUS = 16.dp
private val MAX_CHIP_LABEL_WIDTH = 120.dp
private const val VIEW_POLL_MS = 100L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
//? if = 1.8.9 {
/*private const val NO_PERSPECTIVE = -1
private const val THIRD_PERSON_FRONT = 2
*///?}

internal class NameplateTryOnScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    //? if > 1.8.9 {
    private var previousCamera: CameraType? = null

    override fun added() {
        super.added()
        val options = Minecraft.getInstance().options
        previousCamera = options.cameraType
        options.setCameraType(CameraType.THIRD_PERSON_FRONT)
    }

    private fun restoreCamera() {
        previousCamera?.let { Minecraft.getInstance().options.setCameraType(it) }
        previousCamera = null
    }
    //?} else {
    /*private var previousPerspective = NO_PERSPECTIVE

    override fun init() {
        super.init()
        if (previousPerspective == NO_PERSPECTIVE) {
            val options = Minecraft.getInstance().options
            previousPerspective = options.perspective
            options.perspective = THIRD_PERSON_FRONT
        }
    }

    private fun restoreCamera() {
        if (previousPerspective != NO_PERSPECTIVE) Minecraft.getInstance().options.perspective = previousPerspective
        previousPerspective = NO_PERSPECTIVE
    }
    *///?}

    override fun removed() {
        super.removed()
        restoreCamera()
        NameplateTryOn.stop()
    }

    override fun tick() {
        super.tick()
        when {
            !isInWorld() -> leaveWorld()
            NameplateTryOn.active() == null -> backToWardrobe()
        }
    }

    //? if >= 26.1 {
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}
    //? if < 26.1 && > 1.8.9 {
    /*override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?}

    //? if > 1.8.9 {
    override fun onClose() {
        backToWardrobe()
    }
    //?} else {
    /*override fun keyPressed(chr: Char, key: Int) {
        if (key == Keyboard.KEY_ESCAPE) backToWardrobe() else super.keyPressed(chr, key)
    }
    *///?}

    private fun backToWardrobe() {
        //? if > 1.8.9 {
        Minecraft.getInstance().execute {
        //?} else {
        /*ClientPlatform.runOnMain {
        *///?}
            if (ClientPlatform.currentScreen() === this) PolyPlusOneConfigIntegration.openCosmetics()
        }
    }

    private fun leaveWorld() {
        NameplateTryOn.stop()
        if (ClientPlatform.currentScreen() === this) ClientPlatform.setScreen(null)
    }

    @Composable
    override fun compose() {
        ProvideOneConfigTheme {
            NameplateTryOnPanel(onBack = ::backToWardrobe)
        }
    }
}

private class TryOnView(val trial: NameplateTryOn.Trial?, val seconds: Long, val reactions: Int) {
    override fun equals(other: Any?): Boolean =
        other is TryOnView && other.trial === trial && other.seconds == seconds && other.reactions == reactions

    override fun hashCode(): Int = (System.identityHashCode(trial) * 31 + seconds.hashCode()) * 31 + reactions

    companion object {
        fun now(): TryOnView {
            val trial = NameplateTryOn.active()
            val seconds = trial?.let { (it.remainingMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND } ?: 0L
            return TryOnView(trial, seconds, NameplateTryOn.reactions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameplateTryOnPanel(onBack: () -> Unit) {
    val theme = OneConfigTheme.current
    var view by remember { mutableStateOf(TryOnView.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            view = TryOnView.now()
            delay(VIEW_POLL_MS)
        }
    }
    val trial = view.trial ?: return
    val settings = NameplateBinding.settings
    val muted = OneConfigTheme.current.textColorSecondary

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val panelWidth = (maxWidth * PANEL_SCREEN_SHARE).coerceIn(minOf(PANEL_MIN_WIDTH, maxWidth), PANEL_MAX_WIDTH)
        Column(
            modifier = Modifier.align(Alignment.CenterStart)
                .width(panelWidth)
                .clip(OneConfigTheme.shape(PANEL_RADIUS))
                .background(theme.popupBackground.copy(alpha = 1f))
                .border(1.dp, theme.borderColor, OneConfigTheme.shape(PANEL_RADIUS))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("TRYING ON", color = muted, maxLines = 1, style = OneConfigTheme.current.typography.body.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp))
                Text(trial.title, color = theme.textColor, maxLines = 2, overflow = TextOverflow.Ellipsis, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
                Text("Ends in ${clock(view.seconds)}", color = muted, maxLines = 1, style = OneConfigTheme.current.typography.body.copy(fontSize = 12.sp))
            }
            TryOnNote()
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (trial.choices.size > 1) {
                    Column(Modifier.nameplateCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NameplateCardHeading("Variant")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            trial.choices.forEachIndexed { index, choice ->
                                TryOnVariantChip(choice, selected = index == trial.index) {
                                    NameplateTryOn.select(index)
                                    view = TryOnView.now()
                                }
                            }
                        }
                    }
                }
                NameplateMotionControls(
                    choice = trial.choice,
                    motion = settings.motion,
                    isOn = { view.reactions and it.bit != 0 },
                    onToggle = {
                        NameplateTryOn.toggleReaction(it.bit)
                        view = TryOnView.now()
                    },
                    onPulse = NameplateTryOn::pulseReaction,
                    onPlay = NameplateTryOn::fire,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton("Back to wardrobe", "left-arrow", onBack, style = ControlStyle.Primary, size = ControlSize.Compact, sound = null)
                Spacer(Modifier.weight(1f))
                Text("Esc", color = muted, maxLines = 1, style = OneConfigTheme.current.typography.body.copy(fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun TryOnNote() {
    val theme = OneConfigTheme.current
    val warning = tryOnWarning()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (warning != null) {
            Icon(name = "alert-circle", contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(13.dp), tint = theme.textColor)
        }
        Text(
            warning
                ?: "Nameplate motion is off, so it stays still.".takeIf { NameplateBinding.settings.motion == NameplateMotion.OFF }
                ?: "Only you see this. Nothing is bought or equipped.",
            color = if (warning != null) theme.textColor else OneConfigTheme.current.textColorSecondary,
            style = OneConfigTheme.current.typography.body.copy(fontSize = 12.sp),
        )
    }
}

private fun clock(seconds: Long): String = "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

@Composable
private fun TryOnVariantChip(choice: NameplateChoice, selected: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val swatchVersion = CosmeticSwatches.version
    val art = remember(choice.id, swatchVersion) { if (choice.id > 0) CosmeticSwatches.get(choice.id) else null }
    NameplateChip(selected, 28.dp, if (art != null) 4.dp else 10.dp, theme.textColorSecondary, onClick) {
        if (art != null) {
            Box(Modifier.size(width = 13.dp, height = 20.dp).clip(OneConfigTheme.shape(4.dp)).background(theme.chipBackground)) {
                when (art) {
                    is SwatchArt.Picture -> Image(
                        bitmap = art.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is SwatchArt.Stripes -> Column(Modifier.fillMaxSize()) {
                        for (color in art.colors) {
                            Box(Modifier.fillMaxWidth().weight(1f).background(color))
                        }
                    }
                }
            }
        }
        Text(choice.label, color = if (selected) OneConfigTheme.current.accentTextColor else theme.textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = MAX_CHIP_LABEL_WIDTH), style = OneConfigTheme.current.typography.body.copy(fontSize = 12.sp))
    }
}

private fun tryOnWarning(): String? = when {
    !NameplateTryOn.shownOnOwnTag() -> "Hidden by your nameplate visibility setting."
    NameplateIris.shaderPackInUse() && !NameplateIris.gpuMaterials() -> "Effects use a simpler look while a shader pack is on."
    //? if > 1.8.9
    NameplateSelfVisibility.deferredToPolyNametag() -> "Turn on PolyNametag's own nametag to see it."
    else -> null
}
