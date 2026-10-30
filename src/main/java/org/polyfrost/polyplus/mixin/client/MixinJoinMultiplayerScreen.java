package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Comparator;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private Button editButton;
    //? if >= 26.3 {
    @Shadow private Button joinButton;
    //?} else {
    /*@Shadow private Button selectButton;
    *///?}
    @Shadow private Button deleteButton;

    private static final int BUTTON_ROW_WIDTH = 308;
    private static final int BUTTON_GAP = 4;
    private static final int VANILLA_BUTTON_WIDTH = 74;
    private static final int LABEL_PADDING = 8;
    private static final int SCREEN_MARGIN = 10;

    private long polyplus$featuredRevision = Long.MIN_VALUE;
    private Button polyplus$sortButton;

    protected MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$initializeFeaturedServers(CallbackInfo ci) {
        FeaturedServers.warmUp();
        polyplus$refreshFeaturedServers();

        polyplus$sortButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            ServerFolders.cycleSort();
            polyplus$labelSortButton();
            polyplus$fitSortButton();
            polyplus$refreshFeaturedServers();
        }).build());
        polyplus$labelSortButton();
        polyplus$fitSortButton();
    }

    //? if >= 1.21.10 {
    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void polyplus$refitSortButton(CallbackInfo ci) {
        polyplus$fitSortButton();
    }
    //?}

    // Squeezes the sort button into the lower row, before Back. The row stays as wide as vanilla's
    // unless a label needs more room, which some languages do, and then grows up to vanilla's button width
    private void polyplus$fitSortButton() {
        if (polyplus$sortButton == null || editButton == null) return;
        var row = new ArrayList<AbstractWidget>();
        for (var child : children()) {
            if (child instanceof Button button && button != polyplus$sortButton && button.getY() == editButton.getY()) row.add(button);
        }
        if (row.isEmpty()) return;
        row.sort(Comparator.comparingInt(AbstractWidget::getX));
        row.add(row.size() - 1, polyplus$sortButton);

        int labelWidth = 0;
        for (var button : row) labelWidth = Math.max(labelWidth, this.font.width(button.getMessage()) + LABEL_PADDING);
        int gaps = BUTTON_GAP * (row.size() - 1);
        int width = Math.max((BUTTON_ROW_WIDTH - gaps) / row.size(), Math.min(labelWidth, VANILLA_BUTTON_WIDTH));
        width = Math.min(width, (this.width - 2 * SCREEN_MARGIN - gaps) / row.size());
        int x = this.width / 2 - (width * row.size() + gaps) / 2;
        for (var button : row) {
            button.setWidth(width);
            button.setPosition(x, editButton.getY());
            x += width + BUTTON_GAP;
        }
    }

    // The button only fits the order itself, the tooltip says what it is
    private void polyplus$labelSortButton() {
        var mode = switch (ServerFolders.sort()) {
            case CUSTOM -> Component.translatable("polyplus.serverSort.custom");
            case A_Z -> Component.literal("A-Z");
            case Z_A -> Component.literal("Z-A");
        };
        polyplus$sortButton.setMessage(Component.literal("\u21C5 ").append(mode));
        polyplus$sortButton.setTooltip(Tooltip.create(Component.translatable("polyplus.serverSort", mode)));
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
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerServerListWidget;
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
