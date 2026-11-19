package org.polyfrost.polyplus.client.gui.onboarding.live

//? if < 1.21.5 && > 1.8.9 {
/*import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.shaders.FogShape
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.RenderShape
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
//? if >= 1.21.4 {
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.BufferUsage
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.renderer.CompiledShaderProgram
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.client.renderer.FogParameters
//?} else {
/^import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexSorting
import net.minecraft.client.renderer.ShaderInstance
^///?}

//? if >= 1.21.4 {
internal typealias LiveShader = CompiledShaderProgram
//?} else {
/^internal typealias LiveShader = ShaderInstance
^///?}

internal fun liveProjection(matrix: Matrix4f) {
    //? if >= 1.21.4 {
    RenderSystem.setProjectionMatrix(matrix, ProjectionType.PERSPECTIVE)
    //?} else {
    /^RenderSystem.setProjectionMatrix(matrix, VertexSorting.DISTANCE_TO_ORIGIN)
    ^///?}
}

internal fun liveQuad(): VertexBuffer {
    //? if >= 1.21.4 {
    return VertexBuffer.uploadStatic(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN) { buffer ->
        buffer.addVertex(0f, 0f, 0f)
        buffer.addVertex(1f, 0f, 0f)
        buffer.addVertex(1f, 1f, 0f)
        buffer.addVertex(0f, 1f, 0f)
    }
    //?} else {
    /^val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN)
    builder.addVertex(0f, 0f, 0f)
    builder.addVertex(1f, 0f, 0f)
    builder.addVertex(1f, 1f, 0f)
    builder.addVertex(0f, 1f, 0f)
    val buffer = VertexBuffer(VertexBuffer.Usage.STATIC)
    buffer.bind()
    buffer.upload(builder.buildOrThrow())
    VertexBuffer.unbind()
    return buffer
    ^///?}
}

//? if < 1.21.4 {
/^internal object LiveArgb {
    fun alpha(color: Int): Int = color ushr ALPHA_SHIFT

    fun red(color: Int): Int = (color shr RED_SHIFT) and CHANNEL

    fun green(color: Int): Int = (color shr GREEN_SHIFT) and CHANNEL

    fun blue(color: Int): Int = color and CHANNEL

    fun redFloat(color: Int): Float = red(color) / MAX

    fun greenFloat(color: Int): Float = green(color) / MAX

    fun blueFloat(color: Int): Float = blue(color) / MAX

    fun color(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue

    fun color(alpha: Int, rgb: Int): Int = (alpha shl ALPHA_SHIFT) or (rgb and RGB)

    fun opaque(color: Int): Int = color or (CHANNEL shl ALPHA_SHIFT)

    fun scaleRGB(color: Int, scale: Float): Int =
        color(alpha(color), (red(color) * scale).toInt().coerceIn(0, CHANNEL), (green(color) * scale).toInt().coerceIn(0, CHANNEL), (blue(color) * scale).toInt().coerceIn(0, CHANNEL))

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL = 0xFF
    private const val RGB = 0xFFFFFF
    private const val MAX = 255f
}
^///?}

internal fun liveClear(mask: Int) {
    //? if >= 1.21.4 {
    RenderSystem.clear(mask)
    //?} else {
    /^RenderSystem.clear(mask, Minecraft.ON_OSX)
    ^///?}
}

internal class RenderBlocksMesh(private val label: String) : AutoCloseable {
    private class Layer {
        val bytes = ByteBufferBuilder(INITIAL_BYTES)
        var buffer: VertexBuffer? = null
        var indexCount = 0
    }

    private val layers = HashMap<RenderType, Layer>()

    fun isEmpty() = layers.values.all { it.indexCount == 0 }

    fun rebuild(scene: RenderBlocksScene, positions: Iterable<BlockPos> = scene.blocks.keys, grassMode: Int? = null): Boolean =
        rebuildRegion(scene, positions, BlockPos.ZERO, grassMode)

    class Prepared internal constructor(internal val meshes: Map<RenderType, MeshData?>, val fabric: Boolean) : AutoCloseable {
        override fun close() {
            meshes.values.forEach { it?.close() }
        }
    }

    fun rebuildRegion(
        scene: BlockAndTintGetter,
        positions: Iterable<BlockPos>,
        origin: BlockPos,
        grassMode: Int? = null,
        fluids: Boolean = false,
    ): Boolean {
        val prepared = prepareRegion(scene, positions, origin, grassMode, fluids)
        upload(prepared)
        return prepared.fabric
    }

    fun upload(prepared: Prepared) {
        layers.forEach { (layer, slot) -> upload(slot, prepared.meshes[layer]) }
    }

    fun prepareRegion(
        scene: BlockAndTintGetter,
        positions: Iterable<BlockPos>,
        origin: BlockPos,
        grassMode: Int? = null,
        fluids: Boolean = false,
    ): Prepared {
        val builders = HashMap<RenderType, BufferBuilder>()
        val builder = { layer: RenderType ->
            builders.getOrPut(layer) {
                BufferBuilder(layers.getOrPut(layer, ::Layer).bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)
            }
        }
        val drawn = positions.filter { pos ->
            val state = scene.getBlockState(pos)
            !state.isAir && state.renderShape == RenderShape.MODEL
        }
        val fabric = RenderBlocksFabric.tesselate(scene, drawn, origin, grassMode, builder)
        if (!fabric) {
            val blocks = Minecraft.getInstance().blockRenderer
            val random = RandomSource.create()
            val pose = PoseStack()
            for (pos in drawn) {
                val state = scene.getBlockState(pos)
                random.setSeed(state.getSeed(pos))
                pose.pushPose()
                pose.translate((pos.x - origin.x).toFloat(), (pos.y - origin.y).toFloat(), (pos.z - origin.z).toFloat())
                blocks.renderBatched(state, pos, scene, pose, builder(ItemBlockRenderTypes.getChunkRenderType(state)), true, random)
                pose.popPose()
            }
        }
        if (fluids) tesselateFluids(scene, positions, origin, builder)
        val meshes = HashMap<RenderType, MeshData?>()
        builders.forEach { (layer, buffer) -> meshes[layer] = buffer.build() }
        return Prepared(meshes, fabric)
    }

    private fun tesselateFluids(
        scene: BlockAndTintGetter,
        positions: Iterable<BlockPos>,
        origin: BlockPos,
        builder: (RenderType) -> BufferBuilder,
    ) {
        val blocks = Minecraft.getInstance().blockRenderer
        val shifted = ShiftedConsumer()
        for (pos in positions) {
            val state = scene.getBlockState(pos)
            val fluid = state.fluidState
            if (fluid.isEmpty) continue
            shifted.inner = builder(ItemBlockRenderTypes.getRenderLayer(fluid))
            shifted.dx = ((pos.x and SECTION_MASK.inv()) - origin.x).toFloat()
            shifted.dy = ((pos.y and SECTION_MASK.inv()) - origin.y).toFloat()
            shifted.dz = ((pos.z and SECTION_MASK.inv()) - origin.z).toFloat()
            blocks.renderLiquid(pos, scene, shifted, state, fluid)
        }
    }

    private class ShiftedConsumer : VertexConsumer {
        var inner: VertexConsumer? = null
        var dx = 0f
        var dy = 0f
        var dz = 0f

        private fun target(): VertexConsumer = inner ?: error("No fluid buffer")

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = also { target().addVertex(x + dx, y + dy, z + dz) }

        override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer = also { target().setColor(r, g, b, a) }

        override fun setUv(u: Float, v: Float): VertexConsumer = also { target().setUv(u, v) }

        override fun setUv1(u: Int, v: Int): VertexConsumer = also { target().setUv1(u, v) }

        override fun setUv2(u: Int, v: Int): VertexConsumer = also { target().setUv2(u, v) }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = also { target().setNormal(x, y, z) }
    }

    private fun upload(slot: Layer, mesh: MeshData?) {
        slot.indexCount = 0
        if (mesh == null) return
        val count = mesh.drawState().indexCount()
        //? if >= 1.21.4 {
        val buffer = slot.buffer ?: VertexBuffer(BufferUsage.STATIC_WRITE).also { slot.buffer = it }
        //?} else {
        /^val buffer = slot.buffer ?: VertexBuffer(VertexBuffer.Usage.STATIC).also { slot.buffer = it }
        ^///?}
        buffer.bind()
        buffer.upload(mesh)
        VertexBuffer.unbind()
        slot.indexCount = count
    }

    fun draw(layer: RenderType, modelView: Matrix4f, projection: Matrix4f, shader: LiveShader): Boolean {
        val slot = layers[layer] ?: return false
        val buffer = slot.buffer ?: return false
        if (slot.indexCount == 0) return false
        buffer.bind()
        buffer.drawWithShader(modelView, projection, shader)
        return true
    }

    override fun close() {
        layers.values.forEach {
            it.buffer?.close()
            it.bytes.close()
        }
        layers.clear()
    }

    private companion object {
        const val INITIAL_BYTES = 64 * 1024
        const val SECTION_MASK = 15
    }
}

internal class RenderBlocksRenderer(private val label: String) : AutoCloseable {
    private val lightmap = LiveLightmap(label)

    fun render(
        target: RenderTarget,
        aspect: Float,
        view: RenderBlocksView,
        lighting: RenderBlocksLighting,
        draws: List<RenderBlocksDraw>,
        clearColor: Boolean = true,
    ): Boolean {
        val light = lightmap.update(lighting)
        val camera = view.modelView()
        val eye = view.eye(Vector3f())
        val projection = Matrix4f().setPerspective(Math.toRadians(view.fovDeg.toDouble()).toFloat(), aspect, NEAR, FAR)
        val mc = Minecraft.getInstance()
        target.bindWrite(true)
        if (clearColor) {
            RenderSystem.clearColor(0f, 0f, 0f, 0f)
            liveClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        } else {
            liveClear(GL11.GL_DEPTH_BUFFER_BIT)
        }
        try {
            for (layer in RenderType.chunkBufferLayers()) {
                val shader = shader(mc, layer) ?: continue
                layer.setupRenderState()
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS)
                RenderSystem.setShaderTexture(2, light)
                for (draw in draws) {
                    val origin = draw.origin
                    val modelView = if (origin == null) {
                        Matrix4f(camera).mul(draw.pose)
                    } else {
                        Matrix4f(camera).translate(eye).translate(origin.x() - eye.x, origin.y() - eye.y, origin.z() - eye.z).mul(draw.pose)
                    }
                    draw.mesh.draw(layer, modelView, projection, shader)
                }
                layer.clearRenderState()
            }
        } finally {
            VertexBuffer.unbind()
            mc.mainRenderTarget.bindWrite(true)
        }
        return true
    }

    private fun shader(mc: Minecraft, layer: RenderType): LiveShader? {
        //? if >= 1.21.4 {
        val program = when (layer) {
            RenderType.solid() -> CoreShaders.RENDERTYPE_SOLID
            RenderType.cutoutMipped() -> CoreShaders.RENDERTYPE_CUTOUT_MIPPED
            RenderType.cutout() -> CoreShaders.RENDERTYPE_CUTOUT
            RenderType.translucent() -> CoreShaders.RENDERTYPE_TRANSLUCENT
            RenderType.tripwire() -> CoreShaders.RENDERTYPE_TRIPWIRE
            else -> return null
        }
        return mc.shaderManager.getProgram(program)
        //?} else {
        /^val name = when (layer) {
            RenderType.solid() -> "rendertype_solid"
            RenderType.cutoutMipped() -> "rendertype_cutout_mipped"
            RenderType.cutout() -> "rendertype_cutout"
            RenderType.translucent() -> "rendertype_translucent"
            RenderType.tripwire() -> "rendertype_tripwire"
            else -> return null
        }
        return mc.gameRenderer.getShader(name)
        ^///?}
    }

    override fun close() {
        lightmap.close()
    }

    companion object {
        const val NEAR = 0.05f
        const val FAR = 256f
    }
}

internal class LiveLightmap(private val label: String) : AutoCloseable {
    //? if >= 1.21.4 {
    private val target = TextureTarget(SIZE, SIZE, false).apply {
        setFilterMode(GL11.GL_LINEAR)
        setClearColor(1f, 1f, 1f, 1f)
        clear()
    }
    private val quad: VertexBuffer by lazy { liveQuad() }
    //?} else {
    /^private val texture = DynamicTexture(SIZE, SIZE, false)
    ^///?}
    private var key: List<Any>? = null

    fun update(lighting: RenderBlocksLighting): Int {
        val next = listOf<Any>(lighting.brightness, lighting.blockFactor, lighting.skyFactor, lighting.skyLightColor)
        //? if >= 1.21.4 {
        if (next == key) return target.colorTextureId
        val shader = Minecraft.getInstance().shaderManager.getProgram(CoreShaders.LIGHTMAP) ?: return target.colorTextureId
        shader.safeGetUniform("AmbientLightFactor").set(0f)
        shader.safeGetUniform("SkyFactor").set(lighting.skyFactor)
        shader.safeGetUniform("BlockFactor").set(lighting.blockFactor)
        shader.safeGetUniform("UseBrightLightmap").set(0)
        shader.safeGetUniform("SkyLightColor").set(lighting.skyLightColor.x(), lighting.skyLightColor.y(), lighting.skyLightColor.z())
        shader.safeGetUniform("NightVisionFactor").set(0f)
        shader.safeGetUniform("DarknessScale").set(0f)
        shader.safeGetUniform("DarkenWorldFactor").set(0f)
        shader.safeGetUniform("BrightnessFactor").set(lighting.brightness)
        target.bindWrite(true)
        quad.bind()
        quad.drawWithShader(Matrix4f(), Matrix4f(), shader)
        VertexBuffer.unbind()
        target.unbindWrite()
        key = next
        return target.colorTextureId
        //?} else {
        /^if (next == key) return texture.id
        val pixels = texture.pixels ?: return texture.id
        fillPixels(pixels, lighting)
        texture.upload()
        key = next
        return texture.id
        ^///?}
    }

    //? if < 1.21.4 {
    /^private fun fillPixels(pixels: NativeImage, lighting: RenderBlocksLighting) {
        val color = Vector3f()
        val gray = Vector3f(GRAY, GRAY, GRAY)
        for (sky in 0 until SIZE) for (block in 0 until SIZE) {
            val skyLight = curve(sky) * lighting.skyFactor
            val blockLight = curve(block) * lighting.blockFactor
            color.set(blockLight, blockLight * ((blockLight * 0.6f + 0.4f) * 0.6f + 0.4f), blockLight * (blockLight * blockLight * 0.6f + 0.4f))
            color.add(Vector3f(lighting.skyLightColor).mul(skyLight))
            color.lerp(gray, GRAY_BLEND)
            clamp(color)
            color.lerp(Vector3f(notGamma(color.x), notGamma(color.y), notGamma(color.z)), lighting.brightness.coerceAtLeast(0f))
            color.lerp(gray, GRAY_BLEND)
            clamp(color)
            color.mul(CHANNEL)
            pixels.setPixelRGBA(block, sky, OPAQUE or (color.z.toInt() shl BLUE) or (color.y.toInt() shl GREEN) or color.x.toInt())
        }
    }

    private fun curve(level: Int): Float {
        val ratio = level / MAX_LEVEL
        return ratio / (CURVE_A - CURVE_B * ratio)
    }

    private fun notGamma(value: Float): Float {
        val inverted = 1f - value
        return 1f - inverted * inverted * inverted * inverted
    }

    private fun clamp(color: Vector3f) {
        color.set(color.x.coerceIn(0f, 1f), color.y.coerceIn(0f, 1f), color.z.coerceIn(0f, 1f))
    }
    ^///?}

    override fun close() {
        //? if >= 1.21.4 {
        target.destroyBuffers()
        //?} else {
        /^texture.close()
        ^///?}
    }

    private companion object {
        const val SIZE = 16
        const val GRAY = 0.75f
        const val GRAY_BLEND = 0.04f
        const val CHANNEL = 255f
        const val MAX_LEVEL = 15f
        const val CURVE_A = 4f
        const val CURVE_B = 3f
        const val OPAQUE = 0xFF shl 24
        const val BLUE = 16
        const val GREEN = 8
    }
}

//? if >= 1.21.4 {
internal class LiveFogBuffer(private val label: String) : AutoCloseable {
    fun write(color: Vector4f, renderStart: Float, renderEnd: Float, skyEnd: Float): FogParameters =
        FogParameters(renderStart, minOf(renderEnd, skyEnd), FogShape.CYLINDER, color.x, color.y, color.z, color.w)

    override fun toString(): String = label

    override fun close() = Unit
}
//?} else {
/^internal class LiveFog(val start: Float, val end: Float, val color: FloatArray, val shape: FogShape) {
    fun apply() {
        RenderSystem.setShaderFogStart(start)
        RenderSystem.setShaderFogEnd(end)
        RenderSystem.setShaderFogColor(color[0], color[1], color[2], color[3])
        RenderSystem.setShaderFogShape(shape)
    }

    companion object {
        fun current() = LiveFog(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor().copyOf(), RenderSystem.getShaderFogShape())
    }
}

internal class LiveFogBuffer(private val label: String) : AutoCloseable {
    fun write(color: Vector4f, renderStart: Float, renderEnd: Float, skyEnd: Float): LiveFog =
        LiveFog(renderStart, minOf(renderEnd, skyEnd), floatArrayOf(color.x, color.y, color.z, color.w), FogShape.CYLINDER)

    override fun toString(): String = label

    override fun close() = Unit
}
^///?}
*///?}
