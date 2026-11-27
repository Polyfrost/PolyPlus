@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.joml.Vector3f
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaves
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberOnlineConsent
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewLights
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_VIEW_TYPES
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticsLoadingScreen
import org.polyfrost.polyplus.client.gui.wardrobe.TryOnBar
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSelection
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeStoreData
import org.polyfrost.polyplus.client.gui.wardrobe.addEmoteToWheel
import org.polyfrost.polyplus.client.gui.wardrobe.coloursSaving
import org.polyfrost.polyplus.client.gui.wardrobe.equip
import org.polyfrost.polyplus.client.gui.wardrobe.rememberCosmeticItems
import org.polyfrost.polyplus.client.gui.wardrobe.rememberOutfitPreview
import org.polyfrost.polyplus.client.gui.wardrobe.rememberPreviewBonuses
import org.polyfrost.polyplus.client.gui.wardrobe.rememberSelectionPreview
import org.polyfrost.polyplus.client.gui.wardrobe.rememberSkinPreview
import org.polyfrost.polyplus.client.gui.wardrobe.rememberTryInfo
import org.polyfrost.polyplus.client.gui.wardrobe.rememberTryOn
import org.polyfrost.polyplus.client.gui.wardrobe.selectAuraColor
import org.polyfrost.polyplus.client.gui.wardrobe.selectedVariantId
import org.polyfrost.polyplus.client.gui.wardrobe.toggleMojangCape
import org.polyfrost.polyplus.client.gui.wardrobe.unequip
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbyWardrobe
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupKey
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val FACING_CAMERA = 180f
private const val FRONT_YAW = FACING_CAMERA - 16f
private const val BACK_YAW = -15f
private const val DRAG_DEGREES = 0.5f
private const val STATUS_VISIBLE_MS = 4_000L
private const val TRY_BAR_REACH_MS = 600L
private const val FALLBACK_MODEL_SCALE = 0.36f
private const val FALLBACK_ANCHOR = 0.53f
private const val FALLBACK_FEET = 0.86f
private const val FALLBACK_TILT = 8f
private const val FALLBACK_TEXTURE_LIMIT = 2048
private const val SHADOW_WIDTH = 0.34f
private const val SHADOW_HEIGHT = 0.05f

private val SideScrim = Color(0xFF0B1013)
private val FrameTop = 76.dp
private val FrameStart = LobbyLayout.Margin + RailWidth + LobbyLayout.Gap
private val FrameEnd = LobbyLayout.Margin + CustomizeKit.PanelWidth + LobbyLayout.Gap
private val FrameBottom = LobbyLayout.PageBottom
private val DrawerBottom = LobbyLayout.PageBottom
private val OutfitRowHeight = 46.dp
private val LoadingHeight = 220.dp
private val TryBarWidth = 360.dp

private fun wardrobeFrame(width: Float, height: Float) = LobbyWardrobe.Frame(
    FrameStart.value / width,
    FrameTop.value / height,
    1f - FrameEnd.value / width,
    1f - FrameBottom.value / height,
)

private fun sideScrim() = Brush.horizontalGradient(
    0f to SideScrim.copy(alpha = 0.62f),
    0.3f to Color.Transparent,
    0.7f to Color.Transparent,
    1f to SideScrim.copy(alpha = 0.62f),
)

private fun capeNote(state: MojangCapeState): String? = when (state) {
    MojangCapeState.Idle, MojangCapeState.Loading -> "Loading your Minecraft capes..."
    is MojangCapeState.Unavailable -> state.reason
    is MojangCapeState.Loaded -> "You don't own any Minecraft capes.".takeIf { state.capes.isEmpty() }
}

@Composable
internal fun WardrobePage(onBack: () -> Unit, onMarketplace: () -> Unit) {
    var consent by rememberOnlineConsent()
    WardrobeContent(consent, onBack, onMarketplace) { consent = true }
}

