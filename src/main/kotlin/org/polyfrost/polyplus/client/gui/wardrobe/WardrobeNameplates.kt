@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.NameplatePreview
import org.polyfrost.polyplus.client.gui.preview.NameplatePreviewController
import org.polyfrost.polyplus.client.gui.preview.NameplatePreviewScale
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateChoice
import org.polyfrost.polyplus.client.nameplate.NameplateChoices
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.NameplateTryOn
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction

private val STAGE_HEIGHT = 330.dp
private val STAGE_TAG_TOP = 7.dp
private val STAGE_TAG_HEIGHT = 116.dp
private val STAGE_FONT_PIXEL = 3.dp
private const val STAGE_MODEL_SCALE = 0.36f
private const val STAGE_MODEL_ANCHOR = 0.66f
private const val STAGE_CONTROLS_SHARE = 0.5f
private const val WORLD_POLL_MS = 250L
private const val HURT_MILLIS = 500L

private val TIMED_REACTIONS: Map<Reaction, Long> = mapOf(Reaction.HURT to HURT_MILLIS)

@Composable
internal fun NameplateThumbnail(cosmeticId: Int?, modifier: Modifier) {
    LaunchedEffect(cosmeticId) {
        if (cosmeticId != null) NameplateChoices.ensureLoaded(cosmeticId)
    }
    Box(modifier) {
        NameplatePreview(cosmeticId, Modifier.fillMaxSize(), NameplatePreviewScale.Fit)
    }
}

@Composable
internal fun NameplateStage(
    cosmeticId: Int?,
    title: String,
    modifier: Modifier,
    playerSource: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    extras: @Composable () -> Unit = {},
) {
    val controller = remember(cosmeticId) { NameplatePreviewController() }
    LaunchedEffect(cosmeticId) {
        if (cosmeticId == null) return@LaunchedEffect
        NameplateChoices.ensureLoaded(cosmeticId)
        for (sibling in NameplateChoices.groupIds(cosmeticId)) NameplateChoices.ensureLoaded(sibling)
    }
    val installs = CosmeticAssetCache.installs
    val choice = remember(cosmeticId, installs) { cosmeticId?.let(NameplateChoices::choice) }

    BoxWithConstraints(modifier) {
        val controlsMax = maxHeight * STAGE_CONTROLS_SHARE
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sceneHeight = minOf(maxHeight, STAGE_HEIGHT)
                val scale = sceneHeight / STAGE_HEIGHT
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(sceneHeight)) {
                    PlayerPreview(
                        Modifier.fillMaxSize(),
                        source = playerSource,
                        autoSpin = false,
                        modelScale = STAGE_MODEL_SCALE,
                        verticalAnchor = STAGE_MODEL_ANCHOR,
                        initialYaw = FRONT_YAW_DEG,
                        previewKey = "nameplate-stage",
                        live = true,
                    )
                    NameplatePreview(
                        cosmeticId,
                        Modifier.align(Alignment.TopCenter)
                            .offset(y = STAGE_TAG_TOP * scale)
                            .fillMaxWidth()
                            .height(STAGE_TAG_HEIGHT * scale),
                        NameplatePreviewScale.Fixed(STAGE_FONT_PIXEL * scale),
                        controller,
                    )
                }
            }
            StageControls(
                choice = choice,
                controller = controller,
                modifier = Modifier.fillMaxWidth().heightIn(max = controlsMax),
                extras = extras,
                onTryOn = { if (choice != null && cosmeticId != null) tryOn(cosmeticId, title, choice) },
            )
        }
    }
}

