package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.molang.MolangLimits
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TextTracks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TriggerCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.VariantOverrides
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import org.polyfrost.polyplus.nameplate.engine.internal.text.LetterProgram
import org.polyfrost.polyplus.nameplate.engine.internal.text.TEXT_TARGET
import kotlin.math.abs

internal object TextProgramValidator {
    const val MANIFEST = "manifest.json"
    const val VANILLA = "vanilla"
    const val MOTION = "motion"
    const val OWNED = "owned"

    const val REFERENCE_GLYPHS = 16
    const val REFERENCE_ADVANCE = 6

    private val PROGRAM_KEYS = setOf("mode", "motion")

    private const val OPACITY_STEP = 26
    private const val LUMINANCE_STEP = 0.1
    private const val GLYPH_HEIGHT = TagBlock.GLYPH_HEIGHT

    fun validate(manifest: ManifestJson, report: IssueSink) {
        val mode = manifest.text.mode
        if (!isMode(mode)) return
        val params = paramNames(manifest)
        manifest.text.motion?.takeIf { it.isNotBlank() && mode != VANILLA }?.let { source ->
            ProgramValidator(manifest, mode, params, report).program(source, "$MANIFEST#text.motion", manifest.loop, base = true)
        }
        for ((key, variant) in manifest.variants) {
            variantProgram(manifest, key, variant, params, report)
        }
        val tracks = ProgramValidator(manifest, trackMode(manifest), params, report)
        for ((name, animation) in manifest.animations) {
            val channels = animation.tracks[TEXT_TARGET] ?: continue
            tracks.track("$MANIFEST#animations.$name.tracks.$TEXT_TARGET", name, animation, channels.keys)
        }
    }

    private fun variantProgram(manifest: ManifestJson, key: String, variant: VariantJson, params: Set<String>, report: IssueSink) {
        val overrides = variant.text ?: return
        if (overrides.keys.none { it in PROGRAM_KEYS }) return
        val merged = merged(manifest, variant) ?: return
        val source = merged.motion?.takeIf { it.isNotBlank() && merged.mode != VANILLA } ?: return
        if (merged.mode == manifest.text.mode && source == manifest.text.motion) return
        ProgramValidator(manifest, merged.mode, params, report, VariantOverrides.params(manifest, variant))
            .program(source, "$MANIFEST#variants.$key.text.motion", manifest.loop, base = true)
    }

    private fun trackMode(manifest: ManifestJson): String {
        val modes = if (manifest.variants.isEmpty()) {
            listOf(manifest.text.mode)
        } else {
            manifest.variants.values.mapNotNull { merged(manifest, it)?.mode }
        }
        return when {
            MOTION in modes -> MOTION
            OWNED in modes -> OWNED
            else -> VANILLA
        }
    }

