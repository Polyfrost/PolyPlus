@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.nameplate.engine.api.anim.TextMotion
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.TextProgramValidator
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueSink

@JsExport
class NameplateLetters internal constructor(
    val count: Int,
    val moving: Boolean,
    val dx: IntArray,
    val dy: IntArray,
    val opacity: IntArray,
    val color: IntArray,
    val shadow: IntArray,
    val outline: IntArray,
    val scale: DoubleArray,
    val rotation: IntArray,
)

@JsExport
class NameplateLetterMotion(private val preview: NameplatePreview) {
    private val motion = TextMotion.of(preview.bundle)
    private val frame = LetterFrame()

    val canMove: Boolean
        get() = motion.canMove

    fun mode(variant: String): String = motion.modeOf(preview.bundle.variant(variant)).name.lowercase()

    val cost: Int
        get() = motion.cost

    val failed: Boolean
        get() = motion.failed

    val maxLetters: Int
        get() = LetterFrame.MAX_GLYPHS

    val keep: Int
        get() = LetterFrame.KEEP

    val off: Int
        get() = LetterFrame.OFF

    fun letters(
        variant: String,
        block: NameplateBlock,
        frame: Int,
        seed: Int,
        glyphX: IntArray,
        glyphWidth: IntArray,
        state: NameplateState? = null,
    ): NameplateLetters {
        val count = minOf(glyphX.size, glyphWidth.size)
        val shown = if (count > LetterFrame.MAX_GLYPHS) 0 else count
        val bundle = preview.bundle
        val out = this.frame
        motion.evaluate(
            bundle,
            bundle.variant(variant),
            block.block,
            frame,
            seed,
            state?.state ?: TagState.NONE,
            shown,
            glyphX,
            glyphWidth,
            out,
        )
        return NameplateLetters(
            out.count,
            out.moving,
            out.dx.copyOf(out.count),
            out.dy.copyOf(out.count),
            out.opacity.copyOf(out.count),
            out.color.copyOf(out.count),
            out.shadow.copyOf(out.count),
            out.outline.copyOf(out.count),
            DoubleArray(out.count) { out.scaleOf(it).toDouble() },
            out.rotation.copyOf(out.count),
        )
    }
}

@JsExport
fun nameplateCheckLetterProgram(
    source: String,
    mode: String,
    fps: Int,
    loop: Int,
    params: Array<String> = emptyArray(),
    values: DoubleArray = DoubleArray(0),
): Array<NameplateIssue> {
    val manifest = ManifestJson(
        format = 1,
        id = "letters",
        fps = fps,
        loop = loop,
        params = params.withIndex().associate { (i, name) -> name to values.getOrElse(i) { 0.0 } },
        text = TextJson(mode = mode, motion = source),
    )
    val issues = ArrayList<BundleIssue>()
    TextProgramValidator.validate(manifest, issueSink(issues).coded(IssueCode.LETTER_PROGRAM))
    return issues.map { it.toJs() }.toTypedArray()
}
