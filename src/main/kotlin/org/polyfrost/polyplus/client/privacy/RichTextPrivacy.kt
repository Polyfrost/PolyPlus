package org.polyfrost.polyplus.client.privacy

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.TranslatableContents
import org.apache.logging.log4j.LogManager
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

object RichTextPrivacy {

    private val logger = LogManager.getLogger("PolyPlus/RichTextPrivacy")

    private val BLOCKED_MODS = listOf("debugify")

    private val blockedKeys: Set<String> by lazy {
        ExploitPreventerCompat.block(BLOCKED_MODS)
        collectBlockedKeys()
    }

    fun warmUp() {
        blockedKeys
    }

    @JvmStatic
    fun unresolved(component: Component): String = unresolved(component, blockedKeys)

    internal fun unresolved(component: Component, blocked: Set<String>): String =
        buildString { flatten(component, blocked, this) }

    private fun flatten(component: Component, blocked: Set<String>, out: StringBuilder) {
        when (val contents = component.contents) {
            is TranslatableContents -> out.append(translate(contents, blocked))
            is KeybindContents ->
                if (contents.name in blocked) out.append(contents.name) else contents.visit(consumer(out))

            else -> contents.visit(consumer(out))
        }
        component.siblings.forEach { flatten(it, blocked, out) }
    }

    private fun translate(contents: TranslatableContents, blocked: Set<String>): String {
        if (contents.key in blocked) return contents.fallback ?: contents.key
        val args = contents.args.map { if (it is Component) unresolved(it, blocked) else it }
        return Component.translatableWithFallback(contents.key, contents.fallback, *args.toTypedArray()).string
    }

    private fun consumer(out: StringBuilder) = FormattedText.ContentConsumer<Unit> { text ->
        out.append(text)
        Optional.empty()
    }

    private fun collectBlockedKeys(): Set<String> {
        val keys = HashSet<String>()
        val found = mutableListOf<String>()
        for (id in BLOCKED_MODS) {
            FabricLoader.getInstance().getModContainer(id).ifPresent {
                collect(it, keys)
                found += id
            }
        }
        logger.info("Blocking {} translation keys from {}", keys.size, found.joinToString().ifEmpty { "nothing" })
        return keys
    }

    private fun collect(mod: ModContainer, into: MutableSet<String>) {
        for (root in mod.rootPaths) {
            runCatching { readLangFiles(root, into) }.onFailure {
                logger.warn("Could not read translations from {}", mod.metadata.id, it)
            }
        }
        mod.containedMods.forEach { collect(it, into) }
    }

    private fun readLangFiles(root: Path, into: MutableSet<String>) {
        val assets = root.resolve("assets")
        if (!Files.isDirectory(assets)) return
        Files.list(assets).use { namespaces ->
            namespaces.forEach { namespace ->
                val lang = namespace.resolve("lang").resolve("en_us.json")
                if (Files.isRegularFile(lang)) Files.newInputStream(lang).use { read(it, into) }
            }
        }
    }

    private fun read(stream: InputStream, into: MutableSet<String>) =
        Language.loadFromJson(stream) { key, _ -> into.add(key) }
}
