package org.polyfrost.polyplus.client.lobby

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.Emote
import java.io.ByteArrayInputStream
//? if >= 26.1 {
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import org.joml.Vector3f
import org.polyfrost.polyplus.client.utils.rotateBy
//?}

/**
 * Looping poses your own player can hold while standing in the lobby (picked from its right-click menu).
 * Each one is a tiny inline Bedrock animation; Molang keeps the moving ones going.
 */
object LobbyPoses {
    const val NONE = "none"
    const val LIE = "lie"
    private const val NO_ID = Int.MIN_VALUE
    private const val LIE_LIFT = 0.13f
    private const val LIE_HALF_LENGTH = 0.9f
    private const val PATH = "lobby/pose_"
    private const val MODEL_SCALE = 0.9375f
    private const val MODEL_DROP = 1.501f
    private const val PIXELS = 16f
    const val POINTS = 8

    class Pose(val id: String, val name: String, val bones: String, val length: Double = 2.0)

    val all: List<Pose> = listOf(
        Pose(NONE, "Normal", ""),
        Pose(
            "wave", "Wave",
            """
            "rightArm": {"rotation": [-165, 0, "18 + math.sin(q.life_time * 540) * 28"]},
            "head": {"rotation": [-6, 0, 0]}
            """,
        ),
        Pose(
            "superman", "Superman",
            """
            "root": {"rotation": [78, 0, "math.sin(q.life_time * 90) * 4"], "position": [0, "14 + math.sin(q.life_time * 120) * 1.5", 0]},
            "head": {"rotation": [-62, 0, 0]},
            "rightArm": {"rotation": [-172, 0, 6]},
            "leftArm": {"rotation": [8, 0, -6]},
            "rightLeg": {"rotation": ["4 + math.sin(q.life_time * 240) * 5", 0, 3]},
            "leftLeg": {"rotation": ["4 - math.sin(q.life_time * 240) * 5", 0, -3]}
            """,
        ),
        Pose(
            "fly", "Flying",
            """
            "root": {"rotation": [62, 0, 0], "position": [0, "12 + math.sin(q.life_time * 120) * 2", 0]},
            "head": {"rotation": [-50, 0, 0]},
            "rightArm": {"rotation": [0, 0, "75 + math.sin(q.life_time * 400) * 18"]},
            "leftArm": {"rotation": [0, 0, "-75 - math.sin(q.life_time * 400) * 18"]},
            "rightLeg": {"rotation": ["math.sin(q.life_time * 300) * 10", 0, 4]},
            "leftLeg": {"rotation": ["-math.sin(q.life_time * 300) * 10", 0, -4]}
            """,
        ),
        Pose(
            "float", "Meditate",
            """
            "root": {"position": [0, "8 + math.sin(q.life_time * 120) * 1.5", 0]},
            "rightLeg": {"rotation": [-80, 28, 0]},
            "leftLeg": {"rotation": [-80, -28, 0]},
            "rightArm": {"rotation": [-28, 0, 18]},
            "leftArm": {"rotation": [-28, 0, -18]},
            "head": {"rotation": [8, 0, 0]}
            """,
        ),
        Pose(
            "sit", "Sit",
            """
            "root": {"position": [0, -12, 0]},
            "rightLeg": {"rotation": [-90, 8, 0]},
            "leftLeg": {"rotation": [-90, -8, 0]},
            "rightArm": {"rotation": [-34, 0, 6]},
            "leftArm": {"rotation": [-34, 0, -6]}
            """,
        ),
        Pose(
            "tpose", "T-pose",
            """
            "rightArm": {"rotation": [0, 0, 90]},
            "leftArm": {"rotation": [0, 0, -90]}
            """,
        ),
        Pose(
            "salute", "Salute",
            """
            "rightArm": {"rotation": [-125, -32, 18]},
            "head": {"rotation": [-4, 0, 0]}
            """,
        ),
        Pose(
            "crossed", "Arms crossed",
            """
            "rightArm": {"rotation": [-58, -38, 0]},
            "leftArm": {"rotation": [-52, 38, 0]},
            "head": {"rotation": [-4, 0, 0]}
            """,
        ),
        Pose(
            "dab", "Dab",
            """
            "head": {"rotation": [34, 32, 0]},
            "rightArm": {"rotation": [-140, 0, 62]},
            "leftArm": {"rotation": [-100, -58, 0]}
            """,
        ),
        Pose(
            "dance", "Dance",
            """
            "root": {"rotation": [0, "math.sin(q.life_time * 180) * 18", 0], "position": [0, "math.abs(math.sin(q.life_time * 360)) * 1.5", 0]},
            "head": {"rotation": [0, "math.sin(q.life_time * 180) * -14", "math.sin(q.life_time * 360) * 6"]},
            "rightArm": {"rotation": ["-90 + math.sin(q.life_time * 360) * 60", 0, 22]},
            "leftArm": {"rotation": ["-90 - math.sin(q.life_time * 360) * 60", 0, -22]},
            "rightLeg": {"rotation": ["math.sin(q.life_time * 360) * 22", 0, 4]},
            "leftLeg": {"rotation": ["-math.sin(q.life_time * 360) * 22", 0, -4]}
            """,
        ),
        Pose(
            "cheer", "Cheer",
            """
            "root": {"position": [0, "math.abs(math.sin(q.life_time * 300)) * 2", 0]},
            "rightArm": {"rotation": [-165, 0, "22 + math.sin(q.life_time * 720) * 12"]},
            "leftArm": {"rotation": [-165, 0, "-22 - math.sin(q.life_time * 720) * 12"]},
            "head": {"rotation": [-12, 0, 0]}
            """,
        ),
        Pose(
            "zombie", "Zombie",
            """
            "rightArm": {"rotation": [-90, 0, "math.sin(q.life_time * 150) * 4"]},
            "leftArm": {"rotation": [-90, 0, "-math.sin(q.life_time * 150) * 4"]},
            "head": {"rotation": [8, 0, "math.sin(q.life_time * 90) * 8"]}
            """,
        ),
        Pose(
            "think", "Think",
            """
            "rightArm": {"rotation": [-118, -42, 0]},
            "leftArm": {"rotation": [-40, 36, 0]},
            "head": {"rotation": [6, 0, 8]}
            """,
        ),
        Pose(
            "spin", "Spin",
            """
            "root": {"rotation": [0, "q.life_time * 240", 0]},
            "rightArm": {"rotation": [0, 0, 80]},
            "leftArm": {"rotation": [0, 0, -80]}
            """,
        ),
        Pose(
            "lie", "Lie down",
            """
            "rightArm": {"rotation": [-170, 0, 18]},
            "leftArm": {"rotation": [-170, 0, -18]}
            """,
        ),
    )

