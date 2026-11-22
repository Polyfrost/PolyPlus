package org.polyfrost.polyplus.client.gui.screens.packs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.packs.PackSelectionModel
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackDetector
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo
import org.polyfrost.polyplus.mixin.client.access.PackEntryAccessor
import org.polyfrost.polyplus.mixin.client.access.PackModelAccessor
import org.polyfrost.polyplus.mixin.client.access.PackSelectionScreenAccessor
//?} elif = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.resource.pack.ResourcePacks
import org.polyfrost.oneconfig.utils.v1.Multithreading
import org.polyfrost.polyplus.mixin.client.access.PackUnopenedPackAccessor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
*///?}


internal enum class PackSide { Available, Selected }

internal enum class PackSort(val label: String) {
    Default("Default order"),
    NameAsc("Name, A to Z"),
    NameDesc("Name, Z to A"),
    Newest("Date modified"),
}

internal enum class PackFilter(val label: String, val foldersOnly: Boolean) {
    All("All", false),
    Incompatible("Incompatible", false),
    Server("From server", true),
    InFolders("In folders", true),
    ;

    fun accepts(item: PackItem): Boolean = when (this) {
        All -> true
        Incompatible -> !item.compatible
        Server -> item.server
        InFolders -> item.folder.isNotEmpty()
    }
}

internal class PackPlacement(val ids: List<String>, val anchor: String?, val positioned: Boolean)

internal sealed interface PackDialog {
    class Incompatible(val items: List<PackItem>, val placement: PackPlacement, val profile: PackProfile? = null) : PackDialog

    class Drop(val files: List<Path>) : PackDialog

    class Notice(val title: String, val message: String) : PackDialog

    object SaveProfile : PackDialog

    class DeleteProfile(val profile: PackProfile) : PackDialog

    class NewFolder(val parent: String, val moving: List<String>) : PackDialog

    class RenameFolder(val path: String) : PackDialog

    class DeleteFolder(val folder: PackFolder) : PackDialog

    class DeletePacks(val items: List<PackItem>) : PackDialog

    class Overwrite(val ids: List<String>, val target: String, val names: List<String>, val skipped: List<String>) : PackDialog

    class HardDelete(val paths: List<Path>) : PackDialog
}

internal class PackMovePlan(val moves: List<Triple<String, Path, Path>>, val skipped: List<Path>) {
    fun skippedNotice(): PackDialog = PackDialog.Notice(
        if (skipped.size == 1) "Pack not moved" else "Some packs weren't moved",
        "${PackSession.names(skipped)} ${if (skipped.size == 1) "has" else "have"} the same name as another pack going to that folder.",
    )

    companion object {
        fun isBlocked(move: Triple<String, Path, Path>): Boolean =
            Files.exists(move.third, LinkOption.NOFOLLOW_LINKS) && !(PackFiles.isPack(move.second) && PackFiles.isPack(move.third))

        fun blockedNotice(blocked: List<Triple<String, Path, Path>>): PackDialog = PackDialog.Notice(
            if (blocked.size == 1) "Pack not moved" else "Some packs weren't moved",
            blocked.joinToString(" ") {
                val name = it.third.fileName.toString()
                if (PackFiles.isPack(it.third)) "A pack named $name is already there." else "A folder named $name is already there."
            },
        )

        fun of(ids: List<String>, packDir: Path, targetDir: Path): PackMovePlan =
            of(ids, { id -> PackFolders.relative(id)?.let { runCatching { packDir.resolve(it) }.getOrNull() } }, targetDir)

        fun of(ids: List<String>, sourceOf: (String) -> Path?, targetDir: Path): PackMovePlan {
            val planned = ids.distinct().mapNotNull { id ->
                val source = sourceOf(id) ?: return@mapNotNull null
                if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return@mapNotNull null
                val destination = targetDir.resolve(source.fileName)
                if (destination != source && destination.startsWith(source)) null else Triple(id, source, destination)
            }
            val claimed = HashSet<Path>()
            val moves = ArrayList<Triple<String, Path, Path>>()
            val skipped = ArrayList<Path>()
            for (move in planned.sortedBy { !inPlace(it) }) {
                when {
                    !claimed.add(move.third) -> skipped.add(move.second)
                    !inPlace(move) -> moves.add(move)
                }
            }
            return PackMovePlan(moves, skipped)
        }

        private fun inPlace(move: Triple<String, Path, Path>): Boolean =
            move.second == move.third || runCatching { entryOf(move.second) == entryOf(move.third) }.getOrDefault(false)

        private fun entryOf(path: Path): Path = path.toAbsolutePath().parent.toRealPath().resolve(path.fileName)
    }
}


//? if > 1.8.9 {
internal class PackItem(
    val id: String,
    val title: String,
    val description: String,
    val compatible: Boolean,
    val warning: String,
    val confirm: String,
    val selected: Boolean,
    val required: Boolean,
    val fixed: Boolean,
    val canSelect: Boolean,
    val canUnselect: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val server: Boolean,
    val folder: String,
    val file: Path?,
    val pack: Pack,
    val entry: PackSelectionModel.Entry,
) {
    val side: PackSide get() = if (selected) PackSide.Selected else PackSide.Available

    val deletable: Boolean get() = file != null && !required

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = listOf(id, title, description).map { it.lowercase(Locale.ROOT) }
        val q = query.trim().lowercase(Locale.ROOT)
        if (!q.contains('*')) return haystack.any { it.contains(q) }
        val pattern = Regex(q.split('*').joinToString(".*") { Regex.escape(it) })
        return haystack.any { pattern.containsMatchIn(it) }
    }
}

private enum class Away { None, Reload, Rebuild }

internal class PackSession(initial: PackSelectionScreen) {
    private var screen: PackSelectionScreen = initial
    private val access: PackSelectionScreenAccessor get() = screen as Any as PackSelectionScreenAccessor
    private val model: PackSelectionModel get() = access.`polyplus$model`()
    private val modelAccess: PackModelAccessor? get() = model as Any as? PackModelAccessor
    private val icons = ConcurrentHashMap<String, Optional<ImageBitmap>>()
    private val changedAt = ConcurrentHashMap<String, Long>()
    private var closed = false
    private var removing = 0
    private var commitPending = false
    private var forceReload = false
    private var away = Away.None
    private var leftFrom: Any? = null
    private val watcher = PackTreeWatcher(packDir)

    var host: () -> Screen? = { ClientPlatform.currentScreen() }

    val isAway: Boolean get() = away != Away.None && !closed

    val isClosed: Boolean get() = closed

    val title: String = initial.title.string

    val packDir: Path get() = access.`polyplus$packDir`()

    val resources: Boolean = isResources(initial)

    var revision by mutableIntStateOf(0)
        private set

    var dialog by mutableStateOf<PackDialog?>(null)

    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    private var anchor: Pair<PackSide, String>? = null

    var folder by mutableStateOf("")

    var flat by mutableStateOf(false)

    var sort by mutableStateOf(PackSort.Default)

    var filter by mutableStateOf(PackFilter.All)

