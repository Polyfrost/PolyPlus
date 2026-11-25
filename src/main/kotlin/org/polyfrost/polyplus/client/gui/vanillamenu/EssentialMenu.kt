package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.utils.ClientPlatform

/**
 * Essential has no public API for its menus, so its screens are made by name (classes from Essential 1.5,
 * gg.essential.* isn't relocated). Essential's loader adds it at prelaunch, so FabricLoader may not list it;
 * the class itself is the check.
 */
object EssentialMenu {
    private val LOGGER = LogManager.getLogger("PolyPlus/EssentialMenu")

    enum class Feature(val label: String, val tooltip: String) {
        SOCIAL("Social", "Essential's friends and chats"),
        WARDROBE("Wardrobe", "Essential's cosmetics"),
        PICTURES("Pictures", "Essential's screenshot browser"),
        SETTINGS("Settings", "Essential's settings"),
    }

    val present: Boolean by lazy { runCatching { Class.forName("gg.essential.handlers.PauseMenuDisplay", false, javaClass.classLoader) }.isSuccess }

    /** True while the title screen is ours, so Essential's own title screen layer stays away. */
    @JvmStatic
    fun hidesTitleLayer(): Boolean {
        if (!PolyPlusMainMenuConfig.vanillaMenuButtons || !PolyPlusMainMenuConfig.hideEssentialTitleMenu) return false
        if (MainMenuReplacement.replacesTitleScreen()) return false
        return ClientPlatform.currentScreen() is TitleScreen
    }

    fun open(feature: Feature, parent: Screen?) {
        val opened = runCatching {
            when (feature) {
                Feature.SOCIAL -> openChecked("gg.essential.gui.friends.SocialMenu")
                Feature.WARDROBE -> openChecked("gg.essential.gui.wardrobe.Wardrobe")
                Feature.PICTURES -> openChecked("gg.essential.gui.screenshot.components.ScreenshotBrowser")
                Feature.SETTINGS -> {
                    val config = Class.forName("gg.essential.config.McEssentialConfig")
                    val instance = config.getField("INSTANCE").get(null)
                    val gui = config.methods.first { it.name == "gui" && it.parameterCount == 1 }.invoke(instance, null)
                    (gui as? Screen)?.let { ClientPlatform.setScreen(it); true } ?: false
                }
            }
        }.onFailure { LOGGER.warn("Couldn't open Essential's {}", feature, it) }.getOrDefault(false)
        if (!opened) {
            VanillaTitleMenu.toast("Essential", "Couldn't open Essential's ${feature.label} from here.")
            if (parent != null) ClientPlatform.setScreen(parent)
        }
    }

    /** Through Essential's own GuiUtil.openScreen, which checks its terms and features first, else straight away. */
    private fun openChecked(name: String): Boolean {
        val type = Class.forName(name)
        val make: () -> Screen = { type.getConstructor().newInstance() as Screen }
        val viaGuiUtil = runCatching {
            val util = Class.forName("gg.essential.util.GuiUtil")
            val instance = util.getField("INSTANCE").get(null)
            val method = util.methods.first { it.name == "openScreen" && it.parameterCount == 2 && it.parameterTypes[0] == Class::class.java }
            method.invoke(instance, type, make)
            true
        }.onFailure { LOGGER.debug("GuiUtil.openScreen not usable, opening {} directly", name, it) }.getOrDefault(false)
        if (viaGuiUtil) return true
        ClientPlatform.setScreen(make())
        return true
    }
}
//?}
