package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.PauseMenuPanel
//? if > 1.8.9
import org.polyfrost.polyplus.client.gui.WidgetPress
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.replay.ReplayProviders

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractStringWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.mixin.client.access.AbstractWidgetAccessor
import org.polyfrost.polyplus.mixin.client.access.ButtonAccessor
import org.polyfrost.polyplus.mixin.client.access.ImageButtonAccessor
import org.polyfrost.polyplus.mixin.client.access.SpriteIconButtonAccessor
import org.polyfrost.polyplus.mixin.client.access.TooltipAccessor
//?} else {
/*import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.resource.language.I18n
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
*///?}

internal enum class PauseSlot { RESUME, OPTIONS, WORLD, ADVANCEMENTS, STATS, QUIT, MORE }

internal class PauseEntry(
    val key: Int,
    val slot: PauseSlot,
    val label: String,
    val section: String,
    val enabled: Boolean,
    val tooltip: String?,
    val sprite: ImageBitmap?,
    val glyph: String,
    val modMenu: Boolean = false,
    val relabelled: Boolean = false,
    val press: () -> Unit,
) {
    fun same(other: PauseEntry): Boolean =
        key == other.key && slot == other.slot && label == other.label && section == other.section && modMenu == other.modMenu &&
            enabled == other.enabled && tooltip == other.tooltip && sprite === other.sprite
}

internal class PauseCapture(private val vanilla: PauseScreen) {
    var entries by mutableStateOf<List<PauseEntry>>(emptyList())
        private set

    var uncaptured by mutableStateOf(0)
        private set

    private var refreshedAt = 0L
    //? if > 1.8.9
    private val sprites = HashMap<Identifier, ImageBitmap?>()

    val width: Int get() = vanilla.width
    val height: Int get() = vanilla.height

    fun entry(slot: PauseSlot): PauseEntry? = entries.firstOrNull { it.slot == slot }

    val more: List<PauseEntry> get() = entries.filter { it.slot == PauseSlot.MORE }

    fun sync(width: Int, height: Int) {
        //? if >= 1.21.11 {
        vanilla.init(width, height)
        //?} else {
        /*vanilla.init(Minecraft.getInstance(), width, height)
        *///?}
        ReplayProviders.available.forEach { runCatching { it.detachPauseOverlay(vanilla) } }
        store(read())
    }

    fun tick() {
        //? if > 1.8.9 {
        vanilla.tick()
        //?} else {
        /*val mc = Minecraft.getInstance()
        val shown = mc.screen
        mc.screen = vanilla
        try {
            vanilla.tick()
        } finally {
            if (mc.screen === vanilla) mc.screen = shown
        }
        *///?}
    }

    fun refresh() {
        val now = System.nanoTime()
        if (now - refreshedAt < REFRESH_NANOS) return
        refreshedAt = now
        store(read())
    }

    private fun store(next: List<PauseEntry>) {
        val current = entries
        if (current.size != next.size || current.indices.any { !current[it].same(next[it]) }) entries = next
    }

    private fun section(mod: String?, group: String): String = mod ?: group

    //? if > 1.8.9 {
    private fun read(): List<PauseEntry> {
        val shown = vanilla.children().filterIsInstance<AbstractWidget>().filter { it.visible && !PauseMenuPanel.drawnElsewhere(it) && !shownAsRecording(it) }
        val mirrored = shown.filter(::mirrorable)
        uncaptured = shown.count { !mirrored.contains(it) && it !is AbstractStringWidget }
        val resolved = mirrored.map { it to slotOf(it) }.toMutableList()
        if (resolved.none { it.second.slot == PauseSlot.QUIT }) {
            val bottom = resolved.filter { it.first.width >= WIDE_BUTTON }.maxByOrNull { it.first.y }
            if (bottom != null && bottom.second.slot == PauseSlot.MORE) {
                resolved[resolved.indexOf(bottom)] = bottom.first to Slotted(PauseSlot.QUIT, GROUP_OTHER, GLYPH_LEAVE)
            }
        }
        return resolved.map { (widget, slotted) -> entryFor(widget, slotted) }
    }

