package org.polyfrost.polyplus.mixin.client.access;

//? if >= 1.21.8 {
import com.mojang.blaze3d.platform.Lighting;
//? if >= 26.2 {
import org.joml.Vector3fc;
//?} else {
/*import org.joml.Vector3f;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Lighting.class)
public interface LightingInvoker {
    //? if >= 26.2 {
    @Invoker("updateBuffer")
    void polyplus$updateBuffer(Lighting.Entry entry, Vector3fc first, Vector3fc second);
    //?} else {
    /*@Invoker("updateBuffer")
    void polyplus$updateBuffer(Lighting.Entry entry, Vector3f first, Vector3f second);
    *///?}
}
//?}
