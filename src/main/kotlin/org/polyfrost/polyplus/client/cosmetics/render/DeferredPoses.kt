package org.polyfrost.polyplus.client.cosmetics.render

//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector

interface PooledGeometry : SubmitNodeCollector.CustomGeometryRenderer

object DeferredPoses {
    private val pool = ArrayList<PoseStack.Pose>(32)
    private var used = 0

    @JvmStatic
    fun copy(source: PoseStack.Pose): PoseStack.Pose {
        if (used == pool.size) pool.add(PoseStack.Pose())
        val pose = pool[used++]
        pose.set(source)
        return pose
    }

    @JvmStatic
    fun reset() {
        used = 0
    }
}
//?}
