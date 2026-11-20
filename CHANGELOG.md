# Changelog

All notable changes to PolyPlus are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- A switch to hide the OneClient section from the OneConfig sidebar. Those settings stay in the Mods tab and Minecraft's options

### Changed
- The menu music and startup sound are 5 dB louder
- Minecraft's Reduce FPS setting switches from AFK to Minimized, so it no longer caps your FPS while you're away from the keyboard
- Socials messages show the time the way your system clock does, in the lobby and in game

### Fixed
- Fixed the connection status text and progress bar sitting off center
- Fixed the Add Server screen rejecting addresses with spaces at the start or end

## [2.2.0] - 2026-11-19

- Added a World Options screen on every version
- Added a Reconnect button to the screen you see after losing connection to a server
- Added a switch to stop the warning about resource packs made for another Minecraft version
- Drag the OneClient watermark around while your inventory is open
- Click your player on the main menu to open the Wardrobe
- Sliders in OneClient's menus now click when you let go of them
- Animated cosmetics play their preview when you hover over them in the store
- Free carts skip the checkout page
- Some mods have plainer names on their cards, like Screenshots, Block Highlight, Damage Tint, Text Shadows and Nick Hider
- Argentum Extras, Sarcio and Cera options now show up in Settings on 1.8.9
- Resource packs with a custom lightmap now light the 1.8.9 lobby if you have Cera
- Unlimited framerate on 1.8.9 is really unlimited now, even when another mod raises the slider's limit
- Fixed your player on the pause screen breaking on servers whose resource pack changes player rendering, like CubeCraft
- Fixed cosmetics sitting too low while sneaking with OverflowAnimations' 1.7 sneak
- Fixed onboarding changing your GUI scale when GUI Slider is installed
- Fixed the lobby not loading when Picture Mode is installed

## [2.1.6] - 2026-11-18

