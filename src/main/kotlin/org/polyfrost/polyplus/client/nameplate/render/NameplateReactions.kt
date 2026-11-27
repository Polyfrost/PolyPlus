package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import net.minecraft.world.entity.player.Player
//?} else {
/*import net.minecraft.entity.living.player.PlayerEntity
*///?}
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.NameplateTryOn
import org.polyfrost.polyplus.nameplate.engine.api.anim.TagRecord
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import java.util.UUID

object NameplateReactions {
    private val record = TagRecord<UUID>()
    private val inputs = arrayOfNulls<TagInputs>(1 shl Reaction.entries.size)
    private val held = arrayOfNulls<TagState>(1 shl Reaction.entries.size)

    //? if >= 1.21.1 {
    fun state(player: Player, nameplate: BoundNameplate, frame: Int): TagState = state(player.uuid, nameplate, frame, mask(player))

    private fun mask(player: Player): Int {
        var mask = 0
        if (player.isCrouching) {
            mask = mask or Reaction.SNEAKING.bit
        }
        if (player.isSprinting) {
            mask = mask or Reaction.SPRINTING.bit
        }
        if (player.hurtTime > 0) {
            mask = mask or Reaction.HURT.bit
        }
        if (player.displayFireAnimation()) {
            mask = mask or Reaction.ON_FIRE.bit
        }
        if (player.isSwimming) {
            mask = mask or Reaction.SWIMMING.bit
        }
        if (player.isFallFlying) {
            mask = mask or Reaction.GLIDING.bit
        }
        if (player.isPassenger) {
            mask = mask or Reaction.RIDING.bit
        }
        if (player.isSleeping) {
            mask = mask or Reaction.SLEEPING.bit
        }
        if ((player as? PlayerEmotesAccess)?.`polyplus$emoteController`()?.isActive == true) {
            mask = mask or Reaction.EMOTE.bit
        }
        return mask
    }
    //?} else {
    /*fun state(player: PlayerEntity, nameplate: BoundNameplate, frame: Int): TagState = state(player.uuid, nameplate, frame, mask(player))

    private fun mask(player: PlayerEntity): Int {
        var mask = 0
        if (player.isSneaking) {
            mask = mask or Reaction.SNEAKING.bit
        }
        if (player.isSprinting) {
            mask = mask or Reaction.SPRINTING.bit
        }
        if (player.damagedTimer > 0) {
            mask = mask or Reaction.HURT.bit
        }
        if (player.isOnFire) {
            mask = mask or Reaction.ON_FIRE.bit
        }
        if (player.isRiding) {
            mask = mask or Reaction.RIDING.bit
        }
        if (player.isSleeping) {
            mask = mask or Reaction.SLEEPING.bit
        }
        if ((player as? PlayerEmotesAccess)?.`polyplus$emoteController`()?.isActive == true) {
            mask = mask or Reaction.EMOTE.bit
        }
        return mask
    }
    *///?}

    private fun state(uuid: UUID, nameplate: BoundNameplate, frame: Int, playerMask: Int): TagState {
        val motion = NameplateBinding.settings.motion
        val mask = playerMask or NameplateTryOn.reactionMask(uuid)
        if (motion == NameplateMotion.OFF) {
            NameplateTryOn.drainShots(uuid)
            return held(inputsOf(mask))
        }

        val state = record.update(uuid, nameplate.bundle, nameplate.variant, frame, inputsOf(mask), nameplate.seed)
        if (motion != NameplateMotion.FULL) {
            NameplateTryOn.drainShots(uuid)
            return held(state.inputs)
        }
        val shots = NameplateTryOn.drainShots(uuid)
        if (shots.isEmpty()) return state
        shots.forEach { record.fire(uuid, nameplate.bundle, it, frame) }
        return record.state(uuid)
    }

    private fun inputsOf(mask: Int): TagInputs = inputs[mask] ?: TagInputs.ofMask(mask).also { inputs[mask] = it }

    private fun held(inputs: TagInputs): TagState {
        val mask = inputs.mask
        return held[mask] ?: TagState.of(inputs, reduced = true).also { held[mask] = it }
    }
}
