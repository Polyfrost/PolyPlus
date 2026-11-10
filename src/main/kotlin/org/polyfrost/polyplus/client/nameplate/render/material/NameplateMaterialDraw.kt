package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.resources.Identifier
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import net.minecraft.util.FastColor.ARGB32 as ARGB
*///?}
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.NameplateCanvas
import org.polyfrost.polyplus.client.nameplate.render.NameplateFrame
import org.polyfrost.polyplus.client.nameplate.render.NameplateGeometry
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.client.nameplate.render.PortraitGuard
import org.polyfrost.polyplus.client.nameplate.render.Vertices
//? if >= 26.2 {
import net.minecraft.client.renderer.rendertype.RenderSetup
import org.polyfrost.polyplus.mixin.client.nameplate.RenderTypeInvoker
//?}
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialOp
import kotlin.math.roundToInt

object NameplateMaterialDraw {
    const val MAX_DRAWS_PER_FRAME: Int = 1024

    private const val OPAQUE = 255
    private const val CHANNEL = 255f
    //? if >= 26.3 {
    private const val INDEX_SHIFT = 8
    private const val DEPTH_ORDER = -1_000_000
    private const val DEPTH_SLOTS = 64
    //?}

    private val pools = HashMap<PoolKey, Pool>()
    private val probe = PoolKey("", emptyList())
    private var pooled = 0

    fun submit(
        frame: NameplateFrame,
        program: NameplateShaderProgram,
        area: PixelRect,
        local: PixelRect,
        mirrorX: Boolean,
        z: Float,
        frameIndex: Int,
        uniforms: IntArray,
        textures: List<Identifier> = emptyList(),
        opacity: Int = OPAQUE,
        order: Int = NameplateCanvas.ORDER,
        clip: IntArray? = null,
    ): Boolean {
        val entry = NameplateMaterialPipelines.ready(program.hash) ?: return false
        val registered = entry.program
        if (textures.size != registered.samplers.size || area.width <= 0 || area.height <= 0) {
            return false
        }
        val fade = frame.alpha * opacity.coerceIn(0, OPAQUE) / CHANNEL
        if (fade <= 0f) {
            return true
        }
        val pool = pool(entry, textures)
        //? if >= 26.3 {
        val at = pool.next(NameplateMaterialUniforms.batching)
        //?} else {
        /*val at = pool.next(false)
        *///?}
        if (at < 0) {
            return false
        }
        val slot = pool.slot(at)
        //? if >= 26.3 {
        val index = at % pool.blocks
        NameplateMaterialUniforms.stage(slot.binding, index, uniforms, frameIndex)
        val light = frame.lightCoords or (index shl INDEX_SHIFT)
        //?} else {
        /*NameplateMaterialUniforms.write(slot.binding, registered.uniformBytes, uniforms, frameIndex)
        val light = frame.lightCoords
        *///?}

        val color = PortraitGuard.vertex(vertexColor(registered.blend, fade))
        val quad = NameplateGeometry { pose, buffer ->
            if (clip == null) {
                quad(buffer, pose, area, local, mirrorX, local.x0, local.y0, local.x1, local.y1, z, light, color)
            } else {
                for (i in clip.indices step MaterialOp.CLIP_STRIDE) {
                    quad(buffer, pose, area, local, mirrorX, clip[i], clip[i + 1], clip[i + 2], clip[i + 3], z, light, color)
                }
            }
        }
        if (registered.blend == Material.Blend.ADDITIVE) {
            frame.submitAfterTerrain(order, slot.renderType, quad)
        } else {
            frame.submitGeometry(order, slot.renderType, quad)
        }
        val depth = slot.depthType
        if (depth != null && ARGB.alpha(color) == OPAQUE) {
            //? if >= 26.3 {
            frame.submitGeometry(DEPTH_ORDER - pool.index % DEPTH_SLOTS, depth, quad)
            //?} else {
            /*frame.submitGeometry(order, depth, quad)
            *///?}
        }
        return true
    }

    @JvmStatic
    fun endFrame() {
        for (pool in pools.values) {
            pool.reset()
        }
        NameplateMaterialUniforms.endFrame()
        NameplateMaterialAssets.sweep()
    }

    internal fun release(hash: String) {
        pools.keys.removeIf { it.hash == hash }
        NameplateMaterialUniforms.release(prefix(hash))
    }

    private fun pool(entry: NameplateMaterialPipelines.Entry, textures: List<Identifier>): Pool {
        val hash = entry.program.hash
        probe.hash = hash
        probe.textures = textures
        return pools[probe] ?: Pool(entry, textures, pooled++).also { pools[PoolKey(hash, textures)] = it }
    }

    private fun prefix(hash: String): String = "${PolyPlusConstants.ID}:nameplate_material/$hash/"

    private fun vertexColor(blend: Material.Blend, fade: Float): Int = when (blend) {
        Material.Blend.ALPHA -> ARGB.color((fade * CHANNEL).roundToInt(), OPAQUE, OPAQUE, OPAQUE)
        Material.Blend.ADDITIVE -> {
            val channel = (fade * CHANNEL).roundToInt()
            ARGB.color(OPAQUE, channel, channel, channel)
        }
    }

