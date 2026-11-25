package org.polyfrost.polyplus.guard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Sits next to the swylow build of PolyPlus. If a launcher swaps that jar for the official PolyPlus, this one stays
 * and says so once the game is up, so you know to put the swylow build back.
 */
public final class SwylowGuard implements ClientModInitializer {
    private static final long TOAST_MILLIS = 20_000L;

    @Override
    public void onInitializeClient() {
        Optional<ModContainer> polyplus = FabricLoader.getInstance().getModContainer("polyplus");
        if (polyplus.isEmpty()) return;
        String version = polyplus.get().getMetadata().getVersion().getFriendlyString();
        if (version.contains("swylow")) return;
        System.err.println("[PolyPlus Swylow guard] PolyPlus " + version + " is loaded instead of the swylow build; the launcher probably replaced it.");
        Thread waiter = new Thread(() -> {
            try {
                // the toast waits until the game has finished loading and shows a screen
                while (true) {
                    Thread.sleep(1000L);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null || mc.gui == null || mc.gui.screen() == null || mc.gui.overlay() != null) continue;
                    mc.execute(() -> SystemToast.add(
                        mc.gui.toastManager(),
                        new SystemToast.SystemToastId(TOAST_MILLIS),
                        Component.literal("PolyPlus was replaced by the launcher"),
                        Component.literal("Re-install the swylow build (PolyPlus " + version + " is loaded)")));
                    return;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "PolyPlus Swylow guard");
        waiter.setDaemon(true);
        waiter.start();
    }
}
