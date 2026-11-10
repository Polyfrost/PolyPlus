package org.polyfrost.polyplus.client.nameplate.render

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3

object NameplateSubmitAdapter {
    @JvmStatic
    fun submitName(
        state: EntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        attachment: Vec3?,
        offset: Int,
        name: Component,
        seeThrough: Boolean,
        lightCoords: Int,
        camera: CameraRenderState,
        original: Operation<Void>,
    ) {
        val tag = (state as? NameplateTagAccess)?.`polyplus$nameplateTag`()
        if (tag == null) {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, camera)
            return
        }
        PlayerNameplateRenderer.submit(tag, name, collector, poseStack) { restyled ->
            original.call(collector, poseStack, attachment, offset, restyled, seeThrough, lightCoords, camera)
        }
    }

    @JvmStatic
    fun observe(submit: TextFeatureRenderer.Submit): TextFeatureRenderer.Submit {
        val content = submit.content()
        if (content !is TextFeatureRenderer.Content.Text) {
            return submit
        }
        val changed = NameplateContext.observe(
            submit.displayMode() == Font.DisplayMode.SEE_THROUGH,
            submit.pose(),
            content.x(),
            content.y(),
            content.string(),
            content.dropShadow(),
            content.color(),
            content.backgroundColor(),
            content.outlineColor(),
            submit.lightCoords(),
        )
        if (!changed) {
            return submit
        }

        val invisible = ARGB.alpha(NameplateContext.rewrittenColor) == 0 &&
            NameplateContext.rewrittenOutline == 0 &&
            ARGB.alpha(NameplateContext.rewrittenBackground) == 0
        return TextFeatureRenderer.Submit(
            submit.pose(),
            submit.displayMode(),
            submit.lightCoords(),
            TextFeatureRenderer.Content.Text(
                content.x(),
                content.y(),
                if (invisible) FormattedCharSequence.EMPTY else content.string(),
                content.dropShadow(),
                NameplateContext.rewrittenColor,
                NameplateContext.rewrittenBackground,
                NameplateContext.rewrittenOutline,
            ),
        )
    }

    @JvmStatic
    fun submitDisplayBackground(
        state: TextDisplayEntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        original: Operation<Void>,
    ) {
        val background = DisplayTagRenderer.backgroundColor((state as DisplayTagAccess).`polyplus$displayTag`(), color)
        original.call(collector, poseStack, x0, y0, x1, y1, background, displayMode, lightCoords)
    }

    @JvmStatic
    fun submitDisplayText(
        state: TextDisplayEntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        color: Int,
        backgroundColor: Int,
        outlineColor: Int,
        original: Operation<Void>,
    ) {
        val binding = (state as DisplayTagAccess).`polyplus$displayTag`()
        val info = state.cachedInfo
        val textState = state.textRenderState
        if (binding == null || info == null || textState == null || text !== binding.nameLine) {
            original.call(collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor)
            return
        }
        DisplayTagRenderer.submitLine(
            binding,
            info,
            textState.flags(),
            collector,
            poseStack,
            x,
            y,
            text,
            dropShadow,
            displayMode == Font.DisplayMode.SEE_THROUGH,
            lightCoords,
            color,
            outlineColor,
        ) { line, outline, dx, dy ->
            original.call(collector, poseStack, x + dx, y + dy, line, dropShadow, displayMode, lightCoords, color, backgroundColor, outline)
        }
    }
}
//?} elif = 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3

object NameplateSubmitAdapter {
    private val outlinePose = PoseStack()
    private var outlined = false
    private var outlineX = 0f
    private var outlineY = 0f
    private var outlineText: FormattedCharSequence? = null
    private var outlineTextColor = 0
    private var outlineColor = 0
    private var outlineLight = 0

