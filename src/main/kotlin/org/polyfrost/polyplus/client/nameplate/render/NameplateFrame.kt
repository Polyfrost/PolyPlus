package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import kotlin.math.roundToInt
import kotlin.math.sqrt
//? if >= 26.2 {
import net.minecraft.client.renderer.SubmitNodeCollection
import net.minecraft.client.renderer.feature.CustomFeatureRenderer
//?}
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import net.minecraft.util.FastColor.ARGB32 as ARGB
*///?}
//? if < 1.21.10 {
/*import net.minecraft.client.Minecraft
*///?}
//? if < 1.21.8 {
/*import net.minecraft.util.Mth
*///?}

class NameplateFrame(
    val poseStack: PoseStack,
    val collector: NameplateTarget,
    val x: Float,
    val y: Float,
    val alpha: Float,
    val lightCoords: Int,
    val block: TagBlock,
) {
    val cameraDistance: Float
        get() {
            val pose = poseStack.last().pose()
            return sqrt(pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32())
        }

    fun submitGeometry(order: Int, renderType: NameplateLayer, renderer: NameplateGeometry) {
        translated {
            //? if >= 1.21.10 {
            collector.order(order).submitCustomGeometry(poseStack, renderType, renderer)
            //?} else {
            /*draw(renderType, renderer)
            *///?}
        }
    }

    fun submitAfterTerrain(order: Int, renderType: NameplateLayer, renderer: NameplateGeometry) {
        translated {
            //? if >= 26.2 {
            val target = collector.order(order)
            if (target is SubmitNodeCollection) {
                target.afterTerrain.submit(CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, renderer))
            } else {
                target.submitCustomGeometry(poseStack, renderType, renderer)
            }
            //?} elif >= 1.21.10 {
            /*collector.order(order).submitCustomGeometry(poseStack, renderType, renderer)
            *///?} else {
            /*draw(renderType, renderer)
            *///?}
        }
    }

    fun submitGeometry(renderType: NameplateLayer, renderer: NameplateGeometry) {
        translated {
            //? if >= 1.21.10 {
            collector.submitCustomGeometry(poseStack, renderType, renderer)
            //?} else {
            /*draw(renderType, renderer)
            *///?}
        }
    }

    private inline fun translated(submit: () -> Unit) {
        poseStack.pushPose()
        try {
            poseStack.translate(x, y, 0f)
            submit()
        } finally {
            poseStack.popPose()
        }
    }

    fun submitText(
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        color: Int,
        outlineColor: Int,
    ) {
        //? if >= 1.21.10 {
        collector.submitText(poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, 0, outlineColor)
        //?} else {
        /*val font = Minecraft.getInstance().font
        val pose = poseStack.last().pose()
        //? if < 1.21.8 {
        /^val outline = if (ARGB.alpha(outlineColor) < MIN_FONT_ALPHA) 0 else outlineColor
        if (ARGB.alpha(color) < MIN_FONT_ALPHA && outline == 0) {
            return
        }
        ^///?} else {
        val outline = outlineColor
        //?}
        if (outline != 0) {
            font.drawInBatch8xOutline(text, x, y, color, outline, pose, collector, lightCoords)
        } else {
            font.drawInBatch(text, x, y, color, dropShadow, pose, collector, displayMode, 0, lightCoords)
        }
        *///?}
    }

    fun detached(target: PoseStack): NameplateFrame {
        //? if >= 1.21.10 {
        target.last().set(poseStack.last())
        //?} else {
        /*target.last().pose().set(poseStack.last().pose())
        target.last().normal().set(poseStack.last().normal())
        *///?}
        return NameplateFrame(target, collector, x, y, alpha, lightCoords, block)
    }

    //? if < 1.21.10 {
    /*private fun draw(renderType: NameplateLayer, renderer: NameplateGeometry) {
        renderer.render(poseStack.last(), collector.getBuffer(renderType))
    }
    *///?}

    companion object {
        //? if < 1.21.8 {
        /*private const val CHANNEL_MAX = 255f
        private const val MIN_FONT_ALPHA = 4
        *///?}

        fun fade(argb: Int, alpha: Float): Int = ARGB.color((ARGB.alpha(argb) * alpha).roundToInt(), argb)

        fun alphaOf(argb: Int): Float =
            //? if >= 1.21.4 {
            ARGB.alphaFloat(argb)
            //?} else {
            /*ARGB.alpha(argb) / CHANNEL_MAX
            *///?}

        fun withAlpha(alpha: Float, rgb: Int): Int =
            //? if >= 1.21.8 {
            ARGB.color(alpha, rgb)
            //?} else {
            /*ARGB.color(Mth.floor(alpha * CHANNEL_MAX), rgb)
            *///?}
    }
}

object Vertices {
    fun add(buffer: NameplateVertexSink, pose: NameplatePose, x: Float, y: Float, z: Float): NameplateVertexSink {
        val m = pose.pose()
        return buffer.addVertex(
            m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
            m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
            m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
        )
    }
}
//?} else {
/*import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyVertexSink
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import kotlin.math.floor
import kotlin.math.roundToInt

class NameplateFrame(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val lightCoords: Int,
    val block: TagBlock,
) {
    fun submitGeometry(order: Int, renderType: NameplateLayer, renderer: NameplateGeometry) {
        LegacyVertexSink.draw(renderType, x, y, renderer)
    }

    fun submitGeometry(renderType: NameplateLayer, renderer: NameplateGeometry) {
        LegacyVertexSink.draw(renderType, x, y, renderer)
    }

    companion object {
        private const val CHANNEL_MAX = 255f
        private const val RGB = 0xFFFFFF
        private const val ALPHA_SHIFT = 24

        fun fade(argb: Int, alpha: Float): Int = (((argb ushr ALPHA_SHIFT) * alpha).roundToInt() shl ALPHA_SHIFT) or (argb and RGB)

        fun alphaOf(argb: Int): Float = (argb ushr ALPHA_SHIFT) / CHANNEL_MAX

        fun withAlpha(alpha: Float, rgb: Int): Int = (floor(alpha * CHANNEL_MAX).toInt() shl ALPHA_SHIFT) or (rgb and RGB)
    }
}

object Vertices {
    fun add(buffer: NameplateVertexSink, pose: NameplatePose, x: Float, y: Float, z: Float): NameplateVertexSink = buffer.addVertex(x, y, z)
}
*///?}
