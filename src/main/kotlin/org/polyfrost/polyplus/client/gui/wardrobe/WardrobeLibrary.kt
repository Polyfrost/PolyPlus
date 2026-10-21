@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import org.polyfrost.polyplus.client.cosmetics.outfits.LibraryState
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitException
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.PlayerSkinInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinModel
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewSkin
import org.polyfrost.polyplus.client.gui.preview.PreviewTint
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform


internal const val MAX_LIBRARY_NAME_LENGTH = 64

private const val MAX_USERNAME_LENGTH = 16

private const val MAX_URL_LENGTH = 512

private const val SKIN_FIGURE_FILL = 0.85f

internal val SkinModel.label: String
    get() = when (this) {
        SkinModel.Classic -> "Classic"
        SkinModel.Slim -> "Slim"
    }

@Composable
internal fun OutfitThumbnail(outfit: OutfitInfo, modifier: Modifier) {
    val installs = CosmeticAssetCache.installs
    val textureVersion = MojangCapeService.textureVersion
    val mojangState = MojangCapeService.state
    LaunchedEffect(outfit.equipped) {
        for ((slot, id) in outfit.equipped) {
            if (!isOutfitAssetLoaded(slot, id)) CosmeticAssetCache.ensureCosmeticLoaded(id)
        }
    }
    val skins = SkinLibrary.skins
    val skinVersion = SkinLibrary.textureVersion
    val mojangCape = remember(outfit.mojangCapeId, mojangState, textureVersion) { outfitMojangCape(outfit) }
    val skin = remember(outfit.skinId, skins, skinVersion) { outfitSkin(outfit) }
    val source = remember(outfit.equipped, installs, mojangCape, skin, outfit.particleColor) {
        outfitPreviewSource(outfit.equipped, mojangCape, skin, PreviewTint(outfit.particleColor))
    }
    val loaded = outfit.equipped.count { (slot, id) -> isOutfitAssetLoaded(slot, id) }
    val capeId = outfit.equipped[BodySlot.Cape]
    PlayerPreview(
        modifier,
        source = source,
        autoSpin = false,
        allowDrag = false,
        modelScale = 0.42f,
        verticalAnchor = 0.52f,
        initialYaw = FRONT_YAW_DEG,
        previewKey = "outfit-${outfit.id}-${outfit.updatedAt}-$loaded-${mojangCape != null}-${skin?.texture}-${outfit.particleColor}",
        live = BodySlot.Aura in outfit.equipped || (capeId != null && CosmeticAssetCache.isCapeAnimated(capeId)),
    )
}

private fun isOutfitAssetLoaded(slot: BodySlot, id: Int): Boolean = when (slot) {
    BodySlot.Cape -> CosmeticAssetCache.isCapeLoaded(id)
    BodySlot.Pet -> CosmeticAssetCache.getAttachedCosmetic(id) != null || CosmeticAssetCache.getPetDefinition(id) != null
    else -> CosmeticAssetCache.getAttachedCosmetic(id) != null
}

@Composable
internal fun rememberOutfitPreview(outfit: OutfitInfo?): SelectionPreview? {
    val installs = CosmeticAssetCache.installs
    val textureVersion = MojangCapeService.textureVersion
    val mojangState = MojangCapeService.state
    val skins = SkinLibrary.skins
    val skinVersion = SkinLibrary.textureVersion
    LaunchedEffect(outfit?.equipped) {
        for ((slot, id) in outfit?.equipped.orEmpty()) {
            if (!isOutfitAssetLoaded(slot, id)) CosmeticAssetCache.ensureCosmeticLoaded(id)
        }
    }
    val source = remember(outfit, installs, textureVersion, mojangState, skins, skinVersion) {
        outfit?.let { outfitPreviewSource(it.equipped, outfitMojangCape(it), outfitSkin(it), PreviewTint(it.particleColor)) }
    }
    if (outfit == null || source == null) return null
    return SelectionPreview(source, BodySlot.Hat in outfit.equipped)
}

