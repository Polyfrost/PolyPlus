package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.resource.pack.ResourcePacks;
import org.polyfrost.polyplus.client.gui.screens.packs.PackFolders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(ResourcePacks.class)
public abstract class PackResourcePacksMixin {
    @Shadow @Final private static FileFilter FILE_FILTER;
    @Shadow @Final private File directory;

    @Inject(method = "discoverAvailablePacks", at = @At("RETURN"), cancellable = true)
    private void polyplus$discoverNestedPacks(CallbackInfoReturnable<List<File>> cir) {
        if (!this.directory.isDirectory()) return;
        List<File> found = new ArrayList<>(cir.getReturnValue());
        polyplus$walk(this.directory, 0, found);
        cir.setReturnValue(found);
    }

    @Unique
    private static void polyplus$walk(File dir, int depth, List<File> found) {
        if (depth >= PackFolders.MAX_DEPTH) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (!child.isDirectory() || child.getName().startsWith(".")) continue;
            if (Files.isSymbolicLink(child.toPath()) || new File(child, "pack.mcmeta").isFile()) continue;
            File[] packs = child.listFiles(FILE_FILTER);
            if (packs != null) {
                Arrays.sort(packs);
                found.addAll(Arrays.asList(packs));
            }
            polyplus$walk(child, depth + 1, found);
        }
    }
}
*///?}
