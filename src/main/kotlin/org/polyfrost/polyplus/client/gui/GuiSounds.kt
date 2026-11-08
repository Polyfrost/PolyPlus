@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.a11y

fun playButtonClickSound() {
    val minecraft = Minecraft.getInstance()
    //? if > 1.8.9 {
    minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    //?} else {
    /*minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f))
    *///?}
}

fun Modifier.clickableWithSound(onClick: () -> Unit): Modifier = clickableWithSound(null, onClick = onClick)

internal fun Modifier.clickableWithSound(
    label: String?,
    role: A11yRole = A11yRole.Button,
    selected: Boolean? = null,
    checked: Boolean? = null,
    shape: Shape? = null,
    onClick: () -> Unit,
): Modifier = a11y(label, role, selected, checked, shape, true).clickable(role = role.role) {
    playButtonClickSound()
    onClick()
}

private val A11yRole.role: Role?
    get() = when (this) {
        A11yRole.Button -> Role.Button
        A11yRole.Tab -> Role.Tab
        A11yRole.Checkbox -> Role.Checkbox
        A11yRole.Switch -> Role.Switch
        A11yRole.Option -> Role.DropdownList
        A11yRole.Link, A11yRole.TextField -> null
    }

@Composable
fun Modifier.clickableTextWithSound(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
) {
    playButtonClickSound()
    onClick()
}
