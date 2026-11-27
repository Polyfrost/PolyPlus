package org.polyfrost.polyplus.client.pets

import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals

internal object PetPreviewVariables {
    fun fill(variables: FloatArray, archetype: PetArchetype, signals: PlayerSignalRecord) {
        variables.fill(0f)
        val flying = archetype == PetArchetype.Flying
        val shoulder = archetype == PetArchetype.Shoulder
        val moving = signals.flag(PlayerSignals.IS_MOVING)
        val inWater = !flying && !shoulder && signals.flag(PlayerSignals.IS_IN_WATER)
        variables[PetVariables.MODE] = when {
            signals.flag(PlayerSignals.IS_SLEEPING) -> PetVariables.SLEEPING
            signals.flag(PlayerSignals.IS_RIDING) && (shoulder || !moving) -> PetVariables.RIDING
            signals.flag(PlayerSignals.IS_SNEAKING) && !moving && !shoulder && !flying -> PetVariables.SITTING
            moving -> PetVariables.FOLLOWING
            else -> PetVariables.IDLE
        }
        variables[PetVariables.SPEED] = if (moving) signals[PlayerSignals.GROUND_SPEED] else 0f
        variables[PetVariables.ON_GROUND] = if (flying || inWater) 0f else 1f
        variables[PetVariables.IN_WATER] = if (inWater) 1f else 0f
    }
}
