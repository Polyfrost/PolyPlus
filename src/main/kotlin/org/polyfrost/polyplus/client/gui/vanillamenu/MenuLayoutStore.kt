package org.polyfrost.polyplus.client.gui.vanillamenu

import androidx.compose.runtime.mutableIntStateOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.VanillaMenuLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * One piece of a menu. Built-in pieces only store what the player changed (a null anchor means "where the template
 * puts it"), added pieces store everything.
 *
 * For the vanilla menu x/y are GUI pixels from [anchor]: the same corner, edge middle or center of the piece sits that
 * far from the same point of the screen, so a piece in the bottom right stays there when the window changes size.
 * The Compose menus (lobby, PolyPlus) have no anchor; x/y are dp from where the menu normally puts the piece.
 */
class MenuElement(
    var id: String = "",
    var type: String = TYPE_BUILTIN,
    var anchor: String? = null,
    var x: Float = 0f,
    var y: Float = 0f,
    var w: Float? = null,
    var h: Float? = null,
    var scale: Float = 1f,
    var opacity: Float = 1f,
    var z: Int? = null,
    var visible: Boolean = true,
    /** null: the template's group, "": no group. */
    var group: String? = null,
    var props: MutableMap<String, String> = LinkedHashMap(),
) {
    val builtin: Boolean get() = type == TYPE_BUILTIN

    fun prop(key: String, fallback: String = ""): String = props[key] ?: fallback

    companion object {
        const val TYPE_BUILTIN = "builtin"
    }
}

class MenuScopeLayout(
    var elements: MutableList<MenuElement> = ArrayList(),
    /** Offsets from the old editor (id or shared key to dx,dy), turned into anchored positions on first build. */
    var legacyOffsets: MutableMap<String, List<Float>> = LinkedHashMap(),
    var legacyHidden: MutableList<String> = ArrayList(),
)

class MenuEditorPrefs(
    var gridSnap: Boolean = true,
    var gridSize: Int = 8,
    var barX: Int = Int.MIN_VALUE,
    var barY: Int = Int.MIN_VALUE,
    var panelOpen: Boolean = true,
)

class MenuLayoutFile(
    var version: Int = MenuLayoutStore.VERSION,
    var scopes: MutableMap<String, MenuScopeLayout> = LinkedHashMap(),
    var editor: MenuEditorPrefs = MenuEditorPrefs(),
)

/** config/polyplus/menu-layout.json: one layout per menu ("vanilla", "lobby", "polyplus"), with undo history in memory. */
object MenuLayoutStore {
    const val VERSION = 1
    const val VANILLA = "vanilla"
    private const val HISTORY = 50
    private val LOGGER = LogManager.getLogger("PolyPlus/MenuLayout")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val compact = GsonBuilder().create()

    /** Bumped on every change, so Compose menus redraw. */
    val revision = mutableIntStateOf(0)

    private var data: MenuLayoutFile? = null
    private val undo = HashMap<String, ArrayDeque<String>>()
    private val redo = HashMap<String, ArrayDeque<String>>()

    val path: Path get() = FabricLoader.getInstance().configDir.resolve("polyplus").resolve("menu-layout.json")
    val imageDir: Path get() = FabricLoader.getInstance().configDir.resolve("polyplus").resolve("menu-images")

    private fun file(): MenuLayoutFile {
        data?.let { return it }
        val loaded = runCatching {
            if (Files.exists(path)) gson.fromJson(Files.readString(path), MenuLayoutFile::class.java) else null
        }.onFailure { LOGGER.error("Couldn't read {}, starting from the templates", path, it) }.getOrNull() ?: MenuLayoutFile()
        // gson leaves fields it didn't find null even when Kotlin says they can't be
        @Suppress("SENSELESS_COMPARISON")
        if (loaded.scopes == null) loaded.scopes = LinkedHashMap()
        @Suppress("SENSELESS_COMPARISON")
        if (loaded.editor == null) loaded.editor = MenuEditorPrefs()
        data = loaded
        migrate(loaded)
        return loaded
    }

    /** The old editor kept offsets and hidden pieces as strings in the main menu config; read them once, then clear them. */
    private fun migrate(file: MenuLayoutFile) {
        val offsets = PolyPlusMainMenuConfig.menuLayoutOffsets
        val hidden = PolyPlusMainMenuConfig.menuHidden
        if (offsets.isEmpty() && hidden.isEmpty()) return
        val oldVanilla = when (PolyPlusMainMenuConfig.vanillaMenuLayout) {
            VanillaMenuLayout.CENTERED -> "vanilla-centered"
            VanillaMenuLayout.SIDES -> "vanilla-sides"
            VanillaMenuLayout.ONECLIENT -> "vanilla-oneclient"
        }
        fun target(oldScope: String): String? = when (oldScope) {
            "lobby", "polyplus" -> oldScope
            oldVanilla -> VANILLA
            else -> null // another preset's tweaks don't fit the current template
        }
        offsets.split(';').forEach { entry ->
            val key = entry.substringBefore('=', "")
            val values = entry.substringAfter('=', "").split(',').mapNotNull { it.toFloatOrNull() }
            if (key.isEmpty() || values.size != 2) return@forEach
            val scope = target(key.substringBefore('/')) ?: return@forEach
            val id = key.substringAfter('/')
            val layout = file.scopes.getOrPut(scope) { MenuScopeLayout() }
            if (scope == VANILLA) {
                layout.legacyOffsets[id] = values
            } else {
                val element = layout.elements.firstOrNull { it.id == id } ?: MenuElement(id).also(layout.elements::add)
                element.x = values[0]
                element.y = values[1]
            }
        }
        hidden.split(',').filter { it.isNotEmpty() }.forEach { key ->
            val scope = target(key.substringBefore('/')) ?: return@forEach
            val id = key.substringAfter('/')
            val layout = file.scopes.getOrPut(scope) { MenuScopeLayout() }
            if (scope == VANILLA) {
                layout.legacyHidden.add(id)
            } else {
                (layout.elements.firstOrNull { it.id == id } ?: MenuElement(id).also(layout.elements::add)).visible = false
            }
        }
        LOGGER.info("Moved the old menu editor settings to {}", path)
        PolyPlusMainMenuConfig.menuLayoutOffsets = ""
        PolyPlusMainMenuConfig.menuHidden = ""
        PolyPlusMainMenuConfig.save()
        write()
    }

    fun prefs(): MenuEditorPrefs = file().editor

    fun scope(name: String): MenuScopeLayout = file().scopes.getOrPut(name) { MenuScopeLayout() }

    fun element(scope: String, id: String): MenuElement? = scope(scope).elements.firstOrNull { it.id == id }

    /** The stored element for [id], made if it isn't there yet. */
    fun edit(scope: String, id: String): MenuElement = element(scope, id) ?: MenuElement(id).also { scope(scope).elements.add(it) }

    fun remove(scope: String, id: String) {
        scope(scope).elements.removeAll { it.id == id }
    }

    fun added(scope: String): List<MenuElement> = scope(scope).elements.filter { !it.builtin }

    fun newId(scope: String, type: String): String {
        val base = type.substringBefore(':').replace(Regex("[^a-z0-9-]"), "")
        var n = 1
        while (element(scope, "$base-$n") != null) n++
        return "$base-$n"
    }

    fun changed() {
        revision.intValue++
    }

    fun save() {
        changed()
        write()
    }

    private fun write() {
        val current = data ?: return
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(path, gson.toJson(current))
        }.onFailure { LOGGER.error("Couldn't save {}", path, it) }
    }

    // ---------------------------------------------------------------- undo

    private fun snapshot(scope: String): String = compact.toJson(scope(scope))

    private fun restore(scope: String, json: String) {
        file().scopes[scope] = compact.fromJson(json, MenuScopeLayout::class.java)
        save()
    }

    /** Call before a change, so it can be undone. */
    fun checkpoint(scope: String) {
        val stack = undo.getOrPut(scope) { ArrayDeque() }
        val now = snapshot(scope)
        if (stack.lastOrNull() == now) return
        stack.addLast(now)
        while (stack.size > HISTORY) stack.removeFirst()
        redo[scope]?.clear()
    }

    /** Drops the last checkpoint when the change turned out to be nothing (a click without a drag). */
    fun dropCheckpoint(scope: String) {
        val stack = undo[scope] ?: return
        if (stack.lastOrNull() == snapshot(scope)) stack.removeLast()
    }

    fun undo(scope: String): Boolean {
        val json = undo[scope]?.removeLastOrNull() ?: return false
        redo.getOrPut(scope) { ArrayDeque() }.addLast(snapshot(scope))
        restore(scope, json)
        return true
    }

    fun redo(scope: String): Boolean {
        val json = redo[scope]?.removeLastOrNull() ?: return false
        undo.getOrPut(scope) { ArrayDeque() }.addLast(snapshot(scope))
        restore(scope, json)
        return true
    }

    fun canUndo(scope: String) = undo[scope]?.isNotEmpty() == true

    fun canRedo(scope: String) = redo[scope]?.isNotEmpty() == true

    fun reset(scope: String) {
        checkpoint(scope)
        file().scopes[scope] = MenuScopeLayout()
        save()
    }

    // ---------------------------------------------------------------- sharing

    private const val CODE_PREFIX = "PPMENU1:"

    fun exportCode(scope: String): String =
        CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compact.toJson(scope(scope)).toByteArray())

    /** False when [code] isn't a layout code. */
    fun importCode(scope: String, code: String): Boolean {
        val trimmed = code.trim()
        if (!trimmed.startsWith(CODE_PREFIX)) return false
        val json = runCatching { String(Base64.getUrlDecoder().decode(trimmed.removePrefix(CODE_PREFIX))) }.getOrNull() ?: return false
        if (runCatching { JsonParser.parseString(json).isJsonObject }.getOrDefault(false).not()) return false
        val layout = runCatching { compact.fromJson(json, MenuScopeLayout::class.java) }.getOrNull() ?: return false
        checkpoint(scope)
        file().scopes[scope] = layout
        save()
        return true
    }
}
