## Unreleased changes
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