    private fun quad(
        buffer: VertexConsumer,
        pose: PoseStack.Pose,
        area: PixelRect,
        local: PixelRect,
        mirrorX: Boolean,
        a0: Int,
        b0: Int,
        a1: Int,
        b1: Int,
        z: Float,
        light: Int,
        color: Int,
    ) {
        val x0 = (if (mirrorX) area.x1 - (a1 - local.x0) else area.x0 + (a0 - local.x0)).toFloat()
        val x1 = (if (mirrorX) area.x1 - (a0 - local.x0) else area.x0 + (a1 - local.x0)).toFloat()
        val y0 = (area.y0 + (b0 - local.y0)).toFloat()
        val y1 = (area.y0 + (b1 - local.y0)).toFloat()
        val u0 = (if (mirrorX) a1 else a0).toFloat()
        val u1 = (if (mirrorX) a0 else a1).toFloat()
        val v0 = b0.toFloat()
        val v1 = b1.toFloat()
        vertex(buffer, pose, x0, y1, z, u0, v1, light, color)
        vertex(buffer, pose, x1, y1, z, u1, v1, light, color)
        vertex(buffer, pose, x1, y0, z, u1, v0, light, color)
        vertex(buffer, pose, x0, y0, z, u0, v0, light, color)
    }

    private fun vertex(buffer: VertexConsumer, pose: PoseStack.Pose, x: Float, y: Float, z: Float, u: Float, v: Float, light: Int, color: Int) {
        Vertices.add(buffer, pose, x, y, z).setUv(u, v).setLight(light).setColor(color)
    }

    private class PoolKey(var hash: String, var textures: List<Identifier>) {
        override fun equals(other: Any?): Boolean = other is PoolKey && other.hash == hash && other.textures == textures

        override fun hashCode(): Int = HASH_PRIME * hash.hashCode() + textures.hashCode()

        private companion object {
            const val HASH_PRIME = 31
        }
    }

    private class Slot(val renderType: NameplateLayer, val depthType: NameplateLayer?, val binding: NameplateMaterialUniforms.Binding)

    private class Pool(private val entry: NameplateMaterialPipelines.Entry, private val textures: List<Identifier>, val index: Int) {
        private val slots = ArrayList<Slot>()
        //? if >= 26.3 {
        val blocks: Int = entry.blocks
        //?} else {
        /*val blocks: Int = 1
        *///?}
        private var used = 0
        private var draws = 0

        fun next(batched: Boolean): Int {
            if (draws >= MAX_DRAWS_PER_FRAME) {
                return -1
            }
            if (!batched) {
                used = (used + blocks - 1) / blocks * blocks
            }
            val at = used
            used = if (batched) at + 1 else at + blocks
            draws++
            while (slots.size <= at / blocks) {
                slots += create(slots.size)
            }
            return at
        }

        fun slot(at: Int): Slot = slots[at / blocks]

        fun reset() {
            used = 0
            draws = 0
        }

        private fun create(slot: Int): Slot {
            val program = entry.program
            val name = "${prefix(program.hash)}$index/$slot"
            //? if >= 26.3 {
            val binding = NameplateMaterialUniforms.binding(name, blocks, program.uniformBytes)
            //?} else {
            /*val binding = NameplateMaterialUniforms.binding(name)
            *///?}
            //? if >= 26.3 {
            val setup = RenderSetup.builder(entry.pipeline).setOitPipelines(entry.oit).useLightmap()
            //?} elif >= 26.2 {
            /*val setup = RenderSetup.builder(entry.pipeline).useLightmap()
            *///?}
            //? if >= 26.2 {
            withTextures(setup, program)
            val depthType = entry.depthPipeline?.let { pipeline ->
                val depthName = "$name/depth"
                val depthSetup = RenderSetup.builder(pipeline).useLightmap()
                withTextures(depthSetup, program)
                NameplateMaterialUniforms.share(depthName, binding)
                RenderTypeInvoker.`polyplus$create`(depthName, depthSetup.createRenderSetup())
            }
            return Slot(RenderTypeInvoker.`polyplus$create`(name, setup.createRenderSetup()), depthType, binding)
            //?} else {
            /*val depthType = entry.depthPipeline?.let {
                val depthName = "$name/depth"
                NameplateMaterialUniforms.share(depthName, binding)
                NameplateMaterialPipelines.renderType(entry, depthName, true, textures)
            }
            return Slot(NameplateMaterialPipelines.renderType(entry, name, false, textures), depthType, binding)
            *///?}
        }

        //? if >= 26.2 {
        private fun withTextures(setup: RenderSetup.RenderSetupBuilder, program: NameplateShaderProgram) {
            for ((i, texture) in textures.withIndex()) {
                setup.withTexture(program.samplers[i], texture)
            }
        }
        //?}
    }
}
//?}