@Composable
internal fun rememberSkinPreview(skin: PlayerSkinInfo?): SelectionPreview? {
    val installs = CosmeticAssetCache.installs
    val textureVersion = MojangCapeService.textureVersion
    val mojangState = MojangCapeService.state
    val skinVersion = SkinLibrary.textureVersion
    val base = CosmeticCatalog.localEquipped().equipped
    val preview = remember(skin, skinVersion) { skin?.let(::previewSkin) }
    val source = remember(base, installs, textureVersion, mojangState, preview) {
        outfitPreviewSource(base, MojangCapeService.localCapeTexture(), preview)
    }
    if (skin == null) return null
    return SelectionPreview(source, BodySlot.Hat in base)
}

private fun outfitMojangCape(outfit: OutfitInfo): Identifier? {
    val capeId = outfit.mojangCapeId ?: return null
    return (MojangCapeService.state as? MojangCapeState.Loaded)?.capes?.firstOrNull { it.id == capeId }?.let(MojangCapeService::texture)
}

private fun outfitSkin(outfit: OutfitInfo): PreviewSkin? {
    val skinId = outfit.skinId ?: return null
    return SkinLibrary.skins.firstOrNull { it.id == skinId }?.let(::previewSkin)
}

private fun previewSkin(skin: PlayerSkinInfo): PreviewSkin? =
    SkinLibrary.texture(skin.hash, skin.url)?.let { PreviewSkin(it, skin.model == SkinModel.Slim) }

internal fun outfitPreviewSource(
    equipped: Map<BodySlot, Int>,
    mojangCape: Identifier?,
    skin: PreviewSkin? = null,
    tint: PreviewTint? = null,
): PlayerPreviewSource {
    val equipment = CosmeticEquipment()
    var pet: PetDefinition? = null
    for ((slot, id) in equipped) {
        if (slot == BodySlot.Cape) continue
        val attached = CosmeticAssetCache.getAttachedCosmetic(id)
        if (attached != null) {
            equipment.equip(attached)
        } else if (slot == BodySlot.Pet) {
            pet = CosmeticAssetCache.getPetDefinition(id)
        }
    }
    val capeId = equipped[BodySlot.Cape]
    return PlayerPreviewSource.Override(
        equipment,
        pet = pet,
        capeCosmeticId = capeId,
        mojangCape = if (capeId == null) mojangCape else null,
        skin = skin,
        tint = tint,
    )
}

@Composable
internal fun SkinThumbnail(skin: PlayerSkinInfo, modifier: Modifier) {
    val version = SkinLibrary.bitmapVersion
    val bitmap = remember(skin.hash, version) { SkinLibrary.bitmap(skin) }
    val slim = skin.model == SkinModel.Slim
    if (bitmap == null) {
        Box(modifier)
    } else {
        Canvas(modifier) { drawSkinFront(bitmap, slim) }
    }
}

private fun DrawScope.drawSkinFront(skin: ImageBitmap, slim: Boolean) {
    val legacy = skin.height <= 32
    val unit = floor(min(size.width / 16f, size.height / 32f) * SKIN_FIGURE_FILL).toInt().coerceAtLeast(1)
    val originX = ((size.width - 16 * unit) / 2f).roundToInt()
    val originY = ((size.height - 32 * unit) / 2f).roundToInt()

    fun part(u: Int, v: Int, w: Int, h: Int, x: Int, y: Int, mirror: Boolean = false) {
        val dstOffset = IntOffset(originX + x * unit, originY + y * unit)
        val dstSize = IntSize(w * unit, h * unit)
        if (mirror) {
            val pivot = Offset(dstOffset.x + dstSize.width / 2f, dstOffset.y + dstSize.height / 2f)
            scale(-1f, 1f, pivot) {
                drawImage(skin, IntOffset(u, v), IntSize(w, h), dstOffset, dstSize, filterQuality = FilterQuality.None)
            }
        } else {
            drawImage(skin, IntOffset(u, v), IntSize(w, h), dstOffset, dstSize, filterQuality = FilterQuality.None)
        }
    }

    val arm = if (slim) 3 else 4
    part(8, 8, 8, 8, 4, 0)
    part(20, 20, 8, 12, 4, 8)
    part(44, 20, arm, 12, 4 - arm, 8)
    if (legacy) part(44, 20, arm, 12, 12, 8, mirror = true) else part(36, 52, arm, 12, 12, 8)
    part(4, 20, 4, 12, 4, 20)
    if (legacy) part(4, 20, 4, 12, 8, 20, mirror = true) else part(20, 52, 4, 12, 8, 20)
    if (!legacy) {
        part(20, 36, 8, 12, 4, 8)
        part(44, 36, arm, 12, 4 - arm, 8)
        part(52, 52, arm, 12, 12, 8)
        part(4, 36, 4, 12, 4, 20)
        part(4, 52, 4, 12, 8, 20)
    }
    part(40, 8, 8, 8, 4, 0)
}

