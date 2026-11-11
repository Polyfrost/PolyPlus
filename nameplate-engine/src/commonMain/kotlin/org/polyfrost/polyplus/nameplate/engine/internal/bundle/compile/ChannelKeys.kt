package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangValue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.query.MolangUse
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelValue
import org.polyfrost.polyplus.nameplate.engine.internal.track.Interpolation
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track
import kotlin.math.floor

internal object ChannelKeys {
    const val FRAME = "frame"
    const val POSITION = "position"
    const val OPACITY = "opacity"
    const val VISIBLE = "visible"

    val CHANNELS: Set<String> = setOf(FRAME, POSITION, OPACITY, VISIBLE)

    private val STEP_ONLY = setOf(FRAME, VISIBLE)
    private const val OPACITY_UNITS = 255.0
    private const val HALF = 0.5

    class Parsed(
        val steps: IntArray,
        val values: IntArray,
        val seconds: IntArray,
        val modes: IntArray,
        val molang: Array<MolangValue?>,
        val secondMolang: Array<MolangValue?>,
    )

    private class Value(
        val first: Int,
        val second: Int = 0,
        val firstMolang: MolangValue? = null,
        val secondMolang: MolangValue? = null,
    )

    fun parse(
        channel: String,
        element: JsonElement,
        path: String,
        report: IssueSink,
        environment: MolangEnvironment = NameplateQueries.environment(MolangUse.CHANNEL),
    ): Parsed? {
        if (element !is JsonArray) {
            report(path, "must be a list of ${shape(channel)} keys")
            return null
        }

        val steps = IntArray(element.size)
        val values = IntArray(element.size)
        val seconds = IntArray(element.size)
        val modes = IntArray(element.size)
        val molang = arrayOfNulls<MolangValue>(element.size)
        val secondMolang = arrayOfNulls<MolangValue>(element.size)
        var valid = true
        for ((i, entry) in element.withIndex()) {
            val keyPath = "$path[$i]"
            val key = entry as? JsonArray
            val step = key?.getOrNull(0)?.let(::wholeNumber)
            if (key == null || key.size !in 2..3 || step == null) {
                report(keyPath, "must be ${shape(channel)}")
                valid = false
                continue
            }
            val value = readValue(channel, key[1], keyPath, report, environment)
            val mode = if (key.size == 3) readMode(channel, key[2], keyPath, report) else Interpolation.STEP
            if (value == null || mode == null) {
                valid = false
                continue
            }
            steps[i] = step
            values[i] = value.first
            seconds[i] = value.second
            modes[i] = mode
            molang[i] = value.firstMolang
            secondMolang[i] = value.secondMolang
        }
        return if (valid) Parsed(steps, values, seconds, modes, molang, secondMolang) else null
    }

    fun compile(channels: Map<String, JsonElement>, molang: BundleMolang): ChannelTracks? {
        val frame = scalar(FRAME, channels, molang, ChannelValue.FRAME)
        val position = parsed(POSITION, channels, molang)
        val opacity = scalar(OPACITY, channels, molang, ChannelValue.OPACITY)
        val visible = scalar(VISIBLE, channels, molang, ChannelValue.VISIBLE)
        if (frame == null && position == null && opacity == null && visible == null) return null
        return ChannelTracks(
            frame = frame,
            positionX = position?.let { track(it.steps, it.values, it.modes, it.molang, molang, ChannelValue.POSITION) },
            positionY = position?.let { track(it.steps, it.seconds, it.modes, it.secondMolang, molang, ChannelValue.POSITION) },
            opacity = opacity,
            visible = visible,
        )
    }

    private fun parsed(channel: String, channels: Map<String, JsonElement>, molang: BundleMolang): Parsed? =
        channels[channel]?.let { parse(channel, it, "", { }, molang.channelEnvironment) }

    private fun scalar(channel: String, channels: Map<String, JsonElement>, molang: BundleMolang, kind: Int): Track? =
        parsed(channel, channels, molang)?.let { track(it.steps, it.values, it.modes, it.molang, molang, kind) }