    /** A pose shown while you hover it in the menu, before you pick it. */
    var preview: String? = null

    /** Whose character [preview] shows on: a player's uuid, or null for your own. */
    var previewTarget: String? = null

    private val built = HashMap<String, Emote?>()
    private val lyingIds = HashSet<Int>()

    /** Where the posed model's key points are, per entity id: xyz triples in blocks, relative to the feet. */
    private val shapes = HashMap<Int, FloatArray>()

    /** True while this entity is shown lying down (the whole model is laid flat in [lie], not just its bones). */
    fun lying(entityId: Int): Boolean = entityId in lyingIds

    fun current(): String = if (previewTarget == null) preview ?: LobbySceneStore.pose() else LobbySceneStore.pose()

    /** The pose a lobby character holds: yours from [current], other players' from their own saved pick. */
    fun poseFor(avatar: LobbyAvatar): String {
        if (avatar.isLocal) return current()
        val uuid = avatar.uuid.toString()
        val hovered = preview
        if (hovered != null && previewTarget == uuid) return hovered
        return LobbySceneStore.poseOf(uuid)
    }

    fun name(id: String): String = all.firstOrNull { it.id == id }?.name ?: "Normal"

    /** The posed model's key points for this entity (xyz triples, blocks from the feet), or null when it isn't posed. */
    fun posed(entityId: Int): FloatArray? = shapes[entityId]

    @JvmStatic
    fun clearShape(entityId: Int) {
        shapes.remove(entityId)
    }

