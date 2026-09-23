package org.polyfrost.polyplus.client.featured;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class FeaturedServerRowRegistry {
    private static final Map<ServerSelectionList.OnlineServerEntry, Row> ROWS = new WeakHashMap<>();

    private FeaturedServerRowRegistry() {
    }

    public static synchronized Row register(
        ServerSelectionList.OnlineServerEntry entry,
        ServerSelectionList list,
        JoinMultiplayerScreen screen,
        ServerData data,
        FeaturedServer server,
        boolean promoted,
        boolean header
    ) {
        Row row = new Row(list, entry, screen, data, server, promoted, header);
        ROWS.put(entry, row);
        return row;
    }

    public static synchronized Row get(Object entry) {
        return ROWS.get(entry);
    }

    public static synchronized int promotedCount(ServerSelectionList list) {
        int count = 0;
        for (var row : ROWS.values()) {
            if (row.list() == list && row.promoted() && !row.header()) count++;
        }
        return count;
    }

    public static synchronized void release(ServerSelectionList list) {
        var entries = new ArrayList<ServerSelectionList.OnlineServerEntry>();
        for (var item : ROWS.entrySet()) {
            if (item.getValue().list() == list) entries.add(item.getKey());
        }
        for (var entry : entries) {
            ROWS.remove(entry);
            entry.close();
        }
    }

    public record Row(
        ServerSelectionList list,
        ServerSelectionList.OnlineServerEntry entry,
        JoinMultiplayerScreen screen,
        ServerData data,
        FeaturedServer server,
        boolean promoted,
        boolean header,
        Box box
    ) {
        private Row(
            ServerSelectionList list,
            ServerSelectionList.OnlineServerEntry entry,
            JoinMultiplayerScreen screen,
            ServerData data,
            FeaturedServer server,
            boolean promoted,
            boolean header
        ) {
            this(list, entry, screen, data, server, promoted, header, new Box());
        }

        public int x() {
            return box.x;
        }

        public int y() {
            return box.y;
        }

        public int width() {
            return box.width;
        }

        public void bounds(int x, int y, int width, int height) {
            box.x = x;
            box.y = y;
            box.width = width;
            box.height = height;
        }

        public void dismissBounds(int x, int y, int width, int height) {
            box.dismissX = x;
            box.dismissY = y;
            box.dismissWidth = width;
            box.dismissHeight = height;
        }

        public boolean dismissHit(double mouseX, double mouseY) {
            return !header && box.dismissWidth > 0
                && mouseX >= box.dismissX && mouseX < box.dismissX + box.dismissWidth
                && mouseY >= box.dismissY && mouseY < box.dismissY + box.dismissHeight;
        }

        public boolean registerClick(long nowMillis) {
            boolean doubleClick = nowMillis - box.lastClickMillis < 250L;
            box.lastClickMillis = nowMillis;
            return doubleClick;
        }

        public static final class Box {
            private int x;
            private int y;
            private int width;
            private int height;
            private int dismissX;
            private int dismissY;
            private int dismissWidth;
            private int dismissHeight;
            private long lastClickMillis;
        }
    }
}
//?} else {
/*import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.options.ServerListEntry;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class FeaturedServerRowRegistry {
    private static final Map<ServerListEntryWidget, Row> ROWS = new WeakHashMap<>();

    private FeaturedServerRowRegistry() {
    }

    public static synchronized Row register(
        ServerListEntryWidget entry,
        MultiplayerServerListWidget list,
        MultiplayerScreen screen,
        ServerListEntry data,
        FeaturedServer server,
        boolean promoted
    ) {
        Row row = new Row(list, entry, screen, data, server, promoted);
        ROWS.put(entry, row);
        return row;
    }

    public static synchronized Row get(Object entry) {
        return ROWS.get(entry);
    }

    public static synchronized void release(MultiplayerServerListWidget list) {
        var entries = new ArrayList<ServerListEntryWidget>();
        for (var item : ROWS.entrySet()) {
            if (item.getValue().list == list) entries.add(item.getKey());
        }
        for (var entry : entries) ROWS.remove(entry);
    }

    public static final class Header implements EntryListWidget.Entry {
        @Override
        public void renderOutOfBounds(int index, int x, int y) {
        }

        @Override
        public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            FeaturedServerVanillaRenderer.header(x, y, width, height);
        }

        @Override
        public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
            return false;
        }

        @Override
        public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        }
    }

    public static final class Row {
        private final MultiplayerServerListWidget list;
        private final ServerListEntryWidget entry;
        private final MultiplayerScreen screen;
        private final ServerListEntry data;
        private final FeaturedServer server;
        private final boolean promoted;
        private int x;
        private int y;
        private int width;
        private int dismissX;
        private int dismissY;
        private int dismissWidth;
        private int dismissHeight;

        private Row(
            MultiplayerServerListWidget list,
            ServerListEntryWidget entry,
            MultiplayerScreen screen,
            ServerListEntry data,
            FeaturedServer server,
            boolean promoted
        ) {
            this.list = list;
            this.entry = entry;
            this.screen = screen;
            this.data = data;
            this.server = server;
            this.promoted = promoted;
        }

        public MultiplayerServerListWidget list() {
            return list;
        }

        public ServerListEntryWidget entry() {
            return entry;
        }

        public MultiplayerScreen screen() {
            return screen;
        }

        public ServerListEntry data() {
            return data;
        }

        public FeaturedServer server() {
            return server;
        }

        public boolean promoted() {
            return promoted;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public void bounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        public void dismissBounds(int x, int y, int width, int height) {
            this.dismissX = x;
            this.dismissY = y;
            this.dismissWidth = width;
            this.dismissHeight = height;
        }

        public boolean dismissHit(double mouseX, double mouseY) {
            return dismissWidth > 0
                && mouseX >= dismissX && mouseX < dismissX + dismissWidth
                && mouseY >= dismissY && mouseY < dismissY + dismissHeight;
        }
    }
}
*///?}