    private fun track(
        steps: IntArray,
        values: IntArray,
        modes: IntArray,
        expressions: Array<MolangValue?>,
        molang: BundleMolang,
        kind: Int,
    ): Track {
        if (expressions.all { it == null }) return Track(steps, values, modes, kind = kind)
        val slots = IntArray(expressions.size) { i ->
            expressions[i]?.let { molang.addChannel(it, kind) } ?: Track.NO_SLOT
        }
        val shown = IntArray(values.size) { i -> if (expressions[i] == null) values[i] else ChannelValue.neutral(kind) }
        return Track(steps, shown, modes, slots, kind)
    }

    private fun shape(channel: String): String = when (channel) {
        POSITION -> "[step, [x, y]]"
        OPACITY -> "[step, opacity]"
        VISIBLE -> "[step, visible]"
        else -> "[step, frame]"
    }

    private fun readMode(channel: String, element: JsonElement, path: String, report: IssueSink): Int? {
        val mode = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(Interpolation::parse)
        when {
            mode == null -> report(path, "must be ${shape(channel)}")
            mode != Interpolation.STEP && channel in STEP_ONLY -> report(path, "$channel tracks only use step")
            else -> return mode
        }
        return null
    }

    private fun readValue(
        channel: String,
        element: JsonElement,
        path: String,
        report: IssueSink,
        environment: MolangEnvironment,
    ): Value? {
        if (channel != POSITION && isText(element)) return molang(element, path, report, environment)?.let { Value(0, firstMolang = it) }
        val value = when (channel) {
            POSITION -> position(element, path, report, environment)
            OPACITY -> opacity(element, path, report)
            VISIBLE -> flag(element)
            else -> wholeNumber(element)?.let { Value(it) }
        }
        if (value == null && !(channel == POSITION || channel == OPACITY)) {
            report(path, "must be ${shape(channel)}")
        }
        return value
    }

    private fun flag(element: JsonElement): Value? =
        (element as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { Value(if (it) 1 else 0) }

    private fun position(element: JsonElement, path: String, report: IssueSink, environment: MolangEnvironment): Value? {
        val pair = (element as? JsonArray)?.takeIf { it.size == 2 }
        val xText = pair != null && isText(pair[0])
        val yText = pair != null && isText(pair[1])
        val x = pair?.takeIf { !xText }?.let { wholeNumber(it[0]) }
        val y = pair?.takeIf { !yText }?.let { wholeNumber(it[1]) }
        val range = -BundleLimits.MAX_OFFSET..BundleLimits.MAX_OFFSET
        return when {
            pair == null || (!xText && x == null) || (!yText && y == null) -> failed(path, "must be ${shape(POSITION)}", report)
            (x != null && x !in range) || (y != null && y !in range) ->
                failed(path, "position must be within ${BundleLimits.MAX_OFFSET} px", report)
            else -> {
                val xMolang = if (xText) molang(pair[0], path, report, environment) else null
                val yMolang = if (yText) molang(pair[1], path, report, environment) else null
                if ((xText && xMolang == null) || (yText && yMolang == null)) null else Value(x ?: 0, y ?: 0, xMolang, yMolang)
            }
        }
    }

    private fun opacity(element: JsonElement, path: String, report: IssueSink): Value? {
        val number = number(element)
        return when {
            number == null -> failed(path, "must be ${shape(OPACITY)}", report)
            number !in 0.0..1.0 -> failed(path, "opacity must be 0 to 1", report)
            else -> Value(floor(number * OPACITY_UNITS + HALF).toInt())
        }
    }

    private fun molang(element: JsonElement, path: String, report: IssueSink, environment: MolangEnvironment): MolangValue? =
        expression((element as JsonPrimitive).content, path, report, environment, "is a constant Molang expression; write it as a number")

    fun expression(source: String, path: String, report: IssueSink, environment: MolangEnvironment, constantMessage: String): MolangValue? {
        val value = MolangValue.of(source, environment, path, report.molang()) ?: return null
        if (value is MolangValue.Constant) {
            report.issue(path, constantMessage, IssueCode.MOLANG_CONSTANT)
            return null
        }
        return value
    }

    private fun failed(path: String, message: String, report: IssueSink): Value? {
        report(path, message)
        return null
    }

    private fun isText(element: JsonElement): Boolean = (element as? JsonPrimitive)?.isString == true

    private fun number(element: JsonElement): Double? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    fun wholeNumber(element: JsonElement): Int? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}
