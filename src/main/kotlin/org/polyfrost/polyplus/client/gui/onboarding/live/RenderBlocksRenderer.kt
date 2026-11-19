package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.tan
//? if >= 26.1 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Lightmap
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.renderer.block.FluidRenderer
import net.minecraft.client.renderer.block.ModelBlockRenderer
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.state.LightmapRenderState
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.RenderShape
import org.joml.Vector4f
import java.util.EnumMap
import java.util.Optional
import java.util.OptionalDouble
//?}
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
//?} elif >= 26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.textures.FilterMode
*///?}
//? if = 26.2
/*import com.mojang.blaze3d.PrimitiveTopology*/
//? if >= 26.1 && < 26.2 {
/*import com.mojang.blaze3d.vertex.VertexFormat.Mode as PrimitiveTopology
import java.util.OptionalInt
*///?}

internal fun liveRetire(task: () -> Unit) {
    //? if >= 1.21.5 {
    RenderSystem.queueFencedTask(task)
    //?} else {
    /*task()
    *///?}
}

internal fun PoseStack.turn(axis: Axis, degrees: Float) {
    //? if >= 26.3 {
    rotateDegrees(axis, degrees)
    //?} else {
    /*mulPose(axis.rotationDegrees(degrees))
    *///?}
}

internal class RenderBlocksView(
    val center: Vector3fc,
    val distance: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val fovDeg: Float = DEFAULT_FOV,
) {
    fun modelView(): Matrix4f = Matrix4f()
        .translate(0f, 0f, -distance)
        .rotateX(Math.toRadians(pitchDeg.toDouble()).toFloat())
        .rotateY(Math.toRadians(yawDeg.toDouble()).toFloat())
        .translate(-center.x(), -center.y(), -center.z())

    fun viewProjection(aspect: Float, out: Matrix4f): Matrix4f = out
        .setPerspective(Math.toRadians(fovDeg.toDouble()).toFloat(), aspect, RenderBlocksRenderer.NEAR, RenderBlocksRenderer.FAR)
        .mul(modelView())

    fun eye(out: Vector3f): Vector3f = modelView().invert().transformPosition(out.set(0f, 0f, 0f))

    companion object {
        const val DEFAULT_FOV = 30f

        fun fit(center: Vector3fc, radius: Float, yawDeg: Float, pitchDeg: Float, fovDeg: Float, aspect: Float): RenderBlocksView {
            val halfV = Math.toRadians(fovDeg / 2.0)
            val halfH = atan(tan(halfV) * aspect.coerceAtLeast(MIN_ASPECT))
            val distance = radius / sin(minOf(halfV, halfH)).toFloat()
            return RenderBlocksView(center, distance, yawDeg, pitchDeg, fovDeg)
        }

        private const val MIN_ASPECT = 0.1f
    }
}

internal class RenderBlocksLighting(
    val brightness: Float,
    val blockFactor: Float = VANILLA_BLOCK_FACTOR,
    val skyFactor: Float = 1f,
    val skyLightColor: Vector3fc = WHITE,
    val blockLightTint: Vector3fc = OVERWORLD_BLOCK_TINT,
    val ambientColor: Vector3fc = DEFAULT_AMBIENT,
) {
    companion object {
        val OVERWORLD_BLOCK_TINT: Vector3fc = Vector3f(1f, 0xD8 / 255f, 0x8C / 255f)
        val BLACK: Vector3fc = Vector3f()
        val WHITE: Vector3fc = Vector3f(1f, 1f, 1f)
        //? if >= 26.1 {
        const val VANILLA_BLOCK_FACTOR = 1.4f
        val DEFAULT_AMBIENT: Vector3fc = BLACK
        //?} else {
        /*const val VANILLA_BLOCK_FACTOR = 1.5f
        val DEFAULT_AMBIENT: Vector3fc = WHITE
        *///?}
    }
}

internal class RenderBlocksDraw(val mesh: RenderBlocksMesh, val pose: Matrix4fc, val origin: Vector3fc? = null)

