@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.compat

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.SettingRowDecorator
import org.polyfrost.oneconfig.api.ui.v1.SettingRowDecorators
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Tooltip
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

//? if wwaypoints {
import com.mojang.blaze3d.platform.InputConstants
import com.wwaypoints.WaypointsClient
import com.wwaypoints.client.ModConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
//?}

object WWaypointsCompat {
    const val DISABLED_REASON = "Disabled by PolyPlus"

    const val DISABLED_REASON_METADATA = "disabledReason"

    // mirrors what wWaypoints greys out in its own screens once sneak and sign modifications are disallowed
    private val DISABLED_PROPERTIES = listOf(
        "wwaypoints.toggleSneakStaySneakedInContainers",
        "wwaypoints.toggleSneakBetterInteractions",
        "wwaypoints.sneakKeybindOverrides",
        "wwaypoints.toggleSneakIndicatorMode",
        "wwaypoints.signGuiPopup",
        "wwaypoints.key.toggle_sneak",
        "wwaypoints.key.sign_gui_popup_toggle",
    )

    private val logger = LogManager.getLogger("PolyPlus/WWaypointsCompat")

    private val disabledReasonTooltip = object : SettingRowDecorator() {
        @Composable
        override fun Decorate(property: Property<*>, content: @Composable () -> Unit) {
            val reason = disabledReason(property) ?: return content()
            DisabledReasonTooltip(reason, content)
        }
    }

    //? if wwaypoints {
    // written to disk as well so the features stay off if Poly+ is removed later
    fun initialize() {
        if (!FabricLoader.getInstance().isModLoaded("wwaypoints")) return
        SettingRowDecorators.register(disabledReasonTooltip)
        ClientLifecycleEvents.CLIENT_STARTED.register { client ->
            if (disableFeatures(WaypointsClient.getConfig())) WaypointsClient.saveConfig()
            val boundKeys = listOf(WaypointsClient.getToggleSneakKey(), WaypointsClient.getSignGuiPopupToggleKey())
                .filterNot { it.isUnbound }
            if (boundKeys.isEmpty()) return@register
            boundKeys.forEach { it.setKey(InputConstants.UNKNOWN) }
            KeyMapping.resetMapping()
            client.options.save()
        }
    }

    // returns whether anything had to be changed
    @JvmStatic
    fun disableFeatures(config: ModConfig): Boolean {
        val changed = config.toggleSneakStaySneakedInContainers != false ||
            config.toggleSneakBetterInteractions != false ||
            config.sneakKeybindOverrides ||
            !config.signGuiPopup
        config.toggleSneakStaySneakedInContainers = false
        config.toggleSneakBetterInteractions = false
        config.sneakKeybindOverrides = false
        config.signGuiPopup = true
        return changed
    }
    //?}

    @JvmStatic
    fun lockDisabledOptions(tree: Tree) {
        for (id in DISABLED_PROPERTIES) {
            val prop = tree.getProp(id)
            if (prop == null) {
                logger.warn("OneConfig's wWaypoints tree has no {} property, it will not be locked", id)
                continue
            }
            prop.addMetadata(DISABLED_REASON_METADATA, DISABLED_REASON)
            prop.addDisplayCondition { Property.Display.DISABLED }
        }
    }

    @JvmStatic
    fun disabledReason(prop: Property<*>): String? =
        if (prop.display == Property.Display.DISABLED) prop.getMetadata<String>(DISABLED_REASON_METADATA) else null

    @Composable
    @JvmStatic
    fun DisabledReasonTooltip(reason: String, content: @Composable () -> Unit) {
        Tooltip(
            text = { Text(reason, modifier = Modifier, color = OneConfigTheme.current.textColor, fontSize = 12.sp) },
            modifier = Modifier.widthIn(max = 260.dp),
            anchor = Alignment.TopCenter,
            content = content,
        )
    }
}