- Pick up your player in the lobby by holding or dragging them, then let go to drop them
- Herobrine now plays a cave sound when he shows up
- Added a search bar to Settings
- Every setting in Settings now has a button to reset it to its default
- Renamed the Keyboard tab in Controls to Keybinds
- The tab bar in Settings shows a scrollbar when the tabs don't fit
- The FOV slider is back on 1.8.9 when Argentum is installed
- OneClient's settings pages are now in the OneConfig sidebar, and the vanilla Options screen has a button that opens them
- Custom screens now tell you where to switch back to the vanilla screen
- Direct connect, Refresh and the other server actions on the Multiplayer screen are now buttons in the header instead of a menu
- Added a Social menu size setting, and the Social menu is smaller by default
- Added switches to turn off the vignette in the lobby and on the pause screen
- Added a switch to turn off the Classic pause screen's opening animation
- Pause screen widgets can now sit in the top right or bottom left on the Classic and Compact styles
- Classic now comes before Vanilla in the pause style list
- Press OneConfig's keybind in the lobby (Right Shift by default) to open OneConfig
- The Socials keybind now defaults to Shift + `
- Server resource packs now show a loading screen while they load instead of freezing the game
- Fixed the pack screen getting stuck after pressing Original Pack Screen in Packed Packs

## [2.1.5] - 2026-11-18

- Fix hard crash with wWaypoints again

## [2.1.4] - 2026-11-18

- Fix hard crash with wWaypoints
- Improve lobby rendering performance
- Fix FPS limit not working sometimes

## [2.1.3] - 2026-11-18

- Fix FOV setting / other Sodium mods not showing in OneClient video settings
- Fixed major FPS regression with ModernFix
- Fixed getting stuck in resource packs menu
- Skipped narrator blocking startup dialog
- Fixed cosmetics not showing on offline servers

## [2.1.2] - 2026-11-18

- Fixed the main menu crashing on 1.21.10 and 1.21.11

## [2.1.1] - 2026-11-18

- Added generic names for mods in the OneConfig mod list, such as Motion Blur for PolyBlur and Toggle Sprint for PolySprint
- Vignette Tweaks now starts disabled on new installs

## [2.1.0] - 2026-11-17

Requires OneConfig 1.8.0.

The settings, resource packs, Create World, Edit World, connecting, loading and Realms screens use the lobby's style now. You can switch each one back to vanilla in settings or during onboarding.

### New screens
- Video settings has tabs for Sodium, Iris and Argentum, and Sodium add-ons can add their own pages.
- Controls shows a map of your keyboard, and OneConfig shortcuts can use key combos like Ctrl+K.
- Resource packs can be sorted into folders, dragged around and saved as profiles. Packed Packs works with it.

### Lobby
- You can pin servers to the home page and group them into folders.
- You can customize the lobby and give each server its own scene. The main menu toggles moved here.
- The wardrobe was redesigned.
- Realms show up on the Multiplayer page.
- The dock has a music button, and the top right has a Discord button.
- The Quit options are easier to find, and you can set Quit to need a second click.

### Pause screen
- Added a Classic style. It has the OneClient look with vanilla's button layout, and a row for Social, Wardrobe, Host, Mods and More below it.
- You can pick a separate style for when the game pauses because you clicked out of the window.
- Mods opens OneConfig, and Mod Menu is under More.
- "Classic pause menu" under More is renamed to "Vanilla pause menu".
- Spotlight can keep panning the camera after its animation ends.

### Other changes
- The account manager was rebuilt. You can link Discord from it, and it opens our server invite after you link.
- Added a clock widget that follows the time of day.
- Lobby and pause widgets were reworked.
- The main menu can use the vanilla panorama.
- An optional notification tells you how long the game took to start.
- Play Together tells you when a friend is on another version.
- Player previews follow your skin layer, cape and main hand settings.
- When Polyfrost's services have problems, you get a notification and the lobby shows a button to status.polyfrost.org.
- On 1.8.9 you can switch the Options screen back to vanilla's centered layout.
- Reloading resources from OneClient's screens shows the OneClient loading screen.
- PolyPlus turns off Remove Reloading Screen (RRLS) if it's installed, where it used to refuse to launch.

### Fixes
- Fixed the lobby being upside down with VulkanMod.
- Fixed skins not refreshing after switching accounts.
- Fixed your hand showing in the lobby with some mods and resource packs.
- Fixed formatting codes in pause menu text.
- Fixed Enter not opening Social after closing a popup.
- Fixed widgets moving when you toggle one.
- Fixed sign-ins being retried after Mojang rejected them.
- Fixed sent friend requests showing under "Waiting on you".
- Fixed startup sounds with OneConfig's external sounds.

## [2.0.11] - 2026-11-15

- Fix external sounds mixin

## [2.0.10] - 2026-11-15

- Use lighter lobby effects on weak and integrated GPUs
- Recover the lobby when a scene gets stuck loading instead of hanging the main menu
- Fix skins not showing right away after changing them or switching accounts
- Fix screens opened from the pause menu losing the pause menu background
- Fix Cloth Config mods losing their settings when their config file is empty
- Fix key remapping on 1.8.9 breaking mouse buttons and unusual keys
- Center player heads and party buttons in the lobby's bottom bar
- Add mod icons for Tipper, Shield Status, Polydium, LambDynamicLights, ItemPhysic Lite and TNT Countdown

## [2.0.9] - 2026-11-13

- Fixed crashes with VulkanMod
- Fixed the lobby world not rendering with VulkanMod

## [2.0.8] - 2026-11-12

- fix bossier not appearing with betterf3 (upstream issue)
- let the lobby keep its entities with entityculling 1.12
- show finished spark links on the lobby dock
- show the polyplus version in the lobby footer
- bring back the chat as picker in the in-game social window
- show client translations instead of server pack glyphs
- fix issues with super long social lists

## [2.0.7] - 2026-11-12

- Redesign the Servers page with compact server cards and a simpler top bar
- Show other mods' multiplayer screen buttons in the Servers menu
- Fit server MOTDs the same way the vanilla server list does
- Turn the lobby's scene button into a small dock with spark profiling and health reports
- Add a separate lobby music volume, also in the scene picker
- Add an option to mute the lobby music while the game is in the background
- Bring back the main menu panorama option
- Scale the lobby and main menu with OneConfig's UI scale
- Hide conversations and block players from any social menu
- Click the copyright text to open the credits
- Click the lobby's social prompt to open Social
- Open the pause screen's More menu next to its button
- Wrap Tab focus around in the menus and let keyboards reach the pause More menu
- Apply the onboarding's reduced motion setting to OneConfig right away
- Stop showing the server's address on the pause screen
- Show direct joins by their hostname instead of "Minecraft Server", and never show raw IPs
- Keep custom and palette themes when switching between light and dark
- List Full first in the pause screen styles
- Fix the 1.8.9 lobby sometimes flickering purple
- Fix menus ignoring custom theme corners
- Fix unreadable text on light themes and light accents
- Fix the pause media widget taking a while to show the current song
- Fix EvergreenHUD's hidden media buttons reacting to clicks in the main menu
- Fix long messages getting cut off in the lobby chat popup
- Fix compatibility with FireClient

## [2.0.6] - 2026-11-12

- Add a Player preview switch to the Full pause screen
- Rename the pause screen styles to Spotlight and Full
- Show your linked mod's menus on the pause screen
- Fix the macOS notch leaving a bright strip at the top of the lobby
- Fix render distance staying low after joining a world with Smooth Join
- Fix the lobby not loading with mods like Simple Discord Rich Presence

## [2.0.5] - 2026-11-12

- Fix Panorama drawn before its textures load
- Fix pause-menu playtime source
- Fix player preview rendering wack on 26.1.2

## [2.0.4] - 2026-11-12

- Fixed crash when vitrail packs are loaded

## [2.0.3] - 2026-11-12

- Remove Reloading Screen (RRLS) is now disabled automatically instead of blocking launch
- hold the lobby scene until resource reloads finish
- prefix account accessors to avoid clashes with other mods
- skip worldgen presets when the lobby falls back to vanilla data
- give the 1.21.1 player preview a stand-in local
- keep the Spatial GUI mod off the lobby screen
- stop other mods from moving the lobby camera
- stop the lobby rechecking every resource pack file each tick

## [2.0.2] - 2026-11-11

- Improved lobby loading and compatibility with other mods.
- Fixed lobby UI, name tags, and avatar clicks under the macOS notch.
- Fixed a lobby crash with Sodium on Vulkan and a pause camera crash for scaled-down players.
- Fixed tour clicks and downloaded sounds when RRLS skips the loading screen.
- Fixed lobby blur and depth-of-field rendering issues.
- Added a fallback for lobby scenes when other mods break their data.

## [2.0.1] - 2026-11-11

- Minor optimizations in font rendering emojis / OneClient logo

## [2.0.0] - 2026-11-11

- Complete visual overhaul of OneClient main menu, pause menu, and all GUIs
- Fix compat with Simple Voice Chat
- Fix OneClient badge / emojis on 1.8.9

## [1.4.8] - 2026-11-10

- Hypixel is now also detected through the Hypixel Mod API and the server brand
- wWaypoints' sneak and sign modifications are still off by default, but are now only blocked on Hypixel and can be turned back on elsewhere
- Added compatibility with the OpSec mod
- Polydium is no longer included in Poly+. If you are using the OneClient launcher or modpack, it will be automatically downloaded when you update. Otherwise, you can download it manually from [Modrinth](https://modrinth.com/mod/polydium) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/polydium).

## [1.4.7] - 2026-11-10

- Skip compose state read in CapeResolver.decide
- Fix minor FPS regression with emoji rendering on 1.8
- Fix menu gradient texture leak
- Updated Polydium to 1.4.0

## [1.4.6] - 2026-11-08

- Fixed pause menu icons not loading
- Updated Polydium to 1.3.2

### Polydium changes
- Fixed the automatic Vulkan switch never turning on, since the GPU check ran too early in startup to see the GPU
- Fixed text caching falling behind when a frame draws more text than the cache holds, such as rooms full of signs
- Dropped items now reuse their render state between frames
- Reduced per-frame allocations with Entity Culling's block entity checks
- Fixed VRAM growing with every Iris shader or resource reload on 26.3
- Improved frame pacing on macOS when Slipstream is installed
- Reduced CPU stalls on the Vulkan backend by allowing a third frame in flight
- Stopped the log spam when mods poll unbound keybinds
- Fixed depth corruption on the Vulkan backend at small window sizes with post effects that read the main depth buffer
- Backported performance improvements from 26.2 to 26.1.2 and 1.21.11
- Fixed a crash with Sodium 0.9.3-beta.2 and above

## [1.4.5] - 2026-11-06

- Added automatically joining a world invite when OneClient launches the game for it
- Fix FPS capping to 260 instead of unlimited
- Big FPS

## [1.4.4] - 2026-11-04

- Updated Polydium to 1.2.4

### Polydium changes
- Fixed a crash on the Vulkan backend with mods that rely on OpenGL keeping the previous transforms bound, such as Teamviewer (Hoplite)
- Fixed a crash when opening the OneConfig menu on the Vulkan backend ("Cannot wait on a fence for the current submit")

## [1.4.3] - 2026-11-03

- Updated Polydium to 1.2.3

### Polydium changes
- Fixed a crash on some GPUs when joining a world for the first time or hovering the Vulkan graphics API option

## [1.4.2] - 2026-11-02

- Updated Polydium to 1.2.2

### Polydium changes
- Fixed Graphics API switching away from Vulkan when it wasn't supposed to
- Fixed compatibility with Sodium FPS Cap Fix and Not Enough FPS
- Fixed a crash on Vulkan with GPUs that lack push descriptor support, such as older AMD APUs

## [1.4.1] - 2026-11-02

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

## [1.4.0] - 2026-10-30

- Update Polydium
- Poly+ will now stop a single malformed language file from a mod or resource pack blocking the game from switching to that language
- The main menu account switcher now opens a dedicated screen for adding and managing accounts
- Added a panel beside the pause menu with a player preview, Social, Wardrobe, Host World, Settings and playtime
- Added an optional Poly+ pause menu, enabled in the Main Menu settings
- Changed wWaypoints toast titles to always mention wWaypoints
- Removed wWaypoints toasts for its sharing backend dropping and reconnecting
- Fixed Poly+'s icon being cut off in Mod Menu and launchers
- wWaypoints' toggle sneak is no longer forcibly disabled â€“ added a patch to stop it from flagging anticheats
- Fixed the main menu account dropdown running off the bottom of the window when there are many accounts
- Fixed Escape closing the onboarding screen on 1.8.9

## [1.3.15] - 2026-10-26

- Added a warning when installed mods are on the known-bad list
- Replaced the sensitivity button in Controls with a Pylon raw input toggle
- Title screen icons now follow your theme preference
- Fixed login failing with `NoSuchMethodError` when another mod ships an older kotlinx.coroutines
- PolyPlus now requires Fabric Language Kotlin 1.14.1 or newer
- Updated Polydium to 1.1.9

### Polydium changes
- Fixed Punchy's performance issues
- Added HUD caching to Xaero's Minimap
- Added a VulkanMod notice

## [1.3.14] - 2026-10-25

### Polydium changes
- Fixed OpenGL error spam from `glFlushMappedNamedBufferRange` on 26.2 (#10)
- Fixed a dark square appearing below the player with OverflowAnimations' legacy sky (#13)
- Fixed a crash when exporting Flashback replays on the Vulkan backend with Sodium

## [1.3.13] - 2026-10-24

- Fixed crash with PolyPlus removing stream keybinds on 1.8.9
- Fixed cosmetics not reloading on the title screen after disconnecting
- Update the order of mods

## [1.3.12] - 2026-10-23

- Fixed the game failing to start because Poly+ required an unreleased version of OneConfig

Polydium changes:
- Lowered Vulkan requirements, allowing it to run on more hardware (mirrors 26.4-snapshot.1)
- Fixed automatic Vulkan switches not working
- Polydium now only falls back from Vulkan after crashes that are actually Vulkan-related
- Fixed broken leaf textures with ModernFix (#6)
- Fixed initial startup lag (#7)
- Fixed Sodium Extra's resolution reducer applying on non-Retina displays (#5)
- Fixed sky optimizations conflicting with Skyboxify
- Fixed a crash with Impact Frames when attacking on Polydium's Vulkan backend on 1.21.11/26.1
- Fixed a long freeze when leaving water with Skyboxify custom skies

## [1.3.11] - 2026-10-23

- Fixed the game freezing after loading a few worlds in the same session

## [1.3.10] - 2026-10-17

- Fixed a crash with Cubes Without Borders when using a custom fullscreen resolution with Vulkan
- Fixed the game getting stuck on "Preparing for world creation" with Vulkan on 1.21.11 when there are no singleplayer worlds
- Fixed a crash with Xaero's Minimap's entity radar set to icons on Polydium's Vulkan backend on 1.21.11/26.1

## [1.3.9] - 2026-10-16

- Improved compatibility with other mods that modify the same game, Sodium or Iris code as Polydium
- Fixed a crash on launch with Vulkan on 26.1 when using Sodium 0.8

## [1.3.8] - 2026-10-15

- Fixed crashes and title screen hangs on Vulkan with mods that add custom texture formats, such as Skyblocker
- Fixed depth-tested entity glow from mods such as Skyblocker and SkyHanni showing through walls with Vulkan on 1.21.11 and 26.1
- Fixed an "Out of stack space" crash on launch with Vulkan on 1.21.11 on some GPUs, mainly NVIDIA

## [1.3.7] - 2026-10-15

- Fix sodium extra macOS reduced resolution

## [1.3.6] - 2026-10-15

- Fix scissors on Vulkan 26.1/1.21.11

## [1.3.5] - 2026-10-15

- Vulkan on 1.21.11/26.1.2
- Fix mod card for 3dSkinLayers/WaveyCapes

## [1.3.4] - 2026-10-14

- Fix account switcher saving at wrong location

## [1.3.3] - 2026-10-13

- Fixed random crash when reloading resources on Minecraft 26.3

## [1.3.2] - 2026-10-13

- Fixed crash on launch with VulkanMod

## [1.3.1] - 2026-10-13

- Fix graphical glitches with Iris (26.2) and textures (1.21.8-.11)

## [1.3.0] - 2026-10-13

- ~20% FPS improvements with Polydium beta
- Put Polydium in mod list

## [1.2.60] - 2026-10-13

- ~20% FPS improvements
- Add icons for BetterHurtCam and PolyFullbright
- Strip away Legacy 3D Skin Layers mod icon

## [1.2.59] - 2026-10-11

- Fix not being able to get past onboarding

## [1.2.58] - 2026-10-11

- Have 3DSkinLayers, WaveyCapes, and LambdaBetterGrass off by default
- Alphabetical mod sorting in onboarding
- 1.8: Draw panorama behind loading screen
- 1.8: Change client brand to OneClient
- Improve compat with our mixins

## [1.2.57] - 2026-10-06

- fix padding in quick play when no servers

## [1.2.55] - 2026-10-06

- Fixed compatibility with PolyNametag

## [1.2.54] - 2026-10-06

- Force-disabled wWaypoints 0.8.1's Toggle Sneak and its indicator, which its server opt-out no longer covers despite still triggering anticheat flags
- Extended wWaypoints compatibility to Minecraft 1.21.1 to 1.21.10, which wWaypoints supports as of 0.8.1
- Improved compatibility with other mods that adjust anvil item names or sign text, such as wWaypoints
- Fixed sign and anvil probes being able to detect hidden mods through translation fallbacks with arguments or resource packs that translate their keys
- Hid wWaypoints 0.8.1's server restriction toast when the server only disables features Poly+ already disables

## [1.2.53] - 2026-10-05

- Show a grass block in block highlight onboarding

## [1.2.52] - 2026-10-04

- Fixed screens with a live player preview, such as the main menu, never showing up on macOS on Minecraft 1.21.1 and 1.21.4

## [1.2.51] - 2026-10-04

- Detect the OneClient SkyBlock modpack when comparing installed mods, instead of listing its SkyBlock mods as external mods
- Unbind wWaypoints' create waypoint key by default (also for existing users if you didn't change it from the default)

## [1.2.50] - 2026-10-03

- 1.8: fix async being on until next restart

## [1.2.49] - 2026-10-03

- Fixed the onboarding screen and OneConfig menus breaking with OneConfig 1.2.14

## [1.2.48] - 2026-10-02

- Fixed block highlight onboarding showing CBH defaults instead of vanilla on 1.8.9

## [1.2.47] - 2026-10-02

- Fix crash with Iris

## [1.2.46] - 2026-10-02

- 1.8.9: Added Dynamic FOV and View Bobbing to Video Settings
- 1.8.9: Disable ItemPhysicLite by default

## [1.2.45] - 2026-10-02

- Add various performance boosts
  - Optimizes vanilla's GpuBufferPool on 26.2+
  - Fixes wWaypoints memory allocations
  - Optimizes Iris, especially when no shaders are on
- Fix wrongly flipped players and swapped color channels

## [1.2.44] - 2026-10-01

- Fixed the main menu failing to load when a singleplayer world is corrupted
- Fixed the previous world staying in memory after leaving a server or changing dimension if the cosmetics preview had been opened in-game
- Fixed pets disappearing after changing dimensions or switching lobbies, and pets lingering after their owner unequipped them out of view
- Fixed every opened multiplayer screen staying in memory while featured servers were shown
- Fixed a failed or cancelled friends-hosting attempt keeping shared resource packs in memory and auto-publishing the next singleplayer world to a stale session
- Fixed cosmetics seen in the store or on other players staying in memory until disconnecting
- Fixed the first world joined staying in memory for the whole session when Mount Opacity is installed
- Fixed social and cosmetic events being processed twice, causing duplicate refreshes and extra network requests
- Fixed Social chat message history using more and more memory over long sessions
- Fixed the main menu using more memory each time it was opened and re-pinging the featured server every time
- Fixed memory building up when the game repeatedly creates crash reports for errors it recovers from
- Fixed Poly+ multiplayer connections dropping after switching Poly+ accounts or quickly rejoining a hosted world
- Fixed several smaller memory and resource leaks around server connections, Microsoft sign-in, P2P voice chat, tab list badges, player names, store images, the social overlay and the main menu panorama
- Fixed cosmetics on other players sometimes no longer updating after a failed request
- Reduced stutter and memory churn from player previews in the cosmetics menu and main menu
- Fixed the purchase history tab constantly reloading

## [1.2.43] - 2026-09-30

- Fix wWaypoints FPS issue whilst we wait for 0.8.1
- Replace animatium references with OverflowAnimations

## [1.2.42] - 2026-09-29

- Add freelook icon

## [1.2.41] - 2026-09-29

- Turn RGSS off by default
- Update mod order to include freelook and dynamic lights
- Add dynamic lights to onboarding

## [1.2.40] - 2026-09-29

- The Epic Online Services (EOS) native library used for world hosting is now signed, which should reduce issues with Windows Smart App Control

## [1.2.39] - 2026-09-29

- Fix minor FPS issues

## [1.2.38] - 2026-09-29

- Cosmetic visibility options
- Main menu footer toggle
- Main menu overlay toggle
- Fixed onboarding UI dying

## [1.2.37] - 2026-09-28

- Update mod order list

## [1.2.36] - 2026-09-28

- Add new mod icons

## [1.2.35] - 2026-09-28

- Add onboarding for CustomBlockHighlight
- Fix compat with ExploitPreventer
- Show single player worlds in quickplay
- Add mod icon for PolyPlus mod cards

## [1.2.34] - 2026-09-26

- Fixed Poly+ not asking you to accept the Terms of Service and Privacy Policy again after they were updated
- Fixed the modpack comparison in game logs listing nearly every mod as removed on OneClient
- Removed Animatium custom item offset from onboarding and reset it for existimg users
- Fixed wWaypoints' bannable features not being properly disabled, causing issues with sneaking and signs on some servers

## [1.2.33] - 2026-09-24

- Fixed messages containing emoji not being compacted by Chat Patches and Chat Tweaks
- Fixed cancelling Microsoft sign-in showing a cancellation error
- Fixed cover images that were still loading when leaving a screen never loading again until restart
- Fixed top row of player preview being faded, causing it to appear weirdly cut off
- Fixed world hosting and joining through Poly+ no longer working after about an hour or a brief connection drop until the game was restarted
- Fixed Poly+ giving up on reconnecting after being offline for more than a few minutes
- Fixed hosting a world for friends silently doing nothing when Poly+ couldn't start the session, such as while offline. The error is now shown instead.
- Fixed friends sometimes failing to join a world you're hosting through Poly+ and timing out instead
- Fixed a Poly+ hosted world connection carrying on after a packet was lost, which could desync players or kick them with confusing errors. They are now disconnected with the actual reason.
- Fixed players without Poly+ being unable to join worlds hosted by a Poly+ user, such as through e4mc or Essential
- Fixed pets crashing the game on Minecraft 1.21.4, 26.2, and 26.3
- Fixed Poly+ crash reporting replacing the real cause of a crash with its own error in some cases

## [1.2.32] - 2026-09-22

- Fixed the RAM advisor slightly negatively affecting performance

## [1.2.31] - 2026-09-21

- Fixed the RAM advisor sometimes telling you to lower your RAM allocation too much

## [1.2.30] - 2026-09-21

- Disable "Block Useless Perks" in IQ Addons by default for new and existing users (if you turn it back on it will stay enabled)
- Also disable Remove Reloading Screen's missing texture substitution on 26.2+ Vulkan to stop more crashes
- Force disable wWaypoints' bannable "Stay Sneaked in Containers" option and block it in Exploit Preventer so it is safe to play with it on servers
- PolyPlus now disables Minecraft's narrator while muted by default to avoid unexplained lagspikes (can be disabled in the config)

## [1.2.29] - 2026-09-18

- New panorama for 26.3

## [1.2.28] - 2026-09-18

- Now shows where each mod keeps the settings onboarding wrote

## [1.2.27] - 2026-09-17

- Add more information in logs for debugging.

## [1.2.26] - 2026-09-16

- Add animated cape support
- Fix player preview disappearing on pop-ups

## [1.2.25] - 2026-09-16

- Fixed keybinds on 26.3

## [1.2.24] - 2026-09-16

- Added support for Minecraft 26.3
- New onboarding for several mods and brightness
- Cosmetics now support animated textures
- Fixed authentication breaking when joining a server for the first time
- Fixed accounts being marked as expired when their expiry could not be read
- The RAM advisor now detects memory pressure caused by swapping
- Fixed unrelated connections being blocked by the online services consent
- Cosmetics, the main menu buttons and world hosting now require accepting the Terms of Service

## [1.2.23] - 2026-09-13

- PolyPlus now force disables RRLS on 26.2+ Vulkan to prevent a GPU crash (https://github.com/dima-dencep/rrls/issues/254)

## [1.2.22] - 2026-09-12

- Fixed not being able to buy cosmetics

## [1.2.21] - 2026-09-10

- fix stale p2p messing up next server join
- realign mod order
- put emoji button in chatting's button row
- fix p2p world cheat enabling on 26.2

## [1.2.20] - 2026-09-10

- Fix accidentally overwriting OneConfig theme

## [1.2.19] - 2026-09-10

- Added RAM advisor, helping you with lag spikes caused by memory issues
- Fixed log spam with group chats
- Added recovery for Epic Online Services connection issues (for world hosting)

## [1.2.18] - 2026-09-07

- Fix EuphoriaPatches logs showing in main menu again

## [1.2.17] - 2026-09-06

- Fix modernfix

## [1.2.16] - 2026-09-06

- Change modernfix defaults
- Improve sponsored main menu thing design

## [1.2.15] - 2026-09-06

- main menu card for featured servers

## [1.2.14] - 2026-09-05

- kill OneClient watermark

## [1.2.13] - 2026-09-05

- emoji picker
- OneClient watermark
- replace open to lan with host world
- sponsored servers
- unread message only filter
- add more EOS failure logs

## [1.2.12] - 2026-08-26

- Fix resizing issues

## [1.2.11] - 2026-08-26

- Fix connection issues with multiplayer servers
- Fix fullscreen not applying to Minecraft window sometimes

## [1.2.10] - 2026-08-24

- Fix Sign Translation Vulnerability

## [1.2.9] - 2026-08-24

- fix being stuck on blur optimizing

## [1.2.8] - 2026-08-24

- fix: use FramebufferRenderEvent rather than TickEvent
- fix status handling visually
- support resource pack overriding panorama
- detect EOS stalling, fix p2p connection issues
- fix stupid issues with p2p world disconnecting

## [1.2.7] - 2026-08-22

- feat: add group name in group message notification
- fix(defaults): animatium breaking changes + unbind noxesium
- fix(mainmenu): wait for entity renderers to exist before showing preview
- fix(sentry): don't report crashes we catch and recover from
- fix(preview): guard the render dispatch on 1.21.8 as well
- fix(panorama): treat a half-written cache dir as incomplete
- fix(cosmetics): keep a type listed when its stock probe fails
- chore(emotes): drop the dead emote wheel keybind registration

## [1.2.6] - 2026-08-18

- fix player preview crash on 1.21.1

## [1.2.5] - 2026-08-17

- Force disable bannable Debugify issues
- Force kill Animatium toasts
- Report actual mod count in mod menu

## [1.2.4] - 2026-08-16

- Fix certain capes not loading for other players
- Fix log spam from EOS

## [1.2.3] - 2026-08-16

- Add support for new login params

## [1.2.2] - 2026-08-16

- Show player name and icon in group chat
- Vanilla/OneClient switch main menu button
- Add JVM arg for API URL
- Fix text input focus in socials menu
- Put special chats in separate category

## [1.2.1] - 2026-08-15

- Production backend is now always used outside development environment to prevent users from accidentally changing it and breaking things

## [1.2.0] - 2026-08-15

- new: friends system
- new: world hosting
- feat(accounts): allow adding offline accounts without a Microsoft account signed in
- feat: automatically refresh Microsoft account sessions

## [1.1.25] - 2026-08-13

- feat(accounts): allow adding offline accounts without a Microsoft account signed in

## [1.1.24] - 2026-08-13

- fix(PolyPlusBadge): show the badge on tab lists built from placeholder profiles
- fix(sentry): recognise deliberate crashes with translated messages
- fix(onboarding): fix compat with animatium 4.2

## [1.1.23] - 2026-08-10

- Fixed not being able to join servers that enforce chat signing if using a different account than the one you started the game with
- Fixed cosmetics always showing for the account you started the game with instead of your active account

## [1.1.22] - 2026-08-07

- Filtered out more useless watchdog crash reports

## [1.1.21] - 2026-08-07

- Non-crash events are no longer sent to Sentry at all
- The same crash from the same user will only be sent to Sentry at most once per hour

## [1.1.20] - 2026-08-06

- Fix minor misc. bugs
- Improve PolyBlur onboarding even further
- Put certain mods in the advanced mod card type

## [1.1.19] - 2026-08-06

- Isolated our Sentry crash reporting implementation to prevent interference from other mods
- Fixed Fabric API not being declared as a dependency

## [1.1.18] - 2026-08-05

- Fix websocket issues

## [1.1.17] - 2026-08-05

- Split PolyPlus config
- Add a default mod order
- New motion blur onboarding menu
- Add "Panorama In All Menus": keeps the main menu backdrop behind every other menu.
- Add "Custom Panorama" toggle to opt out of the PolyPlus panorama
- Emoji suggestions no longer open on namespaced ids or while vanilla command completion is showing
- Show "FREE" instead of a price for zero-cost store items and carts
- Fix crash on 1.21.1 when registering the pet entity type
- Fix misc websocket crashes

## [1.1.16] - 2026-08-03

- Fix aura cosmetics being visually stuck

## [1.1.15] - 2026-08-02

- Added compatibility with OneConfig 1.0.14

## [1.1.14] - 2026-08-01

- Fixed some excluded crash types still getting sent as postmortems

## [1.1.13] - 2026-08-01

- Added a workaround for Remove Reloading Screen crashing the game when switching from a newer Minecraft version to an older one

## [1.1.12] - 2026-08-01

- Fixed some connection errors being sent to Sentry as fatal errors
- Added thread information to error reports to aid debugging

## [1.1.11] - 2026-07-31

- Fixed exceptions from background threads being sent to Sentry as fatal errors

## [1.1.10] - 2026-07-31

- Added flashback button to main menu
- Added modmenu button to main menu
- Added realms to main menu
- Now shows e4mc hint as hover tooltip
- Corrected punctuation in Mojang copyright

## [1.1.9] - 2026-07-31

- Improved error and crash reporting to let us prioritize actual critical issues better

## [1.1.7] - 2026-07-29

- Enable Bobby Dynamic Multiworld by defalt

## [1.1.6] - 2026-07-28

- Enable Bobby Dynamic Multiworld by defalt

## [1.1.5] - 2026-07-28

- Changed default avatar to Steve
- FIX: Tooltips capturing cursor
- FIX: Session account not being listed in account switcher

## [1.1.4] - 2026-07-25

- Changed default avatar to Steve
- FIX: Tooltips capturing cursor
- FIX: Session account not being listed in account switcher

## [1.1.3] - 2026-07-24

- All versions: Default blur settings are now applied adaptively based on measured FPS
- All versions: Fixed main menu theme change icon not working
- All versions: Added tooltips to main menu icons
- 1.21.8: Fixed increased title screen panorama blur not working
- 1.21.10â€“1.21.11: Fixed chat emoji features being partially broken (e.g. tab completion)

## [1.1.2] - 2026-07-23

1.1.2 changelogs:

- Make GUI scale apply instantly to main menu

## [1.1.1] - 2026-07-23

1.1.1 changelogs:

- Fix player preview on Vulkan 26.2
- Turn on keyboard-like movements on Controlify by default

## [1.1.0] - 2026-07-22

1.1.0 changelogs:

- adds account switcher functionality
- fixes player preview on 26.2

## [1.0.4] - 2026-07-20

1.0.4 changelogs:

- fix cosmetics showing on invisible players...

## [1.0.3] - 2026-07-20

1.0.3 changelogs:

- Fix PolyPlus version always reporting as 1.0.0

## [1.0.2] - 2026-07-20

1.0.2 changelogs:

- Fix compat with WaveyCapes
- Fix false Sentry errors

## [1.0.1] - 2026-07-18

No changelog provided.

## [1.0.0] - 2026-07-18

No changelog provided.
