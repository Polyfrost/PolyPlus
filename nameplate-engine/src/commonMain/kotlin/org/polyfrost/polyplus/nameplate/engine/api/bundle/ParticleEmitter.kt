package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fade
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Spawn
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Spread
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Wobble
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite

internal class ParticleEmitter(
    val pool: List<List<PixelSprite>>,
    val of: String?,
    val spawn: Spawn,
    val every: Int,
    val count: Int,
    val jitter: Int,
    val life: Spread,
    val velocityX: Spread,
    val velocityY: Spread,
    val gravityX: Int,
    val gravityY: Int,
    val driftX: Int,
    val driftY: Int,
    val wobble: Wobble?,
    val stretch: Boolean,
    val fade: Fade? = null,
) : NameplateBundle.LayerContent {
    val boxWidth: Int = pool.maxOfOrNull { frames -> frames.maxOfOrNull { it.width } ?: 0 } ?: 0

    val boxHeight: Int = pool.maxOfOrNull { frames -> frames.maxOfOrNull { it.height } ?: 0 } ?: 0

    fun spriteOf(state: Int): PixelSprite =
        pool[state / SpriteAdaptive.STATE_STRIDE][state % SpriteAdaptive.STATE_STRIDE]

    fun states(): List<Int> = pool.flatMapIndexed { sprite, frames ->
        frames.indices.map { sprite * SpriteAdaptive.STATE_STRIDE + it }
    }
}
