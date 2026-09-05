package org.polyfrost.polyplus.client.featured;

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
        boolean header,
        boolean expanded
    ) {
        Row row = new Row(list, entry, screen, data, server, promoted, header, expanded);
        ROWS.put(entry, row);
        return row;
    }

    public static synchronized Row get(Object entry) {
        return ROWS.get(entry);
    }

    public static synchronized int promotedCount(ServerSelectionList list) {
        int count = 0;
        for (var row : ROWS.values()) {
            if (row.list == list && row.promoted && !row.header) count++;
        }
        return count;
    }

    public static synchronized void release(ServerSelectionList list) {
        var entries = new ArrayList<ServerSelectionList.OnlineServerEntry>();
        for (var item : ROWS.entrySet()) {
            if (item.getValue().list == list) entries.add(item.getKey());
        }
        for (var entry : entries) {
            ROWS.remove(entry);
            entry.close();
        }
    }

    public static final class Row {
        private final ServerSelectionList list;
        private final ServerSelectionList.OnlineServerEntry entry;
        private final JoinMultiplayerScreen screen;
        private final ServerData data;
        private final FeaturedServer server;
        private final boolean promoted;
        private final boolean header;
        private final boolean expanded;
        private int x;
        private int y;
        private int width;
        private int height;
        private int dismissX;
        private int dismissY;
        private int dismissWidth;
        private int dismissHeight;
        private long lastClickMillis;

        private Row(
            ServerSelectionList list,
            ServerSelectionList.OnlineServerEntry entry,
            JoinMultiplayerScreen screen,
            ServerData data,
            FeaturedServer server,
            boolean promoted,
            boolean header,
            boolean expanded
        ) {
            this.list = list;
            this.entry = entry;
            this.screen = screen;
            this.data = data;
            this.server = server;
            this.promoted = promoted;
            this.header = header;
            this.expanded = expanded;
        }

        public ServerSelectionList list() {
            return list;
        }

        public ServerSelectionList.OnlineServerEntry entry() {
            return entry;
        }

        public JoinMultiplayerScreen screen() {
            return screen;
        }

        public ServerData data() {
            return data;
        }

        public FeaturedServer server() {
            return server;
        }

        public boolean promoted() {
            return promoted;
        }

        public boolean header() {
            return header;
        }

        public boolean expanded() {
            return expanded;
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

        public void bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void dismissBounds(int x, int y, int width, int height) {
            this.dismissX = x;
            this.dismissY = y;
            this.dismissWidth = width;
            this.dismissHeight = height;
        }

        public boolean dismissHit(double mouseX, double mouseY) {
            return !header && dismissWidth > 0
                && mouseX >= dismissX && mouseX < dismissX + dismissWidth
                && mouseY >= dismissY && mouseY < dismissY + dismissHeight;
        }

        public boolean registerClick(long nowMillis) {
            boolean doubleClick = nowMillis - lastClickMillis < 250L;
            lastClickMillis = nowMillis;
            return doubleClick;
        }
    }
}
