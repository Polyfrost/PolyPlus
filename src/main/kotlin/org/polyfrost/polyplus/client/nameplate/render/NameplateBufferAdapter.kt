package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.4 && < 1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4f

object NameplateBufferAdapter {
    //? if >= 1.21.8 {
    private const val HIDDEN_ALPHA = 0
    //?} else {
    /^private const val HIDDEN_ALPHA = 4
    ^///?}

    private class Deferred {
        val pose = Matrix4f()
        var operation: Operation<*>? = null
        var font: Font? = null
        var text: Any? = null
        var x = 0f
        var y = 0f
        var color = 0
        var shadow = false
        var buffers: NameplateTarget? = null
        var mode = Font.DisplayMode.NORMAL
        var background = 0
        var light = 0
        var outline = 0

        fun store(
            operation: Operation<*>,
            font: Font,
            text: Any,
            x: Float,
            y: Float,
            color: Int,
            shadow: Boolean,
            pose: Matrix4f,
            buffers: NameplateTarget,
            mode: Font.DisplayMode,
            background: Int,
            light: Int,
        ) {
            this.operation = operation
            this.font = font
            this.text = text
            this.x = x
            this.y = y
            this.color = color
            this.shadow = shadow
            if (pose !== this.pose) {
                this.pose.set(pose)
            }
            this.buffers = buffers
            this.mode = mode
            this.background = background
            this.light = light
            outline = 0
        }

        fun flush() {
            val operation = operation ?: return
            val font = font ?: return
            val buffers = buffers ?: return
            val text = text ?: return
            clear()
            if (outline != 0) {
                val sequence = if (text is Component) text.visualOrderText else text as FormattedCharSequence
                font.drawInBatch8xOutline(sequence, x, y, hidden(color), outline, pose, buffers, light)
            }
            operation.call(font, text, x, y, color, shadow, pose, buffers, mode, background, light)
        }

        fun clear() {
            operation = null
            font = null
            text = null
            buffers = null
        }
    }

    private val name = Deferred()
    private val nameplatePose = Matrix4f()
    private val seeThroughPose = Matrix4f()
    private var naming = false

    private val lines = ArrayList<Deferred>()
    private var lineCount = 0
    private val displayLine = Deferred()
    private var display: TextDisplayEntityRenderState? = null
    private var displayNamed = false

    @JvmStatic
    fun renderName(state: EntityRenderState, name: Component, poseStack: PoseStack, buffers: NameplateTarget, draw: PlayerNameplateRenderer.NameDraw) {
        val tag = (state as? NameplateTagAccess)?.`polyplus$nameplateTag`()
        if (tag == null || naming) {
            draw.draw(name)
            return
        }
        naming = true
        try {
            PlayerNameplateRenderer.submit(tag, name, buffers, poseStack, draw)
        } finally {
            naming = false
            this.name.flush()
        }
    }

    @JvmStatic
    fun drawName(
        font: Font,
        text: Component,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean,
        pose: Matrix4f,
        buffers: NameplateTarget,
        mode: Font.DisplayMode,
        background: Int,
        light: Int,
        original: Operation<*>,
    ): Int {
        if (!naming) {
            return result(original.call(font, text, x, y, color, shadow, pose, buffers, mode, background, light))
        }
        val seeThrough = mode == Font.DisplayMode.SEE_THROUGH
        val recorded = if (seeThrough) seeThroughPose else nameplatePose
        recorded.set(pose)
        val changed = NameplateContext.observe(seeThrough, recorded, x, y, text.visualOrderText, shadow, color, background, 0, light)
        val drawColor = if (changed) visible(NameplateContext.rewrittenColor, color) else color
        val drawBackground = if (changed) NameplateContext.rewrittenBackground else background
        if (seeThrough) {
            return result(original.call(font, text, x, y, drawColor, shadow, pose, buffers, mode, drawBackground, light))
        }

        this.name.flush()
        this.name.store(original, font, text, x, y, drawColor, shadow, recorded, buffers, mode, drawBackground, light)
        this.name.outline = if (changed) NameplateContext.rewrittenOutline else 0
        return advance(font, text.visualOrderText, x, shadow)
    }

    @JvmStatic
    fun displayBackground(state: TextDisplayEntityRenderState, color: Int): Int =
        DisplayTagRenderer.backgroundColor((state as DisplayTagAccess).`polyplus$displayTag`(), color)

    @JvmStatic
    fun beginDisplay(state: TextDisplayEntityRenderState) {
        flushDisplay()
        val binding = (state as DisplayTagAccess).`polyplus$displayTag`()
        val usable = binding != null && state.cachedInfo != null && state.textRenderState != null && NameplateGate.usable(binding.nameplate)
        display = if (usable) state else null
        displayNamed = false
    }

    @JvmStatic
    fun endDisplay() {
        flushDisplay()
    }

