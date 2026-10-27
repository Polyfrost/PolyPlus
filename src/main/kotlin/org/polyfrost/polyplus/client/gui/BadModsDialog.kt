@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.features.BadMods
import org.polyfrost.polyplus.client.utils.ClientPlatform

class BadModsScreen(private val parent: Screen?) : ComposeScreen(RenderMode.CONTINUOUS) {
    //? if > 1.8.9 {
    override fun shouldCloseOnEsc(): Boolean = true

    override fun onClose() {
        Minecraft.getInstance().execute { ClientPlatform.setScreen(parent) }
    }
    //?} else {
    /*private fun onClose() {
        Minecraft.getInstance().tell { ClientPlatform.setScreen(parent) }
    }
    *///?}

    @Composable
    override fun compose() {
        ProvideOneConfigTheme {
            val dismiss = { BadMods.dismiss(); onClose() }
            SocialScrimBox(dismiss) {
                BadModsContent(BadMods.warnings, onDismiss = dismiss, onDisable = { BadMods.ignoreShown(); onClose() })
            }
        }
    }
}

@Composable
internal fun BadModsDialog() {
    val warnings = BadMods.warnings
    if (warnings.isEmpty()) return
    SocialModalScrim(onDismiss = BadMods::dismiss) {
        BadModsContent(warnings, onDismiss = BadMods::dismiss, onDisable = BadMods::ignoreShown)
    }
}

@Composable
private fun BadModsContent(warnings: List<BadMods.Warning>, onDismiss: () -> Unit, onDisable: () -> Unit) {
    ModalPanel(width = 480.dp, padding = PaddingValues(24.dp), spacing = 16.dp) {
        SocialText(
            if (warnings.size == 1) "Problematic mod detected" else "${warnings.size} problematic mods detected",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SocialText(
            "These mods are known to cause crashes or conflicts with OneClient. We recommend removing them.",
            fontSize = 13.sp,
            color = SocialTextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            warnings.forEach { BadModCard(it) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Don't show again", modifier = Modifier.weight(1f), onClick = onDisable)
            SocialButton("OK", modifier = Modifier.weight(1f), filled = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun BadModCard(warning: BadMods.Warning) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SocialText(warning.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        SocialText(warning.message, fontSize = 12.sp, color = SocialTextSecondary)
        warning.alternative?.let { (title, slug) ->
            SocialButton(
                "Get $title",
                modifier = Modifier.fillMaxWidth(),
                onClick = { ClientPlatform.openUri("https://modrinth.com/mod/$slug") },
            )
        }
    }
}
