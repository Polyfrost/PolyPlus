package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FadeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.RateJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpawnJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Edge
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor

internal class EmitterValidator(
    private val path: String,
    private val emitter: EmitterLayerJson,
    private val layers: Map<String, LayerFileJson>,
    private val sprites: Map<String, SpriteFileJson>,
    private val report: IssueSink,
) {
    fun validate() {
        checkPool()
        checkOf()
        checkSpawn(emitter.spawn)
        emitter.rate?.let(::checkRate)
        checkLifetime(emitter.lifetime)
        emitter.fade?.let(::checkFade)
        checkRange("$path#velocity.x", emitter.velocity.x, AdaptiveLimits.MAX_PARTICLE_SPEED)
        checkRange("$path#velocity.y", emitter.velocity.y, AdaptiveLimits.MAX_PARTICLE_SPEED)
        checkPair("$path#gravity", emitter.gravity, AdaptiveLimits.MAX_GRAVITY)
        checkPair("$path#drift", emitter.drift, AdaptiveLimits.MAX_PARTICLE_SPEED)
        emitter.wobble?.let { wobble ->
            val amplitude = -AdaptiveLimits.MAX_WOBBLE..AdaptiveLimits.MAX_WOBBLE
            if (wobble.x !in amplitude || wobble.y !in amplitude) {
                report("$path#wobble", "x and y must be within ${AdaptiveLimits.MAX_WOBBLE} px")
            }
            if (wobble.period !in 1..AdaptiveLimits.MAX_LIFETIME) {
                report("$path#wobble.period", "must be 1 to ${AdaptiveLimits.MAX_LIFETIME} steps")
            }
        }
        if (emitter.frames != "loop" && emitter.frames != "stretch") {
            report("$path#frames", "must be loop or stretch")
        }
        emitter.breakpoints?.forEachIndexed { i, entry ->
            val max = entry.max
            if (entry.sprite != null) {
                report("$path#breakpoints[$i].sprite", "an emitter takes ranges only")
            }
            if (entry.min !in 0..AdaptiveLimits.MAX_WIDTH || (max != null && max !in entry.min + 1..AdaptiveLimits.MAX_WIDTH)) {
                report("$path#breakpoints[$i]", "needs 0 <= min < max <= ${AdaptiveLimits.MAX_WIDTH}")
            }
        }
        if (emitter.breakpoints?.let { it.isEmpty() || it.size > AdaptiveLimits.MAX_BREAKPOINTS } == true) {
            report("$path#breakpoints", "must hold 1 to ${AdaptiveLimits.MAX_BREAKPOINTS} ranges")
        }
    }

    private fun checkOf() {
        val of = emitter.of ?: return
        when (val target = layers[of]) {
            is BodyLayerJson -> Unit
            is SpriteLayerJson -> if (target.path != null || target.repeat != null) {
                report("$path#of", "must name a sprite with no path and no repeat")
            }
            else -> report("$path#of", "must name a body layer or a sprite layer")
        }
    }

    private fun checkFade(fade: FadeJson) {
        if (fade.after !in 0.0..<1.0) {
            report("$path#fade.after", "must be 0 or more and below 1")
        }
        if (fade.to !in 0.0..1.0) {
            report("$path#fade.to", "must be 0 to 1")
        }
        if (fade.steps !in 1..AdaptiveLimits.MAX_FADE_STEPS) {
            report("$path#fade.steps", "must be 1 to ${AdaptiveLimits.MAX_FADE_STEPS}")
        }
    }

    private fun checkPool() {
        if (emitter.sprites.isEmpty() || emitter.sprites.size > AdaptiveLimits.MAX_POOL) {
            report("$path#sprites", "must name 1 to ${AdaptiveLimits.MAX_POOL} sprites")
        }
        var frames = 0
        for ((i, id) in emitter.sprites.withIndex()) {
            val sprite = sprites[id]
            if (sprite == null) {
                report("$path#sprites[$i]", "no sprite '$id'")
            } else {
                frames += BundleFiles.framesOf(sprite)?.size ?: 0
            }
        }
        if (frames > BundleLimits.MAX_CACHED_STATES) {
            report.issue(
                "$path#sprites",
                "the pool holds $frames frames; at most ${BundleLimits.MAX_CACHED_STATES}",
                IssueCode.OVER_STATES,
                issueValues("frames" to frames, "limit" to BundleLimits.MAX_CACHED_STATES),
            )
        }
    }

    private fun checkSpawn(spawn: SpawnJson) {
        if (SpawnShape.entries.none { it.name.equals(spawn.shape, ignoreCase = true) }) {
            report("$path#spawn.shape", "must be point, line, area or border")
        }
        if (Anchor.named(spawn.anchor) == null) {
            report("$path#spawn.anchor", "must be one of ${BundleValidator.ANCHORS.joinToString()}")
        }
        if (Edge.named(spawn.edge) == null) {
            report("$path#spawn.edge", "must be top, bottom, left or right")
        }
        if (spawn.offset.size != 2 || spawn.offset.any { it !in -BundleLimits.MAX_OFFSET..BundleLimits.MAX_OFFSET }) {
            report("$path#spawn.offset", "must be [x, y], each within ${BundleLimits.MAX_OFFSET} px")
        }
        spawn.follow?.let { follow ->
            val named = EdgeFollow.named(follow)
            val area = spawn.shape.equals(SpawnShape.AREA.name, ignoreCase = true)
            when {
                named == null -> report("$path#spawn.follow", "must be outside, line or inside")
                area && named != EdgeFollow.INSIDE ->
                    report("$path#spawn.follow", "particles spawned across the area can only follow inside")
                !area && !spawn.shape.equals(SpawnShape.BORDER.name, ignoreCase = true) ->
                    report("$path#spawn.follow", "following the body's edge only works on border and area spawns")
            }
        }
    }

    private fun checkRate(rate: RateJson) {
        if (rate.every !in 1..BundleLimits.MAX_LOOP) {
            report("$path#rate.every", "must be 1 to ${BundleLimits.MAX_LOOP} steps")
        }
        if (rate.count !in 1..AdaptiveLimits.MAX_SPAWN_COUNT) {
            report("$path#rate.count", "must be 1 to ${AdaptiveLimits.MAX_SPAWN_COUNT}")
        }
        if (rate.jitter < 0 || rate.jitter >= rate.every) {
            report("$path#rate.jitter", "must be 0 to every - 1")
        }
    }

    private fun checkLifetime(lifetime: List<Int>) {
        if (
            lifetime.size != 2 ||
            lifetime[0] !in 1..AdaptiveLimits.MAX_LIFETIME ||
            lifetime[1] !in lifetime[0]..AdaptiveLimits.MAX_LIFETIME
        ) {
            report("$path#lifetime", "must be [min, max] steps with 1 <= min <= max <= ${AdaptiveLimits.MAX_LIFETIME}")
        }
    }

    private fun checkRange(at: String, range: List<Double>, limit: Double) {
        if (range.size != 2 || range.any { !it.isFinite() || it !in -limit..limit } || range[0] > range[1]) {
            report(at, "must be [min, max] with min <= max, each within $limit")
        }
    }

    private fun checkPair(at: String, pair: List<Double>, limit: Double) {
        if (pair.size != 2 || pair.any { !it.isFinite() || it !in -limit..limit }) {
            report(at, "must be [x, y], each within $limit")
        }
    }
}
