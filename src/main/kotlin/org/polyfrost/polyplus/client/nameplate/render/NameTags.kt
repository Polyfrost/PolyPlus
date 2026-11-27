package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.10 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.player.Player
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.nameplate.NameplateBundle
import org.polyfrost.polyplus.client.nameplate.NameplatePolyNametag
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
//?} elif > 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.nameplate.NameplatePolyNametag
import org.polyfrost.polyplus.client.nameplate.engine.NameplateAtlas
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BADGE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BEHIND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_TEXT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_FLAGS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_RUN_AT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MAX_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_ARGB
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_IMAGE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_RECT
//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.EntityRenderState
//?}
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterialBackend as MaterialDraws
*///?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.TextRenderer
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.entity.Entity
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.nameplate.NameplatePolyNametag
import org.polyfrost.polyplus.client.nameplate.engine.NameplateAtlas
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterialBackend
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BADGE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BEHIND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_TEXT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_FLAGS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_RUN_AT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MAX_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_ARGB
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_IMAGE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_RECT
*///?}

//? if > 1.8.9 {
interface NameTagState {
    fun `polyplus$nameTag`(): NameTagBinding?

    fun `polyplus$setNameTag`(binding: NameTagBinding?)
}
//?}

//? if >= 1.21.10 {
internal class NameTagPending {
    val tag = NameplateDrawTag()
    val list = NameplateDrawList()
    val layout = NameTagLayout()
    val matrix = Matrix4f()
    var collector: SubmitNodeCollector? = null
    var component: Component? = null
    var sequence: FormattedCharSequence? = null
    var x = 0f
    var y = 0f
    var color = 0
    var light = 0
    var dy = 0f
    var textColor = 0
    var shadow = false
    var display = false
    var hidden = false
    var drawn = false
    var yields = false
    var orderEnd = NameTagCanvas.ORDER
    var textX = 0f
    var textY = 0f
    var mode: Font.DisplayMode = Font.DisplayMode.NORMAL

    fun text(): FormattedCharSequence? = sequence ?: component?.visualOrderText

    fun release() {
        collector = null
        component = null
        sequence = null
        display = false
        hidden = false
        drawn = false
        yields = false
        tag.equip = null
        tag.signals = null
    }
}
//?}

object NameTags {
    //? if >= 1.21.10 {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val HIDDEN_ALPHA_MASK = 0x00FFFFFF
    private const val VANILLA_BACKGROUND_OPACITY = 0.25f
    private const val PLATE_OPACITY = 0.7f
    private const val MAX_BINDINGS = 256
    private const val RANKED_TAGS = 32

