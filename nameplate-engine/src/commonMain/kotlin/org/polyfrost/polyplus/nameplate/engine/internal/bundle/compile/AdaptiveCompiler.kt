package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.internal.anim.Particles
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathMotion
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PathJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.WalkersJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Breakpoint
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Burst
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Edge
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeSide
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fade
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PathEnd
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PathStop
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Spawn
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Spread
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpritePath
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteRepeat
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.WidthRange
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Wobble
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.Interpolation
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track
import kotlin.math.floor
import kotlin.math.roundToInt

internal object AdaptiveCompiler {
    const val BURST = "burst"

    private const val HALF = 0.5

    val PRESETS: Map<String, List<Anchor>> = mapOf(
        "top" to listOf(Anchor.TOP_LEFT, Anchor.TOP_RIGHT),
        "bottom" to listOf(Anchor.BOTTOM_LEFT, Anchor.BOTTOM_RIGHT),
        "perimeter" to listOf(Anchor.TOP_LEFT, Anchor.TOP_RIGHT, Anchor.BOTTOM_RIGHT, Anchor.BOTTOM_LEFT, Anchor.TOP_LEFT),
    )

    fun spriteIds(file: LayerFileJson): List<String> = when (file) {
        is BodyLayerJson -> listOfNotNull(file.art?.sprite)
        is SpriteLayerJson -> listOf(file.sprite) + file.breakpoints.orEmpty().mapNotNull { it.sprite }
        is EmitterLayerJson -> file.sprites
        is MaterialLayerJson -> emptyList()
    }

    fun fixed(value: Double, unit: Int): Int = floor(value * unit + HALF).toInt()

    fun sprite(file: SpriteLayerJson, sprites: Map<String, SpriteFileJson>, base: Palette, cycle: Int): SpriteAdaptive {
        val breakpoints = file.breakpoints
        val path = file.path
        val repeat = file.repeat
        if (breakpoints == null && path == null && repeat == null) return SpriteAdaptive.NONE

        val alternatives = ArrayList<List<PixelSprite>>()
        val entries = breakpoints.orEmpty().map { entry ->
            val id = entry.sprite
            val alternative = if (id == null || id == file.sprite) {
                0
            } else {
                alternatives.add(frames(sprites.getValue(id), base))
                alternatives.size
            }
            Breakpoint(WidthRange(entry.min, entry.max), alternative)
        }
        return SpriteAdaptive(
            path = path?.let { path(it, file.walkers ?: WalkersJson(), cycle) },
            repeat = repeat?.let { SpriteRepeat(checkNotNull(Edge.named(it.edge)), it.spacing, it.min, it.max) },
            breakpoints = entries,
            alternatives = alternatives,
        )
    }

    fun emitter(file: EmitterLayerJson, sprites: Map<String, SpriteFileJson>, base: Palette): ParticleEmitter {
        val rate = file.rate
        val wobble = file.wobble
        return ParticleEmitter(
            pool = file.sprites.map { frames(sprites.getValue(it), base) },
            of = file.of,
            spawn = Spawn(
                shape = SpawnShape.valueOf(file.spawn.shape.uppercase()),
                anchor = Anchor.named(file.spawn.anchor) ?: Anchor.TOP_LEFT,
                edge = Edge.named(file.spawn.edge) ?: Edge.TOP,
                dx = file.spawn.offset[0],
                dy = file.spawn.offset[1],
                follow = file.spawn.follow?.let(EdgeFollow::named),
            ),
            every = rate?.every ?: 0,
            count = rate?.count ?: 0,
            jitter = rate?.jitter ?: 0,
            life = Spread(file.lifetime[0], file.lifetime[1]),
            velocityX = spread(file.velocity.x),
            velocityY = spread(file.velocity.y),
            gravityX = fixed(file.gravity[0], Particles.UNIT),
            gravityY = fixed(file.gravity[1], Particles.UNIT),
            driftX = fixed(file.drift[0], Particles.UNIT),
            driftY = fixed(file.drift[1], Particles.UNIT),
            wobble = wobble?.let { Wobble(it.x, it.y, it.period) },
            stretch = file.frames == "stretch",
            fade = file.fade?.let { Fade(it.after, (it.to * LayerPose.OPAQUE).roundToInt(), it.steps) },
        )
    }

    fun gates(manifest: ManifestJson, layers: Map<String, LayerFileJson>): Map<String, List<List<WidthRange>>> {
        val groups = manifest.groups.orEmpty()
        val parents = groups.associate { it.id to it.parent }
        val ranges = groups.associate { group -> group.id to group.breakpoints?.map { WidthRange(it.min, it.max) } }
        val groupOf = HashMap<String, String>()
        for (group in groups) {
            group.layers.forEach { groupOf[it] = group.id }
        }

        val gates = HashMap<String, List<List<WidthRange>>>()
        for (layer in manifest.layers) {
            val chain = ArrayList<List<WidthRange>>()
            val emitter = layers[layer.id] as? EmitterLayerJson
            emitter?.breakpoints?.let { own -> chain.add(own.map { WidthRange(it.min, it.max) }) }
            val attached = emitter?.of?.let(layers::get) as? SpriteLayerJson
            attached?.breakpoints?.let { own -> chain.add(own.map { WidthRange(it.min, it.max) }) }
            var group = groupOf[layer.id]
            var hops = 0
            while (group != null && hops++ <= groups.size) {
                ranges[group]?.let(chain::add)
                group = parents[group]
            }
            if (chain.isNotEmpty()) {
                gates[layer.id] = chain
            }
        }
        return gates
    }

