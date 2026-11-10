package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateChoice
import org.polyfrost.polyplus.client.nameplate.NameplateChoices
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.NameplateRegistry
import org.polyfrost.polyplus.client.nameplate.render.NameplateClock
import org.polyfrost.polyplus.client.nameplate.render.NameplateGuiRenderer
import org.polyfrost.polyplus.client.nameplate.render.NameplateTag
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.nameplate.engine.api.anim.TagRecord
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import kotlin.math.floor
//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
*///?}

object NameplatePreviewOffscreen {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    class Entry internal constructor() {
        var cosmeticId: Int? = null
        var controller: NameplatePreviewController? = null
        var fit = false
        var fontPixelFraction = 0f
        var fw = 0f
        var fh = 0f

        internal var lastDrawNanos = 0L
        internal var offscreen: OffscreenTarget? = null
        internal var hasContent = false
        internal var bound: BoundNameplate? = null
        internal var drawnKey: DrawnKey? = null
    }

    internal data class DrawnKey(
        val bound: BoundNameplate,
        val frame: Int,
        val state: TagState,
        val width: Int,
        val height: Int,
        val pixels: Float,
        val name: String,
        val badge: Boolean,
        val active: Boolean,
        val motion: NameplateMotion,
    )

    private const val MAX_DIM = 1024
    private const val IDLE_NANOS = 250_000_000L
    private const val RELEASE_NANOS = 1_000_000_000L
    private const val FIT_WIDTH = 0.86f
    private const val FIT_HEIGHT = 0.42f
    private const val FIT_MARGIN = 24
    private const val LINE_HEIGHT = 10f
    private const val DEPTH_RANGE = 64f

    private val entries = HashSet<Entry>()
    private val released = ArrayList<OffscreenTarget>()
    private val record = TagRecord<Entry>()
    private val ortho = PreviewFramebuffer.Ortho("polyplus_nameplate_preview", DEPTH_RANGE)

    fun register(): Entry = Entry().also(entries::add)

    fun unregister(entry: Entry) {
        entries.remove(entry)
        record.remove(entry)
        release(entry)
    }

    private fun release(entry: Entry) {
        entry.hasContent = false
        entry.drawnKey = null
        entry.offscreen?.let(released::add)
        entry.offscreen = null
    }

    @JvmStatic
    fun renderAll(main: RenderTarget) {
        released.forEach { it.dispose() }
        released.clear()
        if (entries.isEmpty()) return
        val composeScreen = ClientPlatform.currentScreen() is ComposeScreen
        val now = System.nanoTime()
        for (entry in entries) {
            val sinceDraw = now - entry.lastDrawNanos
            if (!composeScreen || sinceDraw > RELEASE_NANOS) {
                if (entry.offscreen != null) release(entry)
                continue
            }
            if (sinceDraw > IDLE_NANOS) continue
            val rectW = (entry.fw * main.width).toInt()
            val rectH = (entry.fh * main.height).toInt()
            if (rectW <= 0 || rectH <= 0) continue
            val fit = minOf(1f, MAX_DIM.toFloat() / maxOf(rectW, rectH))
            val w = (rectW * fit).toInt().coerceAtLeast(1)
            val h = (rectH * fit).toInt().coerceAtLeast(1)
            renderEntry(entry, w, h)
        }
    }

