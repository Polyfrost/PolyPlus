package org.polyfrost.polyplus.client.nameplate.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.ResourceFinishedLoading
import org.polyfrost.plusrenderer.NameplateAsset
import org.polyfrost.plusrenderer.Nameplates
import org.polyfrost.polyplus.client.CosmeticMotion
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.nameplate.NameplateBundle
import org.polyfrost.polyplus.client.nameplate.NameplateBundleListener
import org.polyfrost.polyplus.client.nameplate.NameplateBundles
import org.polyfrost.polyplus.client.nameplate.NameplateEffects
import org.polyfrost.polyplus.client.nameplate.NameplateEquip
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterials
import org.polyfrost.polyplus.client.nameplate.material.NameplatePrograms
import org.polyfrost.polyplus.client.nameplate.material.NameplateSampler
import org.polyfrost.polyplus.client.nameplate.material.NameplateShaderPacks
import org.polyfrost.polyplus.client.nameplate.material.NameplateShaderSet
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawSource
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawTag
import org.polyfrost.polyplus.client.utils.CosmeticClock
import org.polyfrost.polyplus.libs.sentry.SentryLevel
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object EngineNameplateDrawSource : NameplateDrawSource, NameplateBundleListener {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val STALL_SECONDS = 0.5
    private const val IDLE_FRAMES = 1200
    private const val PRUNE_FRAMES = 300
    private const val ISSUE_LIMIT = 50
    private const val RGB = 0xFFFFFF
    private const val SETTINGS_FLAGS = Nameplates.FLAG_ANIMATED_NAMES_OFF or Nameplates.FLAG_EFFECTS_CPU or Nameplates.FLAG_EFFECTS_OFF
    private const val UNIFORM_BODY = 4
    private const val ATLAS_SLOTS = 4
    private const val BADGE_DECORATION_LAYER = Nameplates.BAND_BADGE or (1 shl 8)
    private const val BADGE_ART_LAYER = Nameplates.BAND_BADGE or (2 shl 8)
    private const val BADGE_ART_TONE = 0xD6
    private const val CHANNEL_MAX = 255
    private const val MAX_FALLBACKS = 64
    private const val FALLBACKS_PER_FRAME = 8
    private val NO_WEARER = UUID(0L, 0L)
    private const val UNKNOWN_ENTITY = -1

    private class Meta(
        val variants: Array<String>,
        val textures: List<String>,
        val shaders: NameplateShaderSet?,
        val info: NameplateBundleInfo,
    )

    private class Parsed(val bundle: NameplateBundle, val generation: Int, val asset: NameplateAsset?, val meta: Meta?)

    private class Loaded(val asset: NameplateAsset, val id: Int, val meta: Meta, var programs: NameplatePrograms) {
        fun variant(key: String?): Int {
            if (key == null) return 0
            val at = meta.variants.indexOf(key)
            return if (at < 0) 0 else at
        }
    }

    private class Slot(val id: Long, val display: Boolean) {
        var wearer: UUID = NO_WEARER
        var cosmeticId = 0
        var bundle: NameplateBundle? = null
        var variant = 0
        var flags = 0
        var external = 0
        var deferred = false
        var distanceSq = 0.0
        var detail = Nameplates.DETAIL_FULL
        var badgeShown = false
        var badgeGapX = NameplateDrawTag.NO_GAP
        val row = IntArray(Nameplates.TAG_INTS)
        val glyphs = FloatArray(Nameplates.GLYPH_FLOATS * NameplateDrawTag.MAX_GLYPHS)
        var glyphCount = 0
        val colors = IntArray(NameplateDrawTag.MAX_COLORS)
        var colorCount = 0
        val sectionColors = IntArray(NameplateDrawTag.MAX_COLORS)
        var sectionColorCount = 0
        val signals = FloatArray(Nameplates.SIGNAL_FLOATS)
        var measuredFrame = Int.MIN_VALUE
        var usedFrame = 0
        var sectionFrame = Int.MIN_VALUE
        var sectionAt = 0
        var headKnown = false
        var ownsBackground = false
        var textMode = Nameplates.TEXT_MODE_VANILLA
        var restyle = false
        var appliedColors: Map<String, Int>? = null
        var appliedAsset = 0
    }

    private val parser = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus Nameplate Parser").apply { isDaemon = true }
    }
    private val effects = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus Nameplate Effects").apply { isDaemon = true }
    }
    private val finished = ConcurrentLinkedQueue<Nameplates.Fallback>()
    private val fallbacksOut = AtomicInteger()
    private val fallbackRunFailed = AtomicBoolean()
    private val generations = ConcurrentHashMap<String, Int>()
    private val parsed = ConcurrentLinkedQueue<Parsed>()
    private val released = ConcurrentLinkedQueue<NameplateBundle>()
    private val forgotten = ConcurrentLinkedQueue<Long>()

    @Volatile
    private var reloaded = false

    private val loaded = IdentityHashMap<NameplateBundle, Loaded>()
    private val failed: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())
    private val reported = HashSet<String>()
    private val vanillaTextures = HashSet<String>()
    private val slots = LongObjectMap<Slot>()
    private val displaySlots = LongObjectMap<Slot>()
    private var expired = LongArray(16)
    private var colorSlots = IntArray(8)
    private var colorRgb = IntArray(8)

    private var plates: Nameplates? = null
    private var platesFailed = false
    private var motion = -1

    private var frame = 0
    private var computedFrame = Int.MIN_VALUE
    private var computedClock = 0.0
    private var lastPrune = 0

    private val batch = ArrayList<Slot>()
    private val ranking = ArrayList<Slot>()
    private val nearest = Comparator<Slot> { a, b -> a.distanceSq.compareTo(b.distanceSq) }
    private var wearers = LongArray(16)
    private var tags = IntArray(Nameplates.TAG_INTS * 16)
    private var signals = FloatArray(Nameplates.SIGNAL_FLOATS * 16)
    private var glyphs = FloatArray(Nameplates.GLYPH_FLOATS * 64)
    private var colors = IntArray(64)
    private var out = IntArray(1024)
    private var texels = IntArray(256)
    private val atlasRect = FloatArray(4)
    private var stripLayout = Int.MIN_VALUE
    private val strips = LongObjectMap<IntArray>()

    init {
        NameplateBundles.listener = this
        NameplateBundles.loaded().forEach(::bundleInstalled)
        eventHandler<ResourceFinishedLoading> { reloaded = true }
    }

    override fun bundleInstalled(bundle: NameplateBundle) {
        val generation = generations.merge(bundle.key, 1, Int::plus) ?: 1
        parser.execute { parse(bundle, generation) }
    }

    override fun bundleReleased(bundle: NameplateBundle) {
        generations.merge(bundle.key, 1, Int::plus)
        released.add(bundle)
    }

    override fun wearerForgotten(wearer: UUID) {
        forgotten.add(CosmeticWearers.wearerId(wearer))
    }

    override fun prepare(tag: NameplateDrawTag, out: NameplateDrawList): Boolean {
        out.reset()
        val equip = tag.equip ?: return false
        val bundle = equip.bundle
        if (bundle in failed) return false
        tickFrame()
        NameplateShaderPacks.probe(frame)
        if (NameplateShaderPacks.state() == NameplateShaderPacks.SHADOW) return false
        if (!tag.deferred) ensureFrame()
        val entry = loaded[bundle] ?: return false
        val slot = slot(tag, bundle, entry, equip)
        if (slot.sectionFrame == frame) {
            apply(slot, out)
        } else if (tag.deferred && slot.headKnown) {
            apply(slot, out)
        }
        return true
    }

    override fun measured(tag: NameplateDrawTag) {
        val slot = slotOf(tag) ?: return
        if (slot.bundle !== tag.equip?.bundle) return
        val row = slot.row
        row.fill(0)
        val display = tag.hasFlag(NameplateDrawTag.FLAG_DISPLAY)
        if (display) {
            val lines = tag.lineCount.coerceIn(1, Nameplates.MAX_LINES)
            row[Nameplates.TAG_LINE_COUNT] = lines
            row[Nameplates.TAG_NAME_LINE] = tag.nameLine.coerceIn(0, lines - 1)
            row[Nameplates.TAG_ALIGN] = tag.align
            for (i in 0 until lines) row[Nameplates.TAG_LINE_WIDTH_0 + i] = tag.lineWidths[i]
        } else {
            row[Nameplates.TAG_LINE_COUNT] = 1
            row[Nameplates.TAG_NAME_LINE] = 0
            row[Nameplates.TAG_ALIGN] = Nameplates.ALIGN_CENTER
            row[Nameplates.TAG_LINE_WIDTH_0] = tag.lineWidth
            row[Nameplates.TAG_BG_X0] = tag.backgroundX0
            row[Nameplates.TAG_BG_Y0] = tag.backgroundY0
            row[Nameplates.TAG_BG_X1] = tag.backgroundX1
            row[Nameplates.TAG_BG_Y1] = tag.backgroundY1
        }
        row[Nameplates.TAG_NAME_X] = tag.nameX
        row[Nameplates.TAG_NAME_WIDTH] = tag.nameWidth
        row[Nameplates.TAG_PREFIX_WIDTH] = tag.prefixWidth
        row[Nameplates.TAG_SNEAK_ALPHA] = tag.textAlpha.coerceIn(0, 255)
        if (tag.externalColor != NameplateDrawTag.NO_EXTERNAL) {
            slot.external = Nameplates.FLAG_EXTERNAL_COLOR
            row[Nameplates.TAG_EXTERNAL_COLOR] = tag.externalColor and RGB
        } else {
            slot.external = 0
        }
        slot.badgeGapX = tag.badgeGapX
        slot.glyphCount = tag.glyphCount
        System.arraycopy(tag.glyphs, 0, slot.glyphs, 0, tag.glyphCount * Nameplates.GLYPH_FLOATS)
        slot.colorCount = tag.colorCount
        System.arraycopy(tag.colors, 0, slot.colors, 0, tag.colorCount)
        val record = tag.signals ?: PlayerSignals.NEUTRAL
        val queued = slot.signals[Nameplates.SIGNAL_EVENTS].toInt()
        record.copyInto(slot.signals, 0)
        slot.signals[Nameplates.SIGNAL_EVENTS] = (queued or record.events(PlayerSignals.READER_NAMEPLATES)).toFloat()
        slot.distanceSq = tag.distanceSq
        slot.deferred = tag.deferred
        slot.measuredFrame = frame
    }

    override fun draw(tag: NameplateDrawTag, out: NameplateDrawList) {
        out.clearDrawing()
        out.plateDx = 0
        out.plateDy = 0
        val slot = slotOf(tag) ?: return
        if (slot.sectionFrame != frame || slot.bundle !== tag.equip?.bundle) return
        try {
            translate(slot, out)
        } catch (e: RuntimeException) {
            out.clearDrawing()
            report("translate", "Could not read a nameplate frame", e)
        }
    }

    override fun peek(tag: NameplateDrawTag, out: NameplateDrawList): Boolean {
        out.clearDrawing()
        out.plateDx = 0
        out.plateDy = 0
        val slot = slotOf(tag) ?: return false
        if (slot.bundle !== tag.equip?.bundle) return false
        if (slot.sectionFrame != computedFrame || computedFrame < frame - 1) return false
        return try {
            translate(slot, out)
            true
        } catch (e: RuntimeException) {
            out.clearDrawing()
            report("translate", "Could not read a nameplate frame", e)
            false
        }
    }

    override fun badgePlacement(equip: NameplateEquip): Int {
        val bundle = equip.bundle
        if (bundle in failed) return NameplateDrawTag.BADGE_NONE
        val entry = loaded[bundle] ?: return NameplateDrawTag.BADGE_NONE
        return entry.meta.info.badge(entry.meta.variants.getOrNull(entry.variant(equip.variantKey)))
    }

    private fun slotOf(tag: NameplateDrawTag): Slot? {
        val id = CosmeticWearers.wearerId(tag.wearer)
        return if (tag.hasFlag(NameplateDrawTag.FLAG_DISPLAY)) displaySlots[id] else slots[id]
    }

    private fun slotsFor(display: Boolean): LongObjectMap<Slot> = if (display) displaySlots else slots

    override fun flush() {
        tickFrame()
        ensureFrame()
    }

    override fun endFrame() {
        frame++
    }

    private fun tickFrame() {
        if (computedFrame == frame && CosmeticClock.seconds() - computedClock > STALL_SECONDS) frame++
    }

    private fun slot(tag: NameplateDrawTag, bundle: NameplateBundle, entry: Loaded, equip: NameplateEquip): Slot {
        val id = CosmeticWearers.wearerId(tag.wearer)
        val display = tag.hasFlag(NameplateDrawTag.FLAG_DISPLAY)
        val table = slotsFor(display)
        val slot = table[id] ?: Slot(id, display).also { table[id] = it }
        val variant = entry.variant(equip.variantKey)
        val placement = entry.meta.info.badge(entry.meta.variants.getOrNull(variant))
        val reserved = tag.badgeShown && placement != NameplateBundleInfo.BADGE_PLACED
        val badge = if (reserved) Nameplates.FLAG_BADGE else 0
        val flags = tag.flags and (SETTINGS_FLAGS or Nameplates.FLAG_BADGE or Nameplates.FLAG_EXTERNAL_COLOR).inv() or settingsFlags(entry) or badge
        slot.wearer = tag.wearer
        slot.cosmeticId = equip.cosmeticId
        slot.badgeShown = tag.badgeShown
        if (slot.bundle !== bundle || slot.variant != variant || slot.flags != flags) {
            slot.headKnown = false
            if (slot.bundle !== bundle) slot.sectionFrame = Int.MIN_VALUE
        }
        slot.bundle = bundle
        slot.variant = variant
        slot.flags = flags
        slot.usedFrame = frame
        return slot
    }

    private fun settingsFlags(entry: Loaded): Int {
        var flags = 0
        if (!PolyPlusCosmeticsConfig.nameplateAnimatedNames) flags = flags or Nameplates.FLAG_ANIMATED_NAMES_OFF
        flags = flags or when (PolyPlusCosmeticsConfig.nameplateEffects) {
            NameplateEffects.GPU -> if (entry.programs.gpu && NameplateShaderPacks.gpuAllowed()) 0 else Nameplates.FLAG_EFFECTS_CPU
            NameplateEffects.CPU -> Nameplates.FLAG_EFFECTS_CPU
            NameplateEffects.OFF -> Nameplates.FLAG_EFFECTS_OFF
        }
        return flags
    }

    private fun apply(slot: Slot, out: NameplateDrawList) {
        out.ownsBackground = slot.ownsBackground
        out.textMode = slot.textMode
        out.restyle = slot.restyle
    }

    private fun ensureFrame() {
        if (computedFrame == frame) return
        computedFrame = frame
        computedClock = CosmeticClock.seconds()
        try {
            compute()
        } catch (e: RuntimeException) {
            report("frame", "Nameplates could not run a frame", e)
        } catch (e: LinkageError) {
            platesFailed = true
            plates = null
            report("native", "Nameplates could not reach the native renderer", e)
        }
    }

    private fun compute() {
        drain()
        if (frame - lastPrune >= PRUNE_FRAMES) prune()
        val plates = nameplates() ?: return
        finishFallbacks(plates)
        updateMotion(plates)

        batch.clear()
        collect(slots)
        collect(displaySlots)
        if (batch.isEmpty()) return
        CosmeticWearers.begin()
        rank()

        val count = batch.size
        var glyphTotal = 0
        var colorTotal = 0
        for (i in 0 until count) {
            val slot = batch[i]
            glyphTotal += slot.glyphCount
            colorTotal += slot.colorCount
        }
        if (wearers.size < count) wearers = LongArray(count * 2)
        if (tags.size < count * Nameplates.TAG_INTS) tags = IntArray(count * 2 * Nameplates.TAG_INTS)
        if (signals.size < count * Nameplates.SIGNAL_FLOATS) signals = FloatArray(count * 2 * Nameplates.SIGNAL_FLOATS)
        if (glyphs.size < glyphTotal * Nameplates.GLYPH_FLOATS) glyphs = FloatArray(glyphTotal * 2 * Nameplates.GLYPH_FLOATS)
        if (colors.size < colorTotal) colors = IntArray(colorTotal * 2)

        var glyphAt = 0
        var colorAt = 0
        for (i in 0 until count) {
            val slot = batch[i]
            val entry = loaded[slot.bundle]!!
            val base = i * Nameplates.TAG_INTS
            wearers[i] = slot.id
            applyColors(plates, slot, entry)
            System.arraycopy(slot.row, 0, tags, base, Nameplates.TAG_INTS)
            tags[base + Nameplates.TAG_ASSET] = entry.id
            tags[base + Nameplates.TAG_VARIANT] = slot.variant
            tags[base + Nameplates.TAG_FLAGS] = slot.flags or slot.external
            slot.sectionColorCount = slot.colorCount
            System.arraycopy(slot.colors, 0, slot.sectionColors, 0, slot.colorCount)
            tags[base + Nameplates.TAG_DETAIL] = slot.detail
            tags[base + Nameplates.TAG_GLYPH_OFFSET] = glyphAt
            tags[base + Nameplates.TAG_GLYPH_COUNT] = slot.glyphCount
            tags[base + Nameplates.TAG_COLOR_OFFSET] = colorAt
            tags[base + Nameplates.TAG_COLOR_COUNT] = slot.colorCount
            System.arraycopy(slot.signals, 0, signals, i * Nameplates.SIGNAL_FLOATS, Nameplates.SIGNAL_FLOATS)
            System.arraycopy(slot.glyphs, 0, glyphs, glyphAt * Nameplates.GLYPH_FLOATS, slot.glyphCount * Nameplates.GLYPH_FLOATS)
            System.arraycopy(slot.colors, 0, colors, colorAt, slot.colorCount)
            glyphAt += slot.glyphCount
            colorAt += slot.colorCount
            slot.signals[Nameplates.SIGNAL_EVENTS] = 0f
        }

        val needed = plates.neededWords(tags, count)
        if (out.size < needed) out = IntArray(needed + needed / 2)
        val began = System.nanoTime()
        try {
            plates.frame(CosmeticClock.seconds(), wearers, count, tags, signals, glyphs, colors, out)
        } finally {
            CosmeticWearers.charge(System.nanoTime() - began)
        }
        read(plates, count)
        startFallbacks(plates)
    }

    private fun finishFallbacks(plates: Nameplates) {
        while (true) {
            val job = finished.poll() ?: break
            fallbacksOut.decrementAndGet()
            try {
                plates.finishFallback(job)
            } catch (e: RuntimeException) {
                report("fallback", "Could not store a nameplate effect drawn on the CPU", e)
            } finally {
                job.close()
            }
        }
    }

    private fun startFallbacks(plates: Nameplates) {
        var started = 0
        while (started < FALLBACKS_PER_FRAME && fallbacksOut.get() < MAX_FALLBACKS) {
            val job = plates.takeFallback() ?: break
            started++
            fallbacksOut.incrementAndGet()
            try {
                effects.execute { runFallback(job) }
            } catch (e: RejectedExecutionException) {
                fallbacksOut.decrementAndGet()
                job.close()
            }
        }
    }

    private fun runFallback(job: Nameplates.Fallback) {
        try {
            job.run()
            finished.add(job)
        } catch (e: Throwable) {
            fallbacksOut.decrementAndGet()
            job.close()
            if (fallbackRunFailed.compareAndSet(false, true)) {
                LOGGER.error("Could not draw a nameplate effect on the CPU", e)
                sentry("Could not draw a nameplate effect on the CPU: ${e.javaClass.name}: ${e.message}")
            }
        }
    }

    private fun collect(from: LongObjectMap<Slot>) {
        for (index in 0 until from.capacity) {
            val slot = from.valueAt(index) ?: continue
            val bundle = slot.bundle ?: continue
            if (bundle in failed || loaded[bundle] == null) continue
            val fresh = slot.measuredFrame == frame || (!slot.deferred && slot.measuredFrame == frame - 1)
            if (fresh) batch += slot
        }
    }

    private fun applyColors(plates: Nameplates, slot: Slot, entry: Loaded) {
        val wanted = CosmeticColors.get(slot.wearer, slot.cosmeticId)
        if (wanted === slot.appliedColors && entry.id == slot.appliedAsset) return
        slot.appliedColors = wanted
        slot.appliedAsset = entry.id
        if (wanted.isNullOrEmpty()) {
            plates.clearCustomColors(slot.id, entry.id)
            return
        }
        val info = entry.meta.info
        if (colorSlots.size < wanted.size) {
            colorSlots = IntArray(wanted.size)
            colorRgb = IntArray(wanted.size)
        }
        var count = 0
        for ((key, rgb) in wanted) {
            val index = info.slot(key)
            if (index < 0) continue
            colorSlots[count] = index
            colorRgb[count] = rgb and RGB
            count++
        }
        if (count == 0) {
            plates.clearCustomColors(slot.id, entry.id)
            return
        }
        try {
            plates.setCustomColors(slot.id, entry.id, colorSlots.copyOf(count), colorRgb.copyOf(count))
        } catch (e: RuntimeException) {
            report("colors", "Could not apply custom nameplate colors", e)
        }
    }

    private fun rank() {
        ranking.clear()
        for (i in 0 until batch.size) {
            val slot = batch[i]
            if (slot.flags and Nameplates.FLAG_OWN != 0) slot.detail = Nameplates.DETAIL_FULL else ranking += slot
        }
        ranking.sortWith(nearest)
        for (i in 0 until ranking.size) {
            val slot = ranking[i]
            slot.detail = detailOf(slot)
        }
        ranking.clear()
    }

    private fun detailOf(slot: Slot): Int {
        if (CosmeticWearers.trackedThisFrame(slot.id)) return CosmeticWearers.emoteDetail(slot.id, UNKNOWN_ENTITY, slot.distanceSq)
        return CosmeticWearers.detail(CosmeticWearers.track(slot.id, UNKNOWN_ENTITY, slot.distanceSq, false))
    }

    private fun read(plates: Nameplates, count: Int) {
        val layout = out[Nameplates.OUT_STRIP_LAYOUT]
        if (layout != stripLayout) {
            stripLayout = layout
            strips.clear()
            NameplateAtlas.clear()
        }
        var badges = false
        for (i in 0 until count) {
            val slot = batch[i]
            if (slot.badgeShown) badges = true
            val dir = Nameplates.OUT_HEADER + i * Nameplates.DIR_WORDS
            val at = out[dir + Nameplates.DIR_OFFSET]
            val len = out[dir + Nameplates.DIR_LEN]
            val status = out[dir + Nameplates.DIR_STATUS]
            val bundle = slot.bundle ?: continue
            if (len >= Nameplates.TAG_HEAD) events(bundle, at, len)
            if (status != Nameplates.STATUS_OK) slot.headKnown = false
            when (status) {
                Nameplates.STATUS_OK -> if (len >= Nameplates.TAG_HEAD) {
                    slot.sectionFrame = frame
                    slot.sectionAt = at
                    head(slot, at)
                    images(plates, at)
                }
                Nameplates.STATUS_DISABLED -> failed += bundle
                Nameplates.STATUS_BAD_ROW -> once("bad-row:${bundle.key}") {
                    LOGGER.warn("Nameplate {} rejected a tag row", bundle.key)
                }
                Nameplates.STATUS_NO_ROOM -> once("no-room:${bundle.key}") {
                    LOGGER.warn("Nameplate {} needed more output room than it declared", bundle.key)
                }
            }
        }
        try {
            if (badges) NameplateBadgeArt.ensure()
            NameplateAtlas.upload()
        } catch (e: RuntimeException) {
            report("atlas", "Could not upload nameplate effect images", e)
        }
    }

    private fun head(slot: Slot, at: Int) {
        val mode = out[at + Nameplates.HEAD_TEXT_MODE]
        val text = textAt(at)
        var restyle = mode != Nameplates.TEXT_MODE_VANILLA ||
            out[text + Nameplates.TEXT_COLOR] != 0 ||
            out[text + Nameplates.TEXT_OUTLINE] != 0 ||
            (shadowRecolours(out[text + Nameplates.TEXT_SHADOW_USE]) && out[text + Nameplates.TEXT_SHADOW] != 0)
        if (!restyle) {
            val adjusted = text + Nameplates.TEXT_FIXED + out[text + Nameplates.TEXT_LETTER_COUNT] * Nameplates.LETTER_WORDS
            for (c in 0 until slot.sectionColorCount) {
                if (out[adjusted + c] and RGB != slot.sectionColors[c] and RGB) {
                    restyle = true
                    break
                }
            }
        }
        slot.ownsBackground = out[at + Nameplates.HEAD_OWNS_BACKGROUND] != 0
        slot.textMode = mode
        slot.restyle = restyle
        slot.headKnown = true
    }

    private fun shadowRecolours(use: Int): Boolean =
        use == Nameplates.SHADOW_ALWAYS || use == Nameplates.SHADOW_WITH_DROP

    private fun textAt(at: Int): Int =
        at + Nameplates.TAG_HEAD + out[at + Nameplates.HEAD_OPS] * Nameplates.OP_WORDS + out[at + Nameplates.HEAD_MATERIAL_WORDS]

    private fun events(bundle: NameplateBundle, at: Int, len: Int) {
        val count = out[at + Nameplates.HEAD_EVENTS]
        if (count <= 0) return
        val start = at + len - count * Nameplates.EVENT_WORDS
        if (start < at + Nameplates.TAG_HEAD) return
        for (e in 0 until count) {
            val code = out[start + e * Nameplates.EVENT_WORDS + Nameplates.EVENT_CODE]
            val layer = out[start + e * Nameplates.EVENT_WORDS + Nameplates.EVENT_LAYER]
            if (code == Nameplates.EVENT_ASSET_DISABLED) failed += bundle
            once("event:${System.identityHashCode(bundle)}:$code:$layer") {
                val what = when (code) {
                    Nameplates.EVENT_LAYER_DISABLED -> "turned off layer $layer after it failed"
                    Nameplates.EVENT_MOLANG_OVER_BUDGET -> "turned off layer $layer after its MoLang went over budget"
                    Nameplates.EVENT_ASSET_DISABLED -> "failed and is off until it reloads"
                    else -> "reported event $code on layer $layer"
                }
                LOGGER.warn("Nameplate {} {}", bundle.key, what)
                sentry("Nameplate ${bundle.key} $what")
            }
        }
    }

    private fun images(plates: Nameplates, at: Int) {
        val ops = out[at + Nameplates.HEAD_OPS]
        var p = at + Nameplates.TAG_HEAD
        for (op in 0 until ops) {
            val kind = (out[p + Nameplates.OP_BAND_LAYER_KIND] shr 16) and 0xFF
            if (kind == Nameplates.OP_KIND_IMAGE) {
                val id = out[p + Nameplates.OP_AUX0]
                if (NameplateAtlas.region(id) == null) {
                    val width = out[p + Nameplates.OP_AUX1] and Nameplates.AUX_LOW
                    val count = plates.stripTexels(id, null)
                    if (width > 0 && count > 0 && count % width == 0) {
                        if (texels.size < count) texels = IntArray(count)
                        plates.stripTexels(id, texels)
                        NameplateAtlas.put(id, width, count / width, texels)
                    }
                }
            }
            p += Nameplates.OP_WORDS
        }
    }

    private fun translate(slot: Slot, list: NameplateDrawList) {
        val at = slot.sectionAt
        list.plateDx = out[at + Nameplates.HEAD_PLATE_DX]
        list.plateDy = out[at + Nameplates.HEAD_PLATE_DY]
        val ops = out[at + Nameplates.HEAD_OPS]
        val programs = slot.bundle?.let { loaded[it] }?.programs ?: NameplatePrograms.NONE
        var materialsLeft = if (programs.gpu) out[at + Nameplates.HEAD_MATERIALS] else 0
        var m = at + Nameplates.TAG_HEAD + ops * Nameplates.OP_WORDS
        var p = at + Nameplates.TAG_HEAD
        for (op in 0..ops) {
            while (materialsLeft > 0 && out[m + Nameplates.MAT_RUN] <= op) {
                m = material(list, programs, m)
                materialsLeft--
            }
            if (op == ops) break
            val x0 = out[p + Nameplates.OP_X0]
            val y0 = out[p + Nameplates.OP_Y0]
            val x1 = out[p + Nameplates.OP_X1]
            val y1 = out[p + Nameplates.OP_Y1]
            val argb = out[p + Nameplates.OP_ARGB]
            val word = out[p + Nameplates.OP_BAND_LAYER_KIND]
            val bandLayer = word and 0xFFFF
            val aux0 = out[p + Nameplates.OP_AUX0]
            val aux1 = out[p + Nameplates.OP_AUX1]
            when ((word shr 16) and 0xFF) {
                Nameplates.OP_KIND_RECT -> list.rect(x0, y0, x1, y1, argb, bandLayer)
                Nameplates.OP_KIND_STRIP -> strip(list, x0, y0, x1, y1, argb ushr 24, bandLayer, aux0, aux1)
                Nameplates.OP_KIND_IMAGE -> image(list, x0, y0, x1, y1, argb, bandLayer, aux0, aux1)
            }
            p += Nameplates.OP_WORDS
        }
        while (materialsLeft > 0) {
            m = material(list, programs, m)
            materialsLeft--
        }
        val text = textAt(at)
        list.textColor = out[text + Nameplates.TEXT_COLOR]
        list.textShadow = out[text + Nameplates.TEXT_SHADOW]
        list.textShadowUse = out[text + Nameplates.TEXT_SHADOW_USE]
        list.textOutline = out[text + Nameplates.TEXT_OUTLINE]
        val letters = out[text + Nameplates.TEXT_LETTER_COUNT]
        val letterAt = text + Nameplates.TEXT_FIXED
        list.letters(out, letterAt, letters)
        val colorsAt = letterAt + letters * Nameplates.LETTER_WORDS
        list.adjust(slot.sectionColors, out, colorsAt, slot.sectionColorCount)
        if (slot.badgeShown) badge(slot, list, colorsAt + slot.sectionColorCount)
    }

    private fun badge(slot: Slot, list: NameplateDrawList, badgeAt: Int) {
        val opacity = out[badgeAt + Nameplates.BADGE_OPACITY]
        if (opacity <= 0) return
        var x = out[badgeAt + Nameplates.BADGE_X]
        val y = out[badgeAt + Nameplates.BADGE_Y]
        if (!slot.display && slot.badgeGapX != NameplateDrawTag.NO_GAP && slot.flags and Nameplates.FLAG_BADGE != 0) {
            x += slot.badgeGapX - (slot.row[Nameplates.TAG_NAME_X] - Nameplates.BADGE_ADVANCE)
        }
        val tint = out[badgeAt + Nameplates.BADGE_TINT]
        val decoration = out[badgeAt + Nameplates.BADGE_DECORATION]
        val alpha = opacity.coerceAtMost(CHANNEL_MAX) shl 24
        val art = NameplateBadgeArt.art() ?: return
        when (decoration ushr Nameplates.DECORATION_KIND_SHIFT) {
            Nameplates.DECORATION_OUTLINE -> NameplateBadgeArt.outline()?.let {
                image(list, x - NameplateBadgeArt.OUTLINE_INSET, y - NameplateBadgeArt.OUTLINE_INSET, NameplateBadgeArt.OUTLINE_SIZE, alpha or (decoration and RGB), BADGE_DECORATION_LAYER, it)
            }
            Nameplates.DECORATION_SHADOW ->
                image(list, x + 1, y + 1, NameplateBadgeArt.SIZE, alpha or brighten(decoration and RGB), BADGE_DECORATION_LAYER, art)
        }
        val rgb = if (tint == 0) RGB else brighten(tint and RGB)
        image(list, x, y, NameplateBadgeArt.SIZE, alpha or rgb, BADGE_ART_LAYER, art)
    }

    private fun image(list: NameplateDrawList, x: Int, y: Int, size: Int, argb: Int, bandLayer: Int, region: IntArray) {
        list.image(
            x, y, x + size, y + size, NameplateDrawList.guard(argb), bandLayer,
            region[0], region[1], region[0] + region[2], region[1] + region[3],
            NameplateDrawList.IMAGE_SOLID,
        )
    }

    private fun brighten(rgb: Int): Int =
        (brightenChannel(rgb shr 16) shl 16) or (brightenChannel(rgb shr 8) shl 8) or brightenChannel(rgb)

    private fun brightenChannel(value: Int): Int =
        minOf(CHANNEL_MAX, ((value and CHANNEL_MAX) * CHANNEL_MAX + BADGE_ART_TONE / 2) / BADGE_ART_TONE)

    private fun material(list: NameplateDrawList, programs: NameplatePrograms, at: Int): Int {
        val clipCount = out[at + Nameplates.MAT_CLIP_COUNT]
        val words = out[at + Nameplates.MAT_UNIFORM_WORDS]
        val clipsAt = at + Nameplates.MAT_FIXED
        val uniformsAt = clipsAt + clipCount * Nameplates.CLIP_WORDS
        val next = uniformsAt + words
        val index = out[at + Nameplates.MAT_SHADER]
        val shader = programs.shader(index) ?: return next
        if (programs.program(index) == null || words < shader.uniformWords) return next
        if (shader.atlasSamplers) fillAtlas(shader.samplers, uniformsAt)
        val bodyX = Float.fromBits(out[uniformsAt + UNIFORM_BODY])
        val bodyY = Float.fromBits(out[uniformsAt + UNIFORM_BODY + 1])
        list.material(
            programs,
            index,
            out[at + Nameplates.MAT_AREA_X0],
            out[at + Nameplates.MAT_AREA_Y0],
            out[at + Nameplates.MAT_AREA_X1],
            out[at + Nameplates.MAT_AREA_Y1],
            Float.fromBits(out[uniformsAt]) + bodyX,
            Float.fromBits(out[uniformsAt + 1]) + bodyY,
            Float.fromBits(out[uniformsAt + 2]) + bodyX,
            Float.fromBits(out[uniformsAt + 3]) + bodyY,
            out[at + Nameplates.MAT_BAND_LAYER],
            out,
            clipsAt,
            clipCount,
            uniformsAt,
            words,
        )
        return next
    }

    private fun fillAtlas(samplers: Array<NameplateSampler>, uniformsAt: Int) {
        val backend = NameplateMaterials.backend ?: return
        for (k in 0 until minOf(samplers.size, ATLAS_SLOTS)) {
            val sampler = samplers[k]
            if (sampler.kind != NameplateSampler.KIND_ATLAS) continue
            if (!backend.atlasRect(sampler.texture, atlasRect)) continue
            val word = uniformsAt + Nameplates.UNIFORM_ATLAS + k * 4
            for (c in 0 until 4) out[word + c] = atlasRect[c].toRawBits()
        }
    }

    private fun strip(list: NameplateDrawList, x0: Int, y0: Int, x1: Int, y1: Int, alpha: Int, bandLayer: Int, id: Int, aux: Int) {
        val tones = strips[id.toLong()] ?: fetchStrip(id) ?: return
        val n = tones.size
        if (n == 0) return
        val phase = (aux shl 16) shr 16
        if (aux and Nameplates.AUX_AXIS_Y != 0) {
            var start = y0
            var color = 0
            for (y in y0..y1) {
                val next = if (y < y1) fade(tones[Math.floorMod(y - y0 + phase, n)], alpha) else 0
                if (y == y0) {
                    color = next
                } else if (y == y1 || next != color) {
                    list.rect(x0, start, x1, y, color, bandLayer)
                    start = y
                    color = next
                }
            }
            return
        }
        val mirror = aux and Nameplates.AUX_MIRROR != 0
        val width = x1 - x0
        var start = x0
        var color = 0
        for (x in x0..x1) {
            val i = x - x0
            val along = if (mirror) width - 1 - i else i
            val next = if (x < x1) fade(tones[Math.floorMod(along + phase, n)], alpha) else 0
            if (x == x0) {
                color = next
            } else if (x == x1 || next != color) {
                list.rect(start, y0, x, y1, color, bandLayer)
                start = x
                color = next
            }
        }
    }

    private fun fetchStrip(id: Int): IntArray? {
        val plates = plates ?: return null
        val count = plates.stripTexels(id, null)
        if (count <= 0) return null
        val tones = IntArray(count)
        plates.stripTexels(id, tones)
        strips[id.toLong()] = tones
        return tones
    }

    private fun image(list: NameplateDrawList, x0: Int, y0: Int, x1: Int, y1: Int, argb: Int, bandLayer: Int, id: Int, aux: Int) {
        val region = NameplateAtlas.region(id) ?: return
        val width = minOf(x1 - x0, region[2])
        val height = minOf(y1 - y0, region[3])
        val left = region[0]
        val top = region[1]
        val mirror = aux and Nameplates.AUX_MIRROR != 0
        val u0 = if (mirror) left + region[2] else left
        val u1 = if (mirror) left + region[2] - width else left + width
        val additive = aux and Nameplates.AUX_ADDITIVE != 0
        list.image(x0, y0, x0 + width, y0 + height, argb or RGB, bandLayer, u0, top, u1, top + height, additive)
    }

    private fun fade(argb: Int, alpha: Int): Int {
        if (alpha >= 255) return argb
        val a = ((argb ushr 24) * alpha + 127) / 255
        return (a shl 24) or (argb and RGB)
    }

    private fun updateMotion(plates: Nameplates) {
        val wanted = when (PolyPlusCosmeticsConfig.cosmeticMotion) {
            CosmeticMotion.FULL -> Nameplates.MOTION_FULL
            CosmeticMotion.REDUCED -> Nameplates.MOTION_REDUCED
            CosmeticMotion.OFF -> Nameplates.MOTION_OFF
        }
        if (wanted == motion) return
        plates.setMotion(wanted)
        motion = wanted
    }

    private fun nameplates(): Nameplates? {
        plates?.let { return it }
        if (platesFailed) return null
        return try {
            Nameplates().also { plates = it }
        } catch (e: Throwable) {
            platesFailed = true
            LOGGER.error("Nameplates are off: the native renderer did not load", e)
            null
        }
    }

    private fun drain() {
        if (reloaded) {
            reloaded = false
            reloadResources()
        }
        while (true) {
            val bundle = released.poll() ?: break
            val entry = loaded.remove(bundle) ?: continue
            try {
                plates?.removeAsset(entry.id)
            } finally {
                NameplateMaterials.release(entry.programs)
                entry.asset.close()
            }
            invalidate(slots, bundle)
            invalidate(displaySlots, bundle)
        }
        while (true) {
            val next = parsed.poll() ?: break
            install(next)
        }
        while (true) {
            val wearer = forgotten.poll() ?: break
            slots.remove(wearer)
            displaySlots.remove(wearer)
            plates?.forgetWearer(wearer)
        }
    }

    private fun install(next: Parsed) {
        val bundle = next.bundle
        val asset = next.asset
        val current = generations[bundle.key] == next.generation && NameplateBundles.bundle(bundle.key) === bundle
        if (!current) {
            asset?.close()
            return
        }
        if (asset == null || next.meta == null) {
            failed += bundle
            return
        }
        val plates = nameplates()
        if (plates == null) {
            asset.close()
            return
        }
        loaded.remove(bundle)?.let {
            plates.removeAsset(it.id)
            NameplateMaterials.release(it.programs)
            it.asset.close()
        }
        val id = try {
            plates.addAsset(asset)
        } catch (e: RuntimeException) {
            LOGGER.error("Nameplate {} could not be added", bundle.key, e)
            0
        }
        if (id <= 0) {
            asset.close()
            failed += bundle
            return
        }
        registerTextures(plates, next.meta.textures)
        loaded[bundle] = Loaded(asset, id, next.meta, programs(bundle, asset, next.meta))
    }

    private fun programs(bundle: NameplateBundle, asset: NameplateAsset, meta: Meta): NameplatePrograms = try {
        NameplateMaterials.install(bundle.key, asset, meta.shaders)
    } catch (e: RuntimeException) {
        report("programs", "Could not prepare nameplate effects for the GPU", e)
        NameplatePrograms.NONE
    }

    private fun reloadResources() {
        vanillaTextures.clear()
        NameplateBadgeArt.reload()
        NameplateAtlas.clear()
        for (entry in loaded.values) NameplateMaterials.release(entry.programs)
        NameplateMaterials.reload()
        val plates = plates
        for ((bundle, entry) in loaded) {
            if (plates != null) registerTextures(plates, entry.meta.textures)
            entry.programs = programs(bundle, entry.asset, entry.meta)
        }
    }

    private fun registerTextures(plates: Nameplates, names: List<String>) {
        for (name in names) {
            if (!vanillaTextures.add(name)) continue
            val image = NameplateVanillaTextures.load(name)
            if (image == null) {
                LOGGER.warn("Nameplate effects asked for the game texture {}, which could not be read", name)
                continue
            }
            try {
                plates.setVanillaTexture(name, image.width, image.height, image.rgba)
            } catch (e: RuntimeException) {
                LOGGER.warn("Could not hand the game texture {} to nameplate effects", name, e)
            }
        }
    }

    private fun prune() {
        lastPrune = frame
        prune(slots, displaySlots)
        prune(displaySlots, slots)
    }

    private fun prune(from: LongObjectMap<Slot>, other: LongObjectMap<Slot>) {
        var count = 0
        for (index in 0 until from.capacity) {
            val slot = from.valueAt(index) ?: continue
            if (frame - slot.usedFrame < IDLE_FRAMES) continue
            if (count == expired.size) expired = expired.copyOf(count * 2)
            expired[count++] = slot.id
        }
        for (i in 0 until count) {
            val id = expired[i]
            from.remove(id)
            if (other[id] == null) plates?.forgetWearer(id)
        }
    }

    private fun invalidate(from: LongObjectMap<Slot>, bundle: NameplateBundle) {
        for (index in 0 until from.capacity) {
            val slot = from.valueAt(index) ?: continue
            if (slot.bundle === bundle) slot.sectionFrame = Int.MIN_VALUE
        }
    }

    private fun parse(bundle: NameplateBundle, generation: Int) {
        var asset: NameplateAsset? = null
        var meta: Meta? = null
        try {
            val bytes = bundle.readBytes()
            val identity = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes), 0, Long.SIZE_BYTES).long
            val parsedAsset = NameplateAsset.parse(bytes, NameplateAsset.MODE_CLIENT, identity)
            when (parsedAsset.status()) {
                NameplateAsset.STATUS_ASSET, NameplateAsset.STATUS_DRAFT -> {
                    asset = parsedAsset
                    meta = meta(parsedAsset, bytes)
                }
                NameplateAsset.STATUS_SKIPPED -> {
                    LOGGER.info("Nameplate {} needs features this version of PolyPlus does not have", bundle.key)
                    parsedAsset.close()
                }
                else -> {
                    val issues = issues(parsedAsset)
                    parsedAsset.close()
                    LOGGER.warn("Nameplate {} is invalid: {}", bundle.key, issues)
                    sentry("Nameplate ${bundle.key} is invalid: $issues")
                }
            }
        } catch (e: Throwable) {
            asset?.close()
            asset = null
            LOGGER.error("Nameplate {} could not be read", bundle.key, e)
        }
        parsed.add(Parsed(bundle, generation, asset, meta))
    }

    private fun meta(asset: NameplateAsset, bytes: ByteArray): Meta {
        val root = JSON.parseToJsonElement(asset.metaJson()) as JsonObject
        val variants = root["variants"] as? JsonArray ?: JsonArray(emptyList())
        val keys = Array(variants.size) { i ->
            ((variants[i] as? JsonObject)?.get("key")?.jsonPrimitive?.contentOrNull).orEmpty()
        }
        val shaders = try {
            NameplateMaterials.shaders(asset)
        } catch (e: RuntimeException) {
            LOGGER.warn("Nameplate effects could not be generated for the GPU; they draw on the CPU", e)
            null
        }
        val info = try {
            NameplateBundleInfo.read(bytes, JSON)
        } catch (e: Exception) {
            LOGGER.warn("Could not read the badge and colour slots of a nameplate", e)
            NameplateBundleInfo.EMPTY
        }
        return Meta(keys, shaders?.gameTextures() ?: textures(asset), shaders, info)
    }

    private fun textures(asset: NameplateAsset): List<String> {
        val json = try {
            asset.shadersJson(NameplateAsset.DIALECT_CORE_330)
        } catch (e: RuntimeException) {
            return emptyList()
        }
        val names = LinkedHashSet<String>()
        val shaders = (JSON.parseToJsonElement(json) as? JsonObject)?.get("shaders") as? JsonArray ?: return emptyList()
        for (shader in shaders) {
            val samplers = (shader as? JsonObject)?.get("samplers") as? JsonArray ?: continue
            for (sampler in samplers) {
                val texture = (sampler as? JsonObject)?.get("texture") as? JsonObject ?: continue
                val kind = texture["kind"]?.jsonPrimitive?.contentOrNull
                if (kind != "vanilla" && kind != "atlas") continue
                texture["name"]?.jsonPrimitive?.contentOrNull?.let(names::add)
            }
        }
        return names.toList()
    }

    private fun issues(asset: NameplateAsset): String = try {
        val list = JSON.parseToJsonElement(asset.issuesJson())
        val array = list as? JsonArray ?: (list as? JsonObject)?.get("issues") as? JsonArray
        if (array == null) list.toString() else JsonArray(array.take(ISSUE_LIMIT)).toString()
    } catch (e: RuntimeException) {
        "unknown"
    }

    private fun report(key: String, message: String, e: Throwable) {
        once("error:$key") {
            LOGGER.error(message, e)
            sentry("$message: ${e.javaClass.name}: ${e.message}")
        }
    }

    private inline fun once(key: String, action: () -> Unit) {
        if (reported.size > 4096) reported.clear()
        if (reported.add(key)) action()
    }

    private fun sentry(message: String) {
        try {
            PolyPlusSentry.activeHub()?.captureMessage(message, SentryLevel.WARNING)
        } catch (e: RuntimeException) {
            LOGGER.debug("Could not report to Sentry", e)
        }
    }
}
