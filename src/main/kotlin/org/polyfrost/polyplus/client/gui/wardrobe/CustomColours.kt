@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.colors.ColorSaveException
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaves
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalette
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import org.polyfrost.polyplus.client.cosmetics.colors.CustomPaletteOrder
import org.polyfrost.polyplus.client.cosmetics.colors.EditorSessions
import org.polyfrost.polyplus.client.cosmetics.colors.OutfitAuraStep
import org.polyfrost.polyplus.client.cosmetics.colors.PaletteJobs
import org.polyfrost.polyplus.client.cosmetics.colors.PaletteSlot
import org.polyfrost.polyplus.client.cosmetics.colors.SavedColors
import org.polyfrost.polyplus.client.cosmetics.colors.auraPalette
import org.polyfrost.polyplus.client.cosmetics.colors.outfitAuraStep
import org.polyfrost.polyplus.client.cosmetics.colors.pairedOutcome
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

internal class CustomColours(
    val ids: List<Int>,
    val palette: CosmeticPalette,
    val colorway: String?,
) {
    private val primary: Int get() = ids.first()

    private val counterparts by lazy { counterpartsOf(ids) }

    private val lockIds by lazy { ColourSaves.group(ids + counterparts.sameColourway + counterparts.others) }

    private val sessions = EditorSessions()

    private var session = 0L

    var editing by mutableStateOf(false)
        private set

    var draft by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    var openSlot by mutableStateOf<String?>(null)

    val saving: Boolean get() = ColourSaves.pending(lockIds)

    val active: Boolean
        get() {
            CosmeticColors.revision
            return CosmeticColors.localColors(primary) != null
        }

    val selected: Boolean get() = editing || active

    val ready: Boolean
        get() {
            CosmeticColors.revision
            return CosmeticColors.palettesLoaded
        }

    private var touched = false
    private var draftFromSaved = false

    fun stored(): Map<String, Int>? =
        sharedPalette(ids, counterparts, CosmeticColors::localColors, CosmeticColors::remembered, localSaveOrder())

    fun open() {
        if (saving) return
        touched = false
        loadDraft()
        session = sessions.open()
        editing = true
        preview()
    }

    fun refreshDraft() {
        if (!editing || touched || draftFromSaved || !ready) return
        loadDraft()
        preview()
    }

    private fun loadDraft() {
        draftFromSaved = CosmeticColors.palettesLoaded
        val stored = stored().orEmpty()
        draft = palette.baseColors(colorway) + stored.filterKeys { key -> palette.customizable.any { it.id == key } }
    }

    fun change(slot: String, rgb: Int) {
        if (saving) return
        touched = true
        draft = draft + (slot to (rgb and RGB_MASK))
        preview()
    }

    fun resetDraft() {
        if (saving) return
        touched = true
        draft = palette.baseColors(colorway)
        preview()
    }

    fun save(onResult: (Result<Unit>) -> Unit) {
        if (!editing) return
        val colors = masked(draft)
        val owned = CosmeticCatalog.ownedCosmeticIds()
        val equipped = CosmeticCatalog.localEquipped().ids().toHashSet()
        val targets = paletteWrites(ids, counterparts, colors, owned::contains, equipped::contains, ::savedWorn, ::savedKept)
        val account = ClientPlatform.localPlayerUuid()
        val group = (ids + counterparts.sameColourway + counterparts.others).toSet()
        val token = ColourSaves.start(lockIds) ?: run {
            onResult(ColourSaves.refused())
            return
        }
        openSlot = null
        val started = session
        launchForAll(
            token,
            onResult,
            account,
            targets,
            closeIf = { sessions.owns(started) && masked(draft) == colors },
            onSaved = { CustomPaletteOrder.mark(account, targets) },
            afterSaved = { reconcileWornPalettes(group) },
        ) { id, look ->
            CosmeticColors.set(id, colors, account, look)
        }
    }

    fun close() {
        openSlot = null
        editing = false
        sessions.close()
        ids.forEach(CosmeticColors::endPreview)
    }

    fun useDesigned(onResult: (Result<Unit>) -> Unit) {
        if (saving) {
            onResult(ColourSaves.refused())
            return
        }
        close()
        val owned = CosmeticCatalog.ownedCosmeticIds()
        val targets = designedTargets(ids, counterparts, owned::contains, ::savedWorn)
        if (targets.isEmpty()) return
        val token = ColourSaves.start(lockIds) ?: run {
            onResult(ColourSaves.refused())
            return
        }
        val account = ClientPlatform.localPlayerUuid()
        launchForAll(token, onResult, account, targets) { id, look -> CosmeticColors.deactivate(id, account, look) }
    }

    private fun launchForAll(
        token: Long,
        onResult: (Result<Unit>) -> Unit,
        account: UUID,
        targets: List<Int>,
        closeIf: () -> Boolean = { false },
        onSaved: () -> Unit = {},
        afterSaved: () -> Unit = {},
        call: suspend (Int, Long) -> Result<Unit>,
    ) {
        val look = CosmeticColors.lookGeneration()
        launchHolding(token, { saveAllOrNone(targets, account, look, onSaved, call) }) { result ->
            if (result.isSuccess && closeIf()) close()
            onResult(result)
            if (result.isSuccess) afterSaved()
        }
    }

    private fun masked(colors: Map<String, Int>): Map<String, Int> = colors.mapValues { (_, rgb) -> rgb and RGB_MASK }

    private fun preview() {
        for (id in ids) CosmeticColors.preview(id, draft)
    }

    private companion object {
        const val RGB_MASK = 0x00FF_FFFF
    }
}

