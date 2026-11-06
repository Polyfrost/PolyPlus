package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.server.packs.resources.ResourceManager
//?} else {
/*import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.DynamicTexture
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import org.lwjgl.opengl.GL11
import javax.imageio.ImageIO
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 1.21.8 {
import net.minecraft.client.renderer.RenderPipelines
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.RenderType
*///?}

//? if >= 1.21.11 {
import net.minecraft.client.renderer.texture.MipmapStrategy
//?}

//? if >= 1.21.4 {
import net.minecraft.client.renderer.texture.ReloadableTexture
import net.minecraft.client.renderer.texture.TextureContents
//?} elif > 1.8.9 {
/*import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.texture.SimpleTexture
import java.io.IOException
*///?}

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.max

internal class StartupArt(val name: String, val height: Int, val width: Int) {
    val id: Identifier = Identifier.fromNamespaceAndPath("polyplus", "startup/$name")
    val path = "/assets/polyplus/startup/$name.png"
}

private fun openArt(path: String) = StartupArt::class.java.getResourceAsStream(path) ?: error("Missing startup art $path")

//? if >= 1.21.4 {
private class StartupTexture(id: Identifier, private val path: String) : ReloadableTexture(id) {
    override fun loadContents(resourceManager: ResourceManager): TextureContents {
        val image = openArt(path).use { NativeImage.read(it) }
        //? if >= 1.21.11 {
        return TextureContents(image, TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0f))
        //?} else {
        /*return TextureContents(image, TextureMetadataSection(true, true))
        *///?}
    }
}
//?} elif > 1.8.9 {
/*private class StartupTexture(id: Identifier, private val path: String) : SimpleTexture(id) {
    override fun getTextureImage(resourceManager: ResourceManager): TextureImage = try {
        TextureImage(TextureMetadataSection(true, true), openArt(path).use { NativeImage.read(it) })
    } catch (error: IOException) {
        TextureImage(error)
    }
}
*///?}

internal object StartupTextures {
    private val mark = listOf(StartupArt("mark_64", 64, 64), StartupArt("mark_128", 128, 128), StartupArt("mark_256", 256, 256))
    private val word = listOf(StartupArt("word_54", 54, 289), StartupArt("word_108", 108, 577), StartupArt("word_216", 216, 1154))
    val pixel = StartupArt("pixel", 1, 1)
    private var loaded = false

    fun mark(pixels: Float): StartupArt = mark.firstOrNull { it.height >= pixels } ?: mark.last()

    fun word(pixels: Float): StartupArt = word.firstOrNull { it.height >= pixels } ?: word.last()

    fun load(mc: Minecraft) {
        if (loaded) return
        loaded = true
        (mark + word + pixel).forEach { register(mc, it) }
    }

    fun release(mc: Minecraft) {
        if (!loaded) return
        loaded = false
        (mark + word + pixel).forEach {
            //? if > 1.8.9 {
            mc.textureManager.release(it.id)
            //?} else {
            /*mc.textureManager.close(it.id)
            *///?}
        }
    }

    private fun register(mc: Minecraft, art: StartupArt) {
        //? if >= 1.21.4 {
        mc.textureManager.registerAndLoad(art.id, StartupTexture(art.id, art.path))
        //?} elif > 1.8.9 {
        /*mc.textureManager.register(art.id, StartupTexture(art.id, art.path))
        *///?} else {
        /*mc.textureManager.register(art.id, DynamicTexture(openArt(art.path).use { ImageIO.read(it) }))
        mc.textureManager.bind(art.id)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        *///?}
    }
}

//? if >= 26.1 {
internal typealias StartupGraphics = GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*internal typealias StartupGraphics = GuiGraphics
*///?}

//? if > 1.8.9 {
internal class StartupCanvas(private val mc: Minecraft, private val g: StartupGraphics) {
    val width: Float = g.guiWidth().toFloat()
    val height: Float = g.guiHeight().toFloat()
    val pixelScale: Float = mc.window.width / max(width, 1f)

    fun drawScreenBelow(partial: Float) {
        val screen = ClientPlatform.currentScreen() ?: return
        //? if >= 26.1 {
        screen.extractRenderStateWithTooltipAndSubtitles(g, 0, 0, partial)
        //?} elif >= 1.21.10 {
        /*screen.renderWithTooltipAndSubtitles(g, 0, 0, partial)
        *///?} elif >= 1.21.8 {
        /*screen.renderWithTooltip(g, 0, 0, partial)
        *///?} else {
        /*screen.render(g, 0, 0, partial)
        *///?}
        raise()
    }

