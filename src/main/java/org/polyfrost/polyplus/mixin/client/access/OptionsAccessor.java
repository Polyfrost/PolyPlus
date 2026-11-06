package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("serverRenderDistance")
    int polyplus$serverRenderDistance();
}
//?}
