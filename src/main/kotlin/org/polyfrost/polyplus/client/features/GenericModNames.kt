package org.polyfrost.polyplus.client.features

import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.platform.v1.ModInfo

object GenericModNames {
    private val NAMES = mapOf(
        "PolyBlur" to "Motion Blur",
        "PolySprint" to "Toggle Sprint",
        "PolyCrosshair" to "Custom Crosshair",
        "PolyHitbox" to "Hitboxes",
        "PolyNametag" to "Nametags",
        "PolyTime" to "Time Changer",
        "PolyWeather" to "Weather Changer",
        "PolyZoom" to "Zoom",
        "Zoomify" to "Zoom",
        "Chatting" to "Chat Visuals",
        "ChatTweaks" to "Chat Tweaks",
        "BehindYouV3" to "Snaplook",
        "BehindYou (SnapLook)" to "Snaplook",
        "OverflowAnimations" to "Old Animations",
        "OverflowParticles" to "Particles",
        "EvergreenHUD" to "HUD Elements",
        "Overlay Tweaks" to "Overlays",
        "FOVChanger" to "FOV Changer",
        "ColorSaturation" to "Color Saturation",
        "LambDynamicLights" to "Dynamic Lights",
        "LambdaBetterGrass" to "Better Grass",
        "Blur+" to "GUI Blur",
        "Legacy GUI Blur" to "GUI Blur",
        "ItemPhysic Lite" to "Item Physics",
        "ItemPhysicLite" to "Item Physics",
        "ItemPhysic Lite Config" to "Item Physics",
        "AppleSkin" to "Saturation",
        "WaveyCapes" to "Wavey Capes",
        "LegacyWaveyCapes" to "Wavey Capes",
        "Gamma Utils" to "Fullbright",
        "FullBright" to "Fullbright",
        "wWaypoints" to "Waypoints",
        "Detail Armor Bar" to "Detailed Armor Bar",
        "Detail Armor Bar Reconstructed" to "Detailed Armor Bar",
        "Tipper" to "AutoTip",
        "3d-Skin-Layers" to "3D Skin Layers",
        "Screencapper" to "Screenshots",
        "Custom Block Highlight" to "Block Highlight",
        "DamageTint" to "Damage Tint",
        "Sciophobia" to "Text Shadows",
        "Simple Nick Hider" to "Nick Hider",
        "LegacySkinLayers" to "3D Skin Layers",
        "BetterHurtCam" to "Hurtcam",
        "GlintColorizer" to "Glint Customizer",
        "HyInfo" to "Hypixel Info",
        "HyLobby" to "Hypixel Lobby",
        "HyBridge" to "Hypixel Bridge",
        "HyBedWars" to "Hypixel BedWars",
        "HyChatter" to "Hypixel Chat"
    )

    fun initialize() {
        ConfigManager.addTreeRegistrationListener { rename(it) }
        ConfigManager.active().trees().forEach { rename(it) }
    }

    internal fun rename(tree: Tree, mods: Set<ModInfo> = ModInfo.loadedMods) {
        val title = tree.title as? String ?: return
        val name = NAMES[title] ?: return

        tree.addMetadata("mod_card_title", name)
        val aliases = tree.getMetadata<Any?>("aliases") as? Iterable<*> ?: emptyList<Any?>()
        tree.addMetadata("aliases", (aliases + title).distinct())

        mods.firstOrNull { it.name.equals(title, ignoreCase = true) }?.let { mod ->
            tree.addMetadata("mod_card_authors", mod.authors)
            tree.addMetadata("mod_card_credits", mod.credits)
            tree.addMetadata("mod_card_version", mod.version)
            tree.addMetadata("mod_card_description", mod.description)
        }
    }
}
