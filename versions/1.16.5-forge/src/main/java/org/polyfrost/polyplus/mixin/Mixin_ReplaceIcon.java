package org.polyfrost.polyplus.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.polyfrost.polyplus.client.PolyPlusClient;
import org.polyfrost.polyplus.client.utils.IconLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

@Mixin(value = Minecraft.class, priority = Integer.MIN_VALUE)
public class Mixin_ReplaceIcon {

    @Shadow @Final private Window window;

    @Inject(method = "<init>", at = @At("TAIL"), cancellable = true)
    private void setWindowIcon(CallbackInfo ci) {
        try (InputStream stream = PolyPlusClient.class.getResourceAsStream("/assets/polyplus/PolyPlusIcon.png")) {
            GLFWImage.Buffer icons = GLFWImage.malloc(2);
            ByteBuffer[] buffers = IconLoader.load(ImageIO.read(Objects.requireNonNull(stream)));
            for (int i = 0; i < buffers.length; i++) {
                try (GLFWImage image = GLFWImage.malloc()) {
                    int[] sizes = IconLoader.IMAGE_SIZES;
                    image.height(sizes[i]);
                    image.width(sizes[i]);
                    image.pixels(buffers[i]);
                    icons.put(i, image);
                }
            }

            GLFW.glfwSetWindowIcon(this.window.getWindow(), icons);
            ci.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
   