package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private Button editButton;
    @Shadow private Button selectButton;
    @Shadow private Button deleteButton;

    private long polyplus$featuredRevision = Long.MIN_VALUE;

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$initializeFeaturedServers(CallbackInfo ci) {
        FeaturedServers.warmUp();
        polyplus$refreshFeaturedServers();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$refreshFeaturedServersWhenChanged(CallbackInfo ci) {
        if (FeaturedServers.revision() != polyplus$featuredRevision) polyplus$refreshFeaturedServers();
    }

    @Inject(method = "onSelectedChange", at = @At("RETURN"))
    private void polyplus$disableRemoteRowEditing(CallbackInfo ci) {
        if (serverSelectionList == null) return;
        var row = FeaturedServerRowRegistry.get(serverSelectionList.getSelected());
        if (row == null) return;
        if (editButton != null) editButton.active = false;
        if (deleteButton != null) deleteButton.active = false;
        if (selectButton != null) selectButton.active = !row.header();
    }

    private void polyplus$refreshFeaturedServers() {
        polyplus$featuredRevision = FeaturedServers.revision();
        if (serverSelectionList instanceof FeaturedServerListAccess access) {
            access.polyplus$rebuildFeaturedServers();
        }
    }
}
