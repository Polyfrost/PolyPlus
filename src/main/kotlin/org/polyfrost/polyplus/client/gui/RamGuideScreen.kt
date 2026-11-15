@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.features.JvmAdvisor

private val MEMORY_STEPS = listOf(
    "Open OneClient's settings and pick Minecraft settings, under GAME SETTINGS.",
    "Find the Memory row. The dropdown holds presets in GB, and the box beside it takes an exact amount in MB.",
    "To change it for one instance only, open that instance's own settings and use its Memory row instead.",
    "Relaunch the game. The new amount takes effect on the next launch.",
)

private val JVM_ARGUMENT_STEPS = listOf(
    "Open OneClient's settings and pick Minecraft settings, under GAME SETTINGS.",
    "Find the JVM Arguments row, just under Memory. It takes launch arguments separated by spaces.",
    "Swap the collector flag there and leave the rest of the arguments as they were.",
    "Relaunch the game. The change takes effect on the next launch.",
)

class RamGuideScreen(private val advice: JvmAdvisor.Advice) :
    OneConfigComposeScreen(previous = null, renderMode = OneConfigComposeScreen.RenderMode.Continuous) {
    @Composable
    override fun Content() {
        ProvidePopupSounds(false) { RamGuideContent(advice, onDismiss = { dismiss() }) }
    }
}

@Composable
private fun RamGuideContent(advice: JvmAdvisor.Advice, onDismiss: () -> Unit) {
    val steps = if (advice.kind == JvmAdvisor.Kind.SWITCH_TO_G1) JVM_ARGUMENT_STEPS else MEMORY_STEPS
    ModalDialog(onDismiss = onDismiss, width = 520.dp, dismissOnScrim = true, scrollBody = false) {
        val scroll = rememberScrollState()
        ScrollContainer(scroll, Modifier.weight(1f, fill = false), scrollbar = false) {
            Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogTitle("Allocating RAM in OneClient")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SocialFieldShape)
                        .background(SocialControlBackground)
                        .padding(12.dp),
                ) {
                    Text(advice.message, color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    steps.forEachIndexed { index, step -> GuideStep(index + 1, step) }
                }
                TextButton(text = "Got it", onClick = onDismiss, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Primary, size = ControlSize.Large)
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("$number.", modifier = Modifier.width(16.dp), style = OneConfigTheme.current.typography.label)
        Text(text, modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
    }
}
