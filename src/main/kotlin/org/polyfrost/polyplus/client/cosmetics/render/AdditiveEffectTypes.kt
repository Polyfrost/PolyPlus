package org.polyfrost.polyplus.client.cosmetics.render

//? if > 1.8.9 {
import net.minecraft.resources.Identifier
//?}

//? if >= 1.21.5 {
import net.minecraft.client.renderer.RenderPipelines
//?}

//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
//?} elif >= 26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.BindGroupLayouts
*///?} elif >= 26.1 {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
*///?} elif >= 1.21.5 {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
*///?} elif > 1.8.9 {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
*///?}

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
*///?}

//? if >= 1.21.4 && < 1.21.6 {
/*import net.minecraft.util.TriState
*///?}

//? if > 1.8.9 {
internal object AdditiveEffectTypes {
    //? if >= 1.21.5 {
    private const val NAMESPACE = "polyplus"
    //?}
    private const val NAME = "polyplus_effect_additive"

    private val types = HashMap<Identifier, RenderType>()

    //? if >= 1.21.5 {
    private val pipeline: RenderPipeline by lazy {
        //? if >= 26.3 {
        RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/effect_additive"))
            .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()
        //?} elif >= 26.2 {
        /*RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/effect_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()
        *///?} elif >= 26.1 {
        /*RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/effect_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()
        *///?} elif >= 1.21.6 {
        /*RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/effect_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .build()
        *///?} else {
        /*RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/effect_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthWrite(false)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .build()
        *///?}
    }
    //?}

    fun get(texture: Identifier): RenderType = types.getOrPut(texture) {
        //? if >= 1.21.11 {
        RenderType.create(NAME, RenderSetup.builder(pipeline).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup())
        //?} elif >= 1.21.5 {
        /*RenderType.create(
            NAME,
            BUFFER_SIZE,
            false,
            true,
            pipeline,
            RenderType.CompositeState.builder()
                //? if >= 1.21.6 {
                .setTextureState(RenderStateShard.TextureStateShard(texture, false))
                //?} else {
                /^.setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
                ^///?}
                .createCompositeState(false),
        )
        *///?} else {
        /*RenderType.create(
            NAME,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            BUFFER_SIZE,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                //? if >= 1.21.4 {
                .setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
                //?} else {
                /^.setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                ^///?}
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false),
        )
        *///?}
    }

    //? if < 1.21.11 {
    /*private const val BUFFER_SIZE = 1536
    *///?}
}
//?}