    @JvmStatic
    fun drawDisplayLine(
        state: TextDisplayEntityRenderState,
        poseStack: PoseStack,
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean,
        pose: Matrix4f,
        buffers: NameplateTarget,
        mode: Font.DisplayMode,
        background: Int,
        light: Int,
        original: Operation<*>,
    ): Int {
        val binding = (state as DisplayTagAccess).`polyplus$displayTag`()
        val info = state.cachedInfo
        val textState = state.textRenderState
        if (display !== state || displayNamed || binding == null || info == null || textState == null) {
            return result(original.call(font, text, x, y, color, shadow, pose, buffers, mode, background, light))
        }
        if (text !== binding.nameLine) {
            if (lineCount == lines.size) {
                lines += Deferred()
            }
            lines[lineCount++].store(original, font, text, x, y, color, shadow, pose, buffers, mode, background, light)
            return advance(font, text, x, shadow)
        }

        displayNamed = true
        try {
            DisplayTagRenderer.submitLine(
                binding,
                info,
                textState.flags(),
                buffers,
                poseStack,
                x,
                y,
                text,
                shadow,
                mode == Font.DisplayMode.SEE_THROUGH,
                light,
                color,
                0,
            ) { line, outline, dx, dy ->
                displayLine.store(original, font, line, x + dx, y + dy, color, shadow, pose, buffers, mode, background, light)
                displayLine.outline = outline
            }
        } finally {
            flushDisplay()
        }
        return advance(font, text, x, shadow)
    }

    @JvmStatic
    fun endFrame() {
        naming = false
        name.clear()
        display = null
        for (i in 0 until lineCount) {
            lines[i].clear()
        }
        lineCount = 0
        displayLine.clear()
    }

    private fun flushDisplay() {
        for (i in 0 until lineCount) {
            lines[i].flush()
        }
        lineCount = 0
        displayLine.flush()
    }

    private fun visible(rewritten: Int, color: Int): Int =
        if (ARGB.alpha(rewritten) < HIDDEN_ALPHA && ARGB.alpha(color) >= HIDDEN_ALPHA) hidden(rewritten) else rewritten

    private fun hidden(color: Int): Int = ARGB.color(HIDDEN_ALPHA, color)

    private fun result(value: Any?): Int = value as? Int ?: 0

    private fun advance(font: Font, text: FormattedCharSequence, x: Float, shadow: Boolean): Int =
        (x + font.width(text)).toInt() + if (shadow) 1 else 0
}
*///?} elif = 1.21.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.nameplate.NameplateSelfVisibility
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle

object NameplateBufferAdapter {
    private const val MAX_NAME_DISTANCE_SQ = 64.0 * 64.0
    private const val WHITE_UV = 0.5f
    private const val BACKGROUND_DEPTH = 0.01f
    private const val BACKGROUND_BOTTOM = 9f

    private val scratch = PoseStack()
    private val deferred = DeferredLine()

    private var tag: NameplateTag? = null
    private var context: NameplateContext? = null
    private var mode = NameplateBundle.TextMode.VANILLA

    private var display: DisplayTagBinding? = null
    private var displayInfo: Display.TextDisplay.CachedInfo? = null
    private var displayFlags: Byte = 0

    @JvmStatic
    fun renderName(player: AbstractClientPlayer, name: Component, draw: PlayerNameplateRenderer.NameDraw) {
        val bound = if (context == null) bind(player) else null
        if (bound == null) {
            draw.draw(name)
            return
        }

        val nameplate = bound.nameplate
        val text = PlayerNameplateRenderer.resolveText(bound, name, player.uuid)
        val textMode = NameplateText.mode(nameplate.variant)
        val opened = NameplateContext.begin(nameplate.variant.ownsBackground, textMode != NameplateBundle.TextMode.VANILLA, text.outline)
        if (opened == null) {
            draw.draw(name)
            return
        }

        tag = bound
        mode = textMode
        context = opened
        try {
            draw.draw(NameplateText.restyle(name, bound.profileName, text, NameplateContext.externalDropShadow))
        } finally {
            opened.end()
            tag = null
            context = null
        }
    }

    @JvmStatic
    fun drawName(
        font: Font,
        name: Component,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        backgroundColor: Int,
        lightCoords: Int,
        original: Operation<Int>,
    ): Int {
        val open = context
        val bound = tag
        if (open == null || bound == null) {
            return original.call(font, name, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords)
        }

        val seeThrough = displayMode == Font.DisplayMode.SEE_THROUGH
        val text = name.visualOrderText
        NameplateContext.observe(seeThrough, pose, x, y, text, dropShadow, color, backgroundColor, 0, lightCoords)
        val hides = mode != NameplateBundle.TextMode.VANILLA
        val background = NameplateContext.rewrittenBackground
        if (seeThrough) {
            return drawText(font, name, text, x, y, color, dropShadow, pose, buffers, displayMode, background, 0, lightCoords, hides, original)
        }

        val layout = NameplateText.layout(font, text, bound.profileName)
        scratch.setIdentity()
        scratch.last().pose().set(pose)
        val frame = PlayerNameplateRenderer.decorate(bound, layout, scratch, buffers, x, y, color, lightCoords)

        val outline = NameplateContext.rewrittenOutline
        val drawn = drawText(font, name, text, x, y, color, dropShadow, pose, buffers, displayMode, background, outline, lightCoords, hides, original)
        if (hides) {
            NameplateLetters.submit(frame, open, text, layout, bound, mode)
        }
        return drawn
    }