    fun raise() {
        //? if >= 1.21.8 {
        g.nextStratum()
        //?} else {
        /*g.flush()
        *///?}
    }

    fun begin() {
        //? if < 1.21.4 {
        /*g.flush()
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        *///?}
    }

    fun end() {
        //? if < 1.21.4 {
        /*g.flush()
        g.setColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        *///?}
    }

    fun quad(art: StartupArt, x: Float, y: Float, w: Float, h: Float, argb: Int) {
        if (w <= 0f || h <= 0f) return
        val texWidth = art.width
        val texHeight = art.height
        //? if >= 1.21.8 {
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(w / texWidth, h / texHeight)
        g.blit(RenderPipelines.GUI_TEXTURED, art.id, 0, 0, 0f, 0f, texWidth, texHeight, texWidth, texHeight, texWidth, texHeight, argb)
        g.pose().popMatrix()
        //?} elif >= 1.21.4 {
        /*g.pose().pushPose()
        g.pose().translate(x, y, OVERLAY_DEPTH)
        g.pose().scale(w / texWidth, h / texHeight, 1f)
        g.blit(RenderType::guiTextured, art.id, 0, 0, 0f, 0f, texWidth, texHeight, texWidth, texHeight, texWidth, texHeight, argb)
        g.pose().popPose()
        *///?} else {
        /*g.setColor(
            (argb shr 16 and 0xFF) / 255f,
            (argb shr 8 and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            (argb ushr 24) / 255f,
        )
        g.pose().pushPose()
        g.pose().translate(x, y, OVERLAY_DEPTH)
        g.pose().scale(w / texWidth, h / texHeight, 1f)
        g.blit(art.id, 0, 0, texWidth, texHeight, 0f, 0f, texWidth, texHeight, texWidth, texHeight)
        g.pose().popPose()
        *///?}
    }

    //? if < 1.21.8 {
    /*private companion object {
        const val OVERLAY_DEPTH = 1000f
    }
    *///?}
}
//?} else {
/*internal class StartupCanvas(private val mc: Minecraft) {
    private val window = Window(mc)
    val width: Float = window.width.toFloat()
    val height: Float = window.height.toFloat()
    val pixelScale: Float = window.scale.toFloat()

    fun begin() {
        GlStateManager.disableLighting()
        GlStateManager.disableFog()
        GlStateManager.disableDepthTest()
        GlStateManager.disableAlphaTest()
        GlStateManager.enableTexture()
        GlStateManager.enableBlend()
        GlStateManager.blendFuncSeparate(770, 771, 1, 0)
    }

    fun end() {
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        GlStateManager.enableAlphaTest()
        GlStateManager.enableDepthTest()
    }

    fun quad(art: StartupArt, x: Float, y: Float, w: Float, h: Float, argb: Int) {
        if (w <= 0f || h <= 0f) return
        mc.textureManager.bind(art.id)
        GlStateManager.color4f(
            (argb shr 16 and 0xFF) / 255f,
            (argb shr 8 and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            (argb ushr 24) / 255f,
        )
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.buffer
        buffer.begin(7, DefaultVertexFormat.POSITION_TEX)
        buffer.vertex(x.toDouble(), (y + h).toDouble(), 0.0).texture(0.0, 1.0).nextVertex()
        buffer.vertex((x + w).toDouble(), (y + h).toDouble(), 0.0).texture(1.0, 1.0).nextVertex()
        buffer.vertex((x + w).toDouble(), y.toDouble(), 0.0).texture(1.0, 0.0).nextVertex()
        buffer.vertex(x.toDouble(), y.toDouble(), 0.0).texture(0.0, 0.0).nextVertex()
        tesselator.end()
    }

    fun projectWindow() {
        GlStateManager.matrixMode(5889)
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, window.width.toDouble(), window.height.toDouble(), 0.0, 1000.0, 3000.0)
        GlStateManager.matrixMode(5888)
        GlStateManager.loadIdentity()
        GlStateManager.translatef(0f, 0f, -2000f)
    }
}
*///?}
