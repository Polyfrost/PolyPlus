package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 1.21.5 {
import com.mojang.blaze3d.vertex.BufferBuilder
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
//? if >= 1.21.8 {
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
//?} else {
/*import net.minecraft.client.renderer.RenderType as ChunkSectionLayer
*///?}
//? if >= 1.21.8 && < 26.1 {
/*import net.fabricmc.fabric.api.renderer.v1.render.BlockVertexConsumerProvider
*///?} elif < 1.21.8 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CarpetBlock
//? if >= 1.21.4
import net.minecraft.world.level.block.MossyCarpetBlock
import net.minecraft.world.level.block.state.BlockState
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional
import kotlin.math.abs
//? if >= 26.1 {
import net.fabricmc.fabric.api.client.renderer.v1.Renderer
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform
import net.minecraft.client.color.block.BlockColors
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.BlockStateModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.resources.model.sprite.Material
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform
import net.minecraft.client.renderer.block.model.BlockModelPart
import net.minecraft.client.renderer.block.model.BlockStateModel
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import java.util.function.Predicate
*///?}

internal object RenderBlocksFabric {
    private val LOG = LoggerFactory.getLogger("polyplus/render-blocks")
    private val available: Boolean by lazy { FabricLoader.getInstance().isModLoaded(RENDERER_API) }
    private var failed = false

    //? if >= 26.1 {
    fun tesselate(
        scene: BlockAndTintGetter,
        positions: List<BlockPos>,
        origin: BlockPos,
        models: BlockStateModelSet,
        ambientOcclusion: Boolean,
        colors: BlockColors,
        grassMode: Int?,
        builder: (ChunkSectionLayer) -> BufferBuilder,
    ): Boolean {
        if (!available || failed) return false
        return runCatching {
            val renderer = Renderer.get() ?: return false
            val blocks = renderer.altModelBlockRenderer(ambientOcclusion, true, colors)
            val emitter = renderer.quadEmitter { quad -> quad.buffer(OverlayTexture.NO_OVERLAY, builder(quad.chunkLayer())) }
            val grass = grassMode?.let { GrassStyle(scene, it, grassTop(), origin) }
            grass?.let(emitter::pushTransform)
            for (pos in positions) {
                val state = scene.getBlockState(pos)
                var model = models.get(state)
                if (grass != null) {
                    val metadata = BetterGrassModel.metadata(model)
                    if (metadata != null) model = BetterGrassModel.unwrapped(model) ?: model
                    grass.at(pos, state, metadata)
                }
                blocks.tesselateBlock(
                    emitter,
                    (pos.x - origin.x).toFloat(), (pos.y - origin.y).toFloat(), (pos.z - origin.z).toFloat(),
                    scene, pos, state, model, state.getSeed(pos),
                )
            }
            grass?.let { emitter.popTransform() }
            true
        }.onFailure {
            failed = true
            LOG.error("[render-blocks] Fabric renderer path failed, using the vanilla block renderer", it)
        }.getOrDefault(false)
    }

    private fun grassTop(): TextureAtlasSprite =
        Minecraft.getInstance().atlasManager.get(Sheets.BLOCKS_MAPPER.defaultNamespaceApply(GRASS_TOP))
    //?} else {
    /*fun tesselate(
        scene: BlockAndTintGetter,
        positions: List<BlockPos>,
        origin: BlockPos,
        grassMode: Int?,
        builder: (ChunkSectionLayer) -> BufferBuilder,
    ): Boolean {
        if (!available || failed) return false
        return runCatching {
            val blocks = Minecraft.getInstance().blockRenderer
            //? if >= 1.21.8 {
            val output = BlockVertexConsumerProvider { layer -> builder(layer) }
            //?} else {
            /^val output = MultiBufferSource { layer -> builder(layer) }
            ^///?}
            val grass = grassMode?.let { GrassStyle(scene, it, grassTop(), origin) }
            val pose = PoseStack()
            for (pos in positions) {
                val state = scene.getBlockState(pos)
                var model = blocks.getBlockModel(state)
                if (grass != null) {
                    val metadata = BetterGrassModel.metadata(model)
                    if (metadata != null) model = BetterGrassModel.unwrapped(model) ?: model
                    grass.at(pos, state, metadata)
                    model = GrassModel(model, grass)
                }
                pose.pushPose()
                pose.translate((pos.x - origin.x).toFloat(), (pos.y - origin.y).toFloat(), (pos.z - origin.z).toFloat())
                blocks.modelRenderer.render(scene, model, state, pos, pose, output, true, state.getSeed(pos), OverlayTexture.NO_OVERLAY)
                pose.popPose()
            }
            true
        }.onFailure {
            failed = true
            LOG.error("[render-blocks] Fabric renderer path failed, using the vanilla block renderer", it)
        }.getOrDefault(false)
    }

    private fun grassTop(): TextureAtlasSprite =
        (Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS) as TextureAtlas)
            .getSprite(Identifier.withDefaultNamespace("block/$GRASS_TOP"))

    private class GrassModel(private val inner: BlockStateModel, private val style: QuadTransform) : BlockStateModel {
        override fun collectParts(random: RandomSource, parts: MutableList<BlockModelPart>) = inner.collectParts(random, parts)

        override fun particleIcon(): TextureAtlasSprite = inner.particleIcon()

        override fun emitQuads(
            emitter: QuadEmitter,
            blockView: BlockAndTintGetter,
            pos: BlockPos,
            state: BlockState,
            random: RandomSource,
            cullTest: Predicate<Direction?>,
        ) {
            emitter.pushTransform(style)
            try {
                inner.emitQuads(emitter, blockView, pos, state, random, cullTest)
            } finally {
                emitter.popTransform()
            }
        }
    }
    *///?}

    private object BetterGrassModel {
        private class Access(val model: Class<*>, val metadata: Field, val wrapped: Field, val getLayer: Method, val getMaterial: Method)

        private val access: Access? by lazy {
            runCatching {
                val model = Class.forName(LBG_MODEL)
                val layer = Class.forName(LBG_LAYER)
                Access(
                    model,
                    model.getDeclaredField("metadata").apply { isAccessible = true },
                    WrapperBlockStateModel::class.java.getDeclaredField("wrapped").apply { isAccessible = true },
                    Class.forName(LBG_METADATA).getMethod("getLayer", Int::class.javaPrimitiveType),
                    //? if >= 26.1 {
                    layer.getMethod("getBakedMaterial", String::class.java),
                    //?} else {
                    /*layer.getMethod("getBakedTexture", String::class.java),
                    *///?}
                )
            }.getOrNull()
        }

        fun metadata(model: BlockStateModel): Any? {
            val lbg = access ?: return null
            if (!lbg.model.isInstance(model)) return null
            return runCatching { lbg.metadata.get(model) }.getOrNull()
        }

        fun unwrapped(model: BlockStateModel): BlockStateModel? =
            runCatching { access?.wrapped?.get(model) as? BlockStateModel }.getOrNull()

        //? if >= 26.1 {
        fun material(metadata: Any, tintIndex: Int, name: String): Material.Baked? = runCatching {
            val lbg = access ?: return null
            val layer = (lbg.getLayer.invoke(metadata, tintIndex) as? Optional<*>)?.orElse(null) ?: return null
            lbg.getMaterial.invoke(layer, name) as? Material.Baked
        }.getOrNull()
        //?} else {
        /*fun material(metadata: Any, tintIndex: Int, name: String): TextureAtlasSprite? = runCatching {
            val lbg = access ?: return null
            val layer = (lbg.getLayer.invoke(metadata, tintIndex) as? Optional<*>)?.orElse(null) ?: return null
            lbg.getMaterial.invoke(layer, name) as? TextureAtlasSprite
        }.getOrNull()
        *///?}

        fun hasLayer(metadata: Any, tintIndex: Int): Boolean = runCatching {
            val lbg = access ?: return false
            (lbg.getLayer.invoke(metadata, tintIndex) as? Optional<*>)?.isPresent == true
        }.getOrDefault(false)
    }

    private class GrassStyle(
        private val scene: BlockAndTintGetter,
        private val mode: Int,
        private val top: TextureAtlasSprite,
        private val origin: BlockPos,
    ) : QuadTransform {
        private var pos = BlockPos.ZERO
        private var local = BlockPos.ZERO
        private var state: BlockState = Blocks.AIR.defaultBlockState()
        private var metadata: Any? = null

        fun at(pos: BlockPos, state: BlockState, metadata: Any?) {
            this.pos = pos
            this.local = pos.subtract(origin)
            this.state = state
            this.metadata = metadata
        }

        override fun transform(quad: MutableQuadView): Boolean {
            if (mode == OnboardingFeatures.BETTER_GRASS_OFF) return true
            val face = quad.nominalFace() ?: return true
            if (face.axis == Direction.Axis.Y) return true
            val lbg = metadata
            if (lbg == null) {
                if (state.`is`(Blocks.GRASS_BLOCK) && quad.tintIndex() >= 0 && canFullyConnect(pos, face)) paintTop(quad, face)
                return true
            }
            if (!BetterGrassModel.hasLayer(lbg, quad.tintIndex())) return true
            if (mode == OnboardingFeatures.BETTER_GRASS_FASTEST) {
                bake(quad, lbg, CONNECT)
                return true
            }
            if (canFullyConnect(pos, face) && bake(quad, lbg, CONNECT)) return true
            if (mode != OnboardingFeatures.BETTER_GRASS_FANCY) return true
            val right = face.clockWise
            val left = face.counterClockWise
            val rightMatch = canConnect(pos.below().relative(right)) ||
                (canConnect(pos.relative(right)) && canFullyConnect(pos.relative(right), face))
            val leftMatch = canConnect(pos.below().relative(left)) ||
                (canConnect(pos.relative(left)) && canFullyConnect(pos.relative(left), face))
            when {
                rightMatch && leftMatch -> bake(quad, lbg, ARCH)
                rightMatch -> bake(quad, lbg, BLEND_UP_MIRRORED)
                leftMatch -> bake(quad, lbg, BLEND_UP)
            }
            return true
        }

        private fun bake(quad: MutableQuadView, metadata: Any, name: String): Boolean {
            val material = BetterGrassModel.material(metadata, quad.tintIndex(), name) ?: return false
            //? if >= 26.1 {
            val shifted = translated(quad)
            if (shifted) quad.translate(-local.x.toFloat(), -local.y.toFloat(), -local.z.toFloat())
            quad.materialBake(material, MutableQuadView.BAKE_LOCK_UV)
            if (shifted) quad.translate(local.x.toFloat(), local.y.toFloat(), local.z.toFloat())
            //?} else {
            /*quad.spriteBake(material, MutableQuadView.BAKE_LOCK_UV)
            *///?}
            return true
        }

        private fun paintTop(quad: MutableQuadView, face: Direction) {
            val shifted = translated(quad)
            for (i in 0 until QUAD_VERTICES) {
                val x = if (shifted) quad.x(i) - local.x else quad.x(i)
                val y = if (shifted) quad.y(i) - local.y else quad.y(i)
                val z = if (shifted) quad.z(i) - local.z else quad.z(i)
                val u = when (face) {
                    Direction.NORTH -> 1f - x
                    Direction.SOUTH -> x
                    Direction.WEST -> z
                    else -> 1f - z
                }
                quad.uv(i, top.u0 + (top.u1 - top.u0) * u, top.v0 + (top.v1 - top.v0) * (1f - y))
            }
        }

        private fun translated(quad: MutableQuadView): Boolean =
            abs(quad.x(0) - local.x - HALF) <= NEAR_BLOCK &&
                abs(quad.y(0) - local.y - HALF) <= NEAR_BLOCK &&
                abs(quad.z(0) - local.z - HALF) <= NEAR_BLOCK

        private fun canFullyConnect(from: BlockPos, face: Direction): Boolean = canConnect(from.relative(face).below())

        private fun canConnect(adjacent: BlockPos): Boolean {
            if (scene.getBlockState(adjacent) != state) return false
            val upPos = adjacent.above()
            val up = scene.getBlockState(upPos)
            //? if >= 1.21.4 {
            return up.isAir || !up.isFaceSturdy(scene, upPos, Direction.DOWN) || up.block is CarpetBlock || up.block is MossyCarpetBlock
            //?} else {
            /*return up.isAir || !up.isFaceSturdy(scene, upPos, Direction.DOWN) || up.block is CarpetBlock
            *///?}
        }
    }

    private const val RENDERER_API = "fabric-renderer-api-v1"
    private const val LBG_MODEL = "dev.lambdaurora.lambdabettergrass.model.LBGBakedModel"
    private const val LBG_METADATA = "dev.lambdaurora.lambdabettergrass.metadata.LBGMetadata"
    private const val LBG_LAYER = "dev.lambdaurora.lambdabettergrass.metadata.grass.LBGGrassLayer"
    private const val GRASS_TOP = "grass_block_top"
    private const val CONNECT = "connect"
    private const val ARCH = "arch"
    private const val BLEND_UP = "blend_up"
    private const val BLEND_UP_MIRRORED = "blend_up_m"
    private const val QUAD_VERTICES = 4
    private const val HALF = 0.5f
    private const val NEAR_BLOCK = 0.6f
}
//?} elif > 1.8.9 {
/*import com.mojang.blaze3d.vertex.BufferBuilder
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter

internal object RenderBlocksFabric {
    fun tesselate(
        scene: BlockAndTintGetter,
        positions: List<BlockPos>,
        origin: BlockPos,
        grassMode: Int?,
        builder: (RenderType) -> BufferBuilder,
    ): Boolean = false
}
*///?}
