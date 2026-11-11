@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.lobby

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.onboardingPending
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.Easing
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object StartupOverlay {
    private const val DARK = 0x11171C
    private const val WHITE = 0xFFFFFF
    private const val LOCKUP_MARK = 28f
    private const val LOCKUP_GAP = 10f
    private const val WORD_ASPECT = 5.3307f
    private const val WORD_HEIGHT = 27.72f
    private const val HERO_WIDTH_SHARE = 0.36f
    private const val HERO_HEIGHT_SHARE = 0.62f
    private const val HERO_CENTER = 0.47f
    private const val LINE_GAP = 72f
    private const val DESIGN_WIDTH = 1600f
    private const val DESIGN_HEIGHT = 900f
    private const val HOLD_LIMIT_SECONDS = 15.0
    private const val FADE_ONLY_SECONDS = 0.5f
    private const val RISE_CHROME_DELAY = 0.95
    private const val CARD_CHROME_DELAY = 0.35
    private const val RISE_SECONDS = 1.66f
    private const val CARD_SECONDS = 1.7f
    private const val LOAD_SHARE = 0.72f
    private const val HOLD_SHARE = 0.26f
    private const val PROMPT_DELAY_SECONDS = 1.0

    class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun mix(to: Box, t: Float) = Box(lerp(x, to.x, t), lerp(y, to.y, t), lerp(w, to.w, t), lerp(h, to.h, t))

        fun scaled(by: Float) = Box(x * by, y * by, w * by, h * by)
    }

    private class Lockup(val mark: Box, val word: Box, val bottom: Float = 0f, val left: Float = 0f, val width: Float = 0f)

    @Volatile var rootWidth = 0f
    @Volatile var navMark: Box? = null
    @Volatile var navWord: Box? = null
    @Volatile var navLine: Box? = null
    @Volatile var cardMark: Box? = null
    @Volatile var cardWord: Box? = null
    @Volatile var cardInk = WHITE

    @JvmField
    @Volatile
    var cardGrow = 1f

    @JvmField
    @Volatile
    var active = false

    private var shownProgress = 0f
    private var holdNanos = 0L
    private var startNanos = 0L
    private var fallbackNanos = 0L
    private var card = false
    private var accent = 0
    private var quickPlay = false

    @JvmStatic
    fun enabled(): Boolean = PolyPlusConfig.brandedStartup

    @JvmStatic
    fun noteQuickPlay(enabled: Boolean) {
        quickPlay = enabled
    }

    fun quickPlaying(): Boolean = quickPlay

    fun place(coordinates: LayoutCoordinates): Box {
        val rect = coordinates.boundsInRoot()
        return Box(rect.left, rect.top, rect.width, rect.height)
    }

    fun promptAllowed(): Boolean = !active || (holdNanos != 0L && seconds(holdNanos, System.nanoTime()) >= PROMPT_DELAY_SECONDS)

    @JvmStatic
    fun begin(fadeIn: Boolean, loadFinished: Boolean): Boolean {
        val initial = !fadeIn && !loadFinished && enabled()
        if (initial) {
            cardGrow = 0f
            active = true
        }
        return initial
    }

    @JvmStatic
    fun holdsOverlay(): Boolean = active

    @JvmStatic
    fun abandon() {
        if (active) finished(Minecraft.getInstance())
        //? if > 1.8.9
        StartupSounds.abandon()
    }

    @JvmStatic
    fun onFrame() {
        if (!active) return
        val mc = Minecraft.getInstance()
        if (!overlayOpen(mc) && loadFinished(mc) && ClientPlatform.currentScreen() !is PolyPlusLobbyScreen) finished(mc)
    }

    private fun overlayOpen(mc: Minecraft): Boolean =
        //? if >= 26.2 {
        mc.gui.overlay() != null
        //?} elif > 1.8.9 {
        /*mc.overlay != null
        *///?} else {
        /*false
        *///?}

    private fun loadFinished(mc: Minecraft): Boolean =
        //? if > 1.8.9 {
        mc.isGameLoadFinished
        //?} else {
        /*mc != null
        *///?}

    //? if > 1.8.9 {
    fun screenFrame(mc: Minecraft, g: StartupGraphics) {
        if (!active || overlayOpen(mc)) return
        StartupTextures.load(mc)
        enterHold(true)
        val canvas = StartupCanvas(mc, g)
        canvas.raise()
        canvas.begin()
        val done = draw(canvas, 1f, true)
        canvas.end()
        if (done) finished(mc)
    }

    @JvmStatic
    fun frame(mc: Minecraft, g: StartupGraphics, partial: Float, reload: Float, holding: Boolean) {
        StartupTextures.load(mc)
        enterHold(holding)
        val canvas = StartupCanvas(mc, g)
        if (holding) canvas.drawScreenBelow(partial)
        canvas.begin()
        val done = draw(canvas, reload, holding)
        canvas.end()
        if (!done) return
        finished(mc)
        //? if >= 26.2 {
        mc.gui.setOverlay(null)
        //?} else {
        /*mc.setOverlay(null)
        *///?}
    }
    //?} else {
    /*@JvmStatic
    fun legacySplash() {
        if (!begin(fadeIn = false, loadFinished = false)) return
        val mc = Minecraft.getInstance()
        StartupTextures.load(mc)
        val canvas = StartupCanvas(mc)
        canvas.projectWindow()
        canvas.begin()
        draw(canvas, 0f, false)
        canvas.end()
    }

    @JvmStatic
    fun legacyInitDone() {
        if (active && ClientPlatform.currentScreen() !is PolyPlusLobbyScreen) finished(Minecraft.getInstance())
    }

    fun legacyFrame() {
        if (!active) return
        val mc = Minecraft.getInstance()
        enterHold(true)
        val canvas = StartupCanvas(mc)
        canvas.begin()
        val done = draw(canvas, 1f, true)
        canvas.end()
        if (done) finished(mc)
    }
    *///?}

    private fun enterHold(holding: Boolean) {
        if (!holding || holdNanos != 0L) return
        holdNanos = System.nanoTime()
        card = onboardingPending()
        LobbyScene.startupReveal(if (card) CARD_CHROME_DELAY else RISE_CHROME_DELAY)
        StartupSounds.gameLoaded()
    }

    private fun draw(canvas: StartupCanvas, reload: Float, holding: Boolean): Boolean {
        val now = System.nanoTime()
        if (accent == 0) accent = ThemeConfig.accentColor.argb and WHITE
        val lobby = ClientPlatform.currentScreen() is PolyPlusLobbyScreen
        val failed = LobbyScene.phase == LobbyScene.Phase.FAILED
        val timedOut = holding && seconds(holdNanos, now) > HOLD_LIMIT_SECONDS
        if (holding && (!lobby || failed || timedOut) && startNanos == 0L) return fadeOnly(canvas, now)
        val started = holding && LobbyScene.revealStarted()
        if (started && startNanos == 0L) {
            startNanos = now
            LobbyAmbience.startup()
        }
        if (startNanos != 0L && Motion.reduced) return fadeOnly(canvas, now)
        val t = if (startNanos == 0L) 0f else seconds(startNanos, now).toFloat()
        val line = progress(reload, holding, started, now)
        return if (card) drawCard(canvas, t, line) else drawRise(canvas, t, line)
    }

    private fun fadeOnly(canvas: StartupCanvas, now: Long): Boolean {
        cardGrow = 1f
        if (fallbackNanos == 0L) fallbackNanos = now
        val t = seconds(fallbackNanos, now).toFloat() / FADE_ONLY_SECONDS
        val alpha = 1f - clamp(t)
        canvas.rect(0f, 0f, canvas.width, canvas.height, tint(DARK, alpha))
        val hero = hero(canvas)
        canvas.brand(hero.mark, hero.word, tint(WHITE, alpha))
        return t >= 1f
    }

    private fun drawRise(canvas: StartupCanvas, t: Float, line: Float): Boolean {
        val w = canvas.width
        val h = canvas.height
        val guiPerPx = if (rootWidth > 0f) w / rootWidth else 1f
        val from = hero(canvas)
        val fallback = navFallback(w, h)
        val toMark = navMark?.scaled(guiPerPx) ?: fallback.mark
        val toWord = navWord?.scaled(guiPerPx) ?: fallback.word
        val toLine = navLine?.scaled(guiPerPx)
        val m = cubic((t - 0.08f) / 1.0f)
        canvas.rect(0f, 0f, w, h, tint(DARK, 1f - smooth(0.04f, 0.92f, t)))
        canvas.brand(from.mark.mix(toMark, m), from.word.mix(toWord, m), tint(WHITE, 1f - smooth(1.2f, 1.62f, t)))
        val thick = max(2f / canvas.pixelScale, 1f)
        val lineFrom = Box(from.left, from.bottom + LINE_GAP * designUnit(w, h), from.width, thick)
        val lineTo = toLine ?: Box(toWord.x, toWord.y + toWord.h, 0f, thick)
        val current = lineFrom.mix(lineTo, cubic((t - 0.04f) / 0.92f))
        canvas.rect(current.x, current.y, current.w, current.h, tint(WHITE, 0.1f * (1f - smooth(0f, 0.25f, t))))
        canvas.rect(current.x, current.y, current.w * (if (t > 0f) 1f else line), current.h, tint(accent, 1f - smooth(1.15f, 1.6f, t)))
        return t >= RISE_SECONDS
    }

    private fun drawCard(canvas: StartupCanvas, t: Float, line: Float): Boolean {
        val w = canvas.width
        val h = canvas.height
        val guiPerPx = if (rootWidth > 0f) w / rootWidth else 1f
        val from = hero(canvas)
        val fallback = navFallback(w, h)
        val toMark = cardMark?.scaled(guiPerPx) ?: fallback.mark
        val toWord = cardWord?.scaled(guiPerPx) ?: fallback.word
        cardGrow = cubic((t - 0.55f) / 0.8f)
        val m = cubic((t - 0.05f) / 0.95f)
        canvas.rect(0f, 0f, w, h, tint(DARK, 1f - smooth(0.04f, 0.92f, t)))
        canvas.brand(from.mark.mix(toMark, m), from.word.mix(toWord, m), tint(mix(WHITE, cardInk, m), 1f - smooth(1.3f, 1.65f, t)))
        val thick = max(2f / canvas.pixelScale, 1f)
        val shrink = 1f - cubic(t / 0.5f)
        val lineW = from.width * shrink
        val lineY = from.bottom + LINE_GAP * designUnit(w, h)
        canvas.rect((w - from.width) / 2f, lineY, from.width, thick, tint(WHITE, 0.1f * (if (t > 0f) shrink else 1f)))
        canvas.rect((w - lineW) / 2f, lineY, lineW * (if (t > 0f) 1f else line), thick, tint(accent, 1f - smooth(0.35f, 0.55f, t)))
        return t >= CARD_SECONDS
    }

    private fun StartupCanvas.rect(x: Float, y: Float, w: Float, h: Float, argb: Int) {
        if (argb ushr 24 != 0) quad(StartupTextures.pixel, x, y, w, h, argb)
    }

    private fun StartupCanvas.brand(mark: Box, word: Box, argb: Int) {
        if (argb ushr 24 == 0) return
        quad(StartupTextures.mark(mark.h * pixelScale), mark.x, mark.y, mark.w, mark.h, argb)
        quad(StartupTextures.word(word.h * pixelScale), word.x, word.y, word.w, word.h, argb)
    }

    private fun hero(canvas: StartupCanvas): Lockup {
        val w = canvas.width
        val h = canvas.height
        val scale = min(w * HERO_WIDTH_SHARE, h * HERO_HEIGHT_SHARE) / (LOCKUP_MARK + LOCKUP_GAP + WORD_HEIGHT * WORD_ASPECT)
        val markSize = LOCKUP_MARK * scale
        val gap = LOCKUP_GAP * scale
        val wordHeight = WORD_HEIGHT * scale
        val wordWidth = wordHeight * WORD_ASPECT
        val total = markSize + gap + wordWidth
        val left = (w - total) / 2f
        val center = h * HERO_CENTER
        return Lockup(
            Box(left, center - markSize / 2f, markSize, markSize),
            Box(left + markSize + gap, center - wordHeight / 2f, wordWidth, wordHeight),
            center + markSize / 2f,
            left,
            total,
        )
    }

    private fun navFallback(w: Float, h: Float): Lockup {
        val unit = designUnit(w, h)
        val mark = LOCKUP_MARK * unit
        val word = WORD_HEIGHT * unit
        val center = 32f * unit
        val x = 28f * unit
        return Lockup(Box(x, center - mark / 2f, mark, mark), Box(x + mark + LOCKUP_GAP * unit, center - word / 2f, word * WORD_ASPECT, word))
    }

    private fun progress(reload: Float, holding: Boolean, started: Boolean, now: Long): Float {
        val target = when {
            started -> 1f
            !holding -> reload * LOAD_SHARE
            else -> LOAD_SHARE + HOLD_SHARE * (1f - exp(-seconds(holdNanos, now).toFloat() * 0.9f))
        }
        shownProgress = clamp(max(shownProgress, shownProgress * 0.88f + target * 0.12f))
        return shownProgress
    }

    private fun finished(mc: Minecraft) {
        active = false
        cardGrow = 1f
        StartupTextures.release(mc)
    }

    private fun seconds(from: Long, now: Long): Double = (now - from) / 1_000_000_000.0

    private fun designUnit(w: Float, h: Float) = min(w / DESIGN_WIDTH, h / DESIGN_HEIGHT)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun clamp(value: Float) = value.coerceIn(0f, 1f)

    private fun smooth(from: Float, to: Float, value: Float): Float = Easing.smoothstep((value - from) / (to - from))

    private fun cubic(value: Float): Float {
        val t = clamp(value)
        if (t < 0.5f) return 4f * t * t * t
        val u = -2f * t + 2f
        return 1f - u * u * u / 2f
    }

    private fun tint(rgb: Int, alpha: Float): Int = ((clamp(alpha) * 255f).toInt() shl 24) or (rgb and WHITE)

    private fun mix(from: Int, to: Int, t: Float): Int {
        val r = lerp((from shr 16 and 0xFF).toFloat(), (to shr 16 and 0xFF).toFloat(), t).toInt()
        val g = lerp((from shr 8 and 0xFF).toFloat(), (to shr 8 and 0xFF).toFloat(), t).toInt()
        val b = lerp((from and 0xFF).toFloat(), (to and 0xFF).toFloat(), t).toInt()
        return (r shl 16) or (g shl 8) or b
    }
}
