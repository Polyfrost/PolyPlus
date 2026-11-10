package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.entity.Entity
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.render.NameplateClock
import org.polyfrost.polyplus.client.nameplate.render.NameplateDetail
import org.polyfrost.polyplus.client.nameplate.render.NameplateGate
import org.polyfrost.polyplus.client.nameplate.render.NameplatePaint
import org.polyfrost.polyplus.client.nameplate.render.NameplateReactions
import org.polyfrost.polyplus.client.nameplate.render.NameplateTag
import org.polyfrost.polyplus.client.nameplate.render.PolyNametagPadding
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import java.util.WeakHashMap

class LegacyTag internal constructor(
    val entity: Entity?,
    val tag: NameplateTag,
    val line: LegacyLine,
    val block: TagBlock,
    val mode: NameplateBundle.TextMode,
    val style: LegacyStyle?,
    val outline: LegacyNameplateText.Outline,
) {
    var x: Float = 0f
        internal set
    var y: Float = 0f
        internal set
    var gapX: Float = -1f
        internal set
    var polyNametagSneak: Boolean = false
        internal set
    var plateX: Int = 0
        internal set
    var plateY: Int = 0
        internal set
}

object LegacyNameplateBinding {
    private const val DEADMAU5 = "deadmau5"
    private const val DEADMAU5_Y = -10f
    private const val BADGE_PAD = 1f
    private const val WHITE_RGB = 0xFFFFFF

    private class DisplayName(val time: Long, val text: String)

    private val names = WeakHashMap<Entity, DisplayName>()

    fun bind(entity: Entity, name: String, sneakPath: Boolean): LegacyTag? {
        if (entity !is ClientPlayerEntity || !NameplateGate.active()) {
            return null
        }
        val nameplate = NameplateGate.bound(entity.uuid) ?: return null
        if (!isNameLine(entity, name)) {
            return null
        }
        return try {
            build(entity, name, nameplate, sneakPath)
        } catch (e: RuntimeException) {
            NameplateGate.fail(nameplate, e)
            null
        }
    }

    fun mode(variant: NameplateBundle.Variant): NameplateBundle.TextMode {
        val settings = NameplateBinding.settings
        return if (settings.animatedNames && settings.motion == NameplateMotion.FULL) variant.textMode else NameplateBundle.TextMode.VANILLA
    }

    private fun isNameLine(entity: Entity, name: String): Boolean {
        val skip = if (name.isNotEmpty() && name[0] == PolyPlusBadge.LEGACY_CHAR) 1 else 0
        val display = displayName(entity)
        return name.length - skip == display.length && name.regionMatches(skip, display, 0, display.length)
    }

    private fun displayName(entity: Entity): String {
        val time = Minecraft.getInstance().world?.time ?: -1L
        val cached = names[entity]
        if (cached != null && cached.time == time) {
            return cached.text
        }
        val text = entity.displayName.formattedString
        names[entity] = DisplayName(time, text)
        return text
    }

    private fun build(entity: ClientPlayerEntity, name: String, nameplate: BoundNameplate, sneakPath: Boolean): LegacyTag {
        LegacyPolyNametag.refresh()
        val minecraft = Minecraft.getInstance()
        val profileName = entity.name
        val frame = NameplateClock.frame(nameplate)
        val state = NameplateReactions.state(entity, nameplate, frame)
        val camera = minecraft.entityRenderDispatcher.camera
        val detailed = NameplateDetail.detailed(entity.networkId, if (camera == null) 0.0 else entity.squaredDistanceTo(camera))
        val tag = NameplateTag(nameplate, profileName, PolyPlusBadge.shouldBadge(entity.uuid), detailed, frame, state)

        val line = LegacyNameplateText.line(minecraft.textRenderer, name, profileName)
        val width = line.textWidth
        val block = TagBlock.line(width, line.nameX, line.nameWidth).placed(line.prefixWidth, background(width))
        val external = LegacyPolyNametag.textRgb
        val text = NameplatePaint.text(nameplate, block, external)
        val mode = mode(nameplate.variant)
        val style = LegacyNameplateText.style(line, text, if (external == ResolvedText.UNSET) WHITE_RGB else external, LegacyPolyNametag.textShadow)
        val keepsVanilla = mode == NameplateBundle.TextMode.VANILLA && !style.changes

        val bound = LegacyTag(entity, tag, line, block, mode, if (keepsVanilla) null else style, LegacyNameplateLetters.outlineOf(text, detailed))
        bound.x = (-(width / 2)).toFloat()
        bound.y = (if (!sneakPath && name == DEADMAU5) DEADMAU5_Y else 0f) + LegacyPolyNametag.offsetY
        bound.gapX = if (line.badgeX >= 0) line.badgeX + BADGE_PAD else -1f
        bound.polyNametagSneak = !sneakPath && LegacyPolyNametag.enabled && entity.isSneaking
        val plate = NameplatePaint.plate(nameplate, block, frame, state)
        bound.plateX = plate.dx
        bound.plateY = plate.dy
        return bound
    }

    private fun background(width: Int): PixelRect =
        if (LegacyPolyNametag.enabled) {
            PolyNametagPadding.backgroundRect(width, TagBlock.GLYPH_HEIGHT)
        } else {
            PixelRect(-1, -1, 2 * (width / 2) + 1, TagBlock.GLYPH_HEIGHT)
        }
}
*///?}
