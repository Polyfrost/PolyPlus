package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.Avatar
//?} elif >= 1.21.4 {
/*import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}
//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
//?}
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSink
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.polyfrost.polyplus.client.PolyPlusBadge

object NameplateExtraction {

    //? if >= 1.21.10 {
    @JvmStatic
    fun bindAvatar(entity: Avatar, state: AvatarRenderState) {
        bindTag(entity as? Player, state as NameplateTagAccess, state.nameTag != null, state.distanceToCameraSq)
    }
    //?} elif >= 1.21.4 {
    /*@JvmStatic
    fun bindPlayer(entity: AbstractClientPlayer, state: PlayerRenderState) {
        bindTag(entity, state as NameplateTagAccess, state.nameTag != null, state.distanceToCameraSq)
    }
    *///?}

    private fun bindTag(player: Player?, access: NameplateTagAccess, named: Boolean, distanceSq: Double) {
        access.`polyplus$setNameplateTag`(null)
        if (!named || player == null || !NameplateGate.active()) {
            return
        }
        access.`polyplus$setNameplateTag`(playerTag(player, distanceSq))
    }

    //? if >= 1.21.4 {
    @JvmStatic
    fun bindDisplay(display: Display.TextDisplay, state: TextDisplayEntityRenderState) {
        val access = state as DisplayTagAccess
        access.`polyplus$setDisplayTag`(null)
        val info = state.cachedInfo ?: return
        val text = state.textRenderState ?: return
        if (!display.isPassenger || !NameplateGate.active() || !textVisible(text, state.interpolationProgress)) {
            return
        }
        access.`polyplus$setDisplayTag`(displayTag(display, info, state.distanceToCameraSq))
    }
    //?}

    fun playerTag(player: Player, distanceSq: Double): NameplateTag? {
        val uuid = player.uuid
        val nameplate = NameplateGate.bound(uuid) ?: return null
        val frame = NameplateClock.frame(nameplate)
        return NameplateTag(
            nameplate,
            profileName(player),
            PolyPlusBadge.shouldBadge(uuid),
            NameplateDetail.detailed(player.id, distanceSq),
            frame,
            NameplateReactions.state(player, nameplate, frame),
        )
    }

    fun displayTag(display: Display.TextDisplay, info: Display.TextDisplay.CachedInfo, distanceSq: Double): DisplayTagBinding? {
        val player = VehicleChain.player(display) ?: return null

        val lines = info.lines()
        val profileName = profileName(player)
        val nameLine = DisplayNameLine.find(lines, profileName)
        if (nameLine == DisplayNameLine.NONE) {
            return null
        }

        val uuid = player.uuid
        val nameplate = NameplateGate.bound(uuid) ?: return null
        if (!DisplayPick.closest(player, display, profileName)) {
            return null
        }
        val frame = NameplateClock.frame(nameplate)
        return DisplayTagBinding(
            nameplate,
            profileName,
            lines[nameLine].contents(),
            nameLine,
            lines.size,
            PolyPlusBadge.shouldBadge(uuid),
            NameplateDetail.detailed(display.id, distanceSq),
            frame,
            NameplateReactions.state(player, nameplate, frame),
        )
    }

    fun textVisible(text: Display.TextDisplay.TextRenderState, progress: Float): Boolean =
        DisplayTagRenderer.textVisible(text.textOpacity().get(progress))

    private fun profileName(player: Player): String =
        //? if >= 1.21.10 {
        player.plainTextName
        //?} else {
        /*player.gameProfile.name
        *///?}
}

object VehicleChain {
    const val MAX_DEPTH: Int = 8

    fun player(start: Entity): Player? {
        var current: Entity? = start
        repeat(MAX_DEPTH) {
            current = current?.vehicle
            val vehicle = current ?: return null
            if (vehicle === start) {
                return null
            }
            if (vehicle is Player) {
                return vehicle
            }
        }
        return null
    }
}

object DisplayPick {
    private const val SETTLED = 1f

    private val queue = ArrayList<Entity>()
    private val splitter = Display.TextDisplay.LineSplitter { input, width -> split(input, width) }

    fun closest(player: Player, display: Display.TextDisplay, profileName: String): Boolean {
        val riders = player.passengers
        if (riders.size == 1 && riders[0] === display) {
            return true
        }

        queue.clear()
        try {
            enqueue(riders)
            var head = 0
            var depth = 1
            while (head < queue.size) {
                val end = queue.size
                while (head < end) {
                    val rider = queue[head++]
                    if (rider === display) {
                        return true
                    }
                    if (rider is Display.TextDisplay && showsName(rider, profileName)) {
                        return false
                    }
                    if (depth < VehicleChain.MAX_DEPTH) {
                        enqueue(rider.passengers)
                    }
                }
                depth++
            }
            return true
        } finally {
            queue.clear()
        }
    }

    private fun enqueue(riders: List<Entity>) {
        for (i in riders.indices) {
            queue.add(riders[i])
        }
    }

    private fun showsName(other: Display.TextDisplay, profileName: String): Boolean {
        val text = other.textRenderState() ?: return false
        if (!NameplateExtraction.textVisible(text, SETTLED)) {
            return false
        }
        return DisplayNameLine.find(other.cacheDisplay(splitter).lines(), profileName) != DisplayNameLine.NONE
    }

    private fun split(input: Component, width: Int): Display.TextDisplay.CachedInfo {
        val font = Minecraft.getInstance().font
        val lines = font.split(input, width)
        val result = ArrayList<Display.TextDisplay.CachedLine>(lines.size)
        var widest = 0
        for (line in lines) {
            val lineWidth = font.width(line)
            widest = maxOf(widest, lineWidth)
            result.add(Display.TextDisplay.CachedLine(line, lineWidth))
        }
        return Display.TextDisplay.CachedInfo(result, widest)
    }
}

object DisplayNameLine {
    const val NONE: Int = -1

    private val plain = StringBuilder()
    private val sink = FormattedCharSink { _, style, codepoint ->
        if (NameplateText.isDefaultFont(style)) {
            plain.appendCodePoint(codepoint)
        }
        true
    }

    fun find(lines: List<Display.TextDisplay.CachedLine>, profileName: String?): Int {
        if (profileName.isNullOrEmpty()) {
            return NONE
        }

        for (line in lines.indices.reversed()) {
            plain.setLength(0)
            lines[line].contents().accept(sink)
            if (lastWhole(plain, profileName) >= 0) {
                return line
            }
        }
        return NONE
    }

    fun lastIndexOf(text: CharSequence, name: String): Int {
        val whole = lastWhole(text, name)
        return if (whole >= 0) whole else text.lastIndexOf(name)
    }

    private fun lastWhole(text: CharSequence, name: String): Int {
        var at = text.lastIndexOf(name)
        while (at >= 0) {
            val end = at + name.length
            if ((at == 0 || !isNameChar(text[at - 1])) && (end == text.length || !isNameChar(text[end]))) {
                return at
            }
            at = if (at == 0) -1 else text.lastIndexOf(name, at - 1)
        }
        return NONE
    }

    private fun isNameChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'
}
//?}
