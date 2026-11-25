# PolyPlus, Swylow edition

Fork of Polyfrost's PolyPlus 2.0.11 (the OneClient companion mod, GPL-3) with Swylow's menu changes:
vanilla style main menu with OneClient buttons, presets, a menu editor, quickplay panel, intro,
dark vanilla buttons, world version sections and multiplayer tabs. Only Minecraft 26.2 (Fabric) is
built and tested. README.md (top part) lists the features for players.

This repo is PUBLIC: never commit keys, tokens, hostnames of private servers, panel/server IDs or
Discord IDs. Notes about deploys stay in the owner's memory notes, not here.

## Stack

- Kotlin + Java, Fabric Loom via Stonecutter (one source tree, many Minecraft versions),
  OneConfig 1.7.1, Compose-style UI from OneConfig/PolyPlus. Gradle needs JDK 25.
- Versions and deps: `stonecutter.properties.toml` (`mod.version` = `2.0.11-swylow.N`, per-version
  deps in the `[...]` tables). Per-version projects in `versions/<mc>-fabric/`.
- Based on the 2.0.11 tag, NOT upstream main (main needs an unreleased OneConfig).

## Layout

- `src/main/kotlin/org/polyfrost/polyplus/client/gui/vanillamenu/` - all Swylow menu code:
  `VanillaTitleMenu` (the vanilla style menu + presets), `MenuLayoutEditor` / `VanillaMenuEditor` /
  `EditMenuChooser` / `MenuLayoutStore` (menu editor, saved layout), `QuickplayPanel`,
  `MultiplayerTabs`, `RecentDirectConnects`, `OtherVersionConfirm`, `VanillaListScreens`, `EssentialMenu`.
- `src/main/kotlin/org/polyfrost/polyplus/client/PolyPlusMainMenuConfig.kt` - the OneConfig options
  (subcategory "Vanilla Style"); new options go here.
- `src/main/java/org/polyfrost/polyplus/mixin/client/` - mixins; ours include
  `MixinTitleScreenVanillaMenu`, `MixinAbstractButtonDark`, `MixinWorldSelectionList`,
  `MixinWorldListEntry` (guarded to >= 26.1). Registered in `src/main/resources/mixins.polyplus.json`.
- `src/main/kotlin/org/polyfrost/polyplus/client/gui/` - upstream PolyPlus UI (lobby, social,
  wardrobe, pause menu, `PauseMenuPanel` drag helpers our editor reuses).
- `tools/swylow-guard/` - tiny separate helper jar (own `build.sh`, `INSTALL.txt` for players).
- `config/detekt`, `config/checkstyle` - upstream lint config.

## Build / test

`export JAVA_HOME=~/.jdks/jdk-25.0.4.1+1`
- `./gradlew :26.2-fabric:build` - jar in `versions/26.2-fabric/build/libs/polyplus-<version>+26.2.jar`
  (~70 MB). Not run when this file was written (another session had uncommitted work in progress);
  the first Gradle run configures every version and can take ~10 minutes.
- In-game test: `Xvfb :78 -ac`, then `DISPLAY=:78 ./gradlew :26.2-fabric:runClient`.
- Lint (Error Prone, Kotlin, detekt, checkstyle) fails on fully qualified class names in code:
  use imports.
- Stonecutter's active version is set in `stonecutter.gradle.kts`; don't switch it (that rewrites
  version comments in `src/`). Build other versions by project name instead.
- Don't `pkill -f` with a pattern that also matches your own shell command (it kills the shell).

## How it goes live

Swylow and his friend get a zip (mod jar + INSTALL.txt) from the download page
swylow.simplemcnetwork.net, plus a bot DM with install steps. Bump `mod.version`
(`2.0.11-swylow.N`) per update. Only upload or message anyone when the owner asks.
Steps: memory note `polyplus-swylow.md`.

## Gotchas and rules

- Branch `swylow-menus` (default branch of the fork). Commit and push there; never force-push.
- New features must stay guarded to >= 26.1 (older versions don't compile them and aren't built).
- Only ONE PolyPlus jar may be in a mods folder (two = crash); the OneClient launcher can swap our
  jar back to the official one on instance updates.
- Not yet verified in game: the Essential button, version sections with real old worlds,
  Friends/Discover with live online services.
- Swylow relays requests from someone else; check open questions in the memory note before guessing.

## Related

- Memory notes: `polyplus-swylow.md`, `swift-utils-mod.md` (Swylow's other mod), `swift-discord.md`.
- `~/projects/PvPEssentials` - Swift Utils, Swylow's PvP mod.
