package org.polyfrost.polyplus.client.gui.onboarding.live

//? if = 1.21.5 {
/*import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.BufferType
import com.mojang.blaze3d.buffers.BufferUsage
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.shaders.FogShape
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.FogParameters
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.RenderShape
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt

internal class RenderBlocksMesh(private val label: String) : AutoCloseable {
    private class Layer {
        val bytes = ByteBufferBuilder(INITIAL_BYTES)
        var buffer: GpuBuffer? = null
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
        prepared.use { layers.forEach { (layer, slot) -> upload(layer, slot, it.meshes[layer]) } }
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

    private fun upload(layer: RenderType, slot: Layer, mesh: MeshData?) {
        slot.indexCount = 0
        if (mesh == null) return
        mesh.use {
            val data = it.vertexBuffer()
            val size = data.remaining()
            var buffer = slot.buffer
            if (buffer == null || buffer.isClosed || buffer.size() < size) {
                buffer?.close()
                buffer = RenderSystem.getDevice().createBuffer({ "$label $layer" }, BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, maxOf(size, INITIAL_BYTES) * 2)
                slot.buffer = buffer
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer, data, 0)
            slot.indexCount = it.drawState().indexCount()
        }
    }

    fun draw(pass: RenderPass, layer: RenderType): Boolean {
        val slot = layers[layer] ?: return false
        val buffer = slot.buffer ?: return false
        if (slot.indexCount == 0) return false
        val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        pass.setVertexBuffer(0, buffer)
        pass.setIndexBuffer(indices.getBuffer(slot.indexCount), indices.type())
        pass.drawIndexed(0, slot.indexCount)
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
        val color = target.colorTexture ?: return false
        val depth = target.depthTexture ?: return false
        val light = lightmap.update(lighting)
        val camera = view.modelView()
        val eye = view.eye(Vector3f())
        val atlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).texture
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(
            Matrix4f().setPerspective(Math.toRadians(view.fovDeg.toDouble()).toFloat(), aspect, NEAR, FAR),
            ProjectionType.PERSPECTIVE,
        )
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        try {
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                color,
                if (clearColor) OptionalInt.of(0) else OptionalInt.empty(),
                depth,
                OptionalDouble.of(1.0),
            ).use { pass ->
                pass.bindSampler("Sampler0", atlas)
                pass.bindSampler("Sampler2", light)
                for (layer in RenderType.chunkBufferLayers()) {
                    pass.setPipeline(layer.renderPipeline)
                    for (draw in draws) {
                        val origin = draw.origin
                        if (origin == null) {
                            modelView.set(camera).mul(draw.pose)
                            RenderSystem.resetModelOffset()
                        } else {
                            modelView.set(camera).translate(eye).mul(draw.pose)
                            RenderSystem.setModelOffset(origin.x() - eye.x, origin.y() - eye.y, origin.z() - eye.z)
                        }
                        draw.mesh.draw(pass, layer)
                    }
                }
            }
        } finally {
            RenderSystem.resetModelOffset()
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
        }
        return true
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
    private val texture: GpuTexture = RenderSystem.getDevice().createTexture({ "$label lightmap" }, TextureFormat.RGBA8, SIZE, SIZE, 1)
    private var key: List<Any>? = null

    init {
        texture.setTextureFilter(FilterMode.LINEAR, false)
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, -1)
    }

    fun update(lighting: RenderBlocksLighting): GpuTexture {
        val next = listOf<Any>(lighting.brightness, lighting.blockFactor, lighting.skyFactor, lighting.skyLightColor)
        if (next == key) return texture
        val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(texture, OptionalInt.empty()).use { pass ->
            pass.setPipeline(RenderPipelines.LIGHTMAP)
            pass.setUniform("AmbientLightFactor", 0f)
            pass.setUniform("SkyFactor", lighting.skyFactor)
            pass.setUniform("BlockFactor", lighting.blockFactor)
            pass.setUniform("UseBrightLightmap", 0)
            pass.setUniform("SkyLightColor", lighting.skyLightColor.x(), lighting.skyLightColor.y(), lighting.skyLightColor.z())
            pass.setUniform("NightVisionFactor", 0f)
            pass.setUniform("DarknessScale", 0f)
            pass.setUniform("DarkenWorldFactor", 0f)
            pass.setUniform("BrightnessFactor", lighting.brightness)
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer())
            pass.setIndexBuffer(indices.getBuffer(QUAD_INDICES), indices.type())
            pass.drawIndexed(0, QUAD_INDICES)
        }
        key = next
        return texture
    }

    override fun close() {
        texture.close()
    }

    private companion object {
        const val SIZE = 16
        const val QUAD_INDICES = 6
    }
}

internal class LiveFogBuffer(private val label: String) : AutoCloseable {
    fun write(color: Vector4f, renderStart: Float, renderEnd: Float, skyEnd: Float): FogParameters =
        FogParameters(renderStart, minOf(renderEnd, skyEnd), FogShape.CYLINDER, color.x, color.y, color.z, color.w)

    override fun toString(): String = label

    override fun close() = Unit
}
*///?}