private fun launchHolding(token: Long, work: suspend () -> Result<Unit>, done: (Result<Unit>) -> Unit) {
    PolyPlusClient.SCOPE.launch {
        try {
            val result = work()
            ClientPlatform.runOnMain { done(result) }
        } finally {
            ColourSaves.finish(token)
        }
    }
}

@Composable
internal fun rememberCustomColours(representatives: List<Int>?, owned: Boolean): CustomColours? {
    val slim = rememberLocalSkinSlim()
    val pieces = representatives?.let { skinPieces(it, slim) }
    val primary = pieces?.firstOrNull()
    val installs = CosmeticAssetCache.installs
    val definition = primary?.let(CosmeticCatalog::getCosmeticDefinition)
    val revision = definition?.let(CosmeticRevision::of)
    val variantKey = definition?.variantKey
    var palette by remember(primary, revision) { mutableStateOf(primary?.let(CosmeticPalettes::cached)) }
    LaunchedEffect(primary, revision, owned, installs) {
        if (!owned || primary == null || palette != null) return@LaunchedEffect
        var backoff = PALETTE_RETRY_MS
        repeat(PALETTE_ATTEMPTS) { attempt ->
            val loaded = withContext(Dispatchers.IO) { CosmeticPalettes.load(primary) }
            if (loaded != null || CosmeticPalettes.isSettled(primary)) {
                palette = loaded
                return@LaunchedEffect
            }
            if (attempt < PALETTE_ATTEMPTS - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(PALETTE_MAX_RETRY_MS)
            }
        }
    }
    val loaded = palette
    val colours = remember(pieces, loaded, revision) {
        if (pieces.isNullOrEmpty() || loaded == null || !loaded.hasCustomizable) {
            null
        } else {
            CustomColours(pieces, loaded, loaded.colorwayFor(variantKey))
        }
    }
    DisposableEffect(colours) { onDispose { colours?.close() } }
    return colours.takeIf { owned }
}

@Composable
internal fun CustomColoursChip(colours: CustomColours) {
    val saving = colours.saving
    Chip(
        label = if (saving) "Saving..." else "Custom",
        selected = colours.selected,
        onClick = { if (!colours.editing) colours.open() },
        enabled = !saving,
        size = ControlSize.Compact,
        sound = null,
        leading = { CustomSwatchStripes(colours) },
    )
}

@Composable
private fun CustomSwatchStripes(colours: CustomColours) {
    CosmeticColors.revision
    val shown = if (colours.editing) colours.draft else colours.stored().orEmpty()
    val stripes = colours.palette.customizable.map { slot ->
        shown[slot.id] ?: colours.palette.baseColor(slot, colours.colorway)
    }
    Box(Modifier.size(width = 13.dp, height = 20.dp).clip(OneConfigTheme.shape(4.dp)).background(OneConfigTheme.current.chipBackground)) {
        Column(Modifier.fillMaxSize()) {
            for (rgb in stripes) {
                Box(Modifier.fillMaxWidth().weight(1f).background(opaque(rgb)))
            }
        }
    }
}

@Composable
internal fun CustomColoursPanel(colours: CustomColours, onStatus: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!colours.editing) return
    val theme = OneConfigTheme.current
    val latestStatus by rememberUpdatedState(onStatus)
    Column(
        modifier = modifier.fillMaxWidth()
            .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(12.dp), glass = false)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your colors", color = theme.textColorSecondary, style = theme.typography.small)
        val ready = colours.ready
        LaunchedEffect(ready) { colours.refreshDraft() }
        SlotList(colours)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SaveColoursButton(colours, ready, latestStatus)
            TextButton(text = "Cancel", onClick = { colours.close() }, size = ControlSize.Compact, sound = null)
            TextButton(text = "Start over", onClick = { colours.resetDraft() }, size = ControlSize.Compact, sound = null, enabled = !colours.saving)
        }
    }
}