    var busy by mutableStateOf(false)
        private set

    val folders = PackFolders(this)

    val foldersEnabled: Boolean get() = resources

    init {
        prepare()
    }

    fun offers(filter: PackFilter): Boolean = foldersEnabled || !filter.foldersOnly

    fun selected(): List<PackItem> = model.selected.toList().map(::item)

    fun available(): List<PackItem> {
        val items = model.unselected.toList().map(::item)
        return when (sort) {
            PackSort.Default -> items
            PackSort.NameAsc -> items.sortedBy { it.title.lowercase(Locale.ROOT) }
            PackSort.NameDesc -> items.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            PackSort.Newest -> items.sortedByDescending { changed(it) }
        }
    }

    fun select(item: PackItem) = request(PackPlacement(listOf(item.id), null, false))

    fun request(placement: PackPlacement) {
        if (closed) return
        val risky = placement.ids.mapNotNull { id -> entry(id)?.takeIf { !it.isSelected && !it.compatibility.isCompatible }?.let(::item) }
        if (PolyPlusConfig.packVersionWarnings && risky.isNotEmpty()) {
            dialog = PackDialog.Incompatible(risky, placement)
            return
        }
        place(placement)
    }

    fun answerIncompatible(dialog: PackDialog.Incompatible, accepted: Boolean) {
        this.dialog = null
        if (!accepted) return
        val profile = dialog.profile
        if (profile != null) applyProfile(profile, true) else place(dialog.placement)
    }

    fun unselect(item: PackItem) = unselect(listOf(item.id))

    fun unselect(ids: Collection<String>) {
        if (closed) return
        for (id in ids) entry(id)?.takeIf { it.isSelected && it.canUnselect() }?.unselect()
        picked = picked - ids.toSet()
        changed()
    }

    fun transfer(side: PackSide, ids: List<String>) {
        if (side == PackSide.Available) request(PackPlacement(ids, null, false)) else unselect(ids)
    }

    fun moveUp(item: PackItem) {
        entry(item.id)?.takeIf { it.isSelected && !it.isFixedPosition && it.canMoveUp() }?.moveUp()
        changed()
    }

    fun moveDown(item: PackItem) {
        entry(item.id)?.takeIf { it.isSelected && !it.isFixedPosition && it.canMoveDown() }?.moveDown()
        changed()
    }

    fun moveToTop(item: PackItem) {
        val current = selectedIds()
        if (item.id !in current || entry(item.id)?.isFixedPosition != false) return
        arrange(listOf(item.id) + current.filter { it != item.id })
        changed()
    }

    fun moveToBottom(item: PackItem) {
        var steps = selectedIds().size
        while (steps-- > 0) {
            val entry = entry(item.id)?.takeIf { it.isSelected && !it.isFixedPosition && it.canMoveDown() } ?: break
            entry.moveDown()
        }
        changed()
    }

    fun toggle(ids: List<String>) {
        val (on, off) = ids.mapNotNull(::entry).partition { it.isSelected }
        if (on.isNotEmpty()) unselect(on.map { it.id })
        if (off.isNotEmpty()) request(PackPlacement(off.map { it.id }, null, false))
    }

    fun pickedItems(): List<PackItem> = picked.mapNotNull { id -> entry(id)?.let(::item) }

    fun refresh() {
        if (closed) return
        reloadKeeping(emptyMap())
    }

    fun showInFolder(item: PackItem) {
        item.file?.let(PackFiles::reveal)
    }

    fun askDelete(items: List<PackItem>) {
        val deletable = items.filter { it.deletable }
        if (deletable.isNotEmpty()) dialog = PackDialog.DeletePacks(deletable)
    }

    fun deletePacks(items: List<PackItem>, accepted: Boolean) {
        dialog = null
        if (!accepted || closed) return
        remove(items.mapNotNull { it.file }, PackFiles.trashSupported)
    }

    fun deleteHard(paths: List<Path>, accepted: Boolean) {
        dialog = null
        if (accepted) remove(paths, false)
    }

    fun movePacks(ids: List<String>, target: String, overwrite: Boolean = false) {
        if (closed || !foldersEnabled) return
        dialog = null
        if (!PackFolders.canHoldPacks(target)) {
            dialog = PackDialog.Notice("Pack not moved", PackFolders.TOO_DEEP)
            return
        }
        val targetDir = (if (target.isEmpty()) packDir else folders.dir(target)) ?: return
        val linked = ids.filter { folders.linkedFolderOf(it) != null }
        val wanted = ids.filter { folders.linkedFolderOf(it) != target }
        val released = if (target.isEmpty()) linked.filter { it in wanted } else emptyList()
        val plan = PackMovePlan.of(wanted, ::fileOfId, targetDir)
        val blocked = plan.moves.filter(PackMovePlan::isBlocked)
        val moves = plan.moves.filter { it !in blocked }
        if (moves.isEmpty()) {
            if (released.isNotEmpty()) {
                unlink(released)
                refreshCaches()
            }
            if (blocked.isNotEmpty()) dialog = PackMovePlan.blockedNotice(blocked)
            else if (plan.skipped.isNotEmpty()) dialog = plan.skippedNotice()
            return
        }
        val conflicts = moves.filter { Files.exists(it.third, LinkOption.NOFOLLOW_LINKS) }
        if (conflicts.isNotEmpty() && !overwrite) {
            dialog = PackDialog.Overwrite(ids, target, conflicts.map { it.third.fileName.toString() }, plan.skipped.map { it.fileName.toString() })
            return
        }
        val replace = conflicts.mapTo(HashSet()) { it.third }
        val enabled = selectedIds().toSet()
        val renames = HashMap<String, String>()
        val failed = ArrayList<Path>()
        for ((id, source, destination) in moves) {
            try {
                PackFiles.move(source, destination, destination in replace, absoluteLink = destination.parent == packDir)
                renames[id] = idOf(destination)
                if (destination in replace && idOf(destination) in enabled) forceReload = true
            } catch (_: Exception) {
                failed.add(source)
            }
        }
        unlink(released + linked.filter { it in renames })
        reloadKeeping(renames)
        if (failed.isNotEmpty()) {
            dialog = PackDialog.Notice(
                if (failed.size == 1) "Couldn't move this pack" else "Couldn't move some packs",
                "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.",
            )
        } else if (blocked.isNotEmpty()) {
            dialog = PackMovePlan.blockedNotice(blocked)
        } else if (plan.skipped.isNotEmpty()) {
            dialog = plan.skippedNotice()
        }
    }

    fun createFolder(parent: String, name: String, moving: List<String>) {
        dialog = null
        if (closed || !foldersEnabled) return
        if (!PackFolders.canAddFolder(parent)) {
            dialog = PackDialog.Notice("Folder not created", PackFolders.TOO_DEEP)
            return
        }
        val base = (if (parent.isEmpty()) packDir else folders.dir(parent)) ?: return
        val target = base.resolve(name.trim())
        try {
            Files.createDirectories(target)
        } catch (_: Exception) {
            dialog = PackDialog.Notice("Folder not created", "Couldn't create ${name.trim()}.")
            return
        }
        folders.clear()
        changed()
        if (moving.isNotEmpty()) movePacks(moving, PackFolders.join(parent, name.trim()))
    }

