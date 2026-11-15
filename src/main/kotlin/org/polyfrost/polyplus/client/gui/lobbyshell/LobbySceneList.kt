package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import java.util.Locale

internal enum class SceneSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    WORLD("By world"),
    NAME("Name"),
}

internal enum class SceneFilter(val label: String) {
    ALL("All scenes"),
    FAVOURITES("Favourites"),
    AUTOMATIC("Automatic"),
    MANUAL("Manual"),
    UNAVAILABLE("Unavailable"),
    ;

    fun matches(entry: LobbySceneEntry): Boolean = when (this) {
        ALL -> true
        FAVOURITES -> entry.favourite
        AUTOMATIC -> entry.automatic
        MANUAL -> entry.manual
        UNAVAILABLE -> !LobbySceneStore.usable(entry)
    }
}

internal sealed interface SceneItem {
    val key: String

    object Automatic : SceneItem {
        override val key: String = "automatic"
    }

    object NoCaptures : SceneItem {
        override val key: String = "no-captures"
    }

    data class Header(override val key: String, val title: String) : SceneItem

    data class Scene(val entry: LobbySceneEntry) : SceneItem {
        override val key: String get() = "scene:${entry.id}"
    }
}

internal class SceneQuery(val text: String, val filter: SceneFilter, val sort: SceneSort)

internal fun matchesQuery(entry: LobbySceneEntry, query: String): Boolean {
    val words = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val text = listOf(entry.label, LobbySceneStore.worldName(entry), biomeName(entry.biome), entry.world).joinToString(" ").lowercase(Locale.ROOT)
    return words.all { it in text }
}

internal fun sceneItems(captures: List<LobbySceneEntry>, query: SceneQuery): List<SceneItem> {
    val shown = captures.filter { query.filter.matches(it) && matchesQuery(it, query.text) }.sortedWith(order(query.sort))
    val everything = query.filter == SceneFilter.ALL
    val idle = everything && query.text.isBlank()
    val (favourites, rest) = shown.partition { it.favourite }
    val bundled = if (everything) LobbySceneStore.bundledScenes.filter { matchesQuery(it, query.text) } else emptyList()
    return buildList {
        if (idle) add(SceneItem.Automatic)
        section("favourites", "Favourites", favourites)
        if (query.sort == SceneSort.WORLD) worlds(rest) else section("captures", "Your captures", rest)
        if (idle && captures.isEmpty()) {
            add(SceneItem.Header("header:captures", "Your captures"))
            add(SceneItem.NoCaptures)
        }
        section("bundled", "Polyfrost scenes", bundled)
    }
}

private fun MutableList<SceneItem>.section(key: String, title: String, entries: List<LobbySceneEntry>) {
    if (entries.isEmpty()) return
    add(SceneItem.Header("header:$key", title))
    entries.mapTo(this, SceneItem::Scene)
}

private fun MutableList<SceneItem>.worlds(entries: List<LobbySceneEntry>) {
    entries.groupBy { it.world }.values
        .sortedBy { LobbySceneStore.worldName(it.first()).lowercase(Locale.ROOT) }
        .forEach { group -> section("world:${group.first().world}", LobbySceneStore.worldName(group.first()), group) }
}

private fun order(sort: SceneSort): Comparator<LobbySceneEntry> = when (sort) {
    SceneSort.NEWEST, SceneSort.WORLD -> compareByDescending { it.capturedAt }
    SceneSort.OLDEST -> compareBy { it.capturedAt }
    SceneSort.NAME -> compareBy<LobbySceneEntry> { it.label.lowercase(Locale.ROOT) }.thenByDescending { it.capturedAt }
}
