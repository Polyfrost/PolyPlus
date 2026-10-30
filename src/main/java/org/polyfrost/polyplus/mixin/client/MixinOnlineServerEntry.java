package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.featured.FeaturedServerEntryAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServerVanillaRenderer;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.polyfrost.polyplus.mixin.client.access.AbstractSelectionListAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if >= 1.21.10 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinOnlineServerEntry implements FeaturedServerEntryAccess {
    //? if >= 26.1 {
    private static final String RENDER = "extractContent";
    //?} elif >= 1.21.10 {
    /*private static final String RENDER = "renderContent";
    *///?} else {
    /*private static final String RENDER = "render";
    *///?}

    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private ServerData serverData;
    @Shadow @Final ServerSelectionList this$0;
    @Unique private FeaturedServerRowRegistry.Row polyplus$row;
    @Unique private boolean polyplus$swapped;

    @Override
    public FeaturedServerRowRegistry.Row polyplus$featuredRow() {
        return polyplus$row;
    }

    @Override
    public void polyplus$setFeaturedRow(FeaturedServerRowRegistry.Row row) {
        polyplus$row = row;
    }

    //? if < 1.21.10 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$beforeRender(
        GuiGraphics graphics, int index, int top, int left, int width, int height,
        int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        FeaturedServerVanillaRenderer.before(graphics, row, left, top, width, height, mouseX, mouseY);
        if (row.header()) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void polyplus$afterRender(
        GuiGraphics graphics, int index, int top, int left, int width, int height,
        int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null) FeaturedServerVanillaRenderer.after(graphics, row, mouseX, mouseY);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean polyplus$hideVanillaControls(boolean hovered) {
        var row = FeaturedServerRowRegistry.get(this);
        return (row == null || !row.header()) && hovered;
    }

    @ModifyExpressionValue(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I")
    )
    private int polyplus$includePromotedControls(int savedServerCount) {
        return controlServerCount(savedServerCount);
    }
    *///?} elif < 26.1 {
    /*@Inject(method = "renderContent", at = @At("HEAD"), cancellable = true)
    private void polyplus$beforeRender(
        GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var self = (ServerSelectionList.OnlineServerEntry) (Object) this;
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        FeaturedServerVanillaRenderer.before(graphics, row, self.getX(), self.getY(), self.getWidth(), self.getHeight(), mouseX, mouseY);
        if (row.header()) ci.cancel();
    }

    @Inject(method = "renderContent", at = @At("RETURN"))
    private void polyplus$afterRender(
        GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null) FeaturedServerVanillaRenderer.after(graphics, row, mouseX, mouseY);
    }

    @ModifyVariable(method = "renderContent", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean polyplus$hideVanillaControls(boolean hovered) {
        var row = FeaturedServerRowRegistry.get(this);
        return (row == null || !row.header()) && hovered;
    }

    @ModifyExpressionValue(
        method = "renderContent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I")
    )
    private int polyplus$includePromotedControls(int savedServerCount) {
        return controlServerCount(savedServerCount);
    }
    *///?} else {
    @Inject(method = "extractContent", at = @At("HEAD"), cancellable = true)
    private void polyplus$beforeRender(
        GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var self = (ServerSelectionList.OnlineServerEntry) (Object) this;
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        FeaturedServerVanillaRenderer.before(graphics, row, self.getX(), self.getY(), self.getWidth(), self.getHeight(), mouseX, mouseY);
        if (row.header()) ci.cancel();
    }

    @Inject(method = "extractContent", at = @At("RETURN"))
    private void polyplus$afterRender(
        GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null) FeaturedServerVanillaRenderer.after(graphics, row, mouseX, mouseY);
    }

    @ModifyVariable(method = "extractContent", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean polyplus$hideVanillaControls(boolean hovered) {
        var row = FeaturedServerRowRegistry.get(this);
        return (row == null || !row.header()) && hovered;
    }

    @ModifyExpressionValue(
        method = "extractContent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I")
    )
    private int polyplus$includePromotedControls(int savedServerCount) {
        return controlServerCount(savedServerCount);
    }
    //?}

    //? if < 1.21.10 {
    /*@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            cir.setReturnValue(false);
            return;
        }
        if (handleSpecialClick(row, mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }
        row.list().setSelected((ServerSelectionList.OnlineServerEntry) (Object) this);
        if (row.registerClick(System.currentTimeMillis())) row.screen().joinSelectedServer();
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void polyplus$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        if (!row.header() && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            row.screen().joinSelectedServer();
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
    *///?} else {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            cir.setReturnValue(false);
            return;
        }
        if (handleSpecialClick(row, event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
        }
        row.list().setSelected((ServerSelectionList.OnlineServerEntry) (Object) this);
        if (doubleClick) row.screen().join(row.data());
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void polyplus$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        if (!row.header() && event.isSelection()) {
            row.screen().join(row.data());
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
    //?}

    // With folders the move arrows work inside the server's own folder: positions and the count
    // are counted within it and mapped back to the saved list when swapping.
    // A sorted list has no order to move in, so it reports one server and the arrows go away
    @WrapOperation(
        method = {RENDER, "keyPressed", "mouseClicked"},
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I")
    )
    private int polyplus$folderIndex(List<?> children, Object entry, Operation<Integer> original) {
        if (FeaturedServerRowRegistry.get(this) != null) return original.call(children, entry);
        if (ServerFolders.sort() != ServerFolders.Sort.CUSTOM) return 0;
        if (!ServerFolders.hasAny()) return original.call(children, entry);
        var group = polyplus$folderGroup();
        for (int index = 0; index < group.size(); index++) {
            if (screen.getServers().get(group.get(index)) == serverData) return index;
        }
        return original.call(children, entry);
    }

    @WrapOperation(
        method = {RENDER, "keyPressed", "mouseClicked"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I")
    )
    private int polyplus$folderSize(ServerList servers, Operation<Integer> original) {
        if (FeaturedServerRowRegistry.get(this) != null) return original.call(servers);
        if (ServerFolders.sort() != ServerFolders.Sort.CUSTOM) return 1;
        return ServerFolders.hasAny() ? polyplus$folderGroup().size() : original.call(servers);
    }

    // Vanilla also swaps the rows on screen by position, which the headers throw off, so the
    // list's own rows are put back in saved order and laid out again
    @WrapOperation(
        method = "swap",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;swap(II)V")
    )
    private void polyplus$swapInFolder(ServerList servers, int from, int to, Operation<Void> original) {
        if (!ServerFolders.hasAny() || ServerFolders.sort() != ServerFolders.Sort.CUSTOM) {
            original.call(servers, from, to);
            return;
        }
        var group = polyplus$folderGroup();
        polyplus$swapped = true;
        original.call(servers, group.get(from), group.get(to));
    }

    @Inject(method = "swap", at = @At("TAIL"))
    private void polyplus$relayoutAfterSwap(int from, int to, CallbackInfo ci) {
        if (!polyplus$swapped) return;
        polyplus$swapped = false;
        var list = this.this$0;
        ((FeaturedServerListAccess) list).polyplus$followSavedOrder();
        list.setSelected((ServerSelectionList.OnlineServerEntry) (Object) this);
    }

    /** Saved-list positions of the servers in this server's folder, in saved order */
    @Unique
    private List<Integer> polyplus$folderGroup() {
        var servers = screen.getServers();
        var folder = ServerFolders.folderOf(serverData.ip);
        var group = new ArrayList<Integer>();
        for (int index = 0; index < servers.size(); index++) {
            if (Objects.equals(ServerFolders.folderOf(servers.get(index).ip), folder)) group.add(index);
        }
        return group;
    }

    @Inject(method = "updateServerList", at = @At("HEAD"), cancellable = true)
    private void polyplus$neverPersistRemoteServer(CallbackInfo ci) {
        if (FeaturedServerRowRegistry.get(this) != null) ci.cancel();
    }

    @Inject(method = "getNarration", at = @At("HEAD"), cancellable = true)
    private void polyplus$headerNarration(CallbackInfoReturnable<Component> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null && row.header()) {
            cir.setReturnValue(row.folder() == null
                ? Component.translatable("polyplus.featured.sponsored")
                : Component.literal(row.folder()));
        }
    }

    private boolean handleSpecialClick(FeaturedServerRowRegistry.Row row, double mouseX, double mouseY) {
        if (row.header()) {
            if (row.folder() != null) {
                int move = FeaturedServerVanillaRenderer.folderArrowHit(row, mouseX);
                if (move != 0) ServerFolders.moveFolder(row.folder(), move, FeaturedServerRowRegistry.folders(row.list()));
                else ServerFolders.toggle(row.folder());
                ((FeaturedServerListAccess) row.list()).polyplus$rebuildFeaturedServers();
            }
            return true;
        }
        if (row.dismissHit(mouseX, mouseY)) {
            var campaign = row.server().getFeatured();
            if (campaign != null) {
                if (row.promoted()) FeaturedServers.dismissMultiplayer(campaign.getCampaignId());
                else FeaturedServers.restoreMultiplayer(campaign.getCampaignId());
            }
            ((FeaturedServerListAccess) row.list()).polyplus$rebuildFeaturedServers();
            return true;
        }
        int iconX = row.x();
        int iconY = row.y();
        //? if >= 1.21.10 {
        iconX += 2;
        iconY += 2;
        //?}
        int relativeX = (int) Math.floor(mouseX) - iconX;
        int relativeY = (int) Math.floor(mouseY) - iconY;
        if (relativeX < 0 || relativeX >= 32 || relativeY < 0 || relativeY >= 32) return false;

        var access = (FeaturedServerListAccess) row.list();
        if (relativeX >= 16) {
            access.polyplus$saveFeaturedServer(row, 0);
            //? if < 1.21.10 {
            /*row.screen().joinSelectedServer();
            *///?} else {
            row.screen().join(row.data());
            //?}
            return true;
        }

        int index = ((AbstractSelectionListAccessor) row.list()).polyplus$children().indexOf(row.entry());
        int savedServerCount = controlServerCount(row.screen().getServers().size());
        if (relativeY < 16 && index > 0) {
            access.polyplus$saveFeaturedServer(row, -1);
            return true;
        }
        if (relativeY >= 16 && index < savedServerCount - 1) {
            access.polyplus$saveFeaturedServer(row, 1);
            return true;
        }
        return false;
    }

    private int controlServerCount(int savedServerCount) {
        var row = FeaturedServerRowRegistry.get(this);
        return row != null && row.promoted()
            ? savedServerCount + FeaturedServerRowRegistry.promotedCount(row.list())
            : savedServerCount;
    }
}
//?} else {
/*import net.minecraft.client.gui.widget.ServerListEntryWidget;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServerVanillaRenderer;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerListEntryWidget.class)
public abstract class MixinOnlineServerEntry {
    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$beforeRender(
        int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null) FeaturedServerVanillaRenderer.before(row, x, y, width, height);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void polyplus$afterRender(
        int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered, CallbackInfo ci
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null) FeaturedServerVanillaRenderer.after(row, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$mouseClicked(
        int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row == null) return;
        var access = (FeaturedServerListAccess) row.list();
        if (row.dismissHit(mouseX, mouseY)) {
            var campaign = row.server().getFeatured();
            if (campaign != null) {
                if (row.promoted()) FeaturedServers.dismissMultiplayer(campaign.getCampaignId(), false);
                else FeaturedServers.restoreMultiplayer(campaign.getCampaignId());
            }
            access.polyplus$rebuildFeaturedServers();
            cir.setReturnValue(true);
            return;
        }
        if (entryMouseX > 16 && entryMouseX < 32) {
            access.polyplus$saveFeaturedServer(row);
            row.screen().connect();
            cir.setReturnValue(true);
        }
    }
}
*///?}
