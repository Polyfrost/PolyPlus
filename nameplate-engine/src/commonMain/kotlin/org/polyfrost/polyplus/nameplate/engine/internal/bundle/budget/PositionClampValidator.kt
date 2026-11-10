package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AnimationCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ChannelKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal object PositionClampValidator {
    private const val TRACKS = "tracks"
    private const val AXIS_X = "x"
    private const val AXIS_Y = "y"

    private class Moved(
        val animation: String,
        val target: String,
        val axis: String,
        val track: Track,
        val onLoopClock: Boolean,
        val frames: Int,
    )

    private class Over(val reach: Double, val variant: NameplateBundle.Variant, val block: TagBlock)

    fun validate(bundle: NameplateBundle): List<BundleIssue> {
        val molang = bundle.molang ?: return emptyList()
        val moved = moved(bundle)
        if (moved.isEmpty()) return emptyList()

        val frame = MolangFrame(molang, bundle.loop, bundle.fps)
        val over = arrayOfNulls<Over>(moved.size)
        for (variant in bundle.variants.values.distinctBy { it.params }) {
            for (block in ReferencePlacements.ALL) {
                val reaches = reaches(moved, over, frame, variant, block)
                for (i in moved.indices) {
                    if (over[i] == null && reaches[i] > BundleLimits.MAX_OFFSET) {
                        over[i] = Over(reaches[i], variant, block)
                    }
                }
            }
        }

        val issues = ArrayList<BundleIssue>()
        val report = IssueSink { issues.add(it) }
        val first = bundle.variants.values.first()
        for (i in moved.indices) {
            over[i]?.let { report.overReach(moved[i], it, first) }
        }
        return issues
    }

    private fun reaches(
        moved: List<Moved>,
        over: Array<Over?>,
        frame: MolangFrame,
        variant: NameplateBundle.Variant,
        block: TagBlock,
    ): DoubleArray {
        val reaches = DoubleArray(moved.size)
        for (at in 0 until moved.maxOf { it.frames }) {
            frame.bind(variant.params, block, at)
            for (i in moved.indices) {
                if (over[i] == null && at < moved[i].frames) {
                    val step = at.takeIf { moved[i].onLoopClock }
                    reaches[i] = maxOf(reaches[i], moved[i].track.positionReach(frame, step))
                }
            }
        }
        return reaches
    }

    private fun moved(bundle: NameplateBundle): List<Moved> {
        val moved = ArrayList<Moved>()
        for ((target, tracks) in bundle.loopChannels) {
            moved.addTracks(AnimationCompiler.LOOP, target, tracks, onLoopClock = true, bundle.loop)
        }
        for (schedule in bundle.schedules) {
            for (event in schedule.events) {
                for (target in event.layers) {
                    event.channels(target)?.let { moved.addTracks(event.name, target, it, onLoopClock = false, bundle.loop) }
                }
            }
        }
        for (shot in bundle.triggers.oneShots) {
            for (target in shot.targets) {
                shot.channels(target)?.let { moved.addTracks(shot.name, target, it, onLoopClock = false, shot.length) }
            }
        }
        for (reaction in bundle.triggers.whiles) {
            for (target in reaction.targets) {
                reaction.channels(target)?.let { moved.addTracks(reaction.name, target, it, onLoopClock = true, bundle.loop) }
            }
        }
        return moved
    }

    private fun MutableList<Moved>.addTracks(animation: String, target: String, tracks: ChannelTracks, onLoopClock: Boolean, frames: Int) {
        tracks.positionX?.takeIf { it.usesMolang }?.let { add(Moved(animation, target, AXIS_X, it, onLoopClock, frames)) }
        tracks.positionY?.takeIf { it.usesMolang }?.let { add(Moved(animation, target, AXIS_Y, it, onLoopClock, frames)) }
    }

    private fun IssueSink.overReach(moved: Moved, over: Over, firstVariant: NameplateBundle.Variant) {
        val reach = over.reach.toLong()
        val width = over.block.nameWidth
        val names = LinkedHashMap<String, String>()
        names["animation"] = moved.animation
        names["target"] = moved.target
        names["axis"] = moved.axis
        if (over.variant !== firstVariant) {
            names["variant"] = over.variant.key
        }
        issue(
            "${BundleFiles.MANIFEST}#animations.${moved.animation}.$TRACKS.${moved.target}.${ChannelKeys.POSITION}",
            "position formula reaches $reach px on ${moved.axis} at a name $width px wide; " +
                "position stops at ${BundleLimits.MAX_OFFSET} px",
            IssueCode.POSITION_CLAMPED,
            issueValues("px" to reach, "limit" to BundleLimits.MAX_OFFSET, "width" to width, "lines" to over.block.lineCount),
            names,
        )
    }
}
