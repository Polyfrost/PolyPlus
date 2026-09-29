package org.polyfrost.polyplus.client.featured;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;

public final class FeaturedServerRowRegistry {
    private FeaturedServerRowRegistry() {
    }

    public static Row register(
        ServerSelectionList.OnlineServerEntry entry,
        ServerSelectionList list,
        JoinMultiplayerScreen screen,
        ServerData data,
        FeaturedServer server,
        boolean promoted,
        boolean header
    ) {
        Row row = new Row(list, entry, screen, data, server, promoted, header);
        ((FeaturedServerEntryAccess) entry).polyplus$setFeaturedRow(row);
        return row;
    }

    public static Row get(Object entry) {
        return entry instanceof FeaturedServerEntryAccess access ? access.polyplus$featuredRow() : null;
    }

    public static int promotedCount(ServerSelectionList list) {
        int count = 0;
        for (var entry : list.children()) {
            var row = get(entry);
            if (row != null && row.promoted() && !row.header()) count++;
        }
        return count;
    }

    public static void release(ServerSelectionList list) {
        for (var entry : list.children()) {
            if (get(entry) != null) entry.close();
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
