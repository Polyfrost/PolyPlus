package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IrisTimeUniforms", remap = false)
public class MixinIrisTimeUniforms {
    static {
        IrisTimeUniforms.updateTime();
    }
}
//?}