@Composable
internal fun SlotList(colours: CustomColours) {
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.fillMaxWidth().heightIn(max = MAX_SLOT_LIST_HEIGHT), scrollbar = false) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (slot in colours.palette.slots) {
                SlotRow(colours, slot)
            }
        }
    }
}

@Composable
internal fun SaveColoursButton(colours: CustomColours, ready: Boolean, onStatus: (String) -> Unit) {
    val saving = colours.saving
    TextButton(
        text = when {
            !ready -> "Loading..."
            saving -> "Saving..."
            else -> "Save"
        },
        onClick = {
            ColourSaves.note(colours.ids, null)
            colours.save { result ->
                val message = result.fold(
                    onSuccess = { "Saved your colors." },
                    onFailure = { colourFailureMessage(it, "Couldn't save your colors. Try again.") },
                )
                ColourSaves.note(colours.ids, message)
                onStatus(message)
            }
        },
        style = ControlStyle.Primary,
        size = ControlSize.Compact,
        sound = null,
        enabled = ready && !saving,
    )
}

@Composable
internal fun SlotRow(colours: CustomColours, slot: PaletteSlot) {
    val theme = OneConfigTheme.current
    val locked = !slot.customizable
    val saving = colours.saving
    val rgb = colours.draft[slot.id] ?: colours.palette.baseColor(slot, colours.colorway)
    val shape = OneConfigTheme.shape(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(SLOT_ROW_HEIGHT)
            .graphicsLayer { alpha = if (locked || saving) theme.disabledAlpha else 1f },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier.size(SWATCH_SIZE)
                    .clip(shape)
                    .background(opaque(rgb))
                    .border(1.dp, theme.borderColor, shape)
                    .let {
                        if (locked) it else it.oneConfigClickable(
                            "Change ${slot.name}",
                            { colours.openSlot = if (colours.openSlot == slot.id) null else slot.id },
                            enabled = !saving,
                            shape = shape,
                            indication = false,
                            sound = null,
                        )
                    },
            )
            if (!locked && !saving && colours.openSlot == slot.id) {
                AuraColorPopup(
                    selectedColor = rgb,
                    onPreview = { colours.change(slot.id, it) },
                    onCommit = { picked -> picked?.let { colours.change(slot.id, it) } },
                    onDismiss = { if (colours.openSlot == slot.id) colours.openSlot = null },
                    title = slot.name,
                    withAlpha = false,
                    placement = Placement.BelowStart,
                )
            }
        }
        Text(
            slot.name,
            modifier = Modifier.weight(1f),
            color = theme.textColor,
            style = theme.typography.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (locked) {
            ThemedIcon("lock", theme.textColorSecondary, 12.dp)
            Text("Locked", color = theme.textColorSecondary, style = theme.typography.small)
        } else {
            Text(hex(rgb), color = theme.textColorSecondary, style = theme.typography.small)
        }
    }
}

internal fun skinPieces(pieces: List<Int>, slim: Boolean): List<Int> =
    pieces.map { CosmeticCatalog.resolveVariantForSkin(it, slim) }.distinct()

internal class PaletteCounterparts(val sameColourway: List<Int>, val others: List<Int>)

internal fun paletteCounterparts(definition: CosmeticDefinition, group: List<CosmeticDefinition>): PaletteCounterparts {
    val slots = definition.allowedSlots.toSet()
    val (same, others) = group
        .filter { it.id != definition.id && it.allowedSlots.toSet() == slots }
        .partition { it.variantName == definition.variantName }
    return PaletteCounterparts(same.map { it.id }, others.map { it.id })
}

private fun counterpartsOf(ids: List<Int>): PaletteCounterparts {
    val same = ArrayList<Int>()
    val others = ArrayList<Int>()
    for (id in ids) {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: continue
        val group = CosmeticCatalog.groupContaining(id)?.variants ?: continue
        val found = paletteCounterparts(definition, group)
        same += found.sameColourway
        others += found.others
    }
    return PaletteCounterparts((same - ids.toSet()).distinct(), (others - ids.toSet()).distinct())
}

