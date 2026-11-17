package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.runtime.Composable
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.screens.packs.PackSettingsPages
import java.util.concurrent.CopyOnWriteArrayList

internal interface SettingsPageProvider {
    fun handles(category: SettingsCategory): Boolean

    @Composable
    fun Page(category: SettingsCategory, parent: () -> Screen?)
}

internal enum class SettingsGroup { Game, OneClient }

internal class SettingsOwner(val name: String, val icon: String?, val tinted: Boolean = true) {
    companion object {
        val Minecraft = SettingsOwner("Minecraft", MINECRAFT_ICON)
        val OneClient = SettingsOwner("OneClient", null)

        fun mod(modId: String): SettingsOwner {
            val metadata = runCatching { FabricLoader.getInstance().getModContainer(modId).orElse(null)?.metadata }.getOrNull()
            val icon = metadata?.getIconPath(32)?.orElse(null)
            return SettingsOwner(metadata?.name ?: modId, icon, tinted = false)
        }
    }
}

internal sealed interface SettingsPlacement {
    data object End : SettingsPlacement

    data class After(val id: String) : SettingsPlacement

    data class Before(val id: String) : SettingsPlacement

    data class Replace(val id: String) : SettingsPlacement

    data class TabIn(val categoryId: String, val at: SettingsPlacement = End) : SettingsPlacement
}

internal class SettingsEntry(
    val id: String,
    val label: String,
    val icon: String,
    val owner: SettingsOwner = SettingsOwner.Minecraft,
    val group: SettingsGroup = SettingsGroup.Game,
    val placement: SettingsPlacement = SettingsPlacement.End,
    val external: ((parent: Screen?) -> Unit)? = null,
    internal val category: SettingsCategory? = null,
    val tabLabel: String? = null,
)

internal interface SettingsEntryProvider {
    fun entries(): List<SettingsEntry>

    @Composable
    fun Page(entry: SettingsEntry, parent: () -> Screen?)

    fun opened(categoryId: String) {}

    fun closed(categoryId: String) {}
}

internal class SettingsTab(val entry: SettingsEntry, val provider: SettingsEntryProvider?, val label: String) {
    val id: String get() = entry.id
}

internal class SettingsNode(val entry: SettingsEntry, val provider: SettingsEntryProvider?, val tabs: List<SettingsTab>) {
    val id: String get() = entry.id

    val providers: List<SettingsEntryProvider> get() = tabs.mapNotNull { it.provider }.distinct()
}

internal object SettingsPages {
    private val providers: List<SettingsPageProvider> = listOf(GeneralSettingsPages, InputSettingsPages, PackSettingsPages)
    private val extensions = CopyOnWriteArrayList<SettingsEntryProvider>()

    fun provider(category: SettingsCategory): SettingsPageProvider? = providers.firstOrNull { it.handles(category) }

    fun register(provider: SettingsEntryProvider) {
        if (provider !in extensions) extensions += provider
    }

    fun nodes(builtIn: List<SettingsEntry>, trailing: List<SettingsEntry> = emptyList()): List<SettingsNode> {
        val out = mutableListOf<Building>()
        builtIn.forEach { place(out, it, null) }
        extensions.forEach { provider ->
            runCatching { provider.entries() }.getOrDefault(emptyList()).forEach { entry -> place(out, entry, provider) }
        }
        trailing.forEach { place(out, it, null) }
        return out.map { node -> SettingsNode(node.entry, node.provider, unique(node.tabs)) }
    }

    private class Building(val entry: SettingsEntry, val provider: SettingsEntryProvider?) {
        val tabs = mutableListOf(entry to provider)
    }

    private fun place(out: MutableList<Building>, entry: SettingsEntry, provider: SettingsEntryProvider?) {
        val placement = entry.placement
        if (placement is SettingsPlacement.TabIn) {
            val host = out.firstOrNull { it.entry.id == placement.categoryId }
            if (host != null) {
                out.forEach { node -> node.tabs.removeAll { it.first.id == entry.id && node.entry.id != entry.id } }
                insert(host.tabs, entry to provider, placement.at) { it.first.id }
                return
            }
        }
        out.removeAll { it.entry.id == entry.id }
        val target = indexFor(out, placement) { it.entry.id }
        val index = target ?: (out.indexOfLast { it.entry.group == entry.group } + 1).takeIf { it > 0 } ?: out.size
        out.add(index.coerceIn(0, out.size), Building(entry, provider))
    }