    private fun merged(manifest: ManifestJson, variant: VariantJson): TextJson? =
        try {
            VariantOverrides.text(manifest.text, variant.text).takeIf { isMode(it.mode) }
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun isMode(mode: String): Boolean = mode == MOTION || mode == OWNED || mode == VANILLA

    private fun paramNames(manifest: ManifestJson): Set<String> {
        val names = LinkedHashSet(manifest.params.keys)
        for (variant in manifest.variants.values) {
            names.addAll(variant.params.keys)
        }
        return names
    }

    private class ProgramValidator(
        private val manifest: ManifestJson,
        private val mode: String,
        private val params: Set<String>,
        private val report: IssueSink,
        private val values: Map<String, Double> = manifest.params,
    ) {
        fun track(path: String, name: String, animation: AnimationJson, channels: Set<String>) {
            for (channel in channels) {
                if (channel != TextTracks.MOTION) {
                    report("$path.$channel", "the text takes only a ${TextTracks.MOTION} track")
                }
            }
            val trigger = animation.trigger?.let { TriggerCompiler.parse(it, path) { } }
            if (name == "loop" || animation.schedule != null || trigger == null) {
                report(path, "text motion takes reaction and one-shot tracks; the loop's letter program is text.motion")
                return
            }
            val element = animation.tracks[TEXT_TARGET]?.get(TextTracks.MOTION) ?: return
            val motionPath = "$path.${TextTracks.MOTION}"
            val source = TextTracks.source(element)
            if (source == null) {
                report(motionPath, "must be a letter program (Molang text), or null to hold the letters still")
                return
            }
            if (mode == VANILLA) {
                report(motionPath, "text motion needs text.mode \"$MOTION\" or \"$OWNED\" on at least one variant")
                return
            }
            if (source.isNotBlank()) {
                val length = if (trigger is Trigger.While) manifest.loop else animation.length ?: manifest.loop
                program(source, motionPath, length, base = false)
            }
        }

        fun program(source: String, path: String, length: Int, base: Boolean) {
            var valid = true
            val molang = report.molang()
            val program = LetterProgram.compile(source, params, path) { issue ->
                valid = false
                molang(issue)
            }
            if (program == null || !valid) return
            if (mode == MOTION) {
                val owned = program.ownedOutputs
                if (owned.isNotEmpty()) {
                    report.issue(
                        path,
                        "${owned.joinToString()} only work when text.mode is \"$OWNED\"; moving letters read the result, v.x and v.opacity",
                        IssueCode.LETTER_OWNED,
                        names = mapOf("outputs" to owned.joinToString()),
                    )
                }
            }
            val worst = program.cost.toLong() * LetterFrame.MAX_GLYPHS
            if (worst > MolangLimits.CPU_BUDGET) {
                report.issue(
                    path,
                    "costs up to ${program.cost} CPU units per letter, $worst for ${LetterFrame.MAX_GLYPHS} letters per tag per frame; " +
                        "the budget is ${MolangLimits.CPU_BUDGET}",
                    IssueCode.OVER_CPU,
                    issueValues(
                        "units" to worst,
                        "perLetter" to program.cost,
                        "letters" to LetterFrame.MAX_GLYPHS,
                        "limit" to MolangLimits.CPU_BUDGET,
                    ),
                )
                return
            }
            if (length !in 1..BundleLimits.MAX_LOOP || manifest.fps !in BundleLimits.MIN_FPS..BundleLimits.MAX_FPS) return
            val readable = base && mode == OWNED
            if (readable || program.writesOpacity || program.writesColor) {
                simulate(program, path, length, readable)
            }
        }

        private fun simulate(program: LetterProgram, path: String, length: Int, readable: Boolean) {
            val glyphs = REFERENCE_GLYPHS
            val runtime = program.runtime()
            val block = TagBlock.line(glyphs * REFERENCE_ADVANCE, 0, glyphs * REFERENCE_ADVANCE)
            for ((name, value) in values) {
                runtime.setParam(name, value)
            }
            NameplateQueries.bindBlock(runtime, block)
            val frames = length + manifest.fps
            val flashes = Array(glyphs) { IntArray(frames) }
            val lastOpacity = IntArray(glyphs) { LetterFrame.OPAQUE }
            val lastLuminance = DoubleArray(glyphs) { Double.NaN }
            val opacityTrend = IntArray(glyphs)
            val luminanceTrend = IntArray(glyphs)
            var inPlace = 0
            val frame = LetterFrame()
            for (f in 0 until frames) {
                val local = f % length
                frame.reset(glyphs)
                runtime.startFrame(MolangLimits.CPU_BUDGET)
                NameplateQueries.bindFrame(runtime, local, length, manifest.fps)
                for (i in 0 until glyphs) {
                    NameplateQueries.bindGlyph(runtime, i, glyphs, i * REFERENCE_ADVANCE, REFERENCE_ADVANCE)
                    if (!program.evaluate(runtime, frame, i)) {
                        report.issue(
                            path,
                            "ran out of its instruction budget; letter programs must finish within " +
                                "${MolangLimits.MAX_INSTRUCTIONS} instructions",
                            IssueCode.LETTER_BUDGET,
                            issueValues("limit" to MolangLimits.MAX_INSTRUCTIONS),
                        )
                        return
                    }
                }
                if (f < length && frame.inPlace) {
                    inPlace++
                }
                for (i in 0 until glyphs) {
                    val opacity = frame.opacity[i]
                    val opacityChange = opacity - lastOpacity[i]
                    if (f > 0 && abs(opacityChange) >= OPACITY_STEP) {
                        val trend = if (opacityChange > 0) 1 else -1
                        if (opacityTrend[i] == -trend) {
                            flashes[i][f] = 1
                            opacityTrend[i] = 0
                        } else {
                            opacityTrend[i] = trend
                        }
                    }
                    lastOpacity[i] = opacity
                    val color = frame.color[i]
                    val luminance = if (color == LetterFrame.KEEP) lastLuminance[i] else Contrast.luminance(color)
                    val luminanceChange = luminance - lastLuminance[i]
                    if (f > 0 && !luminanceChange.isNaN() && abs(luminanceChange) >= LUMINANCE_STEP) {
                        val trend = if (luminanceChange > 0) 1 else -1
                        if (luminanceTrend[i] == -trend) {
                            flashes[i][f] = 1
                            luminanceTrend[i] = 0
                        } else {
                            luminanceTrend[i] = trend
                        }
                    }
                    lastLuminance[i] = luminance
                }
            }
            if (readable && inPlace * 2 < length) {
                report.issue(
                    path,
                    "keeps the name in place for $inPlace of $length frames; " +
                        "owned letters must sit still and readable for at least half the loop",
                    IssueCode.LETTER_READABLE,
                    issueValues("frames" to inPlace, "of" to length),
                )
            }
            if (flashes(flashes, frames)) {
                report.issue(
                    path,
                    "flashes letters more than ${BundleLimits.MAX_FLASHES} times a second over more than " +
                        "${BundleLimits.MAX_FLASH_AREA} px (WCAG 2.3.1)",
                    IssueCode.FLASH,
                    issueValues("limit" to BundleLimits.MAX_FLASHES, "area" to BundleLimits.MAX_FLASH_AREA),
                )
            }
        }

        private fun flashes(flashes: Array<IntArray>, frames: Int): Boolean {
            val window = manifest.fps
            for (start in 0..maxOf(0, frames - window)) {
                var area = 0
                for (glyph in flashes) {
                    var count = 0
                    for (f in start until minOf(frames, start + window)) {
                        count += glyph[f]
                    }
                    if (count > BundleLimits.MAX_FLASHES) {
                        area += REFERENCE_ADVANCE * GLYPH_HEIGHT
                    }
                }
                if (area > BundleLimits.MAX_FLASH_AREA) return true
            }
            return false
        }
    }
}
