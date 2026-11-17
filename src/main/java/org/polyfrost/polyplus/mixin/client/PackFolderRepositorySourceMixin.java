package org.polyfrost.polyplus.mixin.client;

//? if >= 26.3 {
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FolderRepositorySource.class)
public abstract class PackFolderRepositorySourceMixin {
    @Unique
    private static final int POLYPLUS$MAX_DEPTH = 6;

    @Unique
    private static final PackSelectionConfig POLYPLUS$CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);

    @Unique
    private static Boolean polyplus$deferred;

    @Shadow @Final private Path folder;
    @Shadow @Final private PackType packType;
    @Shadow @Final private PackSource packSource;
    @Shadow @Final private DirectoryValidator validator;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void polyplus$loadNestedPacks(Consumer<Pack> result, CallbackInfo ci) {
        if (this.packType != PackType.CLIENT_RESOURCES || polyplus$deferToOthers()) return;
        polyplus$walk(this.folder, 0, result);
    }

    @Unique
    private void polyplus$walk(Path dir, int depth, Consumer<Pack> result) {
        if (depth > POLYPLUS$MAX_DEPTH) return;
        Set<Path> packs = new HashSet<>();
        try {
            FolderRepositorySource.discoverPacks(dir, this.validator, (content, resources) -> {
                packs.add(content);
                if (depth == 0) return;
                String id = "file/" + this.folder.relativize(content).toString().replace('\\', '/');
                PackLocationInfo info = new PackLocationInfo(id, Component.literal(content.getFileName().toString()), this.packSource, Optional.empty());
                Pack pack = Pack.readMetaAndCreate(info, resources, this.packType, POLYPLUS$CONFIG);
                if (pack != null) result.accept(pack);
            });
        } catch (IOException ignored) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                if (packs.contains(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) continue;
                if (child.getFileName().toString().startsWith(".")) continue;
                polyplus$walk(child, depth + 1, result);
            }
        } catch (IOException ignored) {
        }
    }

    @Unique
    private static boolean polyplus$deferToOthers() {
        Boolean cached = polyplus$deferred;
        if (cached != null) return cached;
        FabricLoader loader = FabricLoader.getInstance();
        boolean deferred = loader.isModLoaded("packed_packs") || loader.isModLoaded("packmanager") || loader.isModLoaded("pack_manager") || loader.isModLoaded("pack-manager");
        if (!deferred) {
            try {
                Class.forName("dev.microcontrollers.packmanager.gui.FolderedPackSelectionScreen", false, FolderRepositorySource.class.getClassLoader());
                deferred = true;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        polyplus$deferred = deferred;
        return deferred;
    }
}
//?}