@Composable
private fun StageControls(
    choice: NameplateChoice?,
    controller: NameplatePreviewController,
    modifier: Modifier,
    extras: @Composable () -> Unit,
    onTryOn: () -> Unit,
) {
    val settings = NameplateBinding.settings
    Column(
        modifier = modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        stageNote(choice, settings.enabled, settings.motion)?.let {
            SocialText(it, color = mutedText(), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            extras()
            if (choice != null) {
                NameplateMotionControls(
                    choice = choice,
                    motion = settings.motion,
                    isOn = controller::isOn,
                    onToggle = controller::toggle,
                    onPulse = controller::pulse,
                    onPlay = controller::fire,
                )
            }
        }
        val inWorld by rememberInWorld()
        if (inWorld) {
            SmallButton(label = "Try in world", iconPath = "profiles", primary = choice != null, onClick = onTryOn)
        }
    }
}

internal fun isInWorld(): Boolean {
    val mc = Minecraft.getInstance()
    //? if > 1.8.9 {
    return mc.level != null && mc.player != null
    //?} else {
    /*return mc.world != null && mc.player != null
    *///?}
}

@Composable
internal fun rememberInWorld(): State<Boolean> = produceState(isInWorld()) {
    while (true) {
        value = isInWorld()
        delay(WORLD_POLL_MS)
    }
}

private fun stageNote(choice: NameplateChoice?, enabled: Boolean, motion: NameplateMotion): String? = when {
    choice == null -> "Loading nameplate..."
    !enabled -> "Nameplates are hidden by your visibility setting."
    NameplateIris.shaderPackInUse() && !NameplateIris.gpuMaterials() -> "Effects use a simpler look while a shader pack is on."
    motion == NameplateMotion.OFF -> "Nameplate motion is off, so it stays still."
    else -> null
}

private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
private val ID_SEPARATORS = Regex("[_\\-.\\s]+")

internal fun readableId(id: String): String =
    id.replace(CAMEL_BOUNDARY, "$1 $2")
        .replace(ID_SEPARATORS, " ")
        .trim()
        .lowercase()
        .replaceFirstChar { it.titlecase() }
        .ifEmpty { id }

internal fun reactionLabel(reaction: Reaction): String = readableId(reaction.id)

private fun tryOn(cosmeticId: Int, title: String, current: NameplateChoice) {
    val choices = NameplateChoices.groupChoices(cosmeticId).ifEmpty { listOf(current) }
    val index = choices.indexOfFirst { it.variant === current.variant }.coerceAtLeast(0)
    if (isInWorld() && NameplateTryOn.start(title, choices, index) != null) {
        ClientPlatform.setScreen(NameplateTryOnScreen())
    }
}

@Composable
internal fun Modifier.nameplateCard(): Modifier {
    val theme = OneConfigTheme.current
    return fillMaxWidth()
        .clip(ppShape(CARD_RADIUS))
        .background(theme.popupBackground.copy(alpha = 1f))
        .background(theme.chipBackground)
        .border(1.dp, theme.borderColor, ppShape(CARD_RADIUS))
        .padding(10.dp)
}

@Composable
internal fun NameplateCardHeading(text: String) {
    SocialText(text, color = mutedText(), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NameplateMotionControls(
    choice: NameplateChoice,
    motion: NameplateMotion,
    isOn: (Reaction) -> Boolean,
    onToggle: (Reaction) -> Unit,
    onPulse: (Reaction, Long) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (motion == NameplateMotion.OFF) return
    val (pulses, held) = choice.bundle.triggers.reactions.sortedBy { it.ordinal }.partition { it in TIMED_REACTIONS }
    val shots = if (motion == NameplateMotion.FULL) choice.bundle.triggers.oneShots.map { it.name } else emptyList()
    if (held.isEmpty() && pulses.isEmpty() && shots.isEmpty()) return
    Column(modifier.nameplateCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (held.isNotEmpty()) {
            NameplateCardHeading("States")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
                verticalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            ) {
                for (reaction in held) {
                    StateToggle(reactionLabel(reaction), isOn(reaction)) { onToggle(reaction) }
                }
            }
        }
        if (pulses.isNotEmpty() || shots.isNotEmpty()) {
            if (held.isNotEmpty()) Spacer(Modifier.height(2.dp))
            NameplateCardHeading("Play once")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
                verticalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            ) {
                for (reaction in pulses) {
                    val millis = TIMED_REACTIONS.getValue(reaction)
                    PlayOnceChip(reactionLabel(reaction), millis) { onPulse(reaction, millis) }
                }
                for (shot in shots) {
                    PlayOnceChip(readableId(shot), PLAY_FLASH_MS) { onPlay(shot) }
                }
            }
        }
    }
}

@Composable
private fun StateToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val muted = mutedText()
    val onAccent = contentOnAccent()
    Row(
        modifier = Modifier.height(CONTROL_HEIGHT)
            .clip(ppShape(CONTROL_RADIUS))
            .background(if (on) OneConfigTheme.accent else theme.componentBackground)
            .border(
                1.dp,
                when {
                    on -> OneConfigTheme.accent
                    hovered -> muted
                    else -> theme.borderColor
                },
                ppShape(CONTROL_RADIUS),
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(MARK_SIZE)
                .clip(theme.checkBoxShape)
                .background(if (on) onAccent else Color.Transparent)
                .border(1.5.dp, if (on) onAccent else muted, theme.checkBoxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon("tick", color = OneConfigTheme.accent, modifier = Modifier.size(10.dp))
        }
        CardLabel(label, color = if (on) onAccent else theme.textColor, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PlayOnceChip(label: String, flashMillis: Long, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var litAt by remember { mutableStateOf(0L) }
    val lit = litAt != 0L
    LaunchedEffect(litAt) {
        if (litAt != 0L) {
            delay(flashMillis)
            litAt = 0L
        }
    }
    val content = if (lit) contentOnAccent() else theme.textColor
    Row(
        modifier = Modifier.height(CONTROL_HEIGHT)
            .clip(ppShape(CONTROL_RADIUS))
            .background(if (lit) OneConfigTheme.accent else theme.componentBackground)
            .border(
                1.dp,
                when {
                    lit -> OneConfigTheme.accent
                    hovered -> mutedText()
                    else -> theme.borderColor
                },
                ppShape(CONTROL_RADIUS),
            )
            .hoverable(interaction)
            .clickable {
                litAt = System.nanoTime()
                onClick()
            }
            .padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(MARK_SIZE)) {
            val w = size.width
            val h = size.height
            val play = Path().apply {
                moveTo(w * 0.22f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.5f)
                lineTo(w * 0.22f, h * 0.88f)
                close()
            }
            drawPath(play, color = content)
        }
        CardLabel(label, color = content, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
    }
}

private val CARD_RADIUS = 12.dp
private val CONTROL_RADIUS = 6.dp
private val CONTROL_HEIGHT = 26.dp
private val CONTROL_GAP = 5.dp
private val MARK_SIZE = 14.dp
private const val PLAY_FLASH_MS = 350L
