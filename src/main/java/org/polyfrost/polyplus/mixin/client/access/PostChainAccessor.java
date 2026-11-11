package org.polyfrost.polyplus.mixin.client.access;

//? if >= 1.21.8 || < 1.21.5 {
import java.util.List;
//? if > 1.8.9 {
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
//?} else {
/*import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> polyplus$passes();
}
//?}
