package org.polyfrost.polyplus.client.emoji

import kotlinx.serialization.json.Json
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.util.regex.Pattern

object EmojiRegistry {
    private val LOGGER = LogManager.getLogger()
    private const val SHORTCODE = ":[a-z0-9_+\\-]+:"
    private const val PUA_START = 0xF0000
    private const val PUA_END = 0xFFFFD
    private val JSON = Json { ignoreUnknownKeys = true }

    private val shortcodes: Map<String, String> by lazy { loadMap("shortcodes.json") }

    private val unicode: Map<String, String> by lazy { loadMap("unicode.json") }

    private val aliasesSorted: List<String> by lazy { shortcodes.keys.sorted() }

    private val EMOJI_SHORTCODE: Pattern by lazy { Pattern.compile(SHORTCODE) }

    private val EMOJI: Pattern by lazy {
        val alts = StringBuilder(SHORTCODE)
        for (seq in unicode.keys) alts.append('|').append(Pattern.quote(seq))
        Pattern.compile(alts.toString())
    }

    private val unicodeToShortcode: Map<String, String> by lazy {
        val glyphToAlias = HashMap<String, String>()
        for ((alias, glyph) in shortcodes) {
            val existing = glyphToAlias[glyph]
            if (existing == null || alias.length < existing.length) glyphToAlias[glyph] = alias
        }
        val map = LinkedHashMap<String, String>()
        for (seq in unicode.keys.sortedByDescending { it.length }) {
            val alias = glyphToAlias[unicode[seq]] ?: continue
            map[seq] = ":$alias:"
        }
        map
    }

    private val UNICODE_SEQ: Pattern? by lazy {
        if (unicodeToShortcode.isEmpty()) return@lazy null
        val alts = StringBuilder()
        var first = true
        for (seq in unicodeToShortcode.keys) {
            if (!first) alts.append('|')
            alts.append(Pattern.quote(seq))
            first = false
        }
        Pattern.compile(alts.toString())
    }

    private fun loadMap(name: String): Map<String, String> {
        val stream = EmojiRegistry::class.java.getResourceAsStream("/assets/polyplus/emoji/$name")
        if (stream == null) {
            LOGGER.warn("Emoji map {} not found; chat emoji disabled", name)
            return emptyMap()
        }
        return runCatching {
            stream.bufferedReader().use { JSON.decodeFromString<Map<String, String>>(it.readText()) }
        }.onFailure { LOGGER.error("Failed to load emoji map {}", name, it) }.getOrDefault(emptyMap())
    }

    @JvmStatic
    fun resolve(alias: String): String? = shortcodes[alias]

    @JvmStatic
    fun suggestionRow(alias: String): net.minecraft.util.FormattedCharSequence {
        val comp = Component.empty()
        shortcodes[alias]?.let { comp.append(EmojiFont.glyph(it, Style.EMPTY)).append(Component.literal(" ")) }
        comp.append(Component.literal(":$alias:"))
        return comp.visualOrderText
    }

    private fun glyphFor(token: String): String? =
        if (token.length >= 2 && token[0] == ':' && token[token.length - 1] == ':') {
            shortcodes[token.substring(1, token.length - 1)]
        } else {
            unicode[token]
        }

