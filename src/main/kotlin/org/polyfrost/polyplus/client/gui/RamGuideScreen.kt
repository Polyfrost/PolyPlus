package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
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

class RamGuideScreen(private val advice: JvmAdvisor.Advice) : ComposeScreen(RenderMode.CONTINUOUS) {
    override fun shouldCloseOnEsc(): Boolean = true

    override fun onClose() {
        Minecraft.getInstance().execute { super.onClose() }
    }

    @Composable
    override fun compose() {
        Theme { RamGuideContent(advice, onDismiss = { onClose() }) }
    }
}

@Composable
private fun RamGuideContent(advice: JvmAdvisor.Advice, onDismiss: () -> Unit) {
    val steps = if (advice.kind == JvmAdvisor.Kind.SWITCH_TO_G1) JVM_ARGUMENT_STEPS else MEMORY_STEPS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialScrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(SocialPanelShape)
                .background(SocialPopupBackground)
                .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SocialText("Allocating RAM in OneClient", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SocialFieldShape)
                    .background(SocialControlBackground)
                    .padding(12.dp),
            ) {
                SocialText(advice.message, fontSize = 13.sp, color = SocialTextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { index, step -> GuideStep(index + 1, step) }
            }
            SocialButton("Got it", modifier = Modifier.fillMaxWidth(), filled = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        SocialText("$number.", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(16.dp))
        SocialText(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