@Composable
internal fun OutfitSwitcher(onStatus: (String) -> Unit) {
    LaunchedEffect(Unit) { OutfitLibrary.refreshIfNeeded() }
    val state = OutfitLibrary.state
    val theme = OneConfigTheme.current
    val outfits = OutfitLibrary.outfits.favoritesFirst()
    val activeId = OutfitLibrary.activeOutfitId
    val busy = OutfitLibrary.busy
    val loaded = state == LibraryState.Loaded
    val active = outfits.firstOrNull { it.id == activeId }
    val canCycle = !busy && loaded && outfits.isNotEmpty() && (active == null || outfits.size > 1)
    val label = when {
        state is LibraryState.Unavailable -> state.reason
        active != null -> active.name
        !loaded -> "Loading outfits..."
        outfits.isEmpty() -> "No saved outfits"
        else -> "No outfit selected"
    }

    fun cycle(step: Int) {
        val index = outfits.indexOfFirst { it.id == activeId }
        val next = if (index < 0) {
            if (step > 0) outfits.first() else outfits.last()
        } else {
            outfits[(index + step).mod(outfits.size)]
        }
        wearOutfit(next, onStatus)
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WheelIconButton("left-arrow", enabled = canCycle) { cycle(-1) }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(ppShape(8.dp))
                .background(theme.chipBackground)
                .border(1.dp, theme.borderColor, ppShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            CardLabel(
                label,
                color = if (active != null) theme.textColor else theme.textColorSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        WheelIconButton("right-arrow", enabled = canCycle) { cycle(1) }
        WheelIconButton("plus", enabled = !busy && loaded, tooltip = "New outfit from current look") {
            createOutfitFromCurrent(onStatus)
        }
    }
}

private enum class SkinSource(val label: String, val icon: String) {
    Username("Username", "profiles"),
    Url("URL", "cloud"),
    File("File", "folder"),
}

@Composable
internal fun AddSkinPanel(onClose: () -> Unit, onStatus: (String) -> Unit, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val sources = SkinSource.entries
    var source by remember { mutableStateOf(SkinSource.Username) }
    var username by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<Path?>(null) }
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(SkinModel.Classic) }
    var picking by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val shown = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { shown.set(false) } }

    fun chooseFile() {
        if (picking) return
        picking = true
        PolyPlusClient.SCOPE.launch {
            val picked = withContext(Dispatchers.IO) { runCatching { pickSkinFile() }.getOrNull() }
            ClientPlatform.runOnMain {
                picking = false
                if (picked != null) {
                    file = picked
                    message = null
                }
            }
        }
    }

    fun submit() {
        if (working) return
        val chosen = source
        val player = username.trim()
        val link = url.trim()
        val path = file
        val label = name.trim().ifEmpty { null }
        val arms = model
        val problem = when (chosen) {
            SkinSource.Username -> if (player.isEmpty()) "Enter a Minecraft username." else null
            SkinSource.Url -> if (link.isEmpty()) "Enter the link to a skin image." else null
            SkinSource.File -> if (path == null) "Choose a PNG file first." else null
        }
        if (problem != null) {
            message = problem
            return
        }
        if (!beginSkinChange()) {
            message = "Another skin change is still running."
            return
        }
        working = true
        message = "Adding skin..."
        PolyPlusClient.SCOPE.launch {
            val result = try {
                when (chosen) {
                    SkinSource.Username -> SkinLibrary.addFromUsername(player)
                    SkinSource.Url -> SkinLibrary.addFromUrl(link, label, arms)
                    SkinSource.File -> {
                        val bytes = (path?.let { readSkinFile(it) } ?: Result.failure(OutfitException(UNREADABLE_SKIN_FILE)))
                        bytes.fold(
                            onSuccess = { SkinLibrary.addFromBytes(it, label, arms) },
                            onFailure = { Result.failure(it) },
                        )
                    }
                }
            } finally {
                endSkinChange()
            }
            ClientPlatform.runOnMain {
                working = false
                result.fold(
                    onSuccess = { skin ->
                        message = null
                        onStatus("Added ${skin.name} to your skins.")
                        if (shown.get()) onClose()
                    },
                    onFailure = { message = SkinLibrary.failureMessage(it) },
                )
            }
        }
    }

    Column(
        modifier = modifier.wardrobePanel().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialText("Add skin", color = theme.textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            WheelIconButton("close", enabled = true, tooltip = "Close", onClick = onClose)
        }
        SegmentedControl(
            options = sources.map { it.label to it.icon },
            selectedIndex = sources.indexOf(source),
            onSelect = { index ->
                source = sources[index]
                message = null
            },
        )
        when (source) {
            SkinSource.Username -> FieldGroup("Minecraft username") {
                WardrobeTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = MAX_USERNAME_LENGTH,
                    onSubmit = { submit() },
                )
                SocialText("Saves this player's current skin to your library without changing yours.", color = theme.textColorSecondary, fontSize = 12.sp)
            }
            SkinSource.Url -> FieldGroup("Image URL") {
                WardrobeTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://",
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = MAX_URL_LENGTH,
                    onSubmit = { submit() },
                )
                SocialText("Link to a 64×64 or 64×32 PNG skin. Adding it also puts it on.", color = theme.textColorSecondary, fontSize = 12.sp)
            }
            SkinSource.File -> FieldGroup("Skin file") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (picking) "Choosing..." else "Choose file", iconPath = "folder", primary = false, onClick = { chooseFile() })
                    CardLabel(
                        file?.fileName?.toString() ?: "No file chosen",
                        color = if (file != null) theme.textColor else theme.textColorSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                SocialText("A 64×64 or 64×32 PNG skin. Adding it also puts it on.", color = theme.textColorSecondary, fontSize = 12.sp)
            }
        }
        if (source != SkinSource.Username) {
            FieldGroup("Name") {
                WardrobeTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Optional",
                    modifier = Modifier.fillMaxWidth(),
                    maxLength = MAX_LIBRARY_NAME_LENGTH,
                )
            }
            FieldGroup("Arms") {
                SegmentedControl(
                    options = SkinModel.entries.map { it.label to null },
                    selectedIndex = SkinModel.entries.indexOf(model),
                    onSelect = { model = SkinModel.entries[it] },
                )
            }
        }
        message?.let { SocialText(it, color = theme.textColorSecondary, fontSize = 12.sp) }
        PrimaryWideButton(if (working) "Adding..." else "Add skin", iconPath = "plus", enabled = !working, onClick = { submit() })
    }
}

