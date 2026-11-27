package org.polyfrost.polyplus.client.cosmetics.render

import org.joml.Matrix4f
import org.joml.Matrix4fc
//? if iris {
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.compat.iris.IrisShadowView
//?}

object CosmeticView {
    private val inverse = Matrix4f()
    private val unbased = Matrix4f()
    private val unshadowed = Matrix4f()
    private val shadowInverse = Matrix4f()

    //? if iris {
    private val iris: Boolean by lazy { FabricLoader.getInstance().isModLoaded("iris") }
    private var shadowViewBroken = false
    //?}

    var rebased = false
        private set

    @JvmStatic
    fun enterEntity(base: Matrix4f) {
        rebased = (base.properties() and Matrix4fc.PROPERTY_IDENTITY.toInt()) == 0
        if (rebased) base.invert(inverse)
    }

    @JvmStatic
    fun exitEntity() {
        rebased = false
    }

    fun layerPose(pose: Matrix4f): Matrix4f = if (rebased) inverse.mul(pose, unbased) else unshadow(pose)

    fun unshadow(pose: Matrix4f): Matrix4f {
        val view = shadowView() ?: return pose
        return view.invert(shadowInverse).mul(pose, unshadowed)
    }

    private fun shadowView(): Matrix4f? {
        //? if iris {
        if (!iris || shadowViewBroken) return null
        return try {
            IrisShadowView.modelView()
        } catch (e: LinkageError) {
            shadowViewBroken = true
            null
        }
        //?} else {
        /*return null
        *///?}
    }
}
