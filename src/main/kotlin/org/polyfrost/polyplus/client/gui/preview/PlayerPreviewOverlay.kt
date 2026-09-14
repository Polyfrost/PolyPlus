package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

//? if < 1.21.5 || >= 1.21.8 {
import com.mojang.blaze3d.pipeline.RenderTarget
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
//?}

//? if >= 1.21.8 && < 26.1 {
/*import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
*///?}

object PlayerPreviewOverlay {
    private val LOG = LoggerFactory.getLogger("polyplus/preview-overlay")

    class Entry internal constructor(@JvmField val id: Long) {
        @Volatile @JvmField var source: PlayerPreviewSource = PlayerPreviewSource.LocalLive
        @Volatile @JvmField var modelScale: Float = 0.5f
        @Volatile @JvmField var verticalAnchor: Float = 0.5f
        @Volatile @JvmField var autoSpin: Boolean = false
        @Volatile @JvmField var initialYaw: Float = 0f
        @Volatile @JvmField var allowDrag: Boolean = true
        @Volatile @JvmField var dragYaw: Float = 0f
        @Volatile @JvmField var dragPitch: Float = 0f
        @Volatile @JvmField var dragging: Boolean = false
        @Volatile @JvmField var spinAccum: Float = 0f
        @Volatile @JvmField var lastSpinNanos: Long = 0L
        @Volatile @JvmField var previewKey: Any = id

        @Volatile @JvmField var fadeEdges: Boolean = true
        @Volatile @JvmField var bottomFade: Float = 0f

        @Volatile @JvmField var opacity: Float = 1f

        @Volatile @JvmField var fx: Float = 0f
        @Volatile @JvmField var fy: Float = 0f
        @Volatile @JvmField var fw: Float = 0f
        @Volatile @JvmField var fh: Float = 0f
        @Volatile @JvmField var visible: Boolean = false

        @Volatile @JvmField var owner: WeakReference<Any>? = null
    }

    private val entries = ConcurrentHashMap<Long, Entry>()
    private val idGen = AtomicLong()

    fun register(): Entry {
        val e = Entry(idGen.incrementAndGet())
        entries[e.id] = e
        return e
    }

    fun unregister(id: Long) {
        entries.remove(id)
    }

    fun reportBounds(entry: Entry, fx: Float, fy: Float, fw: Float, fh: Float, visible: Boolean) {
        entry.fx = fx; entry.fy = fy; entry.fw = fw; entry.fh = fh; entry.visible = visible
        entry.owner = if (visible) currentScreen()?.let { WeakReference(it) } else null
    }

    private fun currentScreen(): Any? {
        val mc = Minecraft.getInstance() ?: return null
        //? if >= 26.2 {
        return mc.gui?.screen()
        //?} else {
        /*return mc.screen
        *///?}
    }

    @JvmStatic
    fun clear() {
        if (entries.isNotEmpty()) entries.clear()
    }

    //? if < 1.21.5 || >= 1.21.8 {
    private const val SPIN_DEG_PER_SEC = 37.5f

    @JvmStatic
    fun renderAll(target: RenderTarget) {
        if (java.lang.Boolean.getBoolean("pp.overlay.off")) return
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        val screen = mc?.gui?.screen()
        //?} else {
        /*val screen = mc?.screen
        *///?}
        if (screen !is ComposeScreen) {
            clear()
            return
        }
        if (entries.isEmpty()) return
        entries.values.removeIf { e ->
            val owner = e.owner?.get()
            owner != null && owner !== screen
        }
        if (entries.isEmpty()) return
        val fbW = target.width
        val fbH = target.height
        if (fbW <= 0 || fbH <= 0) return
        //? if >= 1.21.8 && < 26.1 {
        /*foldBackBufferIntoTarget(target)
        *///?}
        val nowNanos = System.nanoTime()
        for (e in entries.values) {
            if (!e.visible || e.fw <= 0f || e.fh <= 0f || e.opacity <= 0.001f) continue
            val x = (e.fx * fbW).toInt()
            val y = (e.fy * fbH).toInt()
            val w = (e.fw * fbW).toInt()
            val h = (e.fh * fbH).toInt()
            if (w <= 0 || h <= 0) continue
            val dtSec = if (e.lastSpinNanos == 0L) 0.0 else (nowNanos - e.lastSpinNanos) / 1_000_000_000.0
            e.lastSpinNanos = nowNanos
            if (e.autoSpin && !e.dragging) {
                e.spinAccum += (dtSec * SPIN_DEG_PER_SEC).toFloat()
            }
            val yaw = e.initialYaw + e.dragYaw + e.spinAccum
            runCatching { PlayerPreviewRenderer.renderOverlayEntry(target, e, yaw, e.dragPitch, x, y, w, h) }
                .onFailure { LOG.error("[preview] overlay entry {} render failed", e.id, it) }
        }
        //? if >= 1.21.8 && < 26.1 {
        /*runCatching { presentTargetToBackBuffer(target) }
            .onFailure { LOG.error("[preview] re-present to back buffer failed", it) }
        *///?}
    }
    //?}

    //? if >= 1.21.8 && < 26.1 {
    /*private fun mainFbo(target: RenderTarget): Int {
        val colorTex = target.colorTexture ?: return -1
        val device = RenderSystem.getDevice() as? GlDevice ?: return -1
        return (colorTex as GlTexture).getFbo(device.directStateAccess(), target.depthTexture)
    }

    private fun foldBackBufferIntoTarget(target: RenderTarget) {
        val fbo = mainFbo(target); if (fbo < 0) return
        val w = target.width; val h = target.height
        GlStateManager._disableScissorTest()
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo)
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST)
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    private fun presentTargetToBackBuffer(target: RenderTarget) {
        val fbo = mainFbo(target); if (fbo < 0) return
        val w = target.width; val h = target.height
        GlStateManager._disableScissorTest()
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0)
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST)
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }
    *///?}
}
