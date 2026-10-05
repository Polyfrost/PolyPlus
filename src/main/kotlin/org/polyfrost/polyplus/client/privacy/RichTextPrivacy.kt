package org.polyfrost.polyplus.client.privacy

//? if > 1.8.9 {
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.chat.contents.TranslatableFormatException
import net.minecraft.server.packs.PackResources
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.mixin.client.access.TranslatableContentsInvoker
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.BiConsumer

//? if >= 1.21.11 {
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources
//?} else {
/*import net.fabricmc.fabric.api.resource.ModResourcePack as ModPackResources
*///?}
//?}

object RichTextPrivacy {
    //? if > 1.8.9 {
    private val logger = LogManager.getLogger("PolyPlus/RichTextPrivacy")

    private val BLOCKED_MODS = listOf(
        "debugify",
        "wwaypoints",
    )

    // includes the mods they bundle, whose translations are blocked along with theirs
    private val blockedMods: List<ModContainer> by lazy {
        BLOCKED_MODS.mapNotNull { FabricLoader.getInstance().getModContainer(it).orElse(null) }.flatMap(::withContained)
    }

    private val blockedModIds: Set<String> by lazy { blockedMods.mapTo(HashSet()) { it.metadata.id } }

    private val blockedKeys: Set<String> by lazy {
        ExploitPreventerCompat.block(BLOCKED_MODS)
        collectBlockedKeys()
    }

    fun warmUp() {
        blockedKeys
    }

    @JvmStatic
    fun unresolved(component: Component, resolved: String): String =
        if (ExploitPreventerCompat.filtersTranslations()) resolved else unresolved(component, resolved, blockedKeys)

    // other mods may have adjusted the resolved text too, so it is only replaced if it would reveal a blocked key
    internal fun unresolved(component: Component, resolved: String, blocked: Set<String>): String =
        unresolved(component, blocked).takeIf { it != component.string } ?: resolved

    internal fun unresolved(component: Component, blocked: Set<String>): String =
        buildString { flatten(component, blocked, this) }

    // other packs can translate the blocked keys too (a server's resource pack, for example), and a client without the
    // blocked mods would still show those translations
    @JvmStatic
    fun trackUnblocked(pack: PackResources, output: BiConsumer<String, String>, unblocked: MutableMap<String, String>): BiConsumer<String, String> {
        val keys = blockedKeys
        if (keys.isEmpty() || pack is ModPackResources && pack.fabricModMetadata.id in blockedModIds) return output
        return BiConsumer { key, value ->
            output.accept(key, value)
            if (key in keys) unblocked[key] = value
        }
    }

    private fun flatten(component: Component, blocked: Set<String>, out: StringBuilder) {
        when (val contents = component.contents) {
            is TranslatableContents -> out.append(translate(contents, blocked))
            // without the mod, its key mapping isn't registered and vanilla translates the name instead
            is KeybindContents ->
                if (contents.name in blocked) out.append(formatMissing(TranslatableContents(contents.name, null, emptyArray())))
                else contents.visit(consumer(out))

            else -> contents.visit(consumer(out))
        }
        component.siblings.forEach { flatten(it, blocked, out) }
    }

    private fun translate(contents: TranslatableContents, blocked: Set<String>): String {
        val args = contents.args.map { if (it is Component) unresolved(it, blocked) else it }.toTypedArray()
        if (contents.key !in blocked) return Component.translatableWithFallback(contents.key, contents.fallback, *args).string
        return formatMissing(TranslatableContents(contents.key, contents.fallback, args))
    }

    // formats the key the way a client without the blocked mods would, using other packs' translation of it or else
    // the fallback, with the args filled in
    private fun formatMissing(contents: TranslatableContents): String {
        val unblocked = (Language.getInstance() as? UnblockedTranslationsAccess)?.`polyplus$unblockedTranslation`(contents.key)
        val template = unblocked ?: contents.fallback ?: contents.key
        val out = StringBuilder()
        return try {
            (contents as TranslatableContentsInvoker).`polyplus$decomposeTemplate`(template) { out.append(it.string) }
            out.toString()
        } catch (_: TranslatableFormatException) {
            template
        }
    }

    private fun consumer(out: StringBuilder) = FormattedText.ContentConsumer<Unit> { text ->
        out.append(text)
        Optional.empty()
    }

    private fun withContained(mod: ModContainer): List<ModContainer> = listOf(mod) + mod.containedMods.flatMap(::withContained)

    private fun collectBlockedKeys(): Set<String> {
        val keys = HashSet<String>()
        for (mod in blockedMods) {
            for (root in mod.rootPaths) {
                runCatching { readLangFiles(root, keys) }.onFailure {
                    logger.warn("Could not read translations from {}", mod.metadata.id, it)
                }
            }
        }
        logger.info("Blocking {} translation keys from {}", keys.size, blockedModIds.joinToString().ifEmpty { "nothing" })
        return keys
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
    //?} else {
    /*fun warmUp() = Unit
    *///?}
}
