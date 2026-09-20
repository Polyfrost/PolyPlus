package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
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
    //? if >= 26.3 {
    @Shadow private Button joinButton;
    //?} else {
    /*@Shadow private Button selectButton;
    *///?}
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

    @Inject(method = "deleteCallback", at = @At("HEAD"))
    private void polyplus$demoteDeletedFeaturedServer(boolean result, CallbackInfo ci) {
        if (!result || serverSelectionList == null) return;
        if (serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry) {
            FeaturedServers.dismissMultiplayerByAddress(entry.getServerData().ip);
        }
    }

    @Inject(method = "onSelectedChange", at = @At("RETURN"))
    private void polyplus$disableRemoteRowEditing(CallbackInfo ci) {
        if (serverSelectionList == null) return;
        var row = FeaturedServerRowRegistry.get(serverSelectionList.getSelected());
        if (row == null) return;
        if (editButton != null) editButton.active = false;
        if (deleteButton != null) deleteButton.active = !row.header();
        //? if >= 26.3 {
        if (joinButton != null) joinButton.active = !row.header();
        //?} else {
        /*if (selectButton != null) selectButton.active = !row.header();
        *///?}
    }

    private void polyplus$refreshFeaturedServers() {
        polyplus$featuredRevision = FeaturedServers.revision();
        if (serverSelectionList instanceof FeaturedServerListAccess access) {
            access.polyplus$rebuildFeaturedServers();
        }
    }
}
//?} else {
/*import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.input.Keyboard;

@Mixin(MultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen {
    @Shadow private MultiplayerServerListWidget serverList;
    @Shadow private ButtonWidget editButton;
    @Shadow private ButtonWidget deleteButton;
    @Shadow private boolean deleteServerConfirmationDialogOpen;

    @Unique
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

    @Inject(method = "confirmResult", at = @At("HEAD"))
    private void polyplus$demoteDeletedFeaturedServer(boolean result, int id, CallbackInfo ci) {
        if (!deleteServerConfirmationDialogOpen || !result) return;
        if (polyplus$selected() instanceof ServerListEntryWidget entry) {
            FeaturedServers.dismissMultiplayerByAddress(entry.fetchServer().ip);
        }
    }

    @Inject(method = "moveToServer", at = @At("RETURN"))
    private void polyplus$disableRemoteRowEditing(int index, CallbackInfo ci) {
        if (FeaturedServerRowRegistry.get(polyplus$selected()) == null) return;
        editButton.active = false;
        deleteButton.active = false;
    }

    @Inject(method = "canMoveUp", at = @At("HEAD"), cancellable = true)
    private void polyplus$lockRemoteRowUp(ServerListEntryWidget entry, int index, CallbackInfoReturnable<Boolean> cir) {
        if (FeaturedServerRowRegistry.get(entry) != null) cir.setReturnValue(false);
    }

    @Inject(method = "canMoveDown", at = @At("HEAD"), cancellable = true)
    private void polyplus$lockRemoteRowDown(ServerListEntryWidget entry, int index, CallbackInfoReturnable<Boolean> cir) {
        if (FeaturedServerRowRegistry.get(entry) != null) cir.setReturnValue(false);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void polyplus$lockRemoteRowKeys(char chr, int key, CallbackInfo ci) {
        if ((key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) && Screen.isShiftDown()
            && FeaturedServerRowRegistry.get(polyplus$selected()) != null) {
            ci.cancel();
        }
    }

    @Unique
    private EntryListWidget.Entry polyplus$selected() {
        if (serverList == null) return null;
        int index = serverList.getCurrentServerIndex();
        return index < 0 ? null : serverList.getEntry(index);
    }

    @Unique
    private void polyplus$refreshFeaturedServers() {
        polyplus$featuredRevision = FeaturedServers.revision();
        if (serverList instanceof FeaturedServerListAccess access) access.polyplus$rebuildFeaturedServers();
    }
}
*///?}
