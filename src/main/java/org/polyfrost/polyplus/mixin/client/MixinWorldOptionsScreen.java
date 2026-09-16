package org.polyfrost.polyplus.mixin.client;

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.social.SocialOverlay;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
//?}

/**
 * 26.3 moved the Open to LAN flow into World Options, so that screen's Multiplayer section is swapped for
 * the PolyPlus host flow.
 */
//? if >= 26.3 {
@Mixin(WorldOptionsScreen.class)
//?}
public class MixinWorldOptionsScreen {
    //? if >= 26.3 {
    @WrapMethod(method = "multiplayerOptions")
    private void polyplus$replaceMultiplayerOptions(LinearLayout content, IntegratedServer server, Operation<Void> original) {
        // once published, vanilla's section is the only way to change the port, guest access, or stop hosting
        if (!PolyPlusConfig.getReplacePauseLanButton() || server.isPublished()) {
            original.call(content, server);
            return;
        }
        content.addChild(
            Button.builder(
                    Component.translatable("polyplus.hostWorld"),
                    button -> SocialOverlay.INSTANCE.openHostCurrentWorld(ClientPlatform.INSTANCE.currentScreen())
                )
                .width(308)
                .build()
        );
    }
    //?}
}
