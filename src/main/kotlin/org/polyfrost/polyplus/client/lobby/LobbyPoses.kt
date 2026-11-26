package org.polyfrost.polyplus.client.lobby

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.Emote
import java.io.ByteArrayInputStream
//? if >= 26.1 {
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf
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

    private val built = HashMap<String, Emote?>()
    private var lyingId = NO_ID

    /** True while this entity is shown lying down (the whole model is laid flat in [lie], not just its bones). */
    fun lying(entityId: Int): Boolean = entityId == lyingId

    fun current(): String = preview ?: LobbySceneStore.pose()

    fun name(id: String): String = all.firstOrNull { it.id == id }?.name ?: "Normal"

    private fun ours(emote: Emote?): Boolean = emote != null && emote.id.namespace == "polyplus" && emote.id.path.startsWith(PATH)

    fun tick(avatar: LobbyAvatar?) {
        lyingId = NO_ID
        avatar ?: return
        val controller = (avatar as PlayerEmotesAccess).`polyplus$emoteController`()
        val playing = controller.playbackSnapshot()?.emote
        val id = current()
        val free = avatar.arrival == LobbyAvatar.Arrival.LANDED && !avatar.dismissed && !avatar.isCrouching && !LobbyGrab.handling(avatar)
        if (id == LIE && free && (playing == null || ours(playing))) lyingId = avatar.id
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
    @JvmStatic
    fun lie(entityId: Int, poseStack: PoseStack, bodyRot: Float) {
        if (entityId != lyingId) return
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