    @JvmStatic
    fun submitName(
        state: EntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        attachment: Vec3?,
        offset: Int,
        name: Component,
        seeThrough: Boolean,
        lightCoords: Int,
        camera: CameraRenderState,
        original: Operation<Void>,
    ) {
        val tag = (state as? NameplateTagAccess)?.`polyplus$nameplateTag`()
        if (tag == null) {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, camera)
            return
        }
        PlayerNameplateRenderer.submit(tag, name, collector, poseStack) { restyled ->
            outlined = false
            try {
                original.call(collector, poseStack, attachment, offset, restyled, seeThrough, lightCoords, camera)
                if (outlined) {
                    submitOutline(collector)
                }
            } finally {
                outlined = false
                outlineText = null
            }
        }
    }

    @JvmStatic
    fun observe(submit: NameTagFeatureRenderer.Submit): NameTagFeatureRenderer.Submit {
        val text = submit.text().visualOrderText
        val changed = NameplateContext.observe(
            submit.displayMode() == Font.DisplayMode.SEE_THROUGH,
            submit.pose(),
            submit.x(),
            submit.y(),
            text,
            false,
            submit.color(),
            submit.backgroundColor(),
            0,
            submit.lightCoords(),
        )
        if (!changed) {
            return submit
        }

        var color = NameplateContext.rewrittenColor
        val outline = NameplateContext.rewrittenOutline
        if (outline != 0) {
            outlined = true
            outlinePose.last().pose().set(submit.pose())
            outlineX = submit.x()
            outlineY = submit.y()
            outlineText = text
            outlineTextColor = color
            outlineColor = outline
            outlineLight = submit.lightCoords()
            color = ARGB.color(0, color)
        }
        return NameTagFeatureRenderer.Submit(
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            submit.lightCoords(),
            color,
            NameplateContext.rewrittenBackground,
            submit.displayMode(),
        )
    }

    private fun submitOutline(collector: SubmitNodeCollector) {
        val text = outlineText ?: return
        collector.submitText(
            outlinePose,
            outlineX,
            outlineY,
            text,
            false,
            Font.DisplayMode.NORMAL,
            outlineLight,
            outlineTextColor,
            0,
            outlineColor,
        )
    }

    @JvmStatic
    fun displayBackground(state: TextDisplayEntityRenderState, color: Int): Int =
        DisplayTagRenderer.backgroundColor((state as DisplayTagAccess).`polyplus$displayTag`(), color)

    @JvmStatic
    fun submitDisplayText(
        state: TextDisplayEntityRenderState,
        root: SubmitNodeCollector,
        collector: OrderedSubmitNodeCollector,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        color: Int,
        backgroundColor: Int,
        outlineColor: Int,
        original: Operation<Void>,
    ) {
        val binding = (state as DisplayTagAccess).`polyplus$displayTag`()
        val info = state.cachedInfo
        val textState = state.textRenderState
        if (binding == null || info == null || textState == null || text !== binding.nameLine) {
            original.call(collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor)
            return
        }
        DisplayTagRenderer.submitLine(
            binding,
            info,
            textState.flags(),
            root,
            poseStack,
            x,
            y,
            text,
            dropShadow,
            displayMode == Font.DisplayMode.SEE_THROUGH,
            lightCoords,
            color,
            outlineColor,
        ) { line, outline, dx, dy ->
            original.call(collector, poseStack, x + dx, y + dy, line, dropShadow, displayMode, lightCoords, color, backgroundColor, outline)
        }
    }
}
*///?} elif >= 1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState
//?} else {
/^import net.minecraft.client.renderer.state.CameraRenderState
^///?}
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fc

object NameplateSubmitAdapter {
    private const val NO_ARGB = -1

    private val outlinePose = PoseStack()
    private var naming = false
    private var outlinePending = false
    private var outlineMatrix: Matrix4fc? = null
    private var outlineX = 0f
    private var outlineY = 0f
    private var outlineText: FormattedCharSequence? = null
    private var outlineLight = 0
    private var outlineColor = 0
    private var outlineArgb = 0

