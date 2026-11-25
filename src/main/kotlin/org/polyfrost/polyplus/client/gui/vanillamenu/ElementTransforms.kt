package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import net.minecraft.client.gui.components.AbstractWidget
import java.util.WeakHashMap

/** Scales the menu editor gave vanilla widgets; read by MixinAbstractWidgetScale for every widget, so it stays cheap. */
object ElementTransforms {
    private val scales = WeakHashMap<AbstractWidget, Float>()

    @Volatile
    private var any = false

    @JvmStatic
    fun scale(widget: AbstractWidget): Float = if (!any) 1f else scales[widget] ?: 1f

    fun set(widget: AbstractWidget, scale: Float) {
        if (scale == 1f) scales.remove(widget) else scales[widget] = scale
        any = scales.isNotEmpty()
    }
}
//?}
