package org.polyfrost.polyplus.client.emotes.playback

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel

//? if = 1.21.1 {
/*import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
*///?}

object EmotePoser {
    //? if = 1.21.1 {
    /*private val hatBefore = Matrix4f()
    private val headPose = Matrix4f()
    private val hatPose = Matrix4f()
    private val decomposed = PartTransform()
    *///?}

    internal fun feed(model: PlayerModel, scene: EmoteScene) {
        feed(scene, EmoteScene.HEAD, model.head)
        feed(scene, EmoteScene.HAT, model.hat)
        feed(scene, EmoteScene.BODY, model.body)
        feed(scene, EmoteScene.RIGHT_ARM, model.rightArm)
        feed(scene, EmoteScene.LEFT_ARM, model.leftArm)
        feed(scene, EmoteScene.RIGHT_LEG, model.rightLeg)
        feed(scene, EmoteScene.LEFT_LEG, model.leftLeg)
        feed(scene, EmoteScene.ROOT, root(model))
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun apply(model: PlayerModel, scene: EmoteScene, allowsVanillaPose: Boolean) {
        apply(scene, EmoteScene.HEAD, model.head)
        apply(scene, EmoteScene.BODY, model.body)
        apply(scene, EmoteScene.RIGHT_ARM, model.rightArm)
        apply(scene, EmoteScene.LEFT_ARM, model.leftArm)
        apply(scene, EmoteScene.RIGHT_LEG, model.rightLeg)
        apply(scene, EmoteScene.LEFT_LEG, model.leftLeg)
        apply(scene, EmoteScene.ROOT, root(model))
        //? if >= 1.21.4 {
        apply(scene, EmoteScene.HAT, model.hat)
        //?} else {
        /*followHead(model, scene, allowsVanillaPose)
        model.jacket.copyFrom(model.body)
        model.rightSleeve.copyFrom(model.rightArm)
        model.leftSleeve.copyFrom(model.leftArm)
        model.rightPants.copyFrom(model.rightLeg)
        model.leftPants.copyFrom(model.leftLeg)
        *///?}
    }

    //? if >= 1.21.4 {
    private fun root(model: PlayerModel): ModelPart = model.root()
    //?} else {
    /*private fun root(model: PlayerModel): ModelPart = (model as PlayerModelRootAccess).`polyplus$root`()
    *///?}

    private fun feed(scene: EmoteScene, index: Int, part: ModelPart) {
        val rest = part.initialPose
        scene.part(
            index, rest.x, rest.y, rest.z,
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale,
        )
    }

    private fun apply(scene: EmoteScene, index: Int, part: ModelPart) {
        if (scene.weight(index) <= 0f) return
        part.x += scene.delta(index, EmoteScene.OFFSET_X)
        part.y += scene.delta(index, EmoteScene.OFFSET_Y)
        part.z += scene.delta(index, EmoteScene.OFFSET_Z)
        part.xRot += scene.delta(index, EmoteScene.ROT_X)
        part.yRot += scene.delta(index, EmoteScene.ROT_Y)
        part.zRot += scene.delta(index, EmoteScene.ROT_Z)
        part.xScale *= scene.delta(index, EmoteScene.SCALE_X)
        part.yScale *= scene.delta(index, EmoteScene.SCALE_Y)
        part.zScale *= scene.delta(index, EmoteScene.SCALE_Z)
    }

    //? if = 1.21.1 {
    /*private fun followHead(model: PlayerModel, scene: EmoteScene, allowsVanillaPose: Boolean) {
        val hat = model.hat
        if (scene.weight(EmoteScene.HAT) <= 0f) {
            hat.copyFrom(model.head)
            return
        }
        if (allowsVanillaPose) {
            hat.toMatrix(hatBefore)
        } else {
            val rest = hat.initialPose
            PartTransform.compose(hatBefore, rest.x, rest.y, rest.z, 0f, 0f, 0f)
        }
        apply(scene, EmoteScene.HAT, hat)
        model.head.toMatrix(headPose).mul(hatBefore.invert()).mul(hat.toMatrix(hatPose))
        hat.setPose(headPose)
    }

    private fun ModelPart.toMatrix(dest: Matrix4f): Matrix4f =
        PartTransform.compose(dest, x, y, z, xRot, yRot, zRot, xScale, yScale, zScale)

    private fun ModelPart.setPose(matrix: Matrix4f) {
        val pose = decomposed.decompose(matrix)
        x = pose.x
        y = pose.y
        z = pose.z
        xRot = pose.xRot
        yRot = pose.yRot
        zRot = pose.zRot
        xScale = pose.xScale
        yScale = pose.yScale
        zScale = pose.zScale
    }
    *///?}
}
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.model.Model
import net.minecraft.client.render.model.ModelPart
import net.minecraft.client.render.model.entity.HumanoidModel
import net.minecraft.entity.Entity
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel

object EmotePoser {
    @JvmField
    var firstPersonHand = false

    private var posedModel: PlayerModel? = null
    private var posedEntity: Entity? = null

    private val rest = FloatArray(3)
    private val hatBefore = Matrix4f()
    private val headPose = Matrix4f()
    private val hatPose = Matrix4f()
    private val decomposed = PartTransform()

