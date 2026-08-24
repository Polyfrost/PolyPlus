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

    private val ALLOWED_MODS = listOf("minecraft", "fabric-api", "modmenu", "placeholder-api", "sodium")

    private val allowedKeys: Set<String> by lazy {
        ExploitPreventerCompat.allow(ALLOWED_MODS)
        collectAllowedKeys()
    }

    fun warmUp() {
        allowedKeys
    }

    @JvmStatic
    fun unresolved(component: Component): String = buildString { flatten(component, this) }

    private fun flatten(component: Component, out: StringBuilder) {
        val contents = component.contents
        val unresolvable = when (contents) {
            is TranslatableContents -> contents.key.takeIf { contents.args.isNotEmpty() || it !in allowedKeys }
            is KeybindContents -> contents.name.takeIf { it !in allowedKeys }
            else -> null
        }
        if (unresolvable != null) {
            out.append(unresolvable)
        } else {
            contents.visit(FormattedText.ContentConsumer<Unit> { text ->
                out.append(text)
                Optional.empty()
            })
        }
        component.siblings.forEach { flatten(it, out) }
    }

    private fun collectAllowedKeys(): Set<String> {
        val keys = HashSet<String>()
        val loader = FabricLoader.getInstance()
        for (id in ALLOWED_MODS) {
            loader.getModContainer(id).ifPresent { collect(it, keys) }
        }
        if ("gui.done" !in keys) {
            runCatching {
                checkNotNull(Language::class.java.getResourceAsStream(VANILLA_LANG)).use { read(it, keys) }
            }.onFailure {
                logger.error("Could not read vanilla translations - sign and anvil text will not resolve", it)
            }
        }
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

    private const val VANILLA_LANG = "/assets/minecraft/lang/en_us.json"
}
