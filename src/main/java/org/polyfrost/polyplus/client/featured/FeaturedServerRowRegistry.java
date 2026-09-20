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
