package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.ModelBakery
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import org.polyfrost.polyplus.client.gui.onboarding.live.LiveArgb as ARGB
*///?}
import org.joml.Matrix4f
//? if < 1.21.4 {
/*import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.lwjgl.opengl.GL11
*///?}
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType as RenderTypes
*///?}

internal object RenderFireRenderer {
    const val VANILLA_ALPHA = 0xE5

    fun submit(collector: LiveHudCollector, heightOffset: Float, alpha: Int) {
        //? if >= 1.21.10 {
        val sprite = Minecraft.getInstance().atlasManager.get(ModelBakery.FIRE_1)
        //?} else {
        /*val sprite = ModelBakery.FIRE_1.sprite()
        *///?}
        val color = ARGB.color(alpha.coerceIn(0, MAX_CHANNEL), WHITE)
        val pose = PoseStack()
        pose.translate(0f, heightOffset, 0f)
        //? if >= 1.21.10 {
        collector.submitCustomGeometry(pose, RenderTypes.fireScreenEffect(sprite.atlasLocation())) { base, buffer -> flames(base.pose(), buffer, sprite, color) }
        //?} elif >= 1.21.4 {
        /*flames(pose.last().pose(), collector.getBuffer(RenderTypes.fireScreenEffect(sprite.atlasLocation())), sprite, color)
        *///?} else {
        /*RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        RenderSystem.depthFunc(GL11.GL_ALWAYS)
        RenderSystem.depthMask(false)
        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
        RenderSystem.setShaderTexture(0, sprite.atlasLocation())
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        flames(pose.last().pose(), builder, sprite, color)
        BufferUploader.drawWithShader(builder.buildOrThrow())
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        RenderSystem.depthFunc(GL11.GL_LEQUAL)
        *///?}
    }

    private fun flames(base: Matrix4f, buffer: VertexConsumer, sprite: TextureAtlasSprite, color: Int) {
        val quad = Matrix4f()
        quad.set(base).translate(SIDE_OFFSET, DROP, 0f).rotateY(-TILT)
        fireQuad(buffer, quad, sprite, color)
        quad.set(base).translate(-SIDE_OFFSET, DROP, 0f).rotateY(TILT)
        fireQuad(buffer, quad, sprite, color)
    }

    private fun fireQuad(buffer: VertexConsumer, pose: Matrix4f, sprite: TextureAtlasSprite, color: Int) {
        buffer.addVertex(pose, -HALF, -HALF, DEPTH).setUv(sprite.u1, sprite.v1).setColor(color)
        buffer.addVertex(pose, HALF, -HALF, DEPTH).setUv(sprite.u0, sprite.v1).setColor(color)
        buffer.addVertex(pose, HALF, HALF, DEPTH).setUv(sprite.u0, sprite.v0).setColor(color)
        buffer.addVertex(pose, -HALF, HALF, DEPTH).setUv(sprite.u1, sprite.v0).setColor(color)
    }

    private const val MAX_CHANNEL = 255
    private const val WHITE = 0xFFFFFF
    private const val SIDE_OFFSET = 0.24f
    private const val DROP = -0.3f
    private const val TILT = 0.17453292f
    private const val HALF = 0.5f
    private const val DEPTH = -0.5f
}
//?}
