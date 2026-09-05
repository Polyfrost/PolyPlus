package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServerVanillaRenderer;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.mixin.client.access.AbstractSelectionListAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinOnlineServerEntry {
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
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        if (!row.header() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
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
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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

    @Inject(method = "updateServerList", at = @At("HEAD"), cancellable = true)
    private void polyplus$neverPersistRemoteServer(CallbackInfo ci) {
        if (FeaturedServerRowRegistry.get(this) != null) ci.cancel();
    }

    @Inject(method = "getNarration", at = @At("HEAD"), cancellable = true)
    private void polyplus$headerNarration(CallbackInfoReturnable<Component> cir) {
        var row = FeaturedServerRowRegistry.get(this);
        if (row != null && row.header()) {
            cir.setReturnValue(Component.translatable("polyplus.featured.sponsored.category", row.expanded() ? "▼" : "▶"));
        }
    }

    private boolean handleSpecialClick(FeaturedServerRowRegistry.Row row, double mouseX, double mouseY) {
        if (row.header()) {
            ((FeaturedServerListAccess) row.list()).polyplus$toggleSponsoredServers();
            return true;
        }
        if (row.dismissHit(mouseX, mouseY)) {
            var campaign = row.server().getFeatured();
            if (campaign != null) FeaturedServers.dismissMultiplayer(campaign.getCampaignId());
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