internal fun paletteWrites(
    ids: List<Int>,
    counterparts: PaletteCounterparts,
    colors: Map<String, Int>,
    owned: (Int) -> Boolean,
    equipped: (Int) -> Boolean,
    worn: (Int) -> Map<String, Int>?,
    kept: (Int) -> Map<String, Int>?,
): List<Int> {
    val siblings = counterparts.sameColourway.filter { owned(it) && it !in ids && (worn(it) != colors || kept(it) != colors) }
    val wornElsewhere = counterparts.others.filter { owned(it) && it !in ids && equipped(it) && worn(it).let { palette -> palette != null && palette != colors } }
    return (ids + siblings + wornElsewhere).distinct()
}

internal fun designedTargets(
    ids: List<Int>,
    counterparts: PaletteCounterparts,
    owned: (Int) -> Boolean,
    worn: (Int) -> Map<String, Int>?,
): List<Int> = (ids + counterparts.sameColourway.filter(owned)).distinct().filter { worn(it) != null }

private fun savedWorn(id: Int): Map<String, Int>? = CosmeticColors.saved(id).worn

private fun savedKept(id: Int): Map<String, Int>? = CosmeticColors.saved(id).kept

private fun localSaveOrder(): (Int) -> Long {
    val account = ClientPlatform.localPlayerUuid()
    return { id -> CustomPaletteOrder.of(account, id) }
}

internal class PaletteSource(val colors: Map<String, Int>, val order: Long)

internal fun sharedPalette(
    ids: List<Int>,
    counterparts: PaletteCounterparts,
    worn: (Int) -> Map<String, Int>?,
    remembered: (Int) -> Map<String, Int>?,
    order: (Int) -> Long = { 0L },
): Map<String, Int>? = sharedPaletteSource(ids, counterparts, worn, remembered, order)?.colors

internal fun sharedPaletteSource(
    ids: List<Int>,
    counterparts: PaletteCounterparts,
    worn: (Int) -> Map<String, Int>?,
    remembered: (Int) -> Map<String, Int>?,
    order: (Int) -> Long = { 0L },
): PaletteSource? =
    newestPalette(ids + counterparts.sameColourway + counterparts.others, remembered, order)
        ?: (
            ids.firstNotNullOfOrNull(remembered)
                ?: counterparts.sameColourway.firstNotNullOfOrNull(remembered)
                ?: counterparts.others.firstNotNullOfOrNull(worn)
                ?: counterparts.others.firstNotNullOfOrNull(remembered)
            )?.let { PaletteSource(it, 0L) }

private fun newestPalette(candidates: List<Int>, remembered: (Int) -> Map<String, Int>?, order: (Int) -> Long): PaletteSource? {
    var newest: PaletteSource? = null
    for (id in candidates) {
        val stamp = order(id)
        if (stamp <= (newest?.order ?: 0L)) continue
        val palette = remembered(id) ?: continue
        newest = PaletteSource(palette, stamp)
    }
    return newest
}

internal fun stalePalette(
    id: Int,
    counterparts: PaletteCounterparts,
    worn: (Int) -> Map<String, Int>?,
    remembered: (Int) -> Map<String, Int>?,
    order: (Int) -> Long,
): PaletteSource? {
    val current = worn(id) ?: return null
    return sharedPaletteSource(listOf(id), counterparts, worn, remembered, order)?.takeIf { it.colors != current }
}

private val paletteJobs = PaletteJobs()

internal fun syncWornPalettes(equipped: Collection<Int>) {
    if (!CosmeticColors.palettesLoaded) return
    val account = ClientPlatform.localPlayerUuid()
    val look = CosmeticColors.lookGeneration()
    val order = localSaveOrder()
    val stale = equipped.distinct().mapNotNull { id ->
        if (paletteJobs.busy(id)) return@mapNotNull null
        val counterparts = counterpartsOf(listOf(id))
        stalePalette(id, counterparts, CosmeticColors::localColors, CosmeticColors::remembered, order) ?: return@mapNotNull null
        if (paletteJobs.claim(id)) id to counterparts else null
    }
    if (stale.isEmpty()) return
    PolyPlusClient.SCOPE.launch {
        for ((id, counterparts) in stale) {
            paletteJobs.runClaimed(id) { refreshWornPalette(id, counterparts, account, look, order) }
        }
    }
}

private fun reconcileWornPalettes(group: Set<Int>) {
    if (!CosmeticColors.palettesLoaded) return
    val worn = CosmeticCatalog.localEquipped().ids().filter { it in group }.distinct()
    if (worn.isEmpty()) return
    val account = ClientPlatform.localPlayerUuid()
    val look = CosmeticColors.lookGeneration()
    val order = localSaveOrder()
    val checks = worn.map { id -> id to counterpartsOf(listOf(id)) }
    PolyPlusClient.SCOPE.launch {
        for ((id, counterparts) in checks) {
            paletteJobs.hold(listOf(id)) { refreshWornPalette(id, counterparts, account, look, order) }
        }
    }
}

