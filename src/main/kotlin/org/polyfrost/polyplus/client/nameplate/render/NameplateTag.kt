package org.polyfrost.polyplus.client.nameplate.render

import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
//? if > 1.8.9 {
import net.minecraft.util.FormattedCharSequence
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
//?}

class NameplateTag(
    val nameplate: BoundNameplate,
    val profileName: String,
    val badge: Boolean,
    val detailed: Boolean,
    val frame: Int,
    val state: TagState,
)

//? if > 1.8.9 {
class DisplayTagBinding(
    val nameplate: BoundNameplate,
    val profileName: String,
    val nameLine: FormattedCharSequence,
    val lineIndex: Int,
    val lineCount: Int,
    val badge: Boolean,
    val detailed: Boolean,
    val frame: Int,
    val state: TagState,
) {
    val ownsBackground: Boolean
        get() = nameplate.variant.ownsBackground && lineCount <= TagBlock.MAX_LINES
}
//?}
