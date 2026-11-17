package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 && < 1.21.11 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenBindAccessor {
    @Accessor("minecraft")
    void polyplusSetMinecraft(Minecraft minecraft);

    @Accessor("font")
    void polyplusSetFont(Font font);
}
*///?}
