plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric"

stonecutter handlers {
    inherit("json5", "json")
}

val legacyImports = mapOf(
    "net.minecraft.client.resources.language.I18n" to "org.polyfrost.polyplus.client.lobby.LobbyI18n as I18n",
    "com.mojang.blaze3d.platform.NativeImage" to "org.polyfrost.oneconfig.internal.legacy.NativeImage",
    "net.minecraft.client.renderer.texture.DynamicTexture" to "net.minecraft.client.render.texture.DynamicTexture",
    "net.minecraft.client.multiplayer.ServerData" to "org.polyfrost.polyplus.client.lobby.LobbyServerData as ServerData",
    "net.minecraft.client.multiplayer.ServerList" to "org.polyfrost.polyplus.client.lobby.LobbyServerList as ServerList",
    "net.minecraft.client.multiplayer.ServerStatusPinger" to "org.polyfrost.polyplus.client.lobby.LobbyServerPinger as ServerStatusPinger",
    "net.minecraft.client.multiplayer.resolver.ServerAddress" to "org.polyfrost.polyplus.client.lobby.LobbyServerAddress as ServerAddress",
    "net.minecraft.network.chat.CommonComponents" to "org.polyfrost.polyplus.client.lobby.LobbyCommonComponents as CommonComponents",
    "org.polyfrost.oneconfig.internal.legacy.chat.CommonComponents" to "org.polyfrost.polyplus.client.lobby.LobbyCommonComponents as CommonComponents",
    "net.minecraft.util.StringDecomposer" to "org.polyfrost.oneconfig.internal.legacy.chat.StringDecomposer",
    "net.minecraft.client.gui.screens.options.OptionsScreen" to "net.minecraft.client.gui.screen.menu.options.OptionsScreen",
    "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen" to "net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen as JoinMultiplayerScreen",
    "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen" to "net.minecraft.client.gui.screen.menu.world.SelectWorldScreen",
    "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen" to "net.minecraft.client.gui.screen.menu.world.CreateWorldScreen",
    "net.minecraft.world.level.GameType" to "net.minecraft.world.WorldSettings.GameMode as GameType",
    "net.minecraft.world.level.storage.LevelSummary" to "org.polyfrost.polyplus.client.lobby.LobbyLevelSummary as LevelSummary",
    "net.minecraft.client.server.LanServerDetection" to "org.polyfrost.polyplus.client.lobby.LobbyLan as LanServerDetection",
    "net.minecraft.world.phys.Vec3" to "net.minecraft.util.math.Vec3d as Vec3",
    "net.minecraft.client.multiplayer.ClientLevel" to "net.minecraft.client.world.ClientWorld as ClientLevel",
    "net.minecraft.client.multiplayer.ClientPacketListener" to "net.minecraft.client.network.handler.ClientPlayNetworkHandler as ClientPacketListener",
    "net.minecraft.client.multiplayer.MultiPlayerGameMode" to "net.minecraft.client.ClientPlayerInteractionManager as MultiPlayerGameMode",
    "net.minecraft.client.player.LocalPlayer" to "net.minecraft.client.entity.living.player.LocalClientPlayerEntity as LocalPlayer",
    "net.minecraft.client.player.RemotePlayer" to "net.minecraft.client.entity.living.player.RemoteClientPlayerEntity as RemotePlayer",
    "net.minecraft.nbt.CompoundTag" to "net.minecraft.nbt.NbtCompound as CompoundTag",
    "net.minecraft.nbt.ListTag" to "net.minecraft.nbt.NbtList as ListTag",
    "net.minecraft.nbt.Tag" to "net.minecraft.nbt.NbtElement as Tag",
    "net.minecraft.world.level.BlockGetter" to "net.minecraft.world.WorldView as BlockGetter",
    "net.minecraft.world.level.LightLayer" to "net.minecraft.world.LightType as LightLayer",
    "net.minecraft.world.level.chunk.LevelChunk" to "net.minecraft.world.chunk.WorldChunk as LevelChunk",
    "net.minecraft.world.level.chunk.LevelChunkSection" to "net.minecraft.world.chunk.WorldChunkSection as LevelChunkSection",
    "net.minecraft.world.level.chunk.DataLayer" to "net.minecraft.world.chunk.ChunkNibbleStorage as DataLayer",
    "net.minecraft.world.level.Level" to "net.minecraft.world.World as Level",
    "net.minecraft.world.level.block.Blocks" to "org.polyfrost.polyplus.client.lobby.LobbyBlocks as Blocks",
    "net.minecraft.tags.BlockTags" to "org.polyfrost.polyplus.client.lobby.LobbyBlockTags as BlockTags",
    "net.minecraft.world.phys.HitResult" to "org.polyfrost.polyplus.client.lobby.LobbyHit as HitResult",
    "net.minecraft.world.phys.BlockHitResult" to "org.polyfrost.polyplus.client.lobby.LobbyHit as BlockHitResult",
    "net.minecraft.world.entity.Pose" to "org.polyfrost.polyplus.client.lobby.LobbyPose as Pose",
    "net.minecraft.core.SectionPos" to "org.polyfrost.polyplus.client.lobby.LobbySectionPos as SectionPos",
    "net.minecraft.world.level.levelgen.Heightmap" to "org.polyfrost.polyplus.client.lobby.LobbyHeightmap as Heightmap",
    "net.minecraft.Util" to "org.polyfrost.polyplus.client.lobby.LobbyUtil as Util",
    "net.minecraft.util.Util" to "org.polyfrost.polyplus.client.lobby.LobbyUtil as Util",
    "net.minecraft.util.Mth" to "org.polyfrost.polyplus.client.lobby.LobbyMth as Mth",
    "net.minecraft.core.BlockPos" to "net.minecraft.util.math.BlockPos",
    "net.minecraft.core.Direction" to "net.minecraft.util.math.Direction",
    "net.minecraft.world.level.ChunkPos" to "net.minecraft.util.math.ChunkPos",
    "net.minecraft.world.entity.Entity" to "net.minecraft.entity.Entity",
    "net.minecraft.world.level.block.Block" to "net.minecraft.block.Block",
    "net.minecraft.world.level.block.entity.BlockEntity" to "net.minecraft.block.entity.BlockEntity",
    "net.minecraft.world.level.block.state.BlockState" to "net.minecraft.block.state.BlockState",
    "net.minecraft.world.level.biome.Biome" to "net.minecraft.world.biome.Biome",
    "net.minecraft.client.renderer.GameRenderer" to "net.minecraft.client.render.GameRenderer",
)

