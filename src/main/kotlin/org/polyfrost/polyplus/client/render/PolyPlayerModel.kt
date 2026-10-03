package org.polyfrost.polyplus.client.render

//? if > 1.8.9 {
//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel
//?}

//? if < 1.21.11 {
/*import net.minecraft.client.model.PlayerModel
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if >= 1.21.11 {
typealias PolyPlayerModel = PlayerModel
//?} elif >= 1.21.4 {
/*typealias PolyPlayerModel = PlayerModel
*///?} else {
/*typealias PolyPlayerModel = PlayerModel<AbstractClientPlayer>
*///?}
//?}

//? if = 1.8.9 {
/*typealias PolyPlayerModel = net.minecraft.client.render.model.entity.PlayerModel

fun net.minecraft.client.render.model.ModelPart.translateAndRotate(poseStack: PoseStack) {
    poseStack.translate(translateX, translateY, translateZ)
    poseStack.translate(x / 16f, y / 16f, z / 16f)
    if (rotationX != 0f || rotationY != 0f || rotationZ != 0f) {
        poseStack.mulPose(org.joml.Quaternionf().rotationZYX(rotationZ, rotationY, rotationX))
    }
}
*///?}
