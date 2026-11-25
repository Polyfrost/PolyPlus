package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.polyfrost.polyplus.client.gui.vanillamenu.OtherVersionConfirm;
import org.polyfrost.polyplus.client.gui.vanillamenu.WorldSectionHeader;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opening a world from another version first asks, like the disconnect confirm. */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class MixinWorldListEntry {
    @Shadow @Final private LevelSummary summary;

    @Shadow public abstract void joinWorld();

    @Unique private static boolean polyplus$confirmed;

    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void polyplus$confirmOtherVersion(CallbackInfo ci) {
        if (polyplus$confirmed || !WorldSectionHeader.enabled() || !summary.primaryActionActive() || !WorldSectionHeader.otherVersion(summary)) return;
        ci.cancel();
        var back = ClientPlatform.INSTANCE.currentScreen();
        OtherVersionConfirm.show(
            summary.getLevelName(),
            summary.getWorldVersionName().getString(),
            () -> {
                ClientPlatform.INSTANCE.setScreen(back);
                polyplus$confirmed = true;
                try {
                    joinWorld();
                } finally {
                    polyplus$confirmed = false;
                }
            },
            () -> ClientPlatform.INSTANCE.setScreen(back)
        );
    }
}
//?}
