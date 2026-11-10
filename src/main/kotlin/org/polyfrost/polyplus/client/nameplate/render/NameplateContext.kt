package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import net.minecraft.util.FastColor.ARGB32 as ARGB
*///?}
import net.minecraft.util.FormattedCharSequence
import org.joml.Matrix4fc
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText

class NameplateContext private constructor() {
    private var active = false
    private var ownsBackground = false
    private var hidesText = false
    private var outlineRgb = ResolvedText.UNSET

    var submitted: Boolean = false
        private set
    var pose: Matrix4fc? = null
        private set
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var text: FormattedCharSequence? = null
        private set
    var color: Int = 0
        private set
    var lightCoords: Int = 0
        private set
    var dropShadow: Boolean = false
        private set
    var outlineColor: Int = 0
        private set

    var seeThrough: Boolean = false
        private set
    var seeThroughPose: Matrix4fc? = null
        private set
    var seeThroughColor: Int = 0
        private set
    var seeThroughLightCoords: Int = 0
        private set
    var seeThroughDropShadow: Boolean = false
        private set

    fun end() {
        active = false
    }

    companion object {
        private val INSTANCE = NameplateContext()

        var externalTextColor: Int = ResolvedText.UNSET
            private set
        var externalDropShadow: Boolean = false
            private set

        fun begin(ownsBackground: Boolean, hidesText: Boolean, outlineRgb: Int): NameplateContext? {
            val context = INSTANCE
            if (context.active) {
                return null
            }

            context.active = true
            context.ownsBackground = ownsBackground
            context.hidesText = hidesText
            context.outlineRgb = outlineRgb
            context.submitted = false
            context.seeThrough = false
            context.pose = null
            context.seeThroughPose = null
            context.text = null
            return context
        }

        var rewrittenColor: Int = 0
            private set
        var rewrittenBackground: Int = 0
            private set
        var rewrittenOutline: Int = 0
            private set

        @JvmStatic
        fun observe(
            seeThrough: Boolean,
            pose: Matrix4fc,
            x: Float,
            y: Float,
            text: FormattedCharSequence,
            dropShadow: Boolean,
            color: Int,
            backgroundColor: Int,
            outlineColor: Int,
            lightCoords: Int,
        ): Boolean {
            val context = INSTANCE
            if (!context.active) {
                return false
            }

            var outline = outlineColor
            if (seeThrough) {
                context.seeThrough = true
                context.seeThroughPose = pose
                context.seeThroughColor = color
                context.seeThroughLightCoords = lightCoords
                context.seeThroughDropShadow = dropShadow
            } else {
                context.submitted = true
                context.pose = pose
                context.x = x
                context.y = y
                context.text = text
                context.color = color
                context.lightCoords = lightCoords
                context.dropShadow = dropShadow
                if (outline == 0 && context.outlineRgb != ResolvedText.UNSET) {
                    outline = ARGB.color(ARGB.alpha(color), PortraitGuard.vertex(context.outlineRgb))
                }
                context.outlineColor = outline

                val rgb = color and 0xFFFFFF
                externalTextColor = if (rgb == 0xFFFFFF) ResolvedText.UNSET else rgb
                externalDropShadow = dropShadow
            }

            val background = if (context.ownsBackground) 0 else backgroundColor
            val rewritten = if (context.hidesText) ARGB.color(0, color) else color
            if (context.hidesText) {
                outline = 0
            }
            rewrittenColor = rewritten
            rewrittenBackground = background
            rewrittenOutline = outline
            return background != backgroundColor || rewritten != color || outline != outlineColor
        }
    }
}
//?}