//? if >= 26.1 {
internal class RenderBlocksMesh(private val label: String) : AutoCloseable {
    private class Layer {
        val bytes = ByteBufferBuilder(INITIAL_BYTES)
        var buffer: GpuBuffer? = null
        var indexCount = 0
        var byteCount = 0L
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
        val mc = Minecraft.getInstance()
        val models = mc.modelManager.blockStateModelSet
        val ambientOcclusion = mc.options.ambientOcclusion().get()
        val builders = EnumMap<ChunkSectionLayer, BufferBuilder>(ChunkSectionLayer::class.java)
        val builder = { layer: ChunkSectionLayer ->
            builders.getOrPut(layer) {
                BufferBuilder(layers.getOrPut(layer, ::Layer).bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK)
            }
        }
        val drawn = positions.filter { pos ->
            val state = scene.getBlockState(pos)
            !state.isAir && state.renderShape == RenderShape.MODEL
        }
        val fabric = RenderBlocksFabric.tesselate(scene, drawn, origin, models, ambientOcclusion, mc.blockColors, grassMode, builder)
        if (!fabric) {
            val renderer = ModelBlockRenderer(ambientOcclusion, true, mc.blockColors)
            val output = BlockQuadOutput { x, y, z, quad, instance ->
                builder(quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance)
            }
            for (pos in drawn) {
                val state = scene.getBlockState(pos)
                renderer.tesselateBlock(
                    output,
                    (pos.x - origin.x).toFloat(), (pos.y - origin.y).toFloat(), (pos.z - origin.z).toFloat(),
                    scene, pos, state, models.get(state), state.getSeed(pos),
                )
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
        val renderer = FluidRenderer(Minecraft.getInstance().modelManager.fluidStateModelSet)
        val shifted = ShiftedConsumer()
        val output = FluidRenderer.Output { layer -> shifted.also { it.inner = builder(layer) } }
        for (pos in positions) {
            val state = scene.getBlockState(pos)
            val fluid = state.fluidState
            if (fluid.isEmpty) continue
            shifted.dx = ((pos.x and SECTION_MASK.inv()) - origin.x).toFloat()
            shifted.dy = ((pos.y and SECTION_MASK.inv()) - origin.y).toFloat()
            shifted.dz = ((pos.z and SECTION_MASK.inv()) - origin.z).toFloat()
            renderer.tesselate(scene, pos, output, state, fluid)
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

        override fun setColor(color: Int): VertexConsumer = also { target().setColor(color) }

        override fun setUv(u: Float, v: Float): VertexConsumer = also { target().setUv(u, v) }

        override fun setUv1(u: Int, v: Int): VertexConsumer = also { target().setUv1(u, v) }

        override fun setUv2(u: Int, v: Int): VertexConsumer = also { target().setUv2(u, v) }

        //? if >= 26.3 {
        override fun setUv3(u: Float, v: Float): VertexConsumer = also { target().setUv3(u, v) }
        //?}

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = also { target().setNormal(x, y, z) }

        override fun setLineWidth(width: Float): VertexConsumer = also { target().setLineWidth(width) }
    }

    private fun upload(layer: ChunkSectionLayer, slot: Layer, mesh: MeshData?) {
        slot.indexCount = 0
        if (mesh == null) return
        mesh.use {
            val data = it.vertexBuffer()
            val size = data.remaining().toLong()
            var buffer = slot.buffer
            if (buffer == null || buffer.isClosed || buffer.size() < size) {
                buffer?.close()
                buffer = RenderSystem.getDevice().createBuffer(
                    { "$label ${layer.label()}" },
                    GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
                    maxOf(size, INITIAL_BYTES.toLong()) * 2,
                )
                slot.buffer = buffer
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, size), data)
            slot.byteCount = size
            slot.indexCount = it.drawState().indexCount()
        }
    }

    fun draw(pass: RenderPass, layer: ChunkSectionLayer): Boolean {
        val slot = layers[layer] ?: return false
        val buffer = slot.buffer ?: return false
        if (slot.indexCount == 0) return false
        val indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        //? if >= 26.2 {
        pass.setVertexBuffer(0, buffer.slice(0L, slot.byteCount))
        pass.setIndexBuffer(indices.getBuffer(slot.indexCount), indices.type())
        pass.drawIndexed(slot.indexCount, 1, 0, 0, 0)
        //?} else {
        /*pass.setVertexBuffer(0, buffer)
        pass.setIndexBuffer(indices.getBuffer(slot.indexCount), indices.type())
        pass.drawIndexed(0, 0, slot.indexCount, 1)
        *///?}
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
    private val lightmap = Lightmap()
    private val lightmapState = LightmapRenderState()
    private val projection = Projection()
    private val projectionBuffer = ProjectionMatrixBuffer(label)
    private var lightmapKey: List<Any>? = null

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
        updateLightmap(lighting)
        projection.setupPerspective(NEAR, FAR, view.fovDeg, aspect, 1f)
        val projectionSlice = projectionBuffer.getBuffer(projection)
        val camera = view.modelView()
        val eye = view.eye(Vector3f())
        val transforms = draws.map { draw ->
            val origin = draw.origin
            if (origin == null) {
                //? if >= 26.3 {
                RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(camera).mul(draw.pose))
                //?} else {
                /*RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(camera).mul(draw.pose), WHITE, Vector3f(), Matrix4f())
                *///?}
            } else {
                RenderSystem.getDynamicUniforms().writeTransform(
                    Matrix4f(camera).translate(eye).mul(draw.pose),
                    WHITE,
                    Vector3f(origin).sub(eye),
                    Matrix4f(),
                )
            }
        }
        val atlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { label },
            colorView,
            //? if >= 26.2 {
            if (clearColor) Optional.of(Vector4f()) else Optional.empty(),
            depthView,
            OptionalDouble.of(0.0),
            //?} else {
            /*if (clearColor) OptionalInt.of(0) else OptionalInt.empty(),
            depthView,
            OptionalDouble.of(1.0),
            *///?}
        ).use { pass ->
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("Projection", projectionSlice)
            //? if >= 26.3 {
            pass.setUniform("Sampler0", atlas.textureView, atlas.sampler)
            pass.setUniform("Sampler2", lightmap.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            //?} else {
            /*pass.bindTexture("Sampler0", atlas.textureView, atlas.sampler)
            pass.bindTexture("Sampler2", lightmap.textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            *///?}
            for ((layer, pipeline) in LAYER_PIPELINES) {
                //? if >= 26.3 {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
                //?} else {
                /*pass.setPipeline(pipeline)
                *///?}
                draws.forEachIndexed { i, draw ->
                    pass.setUniform("DynamicTransforms", transforms[i])
                    draw.mesh.draw(pass, layer)
                }
            }
        }
        return true
    }