    @JvmStatic
    fun bindDisplay(
        entity: Display.TextDisplay,
        state: Display.TextDisplay.TextRenderState,
        info: Display.TextDisplay.CachedInfo,
        progress: Float,
    ): Display.TextDisplay.CachedInfo {
        displayInfo = info
        displayFlags = state.flags()
        val bindable = entity.isPassenger && NameplateGate.active() && NameplateExtraction.textVisible(state, progress)
        display = if (bindable) NameplateExtraction.displayTag(entity, info, distanceSq(entity)) else null
        return info
    }

    @JvmStatic
    fun displayBackground(color: Int): Int = DisplayTagRenderer.backgroundColor(display, color)

    @JvmStatic
    fun drawDisplayText(
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        backgroundColor: Int,
        lightCoords: Int,
        poseStack: PoseStack,
        original: Operation<Int>,
    ): Int {
        val binding = display
        val info = displayInfo
        if (binding == null || info == null || text !== binding.nameLine) {
            return original.call(font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords)
        }

        deferred.reset(text)
        DisplayTagRenderer.submitLine(
            binding,
            info,
            displayFlags,
            buffers,
            poseStack,
            x,
            y,
            text,
            dropShadow,
            displayMode == Font.DisplayMode.SEE_THROUGH,
            lightCoords,
            color,
            0,
            deferred,
        )
        val line = deferred.text ?: text
        val outline = deferred.outline
        val lineX = x + deferred.dx
        val lineY = y + deferred.dy
        deferred.reset(null)
        if (outline != 0) {
            font.drawInBatch8xOutline(line, lineX, lineY, color, outline, pose, buffers, lightCoords)
            return 0
        }
        return original.call(font, line, lineX, lineY, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords)
    }

    @JvmStatic
    fun showOwnName(entity: LivingEntity): Boolean {
        val distance = Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(entity)
        return distance < MAX_NAME_DISTANCE_SQ && NameplateSelfVisibility.shouldShow(entity, distance)
    }

    @JvmStatic
    fun endFrame() {
        display = null
        displayInfo = null
        deferred.reset(null)
    }

    private fun bind(player: AbstractClientPlayer): NameplateTag? =
        if (NameplateGate.active()) NameplateExtraction.playerTag(player, distanceSq(player)) else null

    private fun distanceSq(entity: Entity): Double = Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(entity)

    private fun drawText(
        font: Font,
        name: Component,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        background: Int,
        outline: Int,
        lightCoords: Int,
        hides: Boolean,
        original: Operation<Int>,
    ): Int {
        if (!hides && outline == 0) {
            return original.call(font, name, x, y, color, dropShadow, pose, buffers, displayMode, background, lightCoords)
        }
        if (background != 0) {
            drawBackground(font, text, x, y, background, pose, buffers, displayMode, lightCoords)
        }
        if (!hides) {
            font.drawInBatch8xOutline(text, x, y, color, outline, pose, buffers, lightCoords)
        }
        return 0
    }

    private fun drawBackground(
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        argb: Int,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
    ) {
        val type = if (displayMode == Font.DisplayMode.SEE_THROUGH) {
            RenderType.textSeeThrough(NameplateCanvas.WHITE)
        } else {
            RenderType.text(NameplateCanvas.WHITE)
        }
        val x0 = x - 1f
        val y0 = y + BACKGROUND_BOTTOM
        val x1 = x + font.splitter.stringWidth(text) + 1f
        val y1 = y - 1f
        val buffer = buffers.getBuffer(type)
        buffer.addVertex(pose, x0, y0, BACKGROUND_DEPTH).setColor(argb).setUv(WHITE_UV, WHITE_UV).setLight(lightCoords)
        buffer.addVertex(pose, x1, y0, BACKGROUND_DEPTH).setColor(argb).setUv(WHITE_UV, WHITE_UV).setLight(lightCoords)
        buffer.addVertex(pose, x1, y1, BACKGROUND_DEPTH).setColor(argb).setUv(WHITE_UV, WHITE_UV).setLight(lightCoords)
        buffer.addVertex(pose, x0, y1, BACKGROUND_DEPTH).setColor(argb).setUv(WHITE_UV, WHITE_UV).setLight(lightCoords)
    }

    private class DeferredLine : DisplayTagRenderer.LineDraw {
        var text: FormattedCharSequence? = null
            private set
        var outline: Int = 0
            private set
        var dx: Int = 0
            private set
        var dy: Int = 0
            private set

        fun reset(line: FormattedCharSequence?) {
            text = line
            outline = 0
            dx = 0
            dy = 0
        }

        override fun draw(text: FormattedCharSequence, outlineColor: Int, dx: Int, dy: Int) {
            this.text = text
            outline = outlineColor
            this.dx = dx
            this.dy = dy
        }
    }
}
*///?}
