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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setWindowIcon(CallbackInfo ci) {
        try (InputStream stream = PolyPlusClient.class.getResourceAsStream("/assets/polyplus/PolyPlusIcon.png")) {
            GLFWImage.Buffer icons = GLFWImage.malloc(2);
            ByteBuffer[] buffers = IconLoader.load(ImageIO.read(Objects.requireNonNull(stream)));
            for (int i = 0; i < buffers.length; i++) {
                GLFWImage image = GLFWImage.malloc();
                int size = IconLoader.IMAGE_SIZES[i];

                image.height(size).width(size).pixels(buffers[i]);

                icons.put(i, image);
            }

            for (int i = 0; i < buffers.length; i++) {
                int expected = IconLoader.IMAGE_SIZES[i]
                        * IconLoader.IMAGE_SIZES[i] * 4;

                System.out.println(
                        "[PolyPlus] icon " + i +
                                " expected=" + expected +
                                " actual=" + buffers[i].remaining() +
                                " direct=" + buffers[i].isDirect()
                );
            }


            Minecraft.getInstance().execute(() -> {
                GLFW.glfwSetWindowIcon(this.window.getWindow(), icons);

                icons.forEach(GLFWImage::free);
                icons.free();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
   