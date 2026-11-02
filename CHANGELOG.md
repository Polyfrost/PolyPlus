## Unreleased changes
- Fixed skins not changing correctly
- Fixed the pause menu's Settings button opening the mod list instead of the full settings
- wWaypoints' sharing backend toasts are now only hidden for users who don't have access to its paid waypoint sharing feature
  * This also hides the connection attempts exhausted toasts, which weren't hidden before
- The installed mods comparison against the OneClient modpack no longer downloads the modpack itself from Modrinth
- Updated Polydium to 1.2.1

### Polydium changes
- Fixed the "Unlimited" FPS limit being capped at 260 on 1.21.11 and below
- Fixed a crash on launch and broken post-processing effects with VulkanMod
- The VulkanMod notice is no longer shown on Minecraft versions where Polydium doesn't have a Vulkan backend
- Polydium can now be disabled entirely using the `-Dpolydium.disableAll=true` JVM argument
- Fixed game inputs being bricked after a resource reload if Hide Reload Overlay is enabled with kennytvs-epic-force-close-loading-screen-mod-for-fabric installed