@Composable
private fun WardrobeContent(online: Boolean, onBack: () -> Unit, onMarketplace: () -> Unit, onAccepted: () -> Unit) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var loadState by remember { mutableStateOf(CosmeticLoadProgress.snapshot()) }
    val drawer = remember { DrawerState() }
    var selected by remember { mutableStateOf<Pair<CosmeticType, CosmeticGroupKey>?>(null) }
    var selectedCape by remember { mutableStateOf<MojangCape?>(null) }
    var hovered by remember { mutableStateOf<PickerEntry?>(null) }
    var hoverLeft by remember { mutableStateOf(false) }
    val tryBarHover = remember { MutableInteractionSource() }
    val overTryBar by tryBarHover.collectIsHoveredAsState()
    var selection by remember { mutableStateOf<WardrobeSelection?>(null) }
    var wheelSlot by remember { mutableStateOf<Int?>(null) }
    var historyKey by remember { mutableIntStateOf(0) }
    var consentCard by remember { mutableStateOf(true) }
    var auraColor by remember { mutableStateOf(CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())) }
    var liveScene by remember { mutableStateOf(LobbyScene.showing()) }
    val store = remember { WardrobeStoreData() }
    val variantPicks = remember { mutableStateMapOf<CosmeticGroupKey, Int>() }
    val rotation = remember { PreviewRotation(FRONT_YAW) }

    LaunchedEffect(online) {
        if (online) PolyPlusClient.refreshCosmeticsIfNeeded() else drawer.open(DrawerPage.Slot(CosmeticType.Cape))
        MojangCapeService.refreshIfNeeded()
        while (true) {
            loadState = CosmeticLoadProgress.snapshot()
            liveScene = LobbyScene.showing()
            refreshKey++
            delay(if (loadState.ready || !online) 1_500L else 200L)
        }
    }
    LaunchedEffect(refreshKey) { auraColor = CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid()) }
    LaunchedEffect(hoverLeft, overTryBar) {
        if (!hoverLeft || overTryBar) return@LaunchedEffect
        delay(TRY_BAR_REACH_MS)
        hovered = null
        hoverLeft = false
    }
    val view = drawer.view
    val pickerType = drawer.slotType ?: CosmeticType.Emote.takeIf { view == WardrobeView.Wheel }
    LaunchedEffect(view) {
        when (view) {
            WardrobeView.Outfits -> OutfitLibrary.refreshIfNeeded(reload = true)
            WardrobeView.Skins -> {
                SkinLibrary.refreshIfNeeded()
                OutfitLibrary.refreshIfNeeded()
            }
            WardrobeView.Wheel -> if (store.byType.isEmpty()) store.load()
            else -> Unit
        }
    }

    val items = rememberCosmeticItems(refreshKey).takeIf { online }.orEmpty()
    val ownedIds = remember(refreshKey) { CosmeticCatalog.ownedIds() }
    val mojangState = MojangCapeService.state
    val mojangCapes = (mojangState as? MojangCapeState.Loaded)?.capes.orEmpty()
    val selectedItem = selected?.let { (type, key) -> items.firstOrNull { it.type == type && it.key == key } }
    val triedItem = hovered?.item ?: selectedItem.takeIf { hovered == null }
    val itemPreview = rememberSelectionPreview(
        owned = triedItem,
        variantId = triedItem?.let { selectedVariantId(it, variantPicks) },
        mojang = hovered?.cape ?: selectedCape.takeIf { hovered == null },
    )
    val selectedOutfit = (selection as? WardrobeSelection.Outfit)?.let { pick -> OutfitLibrary.outfits.firstOrNull { it.id == pick.outfitId } }
    val selectedSkin = (selection as? WardrobeSelection.Skin)?.let { pick -> SkinLibrary.skins.firstOrNull { it.id == pick.skinId } }
    val outfitPreview = rememberOutfitPreview(selectedOutfit)
    val skinPreview = rememberSkinPreview(selectedSkin)
    val selectionPreview = outfitPreview ?: skinPreview ?: itemPreview
    val previewOutfit = selectionPreview?.source as? PlayerPreviewSource.Override

    SideEffect { LobbyWardrobe.preview = previewOutfit }
    DisposableEffect(Unit) { onDispose { LobbyWardrobe.preview = null } }

    val shownItem = triedItem.takeIf { outfitPreview == null && skinPreview == null }
    val tryVariant = shownItem?.let { selectedVariantId(it, variantPicks) }
    val tryInfo = rememberTryInfo(shownItem?.let { it.variant(tryVariant)?.pieces ?: listOfNotNull(tryVariant) })
    val tryOn = rememberTryOn(shownItem?.let { it.type to it.key } to tryVariant)
    val bonuses = rememberPreviewBonuses(selectionPreview?.ids ?: CosmeticCatalog.localEquipped().equipped.values)
    LaunchedEffect(tryOn, bonuses) { tryOn.bonuses = bonuses }
    DisposableEffect(tryOn, liveScene) {
        if (liveScene) PreviewTryOn.lobby = tryOn
        onDispose { if (PreviewTryOn.lobby === tryOn) PreviewTryOn.lobby = null }
    }
    val shownTryInfo = if (liveScene) tryInfo.withoutEffects() else tryInfo

    fun completed(message: String) {
        refreshKey++
        status = message
    }

    fun toggle(item: CosmeticUiItem) {
        selected = item.type to item.key
        val variantId = selectedVariantId(item, variantPicks)
        when {
            item.equipped && variantId == item.equippedVariantId -> {
                selected = null
                status = "Unequipping ${item.name}..."
                unequip(item, variantId, ::completed)
            }
            variantId == null -> status = "${item.name} has no variant to equip."
            else -> {
                status = "Equipping ${item.name}..."
                equip(item, variantId, ::completed)
            }
        }
    }

    fun pickVariant(item: CosmeticUiItem, variantId: Int) {
        if (coloursSaving(item)) {
            status = ColourSaves.STILL_SAVING
            return
        }
        variantPicks[item.key] = variantId
        selected = item.type to item.key
    }

    fun clearPicks() {
        selected = null
        selectedCape = null
        hovered = null
        hoverLeft = false
        selection = null
        wheelSlot = null
    }

    LaunchedEffect(drawer.page) { clearPicks() }

    fun openSlot(type: CosmeticType) = drawer.open(DrawerPage.Slot(type))

    fun retryLoad() {
        PolyPlusClient.refreshCosmetics()
        loadState = CosmeticLoadProgress.snapshot()
    }

    fun refresh() {
        retryLoad()
        PolyPlusClient.SCOPE.launch { MojangCapeService.refresh() }
        if (online && view == WardrobeView.Outfits) PolyPlusClient.SCOPE.launch { OutfitLibrary.refresh() }
        if (online && view == WardrobeView.Skins) {
            PolyPlusClient.SCOPE.launch {
                SkinLibrary.refresh()
                OutfitLibrary.refresh()
            }
        }
        refreshKey++
        historyKey++
        status = if (online) "Refreshing cosmetic data..." else "Refreshing your Minecraft capes..."
    }

    fun pickerModel(type: CosmeticType): PickerModel {
        val wheelPicking = view == WardrobeView.Wheel && type == CosmeticType.Emote
        val capes = if (type == CosmeticType.Cape) mojangCapes else emptyList()
        val entries = capes.map { PickerEntry("cape-${it.id}", it.name, it.active, null, it) } +
            items.filter { it.type == type && it.owned }.map { PickerEntry("item-${it.key.tag}", it.name, it.equipped, it, null) }
        val chosen = (entries.firstOrNull { it.item != null && it.item.key == selectedItem?.key && it.item.type == selectedItem.type }
            ?: entries.firstOrNull { it.cape?.id == selectedCape?.id && it.cape != null }).takeUnless { wheelPicking }
        val target = (chosen ?: entries.firstOrNull { it.equipped }).takeUnless { wheelPicking }
        val targetItem = target?.item
        return PickerModel(
            type = type,
            entries = entries,
            selected = chosen,
            target = target,
            unequips = target?.equipped == true && (targetItem == null || selectedVariantId(targetItem, variantPicks) == targetItem.equippedVariantId),
            variantOf = { selectedVariantId(it, variantPicks) },
            onHover = { entry, over ->
                if (over) {
                    hovered = entry
                    hoverLeft = false
                } else if (hovered?.key == entry.key) {
                    hoverLeft = true
                }
            },
            onPick = { entry ->
                val item = entry.item
                if (wheelPicking && item != null) {
                    addEmoteToWheel(item, wheelSlot) { status = it }
                    wheelSlot = null
                } else {
                    selected = item?.let { it.type to it.key }
                    selectedCape = entry.cape
                }
            },
            onPickVariant = ::pickVariant,
            onApply = {
                targetItem?.let(::toggle)
                target?.cape?.let { cape ->
                    selectedCape = null
                    toggleMojangCape(cape) { status = it }
                }
            },
            onClose = drawer::back,
            onMarketplace = onMarketplace.takeIf { online },
            aura = AuraChoice(
                auraColor,
                onPreview = { color ->
                    auraColor = color
                    CosmeticCatalog.previewParticleColor(ClientPlatform.localPlayerUuid(), color)
                },
                onCommit = { color ->
                    if (selectAuraColor(color) { status = it }) auraColor = color
                },
            ).takeIf { type == CosmeticType.Aura && entries.isNotEmpty() },
            forceToggle = online && type == CosmeticType.Cape,
            note = capeNote(mojangState).takeIf { type == CosmeticType.Cape },
            status = status,
            picksToWheel = wheelPicking,
            onStatus = { status = it },
        )
    }

    val facesBack = pickerType in BACK_VIEW_TYPES && pickerType != CosmeticType.Wings
    LaunchedEffect(rotation) {
        snapshotFlow { rotation.yaw }.collect { LobbyWardrobe.turn = it - FACING_CAMERA }
    }
    LaunchedEffect(facesBack) {
        val target = if (facesBack) BACK_YAW else FRONT_YAW
        LobbyWardrobe.restTurn = target - FACING_CAMERA
        if (rotation.userRotated) return@LaunchedEffect
        val start = rotation.yaw
        val delta = ((target - start) % 360f + 540f) % 360f - 180f
        animate(0f, 1f, animationSpec = Motion.spec(tween(300))) { progress, _ ->
            if (!rotation.userRotated) rotation.yaw = start + delta * progress
        }
    }

    LaunchedEffect(status) {
        if (status != null) {
            delay(STATUS_VISIBLE_MS)
            status = null
        }
    }

    val groups = remember(items, mojangCapes, online) { railGroups(items, mojangCapes, online) }

    fun wearing(type: CosmeticType): SlotWearing {
        val slot = groups.asSequence().flatMap { it.slots }.firstOrNull { it.type == type }
        val item = slot?.item
        val cape = slot?.cape
        val action: (() -> Unit)? = when {
            item != null -> {
                {
                    selected = null
                    status = "Unequipping ${item.name}..."
                    unequip(item, item.equippedVariantId, ::completed)
                }
            }
            cape != null -> {
                {
                    selectedCape = null
                    toggleMojangCape(cape) { status = it }
                }
            }
            else -> null
        }
        return SlotWearing(slot?.name, action)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        LaunchedEffect(width, height) {
            LobbyWardrobe.frame = wardrobeFrame(width, height)
        }
        Box(Modifier.fillMaxSize().background(sideScrim()))
        val freeArea = Modifier
            .padding(start = FrameStart, end = FrameEnd, top = FrameTop, bottom = FrameBottom)
            .fillMaxSize()
        if (liveScene) {
            Box(
                freeArea.pointerInput(rotation) {
                    detectDragGestures { change, drag ->
                        rotation.yaw -= drag.x * DRAG_DEGREES
                        rotation.userRotated = true
                        change.consume()
                    }
                },
            )
        } else {
            Canvas(freeArea) {
                val center = Offset(size.width / 2f, size.height * FALLBACK_FEET)
                val radius = size.width * SHADOW_WIDTH / 2f
                drawOval(
                    Brush.radialGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, center = center, radius = radius),
                    Offset(center.x - radius, center.y - size.height * SHADOW_HEIGHT),
                    Size(radius * 2f, size.height * SHADOW_HEIGHT * 2f),
                )
            }
            PlayerPreview(
                freeArea,
                source = selectionPreview?.source ?: PlayerPreviewSource.LocalLive,
                autoSpin = false,
                modelScale = FALLBACK_MODEL_SCALE,
                verticalAnchor = FALLBACK_ANCHOR,
                initialYaw = FRONT_YAW,
                live = true,
                rotation = rotation,
                fadeEdges = false,
                maxTextureSize = FALLBACK_TEXTURE_LIMIT,
                cameraTiltDeg = FALLBACK_TILT,
                lights = FALLBACK_LIGHTS,
                tryOn = tryOn,
            )
        }
        EquippedRail(
            groups = groups,
            title = when {
                !online -> "Capes"
                loadState.ready -> "Equipped"
                else -> loadState.failure ?: loadState.label
            },
            activeType = drawer.slotType,
            onBack = onBack,
            onRefresh = ::refresh,
            onSelect = ::openSlot,
            modifier = Modifier
                .padding(start = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = FrameBottom)
                .width(RailWidth)
                .fillMaxHeight(),
        )
        WardrobeDrawer(
            drawer,
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = DrawerBottom)
                .width(CustomizeKit.PanelWidth)
                .fillMaxHeight(),
        ) { page ->
            when (page) {
                DrawerPage.Home -> DrawerHomePage(
                    drawer,
                    top = {
                        when {
                            online -> OutfitPanel(onStatus = { status = it }, modifier = Modifier.fillMaxWidth().height(OutfitRowHeight))
                            consentCard -> ConsentStep(
                                "Cosmetics and outfits need online services.",
                                Modifier.fillMaxWidth(),
                                { consentCard = false },
                                onAccepted,
                            )
                            else -> CapesOnlyPanel({ consentCard = true }, Modifier.fillMaxWidth())
                        }
                    },
                    bottom = when {
                        !online -> null
                        loadState.ready -> {
                            { SeasonalBundlePanel(items, onMarketplace, Modifier.fillMaxWidth()) }
                        }
                        else -> {
                            { Box(Modifier.fillMaxWidth().height(LoadingHeight)) { CosmeticsLoadingScreen(loadState, ::retryLoad) } }
                        }
                    },
                    libraries = online,
                )
                is DrawerPage.Slot -> DrawerSlotPage(pickerModel(page.type), wearing(page.type))
                is DrawerPage.Library -> LibraryPage(
                    view = page.view,
                    selection = selection,
                    wheelSlot = wheelSlot,
                    wheelPicker = pickerModel(CosmeticType.Emote).takeIf { page.view == WardrobeView.Wheel },
                    store = store,
                    ownedIds = ownedIds,
                    refreshKey = refreshKey,
                    historyKey = historyKey,
                    actions = DrawerActions(
                        onSelect = { selection = it },
                        onSelectSlot = { wheelSlot = it },
                        onStatus = { status = it },
                    ),
                )
            }
        }
        val shownStatus = status?.takeIf { pickerType == null }
        if (shownStatus != null || shownTryInfo.actions.isNotEmpty()) {
            Box(
                Modifier
                    .padding(start = FrameStart, end = FrameEnd, bottom = FrameBottom + LobbyLayout.Gap)
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LobbyLayout.Gap)) {
                    TryOnBar(shownTryInfo, tryOn, Modifier.widthIn(max = TryBarWidth).hoverable(tryBarHover))
                    shownStatus?.let { Badge(it, overImage = true) }
                }
            }
        }
    }
}

private val FALLBACK_LIGHTS = PreviewLights(
    key = Vector3f(0.45f, -0.85f, 1.0f).normalize(),
    fill = Vector3f(-0.8f, -0.2f, 0.2f).normalize(),
)
