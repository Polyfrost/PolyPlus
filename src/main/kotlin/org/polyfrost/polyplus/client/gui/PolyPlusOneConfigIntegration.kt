@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.oneconfig.api.ui.v1.ChangelogSources
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.ModCardIcons
import org.polyfrost.oneconfig.api.ui.v1.ModSortModes
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPage
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.oneconfig.api.ui.v1.page.PageGroup
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.screens.ModConfig
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.features.DefaultModOrder
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsMemory
import org.polyfrost.polyplus.client.utils.ClientPlatform

object PolyPlusOneConfigIntegration {
    private const val MODS_PAGE = "oneconfig:mods"
    private const val BRAND_LOGO = "assets/polyplus/brand/oneclient.svg"
    private const val MOD_ICONS = "assets/polyplus/modicons/"
    private const val ONECLIENT_GROUP_ORDER = 150

    private val modIcons = setOf(
        "betterhurtcam", "betternightvision", "betterscreens", "blur", "confirmdisconnect", "countdown", "crosshairtweaks",
        "custom-block-highlight", "droppeditemtweaks", "fastquit", "fovchanger", "freelook", "gammautils", "itemphysiclite",
        "lambdynlights", "mountopacity", "numericalenchantments", "overlaytweaks", "polydium", "rendertweaks", "sciophobia",
        "scrolltweaks", "shaketweaks", "shieldstatus", "simplenickhider", "smoothskies", "tipper", "tooltipscroll",
        "waveycapes", "zoomify",
    )

    private val wardrobePage = object : OneConfigPage(
        "polyplus:wardrobe",
        PageText.translatable("polyplus.page.wardrobe", "Wardrobe"),
        "assets/polyplus/ico/stars.svg",
        PageGroup.PERSONALIZATION,
    ) {
        override val order: Int get() = 150

        @Composable
        override fun Content() = ProvidePopupSounds(false) { PolyPlusCosmeticsScreen() }
    }

    private val oneClientGroup = PageGroup.create(
        "polyplus:oneclient",
        PageText.translatable("polyplus.pagegroup.oneclient", "OneClient"),
        ONECLIENT_GROUP_ORDER,
    )

    private val sectionShown by lazy { mutableStateOf(PolyPlusConfig.oneConfigSection) }

    fun showSection(shown: Boolean) {
        sectionShown.value = shown
    }

    private class OneClientPageSpec(val slug: String, val file: String, val title: String, val icon: String)

    private class OneClientConfigPage(spec: OneClientPageSpec, override val order: Int, group: PageGroup) : OneConfigPage(
        "polyplus:oneclient/${spec.slug}",
        PageText.translatable("polyplus.page.oneclient.${spec.slug}", spec.title),
        spec.icon,
        group,
    ) {
        private val configId = "${PolyPlusConstants.ID}${spec.file}.json"

        override val showInSidebar: Boolean get() = sectionShown.value

        @Composable
        override fun Content() = ModConfig(configId)
    }

    private val oneClientPages: List<OneConfigPage> = listOf(
        OneClientPageSpec("general", "", "General", "settings"),
        OneClientPageSpec("cosmetics", "-cosmetics", "Cosmetics", "assets/polyplus/ico/stars.svg"),
        OneClientPageSpec("lobby", "-lobby", "Lobby", SOCIAL_ASSETS + "minecraft-block.svg"),
        OneClientPageSpec("mainmenu", "-mainmenu", "Main menu", "layers"),
        OneClientPageSpec("party", "-party", "Party", SOCIAL_ASSETS + "users-01.svg"),
        OneClientPageSpec("pause", "-pause", "Pause screen", SOCIAL_ASSETS + "pause-circle.svg"),
        OneClientPageSpec("screens", "-screens", "Screens", "cog"),
    ).mapIndexed { index, spec -> OneClientConfigPage(spec, index, oneClientGroup) }

    private val generalPage = oneClientPages.first()
    private val screensPage = oneClientPages.last()

    fun register() {
        if (OneConfigPages.byId(wardrobePage.id) == null) OneConfigPages.register(wardrobePage)
        oneClientPages.forEach { OneConfigPages.registerIfAbsent(it) }
        ModCardIcons.register { modId, defaultIcon -> modCardIcon(modId, defaultIcon) }
        ModSortModes.register(DefaultModOrder.recommendedMode)
        ChangelogSources.register(ModpackChangelog)
        OneConfigTheme.overrideLogo(BRAND_LOGO)
    }

    @JvmStatic
    fun openCosmetics() = OneConfigUI.open(wardrobePage)

    fun openCosmeticsFromKeybind() {
        val screen = ClientPlatform.currentScreen()
        if (screen is PolyPlusLobbyScreen) {
            screen.showPage(LobbyPage.Wardrobe)
        } else if (screen == null || screen is TitleScreen || MainMenuReplacement.isMenu(screen)) {
            openCosmetics()
        }
    }

    @JvmStatic
    fun openMods() = OneConfigUI.open(MODS_PAGE)

    @JvmStatic
    fun openSettings() = OneConfigUI.open()

    @JvmStatic
    fun openOneClientSettings() = openReturning { OneConfigUI.open(generalPage) }

    @JvmStatic
    fun openScreensSettings() = openReturning { OneConfigUI.open(screensPage) }

    fun openSettingsReturning() = openReturning { OneConfigUI.open() }

    private fun openReturning(open: () -> Unit) {
        val host = ClientPlatform.currentScreen()?.takeUnless { OneConfigUI.isOneConfigScreen(it) }
        SettingsMemory.openReturningTo(host, open)
    }

    private fun modCardIcon(modId: String, defaultIcon: String?): String? {
        val id = when (val base = modId.replaceFirst(Regex("(v\\d+)?\\.(json|toml)$"), "")) {
            "fullbright", "polyfullbright" -> "gammautils"
            "legacy_waveycapes" -> "waveycapes"
            "tntcountdown" -> "countdown"
            "itemphysicliteconfig.mixins", "oitemphysic" -> "itemphysiclite"
            "hypixelautotip" -> "tipper"
            else -> base
        }
        if (id.startsWith("polyplus")) return MOD_ICONS + "polyplus.svg"
        return if (id in modIcons) "$MOD_ICONS$id.svg" else defaultIcon
    }
}