    @JvmStatic
    fun submitName(
        state: EntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        attachment: Vec3?,
        offset: Int,
        name: Component,
        seeThrough: Boolean,
        lightCoords: Int,
        distanceToCameraSq: Double,
        camera: CameraRenderState,
        original: Operation<Void>,
    ) {
        val tag = (state as? NameplateTagAccess)?.`polyplus$nameplateTag`()
        if (tag == null || naming) {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera)
            return
        }
        PlayerNameplateRenderer.submit(tag, name, collector, poseStack) { restyled ->
            naming = true
            outlinePending = false
            try {
                original.call(collector, poseStack, attachment, offset, restyled, seeThrough, lightCoords, distanceToCameraSq, camera)
            } finally {
                naming = false
            }
            submitOutline(collector)
        }
    }

    @JvmStatic
    fun observe(submit: SubmitNodeStorage.NameTagSubmit, seeThrough: Boolean): SubmitNodeStorage.NameTagSubmit {
        if (!naming) {
            return submit
        }
        val text = submit.text().visualOrderText
        val changed = NameplateContext.observe(
            seeThrough,
            submit.pose(),
            submit.x(),
            submit.y(),
            text,
            false,
            submit.color(),
            submit.backgroundColor(),
            0,
            submit.lightCoords(),
        )
        if (!changed) {
            return submit
        }

        var color = NameplateContext.rewrittenColor
        val outline = if (seeThrough) 0 else NameplateContext.rewrittenOutline
        if (outline != 0) {
            outlinePending = true
            outlineMatrix = submit.pose()
            outlineX = submit.x()
            outlineY = submit.y()
            outlineText = text
            outlineLight = submit.lightCoords()
            outlineColor = color
            outlineArgb = outline
            color = ARGB.color(0, color)
        }
        return SubmitNodeStorage.NameTagSubmit(
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            submit.lightCoords(),
            color,
            NameplateContext.rewrittenBackground,
            submit.distanceToCameraSq(),
        )
    }

    private fun submitOutline(collector: SubmitNodeCollector) {
        val pending = outlinePending
        val matrix = outlineMatrix
        val text = outlineText
        outlinePending = false
        outlineMatrix = null
        outlineText = null
        if (!pending || matrix == null || text == null) {
            return
        }
        outlinePose.last().pose().set(matrix)
        collector.submitText(outlinePose, outlineX, outlineY, text, false, Font.DisplayMode.NORMAL, outlineLight, outlineColor, 0, outlineArgb)
    }

    @JvmStatic
    fun ownsDisplayBackground(state: TextDisplayEntityRenderState): Boolean =
        DisplayTagRenderer.backgroundColor((state as DisplayTagAccess).`polyplus$displayTag`(), NO_ARGB) != NO_ARGB

    @JvmStatic
    fun submitDisplayText(
        state: TextDisplayEntityRenderState,
        root: SubmitNodeCollector,
        target: OrderedSubmitNodeCollector,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        color: Int,
        backgroundColor: Int,
        outlineColor: Int,
        original: Operation<Void>,
    ) {
        val binding = (state as DisplayTagAccess).`polyplus$displayTag`()
        val info = state.cachedInfo
        val textState = state.textRenderState
        if (binding == null || info == null || textState == null || text !== binding.nameLine) {
            original.call(target, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor)
            return
        }
        DisplayTagRenderer.submitLine(
            binding,
            info,
            textState.flags(),
            root,
            poseStack,
            x,
            y,
            text,
            dropShadow,
            displayMode == Font.DisplayMode.SEE_THROUGH,
            lightCoords,
            color,
            outlineColor,
        ) { line, outline, dx, dy ->
            original.call(target, poseStack, x + dx, y + dy, line, dropShadow, displayMode, lightCoords, color, backgroundColor, outline)
        }
    }
}
*///?}