    private val bindings = object : LinkedHashMap<UUID, NameTagBinding>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, NameTagBinding>): Boolean = size > MAX_BINDINGS
    }

    private val failedBundles: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())
    private val pending = ArrayList<NameTagPending>()
    private val ranked = ArrayList<NameTagPending>()
    private val nearest = Comparator<NameTagPending> { a, b -> a.tag.distanceSq.compareTo(b.tag.distanceSq) }
    private var used = 0
    private var current: NameTagPending? = null
    private var captured = false
    private var seeThroughSeen = false

    @JvmStatic
    var rewrittenColor: Int = 0
        private set

    @JvmStatic
    var rewrittenBackground: Int = 0
        private set

    fun failed(bundle: NameplateBundle): Boolean = bundle in failedBundles

    @JvmStatic
    fun bind(entity: Avatar, state: Any) {
        val access = state as? NameTagState ?: return
        access.`polyplus$setNameTag`(binding(entity))
    }

    private fun binding(entity: Avatar): NameTagBinding? {
        if (entity !is Player) return null
        val wearer = entity.uuid
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, wearer)) return null
        val equip = NameplateWearers.resolve(wearer) ?: return null
        if (equip.bundle in failedBundles) return null
        val name = entity.scoreboardName
        val own = entity === Minecraft.getInstance().player
        val cached = bindings[wearer]
        if (cached != null && cached.equip === equip && cached.own == own && cached.profileName == name) return cached
        return NameTagBinding(wearer, name, equip, own).also { bindings[wearer] = it }
    }

    @JvmStatic
    fun beginName(state: EntityRenderState, collector: SubmitNodeCollector): Any? {
        val binding = (state as? NameTagState)?.`polyplus$nameTag`()
        if (binding == null || current != null) return null
        val entry = next()
        if (!prepare(entry, binding, state)) {
            entry.release()
            return null
        }

        entry.collector = collector
        current = entry
        captured = false
        seeThroughSeen = false
        entry.yields = entry.list.ownsBackground
        if (entry.yields) NameplatePolyNametag.yieldBackground(true)
        return entry
    }

    @JvmStatic
    fun endName(handle: Any?, collector: SubmitNodeCollector, completed: Boolean) {
        val entry = handle as? NameTagPending ?: return
        current = null
        if (entry.yields) NameplatePolyNametag.yieldBackground(false)
        if (!completed) {
            entry.release()
            return
        }

        if (!captured) {
            entry.release()
            return
        }
        try {
            measure(entry)
        } catch (e: RuntimeException) {
            fail(entry, e)
            if (entry.list.restyle) {
                entry.textColor = entry.color
                entry.dy = 0f
                restoreText(entry)
            }
            entry.release()
            return
        }
        used++
        if (!NameTagCanvas.inFrame(collector)) finish(entry)
    }

    @JvmStatic
    fun observe(
        seeThrough: Boolean,
        pose: Matrix4fc,
        x: Float,
        y: Float,
        component: Component?,
        sequence: FormattedCharSequence?,
        color: Int,
        background: Int,
        lightCoords: Int,
    ): Boolean {
        val entry = current ?: return false
        var hide = false
        if (seeThrough) {
            if (seeThroughSeen) return false
            seeThroughSeen = true
        } else {
            if (captured) return false
            captured = true
            entry.matrix.set(pose)
            entry.x = x
            entry.y = y
            entry.component = component
            entry.sequence = sequence
            entry.color = color
            entry.light = lightCoords
            hide = entry.list.restyle
        }

        rewrittenColor = if (hide) color and HIDDEN_ALPHA_MASK else color
        rewrittenBackground = if (entry.list.ownsBackground) 0 else background
        return rewrittenColor != color || rewrittenBackground != background
    }

    @JvmStatic
    fun flushTags() {
        try {
            NameplateDrawSources.current.flush()
        } catch (e: RuntimeException) {
            LOGGER.error("Nameplates could not run a frame", e)
        }
        var tail = 1
        for (i in 0 until used) {
            val entry = pending[i]
            entry.orderEnd = NameTagCanvas.ORDER
            entry.drawn = draw(entry)
            if (!entry.drawn) continue
            if (entry.list.materialCount > 0) ranked += entry else tail = maxOf(tail, NameTagCanvas.orderSpan(entry.list))
        }
        rank(tail)
        for (i in 0 until used) {
            val entry = pending[i]
            if (entry.drawn) submit(entry)
        }
    }

    private fun rank(tail: Int) {
        if (ranked.isEmpty()) return
        ranked.sortWith(nearest)
        var end = NameTagCanvas.ORDER - tail
        val unique = minOf(ranked.size, RANKED_TAGS)
        for (i in 0 until unique) {
            val entry = ranked[i]
            entry.orderEnd = end
            end -= NameTagCanvas.orderSpan(entry.list)
        }
        for (i in unique until ranked.size) ranked[i].orderEnd = end
        ranked.clear()
    }

    @JvmStatic
    fun endFrame() {
        for (i in 0 until used) pending[i].release()
        used = 0
        current = null
        DisplayTags.endFrame()
    }

    internal fun beginDisplay(binding: DisplayTagBinding): NameTagPending? {
        if (current != null || binding.equip.bundle in failedBundles) return null
        val entry = next()
        entry.display = true
        DisplayTags.prepare(binding, entry.tag, true)
        val drawn = try {
            NameplateDrawSources.current.prepare(entry.tag, entry.list)
        } catch (e: RuntimeException) {
            fail(entry, e)
            false
        }
        if (!drawn) {
            entry.release()
            return null
        }
        return entry
    }

    internal fun measuredDisplay(entry: NameTagPending, collector: SubmitNodeCollector) {
        entry.collector = collector
        try {
            NameplateDrawSources.current.measured(entry.tag)
        } catch (e: RuntimeException) {
            fail(entry, e)
            if (entry.hidden) restoreDisplayText(entry)
            entry.release()
            return
        }
        used++
        if (!NameTagCanvas.inFrame(collector)) finish(entry)
    }

    internal fun peekDisplay(entry: NameTagPending): Boolean = try {
        NameplateDrawSources.current.peek(entry.tag, entry.list)
    } catch (e: RuntimeException) {
        fail(entry, e)
        false
    }

    internal fun failDisplay(entry: NameTagPending, e: Throwable) {
        fail(entry, e)
        entry.release()
    }

    private fun next(): NameTagPending {
        if (used == pending.size) pending += NameTagPending()
        return pending[used]
    }

    private fun prepare(entry: NameTagPending, binding: NameTagBinding, state: EntityRenderState): Boolean {
        val tag = entry.tag
        tag.wearer = binding.wearer
        tag.profileName = binding.profileName
        tag.equip = binding.equip
        tag.flags = if (binding.own) NameplateDrawTag.FLAG_OWN else 0
        tag.badgeShown = PolyPlusBadge.shouldBadge(binding.wearer)
        tag.deferred = true
        tag.distanceSq = if (binding.own) 0.0 else state.distanceToCameraSq
        tag.signals = PlayerSignals.of(state)
        return try {
            NameplateDrawSources.current.prepare(tag, entry.list)
        } catch (e: RuntimeException) {
            fail(entry, e)
            false
        }
    }

    private fun measure(entry: NameTagPending) {
        val text = entry.text() ?: error("name tag text is missing")
        val font = Minecraft.getInstance().font
        val tag = entry.tag
        val styled = NameplatePolyNametag.style(entry.color, font.width(text))
        entry.textColor = if (styled) NameplatePolyNametag.textArgb else entry.color
        entry.shadow = styled && NameplatePolyNametag.shadow
        entry.dy = if (styled) NameplatePolyNametag.dy else 0f
        entry.layout.scan(font, text, tag.profileName, entry.textColor, tag)
        tag.textAlpha = entry.textColor ushr 24
        tag.backgroundX0 = -1
        tag.backgroundY0 = -1
        tag.backgroundX1 = entry.layout.width
        tag.backgroundY1 = font.lineHeight
        if (styled) NameplatePolyNametag.applyTo(tag)
        NameplateDrawSources.current.measured(tag)
    }

    private fun finish(entry: NameTagPending) {
        entry.orderEnd = NameTagCanvas.ORDER
        if (draw(entry)) submit(entry)
    }

    private fun draw(entry: NameTagPending): Boolean {
        if (entry.collector == null) return false
        return try {
            NameplateDrawSources.current.draw(entry.tag, entry.list)
            true
        } catch (e: RuntimeException) {
            failed(entry, e)
            entry.collector = null
            false
        }
    }

    private fun submit(entry: NameTagPending) {
        val collector = entry.collector ?: return
        val list = entry.list
        try {
            if (entry.display) {
                NameTagCanvas.submit(collector, entry.matrix, entry.x, entry.y, entry.light, plateOpacity(), list, entry.orderEnd)
                if (entry.hidden) {
                    list.baseShadow = entry.shadow
                    NameTagText.redraw(
                        collector,
                        entry.matrix,
                        entry.textX + list.plateDx,
                        entry.textY + list.plateDy,
                        entry.textColor,
                        entry.light,
                        entry.layout,
                        list,
                        entry.mode,
                    )
                }
                return
            }
            val y = entry.y + entry.dy
            NameTagCanvas.submit(
                collector,
                entry.matrix,
                entry.x,
                y,
                entry.light,
                plateOpacity(),
                list,
                entry.orderEnd,
            )
            if (list.restyle) {
                list.baseShadow = entry.shadow
                NameTagText.redraw(collector, entry.matrix, entry.x + list.plateDx, y + list.plateDy, entry.textColor, entry.light, entry.layout, list)
            }
        } catch (e: RuntimeException) {
            failed(entry, e)
        } finally {
            entry.collector = null
        }
    }

    private fun failed(entry: NameTagPending, e: RuntimeException) {
        fail(entry, e)
        if (entry.display) {
            if (entry.hidden) restoreDisplayText(entry)
        } else if (entry.list.restyle) {
            restoreText(entry)
        }
    }

    private fun restoreText(entry: NameTagPending) {
        val collector = entry.collector ?: return
        val text = entry.text() ?: return
        try {
            NameTagText.plain(collector, entry.matrix, entry.x, entry.y + entry.dy, text, entry.textColor, entry.light)
        } catch (e: RuntimeException) {
            LOGGER.error("Could not redraw a hidden name tag", e)
        }
    }

    private fun restoreDisplayText(entry: NameTagPending) {
        val collector = entry.collector ?: return
        val text = entry.sequence ?: return
        try {
            NameTagText.plain(collector, entry.matrix, entry.textX, entry.textY, text, entry.textColor, entry.light, entry.mode)
        } catch (e: RuntimeException) {
            LOGGER.error("Could not redraw a hidden server name tag", e)
        }
    }

    @JvmStatic
    fun plateOpacity(): Float {
        val opacity = Minecraft.getInstance().options.getBackgroundOpacity(VANILLA_BACKGROUND_OPACITY)
        return minOf(1f, PLATE_OPACITY * opacity / VANILLA_BACKGROUND_OPACITY)
    }

    private fun fail(entry: NameTagPending, e: Throwable) {
        val bundle = entry.tag.equip?.bundle ?: return
        if (failedBundles.add(bundle)) LOGGER.error("Nameplate {} failed to draw and is off until it reloads", bundle.key, e)
    }
    //?} elif > 1.8.9 {
    /*//? if >= 1.21.8 {
    private const val HIDDEN_ALPHA = 0
    //?} else {
    /^private const val HIDDEN_ALPHA = 4
    ^///?}

    private const val VANILLA_BACKGROUND_OPACITY = 0.25f
    private const val LAYER_STEP = 0.0001f
    private const val BEHIND = -0.02f
    private const val BACKGROUND = -0.01f
    private const val OVER_BACKGROUND = -0.005f
    private const val OVER_TEXT = 0.04f
    private const val BADGE = 0.03f

    private val tag = NameplateDrawTag()
    private val list = NameplateDrawList()
    private val layout = NameTagLayout()

    private var binding: NameTagBinding? = null
    private var captured = false
    private var seeThroughSeen = false
    private var hidden = false
    private var capturedText: FormattedCharSequence? = null
    private var dy = 0f
    private var textColor = 0
    private var shadow = false
    private var deferring = false
    private var deferredLine: Runnable? = null

    @JvmStatic
    var drawColor: Int = 0
        private set

    @JvmStatic
    var drawBackground: Int = 0
        private set

    @JvmStatic
    fun bind(player: AbstractClientPlayer, state: Any) {
        val access = state as? NameTagState ?: return
        access.`polyplus$setNameTag`(binding(player))
    }

    @JvmStatic
    fun beginStateName(state: Any): Boolean {
        val bound = (state as? NameTagState)?.`polyplus$nameTag`()
        //? if >= 1.21.4 {
        val render = state as? EntityRenderState
        return beginName(bound, PlayerSignals.of(state), render?.distanceToCameraSq ?: 0.0)
        //?} else {
        /^return beginName(bound, null, 0.0)
        ^///?}
    }

    @JvmStatic
    fun renderStateScore(state: Any, original: Runnable) {
        scoreLine((state as? NameTagState)?.`polyplus$nameTag`(), original)
    }

    @JvmStatic
    fun renderEntityScore(entity: Entity, original: Runnable) {
        val player = entity as? AbstractClientPlayer
        scoreLine(if (player == null) null else binding(player), original)
    }

    @JvmStatic
    fun deferLine(seeThrough: Boolean): Boolean = deferring && !seeThrough && deferredLine == null

    @JvmStatic
    fun defer(draw: Runnable) {
        deferredLine = draw
    }

    private fun scoreLine(bound: NameTagBinding?, original: Runnable) {
        if (bound == null || binding != null || deferring || !NameTagSupport.usable(bound)) {
            original.run()
            return
        }
        deferredLine = null
        deferring = true
        try {
            original.run()
        } finally {
            deferring = false
        }
    }

    private fun replayLine() {
        val draw = deferredLine ?: return
        deferredLine = null
        draw.run()
    }

    @JvmStatic
    fun beginEntityName(entity: Entity): Boolean {
        val player = entity as? AbstractClientPlayer ?: return false
        //? if = 1.21.1 {
        /^val signals = PlayerSignals.sample(player)
        ^///?} else {
        val signals: PlayerSignalRecord? = null
        //?}
        val distance = Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(entity)
        return beginName(binding(player), signals, distance)
    }

    private fun binding(player: AbstractClientPlayer): NameTagBinding? =
        NameTagSupport.binding(player.uuid, player.scoreboardName, player === Minecraft.getInstance().player)

    @JvmStatic
    fun endName(started: Boolean) {
        try {
            if (started) release()
        } finally {
            replayLine()
        }
    }

    private fun beginName(bound: NameTagBinding?, signals: PlayerSignalRecord?, distanceSq: Double): Boolean {
        if (bound == null || binding != null || !NameTagSupport.usable(bound) || !NameTagSupport.prepare(bound, tag, list, signals, distanceSq)) {
            return false
        }

        binding = bound
        captured = false
        seeThroughSeen = false
        hidden = false
        return true
    }

    @JvmStatic
    fun observe(
        font: Font,
        text: Component,
        x: Float,
        y: Float,
        color: Int,
        seeThrough: Boolean,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        background: Int,
        light: Int,
    ): Boolean {
        val bound = binding ?: return false
        hidden = false
        if (seeThrough) {
            if (seeThroughSeen) return false
            seeThroughSeen = true
            drawColor = color
            drawBackground = if (list.ownsBackground) 0 else background
            return drawBackground != background
        }
        if (captured) return false
        captured = true

        try {
            decorate(font, text, x, y, color, pose, buffers, light)
        } catch (e: RuntimeException) {
            NameTagSupport.fail(bound, e)
            list.restyle = false
        }
        hidden = list.restyle
        drawColor = if (hidden) NameTagSupport.hide(color, HIDDEN_ALPHA) else color
        drawBackground = if (list.ownsBackground) 0 else background
        return drawColor != color || drawBackground != background
    }

    @JvmStatic
    fun afterName(font: Font, x: Float, y: Float, color: Int, pose: Matrix4f, buffers: MultiBufferSource, light: Int) {
        if (!hidden) return
        hidden = false
        val bound = binding ?: return
        try {
            list.baseShadow = shadow
            NameTagText.redraw(font, pose, buffers, x + list.plateDx, y + dy + list.plateDy, textColor, light, layout, list)
        } catch (e: RuntimeException) {
            NameTagSupport.fail(bound, e)
            val text = capturedText ?: return
            try {
                NameTagText.plain(font, text, x, y + dy, textColor, pose, buffers, light)
            } catch (again: RuntimeException) {
                NameTagSupport.error("Could not redraw a hidden name tag", again)
            }
        }
    }

    @JvmStatic
    fun endFrame() {
        release()
        deferring = false
        deferredLine = null
        MaterialDraws.endFrame()
        NameTagSupport.endFrame()
    }

    @JvmStatic
    fun plateAlpha(): Float {
        val opacity = Minecraft.getInstance().options.getBackgroundOpacity(VANILLA_BACKGROUND_OPACITY)
        return NameTagSupport.plateAlpha(NameTagSupport.PLATE_OPACITY * opacity / VANILLA_BACKGROUND_OPACITY)
    }

    private fun decorate(font: Font, text: Component, x: Float, y: Float, color: Int, pose: Matrix4f, buffers: MultiBufferSource, light: Int) {
        val sequence = text.visualOrderText
        capturedText = sequence
        val styled = NameplatePolyNametag.style(color, font.width(sequence))
        textColor = if (styled) NameplatePolyNametag.textArgb else color
        shadow = styled && NameplatePolyNametag.shadow
        dy = if (styled) NameplatePolyNametag.dy else 0f
        layout.scan(font, sequence, tag.profileName, textColor, tag)
        tag.textAlpha = textColor ushr 24
        tag.backgroundX0 = -1
        tag.backgroundY0 = -1
        tag.backgroundX1 = layout.width
        tag.backgroundY1 = font.lineHeight
        if (styled) NameplatePolyNametag.applyTo(tag)
        NameTagSupport.measureAndDraw(tag, list)
        drawList(list, pose, buffers, x, y + dy, light)
    }

    @JvmStatic
    fun drawList(list: NameplateDrawList, pose: Matrix4f, buffers: MultiBufferSource, x: Float, y: Float, light: Int) {
        val alpha = plateAlpha()
        if (alpha <= 0f) return
        var run = 0
        for (k in 0 until list.materialCount) {
            val runTo = list.materialWord(k, MATERIAL_RUN_AT)
            emitRuns(list, pose, buffers, x, y, light, alpha, run, runTo)
            run = runTo
            MaterialDraws.submit(buffers, pose, list, k, x, y, depth(list.materialWord(k, MATERIAL_BAND_LAYER)), light, alpha)
        }
        emitRuns(list, pose, buffers, x, y, light, alpha, run, list.runCount)
    }

    private fun emitRuns(list: NameplateDrawList, pose: Matrix4f, buffers: MultiBufferSource, x: Float, y: Float, light: Int, alpha: Float, from: Int, to: Int) {
        for (run in from until to) {
            val start = list.runFrom(run)
            val end = list.runTo(run)
            when (list.runKind(run)) {
                RUN_RECT -> emit(list, pose, buffers, x, y, light, alpha, start, end)
                RUN_IMAGE -> emitImages(list, pose, buffers, RenderType.text(NameplateAtlas.ID), x, y, light, alpha, start, end)
                else -> emitImages(list, pose, buffers, MaterialDraws.additiveImages, x, y, light, alpha, start, end)
            }
        }
    }

    private fun emit(list: NameplateDrawList, pose: Matrix4f, buffers: MultiBufferSource, x: Float, y: Float, light: Int, alpha: Float, from: Int, to: Int) {
        if (to <= from) return
        val ops = list.ops
        val sink = buffers.getBuffer(RenderType.textBackground())
        for (op in from until to) {
            val at = op * OP_WORDS
            val argb = NameplateDrawList.fade(ops[at + OP_ARGB], alpha)
            if (argb ushr 24 == 0) continue
            val z = depth(ops[at + OP_BAND_LAYER])
            val x0 = x + ops[at + OP_X0]
            val y0 = y + ops[at + OP_Y0]
            val x1 = x + ops[at + OP_X1]
            val y1 = y + ops[at + OP_Y1]
            sink.addVertex(pose, x0, y1, z).setColor(argb).setLight(light)
            sink.addVertex(pose, x1, y1, z).setColor(argb).setLight(light)
            sink.addVertex(pose, x1, y0, z).setColor(argb).setLight(light)
            sink.addVertex(pose, x0, y0, z).setColor(argb).setLight(light)
        }
    }

    private fun emitImages(
        list: NameplateDrawList,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        type: RenderType,
        x: Float,
        y: Float,
        light: Int,
        alpha: Float,
        from: Int,
        to: Int,
    ) {
        if (to <= from) return
        val images = list.images
        val size = NameplateAtlas.size.toFloat()
        val sink = buffers.getBuffer(type)
        for (i in from until to) {
            val at = i * IMAGE_WORDS
            val argb = NameplateDrawList.imageColor(images[at + IMAGE_FLAGS], images[at + OP_ARGB], alpha)
            if (argb ushr 24 == 0) continue
            val z = depth(images[at + OP_BAND_LAYER])
            val x0 = x + images[at + OP_X0]
            val y0 = y + images[at + OP_Y0]
            val x1 = x + images[at + OP_X1]
            val y1 = y + images[at + OP_Y1]
            val u0 = images[at + IMAGE_U0] / size
            val v0 = images[at + IMAGE_V0] / size
            val u1 = images[at + IMAGE_U1] / size
            val v1 = images[at + IMAGE_V1] / size
            sink.addVertex(pose, x0, y1, z).setColor(argb).setUv(u0, v1).setLight(light)
            sink.addVertex(pose, x1, y1, z).setColor(argb).setUv(u1, v1).setLight(light)
            sink.addVertex(pose, x1, y0, z).setColor(argb).setUv(u1, v0).setLight(light)
            sink.addVertex(pose, x0, y0, z).setColor(argb).setUv(u0, v0).setLight(light)
        }
    }

    private fun release() {
        binding = null
        hidden = false
        capturedText = null
        NameTagSupport.release(tag)
    }
    *///?} else {
    /*private const val HIDDEN_ALPHA = 4
    private const val BACKGROUND_BOTTOM = 8
    private const val FRONT_PERSPECTIVE = 2
    private const val TOWARD_CAMERA = -1f
    private const val GL_SRC_ALPHA = 770
    private const val GL_ONE_MINUS_SRC_ALPHA = 771
    private const val GL_ONE = 1
    private const val GL_ZERO = 0

    private const val LAYER_STEP = 0.0001f * TOWARD_CAMERA
    private const val BEHIND = -0.02f * TOWARD_CAMERA
    private const val BACKGROUND = -0.01f * TOWARD_CAMERA
    private const val OVER_BACKGROUND = -0.005f * TOWARD_CAMERA
    private const val OVER_TEXT = 0.04f * TOWARD_CAMERA
    private const val BADGE = 0.03f * TOWARD_CAMERA

    private val tag = NameplateDrawTag()
    private val list = NameplateDrawList()

    private var nameLineOf: Entity? = null
    private var current: NameTagBinding? = null
    private var sneaking = false
    private var lowered = false
    private var textRgb = 0
    private var shownAlpha = 0xFF
    private var shadow = false
    private var dy = 0f

    @JvmStatic
    fun enterNameLine(entity: Entity): Entity? {
        val previous = nameLineOf
        nameLineOf = entity
        return previous
    }

    @JvmStatic
    fun leaveNameLine(previous: Entity?) {
        nameLineOf = previous
    }

    @JvmStatic
    fun begin(entity: Entity) {
        release()
        if (entity !== nameLineOf) return
        nameLineOf = null
        val lowered = NameplatePolyNametag.lowersSneaking(entity)
        bind(entity, lowered, lowered)
    }

    @JvmStatic
    fun beginSneaking(entity: Entity) {
        release()
        bind(entity, true, false)
    }

    @JvmStatic
    fun bindable(entity: Entity): Boolean {
        val bound = binding(entity) ?: return false
        return NameTagSupport.usable(bound)
    }

    @JvmStatic
    fun cameraPitch(pitch: Float): Float {
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        if (dispatcher.options?.perspective != FRONT_PERSPECTIVE) return pitch
        val cached = dispatcher.cameraPitch
        if (pitch != cached && pitch != -cached) return pitch
        val camera = dispatcher.camera ?: return if (pitch == cached) -pitch else pitch
        val partial = FirstPersonEffects.partialTick(mc)
        return FrontViewPitch.facing(cached, camera.lastPitch + (camera.pitch - camera.lastPitch) * partial)
    }

    @JvmStatic
    fun backgroundAlpha(alpha: Float): Float = if (current != null && list.ownsBackground) 0f else alpha

    @JvmStatic
    fun drawSeeThroughName(font: TextRenderer, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        if (current == null) return original.call(font, text, x, y, color)
        if (lowered) return drawName(font, text, x, y, color, original)
        PolyPlusBadge.inlineHidden = tag.badgeShown
        try {
            return original.call(font, text, x, y, color)
        } finally {
            PolyPlusBadge.inlineHidden = false
        }
    }

    @JvmStatic
    fun drawName(font: TextRenderer, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        val bound = current ?: return original.call(font, text, x, y, color)
        current = null
        val shaderText = LegacyCeraShaders.beginText()
        try {
            return drawBound(bound, font, text, x, y, color, original)
        } finally {
            LegacyCeraShaders.endText(shaderText)
        }
    }

    private fun drawBound(bound: NameTagBinding, font: TextRenderer, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        NameplatePolyNametag.yieldBackground(false)
        val layout = try {
            decorate(bound, font, text, x.toFloat(), y.toFloat(), color)
        } catch (e: RuntimeException) {
            NameTagSupport.fail(bound, e)
            NameTagSupport.release(tag)
            return original.call(font, text, x, y, color)
        }

        PolyPlusBadge.inlineHidden = tag.badgeShown
        try {
            if (!NameTagSupport.redrawsText(list.restyle, shownAlpha)) return original.call(font, text, x, y, color)
            val advance = original.call(font, text, x, y, NameTagSupport.hide(color, HIDDEN_ALPHA))
            val shown = (shownAlpha shl 24) or textRgb
            val top = y + dy
            try {
                list.baseShadow = shadow
                NameTagText.redraw(font, layout, list, x.toFloat() + list.plateDx, top + list.plateDy, shown)
            } catch (e: RuntimeException) {
                NameTagSupport.fail(bound, e)
                try {
                    NameTagText.plain(font, text, x.toFloat(), top, shown)
                } catch (again: RuntimeException) {
                    NameTagSupport.error("Could not redraw a hidden name tag", again)
                }
            }
            return advance
        } finally {
            PolyPlusBadge.inlineHidden = false
            NameTagSupport.release(tag)
        }
    }

    @JvmStatic
    fun release() {
        current = null
        NameplatePolyNametag.yieldBackground(false)
        NameTagSupport.release(tag)
    }

    @JvmStatic
    fun endFrame() {
        release()
        nameLineOf = null
        NameTagSupport.endFrame()
    }

    private fun bind(entity: Entity, sneak: Boolean, lowered: Boolean) {
        val bound = binding(entity) ?: return
        if (!NameTagSupport.usable(bound)) return
        val player = entity as ClientPlayerEntity
        val signals = PlayerSignals.sample(player)
        val viewer = Minecraft.getInstance().player
        val distance = if (viewer == null) 0.0 else viewer.squaredDistanceTo(entity)
        if (!NameTagSupport.prepare(bound, tag, list, signals, distance)) return
        current = bound
        sneaking = sneak
        this.lowered = lowered
        if (list.ownsBackground) NameplatePolyNametag.yieldBackground(true)
    }

    private fun binding(entity: Entity): NameTagBinding? {
        val player = entity as? ClientPlayerEntity ?: return null
        return NameTagSupport.binding(player.uuid, player.name, player === Minecraft.getInstance().player)
    }

    private fun decorate(bound: NameTagBinding, font: TextRenderer, text: String, x: Float, y: Float, color: Int): NameTagLayout {
        val layout = NameTagText.layout(font, text, tag.profileName)
        val dimmed = sneaking && !lowered
        val styled = !dimmed && NameplatePolyNametag.style(color, layout.width)
        textRgb = (if (styled) NameplatePolyNametag.textArgb else color) and RGB
        shownAlpha = NameTagSupport.legacyPassAlpha(if (styled) NameplatePolyNametag.textArgb else color)
        shadow = styled && NameplatePolyNametag.shadow
        dy = if (styled) NameplatePolyNametag.dy else 0f
        layout.measure(tag, textRgb)
        tag.backgroundX0 = -1
        tag.backgroundY0 = -1
        tag.backgroundX1 = 2 * (layout.width / 2) + 1
        tag.backgroundY1 = BACKGROUND_BOTTOM
        if (styled) NameplatePolyNametag.applyTo(tag)
        val alpha = NameTagSupport.plateAlpha(NameTagSupport.PLATE_OPACITY)
        if (dimmed) {
            NameplateDrawSources.current.draw(tag, list)
            shownAlpha = sneakAlpha(bound, alpha)
            tag.textAlpha = shownAlpha
            NameplateDrawSources.current.measured(tag)
        } else {
            tag.textAlpha = shownAlpha
            NameTagSupport.measureAndDraw(tag, list)
        }
        bound.rowAlpha = tag.textAlpha
        if (alpha <= 0f) return layout
        drawList(x, y + dy, alpha)
        return layout
    }

    private fun sneakAlpha(bound: NameTagBinding, plateAlpha: Float): Int {
        val plateX = list.plateDx
        val plateY = list.plateDy
        val fresh = list.opCount + list.imageCount + list.materialCount > 0 || bound.sneakAlpha == 0
        if (fresh) {
            bound.sneakAlpha = LegacySneakAlpha.of(
                list,
                tag.backgroundX0 + plateX,
                tag.backgroundY0 + plateY,
                tag.backgroundX1 + plateX,
                tag.backgroundY1 + plateY,
                bound.rowAlpha,
                plateAlpha,
                !list.ownsBackground,
            )
        }
        return bound.sneakAlpha
    }

    private fun drawList(x: Float, y: Float, alpha: Float) {
        var run = 0
        for (k in 0 until list.materialCount) {
            val runTo = list.materialWord(k, MATERIAL_RUN_AT)
            emitRuns(x, y, alpha, run, runTo)
            run = runTo
            NameplateMaterialBackend.draw(list, k, x, y, depth(list.materialWord(k, MATERIAL_BAND_LAYER)), alpha, sneaking)
        }
        emitRuns(x, y, alpha, run, list.runCount)
    }

    private fun emitRuns(x: Float, y: Float, alpha: Float, from: Int, to: Int) {
        for (run in from until to) {
            val start = list.runFrom(run)
            val end = list.runTo(run)
            when (list.runKind(run)) {
                RUN_RECT -> emit(x, y, alpha, start, end)
                RUN_IMAGE -> emitImages(x, y, alpha, start, end, false)
                else -> emitAdditive(x, y, alpha, start, end)
            }
        }
    }

    private fun emit(x: Float, y: Float, alpha: Float, from: Int, to: Int) {
        if (to <= from) return
        val ops = list.ops
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.buffer
        var began = false
        var layer = 0
        try {
            for (op in from until to) {
                val at = op * OP_WORDS
                val argb = NameplateDrawList.fade(ops[at + OP_ARGB], alpha)
                val a = argb ushr 24
                if (a == 0) continue
                val word = ops[at + OP_BAND_LAYER]
                if (!began) {
                    LegacyCeraShaders.bindWhite()
                    GlStateManager.disableTexture()
                    GlStateManager.disableAlphaTest()
                    layer = layerOf(word)
                    offsetLayer(layer)
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR)
                    began = true
                } else if (layerOf(word) != layer) {
                    tesselator.end()
                    layer = layerOf(word)
                    offsetLayer(layer)
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR)
                }
                val z = depth(word).toDouble()
                val x0 = (x + ops[at + OP_X0]).toDouble()
                val y0 = (y + ops[at + OP_Y0]).toDouble()
                val x1 = (x + ops[at + OP_X1]).toDouble()
                val y1 = (y + ops[at + OP_Y1]).toDouble()
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                buffer.vertex(x0, y0, z).color(r, g, b, a).nextVertex()
                buffer.vertex(x0, y1, z).color(r, g, b, a).nextVertex()
                buffer.vertex(x1, y1, z).color(r, g, b, a).nextVertex()
                buffer.vertex(x1, y0, z).color(r, g, b, a).nextVertex()
            }
        } finally {
            if (began) {
                tesselator.end()
                clearLayerOffset()
                GlStateManager.enableAlphaTest()
                GlStateManager.enableTexture()
            }
        }
    }

    private fun emitAdditive(x: Float, y: Float, alpha: Float, from: Int, to: Int) {
        GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE)
        try {
            emitImages(x, y, alpha, from, to, true)
        } finally {
            GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        }
    }

    private fun emitImages(x: Float, y: Float, alpha: Float, from: Int, to: Int, additive: Boolean) {
        if (to <= from) return
        val images = list.images
        val size = NameplateAtlas.size.toDouble()
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.buffer
        Minecraft.getInstance().textureManager.bind(NameplateAtlas.ID)
        if (additive) GlStateManager.disableAlphaTest()
        var layer = 0
        offsetLayer(layer)
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        try {
            for (i in from until to) {
                val at = i * IMAGE_WORDS
                val argb = NameplateDrawList.imageColor(images[at + IMAGE_FLAGS], images[at + OP_ARGB], alpha)
                val a = argb ushr 24
                if (a == 0) continue
                val word = images[at + OP_BAND_LAYER]
                if (layerOf(word) != layer) {
                    tesselator.end()
                    layer = layerOf(word)
                    offsetLayer(layer)
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                }
                val z = depth(word).toDouble()
                val x0 = (x + images[at + OP_X0]).toDouble()
                val y0 = (y + images[at + OP_Y0]).toDouble()
                val x1 = (x + images[at + OP_X1]).toDouble()
                val y1 = (y + images[at + OP_Y1]).toDouble()
                val u0 = images[at + IMAGE_U0] / size
                val v0 = images[at + IMAGE_V0] / size
                val u1 = images[at + IMAGE_U1] / size
                val v1 = images[at + IMAGE_V1] / size
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                buffer.vertex(x0, y0, z).texture(u0, v0).color(r, g, b, a).nextVertex()
                buffer.vertex(x0, y1, z).texture(u0, v1).color(r, g, b, a).nextVertex()
                buffer.vertex(x1, y1, z).texture(u1, v1).color(r, g, b, a).nextVertex()
                buffer.vertex(x1, y0, z).texture(u1, v0).color(r, g, b, a).nextVertex()
            }
        } finally {
            tesselator.end()
            clearLayerOffset()
            if (additive) GlStateManager.enableAlphaTest()
        }
    }

    private fun layerOf(word: Int): Int = minOf((word shr 8) and 0xFF, MAX_LAYER)

    private fun offsetLayer(layer: Int) {
        if (layer == 0) {
            clearLayerOffset()
            return
        }
        GlStateManager.enablePolygonOffset()
        GlStateManager.polygonOffset(-layer.toFloat(), -layer.toFloat())
    }

    private fun clearLayerOffset() {
        GlStateManager.polygonOffset(0f, 0f)
        GlStateManager.disablePolygonOffset()
    }

    private const val RGB = 0xFFFFFF
    *///?}

    //? if < 1.21.10 {
    /*private fun depth(word: Int): Float {
        val band = when (word and 0xFF) {
            BAND_BEHIND -> BEHIND
            BAND_BACKGROUND -> BACKGROUND
            BAND_OVER_BACKGROUND -> OVER_BACKGROUND
            BAND_OVER_TEXT -> OVER_TEXT
            BAND_BADGE -> BADGE
            else -> BACKGROUND
        }
        return band + minOf((word shr 8) and 0xFF, MAX_LAYER) * LAYER_STEP
    }
    *///?}
}
