package org.polyfrost.polyplus.client.nameplate.render

//? if >= 26.3 {
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.feature.TextFeatureRenderer

object NameTagCapture {
    @JvmStatic
    fun observe(submit: TextFeatureRenderer.Submit): TextFeatureRenderer.Submit {
        val content = submit.content() as? TextFeatureRenderer.Content.Text ?: return submit
        val changed = NameTags.observe(
            submit.displayMode() == Font.DisplayMode.SEE_THROUGH,
            submit.pose(),
            content.x(),
            content.y(),
            null,
            content.string(),
            content.color(),
            content.backgroundColor(),
            submit.lightCoords(),
        )
        if (!changed) return submit
        return TextFeatureRenderer.Submit(
            submit.pose(),
            submit.displayMode(),
            submit.lightCoords(),
            TextFeatureRenderer.Content.Text(
                content.x(),
                content.y(),
                content.string(),
                content.dropShadow(),
                NameTags.rewrittenColor,
                NameTags.rewrittenBackground,
                content.outlineColor(),
            ),
        )
    }
}
//?} elif = 26.2 {
/*import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer

object NameTagCapture {
    @JvmStatic
    fun observe(submit: NameTagFeatureRenderer.Submit): NameTagFeatureRenderer.Submit {
        val changed = NameTags.observe(
            submit.displayMode() == Font.DisplayMode.SEE_THROUGH,
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            null,
            submit.color(),
            submit.backgroundColor(),
            submit.lightCoords(),
        )
        if (!changed) return submit
        return NameTagFeatureRenderer.Submit(
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            submit.lightCoords(),
            NameTags.rewrittenColor,
            NameTags.rewrittenBackground,
            submit.displayMode(),
        )
    }
}
*///?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.SubmitNodeStorage

object NameTagCapture {
    @JvmStatic
    fun observe(submit: SubmitNodeStorage.NameTagSubmit, seeThrough: Boolean): SubmitNodeStorage.NameTagSubmit {
        val changed = NameTags.observe(
            seeThrough,
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            null,
            submit.color(),
            submit.backgroundColor(),
            submit.lightCoords(),
        )
        if (!changed) return submit
        return SubmitNodeStorage.NameTagSubmit(
            submit.pose(),
            submit.x(),
            submit.y(),
            submit.text(),
            submit.lightCoords(),
            NameTags.rewrittenColor,
            NameTags.rewrittenBackground,
            submit.distanceToCameraSq(),
        )
    }
}
*///?}