    private fun shownAsRecording(widget: AbstractWidget): Boolean {
        val prefixes = ReplayProviders.available.flatMap { runCatching { it.controlKeyPrefixes }.getOrDefault(emptyList()) }
        if (prefixes.isEmpty()) return false
        val keys = listOfNotNull((widget.message.contents as? TranslatableContents)?.key, tooltipKey(widget))
        return keys.any { key -> prefixes.any(key::startsWith) }
    }

    private fun tooltipKey(widget: AbstractWidget): String? {
        val tooltip = (widget as AbstractWidgetAccessor).`polyplus$tooltip`().get() ?: return null
        return ((tooltip as TooltipAccessor).`polyplus$message`().contents as? TranslatableContents)?.key
    }

    private fun mirrorable(widget: AbstractWidget): Boolean =
        widget !is AbstractStringWidget && widget !is AbstractSliderButton && widget !is EditBox

    private class Slotted(val slot: PauseSlot, val group: String, val glyph: String)

    private fun slotOf(widget: AbstractWidget): Slotted {
        val key = (widget.message.contents as? TranslatableContents)?.key
        return when (key) {
            "menu.returnToGame" -> Slotted(PauseSlot.RESUME, GROUP_GAME, GLYPH_ROW)
            "menu.options" -> Slotted(PauseSlot.OPTIONS, GROUP_GAME, "cog")
            "options.worldOptions.button", "menu.shareToLan", "menu.multiplayerOptions.button", "polyplus.hostWorld" ->
                Slotted(PauseSlot.WORLD, GROUP_GAME, GLYPH_WORLD)
            "gui.advancements" -> Slotted(PauseSlot.ADVANCEMENTS, GROUP_GAME, "star")
            "gui.stats" -> Slotted(PauseSlot.STATS, GROUP_GAME, "align")
            in VANILLA_QUIT_KEYS -> Slotted(PauseSlot.QUIT, GROUP_GAME, GLYPH_LEAVE)
            "menu.sendFeedback", "menu.reportBugs", "menu.feedback", "menu.playerReporting" -> Slotted(PauseSlot.MORE, GROUP_HELP, GLYPH_ROW)
            "menu.server_links" -> Slotted(PauseSlot.MORE, GROUP_SERVER, GLYPH_ROW)
            else -> Slotted(PauseSlot.MORE, GROUP_OTHER, GLYPH_ROW)
        }
    }

    private fun entryFor(widget: AbstractWidget, slotted: Slotted): PauseEntry {
        val tooltip = WidgetPress.tooltip(widget)
        val handler = (widget as? Button)?.let { (it as ButtonAccessor).`polyplus$onPress`() }
        val mod = if (slotted.group == GROUP_OTHER) PauseModSource.of(widget.javaClass, handler?.javaClass) else null
        val group = if (slotted.group == GROUP_OTHER && widget.message.contents is TranslatableContents) GROUP_GAME else slotted.group
        val text = PauseText.of(widget.message).ifBlank { tooltip.orEmpty() }.ifBlank { UNNAMED }
        return PauseEntry(
            System.identityHashCode(widget),
            slotted.slot,
            text.removeSuffix("...").removeSuffix("…").trim(),
            section(mod, group),
            widget.isActive,
            tooltip?.takeIf { it != text },
            spriteOf(widget),
            slotted.glyph,
            slotted.slot == PauseSlot.MORE && isModMenu(widget, handler),
            slotted.slot == PauseSlot.QUIT && (widget.message.contents as? TranslatableContents)?.key !in VANILLA_QUIT_KEYS,
        ) { WidgetPress.press(widget) }
    }

    private fun isModMenu(widget: AbstractWidget, handler: Any?): Boolean {
        if ((widget.message.contents as? TranslatableContents)?.key == MOD_MENU_KEY) return true
        return listOfNotNull(widget.javaClass, handler?.javaClass).any { it.name.startsWith(MOD_MENU_PACKAGE) }
    }