    fun renameFolder(path: String, name: String) {
        dialog = null
        if (closed || !foldersEnabled) return
        val source = folders.dir(path) ?: return
        val target = source.resolveSibling(name.trim())
        if (target == source) return
        val prefix = "file/$path/"
        val affected = PackBridges.withHidden(model) { (selected() + available()).filter { it.file != null }.map { it.id } }.filter { it.startsWith(prefix) }
        try {
            Files.move(source, target)
        } catch (_: Exception) {
            dialog = PackDialog.Notice("Folder not renamed", "A pack in this folder may be in use by the game. Turn it off, leave this page, then try again.")
            return
        }
        val renamedPath = PackFolders.join(PackFolders.parentOf(path), name.trim())
        val renames = affected.associateWith { "file/$renamedPath/" + it.removePrefix(prefix) }
        if (folder == path || folder.startsWith("$path/")) folder = renamedPath + folder.removePrefix(path)
        reloadKeeping(renames)
    }

    fun deleteFolder(target: PackFolder) {
        if (closed || !foldersEnabled) return
        val dir = folders.dir(target.path) ?: return
        if (PackFiles.isEmptyDir(dir)) {
            runCatching { Files.deleteIfExists(dir) }
            leaveDeleted(target.path)
            refresh()
        } else {
            dialog = PackDialog.DeleteFolder(target)
        }
    }

    fun answerDeleteFolder(target: PackFolder, accepted: Boolean) {
        dialog = null
        if (!accepted || closed) return
        val dir = folders.dir(target.path) ?: return
        leaveDeleted(target.path)
        remove(listOf(dir), PackFiles.trashSupported)
    }

    fun folderNameError(parent: String, name: String, except: String? = null): String? {
        PackFiles.validName(name)?.let { return it }
        val path = PackFolders.join(parent, name.trim())
        if (path == except) return null
        val clash = runCatching { packDir.resolve(path) }.getOrNull()?.let { Files.exists(it, LinkOption.NOFOLLOW_LINKS) } == true
        return if (clash) "Something with that name is already here." else null
    }

    private fun leaveDeleted(path: String) {
        if (folder == path || folder.startsWith("$path/")) folder = PackFolders.parentOf(path)
    }

    private fun remove(paths: List<Path>, trash: Boolean) {
        if (paths.isEmpty()) return
        removing++
        busy = true
        PackFiles.remove(paths, trash) done@{ failed ->
            removing--
            busy = removing > 0
            if ((closed || commitPending) && failed.isNotEmpty()) PackDeleteNotice.show(failed)
            if (closed) return@done
            reloadKeeping(emptyMap())
            if (commitPending) {
                if (removing == 0) {
                    commitPending = false
                    commit()
                }
                return@done
            }
            if (failed.isNotEmpty()) {
                dialog = if (trash) {
                    PackDialog.HardDelete(failed)
                } else {
                    PackDialog.Notice("Couldn't delete", "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.")
                }
            }
        }
    }

    private fun reloadKeeping(renames: Map<String, String>) {
        PackBridges.withHidden(model) {
            val desired = selectedIds().map { renames[it] ?: it }
            rescan()
            for (id in desired) entry(id)?.takeIf { !it.isSelected && it.canSelect() }?.select()
            val current = selectedIds()
            arrange(desired.filter { it in current } + current.filter { it !in desired })
        }
        picked = picked.mapTo(HashSet()) { renames[it] ?: it }
        if (renames.isNotEmpty() && !PackProfiles.rename(renames)) PackProfileNotice.show()
        if (!folders.remapLinks(renames)) linkNotice()
        refreshCaches()
    }

    private fun unlink(ids: List<String>) {
        if (ids.isNotEmpty() && !folders.unlink(ids)) linkNotice()
    }

    private fun linkNotice() {
        runCatching { Notifications.error("Couldn't update folders", "A Packed Packs folder list could not be written. Check free disk space and try again.") }
    }

    private fun idOf(path: Path): String = "file/" + packDir.relativize(path).toString().replace('\\', '/')

    fun click(side: PackSide, id: String, visible: List<String>, toggle: Boolean, range: Boolean) {
        val previous = anchor
        if (range && previous != null && previous.first == side) {
            val from = visible.indexOf(previous.second)
            val to = visible.indexOf(id)
            if (from >= 0 && to >= 0) {
                val span = visible.subList(minOf(from, to), maxOf(from, to) + 1)
                picked = if (toggle) picked + span else span.toSet()
                return
            }
        }
        anchor = side to id
        picked = when {
            toggle -> if (id in picked) picked - id else picked + id
            picked == setOf(id) -> emptySet()
            else -> setOf(id)
        }
    }

    fun pickAll(ids: List<String>) {
        picked = ids.toSet()
    }

    fun clearPicks() {
        picked = emptySet()
        anchor = null
    }

    fun dragIds(side: PackSide, id: String, visible: List<String>): List<String> =
        if (id in picked) visible.filter { it in picked && sideOf(it) == side } else listOf(id)

    fun sideOf(id: String): PackSide? = entry(id)?.let { if (it.isSelected) PackSide.Selected else PackSide.Available }

    fun tick() {
        if (away != Away.None && ClientPlatform.currentScreen() === leftFrom) resume()
        if (closed) return
        if (access.`polyplus$ticksToReload`() == 1L) {
            rescan()
            refreshCaches()
        }
        if (away == Away.None && removing == 0 && watcher.poll()) {
            rescan()
            refreshCaches()
        }
        screen.tick()
    }

    fun resume() {
        val pending = away
        away = Away.None
        leftFrom = null
        when {
            closed || pending == Away.Rebuild -> rebuild()
            pending == Away.Reload -> {
                rescan()
                refreshCaches()
            }
        }
    }

    fun openFolder() = PackFiles.openPath(packDir)

    fun icon(item: PackItem): ImageBitmap? =
        icons.computeIfAbsent(item.id) { Optional.ofNullable(readIcon(item.pack)) }.orElse(null)

    fun cachedIcon(item: PackItem): ImageBitmap? = icons[item.id]?.orElse(null)

    fun folderIcon(path: String): ImageBitmap? =
        icons.computeIfAbsent("folder:$path") { Optional.ofNullable(folders.iconFile(path)?.let(::decode)) }.orElse(null)

    fun cachedFolderIcon(path: String): ImageBitmap? = icons["folder:$path"]?.orElse(null)

    fun commit() {
        if (closed) return
        if (removing > 0) {
            commitPending = true
            dialog = null
            return
        }
        closed = true
        watcher.close()
        dialog = null
        val forced = PackBridges.consumeForcedReload() || forceReload
        if (resources && forced) Minecraft.getInstance().options.resourcePacks.clear()
        screen.onClose()
    }

