package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.polyfrost.polyplus.client.featured.FeaturedServer;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.polyfrost.polyplus.mixin.client.access.AbstractSelectionListAccessor;
import org.polyfrost.polyplus.mixin.client.access.OnlineServerEntryInvoker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class MixinServerSelectionList implements FeaturedServerListAccess {
    private static final int POLYPLUS$SPONSORED_HEADER_HEIGHT = 18;

    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private List<ServerSelectionList.OnlineServerEntry> onlineServers;
    @Shadow @Final private ServerSelectionList.Entry lanHeader;
    @Shadow @Final private List<ServerSelectionList.NetworkServerEntry> networkServers;

    @Inject(method = "refreshEntries", at = @At("HEAD"))
    private void polyplus$releaseFeaturedEntries(CallbackInfo ci) {
        FeaturedServerRowRegistry.release((ServerSelectionList) (Object) this);
    }

    @Inject(method = "refreshEntries", at = @At("RETURN"))
    private void polyplus$appendFeaturedEntries(CallbackInfo ci) {
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
    public void polyplus$moveSavedRow(ServerSelectionList.OnlineServerEntry moved, ServerData neighbor) {
        var self = (ServerSelectionList) (Object) this;
        var servers = screen.getServers();
        var position = new IdentityHashMap<ServerData, Integer>();
        for (int index = 0; index < servers.size(); index++) position.put(servers.get(index), index);
        onlineServers.sort(Comparator.comparingInt(entry -> position.getOrDefault(entry.getServerData(), Integer.MAX_VALUE)));

        //? if >= 1.21.10 {
        var children = mutableChildren();
        int from = children.indexOf(moved);
        int to = -1;
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) instanceof ServerSelectionList.OnlineServerEntry entry
                && entry.getServerData() == neighbor
                && FeaturedServerRowRegistry.get(entry) == null) {
                to = index;
                break;
            }
        }
        if (from >= 0 && to >= 0) {
            ((AbstractSelectionListAccessor) this).polyplus$swap(from, to);
            self.setSelected(moved);
            return;
        }
        //?}
        polyplus$rebuildFeaturedServers();
        if (mutableChildren().contains(moved)) self.setSelected(moved);
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
        var groups = new ArrayList<>(ServerFolders.layout(
            onlineServers,
            entry -> entry.getServerData().ip,
            entry -> entry.getServerData().name
        ));
        groups.sort(Comparator.comparingInt(group -> group.folder == null ? 0 : 1));
        for (var group : groups) {
            if (group.folder != null) addFolderHeader(group.folder);
            if (group.folder == null || !ServerFolders.isCollapsed(group.folder)) {
                for (var entry : group.items) add(entry);
            }
        }
        add(lanHeader);
        for (var entry : networkServers) add(entry);

        boolean hasUnsavedSponsored = sponsored.stream().anyMatch(server ->
            !savedByAddress.containsKey(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))
        );
        if (hasUnsavedSponsored) {
            addHeader();
            for (var server : sponsored) {
                if (!savedByAddress.containsKey(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))) {
                    addRemote(server, false);
                }
            }
        }
        if (self.getSelected() != null && !mutableChildren().contains(self.getSelected())) self.setSelected(null);
    }

    private void addRemote(FeaturedServer server, boolean promoted) {
        var data = new ServerData(server.getName(), server.getAddress(), ServerData.Type.OTHER);
        var self = (ServerSelectionList) (Object) this;
        var entry = OnlineServerEntryInvoker.polyplus$create(self, screen, data);
        FeaturedServerRowRegistry.register(entry, self, screen, data, server, promoted, false);
        add(entry);
    }

    private void addFolderHeader(String folder) {
        var data = new ServerData(folder, "0.0.0.0", ServerData.Type.OTHER);
        var self = (ServerSelectionList) (Object) this;
        var entry = OnlineServerEntryInvoker.polyplus$create(self, screen, data);
        FeaturedServerRowRegistry.registerFolder(entry, self, screen, data, folder);
        add(entry, true);
    }

    private void addHeader() {
        var data = new ServerData("Sponsored", "0.0.0.0", ServerData.Type.OTHER);
        var self = (ServerSelectionList) (Object) this;
        var entry = OnlineServerEntryInvoker.polyplus$create(self, screen, data);
        FeaturedServerRowRegistry.register(entry, self, screen, data, null, false, true);
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
//?} else {
/*import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.LanServerEntry;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import org.polyfrost.polyplus.client.featured.FeaturedServer;
import org.polyfrost.polyplus.client.featured.FeaturedServerListAccess;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerRowRegistry;
import org.polyfrost.polyplus.client.featured.FeaturedServers;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.polyfrost.polyplus.mixin.client.access.OnlineServerEntryInvoker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Mixin(MultiplayerServerListWidget.class)
public abstract class MixinServerSelectionList implements FeaturedServerListAccess {
    @Shadow @Final private MultiplayerScreen parent;
    @Shadow @Final private List<ServerListEntryWidget> servers;
    @Shadow @Final private List<LanServerEntry> lanServers;
    @Shadow @Final private EntryListWidget.Entry scanningWidget;
    @Shadow private int currentServerIndex;

    @Unique
    private final List<EntryListWidget.Entry> polyplus$display = new ArrayList<>();

    @Unique
    private final List<EntryListWidget.Entry> polyplus$featured = new ArrayList<>();

    @Unique
    private final List<String> polyplus$folders = new ArrayList<>();

    @Unique
    private Object polyplus$keptSelection;

    @Unique
    private boolean polyplus$keeping;

    @Inject(method = "setServers", at = @At("HEAD"))
    private void polyplus$rememberSelection(ServerList list, CallbackInfo ci) {
        polyplus$keptSelection = polyplus$keyOf(currentServerIndex);
        polyplus$keeping = true;
    }

    @Inject(method = "setServers", at = @At("RETURN"))
    private void polyplus$appendFeaturedEntries(ServerList list, CallbackInfo ci) {
        polyplus$rebuildFeaturedServers();
    }

    @Inject(method = "size", at = @At("HEAD"), cancellable = true)
    private void polyplus$countEntries(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(polyplus$count());
    }

    @Inject(method = "getEntry", at = @At("HEAD"), cancellable = true)
    private void polyplus$displayEntry(int index, CallbackInfoReturnable<EntryListWidget.Entry> cir) {
        cir.setReturnValue(polyplus$entryAt(index));
    }

    @Unique
    private int polyplus$count() {
        return polyplus$display.size() + 1 + lanServers.size() + polyplus$featured.size();
    }

    @Unique
    private EntryListWidget.Entry polyplus$entryAt(int index) {
        if (index < 0) return null;
        if (index < polyplus$display.size()) return polyplus$display.get(index);
        index -= polyplus$display.size();
        if (index == 0) return scanningWidget;
        index--;
        if (index < lanServers.size()) return lanServers.get(index);
        index -= lanServers.size();
        return index < polyplus$featured.size() ? polyplus$featured.get(index) : null;
    }

    @Unique
    private Object polyplus$keyOf(int index) {
        var entry = polyplus$entryAt(index);
        if (entry == null) return null;
        if (entry instanceof FeaturedServerRowRegistry.FolderHeader header) return "folder:" + header.folder();
        if (entry instanceof ServerListEntryWidget widget) {
            var row = FeaturedServerRowRegistry.get(widget);
            if (row == null) return widget.fetchServer();
            return (row.promoted() ? "promoted:" : "sponsored:") + FeaturedServerModelsKt.normalizeServerAddress(row.data().ip);
        }
        return index - polyplus$display.size();
    }

    @Unique
    private int polyplus$indexOf(Object key) {
        if (key == null) return -1;
        int count = polyplus$count();
        for (int index = 0; index < count; index++) {
            if (Objects.equals(key, polyplus$keyOf(index))) return index;
        }
        return -1;
    }

    @Override
    public List<String> polyplus$folders() {
        return polyplus$folders;
    }

    @Override
    public void polyplus$rebuildFeaturedServers() {
        var self = (MultiplayerServerListWidget) (Object) this;
        Object selection = polyplus$keeping ? polyplus$keptSelection : polyplus$keyOf(currentServerIndex);
        polyplus$keeping = false;
        polyplus$keptSelection = null;
        FeaturedServerRowRegistry.release(self);
        polyplus$display.clear();
        polyplus$featured.clear();
        polyplus$folders.clear();

        var groups = ServerFolders.layout(servers, entry -> entry.fetchServer().ip, entry -> Objects.toString(entry.fetchServer().name, ""));
        for (var group : groups) {
            if (group.folder != null) {
                polyplus$folders.add(group.folder);
                polyplus$display.add(new FeaturedServerRowRegistry.FolderHeader(self, parent, group.folder));
            }
            if (group.folder == null || !ServerFolders.isCollapsed(group.folder)) polyplus$display.addAll(group.items);
        }

        long now = System.currentTimeMillis();
        var snapshot = FeaturedServers.snapshot();
        var saved = new HashSet<String>();
        for (var entry : servers) saved.add(FeaturedServerModelsKt.normalizeServerAddress(entry.fetchServer().ip));

        for (var server : snapshot.featuredServers(now)) {
            if (!saved.contains(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))) {
                polyplus$addRemote(server, true);
            }
        }
        boolean headerAdded = false;
        for (var server : snapshot.sponsoredServers(now)) {
            if (saved.contains(FeaturedServerModelsKt.normalizeServerAddress(server.getAddress()))) continue;
            if (!headerAdded) {
                polyplus$featured.add(new FeaturedServerRowRegistry.Header());
                headerAdded = true;
            }
            polyplus$addRemote(server, false);
        }
        self.setCurrentServerIndex(polyplus$indexOf(selection));
    }

    @Unique
    private void polyplus$addRemote(FeaturedServer server, boolean promoted) {
        var data = new ServerListEntry(server.getName(), server.getAddress(), false);
        var entry = OnlineServerEntryInvoker.polyplus$create(parent, data);
        FeaturedServerRowRegistry.register(entry, (MultiplayerServerListWidget) (Object) this, parent, data, server, promoted);
        polyplus$featured.add(entry);
    }

    @Override
    public void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row) {
        ServerList list = parent.getServerList();
        var address = FeaturedServerModelsKt.normalizeServerAddress(row.data().ip);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (FeaturedServerModelsKt.normalizeServerAddress(list.get(i).ip).equals(address)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            list.add(new ServerListEntry(row.data().name, row.data().ip, false));
            index = list.size() - 1;
            if (row.promoted()) {
                for (int i = index; i > 0; i--) list.swap(i, i - 1);
                index = 0;
            }
            list.save();
        }
        ((MultiplayerServerListWidget) (Object) this).setServers(list);
        var data = list.get(index);
        int shown = polyplus$indexOf(data);
        var folder = ServerFolders.folderOf(data.ip);
        if (shown < 0 && folder != null && ServerFolders.isCollapsed(folder)) {
            ServerFolders.toggle(folder);
            polyplus$rebuildFeaturedServers();
            shown = polyplus$indexOf(data);
        }
        parent.moveToServer(shown);
    }
}
*///?}
