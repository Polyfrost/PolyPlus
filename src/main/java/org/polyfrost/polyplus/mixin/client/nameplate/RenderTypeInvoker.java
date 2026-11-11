package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}

//? if >= 1.21.11 {
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType polyplus$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
//?} else {
/*public interface RenderTypeInvoker {
}
*///?}
