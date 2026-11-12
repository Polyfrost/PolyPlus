# Client import audit — 11 November 2026

Compared the importer used by Minecraft 1.8.9 (Ornithe) and 26.3 (Fabric). This is a source/schema audit plus automated import tests, not an in-game visual comparison of every option.

## Confirmed and corrected

| Setting | Gap | Correction |
| --- | --- | --- |
| Keyboard bindings | LWJGL 2 numeric codes were copied into modern named-key fields, or vice versa. | Convert common keyboard, modifier, function, navigation, keypad and mouse bindings in both directions, including 26.3's SDL names for the physical keypad period and application keys. Skip keys that have no known destination representation, keeping the existing binding. |
| Clouds | Lunar JSON booleans became bare `true`/`false`; modern Minecraft's cloud enum requires JSON strings. Quoted modern values do not match legacy values. | Serialize modern cloud names as JSON strings and unwrap them for 1.8.9. |
| Ambient occlusion | Legacy integer values and modern booleans were copied unchanged. | Convert off/on to the destination representation. Legacy minimum/maximum smoothing both become modern enabled. |
| Language | Legacy language codes use country capitals, while modern codes are lowercase. | Normalize language code casing for the destination. |
| Options data version | The source `version` could replace the destination version, affecting Minecraft's data fixes. | Preserve the destination's version metadata. |
| Lunar player/self hitboxes | Self's separate default `Never` show condition could hide it even after importing its color. Lunar may omit unchanged options entirely. | Enable the player and self visual/logic overrides and visibility, including when Lunar has no customized player color. Apply the saved player color to both when present. |
| Badlion/NoRisk hidden hitboxes | The importer used index 2 for `Never`; installed PolyHitbox 1.3.9 uses 2 for `Hovered` and 3 for `Never`. | Import hidden categories as index 3. Explicitly visible categories also replace an existing hidden condition. |
| PolyHitbox category formats | Badlion and NoRisk emitted flat settings only; newer PolyHitbox uses nested category trees and a combined override. | Emit both flat split overrides and nested combined overrides for translated settings from all three clients. |
| Hitbox thickness | Imported values could exceed the installed mod's supported 0.5–3.0 range. | Clamp to that range and skip non-finite NoRisk line widths. |
| Imported options never took effect | `options.txt` was merged on disk, but the running game only reads it at startup, so nothing applied that session and the game's next options save wrote the old values back over the import. | Reload the game's options straight after the merge. |
| Graphics quality | `fancyGraphics` (to 1.15), `graphicsMode` (1.16–1.21.10) and `graphicsPreset` plus its split options (1.21.11+) were copied under the source's name only. | Write the form each generation reads, using the same split the game applies when it upgrades. |
| Key names across input backends | Names were passed through unchecked. Before 26.3 an unknown name stops the game loading the rest of the file; on 26.3 it unbinds the key. | Accept only names the destination knows, map the keypad decimal and application keys by the source's data version, and skip bindings with a Forge modifier suffix. |
| Renamed or reformatted options | `showNowPlayingToast`/`musicToast`, the pre-1.21 fractional `menuBackgroundBlurriness`, `key_key.swapHands`, and gamma above 1.0 from 1.8.9 fullbright tweaks were rejected or ignored by the destination. | Convert each to the destination's form. |
| Session state | `startedCleanly` was copied from the source. | Excluded. |
| Unmapped HUDs with an equivalent | Lunar saturation, playtime, tab list, Hypixel TPS and the Bed Wars height limit child; Badlion scoreboard, server address, TPS, uptime and player list; NoRisk mouse tracker; Lunar and Badlion item tracker. | Mapped. The Badlion ones have no saved box in the inspected profiles, so only their on/off state and styling carry over. |
| Lunar child HUD options | Options saved on the child that holds a HUD's placement (e.g. `PING_HUD`) were ignored. | Read alongside the module's own options. |
| Unplaced single-instance HUDs | A scoreboard, tab list or similar HUD that was on but had no saved position could be created stacked in the top-left corner. | Left at the game's usual spot unless an instance already exists. |
| NoRisk 3D skins | Only Lunar's `3D_SKINS` set the 3D skin layers preference. | NoRisk's `3D Skin` module sets it too when no Lunar profile is part of the import. |

## Settings that still do not fully transfer

These are shared importer coverage limits, rather than differences between the two PolyPlus builds.

| Area | Current coverage | Remaining gap |
| --- | --- | --- |
| Most non-HUD client modules | Recognized enabled modules enable their matching installed mod. Lunar fly boost is also translated. | Detailed settings such as crosshair geometry, blur strength, time/weather values, glint/hit colors, FOV adjustments, particle rules, nametag styling and auto-text actions/keybinds have no translators. |
| Lunar HUD styling | Placement, scale, shared background/text colors, chroma, shadow, brackets and selected coordinate options. | Other module-specific options, including armor durability text placement, are not mapped. |
| Feather HUD styling | Recognized HUDs' enabled state and placement. | HUD scale, colors and other style options are not translated. |
| Badlion HUD styling | Recognized HUD placements and selected explicitly customized background/text/shadow settings. | Scale and other per-HUD style options are not translated. |
| NoRisk HUD styling | Recognized HUD placements, scale, background and text colors/chroma. | Other module-specific style options are not translated. |
| Hitboxes | Lunar player color; Badlion category visibility, outline colors and sizes; NoRisk category visibility, outline/view-ray colors, view-ray visibility and width. | Feather hitbox details; other Lunar entity styles; additional source-specific visual options and animated color behavior are not mapped. |
| Other client modules | Only named features and HUDs in the mapping tables are recognized. | For example, the inspected Lunar profile's waypoint naming and barrier-outline options have no mappings. |
| Options across generations | Matching option names/representations plus the corrections above. | Settings added or removed by a Minecraft version have no automatic equivalent. Named keys introduced by 26.3's input backend have no representation on older versions and are skipped. On 1.8.9 an imported fullscreen setting applies at the next launch. |
| Direct mod config import | Only the shared-mod allowlist and recognized OneConfig HUD/profile files are copied. | Arbitrary OneConfig mod settings are not copied merely because both instances have the mod. A matching feature mod must be loaded and its target config must already exist for a translated feature patch to apply. |

Installation/session values (resource pack selections, sound device, last server and onboarding state) are intentionally excluded. Resource pack files and servers are not part of this settings import.

## Evidence and verification

- Installed PolyHitbox 1.3.9 configs and class definitions were checked on both game versions, including split overrides and the `Always / Toggled / Hovered / Never` ordering.
- All 30 mapped EvergreenHUD/VanillaHUD classes were present in both installed sets: EvergreenHUD 3.9.16 / 3.9.14 respectively, and VanillaHUD 3.5.10 on both. Optional HUDs still depend on their owning mod being installed.
- Saved HUD instances use matching shared fields on both versions (including packed ARGB colors, anchor sections, relative positions and scale).
- Minecraft's cached 26.3 option classes and saved legacy/modern options were inspected for cloud, ambient-occlusion, keybind and language representations.
- Regression suites: `GameOptionsImportTest`, `HitboxImportTest`, `ClientFeatureImportTest`, `ClientHudImportTest`, and `ModConfigPatcherTest`, run for both `:1.8.9-ornithe:test` and `:26.3-fabric:test`. The 1.21.x, 26.1 and 26.2 builds were not compiled or tested for the later corrections.
- Option names, key names and the game's own options upgraders were read from the cached 1.8.9, 1.21.1–1.21.11 and 26.1–26.3 game jars.

Existing imported profiles are not automatically reimported or edited by this change. These corrections apply the next time an import is performed.