    @JvmStatic
    fun afterSetupAnimation(model: HumanoidModel, entity: Entity?, walkAnimationSpeed: Float, bob: Float) {
        if (model is PlayerModel) {
            posedModel = null
            posedEntity = null
            if (firstPersonHand) return
            val player = entity as? ClientPlayerEntity ?: return
            val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`()
            if (!controller.isActive) return
            controller.applyToModel(model, PlayerRenderContext.from(player, 0f, walkAnimationSpeed, bob))
            (model as PlayerModelRootAccess).`polyplus$markPosed`()
            posedModel = model
            posedEntity = player
        } else if (entity != null && entity === posedEntity) {
            val source = posedModel ?: return
            copyPose(source.head, model.head)
            copyPose(source.hat, model.hat)
            copyPose(source.body, model.body)
            copyPose(source.rightArm, model.rightArm)
            copyPose(source.leftArm, model.leftArm)
            copyPose(source.rightLeg, model.rightLeg)
            copyPose(source.leftLeg, model.leftLeg)
            (model as PlayerModelRootAccess).`polyplus$markPosed`()
        }
    }

    private fun copyPose(from: ModelPart, to: ModelPart) {
        Model.copyRotation(from, to)
        to.copyScale(from)
    }

    internal fun feed(model: PlayerModel, scene: EmoteScene) {
        val access = model as PlayerModelRootAccess
        feed(scene, access, EmoteScene.HEAD, model.head)
        feed(scene, access, EmoteScene.HAT, model.hat)
        feed(scene, access, EmoteScene.BODY, model.body)
        feed(scene, access, EmoteScene.RIGHT_ARM, model.rightArm)
        feed(scene, access, EmoteScene.LEFT_ARM, model.leftArm)
        feed(scene, access, EmoteScene.RIGHT_LEG, model.rightLeg)
        feed(scene, access, EmoteScene.LEFT_LEG, model.leftLeg)
        feed(scene, access, EmoteScene.ROOT, access.`polyplus$root`())
    }

    internal fun apply(model: PlayerModel, scene: EmoteScene, allowsVanillaPose: Boolean) {
        val access = model as PlayerModelRootAccess
        apply(scene, EmoteScene.HEAD, model.head)
        apply(scene, EmoteScene.BODY, model.body)
        apply(scene, EmoteScene.RIGHT_ARM, model.rightArm)
        apply(scene, EmoteScene.LEFT_ARM, model.leftArm)
        apply(scene, EmoteScene.RIGHT_LEG, model.rightLeg)
        apply(scene, EmoteScene.LEFT_LEG, model.leftLeg)
        apply(scene, EmoteScene.ROOT, access.`polyplus$root`())
        followHead(model, scene, access, allowsVanillaPose)
    }

    private fun feed(scene: EmoteScene, access: PlayerModelRootAccess, index: Int, part: ModelPart) {
        access.`polyplus$copyRest`(part, rest)
        scene.part(
            index, rest[0], rest[1], rest[2],
            part.x, part.y, part.z, part.rotationX, part.rotationY, part.rotationZ,
            part.scale.`polyplus$scaleX`(), part.scale.`polyplus$scaleY`(), part.scale.`polyplus$scaleZ`(),
        )
    }

    private fun apply(scene: EmoteScene, index: Int, part: ModelPart) {
        if (scene.weight(index) <= 0f) return
        part.x += scene.delta(index, EmoteScene.OFFSET_X)
        part.y += scene.delta(index, EmoteScene.OFFSET_Y)
        part.z += scene.delta(index, EmoteScene.OFFSET_Z)
        part.rotationX += scene.delta(index, EmoteScene.ROT_X)
        part.rotationY += scene.delta(index, EmoteScene.ROT_Y)
        part.rotationZ += scene.delta(index, EmoteScene.ROT_Z)
        val scale = part.scale
        scale.`polyplus$setScale`(
            scale.`polyplus$scaleX`() * scene.delta(index, EmoteScene.SCALE_X),
            scale.`polyplus$scaleY`() * scene.delta(index, EmoteScene.SCALE_Y),
            scale.`polyplus$scaleZ`() * scene.delta(index, EmoteScene.SCALE_Z),
        )
    }

    private fun followHead(model: PlayerModel, scene: EmoteScene, access: PlayerModelRootAccess, allowsVanillaPose: Boolean) {
        val hat = model.hat
        if (scene.weight(EmoteScene.HAT) <= 0f) {
            Model.copyRotation(model.head, hat)
            hat.copyScale(model.head)
            return
        }
        if (allowsVanillaPose) {
            hat.toMatrix(hatBefore)
        } else {
            access.`polyplus$copyRest`(hat, rest)
            PartTransform.compose(hatBefore, rest[0], rest[1], rest[2], 0f, 0f, 0f)
        }
        apply(scene, EmoteScene.HAT, hat)
        model.head.toMatrix(headPose).mul(hatBefore.invert()).mul(hat.toMatrix(hatPose))
        hat.setPose(headPose)
    }

    private fun ModelPart.toMatrix(dest: Matrix4f): Matrix4f =
        PartTransform.compose(
            dest, x, y, z, rotationX, rotationY, rotationZ,
            scale.`polyplus$scaleX`(), scale.`polyplus$scaleY`(), scale.`polyplus$scaleZ`(),
        )

    private val ModelPart.scale: ModelPartScaleAccess
        get() = this as ModelPartScaleAccess

    private fun ModelPart.copyScale(from: ModelPart) {
        val source = from.scale
        scale.`polyplus$setScale`(source.`polyplus$scaleX`(), source.`polyplus$scaleY`(), source.`polyplus$scaleZ`())
    }

    private fun ModelPart.setPose(matrix: Matrix4f) {
        val pose = decomposed.decompose(matrix)
        x = pose.x
        y = pose.y
        z = pose.z
        rotationX = pose.xRot
        rotationY = pose.yRot
        rotationZ = pose.zRot
        scale.`polyplus$setScale`(pose.xScale, pose.yScale, pose.zScale)
    }
}
*///?}
