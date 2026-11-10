package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.FormattedCharSequence
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText

internal object NameplateTextCache {
    private const val MAX_ENTRIES = 512
    private const val UNICODE_FONT = 1
    private const val JAPANESE_GLYPHS = 2

    val EMPTY: TagBlock = TagBlock.line(0, 0, 0)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > MAX_ENTRIES
    }
    private var listening = false

    fun block(shown: Component, profileName: String): TagBlock = entry(shown, profileName).block()

    fun entry(shown: Component, profileName: String): Entry {
        val minecraft = Minecraft.getInstance()
        listen(minecraft)
        val language = Language.getInstance()
        val options = fontOptions(minecraft)
        val entry = entries[profileName]
        if (entry != null && entry.matches(shown, language, options)) {
            return entry
        }

        val cacheable = cacheable(shown)
        if (entry == null) {
            return Entry(profileName, shown, language, options, cacheable).also { entries[profileName] = it }
        }
        entry.refresh(shown, language, options, cacheable)
        return entry
    }

    fun placed(profileName: String, layout: NameplateText.Layout, background: PixelRect): TagBlock =
        entries[profileName]?.placed(layout, background) ?: build(layout, background)

    private fun build(layout: NameplateText.Layout, background: PixelRect): TagBlock =
        TagBlock.line(layout.textWidth, layout.nameX, layout.nameWidth).placed(layout.prefixWidth, background)

    private fun listen(minecraft: Minecraft) {
        if (listening) {
            return
        }
        val manager = minecraft.resourceManager as? ReloadableResourceManager ?: return
        manager.registerReloadListener(ResourceManagerReloadListener { entries.clear() })
        listening = true
    }

    private fun fontOptions(minecraft: Minecraft): Int {
        val options = minecraft.options
        var bits = 0
        if (options.forceUnicodeFont().get()) {
            bits = bits or UNICODE_FONT
        }
        if (options.japaneseGlyphVariants().get()) {
            bits = bits or JAPANESE_GLYPHS
        }
        return bits
    }

    private fun cacheable(component: Component): Boolean {
        val contents = component.contents
        if (contents is KeybindContents) {
            return false
        }
        if (contents is TranslatableContents) {
            for (arg in contents.args) {
                if (arg is Component && !cacheable(arg)) {
                    return false
                }
            }
        }
        for (sibling in component.siblings) {
            if (!cacheable(sibling)) {
                return false
            }
        }
        return true
    }

    class Entry internal constructor(
        private val profileName: String,
        private var name: Component,
        private var language: Language,
        private var options: Int,
        private var cacheable: Boolean,
    ) {
        private var block: TagBlock? = null
        private var restyled: Component? = null
        private var restyledText: ResolvedText? = null
        private var restyledShadow = false
        private var layout: NameplateText.Layout? = null
        private var layoutText: FormattedCharSequence? = null
        private var layoutFont: Font? = null
        private var placed: TagBlock? = null

        internal fun matches(shown: Component, language: Language, options: Int): Boolean =
            cacheable && language === this.language && options == this.options && (shown === name || shown == name)

        internal fun refresh(shown: Component, language: Language, options: Int, cacheable: Boolean) {
            name = shown
            this.language = language
            this.options = options
            this.cacheable = cacheable
            block = null
            restyled = null
            restyledText = null
            layoutText = null
            layoutFont = null
        }

        fun block(): TagBlock {
            block?.let { return it }
            val layout = NameplateText.layout(Minecraft.getInstance().font, name.visualOrderText, profileName)
            return TagBlock.line(layout.textWidth, layout.nameX, layout.nameWidth).also { block = it }
        }

        fun restyled(text: ResolvedText, submitDropShadow: Boolean): Component {
            val cached = restyled
            if (cached != null && text === restyledText && submitDropShadow == restyledShadow) {
                return cached
            }
            val built = NameplateText.restyle(name, profileName, text, submitDropShadow)
            restyled = built
            restyledText = text
            restyledShadow = submitDropShadow
            return built
        }

        fun layout(font: Font, text: FormattedCharSequence): NameplateText.Layout {
            val target = layout ?: NameplateText.Layout().also { layout = it }
            if (cacheable && text === layoutText && font === layoutFont) {
                return target
            }
            NameplateText.layout(font, text, profileName, target)
            layoutText = text
            layoutFont = font
            return target
        }

        fun placed(layout: NameplateText.Layout, background: PixelRect): TagBlock {
            val cached = placed
            if (cached != null &&
                cached.width == layout.textWidth &&
                cached.nameX == layout.nameX &&
                cached.nameWidth == layout.nameWidth &&
                cached.prefixWidth == layout.prefixWidth &&
                cached.background == background
            ) {
                return cached
            }
            return build(layout, background).also { placed = it }
        }
    }
}
//?}
