package org.polyfrost.polyplus.client.gui

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.AbstractWidgetAccessor
import org.polyfrost.polyplus.mixin.client.access.TooltipAccessor
//?}

//? if >= 1.21.9 {
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
//?}

//? if > 1.8.9 {
internal object WidgetPress {
    fun press(widget: AbstractWidget) {
        ClientPlatform.runOnMain {
            if (!widget.isActive) return@runOnMain
            if (widget is AbstractButton) {
                //? if >= 1.21.9 {
                widget.onPress(KeyEvent(InputConstants.KEY_RETURN, 0, 0))
                //?} else {
                /*widget.onPress()
                *///?}
                return@runOnMain
            }
            val x = (widget.x + widget.width / 2).toDouble()
            val y = (widget.y + widget.height / 2).toDouble()
            //? if >= 1.21.9 {
            val event = MouseButtonEvent(x, y, MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0))
            widget.mouseClicked(event, false)
            widget.mouseReleased(event)
            //?} else {
            /*widget.mouseClicked(x, y, 0)
            widget.mouseReleased(x, y, 0)
            *///?}
        }
    }

    fun tooltip(widget: AbstractWidget): String? {
        val tooltip = (widget as AbstractWidgetAccessor).`polyplus$tooltip`().get() ?: return null
        return Platform.i18n().getUnformattedText((tooltip as TooltipAccessor).`polyplus$message`())
    }
}
//?}
