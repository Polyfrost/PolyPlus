package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 || = 1.8.9 {
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
//?}
//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
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

//? if >= 26.3 || = 1.8.9 {

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
    class Incompatible(val items: List<PackItem>, val placement: PackPlacement) : PackDialog

    class Drop(val files: List<Path>) : PackDialog

    class Notice(val title: String, val message: String) : PackDialog

    object SaveProfile : PackDialog

    class DeleteProfile(val profile: PackProfile) : PackDialog

    class NewFolder(val parent: String, val moving: List<String>) : PackDialog

    class RenameFolder(val path: String) : PackDialog

    class DeleteFolder(val folder: PackFolder) : PackDialog

    class DeletePacks(val items: List<PackItem>) : PackDialog

    class Overwrite(val ids: List<String>, val target: String, val names: List<String>) : PackDialog

    class HardDelete(val paths: List<Path>) : PackDialog
}

//?}

//? if >= 26.3 {
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
    private var away = Away.None
    private var leftFrom: Any? = null

    var host: () -> Screen? = { ClientPlatform.currentScreen() }

    val isAway: Boolean get() = away != Away.None && !closed

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
        if (risky.isNotEmpty()) {
            dialog = PackDialog.Incompatible(risky, placement)
            return
        }
        place(placement)
    }

    fun answerIncompatible(dialog: PackDialog.Incompatible, accepted: Boolean) {
        this.dialog = null
        if (accepted) place(dialog.placement)
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
        entry(item.id)?.takeIf { it.isSelected && it.canMoveUp() }?.moveUp()
        changed()
    }

    fun moveDown(item: PackItem) {
        entry(item.id)?.takeIf { it.isSelected && it.canMoveDown() }?.moveDown()
        changed()
    }

    fun moveToTop(item: PackItem) {
        val current = selectedIds()
        if (item.id !in current) return
        arrange(listOf(item.id) + current.filter { it != item.id })
        changed()
    }

    fun moveToBottom(item: PackItem) {
        var steps = selectedIds().size
        while (steps-- > 0) {
            val entry = entry(item.id)?.takeIf { it.isSelected && it.canMoveDown() } ?: break
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
        val targetDir = (if (target.isEmpty()) packDir else folders.dir(target)) ?: return
        val moves = ids.mapNotNull { id ->
            val source = PackFolders.relative(id)?.let { runCatching { packDir.resolve(it) }.getOrNull() } ?: return@mapNotNull null
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return@mapNotNull null
            val destination = targetDir.resolve(source.fileName)
            if (destination == source || destination.startsWith(source)) null else Triple(id, source, destination)
        }
        if (moves.isEmpty()) return
        val conflicts = moves.filter { Files.exists(it.third, LinkOption.NOFOLLOW_LINKS) }
        if (conflicts.isNotEmpty() && !overwrite) {
            dialog = PackDialog.Overwrite(ids, target, conflicts.map { it.third.fileName.toString() })
            return
        }
        val renames = HashMap<String, String>()
        val failed = ArrayList<Path>()
        for ((id, source, destination) in moves) {
            try {
                PackFiles.move(source, destination, overwrite)
                renames[id] = idOf(destination)
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
        }
    }

    fun createFolder(parent: String, name: String, moving: List<String>) {
        dialog = null
        if (closed || !foldersEnabled) return
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
        val affected = (selected() + available()).map { it.id }.filter { it.startsWith(prefix) }
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
        busy = true
        PackFiles.remove(paths, trash) { failed ->
            busy = false
            if (!closed) {
                reloadKeeping(emptyMap())
                if (failed.isNotEmpty()) {
                    dialog = if (trash) {
                        PackDialog.HardDelete(failed)
                    } else {
                        PackDialog.Notice("Couldn't delete", "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.")
                    }
                }
            }
        }
    }

    private fun reloadKeeping(renames: Map<String, String>) {
        val desired = selectedIds().map { renames[it] ?: it }
        access.`polyplus$reload`()
        for (id in desired) entry(id)?.takeIf { !it.isSelected && it.canSelect() }?.select()
        val current = selectedIds()
        arrange(desired.filter { it in current } + current.filter { it !in desired })
        picked = picked.mapTo(HashSet()) { renames[it] ?: it }
        if (renames.isNotEmpty()) PackProfiles.rename(renames)
        refreshCaches()
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
        val pending = access.`polyplus$ticksToReload`() > 0L
        screen.tick()
        if (pending && access.`polyplus$ticksToReload`() == 0L) refreshCaches()
    }

    fun resume() {
        val pending = away
        away = Away.None
        leftFrom = null
        when {
            closed || pending == Away.Rebuild -> rebuild()
            pending == Away.Reload -> {
                access.`polyplus$reload`()
                refreshCaches()
            }
        }
    }

    fun openFolder() = Blaze3D.openPath(packDir)

    fun icon(item: PackItem): ImageBitmap? =
        icons.computeIfAbsent(item.id) { Optional.ofNullable(readIcon(item.pack)) }.orElse(null)

    fun cachedIcon(item: PackItem): ImageBitmap? = icons[item.id]?.orElse(null)

    fun folderIcon(path: String): ImageBitmap? =
        icons.computeIfAbsent("folder:$path") { Optional.ofNullable(folders.iconFile(path)?.let(::decode)) }.orElse(null)

    fun cachedFolderIcon(path: String): ImageBitmap? = icons["folder:$path"]?.orElse(null)

    fun commit() {
        if (closed) return
        closed = true
        dialog = null
        if (resources && PackBridges.consumeForcedReload()) Minecraft.getInstance().options.resourcePacks.clear()
        screen.onClose()
    }

    fun commitLater() {
        if (closed) return
        Minecraft.getInstance().schedule(Runnable { commit() })
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
        PackProfiles.save(name, selected().filter { !it.fixed }.map { it.id })
        changed()
    }

    fun deleteProfile(profile: PackProfile, accepted: Boolean) {
        dialog = null
        if (accepted) PackProfiles.delete(profile)
        changed()
    }

    fun applyProfile(profile: PackProfile) {
        if (closed) return
        val known = profile.packs.filter { entry(it) != null }
        unselect(selected().filter { it.id !in known && it.canUnselect }.map { it.id })
        for (id in known) entry(id)?.takeIf { it.canSelect() }?.select()
        val current = selectedIds()
        arrange(known.filter { it in current } + current.filter { it !in known })
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
            val moving = placement.ids.filter { it in current }
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
                if (!entry.canMoveUp()) break
                entry.moveUp()
            }
        }
    }

    private fun selectedIds(): List<String> = model.selected.map { it.id }.toList()

    private fun rebuild() {
        val args = modelAccess
        if (args == null) {
            if (!closed) access.`polyplus$reload`()
            refreshCaches()
            return
        }
        val title = screen.title
        if (!closed) runCatching { access.`polyplus$closeWatcher`() }
        screen = PackSelectionScreen(args.`polyplus$repository`(), args.`polyplus$output`(), packDir, title)
        closed = false
        clearPicks()
        refreshCaches()
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
            access.`polyplus$reload`()
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
        val file = PackFolders.relative(item.id)?.let { runCatching { packDir.resolve(it) }.getOrNull() }
        file?.let { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrNull() } ?: 0L
    }

    private fun item(entry: PackSelectionModel.Entry): PackItem {
        val compatibility = entry.compatibility
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
            canMoveUp = entry.isSelected && entry.canMoveUp(),
            canMoveDown = entry.isSelected && entry.canMoveDown(),
            server = entry.packSource == PackSource.SERVER,
            folder = PackFolders.folderOf(entry.id),
            file = fileOf(entry.id),
            pack = (entry as Any as PackEntryAccessor).`polyplus$pack`(),
            entry = entry,
        )
    }

    private fun fileOf(id: String): Path? {
        if (!resources) return null
        val relative = PackFolders.relative(id) ?: return null
        return runCatching { packDir.resolve(relative) }.getOrNull()?.takeIf { it.startsWith(packDir) }
    }

    private fun readIcon(pack: Pack): ImageBitmap? = runCatching {
        pack.openMetadata().use { resources ->
            val supplier = resources.getRootResource("pack.png") ?: return@use null
            supplier.get().use { stream -> PolyBitmap.decode(stream.readBytes())?.asComposeImageBitmap() }
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

    var host: () -> Screen? = { ClientPlatform.currentScreen() }

    val isAway: Boolean get() = false

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
        if (risky.isNotEmpty()) {
            dialog = PackDialog.Incompatible(risky, placement)
            return
        }
        place(placement)
    }

    fun answerIncompatible(dialog: PackDialog.Incompatible, accepted: Boolean) {
        this.dialog = null
        if (accepted) place(dialog.placement)
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
        val targetDir = (if (target.isEmpty()) packDir else folders.dir(target)) ?: return
        val moves = ids.mapNotNull { id ->
            val source = PackFolders.relative(id)?.let { runCatching { packDir.resolve(it) }.getOrNull() } ?: return@mapNotNull null
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return@mapNotNull null
            val destination = targetDir.resolve(source.fileName)
            if (destination == source || destination.startsWith(source)) null else Triple(id, source, destination)
        }
        if (moves.isEmpty()) return
        val conflicts = moves.filter { Files.exists(it.third, LinkOption.NOFOLLOW_LINKS) }
        if (conflicts.isNotEmpty() && !overwrite) {
            dialog = PackDialog.Overwrite(ids, target, conflicts.map { it.third.fileName.toString() })
            return
        }
        val renames = HashMap<String, String>()
        val failed = ArrayList<Path>()
        for ((id, source, destination) in moves) {
            try {
                PackFiles.move(source, destination, overwrite)
                renames[id] = idOf(destination)
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
        }
    }

    fun createFolder(parent: String, name: String, moving: List<String>) {
        dialog = null
        if (closed) return
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

    fun tick() {}

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
        closed = true
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
        PackProfiles.save(name, chosen.toList())
        changed()
    }

    fun deleteProfile(profile: PackProfile, accepted: Boolean) {
        dialog = null
        if (accepted) PackProfiles.delete(profile)
        changed()
    }

    fun applyProfile(profile: PackProfile) {
        if (closed) return
        val known = profile.packs.filter { it in packs }.distinct()
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
        busy = true
        PackFiles.remove(paths, trash) { failed ->
            busy = false
            if (!closed) {
                reloadKeeping(emptyMap())
                if (failed.isNotEmpty()) {
                    dialog = if (trash) {
                        PackDialog.HardDelete(failed)
                    } else {
                        PackDialog.Notice("Couldn't delete", "${names(failed)} may be in use by the game. Turn it off, leave this page, then try again.")
                    }
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
        if (renames.isNotEmpty()) PackProfiles.rename(renames)
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
        if (names == initial) return
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

//? if >= 26.3 || = 1.8.9 {
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

internal object PackDrops {
    var active: PackSession? = null

    fun drop(files: List<Path>): Boolean {
        val session = active ?: return false
        session.drop(files)
        return true
    }
}
//?}