    @JvmStatic
    fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        return aliasesSorted.asSequence().filter { it.startsWith(p) }.take(limit).toList()
    }

    @JvmStatic
    fun enabled(): Boolean = PolyPlusConfig.showChatEmoji

    @JvmStatic
    fun transformForViewer(component: Component?): Component? =
        if (component == null || !enabled()) component else transform(component)

    fun transform(component: Component): Component {
        val contents = component.contents
        val expandedSelf: MutableComponent? = when (contents) {
            is PlainTextContents -> expand(contents.text(), component.style, EMOJI_SHORTCODE)
            is TranslatableContents -> transformTranslatable(contents, component.style)
            else -> null
        }

        val siblings = component.siblings
        var siblingsChanged = false
        val newSiblings = ArrayList<Component>(siblings.size)
        for (sibling in siblings) {
            val transformed = transform(sibling)
            if (transformed !== sibling) siblingsChanged = true
            newSiblings.add(transformed)
        }

        if (expandedSelf == null && !siblingsChanged) return component

        val root: MutableComponent = expandedSelf ?: component.plainCopy().setStyle(component.style)
        for (sibling in newSiblings) root.append(sibling)
        return root
    }

    private fun transformTranslatable(
        contents: TranslatableContents,
        style: Style,
    ): MutableComponent? {
        val args: Array<out Any?> = contents.args
        var changed = false
        val newArgs = arrayOfNulls<Any>(args.size)
        for (i in args.indices) {
            val arg = args[i]
            when (arg) {
                is Component -> {
                    val t = transform(arg)
                    if (t !== arg) changed = true
                    newArgs[i] = t
                }
                is String -> {
                    val expanded = expand(arg, style, EMOJI_SHORTCODE)
                    if (expanded != null) { changed = true; newArgs[i] = expanded } else newArgs[i] = arg
                }
                else -> newArgs[i] = arg
            }
        }
        if (!changed) return null
        @Suppress("UNCHECKED_CAST")
        val rebuilt = TranslatableContents(contents.key, contents.fallback, newArgs as Array<Any>)
        return MutableComponent.create(rebuilt).setStyle(style)
    }

    private fun expand(text: String, style: Style, pattern: Pattern): MutableComponent? {
        val matcher = pattern.matcher(text)
        var root: MutableComponent? = null
        var last = 0
        while (matcher.find()) {
            val glyph = glyphFor(matcher.group()) ?: continue
            if (root == null) root = Component.empty().setStyle(style)
            if (matcher.start() > last) {
                root.append(Component.literal(text.substring(last, matcher.start())).setStyle(style))
            }
            root.append(EmojiFont.glyph(glyph, style))
            last = matcher.end()
        }
        val built = root ?: return null
        if (last < text.length) built.append(Component.literal(text.substring(last)).setStyle(style))
        return built
    }

    @JvmStatic
    fun styleInput(text: String, base: Style): net.minecraft.util.FormattedCharSequence? =
        expand(text, base, EMOJI)?.visualOrderText

    data class EmojiEntry(val glyph: String, val alias: String, val aliases: List<String>)

    sealed interface Segment {
        data class Text(val text: String) : Segment
        data class Emoji(val glyph: String) : Segment
    }

    val catalog: List<EmojiEntry> by lazy {
        val byGlyph = LinkedHashMap<String, MutableList<String>>()
        for ((alias, glyph) in shortcodes) byGlyph.getOrPut(glyph) { ArrayList() }.add(alias)
        byGlyph.entries
            .sortedBy { atlasIndex(it.key) }
            .map { (glyph, aliases) ->
                EmojiEntry(glyph, aliases.minByOrNull { it.length } ?: "", aliases.sorted())
            }
    }

    @JvmStatic
    @JvmOverloads
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<EmojiEntry> {
        val q = query.trim().lowercase().removePrefix(":").removeSuffix(":")
        if (q.isEmpty()) return if (limit >= catalog.size) catalog else catalog.take(limit)
        val prefixed = ArrayList<EmojiEntry>()
        val contained = ArrayList<EmojiEntry>()
        for (entry in catalog) {
            when {
                entry.aliases.any { it.startsWith(q) } -> prefixed.add(entry)
                entry.aliases.any { it.contains(q) } -> contained.add(entry)
            }
        }
        prefixed.addAll(contained)
        return if (limit >= prefixed.size) prefixed else prefixed.subList(0, limit).toList()
    }

    @JvmStatic
    fun atlasIndex(glyph: String): Int {
        if (glyph.isEmpty()) return -1
        val cp = glyph.codePointAt(0)
        return if (cp < PUA_START || cp > PUA_END) -1 else cp - PUA_START
    }

    @JvmStatic
    fun segments(text: String): List<Segment> {
        if (text.isEmpty()) return listOf(Segment.Text(text))
        val matcher = EMOJI.matcher(text)
        val out = ArrayList<Segment>()
        var last = 0
        while (matcher.find()) {
            val glyph = glyphFor(matcher.group()) ?: continue
            if (matcher.start() > last) out.add(Segment.Text(text.substring(last, matcher.start())))
            out.add(Segment.Emoji(glyph))
            last = matcher.end()
        }
        if (out.isEmpty()) return listOf(Segment.Text(text))
        if (last < text.length) out.add(Segment.Text(text.substring(last)))
        return out
    }

    @JvmStatic
    fun toShortcodes(text: String): String {
        if (!enabled() || text.isEmpty()) return text
        val pattern = UNICODE_SEQ ?: return text
        val matcher = pattern.matcher(text)
        if (!matcher.find()) return text
        val sb = StringBuilder(text.length)
        var last = 0
        do {
            sb.append(text, last, matcher.start())
            sb.append(unicodeToShortcode[matcher.group()])
            last = matcher.end()
        } while (matcher.find())
        sb.append(text, last, text.length)
        return sb.toString()
    }
}
