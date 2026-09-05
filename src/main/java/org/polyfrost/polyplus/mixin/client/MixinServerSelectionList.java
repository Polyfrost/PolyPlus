package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.polyfrost.polyplus.client.featured.FeaturedServer;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.mixin.client.access.AbstractSelectionListAccessor;
import org.polyfrost.polyplus.mixin.client.access.OnlineServerEntryInvoker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class MixinServerSelectionList implements FeaturedServerListAccess {
    private static final int POLYPLUS$SPONSORED_HEADER_HEIGHT = 18;

    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private List<ServerSelectionList.OnlineServerEntry> onlineServers;
    @Shadow @Final private ServerSelectionList.Entry lanHeader;
    @Shadow @Final private List<ServerSelectionList.NetworkServerEntry> networkServers;

    private boolean polyplus$sponsoredExpanded = true;

    @Inject(method = "refreshEntries", at = @At("HEAD"))
    private void polyplus$releaseFeaturedEntries(CallbackInfo ci) {
        FeaturedServerRowRegistry.release((ServerSelectionList) (Object) this);
    }

    @Inject(method = "refreshEntries", at = @At("RETURN"))
    private void polyplus$appendFeaturedEntries(CallbackInfo ci) {
        polyplus$rebuildFeaturedServers();
    }

    @Override
    public void polyplus$toggleSponsoredServers() {
        polyplus$sponsoredExpanded = !polyplus$sponsoredExpanded;
        polyplus$rebuildFeaturedServers();
    }

    @Override
    public void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row, int moveDirection) {
        var servers = screen.getServers();
        var address = FeaturedServerModelsKt.normalizeServerAddress(row.data().ip);
        boolean alreadySaved = false;
        for (int index = 0; index < servers.size(); index++) {
            if (FeaturedServerModelsKt.normalizeServerAddress(servers.get(index).ip).equals(address)) {
                alreadySaved = true;
                break;
            }
        }

        if (!alreadySaved) {
            int previousSize = servers.size();
            servers.add(row.data(), false);
            int targetIndex = row.promoted() ? 0 : previousSize;
            if (moveDirection > 0 && targetIndex < previousSize) targetIndex++;
            if (moveDirection < 0 && targetIndex > 0) targetIndex--;
            for (int index = previousSize; index > targetIndex; index--) {
                servers.swap(index, index - 1);
            }
            servers.save();
        }

        var self = (ServerSelectionList) (Object) this;
        self.updateOnlineServers(servers);
        for (var entry : onlineServers) {
            if (FeaturedServerModelsKt.normalizeServerAddress(entry.getServerData().ip).equals(address)) {
                self.setSelected(entry);
                break;
            }
        }
    }

    @Override
    public void polyplus$rebuildFeaturedServers() {
        var self = (ServerSelectionList) (Object) this;
        if (FeaturedServerRowRegistry.get(self.getSelected()) != null) self.setSelected(null);
        FeaturedServerRowRegistry.release(self);

        long now = System.currentTimeMillis();
        var snapshot = FeaturedServers.snapshot();
        var promoted = snapshot.featuredServers(now);
        var sponsored = snapshot.sponsoredServers(now);
        var savedByAddress = new HashMap<String, ServerData>();
        for (var entry : onlineServers) {
            savedByAddress.putIfAbsent(
                FeaturedServerModelsKt.normalizeServerAddress(entry.getServerData().ip),
                entry.getServerData()
            );
        }
        mutableChildren().clear();
        for (var server : promoted) {
            if (!savedByAddress.containsKey(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))) {
                addRemote(server, true);
            }
        }
        for (var entry : onlineServers) add(entry);
        add(lanHeader);
        for (var entry : networkServers) add(entry);

        boolean hasUnsavedSponsored = sponsored.stream().anyMatch(server ->
            !savedByAddress.containsKey(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))
        );
        if (hasUnsavedSponsored) {
            addHeader();
            if (polyplus$sponsoredExpanded) {
                for (var server : sponsored) {
                    if (!savedByAddress.containsKey(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))) {
                        addRemote(server, false);
                    }
                }
            }
        }
        if (self.getSelected() != null && !mutableChildren().contains(self.getSelected())) self.setSelected(null);
    }

    private void addRemote(FeaturedServer server, boolean promoted) {
        var data = new ServerData(server.getName(), server.getAddress(), ServerData.Type.OTHER);
        var self = (ServerSelectionList) (Object) this;
        var entry = OnlineServerEntryInvoker.polyplus$create(self, screen, data);
        FeaturedServerRowRegistry.register(entry, self, screen, data, server, promoted, false, false);
        add(entry);
    }

    private void addHeader() {
        var data = new ServerData("Sponsored", "0.0.0.0", ServerData.Type.OTHER);
        var self = (ServerSelectionList) (Object) this;
        var entry = OnlineServerEntryInvoker.polyplus$create(self, screen, data);
        FeaturedServerRowRegistry.register(entry, self, screen, data, null, false, true, polyplus$sponsoredExpanded);
        add(entry, true);
    }

    private void add(ServerSelectionList.Entry entry) {
        add(entry, false);
    }

    private void add(ServerSelectionList.Entry entry, boolean sponsoredHeader) {
        var self = (ServerSelectionList) (Object) this;
        //? if >= 1.21.10 {
        entry.setX(self.getRowLeft());
        entry.setWidth(self.getRowWidth());
        entry.setY(self.getNextY());
        entry.setHeight(
            sponsoredHeader
                ? POLYPLUS$SPONSORED_HEADER_HEIGHT
                : ((AbstractSelectionListAccessor) this).polyplus$entryHeight()
        );
        //?}
        mutableChildren().add(entry);
    }

    @SuppressWarnings("unchecked")
    private List<ServerSelectionList.Entry> mutableChildren() {
        return (List<ServerSelectionList.Entry>) (List<?>) ((AbstractSelectionListAccessor) this).polyplus$children();
    }
}
