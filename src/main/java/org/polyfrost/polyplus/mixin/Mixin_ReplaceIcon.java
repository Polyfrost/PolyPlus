package org.polyfrost.polyplus.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.polyfrost.polyplus.client.PolyPlusClient;
import org.polyfrost.polyplus.client.utils.IconLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.InputStream;
import java.util.Objects;

@Mixin(value = Minecraft.class, priority = Integer.MIN_VALUE)
public class Mixin_ReplaceIcon {

    @Inject(method = "setWindowIcon", at = @At("HEAD"), cancellable = true)
    private void setWindowIcon(CallbackInfo ci) {
        try (InputStream stream = PolyPlusClient.class.getResourceAsStream("/assets/polyplus/PolyPlusIcon.png")) {
            Display.setIcon(IconLoader.load(ImageIO.read(Objects.requireNonNull(stream))));
            ci.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
