package org.polyfrost.polyplus.client.nameplate.render

import net.minecraft.resources.Identifier
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.RenderType
*///?}
//? if > 1.8.9 {
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
//?}

//? if > 1.8.9 {
typealias NameplatePose = PoseStack.Pose

typealias NameplateVertexSink = VertexConsumer

typealias NameplateLayer = RenderType

const val Z_TOWARD_CAMERA: Float = 1f
//?} else {
/*class NameplatePose

interface NameplateVertexSink {
    fun addVertex(x: Float, y: Float, z: Float): NameplateVertexSink

    fun setUv(u: Float, v: Float): NameplateVertexSink

    fun setLight(light: Int): NameplateVertexSink

    fun setColor(argb: Int): NameplateVertexSink
}

typealias NameplateLayer = Identifier

const val Z_TOWARD_CAMERA: Float = -1f
*///?}

//? if >= 1.21.10 {
typealias NameplateGeometry = SubmitNodeCollector.CustomGeometryRenderer

typealias NameplateTarget = SubmitNodeCollector
//?} else {
/*fun interface NameplateGeometry {
    fun render(pose: NameplatePose, buffer: NameplateVertexSink)
}
*///?}
//? if < 1.21.10 && > 1.8.9 {
/*typealias NameplateTarget = MultiBufferSource
*///?}

object NameplateLayers {
    fun text(texture: Identifier): NameplateLayer =
        //? if >= 1.21.11 {
        RenderTypes.text(texture)
        //?} elif > 1.8.9 {
        /*RenderType.text(texture)
        *///?} else {
        /*texture
        *///?}
}
