@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.polyplus.client.gui.ClientWatermark
import org.polyfrost.polyplus.client.gui.LocalScreenFit
import org.polyfrost.polyplus.client.gui.onboarding.live.RenderScreenKind
import org.polyfrost.polyplus.client.gui.onboarding.live.RenderScreenPreview
import org.polyfrost.polyplus.client.gui.pausescreen.PauseRoot
import org.polyfrost.polyplus.client.gui.screens.ScreenHost
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellFrame
import org.polyfrost.polyplus.client.gui.screens.connection.CONNECTION_STEPS
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionAction
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionCard
import org.polyfrost.polyplus.client.gui.screens.connection.ConnectionSteps
//? if > 1.8.9
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsBody
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsBody
import org.polyfrost.polyplus.client.gui.screens.world.CreateWorldContent
import org.polyfrost.polyplus.client.gui.screens.world.WorldDifficulty
import org.polyfrost.polyplus.client.gui.screens.world.WorldDraft
import org.polyfrost.polyplus.client.gui.screens.world.WorldGameMode
import org.polyfrost.polyplus.client.gui.screens.world.WorldRule
import org.polyfrost.polyplus.client.gui.screens.world.WorldRules
import org.polyfrost.polyplus.client.gui.screens.world.WorldSection
import org.polyfrost.polyplus.client.gui.screens.world.WorldTypeOption
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize
import org.polyfrost.polyplus.client.lobby.StartupOverlay

@Composable
internal fun ScreenMiniature(part: ScreenPart?, ours: Boolean, modifier: Modifier) {
    val description = "${part?.label ?: WATERMARK_LABEL}, ${if (ours) brandName else VANILLA_NAME} preview"
    Box(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .focusProperties {
                canFocus = false
                onEnter = { cancelFocusChange() }
            }
            .focusGroup(),
    ) {
        when {
            part == null -> RenderScreenPreview(
                RenderScreenKind.INVENTORY,
                Modifier.matchParentSize(),
                note = false,
                overlay = if (ours) ({ unit -> ClientWatermark.Sample(unit) }) else null,
            )
            !ours -> RenderScreenPreview(part.kind, Modifier.matchParentSize(), note = false)
            part == ScreenPart.LOADING -> VirtualScreen(Modifier.matchParentSize()) { OursLoading() }
            else -> {
                RenderScreenPreview(RenderScreenKind.BACKDROP, Modifier.matchParentSize(), note = false)
                VirtualScreen(Modifier.matchParentSize()) { OursScreen(part) }
            }
        }
        Box(Modifier.matchParentSize().inert())
    }
}

@Composable
private fun VirtualScreen(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val window = Minecraft.getInstance().window
        val windowWidth = window.width.toFloat()
        val windowHeight = window.height.toFloat()
        val tileWidth = constraints.maxWidth.toFloat()
        val tileHeight = constraints.maxHeight.toFloat()
        if (!usableSize(windowWidth, windowHeight) || !usableSize(tileWidth, tileHeight)) return@BoxWithConstraints
        val fit = minOf(tileWidth / windowWidth, tileHeight / windowHeight)
        val outer = LocalDensity.current
        val root = remember { rootDensity() }
        val virtual = remember(root, fit) { Density(usableScale(root * fit), 1f) }
        Box(Modifier.align(Alignment.Center).size(with(outer) { (windowWidth * fit).toDp() }, with(outer) { (windowHeight * fit).toDp() })) {
            CompositionLocalProvider(LocalDensity provides virtual, LocalScreenFit provides fit) {
                ProvideOneConfigTheme {
                    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { content() }
                }
            }
        }
    }
}

private fun rootDensity(): Float = runCatching {
    Class.forName(SCENE_DENSITY_OWNER).getMethod(SCENE_DENSITY).invoke(null) as Float
}.getOrDefault(1f)

@Composable
private fun OursScreen(part: ScreenPart) {
    val host = remember { PreviewHost(ScreenRouter.contextShell()) }
    when (part) {
        ScreenPart.SETTINGS -> ShellFrame(host, "Settings") { SettingsBody({ null }) }
        ScreenPart.WORLDS -> {
            val draft = remember { PreviewWorldDraft() }
            val rules = remember { mutableStateMapOf<String, String>() }
            ShellFrame(host, "Create world") { CreateWorldContent(draft, 0, rules) {} }
        }
        ScreenPart.CONNECTING -> ShellFrame(host, JOINING_TITLE, chrome = false) {
            ConnectionCard(
                host.shell,
                JOINING_TITLE,
                content = { ConnectionSteps(Component.translatable("connect.connecting").string, 1, CONNECTION_STEPS) },
            ) {
                ConnectionAction(Component.translatable("gui.cancel").string) {}
            }
        }
        ScreenPart.REALMS -> {
            //? if > 1.8.9 {
            val realms = remember { PreviewRealms() }
            ShellFrame(host, "Realms") { RealmsBody(realms) }
            //?}
        }
        ScreenPart.PAUSE -> {
            val pause = remember { PreviewPause() }
            ProvidePopupSounds(false) { PauseRoot(pause) }
        }
        ScreenPart.LOADING -> Unit
    }
}