    fun commitLater() {
        if (closed) return
        //? if >= 1.21.4 {
        Minecraft.getInstance().schedule(Runnable { commit() })
        //?} else {
        /*Minecraft.getInstance().tell(Runnable { commit() })
        *///?}
    }

    fun drop(files: List<Path>) {
        if (closed || files.isEmpty()) return
        dialog = PackDialog.Drop(files)
    }

    fun answerDrop(files: List<Path>, accepted: Boolean) {
        dialog = null
        if (accepted) dialog = import(files)
    }

    fun profiles(): List<PackProfile> = if (resources) PackProfiles.all() else emptyList()

    fun saveProfile(name: String) {
        dialog = null
        val ids = PackBridges.withHidden(model) { selected().filter { !(it.required && it.fixed) }.map { it.id } }
        if (!PackProfiles.save(name, ids)) PackProfileNotice.show()
        changed()
    }

    fun deleteProfile(profile: PackProfile, accepted: Boolean) {
        dialog = null
        if (accepted && !PackProfiles.delete(profile)) PackProfileNotice.show()
        changed()
    }

    fun applyProfile(profile: PackProfile, confirmed: Boolean = false) {
        if (closed) return
        PackBridges.withHidden(model) { applyProfileFully(profile, confirmed) }
    }

    private fun applyProfileFully(profile: PackProfile, confirmed: Boolean) {
        val known = profile.packs.filter { entry(it) != null }
        if (!confirmed) {
            val risky = known.mapNotNull { id -> entry(id)?.takeIf { !it.isSelected && it.canSelect() && !it.compatibility.isCompatible }?.let(::item) }
            if (PolyPlusConfig.packVersionWarnings && risky.isNotEmpty()) {
                dialog = PackDialog.Incompatible(risky, PackPlacement(known, null, false), profile)
                return
            }
        }
        unselect(selected().filter { it.id !in known && it.canUnselect }.map { it.id })
        for (id in known) entry(id)?.takeIf { it.canSelect() }?.select()
        val current = selectedIds()
        val order = known.distinct().filter { it in current && entry(it)?.isFixedPosition == false }
        val rest = current.filter { it !in order && entry(it)?.isFixedPosition == false }
        val flow = (order + rest).iterator()
        arrange(current.map { id -> if (entry(id)?.isFixedPosition == false) flow.next() else id })
        clearPicks()
        changed()
        val missing = profile.packs.size - known.size
        if (missing > 0) {
            dialog = PackDialog.Notice("Profile applied", if (missing == 1) "One pack in this profile isn't installed." else "$missing packs in this profile aren't installed.")
        }
    }

    fun optionsFor(item: PackItem): Boolean = resources && PackBridges.configurable(item.entry)

    fun openOptions(item: PackItem) {
        leave(Away.Reload)
        PackBridges.configure(item.entry)
    }

    fun onlineActions(): List<PackOnlineAction> = if (resources) PackBridges.online(screen) else emptyList()

    fun runOnline(action: PackOnlineAction) {
        leave(Away.Reload)
        action.run()
    }

    fun canOpenPackedPacks(): Boolean = PackBridges.packedPacksLoaded

    fun openPackedPacks() {
        if (closed) return
        modelAccess?.`polyplus$updateRepoSelectedList`()
        val previous = host()
        val target = PackBridges.packedPacksScreen(screen, previous) ?: return
        leave(Away.Rebuild, previous)
        PackDetour.open(target, previous ?: ClientPlatform.currentScreen())
        ClientPlatform.setScreen(target)
    }

    fun returningTo(current: Screen?): Boolean = isAway && leftFrom != null && current === leftFrom

    fun abandon() {
        if (closed) return
        val pending = away
        away = Away.None
        leftFrom = null
        if (pending == Away.Rebuild) {
            closed = true
            watcher.close()
            dialog = null
            runCatching { access.`polyplus$closeWatcher`() }
        } else {
            commitLater()
        }
    }

    private fun leave(kind: Away, previous: Screen? = host()) {
        away = kind
        leftFrom = previous ?: ClientPlatform.currentScreen()
    }

    private fun place(placement: PackPlacement) {
        if (closed) return
        for (id in placement.ids) entry(id)?.takeIf { !it.isSelected && it.canSelect() }?.select()
        if (placement.positioned) {
            val current = selectedIds()
            val moving = placement.ids.filter { it in current && entry(it)?.isFixedPosition == false }
            val rest = current.filter { it !in moving }
            val anchor = placement.anchor?.let { id -> if (id in moving) current.dropWhile { it != id }.firstOrNull { it !in moving } else id }
            val at = anchor?.let { rest.indexOf(it) }?.takeIf { it >= 0 } ?: rest.size
            arrange(rest.take(at) + moving + rest.drop(at))
        }
        picked = picked - placement.ids.toSet()
        changed()
    }

    private fun arrange(desired: List<String>) {
        for ((target, id) in desired.withIndex()) {
            var steps = desired.size
            while (steps-- > 0) {
                val index = selectedIds().indexOf(id)
                if (index <= target) break
                val entry = entry(id) ?: break
                if (entry.isFixedPosition || !entry.canMoveUp()) break
                entry.moveUp()
            }
        }
    }

    private fun selectedIds(): List<String> = model.selected.map { it.id }.toList()

    private fun rebuild() {
        val args = modelAccess
        if (args == null) {
            if (!closed) rescan()
            refreshCaches()
            return
        }
        val title = screen.title
        if (!closed) runCatching { access.`polyplus$closeWatcher`() }
        screen = PackSelectionScreen(args.`polyplus$repository`(), args.`polyplus$output`(), packDir, title)
        prepare()
        closed = false
        clearPicks()
        refreshCaches()
    }

    private fun prepare() {
        //? if >= 1.21.10 {
        modelAccess?.`polyplus$setOnListChanged`(Consumer {})
        //?} else {
        /*modelAccess?.`polyplus$setOnListChanged`(Runnable {})
        *///?}
        rescan()
    }

    private fun rescan() {
        model.findNewPacks()
        access.`polyplus$setTicksToReload`(0L)
    }

    private fun refreshCaches() {
        icons.clear()
        changedAt.clear()
        folders.clear()
        changed()
    }

    private fun import(files: List<Path>): PackDialog? {
        val mc = Minecraft.getInstance()
        val candidates = ArrayList<Path>(files.size)
        val leftover = LinkedHashSet(files)
        val issues = ArrayList<ForbiddenSymlinkInfo>()
        val detector = object : PackDetector<Path>(mc.directoryValidator()) {
            override fun createZipPack(content: Path): Path = content

            override fun createDirectoryPack(content: Path): Path = content
        }
        for (path in files) {
            try {
                val candidate = detector.detectPackResources(path, issues) ?: continue
                candidates.add(candidate)
                leftover.remove(candidate)
            } catch (_: IOException) {
            }
        }
        if (issues.isNotEmpty()) {
            return PackDialog.Notice("Pack not added", "Packs that use symbolic links can't be added from here.")
        }
        if (candidates.isNotEmpty()) {
            PackSelectionScreenAccessor.`polyplus$copyPacks`(mc, candidates, packDir)
            rescan()
            refreshCaches()
        }
        if (leftover.isNotEmpty()) {
            return PackDialog.Notice("Some files weren't added", "These aren't packs: ${names(leftover)}")
        }
        return null
    }

