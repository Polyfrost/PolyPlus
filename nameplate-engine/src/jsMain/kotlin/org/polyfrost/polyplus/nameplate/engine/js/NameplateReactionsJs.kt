@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.nameplate.engine.api.anim.TagRecord
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState

@JsExport
class NameplateState internal constructor(internal val state: TagState, private val names: Array<String>) {
    val reactions: Array<String>
        get() = state.inputs.reactions().map { it.id }.toTypedArray()

    val oneShots: Array<String>
        get() = Array(state.oneShotCount) { names.getOrElse(state.oneShot(it)) { "" } }

    val starts: IntArray
        get() = IntArray(state.oneShotCount) { state.start(it) }
}

@JsExport
fun nameplateState(reactions: Array<String>, reduced: Boolean = false): NameplateState =
    NameplateState(TagState.of(inputsOf(reactions), reduced = reduced), emptyArray())

@JsExport
class NameplateRecord(private val preview: NameplatePreview) {
    private var now = 0L
    private val record = TagRecord<String>(clock = { now })
    private val names = preview.oneShots

    val size: Int
        get() = record.size

    fun update(key: String, variant: String, frame: Int, reactions: Array<String>, timeMs: Double, seed: Int = 0): NameplateState {
        now = timeMs.toLong()
        val bundle = preview.bundle
        return NameplateState(record.update(key, bundle, bundle.variant(variant), frame, inputsOf(reactions), seed), names)
    }

    fun fire(key: String, animation: String, frame: Int): Boolean = record.fire(key, preview.bundle, animation, frame)

    fun state(key: String): NameplateState = NameplateState(record.state(key), names)

    fun clear() {
        record.clear()
    }
}

private fun inputsOf(reactions: Array<String>): TagInputs =
    TagInputs.of(*reactions.mapNotNull(Reaction::of).toTypedArray())