@Composable
private fun OursLoading() {
    val mark = remember { startupArt(MARK_ART) }
    val word = remember { startupArt(WORD_ART) }
    val window = Minecraft.getInstance().window
    val guiScale = window.guiScale.toFloat()
    val windowWidth = window.width.toFloat()
    Canvas(Modifier.fillMaxSize()) {
        val thickness = maxOf(LINE_PIXELS, guiScale) * size.width / windowWidth
        val frame = StartupOverlay.loadingFrame(size.width, size.height, thickness)
        drawRect(Color(frame.background))
        drawRect(Color(frame.track), Offset(frame.line.x, frame.line.y), Size(frame.line.w, frame.line.h))
        drawRect(Color(frame.accent), Offset(frame.line.x, frame.line.y), Size(frame.line.w * LOADING_PROGRESS, frame.line.h))
        drawPoly {
            mark?.let { art -> it.image(art, frame.mark.x, frame.mark.y, frame.mark.w, frame.mark.h, 0f, 0f, 1f, 1f, PolyColor(-1), FilterQuality.Low) }
            word?.let { art -> it.image(art, frame.word.x, frame.word.y, frame.word.w, frame.word.h, 0f, 0f, 1f, 1f, PolyColor(-1), FilterQuality.Low) }
        }
    }
}

private fun startupArt(name: String): PolyBitmap? = runCatching {
    val bytes = PreviewWorldDraft::class.java.getResourceAsStream("/assets/polyplus/startup/$name.png")!!.use { it.readBytes() }
    PolyBitmap.decode(bytes)
}.getOrNull()

private fun Modifier.inert(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

private class PreviewHost(override val shell: ScreenShell) : ScreenHost {

    override fun back() = Unit

    override fun open(screen: Screen?) = Unit
}

private class PreviewWorldDraft : WorldDraft {
    override val sections: Set<WorldSection> = WorldSection.entries.toSet()
    override val recreated: Boolean = false
    override var name: String = Component.translatable("selectWorld.newWorld").string
    override val folder: String get() = name
    override var seed: String = ""
    override val gameModes: List<WorldGameMode> = listOf(WorldGameMode.Survival, WorldGameMode.Hardcore, WorldGameMode.Creative)
    override var gameMode: WorldGameMode = WorldGameMode.Survival
    override val gameModeLocked: Boolean = false
    override var difficulty: WorldDifficulty = WorldDifficulty.Normal
    override val difficultyLocked: Boolean = false
    override var allowCommands: Boolean = false
    override val allowCommandsLocked: Boolean = false
    override var bonusChest: Boolean = false
    override val bonusChestLocked: Boolean = false
    override var generateStructures: Boolean = true
    override val generateStructuresLocked: Boolean = false
    override val worldTypes: List<WorldTypeOption> = WORLD_TYPES.map { (id, key) -> WorldTypeOption(id, Component.translatable(key).string) }
    override val extraWorldTypes: List<WorldTypeOption> = emptyList()
    override var worldType: WorldTypeOption = worldTypes.first()
    override val worldTypeLocked: Boolean = false
    override val canCustomizeType: Boolean = false
    override val dataPackCount: Int = 0
    override val experimental: Boolean = false

    override fun customizeType() = Unit

    override fun editRules(): WorldRules = NoRules

    override fun openDataPacks() = Unit

    override fun openExperiments() = Unit

    override fun create() = Unit

    override fun cancel() = Unit

    override fun useVanilla() = Unit
}

private object NoRules : WorldRules {
    override val rules: List<WorldRule> = emptyList()

    override fun value(rule: WorldRule): String = rule.defaultValue

    override fun accepts(rule: WorldRule, value: String): Boolean = false

    override fun set(rule: WorldRule, value: String): Boolean = false

    override fun apply() = Unit
}

private val WORLD_TYPES = listOf(
    "minecraft:normal" to "generator.minecraft.normal",
    "minecraft:flat" to "generator.minecraft.flat",
    "minecraft:large_biomes" to "generator.minecraft.large_biomes",
    "minecraft:amplified" to "generator.minecraft.amplified",
    "minecraft:single_biome_surface" to "generator.single_biome_surface",
)

private const val WATERMARK_LABEL = "Watermark"
private const val JOINING_TITLE = "Joining server"
private const val MARK_ART = "mark_256"
private const val WORD_ART = "word_216"
private const val LOADING_PROGRESS = 0.6f
private const val LINE_PIXELS = 2f
private const val SCENE_DENSITY_OWNER = "org.polyfrost.oneconfig.internal.ui.compose.ComposeScreenKt"
private const val SCENE_DENSITY = "sceneDensity"
