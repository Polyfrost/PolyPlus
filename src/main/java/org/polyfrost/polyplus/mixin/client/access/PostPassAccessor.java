package org.polyfrost.polyplus.mixin.client.access;

//? if >= 1.21.8 || = 1.21.4 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
//?} elif >= 1.21.8 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
*///?} else {
/*import net.minecraft.client.renderer.CompiledShaderProgram;
*///?}
//? if >= 1.21.8
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface PostPassAccessor {
    //? if >= 1.21.8 {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> polyplus$customUniforms();
    //?} else {
    /*@Accessor("shader")
    CompiledShaderProgram polyplus$shader();
    *///?}
}
//?}
