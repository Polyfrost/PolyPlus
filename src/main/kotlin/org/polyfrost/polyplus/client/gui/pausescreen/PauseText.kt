package org.polyfrost.polyplus.client.gui.pausescreen

//? if > 1.8.9 {
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.chat.contents.TranslatableFormatException
import org.polyfrost.polyplus.mixin.client.access.TranslatableContentsInvoker
import java.util.Optional

// resolves widget text the way it reads without server resource packs; the vanilla pause screen still shows the
// server's version, since only it draws with the server's fonts
internal object PauseText {
    fun of(component: Component): String = buildString { flatten(component, this) }

    private fun flatten(component: Component, out: StringBuilder) {
        when (val contents = component.contents) {
            is TranslatableContents -> out.append(translate(contents))
            else -> contents.visit(FormattedText.ContentConsumer<Unit> { text ->
                out.append(text)
                Optional.empty()
            })
        }
        component.siblings.forEach { flatten(it, out) }
    }

    private fun translate(contents: TranslatableContents): String {
        val template = (Language.getInstance() as? ClientTranslationsAccess)?.`polyplus$clientTranslation`(contents.key)
            ?: return MutableComponent.create(contents).string
        val args = contents.args.map { if (it is Component) Component.literal(of(it)) else it }.toTypedArray()
        val out = StringBuilder()
        return try {
            (TranslatableContents(contents.key, contents.fallback, args) as TranslatableContentsInvoker)
                .`polyplus$decomposeTemplate`(template) { out.append(it.string) }
            out.toString()
        } catch (_: TranslatableFormatException) {
            template
        }
    }
}
//?}