    private fun entry(id: String): PackSelectionModel.Entry? =
        model.selected.filter { it.id == id }.findFirst().orElse(null)
            ?: model.unselected.filter { it.id == id }.findFirst().orElse(null)

    private fun changed() {
        revision++
    }

    private fun changed(item: PackItem): Long = changedAt.computeIfAbsent(item.id) {
        item.file?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrNull() } ?: 0L
    }

    private fun item(entry: PackSelectionModel.Entry): PackItem {
        val compatibility = entry.compatibility
        val pack = (entry as Any as PackEntryAccessor).`polyplus$pack`()
        val file = fileOf(pack)
        return PackItem(
            id = entry.id,
            title = entry.title.string,
            description = entry.extendedDescription.string,
            compatible = compatibility.isCompatible,
            warning = compatibility.description.string,
            confirm = compatibility.confirmation.string,
            selected = entry.isSelected,
            required = entry.isRequired,
            fixed = entry.isFixedPosition,
            canSelect = entry.canSelect(),
            canUnselect = entry.canUnselect(),
            canMoveUp = entry.isSelected && !entry.isFixedPosition && entry.canMoveUp(),
            canMoveDown = entry.isSelected && !entry.isFixedPosition && entry.canMoveDown(),
            server = entry.packSource == PackSource.SERVER,
            folder = if (resources) folders.linkedFolderOf(entry.id) ?: folderOf(file) else PackFolders.folderOf(entry.id),
            file = file,
            pack = pack,
            entry = entry,
        )
    }

    private fun fileOfId(id: String): Path? = entry(id)?.let { fileOf((it as Any as PackEntryAccessor).`polyplus$pack`()) }

    private fun fileOf(pack: Pack): Path? {
        if (!resources) return null
        val link = PackFolders.relative(pack.id)?.let { runCatching { packDir.resolve(it) }.getOrNull() }
        if (link != null && Files.isSymbolicLink(link)) return link
        val real = PackSources.pathOf(pack) ?: return null
        return real.takeIf { relativeToPackDir(it) != null && Files.exists(it, LinkOption.NOFOLLOW_LINKS) }
    }

    private fun relativeToPackDir(path: Path): Path? = runCatching {
        val base = packDir.toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        if (target != base && target.startsWith(base)) base.relativize(target) else null
    }.getOrNull()

    private fun folderOf(file: Path?): String {
        val relative = file?.let(::relativeToPackDir) ?: return ""
        return relative.parent?.toString()?.replace('\\', '/') ?: ""
    }

    private fun readIcon(pack: Pack): ImageBitmap? = runCatching {
        //? if >= 26.3 {
        pack.openMetadata().use { resources ->
        //?} else {
        /*pack.open().use { resources ->
        *///?}
            val supplier = resources.getRootResource("pack.png") ?: return@use null
            supplier.get().use { stream -> PolyBitmap.decode(stream.readAllBytes())?.asComposeImageBitmap() }
        }
    }.getOrNull()

    private fun decode(file: Path): ImageBitmap? = runCatching {
        PolyBitmap.decode(Files.readAllBytes(file))?.asComposeImageBitmap()
    }.getOrNull()

    companion object {
        val INCOMPATIBLE_TITLE: String get() = Component.translatable("pack.incompatible.confirm.title").string

        fun names(files: Collection<Path>): String = files.joinToString(", ") { it.fileName.toString() }

        fun resources(): PackSession {
            val mc = Minecraft.getInstance()
            val screen = PackSelectionScreen(
                mc.resourcePackRepository,
                { repository -> mc.options.updateResourcePacks(repository) },
                mc.resourcePackDirectory,
                Component.translatable("resourcePack.title"),
            )
            return PackSession(screen)
        }

        private fun isResources(screen: PackSelectionScreen): Boolean {
            val model = (screen as Any as PackSelectionScreenAccessor).`polyplus$model`()
            val repository = (model as Any as? PackModelAccessor)?.`polyplus$repository`()
            if (repository != null) return repository === Minecraft.getInstance().resourcePackRepository
            return (screen.title.contents as? TranslatableContents)?.key == "resourcePack.title"
        }
    }
}

