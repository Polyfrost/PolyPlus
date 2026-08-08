package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

fun playButtonClickSound() {
    val minecraft = Minecraft.getInstance()
    minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
}

fun Modifier.clickableWithSound(onClick: () -> Unit): Modifier = clickable {
    playButtonClickSound()
    onClick()
}

@Composable
fun Modifier.clickableTextWithSound(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
) {
    playButtonClickSound()
    onClick()
}
