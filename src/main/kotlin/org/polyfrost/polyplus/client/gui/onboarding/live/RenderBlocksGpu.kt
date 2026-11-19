package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 1.21.8 && < 26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.RenderShape
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import java.util.EnumMap
import java.util.OptionalDouble
import java.util.OptionalInt
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.pipeline.RenderPipeline

internal fun liveTransform(pose: Matrix4f, color: Vector4f, offset: Vector3f): GpuBufferSlice =
    //? if >= 1.21.11 {
    RenderSystem.getDynamicUniforms().writeTransform(pose, color, offset, Matrix4f())
    //?} else {
    /^RenderSystem.getDynamicUniforms().writeTransform(pose, color, offset, Matrix4f(), 0f)
    ^///?}

internal class RenderBlocksMesh(private val label: String) : AutoCloseable {
    private class Layer {
        val bytes = ByteBufferBuilder(INITIAL_BYTES)
        var buffer: GpuBuffer? = null
        var indexCount = 0
    }

    private val layers = EnumMap<ChunkSectionLayer, Layer>(ChunkSectionLayer::class.java)

    fun isEmpty() = layers.values.all { it.indexCount == 0 }

    fun rebuild(scene: RenderBlocksScene, positions: Iterable<BlockPos> = scene.blocks.keys, grassMode: Int? = null): Boolean =
        rebuildRegion(scene, positions, BlockPos.ZERO, grassMode)

    class Prepared internal constructor(internal val meshes: Map<ChunkSectionLayer, MeshData?>, val fabric: Boolean) : AutoCloseable {
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
        prepared.use { layers.forEach { (layer, slot) -> upload(layer, slot, it.meshes[layer]) } }
    }

    fun prepareRegion(
        scene: BlockAndTintGetter,
        positions: Iterable<BlockPos>,
        origin: BlockPos,
        grassMode: Int? = null,
        fluids: Boolean = false,
    ): Prepared {
        val builders = EnumMap<ChunkSectionLayer, BufferBuilder>(ChunkSectionLayer::class.java)
        val builder = { layer: ChunkSectionLayer ->
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
            val parts = ArrayList<BlockModelPart>()
            val pose = PoseStack()
            for (pos in drawn) {
                val state = scene.getBlockState(pos)
                random.setSeed(state.getSeed(pos))
                blocks.getBlockModel(state).collectParts(random, parts)
                pose.pushPose()
                pose.translate((pos.x - origin.x).toFloat(), (pos.y - origin.y).toFloat(), (pos.z - origin.z).toFloat())
                blocks.renderBatched(state, pos, scene, pose, builder(ItemBlockRenderTypes.getChunkRenderType(state)), true, parts)
                pose.popPose()
                parts.clear()
            }
        }
        if (fluids) tesselateFluids(scene, positions, origin, builder)
        val meshes = EnumMap<ChunkSectionLayer, MeshData?>(ChunkSectionLayer::class.java)
        builders.forEach { (layer, buffer) -> meshes[layer] = buffer.build() }
        return Prepared(meshes, fabric)
    }

    private fun tesselateFluids(
        scene: BlockAndTintGetter,
        positions: Iterable<BlockPos>,
        origin: BlockPos,
        builder: (ChunkSectionLayer) -> BufferBuilder,
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

        //? if >= 1.21.11 {
        override fun setColor(color: Int): VertexConsumer = also { target().setColor(color) }

        override fun setLineWidth(width: Float): VertexConsumer = also { target().setLineWidth(width) }
        //?}

        override fun setUv(u: Float, v: Float): VertexConsumer = also { target().setUv(u, v) }

        override fun setUv1(u: Int, v: Int): VertexConsumer = also { target().setUv1(u, v) }

        override fun setUv2(u: Int, v: Int): VertexConsumer = also { target().setUv2(u, v) }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = also { target().setNormal(x, y, z) }
    }