//?} elif = 1.8.9 {
/*internal class PackItem(
    val id: String,
    val title: String,
    val description: String,
    val compatible: Boolean,
    val warning: String,
    val confirm: String,
    val selected: Boolean,
    val required: Boolean,
    val fixed: Boolean,
    val canSelect: Boolean,
    val canUnselect: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val server: Boolean,
    val folder: String,
    val file: Path?,
    val pack: ResourcePacks.UnopenedPack?,
) {
    val side: PackSide get() = if (selected) PackSide.Selected else PackSide.Available

    val deletable: Boolean get() = file != null && !required

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = listOf(id, title, description).map { it.lowercase(Locale.ROOT) }
        val q = query.trim().lowercase(Locale.ROOT)
        if (!q.contains('*')) return haystack.any { it.contains(q) }
        val pattern = Regex(q.split('*').joinToString(".*") { Regex.escape(it) })
        return haystack.any { pattern.containsMatchIn(it) }
    }
}

internal class PackSession private constructor(private val exit: Screen?, private val routed: Boolean) {
    private val mc: Minecraft get() = Minecraft.getInstance()
    private val repository: ResourcePacks get() = mc.getResourcePacks()
    private val packs = LinkedHashMap<String, ResourcePacks.UnopenedPack>()
    private val chosen = ArrayList<String>()
    private val initial: List<String>
    private val icons = ConcurrentHashMap<String, Optional<ImageBitmap>>()
    private val changedAt = ConcurrentHashMap<String, Long>()
    private var closed = false
    private var removing = 0
    private var commitPending = false
    private var forceReload = false
    private val watcher = PackTreeWatcher(repository.getDirectory().toPath())

    var host: () -> Screen? = { ClientPlatform.currentScreen() }

    val isAway: Boolean get() = false

    val isClosed: Boolean get() = closed

    val title: String = I18n.translate("resourcePack.title")

    val packDir: Path = repository.getDirectory().toPath()

    val resources: Boolean = true

    var revision by mutableIntStateOf(0)
        private set

    var dialog by mutableStateOf<PackDialog?>(null)

    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    private var anchor: Pair<PackSide, String>? = null

    var folder by mutableStateOf("")

    var flat by mutableStateOf(false)

    var sort by mutableStateOf(PackSort.Default)

    var filter by mutableStateOf(PackFilter.All)

    var busy by mutableStateOf(false)
        private set

    val folders = PackFolders(this)

    val foldersEnabled: Boolean get() = true

    init {
        repository.reload()
        index()
        chosen.addAll(repository.getSelectedPacks().reversed().map(::idOf).filter { it in packs })
        initial = repository.getSelectedPacks().map { it.getName() }
    }

    fun offers(filter: PackFilter): Boolean = filter != PackFilter.Server

    fun selected(): List<PackItem> = chosen.mapNotNull(::item) + defaultItem()

    fun available(): List<PackItem> {
        val items = packs.keys.filter { it !in chosen }.mapNotNull(::item)
        return when (sort) {
            PackSort.Default -> items
            PackSort.NameAsc -> items.sortedBy { it.title.lowercase(Locale.ROOT) }
            PackSort.NameDesc -> items.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            PackSort.Newest -> items.sortedByDescending { changed(it) }
        }
    }

    fun select(item: PackItem) = request(PackPlacement(listOf(item.id), null, false))

    fun request(placement: PackPlacement) {
        if (closed) return
        val risky = placement.ids.filter { it !in chosen }.mapNotNull(::item).filter { !it.compatible }
        if (PolyPlusConfig.packVersionWarnings && risky.isNotEmpty()) {
            dialog = PackDialog.Incompatible(risky, placement)
            return
        }
        place(placement)
    }

    fun answerIncompatible(dialog: PackDialog.Incompatible, accepted: Boolean) {
        this.dialog = null
        if (!accepted) return
        val profile = dialog.profile
        if (profile != null) applyProfile(profile, true) else place(dialog.placement)
    }

    fun unselect(item: PackItem) = unselect(listOf(item.id))

    fun unselect(ids: Collection<String>) {
        if (closed) return
        chosen.removeAll(ids.toSet())
        picked = picked - ids.toSet()
        changed()
    }

    fun transfer(side: PackSide, ids: List<String>) {
        if (side == PackSide.Available) request(PackPlacement(ids, null, false)) else unselect(ids)
    }

    fun moveUp(item: PackItem) {
        val index = chosen.indexOf(item.id)
        if (index > 0) {
            chosen.removeAt(index)
            chosen.add(index - 1, item.id)
        }
        changed()
    }

    fun moveDown(item: PackItem) {
        val index = chosen.indexOf(item.id)
        if (index >= 0 && index < chosen.size - 1) {
            chosen.removeAt(index)
            chosen.add(index + 1, item.id)
        }
        changed()
    }

    fun moveToTop(item: PackItem) {
        if (!chosen.remove(item.id)) return
        chosen.add(0, item.id)
        changed()
    }

    fun moveToBottom(item: PackItem) {
        if (!chosen.remove(item.id)) return
        chosen.add(item.id)
        changed()
    }

    fun toggle(ids: List<String>) {
        val known = ids.filter { it in packs }
        val (on, off) = known.partition { it in chosen }
        if (on.isNotEmpty()) unselect(on)
        if (off.isNotEmpty()) request(PackPlacement(off, null, false))
    }

    fun pickedItems(): List<PackItem> = picked.mapNotNull(::item)

    fun refresh() {
        if (closed) return
        reloadKeeping(emptyMap())
    }

    fun showInFolder(item: PackItem) {
        item.file?.let(PackFiles::reveal)
    }

    fun askDelete(items: List<PackItem>) {
        val deletable = items.filter { it.deletable }
        if (deletable.isNotEmpty()) dialog = PackDialog.DeletePacks(deletable)
    }

    fun deletePacks(items: List<PackItem>, accepted: Boolean) {
        dialog = null
        if (!accepted || closed) return
        remove(items.mapNotNull { it.file }, PackFiles.trashSupported)
    }

    fun deleteHard(paths: List<Path>, accepted: Boolean) {
        dialog = null
        if (accepted) remove(paths, false)
    }

    fun movePacks(ids: List<String>, target: String, overwrite: Boolean = false) {
        if (closed) return
        dialog = null
        if (!PackFolders.canHoldPacks(target)) {
            dialog = PackDialog.Notice("Pack not moved", PackFolders.TOO_DEEP)
            return
        }
        val targetDir = (if (target.isEmpty()) packDir else folders.dir(target)) ?: return
        val plan = PackMovePlan.of(ids, packDir, targetDir)
        val blocked = plan.moves.filter(PackMovePlan::isBlocked)
        val moves = plan.moves.filter { it !in blocked }
        if (moves.isEmpty()) {
            if (blocked.isNotEmpty()) dialog = PackMovePlan.blockedNotice(blocked)
            else if (plan.skipped.isNotEmpty()) dialog = plan.skippedNotice()
            return
        }
        val conflicts = moves.filter { Files.exists(it.third, LinkOption.NOFOLLOW_LINKS) }
        if (conflicts.isNotEmpty() && !overwrite) {
            dialog = PackDialog.Overwrite(ids, target, conflicts.map { it.third.fileName.toString() }, plan.skipped.map { it.fileName.toString() })
            return
        }
        val replace = conflicts.mapTo(HashSet()) { it.third }
        val enabled = chosen.toSet()
        val renames = HashMap<String, String>()
        val failed = ArrayList<Path>()
        for ((id, source, destination) in moves) {
            try {
                PackFiles.move(source, destination, destination in replace, absoluteLink = destination.parent == packDir)
                renames[id] = idOf(destination)
                if (destination in replace && idOf(destination) in enabled) forceReload = true
            } catch (_: Exception) {
                failed.add(source)
            }
        }
        reloadKeeping(renames)
        if (failed.isNotEmpty()) {
            dialog = PackDialog.Notice(
                if (failed.size == 1) "Couldn't move this pack" else "Couldn't move some packs",
                "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.",
            )
        } else if (blocked.isNotEmpty()) {
            dialog = PackMovePlan.blockedNotice(blocked)
        } else if (plan.skipped.isNotEmpty()) {
            dialog = plan.skippedNotice()
        }
    }

    fun createFolder(parent: String, name: String, moving: List<String>) {
        dialog = null
        if (closed) return
        if (!PackFolders.canAddFolder(parent)) {
            dialog = PackDialog.Notice("Folder not created", PackFolders.TOO_DEEP)
            return
        }
        val base = (if (parent.isEmpty()) packDir else folders.dir(parent)) ?: return
        val target = base.resolve(name.trim())
        try {
            Files.createDirectories(target)
        } catch (_: Exception) {
            dialog = PackDialog.Notice("Folder not created", "Couldn't create ${name.trim()}.")
            return
        }
        folders.clear()
        changed()
        if (moving.isNotEmpty()) movePacks(moving, PackFolders.join(parent, name.trim()))
    }

    fun renameFolder(path: String, name: String) {
        dialog = null
        if (closed) return
        val source = folders.dir(path) ?: return
        val target = source.resolveSibling(name.trim())
        if (target == source) return
        val prefix = "file/$path/"
        val affected = packs.keys.filter { it.startsWith(prefix) }
        try {
            Files.move(source, target)
        } catch (_: Exception) {
            dialog = PackDialog.Notice("Folder not renamed", "A pack in this folder may be in use by the game. Turn it off, leave this page, then try again.")
            return
        }
        val renamedPath = PackFolders.join(PackFolders.parentOf(path), name.trim())
        val renames = affected.associateWith { "file/$renamedPath/" + it.removePrefix(prefix) }
        if (folder == path || folder.startsWith("$path/")) folder = renamedPath + folder.removePrefix(path)
        reloadKeeping(renames)
    }

    fun deleteFolder(target: PackFolder) {
        if (closed) return
        val dir = folders.dir(target.path) ?: return
        if (PackFiles.isEmptyDir(dir)) {
            runCatching { Files.deleteIfExists(dir) }
            leaveDeleted(target.path)
            refresh()
        } else {
            dialog = PackDialog.DeleteFolder(target)
        }
    }

    fun answerDeleteFolder(target: PackFolder, accepted: Boolean) {
        dialog = null
        if (!accepted || closed) return
        val dir = folders.dir(target.path) ?: return
        leaveDeleted(target.path)
        remove(listOf(dir), PackFiles.trashSupported)
    }

    fun folderNameError(parent: String, name: String, except: String? = null): String? {
        PackFiles.validName(name)?.let { return it }
        val path = PackFolders.join(parent, name.trim())
        if (path == except) return null
        val clash = runCatching { packDir.resolve(path) }.getOrNull()?.let { Files.exists(it, LinkOption.NOFOLLOW_LINKS) } == true
        return if (clash) "Something with that name is already here." else null
    }

    fun click(side: PackSide, id: String, visible: List<String>, toggle: Boolean, range: Boolean) {
        val previous = anchor
        if (range && previous != null && previous.first == side) {
            val from = visible.indexOf(previous.second)
            val to = visible.indexOf(id)
            if (from >= 0 && to >= 0) {
                val span = visible.subList(minOf(from, to), maxOf(from, to) + 1)
                picked = if (toggle) picked + span else span.toSet()
                return
            }
        }
        anchor = side to id
        picked = when {
            toggle -> if (id in picked) picked - id else picked + id
            picked == setOf(id) -> emptySet()
            else -> setOf(id)
        }
    }

    fun pickAll(ids: List<String>) {
        picked = ids.toSet()
    }

    fun clearPicks() {
        picked = emptySet()
        anchor = null
    }

    fun dragIds(side: PackSide, id: String, visible: List<String>): List<String> =
        if (id in picked) visible.filter { it in picked && sideOf(it) == side } else listOf(id)

    fun sideOf(id: String): PackSide? = when {
        id == DEFAULT_ID -> PackSide.Selected
        id in chosen -> PackSide.Selected
        id in packs -> PackSide.Available
        else -> null
    }

    fun tick() {
        if (!closed && removing == 0 && watcher.poll()) reloadKeeping(emptyMap())
    }

    fun resume() {}

    fun openFolder() = PackFiles.openPath(packDir)

    fun icon(item: PackItem): ImageBitmap? =
        icons.computeIfAbsent(item.id) { Optional.ofNullable(readIcon(item)) }.orElse(null)

    fun cachedIcon(item: PackItem): ImageBitmap? = icons[item.id]?.orElse(null)

    fun folderIcon(path: String): ImageBitmap? =
        icons.computeIfAbsent("folder:$path") { Optional.ofNullable(folders.iconFile(path)?.let(::decode)) }.orElse(null)

    fun cachedFolderIcon(path: String): ImageBitmap? = icons["folder:$path"]?.orElse(null)

    fun commit() {
        if (closed) return
        if (removing > 0) {
            commitPending = true
            dialog = null
            return
        }
        closed = true
        watcher.close()
        dialog = null
        if (routed) ClientPlatform.setScreen(exit)
        nextTick { apply() }
    }

    fun commitLater() {
        if (closed) return
        nextTick { commit() }
    }

    private fun nextTick(action: () -> Unit) {
        Multithreading.submit { mc.executeTask(Runnable { action() }) }
    }

    fun drop(files: List<Path>) {
        if (closed || files.isEmpty()) return
        dialog = PackDialog.Drop(files)
    }

    fun answerDrop(files: List<Path>, accepted: Boolean) {
        dialog = null
        if (accepted) dialog = import(files)
    }

    fun profiles(): List<PackProfile> = PackProfiles.all()

    fun saveProfile(name: String) {
        dialog = null
        if (!PackProfiles.save(name, chosen.toList())) PackProfileNotice.show()
        changed()
    }

    fun deleteProfile(profile: PackProfile, accepted: Boolean) {
        dialog = null
        if (accepted && !PackProfiles.delete(profile)) PackProfileNotice.show()
        changed()
    }

    fun applyProfile(profile: PackProfile, confirmed: Boolean = false) {
        if (closed) return
        val known = profile.packs.filter { it in packs }.distinct()
        if (!confirmed) {
            val risky = known.filter { it !in chosen }.mapNotNull(::item).filter { !it.compatible }
            if (PolyPlusConfig.packVersionWarnings && risky.isNotEmpty()) {
                dialog = PackDialog.Incompatible(risky, PackPlacement(known, null, false), profile)
                return
            }
        }
        chosen.clear()
        chosen.addAll(known)
        clearPicks()
        changed()
        val missing = profile.packs.size - known.size
        if (missing > 0) {
            dialog = PackDialog.Notice("Profile applied", if (missing == 1) "One pack in this profile isn't installed." else "$missing packs in this profile aren't installed.")
        }
    }

    fun optionsFor(item: PackItem): Boolean = false

    fun openOptions(item: PackItem) {}

    fun onlineActions(): List<PackOnlineAction> = emptyList()

    fun runOnline(action: PackOnlineAction) {}

    fun canOpenPackedPacks(): Boolean = false

    fun openPackedPacks() {}

    fun returningTo(current: Screen?): Boolean = false

    fun abandon() = commitLater()

    private fun leaveDeleted(path: String) {
        if (folder == path || folder.startsWith("$path/")) folder = PackFolders.parentOf(path)
    }

    private fun remove(paths: List<Path>, trash: Boolean) {
        if (paths.isEmpty()) return
        removing++
        busy = true
        PackFiles.remove(paths, trash) done@{ failed ->
            removing--
            busy = removing > 0
            if ((closed || commitPending) && failed.isNotEmpty()) PackDeleteNotice.show(failed)
            if (closed) return@done
            reloadKeeping(emptyMap())
            if (commitPending) {
                if (removing == 0) {
                    commitPending = false
                    commit()
                }
                return@done
            }
            if (failed.isNotEmpty()) {
                dialog = if (trash) {
                    PackDialog.HardDelete(failed)
                } else {
                    PackDialog.Notice("Couldn't delete", "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.")
                }
            }
        }
    }

    private fun reloadKeeping(renames: Map<String, String>) {
        val desired = chosen.map { renames[it] ?: it }
        repository.reload()
        index()
        chosen.clear()
        chosen.addAll(desired.filter { it in packs }.distinct())
        picked = picked.mapTo(HashSet()) { renames[it] ?: it }
        if (renames.isNotEmpty() && !PackProfiles.rename(renames)) PackProfileNotice.show()
        refreshCaches()
    }

    private fun index() {
        packs.clear()
        for (pack in repository.getAvailablePacks()) packs.putIfAbsent(idOf(pack), pack)
    }

    private fun place(placement: PackPlacement) {
        if (closed) return
        val moving = placement.ids.filter { it in packs }.distinct()
        if (moving.isEmpty()) return
        val fresh = moving.filter { it !in chosen }
        if (placement.positioned) {
            val rest = chosen.filter { it !in moving }
            val anchor = placement.anchor?.let { id -> if (id in moving) chosen.dropWhile { it != id }.firstOrNull { it !in moving } else id }
            val at = anchor?.let { rest.indexOf(it) }?.takeIf { it >= 0 } ?: rest.size
            chosen.clear()
            chosen.addAll(rest.take(at) + moving + rest.drop(at))
        } else {
            chosen.addAll(0, fresh)
        }
        picked = picked - placement.ids.toSet()
        changed()
    }

    private fun apply() {
        val order = chosen.mapNotNull { packs[it] }.reversed()
        val names = order.map { it.getName() }
        if (names == initial && !forceReload) return
        repository.setSelectedPacks(order)
        val options = mc.options
        options.resourcePacks.clear()
        options.incompatibleResourcePacks.clear()
        for (pack in order) {
            options.resourcePacks.add(pack.getName())
            if (format(pack) != LEGACY_FORMAT) options.incompatibleResourcePacks.add(pack.getName())
        }
        options.save()
        mc.reloadResources()
    }

    private fun refreshCaches() {
        icons.clear()
        changedAt.clear()
        folders.clear()
        changed()
    }

    private fun import(files: List<Path>): PackDialog? {
        val candidates = files.filter { path ->
            val file = path.toFile()
            (file.isFile && file.name.endsWith(".zip")) || (file.isDirectory && File(file, "pack.mcmeta").isFile)
        }
        val leftover = files.filter { it !in candidates }
        var failed = false
        for (path in candidates) {
            try {
                copyPack(path, packDir.resolve(path.fileName.toString()))
            } catch (_: IOException) {
                failed = true
            }
        }
        if (candidates.isNotEmpty()) reloadKeeping(emptyMap())
        if (failed) return PackDialog.Notice("Some packs weren't added", "Couldn't copy every pack into the pack folder.")
        if (leftover.isNotEmpty()) return PackDialog.Notice("Some files weren't added", "These aren't packs: ${names(leftover)}")
        return null
    }

    private fun copyPack(source: Path, target: Path) {
        if (!Files.isDirectory(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        Files.walk(source).use { stream ->
            for (path in stream.toList()) {
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(destination) else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun changed() {
        revision++
    }

    private fun changed(item: PackItem): Long = changedAt.computeIfAbsent(item.id) {
        item.file?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrNull() } ?: 0L
    }

    private fun fileOf(pack: ResourcePacks.UnopenedPack): File = (pack as Any as PackUnopenedPackAccessor).`polyplus$file`()

    private fun idOf(pack: ResourcePacks.UnopenedPack): String = idOf(fileOf(pack).toPath())

    private fun idOf(path: Path): String {
        val base = packDir.toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        val relative = if (target.startsWith(base)) base.relativize(target).toString() else target.fileName.toString()
        return "file/" + relative.replace('\\', '/')
    }

    private fun item(id: String): PackItem? {
        if (id == DEFAULT_ID) return defaultItem()
        val pack = packs[id] ?: return null
        val format = format(pack)
        val selected = id in chosen
        val index = chosen.indexOf(id)
        val compatible = format == LEGACY_FORMAT
        val file = fileOf(pack).toPath()
        return PackItem(
            id = id,
            title = file.fileName.toString(),
            description = plain(runCatching { pack.getDescription() }.getOrDefault("")),
            compatible = compatible,
            warning = if (compatible) "" else I18n.translate(if (format < LEGACY_FORMAT) "resourcePack.incompatible.old" else "resourcePack.incompatible.new"),
            confirm = if (compatible) "" else I18n.translate(if (format > LEGACY_FORMAT) "resourcePack.incompatible.confirm.new" else "resourcePack.incompatible.confirm.old"),
            selected = selected,
            required = false,
            fixed = false,
            canSelect = !selected,
            canUnselect = selected,
            canMoveUp = selected && index > 0,
            canMoveDown = selected && index < chosen.size - 1,
            server = false,
            folder = PackFolders.folderOf(id),
            file = file.takeIf { it.toAbsolutePath().normalize().startsWith(packDir.toAbsolutePath().normalize()) },
            pack = pack,
        )
    }

    private fun defaultItem(): PackItem = PackItem(
        id = DEFAULT_ID,
        title = "Default",
        description = "The default look of Minecraft",
        compatible = true,
        warning = "",
        confirm = "",
        selected = true,
        required = true,
        fixed = true,
        canSelect = false,
        canUnselect = false,
        canMoveUp = false,
        canMoveDown = false,
        server = false,
        folder = "",
        file = null,
        pack = null,
    )

    private fun format(pack: ResourcePacks.UnopenedPack): Int = runCatching { pack.getFormat() }.getOrDefault(LEGACY_FORMAT)

    private fun readIcon(item: PackItem): ImageBitmap? = runCatching {
        val pack = item.pack
        val image = if (pack == null) repository.defaultPack.getIcon() else (pack as Any as PackUnopenedPackAccessor).`polyplus$icon`()
        image?.let(::bitmap)
    }.getOrNull()

    private fun bitmap(image: BufferedImage): ImageBitmap? {
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(image, "png", out)) return null
        return PolyBitmap.decode(out.toByteArray())?.asComposeImageBitmap()
    }

    private fun decode(file: Path): ImageBitmap? = runCatching {
        PolyBitmap.decode(Files.readAllBytes(file))?.asComposeImageBitmap()
    }.getOrNull()

    companion object {
        private const val DEFAULT_ID = "vanilla"
        private const val LEGACY_FORMAT = 1
        private val FORMATTING = Regex("§.")

        val INCOMPATIBLE_TITLE: String get() = I18n.translate("resourcePack.incompatible.confirm.title")

        fun names(files: Collection<Path>): String = files.joinToString(", ") { it.fileName.toString() }

        fun resources(): PackSession = PackSession(null, false)

        fun resources(exit: Screen?): PackSession = PackSession(exit, true)

        private fun plain(text: String): String = text.replace(FORMATTING, "")
    }
}
*///?}

internal object PackParking {
    private var parked: PackSession? = null

    fun park(session: PackSession) {
        val previous = parked
        if (previous != null && previous !== session) previous.abandon()
        parked = session
    }

    fun take(): PackSession? {
        val session = parked ?: return null
        parked = null
        if (session.returningTo(ClientPlatform.currentScreen())) return session
        session.abandon()
        return null
    }
}

internal object PackDeleteNotice {
    fun show(failed: List<Path>) {
        val message = "${PackSession.names(failed)} ${if (failed.size == 1) "is" else "are"} still in the pack folder. It may be in use by the game."
        runCatching { Notifications.error("Couldn't delete", message) }
    }
}

internal object PackDrops {
    var active: PackSession? = null

    fun drop(files: List<Path>): Boolean {
        val session = active ?: return false
        session.drop(files)
        return true
    }
}