    private fun renderEntry(entry: Entry, w: Int, h: Int) {
        val choice = entry.cosmeticId?.let(NameplateChoices::choice)
        if (choice == null) {
            entry.hasContent = false
            entry.drawnKey = null
            return
        }
        val bound = boundFor(entry, choice)
        val frame = NameplateClock.frame(bound)
        val state = stateFor(entry, bound, frame)
        val uuid = ClientPlatform.localPlayerUuid()
        val profileName = ClientPlatform.localPlayerName()
        val badge = PolyPlusBadge.shouldBadge(uuid)
        val pixels = pixelsPerFontPixel(entry, profileName, w, h)
        val key = DrawnKey(
            bound, frame, state, w, h, pixels, profileName, badge,
            NameplateBinding.settings.enabled, NameplateBinding.settings.motion,
        )
        val animatedMaterials = MaterialLayer.CAPABILITY in bound.bundle.requires
        if (entry.hasContent && !animatedMaterials && key == entry.drawnKey) return

        val offscreen = entry.offscreen ?: OffscreenTarget().also { entry.offscreen = it }
        if (!offscreen.resolve(w, h)) {
            entry.hasContent = false
            return
        }
        val target = offscreen.target ?: return
        runCatching {
            entry.hasContent = renderInto(target, w, h, bound, frame, state, pixels, profileName, badge)
            if (entry.hasContent) entry.drawnKey = key
        }.onFailure {
            entry.hasContent = false
            LOGGER.error("[nameplate-preview] render failed", it)
        }
    }

    private fun boundFor(entry: Entry, choice: NameplateChoice): BoundNameplate {
        val existing = entry.bound
        if (existing != null && existing.bundle === choice.bundle && existing.variant === choice.variant) return existing
        val seed = NameplateRegistry.seedOf(ClientPlatform.localPlayerUuid())
        return BoundNameplate(choice.bundle, choice.variant, seed).also { entry.bound = it }
    }

    private fun stateFor(entry: Entry, bound: BoundNameplate, frame: Int): TagState {
        val controller = entry.controller
        val inputs = TagInputs.ofMask(controller?.reactions ?: 0)
        val shots = controller?.drainShots().orEmpty()
        return when (NameplateBinding.settings.motion) {
            NameplateMotion.OFF -> TagState.NONE
            NameplateMotion.REDUCED -> TagState.of(inputs)
            NameplateMotion.FULL -> {
                val state = record.update(entry, bound.bundle, bound.variant, frame, inputs)
                if (shots.isEmpty()) {
                    state
                } else {
                    shots.forEach { record.fire(entry, bound.bundle, it, frame) }
                    record.state(entry)
                }
            }
        }
    }

    private fun pixelsPerFontPixel(entry: Entry, profileName: String, w: Int, h: Int): Float {
        val raw = if (entry.fit) {
            //? if > 1.8.9 {
            val textWidth = Minecraft.getInstance().font.width(profileName) + FIT_MARGIN
            //?} else {
            /*val textWidth = Minecraft.getInstance().textRenderer.getWidth(profileName) + FIT_MARGIN
            *///?}
            minOf(w * FIT_WIDTH / textWidth, h * FIT_HEIGHT / LINE_HEIGHT)
        } else {
            h * entry.fontPixelFraction
        }
        return if (raw >= 1f) floor(raw) else raw.coerceAtLeast(MIN_PIXELS)
    }

    private const val MIN_PIXELS = 0.25f

    private fun renderInto(
        fbo: RenderTarget,
        w: Int,
        h: Int,
        bound: BoundNameplate,
        frame: Int,
        state: TagState,
        pixels: Float,
        profileName: String,
        badge: Boolean,
    ): Boolean {
        val tag = NameplateTag(bound, profileName, badge, true, frame, state)
        //? if > 1.8.9 {
        val name = NameplateGuiRenderer.name(profileName, bound, badge)
        return PreviewFramebuffer.render(fbo, w, h, ortho) {
            PreviewFramebuffer.collect { collector ->
                NameplateGuiRenderer.submit(collector, PoseStack(), w / 2f, h / 2f, pixels, tag, name)
            }
            true
        }
        //?} else {
        /*return PreviewFramebuffer.render(fbo, w, h, ortho) {
            NameplateGuiRenderer.draw(w / 2f, h / 2f, pixels, tag)
            true
        }
        *///?}
    }

    fun draw(entry: Entry, ctx: RenderContext, width: Float, height: Float) {
        entry.lastDrawNanos = System.nanoTime()
        if (!entry.hasContent || width <= 0f || height <= 0f) return
        val target = entry.offscreen?.target ?: return
        val textures = ctx.renderer as? TextureRenderer ?: return
        textures.texture(target, 0f, 0f, width, height, 0f, 1f, 1f, 0f, 1f)
    }
}