    private fun updateLightmap(lighting: RenderBlocksLighting) {
        val key = listOf<Any>(
            lighting.brightness, lighting.blockFactor, lighting.skyFactor,
            lighting.skyLightColor, lighting.blockLightTint, lighting.ambientColor,
        )
        if (key == lightmapKey) return
        lightmapKey = key
        lightmapState.needsUpdate = true
        lightmapState.brightness = lighting.brightness
        lightmapState.blockFactor = lighting.blockFactor
        lightmapState.skyFactor = lighting.skyFactor
        lightmapState.skyLightColor = lighting.skyLightColor
        lightmapState.blockLightTint = lighting.blockLightTint
        lightmapState.ambientColor = lighting.ambientColor
        lightmapState.nightVisionEffectIntensity = 0f
        lightmapState.darknessEffectScale = 0f
        lightmapState.bossOverlayWorldDarkening = 0f
        lightmap.render(lightmapState)
    }

    override fun close() {
        lightmap.close()
        projectionBuffer.close()
    }

    companion object {
        const val NEAR = 0.05f
        const val FAR = 256f
        private val WHITE = Vector4f(1f, 1f, 1f, 1f)
        private val LAYER_PIPELINES: List<Pair<ChunkSectionLayer, RenderPipeline>> = listOf(
            ChunkSectionLayer.SOLID to RenderPipelines.SOLID_BLOCK,
            ChunkSectionLayer.CUTOUT to RenderPipelines.CUTOUT_BLOCK,
            ChunkSectionLayer.TRANSLUCENT to RenderPipelines.TRANSLUCENT_BLOCK,
        )
    }
}
//?}
//?}
