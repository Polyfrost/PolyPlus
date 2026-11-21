package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.polyfrost.polyplus.client.gui.screens.packs.PackFolders;
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
        polyplus$walk(this.folder, 0, result, new HashSet<>());
    }

    @Unique
    private void polyplus$walk(Path dir, int depth, Consumer<Pack> result, Set<Path> ancestors) {
        if (depth > PackFolders.MAX_DEPTH) return;
        Path real;
        try {
            real = dir.toRealPath();
        } catch (IOException e) {
            return;
        }
        if (!ancestors.add(real)) return;
        List<Path> groups = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                if (polyplus$forbidden(child)) continue;
                Pack.ResourcesSupplier resources = depth == 0 ? null : polyplus$detect(child);
                if (resources != null) {
                    String id = "file/" + this.folder.relativize(child).toString().replace('\\', '/');
                    PackLocationInfo info = new PackLocationInfo(id, Component.literal(child.getFileName().toString()), this.packSource, Optional.empty());
                    Pack pack = Pack.readMetaAndCreate(info, resources, this.packType, POLYPLUS$CONFIG);
                    if (pack != null) result.accept(pack);
                } else if (Files.isDirectory(child)
                    && !child.getFileName().toString().startsWith(".")
                    && !Files.isRegularFile(child.resolve("pack.mcmeta"))) {
                    groups.add(child);
                }
            }
        } catch (IOException ignored) {
        }
        for (Path group : groups) polyplus$walk(group, depth + 1, result, ancestors);
        ancestors.remove(real);
    }

    @Unique
    private Pack.ResourcesSupplier polyplus$detect(Path entry) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class);
            if (attributes.isDirectory()) {
                if (!Files.isRegularFile(entry.resolve("pack.mcmeta"))) return null;
                List<ForbiddenSymlinkInfo> issues = new ArrayList<>();
                this.validator.validateKnownDirectory(entry.toRealPath(), issues);
                return issues.isEmpty() ? new PathPackResources.PathResourcesSupplier(entry) : null;
            }
            if (attributes.isRegularFile() && entry.getFileName().toString().endsWith(".zip")) {
                return new FilePackResources.FileResourcesSupplier(entry);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    @Unique
    private boolean polyplus$forbidden(Path entry) {
        try {
            List<ForbiddenSymlinkInfo> issues = new ArrayList<>();
            Path link = entry;
            for (int hops = 0; Files.isSymbolicLink(link); hops++) {
                if (hops >= PackFolders.MAX_LINK_HOPS) return true;
                this.validator.validateSymlink(link, issues);
                if (!issues.isEmpty()) return true;
                link = link.resolveSibling(Files.readSymbolicLink(link));
            }
            return false;
        } catch (IOException e) {
            return true;
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
