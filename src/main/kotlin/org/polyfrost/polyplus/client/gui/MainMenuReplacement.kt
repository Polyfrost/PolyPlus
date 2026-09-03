package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent

object MainMenuReplacement {
    @JvmStatic
    fun enabled(): Boolean = !PolyPlusMainMenuConfig.useVanillaMainMenu

    @JvmStatic
    fun alreadyOpen(): Boolean = ClientPlatform.currentScreen().let {
        it is PolyPlusMainMenuScreen || it is PolyPlusOnboardingScreen
    }

    @JvmStatic
    fun create(): Screen =
        if (!PolyPlusConfig.onboardingCompleted || PrivacyConsent.needsPrompt() || OnboardingFeatures.needsMotionBlurChoice()) {
            PolyPlusOnboardingScreen()
        } else {
            PolyPlusMainMenuScreen()
        }

    @JvmStatic
    fun open() = ClientPlatform.setScreen(create())
}
