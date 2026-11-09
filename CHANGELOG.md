## 1.4.6
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