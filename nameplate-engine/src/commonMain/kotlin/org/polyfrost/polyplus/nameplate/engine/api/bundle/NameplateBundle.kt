package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BodyArt
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Burst
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Flag
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Group
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.WidthRange
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.CornerRadii
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.palette.PaletteTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.MaterialParamTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.PoseTarget
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule

class NameplateBundle internal constructor(
    val id: String,
    val requires: Set<String>,
    val fps: Int,
    val loop: Int,
    val ownsBackground: Boolean,
    internal val params: Map<String, Double>,
    internal val layers: List<Layer>,
    internal val palette: Palette,
    internal val flags: List<Flag>,
    val variants: Map<String, Variant>,
    internal val schedules: List<Schedule> = emptyList(),
    internal val paletteTracks: List<PaletteTrack> = emptyList(),
    val perPlayerPhase: Boolean = false,
    internal val groups: List<Group> = emptyList(),
    internal val loopChannels: Map<String, ChannelTracks> = emptyMap(),
    val triggers: Triggers = Triggers.NONE,
    internal val layerGates: Map<String, List<List<WidthRange>>> = emptyMap(),
    internal val bursts: Map<String, List<Burst>> = emptyMap(),
    internal val molang: BundleMolang? = null,
    internal val materialParams: MaterialParamTracks = MaterialParamTracks.NONE,
) {
    private val layersById = layers.associateBy { it.id }
    private val schedulesByLayer: Map<String, Schedule> = buildMap {
        for (schedule in schedules) {
            for (event in schedule.events) {
                for (layer in event.layers) {
                    put(layer, schedule)
                }
            }
        }
    }

    private val scheduleSteps: Map<String, Int> = schedules.associate { schedule ->
        val targets = schedule.events.flatMapTo(HashSet()) { it.layers }
        schedule.id to (layers.firstOrNull { it.id in targets }?.step ?: 1)
    }
    private val poseChains: Map<String, Array<PoseTarget>> = buildPoseChains()
    private val followChains: Map<String, Array<PoseTarget>> = FollowChains.build(layers, poseChains)

    internal val plateChain: Array<PoseTarget> = listOfNotNull(poseTarget(PLATE_TARGET)).toTypedArray()

    internal val badgeChain: Array<PoseTarget> = listOfNotNull(poseTarget(BADGE_TARGET)).toTypedArray()

    internal val hasMasks: Boolean = layers.any { it.mask != null }

    fun variant(key: String?): Variant = key?.let(variants::get) ?: variants.values.first()

    fun layer(id: String): Layer = layersById.getValue(id)

    internal fun scheduleOf(layerId: String): Schedule? = schedulesByLayer[layerId]

    internal fun poseChain(layerId: String): Array<PoseTarget> = poseChains[layerId] ?: NO_TARGETS

    internal fun followChain(layerId: String): Array<PoseTarget> = followChains[layerId] ?: NO_TARGETS

    private fun buildPoseChains(): Map<String, Array<PoseTarget>> {
        val parents = groups.associate { it.id to it.parent }
        val groupOfLayer = HashMap<String, String>()
        for (group in groups) {
            for (layer in group.layers) {
                groupOfLayer[layer] = group.id
            }
        }
        return layers.associate { layer ->
            val chain = ArrayList<PoseTarget>()
            poseTarget(layer.id)?.let(chain::add)
            var group = groupOfLayer[layer.id]
            var depth = 0
            while (group != null && depth++ <= groups.size) {
                poseTarget(group)?.let(chain::add)
                group = parents[group]
            }
            layer.id to chain.toTypedArray()
        }
    }

    private fun poseTarget(id: String): PoseTarget? {
        val loop = loopChannels[id]
        val schedule = schedulesByLayer[id]
        if (loop == null && schedule == null && id !in triggers.targets) return null
        return PoseTarget(id, schedule?.let { scheduleSteps[it.id] } ?: 1, loop, schedule)
    }

    class Layer internal constructor(
        internal val id: String,
        internal val depth: Depth,
        internal val step: Int,
        internal val mask: String?,
        internal val content: LayerContent,
        internal val clipFill: Boolean = false,
    )

    sealed interface LayerContent {
        class Body internal constructor(
            internal val padLeft: Int,
            internal val padTop: Int,
            internal val padRight: Int,
            internal val padBottom: Int,
            internal val minWidth: Int,
            internal val radius: Int,
            internal val fill: Fill?,
            internal val rings: List<Fill>,
            internal val corners: CornerRadii = CornerRadii.uniform(radius),
            internal val art: BodyArt? = null,
            internal val aroundName: Boolean = false,
        ) : LayerContent {
            internal fun rect(block: TagBlock): PixelRect {
                val least = maxOf(minWidth, art?.minWidth ?: 0)
                if (aroundName) {
                    val name = block.nameRect()
                    return PixelRect(name.x0 - padLeft, name.y0 - padTop, name.x1 + padRight, name.y1 + padBottom).withMinWidth(least)
                }
                val background = block.background
                return PixelRect(
                    background.x0 + VANILLA_INSET - padLeft,
                    background.y0 + VANILLA_INSET - padTop,
                    background.x1 + padRight,
                    background.y1 - VANILLA_INSET + padBottom,
                ).withMinWidth(least)
            }
        }

        class Sprite internal constructor(
            internal val frames: List<PixelSprite>,
            internal val of: String?,
            internal val anchor: Anchor,
            internal val pivot: Anchor,
            internal val dx: Int,
            internal val dy: Int,
            internal val mirror: Boolean,
            private val timeline: IntArray? = null,
            internal val mirrorDelay: Int = 0,
            internal val mirrorSeed: Boolean = false,
            internal val adaptive: SpriteAdaptive = SpriteAdaptive.NONE,
            internal val place: OrnamentAnchor = OrnamentAnchor.of(anchor),
            internal val follow: EdgeFollow? = null,
        ) : LayerContent {
            internal val hasTimeline: Boolean
                get() = timeline != null

            internal fun timelineFrame(cycleStep: Int): Int = timeline?.get(cycleStep) ?: 0

            internal fun timelineFrames(): IntArray = timeline?.copyOf() ?: IntArray(0)
        }
    }

    class Variant internal constructor(
        val key: String,
        internal val colorway: String,
        internal val palette: Palette,
        val text: TextStyling,
        val badge: BadgeStyling,
        internal val hidden: Set<String>,
        internal val swaps: Map<String, String>,
        internal val layerOrder: List<String>,
        internal val flag: Flag?,
        val ownsBackground: Boolean,
        internal val params: Map<String, Double>,
        val textMode: TextMode,
        internal val motion: String?,
    ) {
        internal val resolved: ResolvedPalette = palette.resolve()
    }

    enum class Depth {
        BEHIND,
        BACKGROUND,
        OVER_BACKGROUND,
        OVER_TEXT,
    }

    enum class TextMode {
        VANILLA,
        MOTION,
        OWNED,
    }

    private companion object {
        const val VANILLA_INSET = 1

        val NO_TARGETS: Array<PoseTarget> = emptyArray()
    }
}
