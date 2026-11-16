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
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Comparator;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
    @Unique private static final int BUTTON_ROW_WIDTH = 308;
    @Unique private static final int BUTTON_GAP = 4;
    @Unique private static final int VANILLA_BUTTON_WIDTH = 74;
    @Unique private static final int LABEL_PADDING = 8;
    @Unique private static final int SCREEN_MARGIN = 10;

    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private Button editButton;
    //? if >= 26.3 {
    @Shadow private Button joinButton;
    //?} else {
    /*@Shadow private Button selectButton;
    *///?}
    @Shadow private Button deleteButton;

    private long polyplus$featuredRevision = Long.MIN_VALUE;
    @Unique private int polyplus$foldersRevision = Integer.MIN_VALUE;
    @Unique private Button polyplus$sortButton;

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

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$refreshFeaturedServersWhenChanged(CallbackInfo ci) {
        if (FeaturedServers.revision() != polyplus$featuredRevision || ServerFolders.revision() != polyplus$foldersRevision) {
            polyplus$refreshFeaturedServers();
            if (polyplus$sortButton != null) {
                polyplus$labelSortButton();
                polyplus$fitSortButton();
            }
        }
    }

    @Inject(method = "deleteCallback", at = @At("HEAD"))
    private void polyplus$demoteDeletedFeaturedServer(boolean result, CallbackInfo ci) {
        if (!result || serverSelectionList == null) return;
        if (serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry) {
            FeaturedServers.dismissMultiplayerByAddress(entry.getServerData().ip);
            if (FeaturedServerRowRegistry.get(entry) == null) polyplus$forgetFolder(entry);
        }
    }

    @Unique
    private void polyplus$forgetFolder(ServerSelectionList.OnlineServerEntry entry) {
        var data = entry.getServerData();
        var address = FeaturedServerModelsKt.normalizeServerAddress(data.ip);
        var servers = ((JoinMultiplayerScreen) (Object) this).getServers();
        for (int index = 0; index < servers.size(); index++) {
            var other = servers.get(index);
            if (other != data && FeaturedServerModelsKt.normalizeServerAddress(other.ip).equals(address)) return;
        }
        ServerFolders.forget(data.ip);
    }

    @Unique
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

    @Unique
    private void polyplus$labelSortButton() {
        var mode = switch (ServerFolders.sort()) {
            case CUSTOM -> Component.translatable("polyplus.serverSort.custom");
            case A_Z -> Component.literal("A-Z");
            case Z_A -> Component.literal("Z-A");
        };
        polyplus$sortButton.setMessage(Component.literal("\u21C5 ").append(mode));
        polyplus$sortButton.setTooltip(Tooltip.create(Component.translatable("polyplus.serverSort", mode)));
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
        polyplus$foldersRevision = ServerFolders.revision();
        if (serverSelectionList instanceof FeaturedServerListAccess access) {
            access.polyplus$rebuildFeaturedServers();
        }
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Mixin(MultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
    @Unique private static final int SORT_BUTTON_ID = 0x50505301;
    @Unique private static final int BUTTON_ROW_WIDTH = 308;
    @Unique private static final int BUTTON_GAP = 4;
    @Unique private static final int VANILLA_BUTTON_WIDTH = 74;
    @Unique private static final int LABEL_PADDING = 8;
    @Unique private static final int SCREEN_MARGIN = 10;

    @Shadow private MultiplayerServerListWidget serverList;
    @Shadow private ServerList servers;
    @Shadow private ButtonWidget editButton;
    @Shadow private ButtonWidget joinButton;
    @Shadow private ButtonWidget deleteButton;
    @Shadow private boolean deleteServerConfirmationDialogOpen;

    @Shadow public abstract void moveToServer(int index);

    @Unique
    private long polyplus$featuredRevision = Long.MIN_VALUE;

    @Unique
    private int polyplus$foldersRevision = Integer.MIN_VALUE;

    @Unique
    private ButtonWidget polyplus$sortButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$initializeFeaturedServers(CallbackInfo ci) {
        FeaturedServers.warmUp();
        polyplus$refreshFeaturedServers();
    }

    @Inject(method = "addButtons", at = @At("RETURN"))
    private void polyplus$addSortButton(CallbackInfo ci) {
        if (editButton == null) return;
        polyplus$sortButton = new ButtonWidget(SORT_BUTTON_ID, 0, editButton.y, VANILLA_BUTTON_WIDTH, 20, "");
        buttons.add(polyplus$sortButton);
        polyplus$labelSortButton();
        polyplus$fitSortButton();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$refreshFeaturedServersWhenChanged(CallbackInfo ci) {
        if (FeaturedServers.revision() != polyplus$featuredRevision || ServerFolders.revision() != polyplus$foldersRevision) {
            polyplus$refreshFeaturedServers();
            if (polyplus$sortButton != null) {
                polyplus$labelSortButton();
                polyplus$fitSortButton();
            }
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$cycleSort(ButtonWidget button, CallbackInfo ci) {
        if (button == null || button != polyplus$sortButton) return;
        ci.cancel();
        if (!button.active) return;
        ServerFolders.cycleSort();
        polyplus$labelSortButton();
        polyplus$fitSortButton();
        polyplus$refreshFeaturedServers();
    }

    @Unique
    private void polyplus$labelSortButton() {
        String mode = switch (ServerFolders.sort()) {
            case CUSTOM -> I18n.translate("polyplus.serverSort.custom");
            case A_Z -> "A-Z";
            case Z_A -> "Z-A";
        };
        polyplus$sortButton.message = I18n.translate("polyplus.serverSort", mode);
    }

    @Unique
    private void polyplus$fitSortButton() {
        if (polyplus$sortButton == null || editButton == null) return;
        var row = new ArrayList<ButtonWidget>();
        for (var button : buttons) {
            if (button != polyplus$sortButton && button.y == editButton.y) row.add(button);
        }
        if (row.isEmpty()) return;
        row.sort(Comparator.comparingInt(button -> button.x));
        row.add(row.size() - 1, polyplus$sortButton);

        int labelWidth = 0;
        for (var button : row) labelWidth = Math.max(labelWidth, textRenderer.getWidth(button.message) + LABEL_PADDING);
        int gaps = BUTTON_GAP * (row.size() - 1);
        int width = Math.max((BUTTON_ROW_WIDTH - gaps) / row.size(), Math.min(labelWidth, VANILLA_BUTTON_WIDTH));
        width = Math.min(width, (this.width - 2 * SCREEN_MARGIN - gaps) / row.size());
        int x = this.width / 2 - (width * row.size() + gaps) / 2;
        for (var button : row) {
            button.setWidth(width);
            button.x = x;
            button.y = editButton.y;
            x += width + BUTTON_GAP;
        }
    }

    @Inject(method = "confirmResult", at = @At("HEAD"))
    private void polyplus$demoteDeletedFeaturedServer(boolean result, int id, CallbackInfo ci) {
        if (!deleteServerConfirmationDialogOpen || !result) return;
        if (polyplus$selected() instanceof ServerListEntryWidget entry) {
            FeaturedServers.dismissMultiplayerByAddress(entry.fetchServer().ip);
            if (FeaturedServerRowRegistry.get(entry) == null) polyplus$forgetFolder(entry.fetchServer());
        }
    }

    @WrapOperation(
        method = "confirmResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/ServerList;remove(I)V")
    )
    private void polyplus$removeShownServer(ServerList list, int index, Operation<Void> original) {
        if (!(polyplus$selected() instanceof ServerListEntryWidget entry)) return;
        int stored = polyplus$storedIndex(entry.fetchServer());
        if (stored >= 0) original.call(list, stored);
    }

    @Unique
    private void polyplus$forgetFolder(ServerListEntry data) {
        var address = FeaturedServerModelsKt.normalizeServerAddress(data.ip);
        for (int index = 0; index < servers.size(); index++) {
            var other = servers.get(index);
            if (other != data && FeaturedServerModelsKt.normalizeServerAddress(other.ip).equals(address)) return;
        }
        ServerFolders.forget(data.ip);
    }

    @Inject(method = "moveToServer", at = @At("RETURN"))
    private void polyplus$disableRemoteRowEditing(int index, CallbackInfo ci) {
        var selected = polyplus$selected();
        if (selected instanceof FeaturedServerRowRegistry.Header || selected instanceof FeaturedServerRowRegistry.FolderHeader) {
            joinButton.active = false;
            return;
        }
        if (FeaturedServerRowRegistry.get(selected) == null) return;
        editButton.active = false;
        deleteButton.active = false;
    }

    @Inject(method = "canMoveUp", at = @At("HEAD"), cancellable = true)
    private void polyplus$canMoveUpInFolder(ServerListEntryWidget entry, int index, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(polyplus$canMove(entry, -1));
    }

    @Inject(method = "canMoveDown", at = @At("HEAD"), cancellable = true)
    private void polyplus$canMoveDownInFolder(ServerListEntryWidget entry, int index, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(polyplus$canMove(entry, 1));
    }

    @Inject(method = "moveUp", at = @At("HEAD"), cancellable = true)
    private void polyplus$moveUpInFolder(ServerListEntryWidget entry, int index, boolean moveToTop, CallbackInfo ci) {
        ci.cancel();
        polyplus$move(entry, -1, moveToTop);
    }

    @Inject(method = "moveDown", at = @At("HEAD"), cancellable = true)
    private void polyplus$moveDownInFolder(ServerListEntryWidget entry, int index, boolean moveToBottom, CallbackInfo ci) {
        ci.cancel();
        polyplus$move(entry, 1, moveToBottom);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void polyplus$moveWithKeys(char chr, int key, CallbackInfo ci) {
        if ((key != Keyboard.KEY_UP && key != Keyboard.KEY_DOWN) || !Screen.isShiftDown()) return;
        if (serverList == null || serverList.getCurrentServerIndex() < 0) return;
        ci.cancel();
        if (!(polyplus$selected() instanceof ServerListEntryWidget entry)) return;
        int direction = key == Keyboard.KEY_UP ? -1 : 1;
        if (!polyplus$canMove(entry, direction)) return;
        polyplus$move(entry, direction, false);
        serverList.scroll(direction * serverList.getEntryHeight());
    }

    @Unique
    private boolean polyplus$canMove(ServerListEntryWidget entry, int direction) {
        if (FeaturedServerRowRegistry.get(entry) != null || ServerFolders.sort() != ServerFolders.Sort.CUSTOM) return false;
        var group = polyplus$folderGroup(entry.fetchServer());
        int position = polyplus$position(group, entry.fetchServer());
        int target = position + direction;
        return position >= 0 && target >= 0 && target < group.size();
    }

    @Unique
    private void polyplus$move(ServerListEntryWidget entry, int direction, boolean toEnd) {
        if (!polyplus$canMove(entry, direction)) return;
        var data = entry.fetchServer();
        var group = polyplus$folderGroup(data);
        int position = polyplus$position(group, data);
        int target = toEnd ? (direction < 0 ? 0 : group.size() - 1) : position + direction;
        servers.swap(group.get(position), group.get(target));
        serverList.setServers(servers);
        moveToServer(serverList.getCurrentServerIndex());
    }

    @Unique
    private List<Integer> polyplus$folderGroup(ServerListEntry data) {
        var folder = ServerFolders.folderOf(data.ip);
        var group = new ArrayList<Integer>();
        for (int index = 0; index < servers.size(); index++) {
            if (Objects.equals(ServerFolders.folderOf(servers.get(index).ip), folder)) group.add(index);
        }
        return group;
    }

    @Unique
    private int polyplus$position(List<Integer> group, ServerListEntry data) {
        for (int position = 0; position < group.size(); position++) {
            if (servers.get(group.get(position)) == data) return position;
        }
        return -1;
    }

    @Unique
    private int polyplus$storedIndex(ServerListEntry data) {
        for (int index = 0; index < servers.size(); index++) {
            if (servers.get(index) == data) return index;
        }
        return -1;
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
        polyplus$foldersRevision = ServerFolders.revision();
        if (serverList instanceof FeaturedServerListAccess access) access.polyplus$rebuildFeaturedServers();
        if (serverList != null && joinButton != null) moveToServer(serverList.getCurrentServerIndex());
    }
}
*///?}
