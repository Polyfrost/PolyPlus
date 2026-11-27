package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.8 && < 1.21.11 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.Identifier
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.OptionalDouble
import java.util.OptionalInt

class NameplateMaterialRenderType(
    private val key: String,
    private val materialPipeline: RenderPipeline,
    private val textures: List<Identifier>,
    private val samplers: List<String>,
) : RenderType(key, BUFFER_SIZE, false, false, {}, {}) {
    override fun format(): VertexFormat = materialPipeline.vertexFormat

    override fun mode(): VertexFormat.Mode = materialPipeline.vertexFormatMode

    //? if >= 1.21.10 {
    override fun pipeline(): RenderPipeline = materialPipeline
    //?}

    override fun draw(mesh: MeshData) {
        val minecraft = Minecraft.getInstance()
        val transforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),
            WHITE,
            //? if >= 1.21.10 {
            NO_OFFSET,
            //?} else {
            /*RenderSystem.getModelOffset(),
            *///?}
            RenderSystem.getTextureMatrix(),
            RenderSystem.getShaderLineWidth(),
        )
        mesh.use { data ->
            val format = materialPipeline.vertexFormat
            val vertices = format.uploadImmediateVertexBuffer(data.vertexBuffer())
            val sorted = data.indexBuffer()
            val indices: GpuBuffer
            val indexType: VertexFormat.IndexType
            if (sorted == null) {
                val sequential = RenderSystem.getSequentialBuffer(data.drawState().mode())
                indices = sequential.getBuffer(data.drawState().indexCount())
                indexType = sequential.type()
            } else {
                indices = format.uploadImmediateIndexBuffer(sorted)
                indexType = data.drawState().indexType()
            }
            val target = minecraft.mainRenderTarget
            val color = RenderSystem.outputColorTextureOverride ?: target.colorTextureView ?: return
            val depth = if (target.useDepth) RenderSystem.outputDepthTextureOverride ?: target.depthTextureView else null
            RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass({ LABEL }, color, OptionalInt.empty(), depth, OptionalDouble.empty())
                .use { pass ->
                    pass.setPipeline(materialPipeline)
                    val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
                    if (scissor.enabled()) {
                        pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height())
                    }
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform(DYNAMIC_TRANSFORMS, transforms)
                    NameplateMaterialUniforms.bind(key, pass)
                    pass.setVertexBuffer(0, vertices)
                    pass.bindSampler(LIGHTMAP, minecraft.gameRenderer.lightTexture().textureView)
                    for ((i, texture) in textures.withIndex()) {
                        pass.bindSampler(samplers[i], minecraft.textureManager.getTexture(texture).textureView)
                    }
                    pass.setIndexBuffer(indices, indexType)
                    pass.drawIndexed(0, 0, data.drawState().indexCount(), 1)
                }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1536
        const val LABEL = "PolyPlus nameplate material"
        const val DYNAMIC_TRANSFORMS = "DynamicTransforms"
        const val LIGHTMAP = "Sampler2"
        val WHITE = Vector4f(1f, 1f, 1f, 1f)
        val NO_OFFSET = Vector3f()
    }
}
*///?}