    private fun ours(emote: Emote?): Boolean = emote != null && emote.id.namespace == "polyplus" && emote.id.path.startsWith(PATH)

    fun tick(avatars: List<LobbyAvatar>) {
        lyingIds.clear()
        for (avatar in avatars) tickOne(avatar)
    }

    private fun tickOne(avatar: LobbyAvatar) {
        val controller = (avatar as PlayerEmotesAccess).`polyplus$emoteController`()
        val playing = controller.playbackSnapshot()?.emote
        val id = poseFor(avatar)
        val free = avatar.arrival == LobbyAvatar.Arrival.LANDED && !avatar.dismissed && !avatar.isCrouching && !LobbyGrab.handling(avatar)
        if (id == LIE && free && (playing == null || ours(playing))) lyingIds.add(avatar.id)
        if (id == NONE || !free) {
            if (ours(playing)) controller.stop()
            return
        }
        if (playing != null && !ours(playing)) return
        val emote = emote(id) ?: return
        if (playing?.id != emote.id) controller.play(emote)
    }

    //? if >= 26.1 {
    /**
     * Lays the whole model on its back, centred on the entity so the shadow, cape, cosmetics and
     * name tag all stay with the body. Runs before setupRotations turns the body by (180 - bodyRot),
     * so the extra turn here is undone by that one and the tilt happens in the body's own frame.
     */
    /**
     * Records where the posed model's head, body, hands and feet end up, so the name tag and the
     * right-click area can follow the animation. Mirrors the player renderer: turn by (180 - bodyRot),
     * flip and scale by 0.9375, drop by 1.501, then each part's own pivot and rotation.
     */
    @JvmStatic
    fun shape(entityId: Int, model: PlayerModel, bodyRot: Float) {
        val stack = PoseStack()
        stack.rotateBy(Quaternionf().rotationY(Math.toRadians(180.0 - bodyRot).toFloat()))
        stack.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE)
        stack.translate(0f, -MODEL_DROP, 0f)
        val out = shapes[entityId] ?: FloatArray(POINTS * 3).also { shapes[entityId] = it }
        var at = 0
        fun point(part: ModelPart, x: Float, y: Float) {
            stack.pushPose()
            model.root().translateAndRotate(stack)
            part.translateAndRotate(stack)
            val v = stack.last().pose().transformPosition(Vector3f(x / PIXELS, y / PIXELS, 0f))
            stack.popPose()
            out[at++] = v.x
            out[at++] = v.y
            out[at++] = v.z
        }
        point(model.head, 0f, -8f)
        point(model.head, 0f, -4f)
        point(model.body, 0f, 0f)
        point(model.body, 0f, 12f)
        point(model.rightArm, -1f, 10f)
        point(model.leftArm, 1f, 10f)
        point(model.rightLeg, 0f, 12f)
        point(model.leftLeg, 0f, 12f)
    }

    @JvmStatic
    fun lie(entityId: Int, poseStack: PoseStack, bodyRot: Float) {
        if (entityId !in lyingIds) return
        val turn = Math.toRadians(180.0 - bodyRot).toFloat()
        poseStack.rotateBy(Quaternionf().rotationY(turn))
        poseStack.translate(0f, LIE_LIFT, -LIE_HALF_LENGTH)
        poseStack.rotateBy(Quaternionf().rotationX((Math.PI / 2).toFloat()))
        poseStack.rotateBy(Quaternionf().rotationY(-turn))
    }
    //?}

    private fun emote(id: String): Emote? {
        if (built.containsKey(id)) return built[id]
        val pose = all.firstOrNull { it.id == id }
        val made = pose?.let {
            val name = "animation.lobby.pose_$id"
            val json = """{"format_version": "1.8.0", "animations": {"$name": {"loop": true, "animation_length": ${it.length}, "bones": {${it.bones}}}}}"""
            runCatching {
                val animation = ByteArrayInputStream(json.toByteArray()).use(BedrockAnimationParser::parseStream).animations.getValue(name)
                Emote(Identifier.fromNamespaceAndPath("polyplus", PATH + id), animation)
            }.getOrNull()
        }
        built[id] = made
        return made
    }
}