    private fun upload(layer: ChunkSectionLayer, slot: Layer, mesh: MeshData?) {
        slot.indexCount = 0
        if (mesh == null) return
        mesh.use {
            val data = it.vertexBuffer()
            val size = data.remaining()
            var buffer = slot.buffer
            if (buffer == null || buffer.isClosed || buffer.size() < size) {
                buffer?.close()
                buffer = RenderSystem.getDevice().createBuffer(
                    { "$label ${layer.label()}" },
                    GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                    //? if >= 1.21.11 {
                    maxOf(size, INITIAL_BYTES).toLong() * 2,
                    //?} else {
                    /^maxOf(size, INITIAL_BYTES) * 2,
                    ^///?}
                )
                slot.buffer = buffer
            }
            //? if >= 1.21.11 {
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, size.toLong()), data)
            //?} else {
            /^RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, size), data)
            ^///?}
            slot.indexCount = it.drawState().indexCount()
        }
    }

    fun draw(pass: RenderPass, layer: ChunkSectionLayer): Boolean {
        val slot = layers[layer] ?: return false
        val buffer = slot.buffer ?: return false
        if (slot.indexCount == 0) return false
        val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        pass.setVertexBuffer(0, buffer)
        pass.setIndexBuffer(indices.getBuffer(slot.indexCount), indices.type())
        pass.drawIndexed(0, 0, slot.indexCount, 1)
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
    private val projection = CachedPerspectiveProjectionMatrixBuffer(label, NEAR, FAR)

    fun render(
        target: RenderTarget,
        aspect: Float,
        view: RenderBlocksView,
        lighting: RenderBlocksLighting,
        draws: List<RenderBlocksDraw>,
        clearColor: Boolean = true,
    ): Boolean {
        val colorView = target.colorTextureView ?: return false
        val depthView = target.depthTextureView ?: return false
        RenderSystem.getShaderFog() ?: return false
        val lightView = lightmap.update(lighting) ?: return false
        val projectionSlice = projection.getBuffer(target.width, target.height, view.fovDeg)
        val camera = view.modelView()
        val eye = view.eye(Vector3f())
        val transforms = draws.map { draw ->
            val origin = draw.origin
            if (origin == null) {
                liveTransform(Matrix4f(camera).mul(draw.pose), WHITE, Vector3f())
            } else {
                liveTransform(Matrix4f(camera).translate(eye).mul(draw.pose), WHITE, Vector3f(origin).sub(eye))
            }
        }
        val atlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { label },
            colorView,
            if (clearColor) OptionalInt.of(0) else OptionalInt.empty(),
            depthView,
            OptionalDouble.of(1.0),
        ).use { pass ->
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("Projection", projectionSlice)
            //? if >= 1.21.11 {
            pass.bindTexture("Sampler0", atlas.textureView, atlas.sampler)
            pass.bindTexture("Sampler2", lightView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            //?} else {
            /^pass.bindSampler("Sampler0", atlas.textureView)
            pass.bindSampler("Sampler2", lightView)
            ^///?}
            for (layer in ChunkSectionLayer.entries) {
                pass.setPipeline(pipeline(layer))
                draws.forEachIndexed { i, draw ->
                    pass.setUniform("DynamicTransforms", transforms[i])
                    draw.mesh.draw(pass, layer)
                }
            }
        }
        return true
    }

    override fun close() {
        lightmap.close()
        projection.close()
    }

    companion object {
        const val NEAR = 0.05f
        const val FAR = 256f
        private val WHITE = Vector4f(1f, 1f, 1f, 1f)

        //? if >= 1.21.11 {
        private fun pipeline(layer: ChunkSectionLayer): RenderPipeline = when (layer) {
            ChunkSectionLayer.SOLID -> RenderPipelines.SOLID_BLOCK
            ChunkSectionLayer.CUTOUT -> RenderPipelines.CUTOUT_BLOCK
            ChunkSectionLayer.TRANSLUCENT -> RenderPipelines.TRANSLUCENT_MOVING_BLOCK
            ChunkSectionLayer.TRIPWIRE -> RenderPipelines.TRIPWIRE_BLOCK
        }
        //?} else {
        /^private fun pipeline(layer: ChunkSectionLayer): RenderPipeline = when (layer) {
            ChunkSectionLayer.SOLID -> RenderPipelines.SOLID
            ChunkSectionLayer.CUTOUT_MIPPED -> RenderPipelines.CUTOUT_MIPPED
            ChunkSectionLayer.CUTOUT -> RenderPipelines.CUTOUT
            ChunkSectionLayer.TRANSLUCENT -> RenderPipelines.TRANSLUCENT
            ChunkSectionLayer.TRIPWIRE -> RenderPipelines.TRIPWIRE
        }
        ^///?}
    }
}