    private fun spriteOf(widget: AbstractWidget): ImageBitmap? {
        val id = runCatching { spriteId(widget) }.getOrNull() ?: return null
        return sprites.getOrPut(id) { loadSprite(id) }
    }

    private fun spriteId(widget: AbstractWidget): Identifier? =
        when (widget) {
            is SpriteIconButton -> {
                val access = widget as SpriteIconButtonAccessor
                //? if >= 1.21.9 {
                access.`polyplus$sprite`()?.get(widget.isActive, false)
                //?} else {
                /*access.`polyplus$sprite`()
                *///?}
            }
            is ImageButton -> (widget as ImageButtonAccessor).`polyplus$sprites`()?.get(widget.isActive, false)
            else -> null
        }

    private fun loadSprite(id: Identifier): ImageBitmap? = runCatching {
        val location = Identifier.fromNamespaceAndPath(id.namespace, "textures/gui/sprites/${id.path}.png")
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: return@runCatching null
        PolyBitmap.decode(resource.open().use { it.readBytes() })?.asComposeImageBitmap()
    }.getOrNull()
    //?} else {
    /*private fun read(): List<PauseEntry> {
        uncaptured = 0
        return (vanilla as ScreenAccessor).`polyplus$buttons`().filter { it.visible }.map { button ->
            val slot = when (button.id) {
                LEGACY_RESUME -> PauseSlot.RESUME
                LEGACY_OPTIONS -> PauseSlot.OPTIONS
                LEGACY_LAN -> PauseSlot.WORLD
                LEGACY_ACHIEVEMENTS -> PauseSlot.ADVANCEMENTS
                LEGACY_STATS -> PauseSlot.STATS
                LEGACY_QUIT -> PauseSlot.QUIT
                else -> PauseSlot.MORE
            }
            val mod = PauseModSource.of(button.javaClass, null)
            PauseEntry(
                System.identityHashCode(button),
                slot,
                button.message.ifBlank { UNNAMED },
                section(mod, GROUP_OTHER),
                button.active,
                null,
                null,
                when (slot) {
                    PauseSlot.OPTIONS -> "cog"
                    PauseSlot.WORLD -> GLYPH_WORLD
                    PauseSlot.ADVANCEMENTS -> "star"
                    PauseSlot.STATS -> "align"
                    PauseSlot.QUIT -> GLYPH_LEAVE
                    else -> GLYPH_ROW
                },
                relabelled = slot == PauseSlot.QUIT && VANILLA_QUIT_KEYS.none { I18n.translate(it) == button.message },
            ) { press(button) }
        }
    }

    private fun press(button: ButtonWidget) {
        ClientPlatform.runOnMain { if (button.active) (vanilla as ScreenAccessor).`polyplus$buttonClicked`(button) }
    }
    *///?}

    private companion object {
        const val REFRESH_NANOS = 200_000_000L
        const val WIDE_BUTTON = 150
        const val GROUP_GAME = "Game"
        const val GROUP_HELP = "Help"
        const val GROUP_SERVER = "Server"
        const val GROUP_OTHER = "Other"
        const val GLYPH_ROW = "dots-grid"
        const val GLYPH_WORLD = "assets/polyplus/mainmenu/globe-01.svg"
        const val GLYPH_LEAVE = "assets/polyplus/mainmenu/log-out-04.svg"
        const val UNNAMED = "Mod button"
        const val MOD_MENU_KEY = "modmenu.title"
        const val MOD_MENU_PACKAGE = "com.terraformersmc.modmenu."
        val VANILLA_QUIT_KEYS = setOf("menu.returnToMenu", "menu.disconnect", "gui.toTitle")
        //? if = 1.8.9 {
        /*const val LEGACY_OPTIONS = 0
        const val LEGACY_QUIT = 1
        const val LEGACY_RESUME = 4
        const val LEGACY_ACHIEVEMENTS = 5
        const val LEGACY_STATS = 6
        const val LEGACY_LAN = 7
        *///?}
    }
}