@Composable
private fun SegmentedControl(
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp)
            .clip(ppShape(8.dp))
            .background(theme.chipBackground)
            .border(1.dp, theme.borderColor, ppShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val color = if (selected) theme.accentTextColor else if (hovered) theme.textColor else theme.textColorSecondary
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clip(ppShape(6.dp))
                    .background(if (selected) OneConfigTheme.accent else Color.Transparent)
                    .hoverable(interaction)
                    .clickable { onSelect(index) },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Icon(icon, color = color, modifier = Modifier.size(12.dp))
                SocialText(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PrimaryWideButton(label: String, iconPath: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .clip(ppShape(8.dp))
            .background(OneConfigTheme.accent)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconPath, color = theme.accentTextColor, modifier = Modifier.size(14.dp))
        SocialText(label, color = theme.accentTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FieldGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SocialText(label, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        content()
    }
}

private suspend fun readSkinFile(path: Path): Result<ByteArray> =
    withContext(Dispatchers.IO) {
        val size = runCatching { Files.size(path) }
            .getOrElse { return@withContext Result.failure(OutfitException(UNREADABLE_SKIN_FILE)) }
        if (size > SkinLibrary.MAX_FILE_BYTES) return@withContext Result.failure(OutfitException(SkinLibrary.TOO_LARGE))
        runCatching { Files.readAllBytes(path) }.recoverCatching { throw OutfitException(UNREADABLE_SKIN_FILE) }
    }

private const val UNREADABLE_SKIN_FILE = "Couldn't read that file."

private fun pickSkinFile(): Path? =
    TinyFdApi.getInstance().openFileSelector("Choose a skin", null, arrayOf("*.png"), "PNG images")