    private fun <T> insert(list: MutableList<T>, item: T, at: SettingsPlacement, id: (T) -> String) {
        val index = indexFor(list, at, id) ?: list.size
        list.add(index.coerceIn(0, list.size), item)
    }

    private fun <T> indexFor(list: MutableList<T>, placement: SettingsPlacement, id: (T) -> String): Int? = when (placement) {
        is SettingsPlacement.After -> list.indexOfFirst { id(it) == placement.id }.takeIf { it >= 0 }?.plus(1)
        is SettingsPlacement.Before -> list.indexOfFirst { id(it) == placement.id }.takeIf { it >= 0 }
        is SettingsPlacement.Replace -> list.indexOfFirst { id(it) == placement.id }.takeIf { it >= 0 }?.also { list.removeAt(it) }
        is SettingsPlacement.TabIn, SettingsPlacement.End -> null
    }

    private fun unique(tabs: List<Pair<SettingsEntry, SettingsEntryProvider?>>): List<SettingsTab> {
        val base = tabs.map { (entry, _) -> entry.tabLabel ?: entry.label }
        val owned = base.mapIndexed { index, label ->
            val owner = tabs[index].first.owner.name
            val clash = base.count { it.equals(label, ignoreCase = true) } > 1
            if (clash && !label.contains(owner, ignoreCase = true)) "$owner $label" else label
        }
        val seen = mutableMapOf<String, Int>()
        return tabs.mapIndexed { index, (entry, provider) ->
            val label = owned[index]
            val count = seen.merge(label.lowercase(), 1, Int::plus)!!
            SettingsTab(entry, provider, if (count > 1) "$label $count" else label)
        }
    }
}

@OptIn(ExperimentalOneConfigUiApi::class)
internal object OneClientConfigs {
    private const val MAIN = PolyPlusConstants.ID + ".json"
    private const val PREFIX = PolyPlusConstants.ID + "-"
    private val suffix = Regex("""\s*\([^)]*\)\s*$""")

    fun entries(): List<SettingsEntry> {
        val trees = runCatching { ConfigManager.active().trees().toList() }.getOrDefault(emptyList())
        return trees
            .filter { owned(it) }
            .sortedWith(compareBy<Tree>({ it.id != MAIN }, { label(it) }))
            .map { tree ->
                val id = tree.id!!
                SettingsEntry(
                    "oneclient:config:$id",
                    label(tree),
                    icon(id),
                    SettingsOwner.OneClient,
                    SettingsGroup.OneClient,
                    external = { host -> SettingsMemory.returnOnClose(host) { open(id) } },
                )
            }
    }

    private fun owned(tree: Tree): Boolean {
        val id = tree.id ?: return false
        if (id != MAIN && !id.startsWith(PREFIX)) return false
        if (tree.title == null) return false
        return tree.getMetadata<Any?>("hidden").let { it == null || it == false }
    }

    private fun open(id: String) = OneConfigUI.open(OneConfigDestination.ModConfig(OneConfigPages.modConfig(id)))

    private fun label(tree: Tree): String {
        if (tree.id == MAIN) return "General"
        val title = tree.title.toString().replace(suffix, "").trim()
        val words = title.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return tree.id!!.removePrefix(PREFIX).removeSuffix(".json").replaceFirstChar { it.uppercase() }
        return (listOf(words.first()) + words.drop(1).map { word -> if (word.drop(1).any { it.isUpperCase() }) word else word.lowercase() }).joinToString(" ")
    }

    private fun icon(id: String): String = when (id.removePrefix(PREFIX).removeSuffix(".json")) {
        MAIN.removeSuffix(".json") -> "settings"
        "cosmetics" -> "assets/polyplus/ico/stars.svg"
        "lobby" -> SOCIAL_ASSETS + "minecraft-block.svg"
        "mainmenu" -> "layers"
        "party" -> SOCIAL_ASSETS + "users-01.svg"
        "pause" -> SOCIAL_ASSETS + "pause-circle.svg"
        else -> "cog"
    }
}
