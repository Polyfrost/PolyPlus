@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.ModCardIcons
import org.polyfrost.oneconfig.api.ui.v1.ModsPageDecorator
import org.polyfrost.oneconfig.api.ui.v1.ModsPageDecorators
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPage
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.oneconfig.api.ui.v1.page.PageGroup
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.utils.ClientPlatform

object PolyPlusOneConfigIntegration {
    private const val MODS_PAGE = "oneconfig:mods"
    private const val BRAND_LOGO = "assets/polyplus/brand/oneclient.svg"
    private const val MOD_ICONS = "assets/polyplus/modicons/"

    private val modIcons = setOf(
        "betterhurtcam", "betternightvision", "betterscreens", "blur", "confirmdisconnect", "crosshairtweaks",
        "custom-block-highlight", "droppeditemtweaks", "fastquit", "fovchanger", "freelook", "gammautils", "mountopacity",
        "numericalenchantments", "overlaytweaks", "rendertweaks", "sciophobia", "scrolltweaks", "shaketweaks",
        "simplenickhider", "smoothskies", "tooltipscroll", "waveycapes", "zoomify",
    )

    private val wardrobePage = object : OneConfigPage(
        "polyplus:wardrobe",
        PageText.translatable("polyplus.page.wardrobe", "Wardrobe"),
        "assets/polyplus/ico/stars.svg",
        PageGroup.PERSONALIZATION,
    ) {
        override val order: Int get() = 150

        @Composable
        override fun Content() = PolyPlusCosmeticsScreen()
    }

    private val modOrderToggle = object : ModsPageDecorator() {
        @Composable
        override fun Decorate(content: @Composable () -> Unit) = ModOrderToggle.Overlay(content)
    }

    fun register() {
        if (OneConfigPages.byId(wardrobePage.id) == null) OneConfigPages.register(wardrobePage)
        ModsPageDecorators.register(modOrderToggle)
        ModCardIcons.register { modId, defaultIcon -> modCardIcon(modId, defaultIcon) }
        OneConfigTheme.overrideLogo(BRAND_LOGO)
    }

    @JvmStatic
    fun openCosmetics() = OneConfigUI.open(wardrobePage)

    fun openCosmeticsFromKeybind() {
        when (ClientPlatform.currentScreen()) {
            null, is TitleScreen, is PolyPlusMainMenuScreen -> openCosmetics()
            else -> Unit
        }
    }

    @JvmStatic
    fun openMods() = OneConfigUI.open(MODS_PAGE)

    private fun modCardIcon(modId: String, defaultIcon: String?): String? {
        val id = when (val base = modId.replaceFirst(Regex("(v\\d+)?\\.(json|toml)$"), "")) {
            "fullbright", "polyfullbright" -> "gammautils"
            "legacy_waveycapes" -> "waveycapes"
            "legacy_skin_layers", "legacy-skin-layers", "skinlayers3d" -> return null
            else -> base
        }
        if (id.startsWith("polyplus")) return MOD_ICONS + "polyplus.svg"
        return if (id in modIcons) "$MOD_ICONS$id.svg" else defaultIcon
    }
}