    fun bursts(manifest: ManifestJson): Map<String, List<Burst>> {
        val steps = manifest.layers.associate { it.id to it.step }
        val schedules = AnimationCompiler.schedules(manifest.animations, BundleMolang.of(manifest)).associateBy { it.id }
        val triggered = TriggerCompiler.triggered(manifest.animations)
        val shots = TriggerCompiler.oneShotIndices(manifest.animations)
        val bursts = LinkedHashMap<String, MutableList<Burst>>()
        for ((name, animation) in manifest.animations) {
            val scheduleId = animation.schedule?.let { it.id ?: name }
            val trigger = triggered[name]
            val shot = shots[name] ?: Burst.NO_SHOT
            if (
                (animation.trigger != null && trigger == null) ||
                (trigger == null && scheduleId == null && name != AnimationCompiler.LOOP)
            ) {
                continue
            }
            for ((target, channels) in animation.tracks) {
                val keys = channels[BURST] as? JsonArray ?: continue
                val step = steps[target] ?: 1
                val schedule = scheduleId?.takeIf { trigger == null }?.let(schedules::getValue)
                val at = IntArray(keys.size) { keyAt(keys, it, 0) }
                val burst = Burst(
                    schedule = schedule,
                    event = schedule?.events?.indexOfFirst { it.name == name } ?: 0,
                    steps = if (schedule == null && shot == Burst.NO_SHOT) IntArray(at.size) { at[it] / step } else at,
                    counts = IntArray(keys.size) { keyAt(keys, it, 1) },
                    shot = shot,
                    reaction = (trigger as? Trigger.While)?.reaction,
                )
                bursts.getOrPut(target) { ArrayList() }.add(burst)
            }
        }
        return bursts
    }

    private fun keyAt(keys: JsonArray, index: Int, part: Int): Int =
        ((keys[index] as? JsonArray)?.getOrNull(part))?.let(ChannelKeys::wholeNumber) ?: 0

    private fun path(path: PathJson, walkers: WalkersJson, cycle: Int): SpritePath {
        val anchors = path.preset?.let(PRESETS::getValue) ?: path.points.orEmpty().map { checkNotNull(Anchor.named(it.anchor)) }
        val points = path.points.orEmpty()
        val speed = speedTrack(path.speed)
        val facings = arrayOfNulls<IntArray>(PathMotion.FACINGS.size)
        for ((facing, frames) in path.frames.orEmpty()) {
            facings[PathMotion.FACINGS.indexOf(facing)] = frames.toIntArray()
        }
        return SpritePath(
            anchors = anchors,
            offsetsX = IntArray(anchors.size) { points.getOrNull(it)?.offset?.get(0) ?: 0 },
            offsetsY = IntArray(anchors.size) { points.getOrNull(it)?.offset?.get(1) ?: 0 },
            pauses = IntArray(anchors.size) { points.getOrNull(it)?.pause ?: 0 },
            speed = speed,
            constantSpeed = if (path.speed is JsonArray) 0 else speed.at(0),
            cycle = cycle,
            end = PathEnd.valueOf(path.end.uppercase()),
            facings = facings,
            walkers = walkers.count,
            spacing = walkers.spacing,
            follow = path.follow?.let(EdgeFollow::named),
            side = path.preset?.let(EdgeSide::named) ?: EdgeSide.PERIMETER,
            laps = path.laps ?: 0,
            reverse = path.reverse,
            even = walkers.even,
            stops = path.stops.orEmpty().map { PathStop(it.at, it.pause) },
        )
    }

    fun speedTrack(element: JsonElement?): Track {
        if (element !is JsonArray) {
            val value = (element as? JsonPrimitive)?.doubleOrNull ?: 1.0
            return Track(intArrayOf(0), intArrayOf(fixed(value, PathMotion.UNIT)))
        }

        val keys = element.map { it as JsonArray }
        return Track(
            IntArray(keys.size) { keys[it][0].let(ChannelKeys::wholeNumber) ?: 0 },
            IntArray(keys.size) { fixed((keys[it][1] as JsonPrimitive).doubleOrNull ?: 0.0, PathMotion.UNIT) },
            IntArray(keys.size) { i ->
                keys[i].getOrNull(2)?.let { (it as? JsonPrimitive)?.contentOrNull }?.let(Interpolation::parse) ?: Interpolation.STEP
            },
        )
    }

    private fun spread(range: List<Double>): Spread = Spread(fixed(range[0], Particles.UNIT), fixed(range[1], Particles.UNIT))

    private fun frames(sprite: SpriteFileJson, base: Palette): List<PixelSprite> {
        val colors = sprite.colors.entries.associate { (key, ref) ->
            val slot = ColorRef.parse(ref) as ColorRef.Slot
            key[0] to base.ref(slot.id, slot.tone)
        }
        return BundleFiles.framesOf(sprite).orEmpty().map { PixelSprite(it, colors) }
    }
}