internal class LiveLightmap(private val label: String) : AutoCloseable {
    private val texture: GpuTexture = RenderSystem.getDevice().createTexture({ "$label lightmap" }, USAGE, TextureFormat.RGBA8, SIZE, SIZE, 1, 1)
    private val view: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)
    //? if >= 1.21.11 {
    private val ubo: GpuBuffer = RenderSystem.getDevice().createBuffer({ "$label lightmap info" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, UBO_SIZE.toLong())
    //?} else {
    /^private val ubo: GpuBuffer = RenderSystem.getDevice().createBuffer({ "$label lightmap info" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, UBO_SIZE)
    ^///?}
    private var key: List<Any>? = null

    init {
        //? if < 1.21.11
        /^texture.setTextureFilter(FilterMode.LINEAR, false)^/
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, -1)
    }

    fun update(lighting: RenderBlocksLighting): GpuTextureView? {
        val next = listOf<Any>(lighting.brightness, lighting.blockFactor, lighting.skyFactor, lighting.skyLightColor, lighting.ambientColor)
        if (next == key) return view
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        MemoryStack.stackPush().use { stack ->
            val data = Std140Builder.onStack(stack, UBO_SIZE)
                .putFloat(0f)
                .putFloat(lighting.skyFactor)
                .putFloat(lighting.blockFactor)
            //? if < 1.21.10
            /^data.putInt(0)^/
            data.putFloat(0f)
                .putFloat(0f)
                .putFloat(0f)
                .putFloat(lighting.brightness)
                .putVec3(lighting.skyLightColor.x(), lighting.skyLightColor.y(), lighting.skyLightColor.z())
            //? if >= 1.21.10
            data.putVec3(lighting.ambientColor.x(), lighting.ambientColor.y(), lighting.ambientColor.z())
            encoder.writeToBuffer(ubo.slice(), data.get())
        }
        encoder.createRenderPass({ "$label lightmap" }, view, OptionalInt.empty()).use { pass ->
            pass.setPipeline(RenderPipelines.LIGHTMAP)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("LightmapInfo", ubo)
            //? if >= 1.21.10 {
            pass.draw(0, FULLSCREEN_VERTICES)
            //?} else {
            /^val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer())
            pass.setIndexBuffer(indices.getBuffer(QUAD_INDICES), indices.type())
            pass.drawIndexed(0, 0, QUAD_INDICES, 1)
            ^///?}
        }
        key = next
        return view
    }

    override fun close() {
        view.close()
        texture.close()
        ubo.close()
    }

    private companion object {
        const val SIZE = 16
        const val USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST
        const val FULLSCREEN_VERTICES = 3
        const val QUAD_INDICES = 6
        //? if >= 1.21.10 {
        val UBO_SIZE = Std140SizeCalculator().putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().putVec3().putVec3().get()
        //?} else {
        /^val UBO_SIZE = Std140SizeCalculator().putFloat().putFloat().putFloat().putInt().putFloat().putFloat().putFloat().putFloat().putVec3().get()
        ^///?}
    }
}

internal class LiveFogBuffer(label: String) : AutoCloseable {
    //? if >= 1.21.11 {
    private val buffer: GpuBuffer = RenderSystem.getDevice().createBuffer({ "$label fog" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, SIZE.toLong())
    //?} else {
    /^private val buffer: GpuBuffer = RenderSystem.getDevice().createBuffer({ "$label fog" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, SIZE)
    ^///?}

    fun write(color: Vector4f, renderStart: Float, renderEnd: Float, skyEnd: Float): GpuBufferSlice {
        MemoryStack.stackPush().use { stack ->
            val data = Std140Builder.onStack(stack, SIZE)
                .putVec4(color)
                .putFloat(Float.MAX_VALUE)
                .putFloat(Float.MAX_VALUE)
                .putFloat(renderStart)
                .putFloat(renderEnd)
                .putFloat(skyEnd)
                .putFloat(skyEnd)
                .get()
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data)
        }
        return buffer.slice()
    }

    override fun close() {
        buffer.close()
    }

    private companion object {
        val SIZE = Std140SizeCalculator().putVec4().putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().get()
    }
}
*///?}
