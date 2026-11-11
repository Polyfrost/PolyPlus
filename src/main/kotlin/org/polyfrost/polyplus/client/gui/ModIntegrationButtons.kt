package org.polyfrost.polyplus.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

//? if fabric || ornithe {
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.compat.Mods
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
//?}

internal class ModIntegrationButton(
    val icon: String,
    val tooltip: String,
    val isPresent: () -> Boolean,
    val onClick: (Screen) -> Unit,
    logo: () -> ByteArray? = { null },
) {
    val logo: ByteArray? by lazy(logo)
}

internal object ModIntegrationButtons {
    private const val ASSETS = "assets/polyplus/mainmenu/"
    private const val GLYPH_FRAME = 20
    private const val GLYPH_MIN_ALPHA = 200
    private const val GLYPH_MIN_CHANNEL = 235
    private const val OPAQUE_WHITE = -1

    private val modMenuIcons = listOf(
        "assets/modmenu/textures/gui/sprites/mods/enabled.png",
        "assets/modmenu/textures/gui/mods_button.png",
    )

    private val modMenu = ParentScreenFactory("com.terraformersmc.modmenu.gui.ModsScreen")

    private val modMenuButton = ModIntegrationButton(
        icon = ASSETS + "package-01.svg",
        tooltip = "Fabric Mod Menu",
        isPresent = { modLoaded("modmenu") && modMenu.isUsable },
        onClick = { parent -> modMenu.open(parent) },
        logo = ::modMenuGlyph,
    )

    private val snapper = ParentScreenFactory(
        "dev.spiritstudios.snapper.gui.screen.ScreenshotListScreen",
        "dev.spiritstudios.snapper.gui.screen.GalleryScreen",
        "dev.spiritstudios.snapper.gui.screen.ScreenshotScreen",
    )

    private val snapperButton = ModIntegrationButton(
        icon = ASSETS + "image-01.svg",
        tooltip = "Screenshots",
        isPresent = { modLoaded("snapper") && snapper.isUsable },
        onClick = { parent -> snapper.open(parent) },
    )

    private val all: List<ModIntegrationButton> = listOf(modMenuButton, snapperButton)

    fun available(): List<ModIntegrationButton> = all.filter(::isAvailable)

    fun availableModMenu(): ModIntegrationButton? = modMenuButton.takeIf(::isAvailable)

    fun withoutModMenu(): List<ModIntegrationButton> = available() - modMenuButton

    private fun isAvailable(button: ModIntegrationButton): Boolean = runCatching { button.isPresent() }.getOrDefault(false)

    private fun modLoaded(id: String): Boolean {
        //? if fabric || ornithe {
        return Mods.loaded(id)
        //?} else {
        /*return false
        *///?}
    }

    private fun modMenuGlyph(): ByteArray? {
        //? if fabric || ornithe {
        return runCatching {
            val mod = FabricLoader.getInstance().getModContainer("modmenu").orElse(null) ?: return@runCatching null
            modMenuIcons.firstNotNullOfOrNull { path ->
                mod.findPath(path).orElse(null)?.let { glyphOf(Files.readAllBytes(it)) }
            }
        }.getOrNull()
        //?} else {
        /*return null
        *///?}
    }

    //? if fabric || ornithe {
    private fun glyphOf(png: ByteArray): ByteArray? {
        val frame = ImageIO.read(ByteArrayInputStream(png)) ?: return null
        val width = minOf(GLYPH_FRAME, frame.width)
        val height = minOf(GLYPH_FRAME, frame.height)
        val lit = ArrayList<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (isGlyphPixel(frame.getRGB(x, y))) lit += x to y
            }
        }
        if (lit.isEmpty()) return null
        val left = lit.minOf { it.first }
        val top = lit.minOf { it.second }
        val glyphWidth = lit.maxOf { it.first } - left + 1
        val glyphHeight = lit.maxOf { it.second } - top + 1
        val side = maxOf(glyphWidth, glyphHeight)
        val glyph = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        val offsetX = (side - glyphWidth) / 2
        val offsetY = (side - glyphHeight) / 2
        lit.forEach { (x, y) -> glyph.setRGB(x - left + offsetX, y - top + offsetY, OPAQUE_WHITE) }
        return ByteArrayOutputStream().also { ImageIO.write(glyph, "png", it) }.toByteArray()
    }

    private fun isGlyphPixel(argb: Int): Boolean {
        val alpha = argb ushr 24
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return alpha >= GLYPH_MIN_ALPHA && minOf(red, green, blue) >= GLYPH_MIN_CHANNEL
    }
    //?}
}

// Reflective so the other mod's class need not be present at compile time
private class ParentScreenFactory(private vararg val classNames: String) {
    private val constructor by lazy {
        classNames.firstNotNullOfOrNull { className ->
            runCatching {
                Class.forName(className, false, javaClass.classLoader)
                    .getConstructor(Screen::class.java)
            }.getOrNull()
        }
    }

    val isUsable: Boolean get() = constructor != null

    fun open(parent: Screen) {
        val screen = runCatching { constructor?.newInstance(parent) as? Screen }.getOrNull() ?: return
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        mc.gui.setScreen(screen)
        //?} elif > 1.8.9 {
        /*mc.setScreen(screen)
        *///?} else {
        /*mc.openScreen(screen)
        *///?}
    }
}
