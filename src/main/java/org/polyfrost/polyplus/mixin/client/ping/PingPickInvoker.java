package org.polyfrost.polyplus.mixin.client.ping;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface PingPickInvoker {
    //? if >= 26.1 {
    @Invoker("pickBlockOrEntity")
    //?} elif > 1.8.9 {
    /*@Invoker("pickBlock")
    *///?} else {
    /*@Invoker("doPick")
    *///?}
    void polyplus$pick();
}
