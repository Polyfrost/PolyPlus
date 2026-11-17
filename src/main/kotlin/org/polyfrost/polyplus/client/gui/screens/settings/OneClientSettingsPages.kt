@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.screens.ConfigScreen
import org.polyfrost.oneconfig.internal.ui.shell.LocalShell
import org.polyfrost.oneconfig.internal.ui.shell.ShellController
import java.util.concurrent.Executors

private val OneClientGutter = 25.dp
private val OneClientSearchWidth = 200.dp
private const val ONECLIENT_ENTRY_PREFIX = "oneclient:config:"

internal object OneClientSettingsPages : SettingsEntryProvider {
    private val logger = LogManager.getLogger("PolyPlus/OneClientSettings")
    private val shell = ShellController()
    private val saver = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PolyPlus-OneClientSettingsSave").apply { isDaemon = true }
    }

    fun register() = SettingsPages.register(this)

    override fun entries(): List<SettingsEntry> {
        var previous = "oneclient:mods"
        return OneClientConfigs.entries().map { entry ->
            val placement = SettingsPlacement.After(previous)
            previous = entry.id
            SettingsEntry(entry.id, entry.label, entry.icon, SettingsOwner.OneClient, SettingsGroup.OneClient, placement)
        }
    }

    @Composable
    override fun Page(entry: SettingsEntry, parent: () -> Screen?) {
        val id = entry.id.removePrefix(ONECLIENT_ENTRY_PREFIX)
        val revision = ConfigRegistry.revision
        val tree = remember(id, revision) { tree(id) }
        DisposableEffect(id) {
            shell.search.clear()
            onDispose {
                shell.search.clear()
                save(id)
            }
        }
        CompositionLocalProvider(LocalShell provides shell, LocalSettingsOwner provides SettingsOwner.OneClient) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OneClientHeader(entry.label, searchable = tree != null)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (tree == null) {
                        OneClientMissing(id)
                    } else {
                        key(revision, tree) { ConfigScreen(tree, pageKey = "polyplus-settings/$id") }
                    }
                }
            }
        }
    }

    @Composable
    private fun OneClientHeader(title: String, searchable: Boolean) {
        SettingsPageHeader(
            title,
            "Changes apply as you make them.",
            Modifier.padding(horizontal = OneClientGutter),
            owner = SettingsOwner.OneClient,
        ) {
            if (searchable) {
                SearchField(
                    shell.search.query,
                    { shell.search.update(it) },
                    Modifier.width(OneClientSearchWidth),
                    placeholder = "Search",
                    size = ControlSize.Regular,
                )
            }
        }
    }

    @Composable
    private fun OneClientMissing(id: String) {
        EmptyState(
            "These settings are not loaded",
            Modifier.fillMaxSize(),
            icon = "settings",
            text = "They may still be loading, or open in OneConfig instead.",
            action = {
                TextButton(
                    "Open in OneConfig",
                    { OneConfigUI.open(OneConfigDestination.ModConfig(OneConfigPages.modConfig(id))) },
                    style = ControlStyle.Primary,
                    size = ControlSize.Large,
                )
            },
        )
    }

    private fun tree(id: String): Tree? =
        runCatching { ConfigRegistry.findTree(id) }.getOrNull()
            ?: runCatching { ConfigManager.active().trees().firstOrNull { it.id == id } }.getOrNull()

    private fun save(id: String) {
        saver.execute {
            runCatching {
                val manager = ConfigManager.active()
                val tree = manager.trees().firstOrNull { it.id == id }
                if (tree != null) manager.save(tree) else manager.save(id)
            }.onFailure { logger.error("Failed to save {}", id, it) }
        }
    }
}