private suspend fun refreshWornPalette(id: Int, counterparts: PaletteCounterparts, account: UUID, look: Long, order: (Int) -> Long) {
    var source: PaletteSource? = null
    CosmeticColors.propagate(id, account, look) {
        stalePalette(id, counterparts, CosmeticColors::localColors, CosmeticColors::remembered, order)
            .also { source = it }
            ?.colors
    }.onSuccess { wrote ->
        val written = source
        if (wrote && written != null) CustomPaletteOrder.raise(account, id, written.order)
    }
}

internal fun applyOutfitAura(aura: Int, color: Int?, before: SavedColors?): Job {
    val account = ClientPlatform.localPlayerUuid()
    val look = CosmeticColors.lookGeneration()
    val order = localSaveOrder()
    val counterparts = counterpartsOf(listOf(aura))
    fun step(resend: Boolean): OutfitAuraStep {
        val latest = sharedPalette(listOf(aura), counterparts, CosmeticColors::localColors, CosmeticColors::remembered, order)
        return outfitAuraStep(CosmeticColors.remembered(aura), latest, color, resend)
    }
    val job: suspend (Boolean) -> Unit = job@{ waited ->
        if (CosmeticColors.lookStale(aura, look)) return@job
        if (color == null && !CosmeticColors.outfitAuraCleared(aura, if (waited) CosmeticColors.saved(aura) else before)) return@job
        when (step(waited)) {
            OutfitAuraStep.Settled -> {
                if (color != null || CosmeticColors.remembered(aura) != null) CustomPaletteOrder.mark(account, listOf(aura))
            }
            OutfitAuraStep.Clear -> CosmeticColors.set(aura, null, account, look)
            is OutfitAuraStep.Write -> CosmeticColors.propagate(aura, account, look) {
                (step(waited) as? OutfitAuraStep.Write)?.colors ?: auraPalette(CosmeticColors.remembered(aura), color)
            }.onSuccess { wrote -> if (wrote) CustomPaletteOrder.mark(account, listOf(aura)) }
        }
    }
    return PolyPlusClient.SCOPE.launch(start = CoroutineStart.UNDISPATCHED) { paletteJobs.run(aura, job) }
}

internal fun useDesignedColours(representatives: List<Int>, onResult: (Result<Unit>) -> Unit) {
    val active = (skinPieces(representatives, true) + skinPieces(representatives, false)).distinct()
        .filter { CosmeticColors.localColors(it) != null }
    if (active.isEmpty()) return
    val token = ColourSaves.start(representatives + active) ?: run {
        onResult(ColourSaves.refused())
        return
    }
    val account = ClientPlatform.localPlayerUuid()
    val look = CosmeticColors.lookGeneration()
    launchHolding(token, { saveAllOrNone(active, account, look) { id, seen -> CosmeticColors.deactivate(id, account, seen) } }, onResult)
}

private suspend fun saveAllOrNone(
    ids: List<Int>,
    account: UUID,
    look: Long,
    onSaved: () -> Unit = {},
    call: suspend (Int, Long) -> Result<Unit>,
): Result<Unit> = paletteJobs.hold(ids) {
    val before = ids.associateWith(CosmeticColors::saved)
    val results = coroutineScope { ids.map { id -> async { call(id, look) } }.awaitAll() }
    pairedOutcome(results) { saved ->
        coroutineScope {
            saved.map { index -> async { CosmeticColors.restore(ids[index], before.getValue(ids[index]), account, look) } }.awaitAll()
        }.all { it.isSuccess }
    }.onSuccess { onSaved() }
}

internal fun coloursSaving(variants: Collection<CosmeticVariantUi>): Boolean =
    ColourSaves.pending(variants.flatMap { it.pieces })

internal fun coloursSaving(item: CosmeticUiItem): Boolean =
    ColourSaves.pending(item.memberIds + item.variants.flatMap { it.pieces })

internal fun colourFailureMessage(error: Throwable, fallback: String): String =
    (error as? ColorSaveException)?.message ?: fallback

private fun opaque(rgb: Int): Color = Color(rgb or (0xFF shl 24))

private fun hex(rgb: Int): String = "#%06X".format(rgb and 0x00FF_FFFF)

private const val PALETTE_ATTEMPTS = 6

private const val PALETTE_RETRY_MS = 2_000L

private const val PALETTE_MAX_RETRY_MS = 30_000L

private val MAX_SLOT_LIST_HEIGHT = 168.dp

private val SLOT_ROW_HEIGHT = 24.dp

private val SWATCH_SIZE = 18.dp