stonecutter parameters {
    constants {
        match(current.project.substringAfterLast("-"), "fabric", "ornithe")
    }
    replacements {
        string(eval(current.version, ">= 1.21.11"), "identifier") {
            replace("ResourceLocation", "Identifier")
        }
        regex(eval(current.version, "< 1.21.11") && current.version != "1.8.9") {
            replace(
                "import net.minecraft.resources.Identifier(?!;)",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.Identifier",
            )
        }
        regex(eval(current.version, "= 1.8.9")) {
            legacyImports.forEach { (modern, legacy) ->
                val from = modern.replace(".", "\\.")
                val back = legacy.replace(".", "\\.")
                replace("import $from(?=\\s|\\*/)", "import $legacy", "import $back(?=\\s|\\*/)", "import $modern")
            }
            replace("net\\.minecraft\\.resources\\.Identifier", "net.minecraft.resource.Identifier", "net\\.minecraft\\.resource\\.Identifier", "net.minecraft.resources.Identifier")
            replace("\\bIdentifier\\.fromNamespaceAndPath\\(", "Identifier(", "\\bIdentifier\\(", "Identifier.fromNamespaceAndPath(")
        }
        string(eval(current.version, "= 1.8.9")) {
            replace("com.mojang.blaze3d.platform.InputConstants", "org.polyfrost.oneconfig.internal.legacy.InputConstants")
            replace("net.minecraft.network.chat.contents.TranslatableContents", "org.polyfrost.oneconfig.internal.legacy.chat.TranslatableContents")
            replace("net.minecraft.network.chat.contents.PlainTextContents", "org.polyfrost.oneconfig.internal.legacy.chat.PlainTextContents")
            replace("net.minecraft.network.chat.", "org.polyfrost.oneconfig.internal.legacy.chat.")
            replace("net.minecraft.util.FormattedCharSequence", "org.polyfrost.oneconfig.internal.legacy.chat.FormattedCharSequence")
            replace("net.minecraft.ChatFormatting", "org.polyfrost.oneconfig.internal.legacy.chat.ChatFormatting")
            replace("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback", "org.polyfrost.oneconfig.internal.legacy.command.ClientCommandRegistrationCallback")
            replace("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource", "org.polyfrost.oneconfig.internal.legacy.command.FabricClientCommandSource")
            replace("net.minecraft.client.User", "org.polyfrost.oneconfig.internal.legacy.User")
            replace("net.minecraft.client.resources.sounds.SimpleSoundInstance", "org.polyfrost.oneconfig.internal.legacy.SimpleSoundInstance")
            replace("net.minecraft.sounds.SoundEvents", "org.polyfrost.oneconfig.internal.legacy.SoundEvents")
            replace("net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screen.Screen")
            replace("net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.gui.screen.menu.TitleScreen")
            replace("net.minecraft.client.gui.screens.ConnectScreen", "net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen")
            replace("net.minecraft.client.gui.screens.DisconnectedScreen", "net.minecraft.client.gui.screen.menu.multiplayer.DisconnectedScreen")
            replace("net.minecraft.CrashReport", "net.minecraft.util.crash.CrashReport")
            replace("net.minecraft.server.Bootstrap", "net.minecraft.Bootstrap")
            replace("BlockPos.MutableBlockPos", "BlockPos.Mutable")
        }
    }
}

stonecutter tasks {
    order("runClient")
}
